package omegadrive.util;

import org.slf4j.Logger;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface Fifo<T> extends Serializable {
    void push(T data);

    T pop();

    T peek();

    boolean isEmpty();

    boolean isFull();

    /**
     * @return the current size of the fifo
     */
    int getLevel();

    default int isEmptyBit() {
        return isEmpty() ? 1 : 0;
    }

    default int isFullBit() {
        return isFull() ? 1 : 0;
    }

    default void clear() {
        while (!isEmpty()) {
            pop();
        }
    }

    static Fifo<Integer> createIntegerFixedSizeFifo(int fifoSize) {
        FixedSizeFifo<Integer> f = new FixedSizeFifo<>(fifoSize);
        f.fifo = new Integer[fifoSize];
        Arrays.fill(f.fifo, 0);
        return f;
    }

    class FixedSizeFifo<T> implements Fifo<T> {

        @Serial
        private static final long serialVersionUID = -3596142249449303723L;

        protected final static Logger LOG = LogHelper.getLogger(FixedSizeFifo.class.getSimpleName());
        public static final boolean logEnable = false;

        public static final boolean printToSysOut = false;
        private int popPointer;
        protected int pushPointer;
        private final int fifoSize;
        private int currentSize;

        protected T[] fifo;

        public FixedSizeFifo(int size) {
            this.fifoSize = size;
        }

        @Override
        public void push(T data) {
            if (isFull()) {
                LOG.info("FIFO full");
                return;
            }
            fifo[pushPointer] = data;
            pushPointer = (pushPointer + 1) % fifoSize;
            currentSize++;
            logState(data, "push");
        }

        @Override
        public T pop() {
            if (isEmpty()) {
                LOG.info("FIFO empty");
                return null;
            }
            T entry = fifo[popPointer];
            popPointer = (popPointer + 1) % fifoSize;
            currentSize--;
            logState(entry, "pop");
            return entry;
        }


        public T peek() {
            return fifo[popPointer];
        }

        @Override
        public boolean isEmpty() {
            return currentSize == 0;
        }

        @Override
        public boolean isFull() {
            return currentSize >= fifoSize;
        }

        @Override
        public int getLevel() {
            return currentSize;
        }

        protected void logState(T entry, String type) {
            if (logEnable) {
                String str = LogHelper.formatMessage("Fifo {}: {}, push: {}, pop: {}, size: {}\nstate: {}", type,
                        entry, pushPointer, popPointer, currentSize, Arrays.toString(fifo));
                LOG.info(str);
                if (printToSysOut) {
                    System.out.println(str);
                }

            }
        }
    }
}
