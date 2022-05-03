package omegadrive.util;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Representation a fixed number of bits stored within an internal long array,
 * mapping positive integer indices to individual bits and facilitating the
 * manipulation of those individual bits or ranges of bits, as well as
 * retrieving their boolean values.
 * <p>
 * The bits represented by this {@link FastBitSet} will either be in the <i>live</i>
 * state (<code>1, true</code>), or the <i>dead</i> state
 * (<code>0, false</code>).
 * <p>
 * <p>
 * If {@link #size} isn't a multiple of 64, there will be hanging bits that
 * exist on the end of the last long within {@link #words}, which are not
 * accounted for by {@link #size}. No exception will be thrown when these bit
 * indices are manipulated or read, and in the aggregating functions
 * {@link #population()}, {@link #hashCode()}, etc., hanging bits can have their
 * effect on those aggregating functions made consistent by calling
 * {@link #clearHanging()}. Otherwise, accessing a negative index, or any index
 * greater than or equal to {@link #size} will cause an
 * {@link IndexOutOfBoundsException} to be thrown.
 *
 * @author Aaron Shouldis
 * @author Federico Berti
 * - 202205: refactoring, merge BitSet and InlineBitSet
 * @see <a href="https://github.com/ashouldis/bitset">BitSet</a>
 */
public class FastBitSet implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Long mask with all bits in the <i>live</i> state. Used for readability in
     * place of {@code -1L} (0xFFFFFFFFFFFFFFFF).
     */
    public static final long LIVE = -1L;

    /**
     * Long mask with all bits in the <i>dead</i> state. Used for readability in
     * place of {@code 0L} (0x0000000000000000).
     */
    public static final long DEAD = 0L;

    /**
     * Mask used to compute potentially faster modulo operations. {@code n % m} is
     * equivalent to {@code n & (m -1)} if n is positive, and m = 2<sup>k</sup>.
     */
    protected static final int MOD_SIZE_MASK = Long.SIZE - 1;

    /**
     * log<sub>2</sub>64. Used to relate bit indices to word indices through
     * bit-shifting as an alternative to division or multiplication by 64.
     */
    protected static final int LOG_2_SIZE = 6;

    /**
     * The number of indices accessible by this {@link FastBitSet}. Indices <b>0</b>
     * through <b>size -1</b> are accessible.
     */
    public final int size;

    /**
     * The number of long words contained by this {@link FastBitSet}. Equal to
     * ceiling({@link #size} / 64) and {@link #words}.length.
     */
    public final int wordCount;

    /**
     * Array holding the long words whose bits are manipulated. Has length
     * ceiling({@link #size} / 64). Though this has protected visibility, using
     * methods such as {@link #getWord(int)}, {@link #setWord(int, long)},
     * {@link #andWord(int, long)} is preferred over direct access.
     */
    protected final long[] words;

    /**
     * Creates a {@link FastBitSet} with the specified number of bits indices. Indices 0
     * through <b>size</b> -1 will be accessible. All bits are initially in the
     * <i>dead</i> state.
     *
     * @param size the number of bit indices that this {@link FastBitSet} will hold.
     * @throws IllegalArgumentException if <b>size</b> is less than 0.
     */
    public FastBitSet(final int size) {
        int wordCount = FastBitSet.divideSize(size);
        if (wordCount < 0) {
            throw new IllegalArgumentException(Integer.toString(size));
        }
        if (FastBitSet.modSize(size) > 0) {
            wordCount++;
        }
        this.size = size;
        this.wordCount = wordCount;
        words = new long[wordCount];
    }

    /**
     * Creates a {@link FastBitSet} which is a clone of the specified {@link FastBitSet}
     * <b>set</b>. The copy will have an identical {@link #size}, and will copy the
     * contents of <b>set</b>'s {@link #words} through {@link #copy(FastBitSet)}.
     *
     * @param set the {@link FastBitSet} to copy.
     * @throws NullPointerException if <b>set</b> is null.
     */
    public FastBitSet(final FastBitSet set) {
        this(set.size);
        copy(set);
    }

    public final boolean get(final int index) {
        return (words[index >>> LOG_2_SIZE] & (1L << index)) != DEAD;
    }

    public final int get(final int from, final int to) {
        final int start = from >>> LOG_2_SIZE;
        final int end = (to - 1) << LOG_2_SIZE;
        final long startMask = LIVE << from;
        final long endMask = LIVE >>> -to;
        int sum;
        if (start == end) {
            sum = Long.bitCount(words[start] & startMask & endMask);
        } else {
            sum = Long.bitCount(words[start] & startMask);
            for (int i = start + 1; i < end; i++) {
                sum += Long.bitCount(words[i]);
            }
            sum += Long.bitCount(words[end] & endMask);
        }
        return sum;
    }

    public final void set(int index, boolean val) {
        if (val) {
            set(index);
        } else {
            clear(index);
        }
    }

    public final void set(final int index) {
        words[index >>> LOG_2_SIZE] |= (1L << index);
    }

    public final void set(final int from, final int to) {
        final int start = from >>> LOG_2_SIZE;
        final int end = (to - 1) << LOG_2_SIZE;
        final long startMask = LIVE << from;
        final long endMask = LIVE >>> -to;
        if (start == end) {
            words[start] |= startMask & endMask;
        } else {
            words[start] |= startMask;
            for (int i = start + 1; i < end; i++) {
                words[i] = LIVE;
            }
            words[end] |= endMask;
        }
    }

    public final void clear(final int index) {
        words[index >>> LOG_2_SIZE] &= ~(1L << index);
    }

    public final void clear(final int from, final int to) {
        final int start = from >>> LOG_2_SIZE;
        final int end = (to - 1) << LOG_2_SIZE;
        final long startMask = LIVE << from;
        final long endMask = LIVE >>> -to;
        if (start == end) {
            words[start] &= ~(startMask & endMask);
        } else {
            words[start] &= ~startMask;
            for (int i = start + 1; i < end; i++) {
                words[i] = DEAD;
            }
            words[end] &= ~endMask;
        }
    }

    public void flip(final int index) {
        words[index >>> LOG_2_SIZE] ^= (1L << index);
    }

    public void flip(final int from, final int to) {
        final int start = from >>> LOG_2_SIZE;
        final int end = (to - 1) << LOG_2_SIZE;
        final long startMask = LIVE << from;
        final long endMask = LIVE >>> -to;
        if (start == end) {
            words[start] ^= startMask & endMask;
        } else {
            words[start] ^= startMask;
            for (int i = start + 1; i < end; i++) {
                words[i] ^= LIVE;
            }
            words[end] ^= endMask;
        }
    }

    public boolean add(final int index) {
        final int wordIndex = index >>> LOG_2_SIZE;
        final long mask = 1L << index;
        if ((words[wordIndex] & mask) != DEAD) {
            return false;
        }
        words[wordIndex] |= mask;
        return true;
    }

    public boolean remove(final int index) {
        final int wordIndex = index >>> LOG_2_SIZE;
        final long mask = ~(1L << index);
        if ((words[wordIndex] | mask) != LIVE) {
            return false;
        }
        words[wordIndex] &= mask;
        return true;
    }

    public void andWord(final int wordIndex, final long mask) {
        words[wordIndex] &= mask;
    }

    public void orWord(final int wordIndex, final long mask) {
        words[wordIndex] |= mask;
    }

    public void xOrWord(final int wordIndex, final long mask) {
        words[wordIndex] ^= mask;
    }

    public void notAndWord(final int wordIndex, final long mask) {
        words[wordIndex] = ~(words[wordIndex] & mask);
    }

    public void notOrWord(final int wordIndex, final long mask) {
        words[wordIndex] = ~(words[wordIndex] | mask);
    }

    public void notXOrWord(final int wordIndex, final long mask) {
        words[wordIndex] = ~(words[wordIndex] ^ mask);
    }

    public void setWordSegment(final int wordIndex, final long word, final long mask) {
        words[wordIndex] = (mask & word) | (~mask & words[wordIndex]);
    }

    public void flipWord(final int wordIndex) {
        words[wordIndex] ^= LIVE;
    }

    public void fillWord(final int wordIndex) {
        words[wordIndex] = LIVE;
    }

    public void emptyWord(final int wordIndex) {
        words[wordIndex] = DEAD;
    }


    /**
     * Returns the long word at the specified <b>wordIndex</b> within
     * {@link #words}.
     *
     * @param wordIndex the index within {@link #words} to read.
     * @return the raw contents of {@link #words} at the specified <b>wordIndex</b>.
     * @throws ArrayIndexOutOfBoundsException if <b>wordIndex</b> is outside of the
     *                                        range [0, {@link #wordCount}).
     */
    public long getWord(final int wordIndex) {
        return words[wordIndex];
    }

    /**
     * Changes the long word at the specified <b>wordIndex</b> within
     * {@link #words}, setting it to <b>word</b>.
     *
     * @param wordIndex the index within {@link #words} to set.
     * @param word      the long value to be set to {@link #words} at
     *                  <b>wordIndex</b>.
     * @throws ArrayIndexOutOfBoundsException if <b>wordIndex</b> is outside of the
     *                                        range [0, {@link #wordCount}).
     */
    public void setWord(final int wordIndex, final long word) {
        words[wordIndex] = word;
    }


    /**
     * Transforms each bit in this {@link FastBitSet} to the <i>live</i> state.
     */
    public final void fill() {
        for (int i = 0; i < wordCount; i++) {
            fillWord(i);
        }
    }

    /**
     * Transforms each bit in this {@link FastBitSet} to the <i>dead</i> state.
     */
    public final void empty() {
        for (int i = 0; i < wordCount; i++) {
            emptyWord(i);
        }
    }

    /**
     * Transforms each bit in this {@link FastBitSet} into the complement of its current
     * state.
     */
    public final void flip() {
        for (int i = 0; i < wordCount; i++) {
            flipWord(i);
        }
    }

    /**
     * Performs a global {@code AND} operation on all bits in this {@link FastBitSet}
     * with those in the specified {@link FastBitSet} <b>set</b>.
     *
     * @param set the other {@link FastBitSet} from which to perform the {@code AND}
     *            operation.
     * @throws IllegalArgumentException if the {@link #size}s of both
     *                                  {@link FastBitSet}s are not equal.
     * @throws NullPointerException     if <b>set</b> is null.
     */
    public final void and(final FastBitSet set) {
        for (int i = 0; i < wordCount; i++) {
            andWord(i, set.getWord(i));
        }
    }

    /**
     * Performs a global {@code OR} operation on all bits in this {@link FastBitSet}
     * with those in the specified {@link FastBitSet} <b>set</b>.
     *
     * @param set the other {@link FastBitSet} from which to perform the {@code OR}
     *            operation.
     * @throws IllegalArgumentException if the {@link #size}s of both
     *                                  {@link FastBitSet}s are not equal.
     * @throws NullPointerException     if <b>set</b> is null.
     */
    public final void or(final FastBitSet set) {
        for (int i = 0; i < wordCount; i++) {
            orWord(i, set.getWord(i));
        }
    }

    /**
     * Performs a global {@code XOR} operation on all bits in this {@link FastBitSet}
     * with those in the specified {@link FastBitSet} <b>set</b>.
     *
     * @param set the other {@link FastBitSet} from which to perform the {@code XOR}
     *            operation.
     * @throws IllegalArgumentException if the {@link #size}s of both
     *                                  {@link FastBitSet}s are not equal.
     * @throws NullPointerException     if <b>set</b> is null.
     */
    public final void xOr(final FastBitSet set) {
        for (int i = 0; i < wordCount; i++) {
            xOrWord(i, set.getWord(i));
        }
    }

    /**
     * Performs a global {@code NOT AND} operation on all bits in this
     * {@link FastBitSet} with those in the specified {@link FastBitSet} <b>set</b>.
     *
     * @param set the other {@link FastBitSet} from which to perform the {@code NOT AND}
     *            operation.
     * @throws IllegalArgumentException if the {@link #size}s of both
     *                                  {@link FastBitSet}s are not equal.
     * @throws NullPointerException     if <b>set</b> is null.
     */
    public final void notAnd(final FastBitSet set) {
        for (int i = 0; i < wordCount; i++) {
            notAndWord(i, set.getWord(i));
        }
    }

    /**
     * Performs a global {@code NOT OR} operation on all bits in this {@link FastBitSet}
     * with those in the specified {@link FastBitSet} <b>set</b>.
     *
     * @param set the other {@link FastBitSet} from which to perform the {@code NOT OR}
     *            operation.
     * @throws IllegalArgumentException if the {@link #size}s of both
     *                                  {@link FastBitSet}s are not equal.
     * @throws NullPointerException     if <b>set</b> is null.
     */
    public final void notOr(final FastBitSet set) {
        for (int i = 0; i < wordCount; i++) {
            notOrWord(i, set.getWord(i));
        }
    }

    /**
     * Performs a global {@code NOT XOR} operation on all bits in this
     * {@link FastBitSet} with those in the specified {@link FastBitSet} <b>set</b>.
     *
     * @param set the other {@link FastBitSet} from which to perform the {@code NOT XOR}
     *            operation.
     * @throws IllegalArgumentException if the {@link #size}s of both
     *                                  {@link FastBitSet}s are not equal.
     * @throws NullPointerException     if <b>set</b> is null.
     */
    public final void notXOr(final FastBitSet set) {
        for (int i = 0; i < wordCount; i++) {
            notXOrWord(i, set.getWord(i));
        }
    }

    /**
     * Transforms this {@link FastBitSet} into the complement of the specified
     * <b>set</b>.
     *
     * @param set the other {@link FastBitSet} from which to perform the {@code NOT}
     *            operation.
     * @throws IllegalArgumentException if the {@link #size}s of both
     *                                  {@link FastBitSet}s are not equal.
     * @throws NullPointerException     if <b>set</b> is null.
     */
    public final void not(final FastBitSet set) {
        for (int i = 0; i < wordCount; i++) {
            setWord(i, ~set.getWord(i));
        }
    }

    /**
     * Transforms this {@link FastBitSet} so that each bit matches the state of that in
     * the give <b>set</b>.
     *
     * @param set the other {@link FastBitSet} from which to copy.
     * @throws IllegalArgumentException if the {@link #size}s of both
     *                                  {@link FastBitSet}s are not equal.
     * @throws NullPointerException     if <b>set</b> is null.
     */
    public final void copy(final FastBitSet set) {
        for (int i = 0; i < wordCount; i++) {
            setWord(i, set.getWord(i));
        }
    }

    /**
     * Changes the state of any hanging bits to the <i>dead</i> state in order to
     * maintain their effect on aggregating functions ({@link #population()},
     * {@link #density()}, etc).
     */
    public final void clearHanging() {
        final int hanging = FastBitSet.modSize(-size);
        if (hanging > 0) {
            andWord(wordCount - 1, LIVE >>> hanging);
        }
    }

    public final boolean isEmpty() {
        clearHanging();
        return population() == 0;
    }

    /**
     * Calculates the number of <i>live</i> bits within this {@link FastBitSet}.
     * {@link #clearHanging()} can be used to stop the interference of hanging bits.
     * In certain cases, hanging bits can cause an integer overflow.
     *
     * @return the number of <i>live</i> bits.
     */
    public final int population() {
        int population = 0;
        for (int i = 0; i < wordCount; i++) {
            population += Long.bitCount(getWord(i));
        }
        return population;
    }

    /**
     * Calculates what percentage of bits in this {@link FastBitSet} are in the
     * <i>live</i> state in the specified range [<b>from</b>, <b>to</b>).
     * {@link #clearHanging()} can be used to stop the interference of hanging bits.
     *
     * @param from (inclusive) the index of the first bit to be checked.
     * @param to   (exclusive) the end of the range of bits to be checked.
     * @return the percentage of <i>live</i> bits.
     * @throws ArrayIndexOutOfBoundsException if <b>from</b> or <b>to</b> are
     *                                        outside of the range [0,
     *                                        {@link #size}).
     */
    public final double density(final int from, final int to) {
        return get(from, to) / (double) (to - from);
    }

    /**
     * Calculates what percentage of bits in this {@link FastBitSet} are in the
     * <i>live</i> state. {@link #clearHanging()} can be used to stop the
     * interference of hanging bits.
     *
     * @return the percentage of <i>live</i> bits.
     */
    public final double density() {
        return population() / (double) size;
    }

    /**
     * Calculates <b>n</b> / 64. Typically used to translate the index of a bit to
     * the index of the word that bit belongs to within {@link #words}.
     *
     * @param n the number to divide by 64.
     * @return <b>n</b> / 64.
     */
    public static final int divideSize(final int n) {
        return n >>> LOG_2_SIZE;
    }

    /**
     * Calculates <b>n</b> * 64. Typically used to translate the index of a word
     * within {@link #words} to the index of that word's first bit.
     *
     * @param n the number to multiply by 64.
     * @return <b>n</b> * 64.
     */
    public static final int multiplySize(final int n) {
        return n << LOG_2_SIZE;
    }

    /**
     * Calculates <b>n</b> % 64 for positive numbers. Also calculates 64 -
     * (-<b>n</b> % 64) for negative numbers as a side effect.
     *
     * @param n the number to modulo by 64.
     * @return the result of the modulo operation.
     */
    public static final int modSize(final int n) {
        return n & MOD_SIZE_MASK;
    }

    /**
     * Calculates a mask to represent the bit at which a specific index will be
     * stored within a long word.
     *
     * @param index the index to represent as a bit.
     * @return the bit that represents the position of an index within a word.
     */
    public static final long bitMask(final int index) {
        return 1L << index;
    }

    @Override
    public int hashCode() {
        long hash = size;
        for (int i = 0; i < wordCount; i++) {
            hash *= 31L;
            hash += getWord(i);
        }
        return (int) (hash ^ (hash >>> Integer.SIZE));
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        return obj instanceof FastBitSet && Arrays.equals(words, ((FastBitSet) obj).words);
    }

}