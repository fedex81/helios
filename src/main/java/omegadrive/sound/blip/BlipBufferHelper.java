package omegadrive.sound.blip;


import static omegadrive.util.ArrayEndianUtil.setSigned16LE;
import static omegadrive.util.SoundUtil.clampToByte;
import static omegadrive.util.SoundUtil.clampToShort;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class BlipBufferHelper {

    /**
     * leak = 1/512 per sample, ~14Hz high-pass @ 44.1kHz
     */
    private static final int DC_BLOCK_SHIFT = 9;
    /**
     * accumulator fixed-point scale -> signed 16-bit sample
     */
    private static final int ACCUM_FRACTION_BITS = 15;

    /**
     * BlipBuffer stores 16 bit stereo samples, convert to byte[] format: 16 bit stereo
     */
    public static int readSamples16bitStereo(StereoBlipBuffer blipBuffer, byte[] out, int pos, int countMono) {
        final int availMonoSamples = blipBuffer.samplesAvail();
        if (countMono > availMonoSamples)
            countMono = availMonoSamples;

        if (countMono > 0) {
            BlipBuffer left = blipBuffer.left();
            BlipBuffer right = blipBuffer.right();
            final int[] deltaBufL = left.buf;
            final int[] deltaBufR = right.buf;
            // Integrate
            int accumL = left.accum.get();
            int accumR = right.accum.get();
            int i = 0;
            do {
                accumL += deltaBufL[i] - (accumL >> DC_BLOCK_SHIFT);
                accumR += deltaBufR[i] - (accumR >> DC_BLOCK_SHIFT);
                int sl = accumL >> ACCUM_FRACTION_BITS;
                int sr = accumR >> ACCUM_FRACTION_BITS;

                // clamp to 16 bits
                sl = clampToShort(sl);
                sr = clampToShort(sr);

                setSigned16LE((short) sl, out, pos);
                setSigned16LE((short) sr, out, pos + 2);
                pos += 4;
            }
            while (++i < countMono);
            left.accum.set(accumL);
            right.accum.set(accumR);
            left.removeSamples(countMono);
            right.removeSamples(countMono);
        }
        return countMono;
    }

    /**
     * BlipBuffer stores 16 bit stereo samples, convert to int[] format: 16 bit stereo
     */
    public static int readSamples16bitStereo(StereoBlipBuffer blipBuffer, int[] out, int pos, int countMono) {
        final int availMonoSamples = blipBuffer.samplesAvail();
        if (countMono > availMonoSamples)
            countMono = availMonoSamples;

        if (countMono > 0) {
            BlipBuffer left = blipBuffer.left();
            BlipBuffer right = blipBuffer.right();
            final int[] deltaBufL = left.buf;
            final int[] deltaBufR = right.buf;
            // Integrate
            int accumL = left.accum.get();
            int accumR = right.accum.get();
            int i = 0;
            do {
                accumL += deltaBufL[i] - (accumL >> DC_BLOCK_SHIFT);
                accumR += deltaBufR[i] - (accumR >> DC_BLOCK_SHIFT);
                int sl = accumL >> ACCUM_FRACTION_BITS;
                int sr = accumR >> ACCUM_FRACTION_BITS;

                // clamp to 16 bits
                out[pos] = clampToShort(sl);
                out[pos + 1] = clampToShort(sr);
                pos += 2;
            }
            while (++i < countMono);
            left.accum.set(accumL);
            right.accum.set(accumR);
            left.removeSamples(countMono);
            right.removeSamples(countMono);
        }
        return countMono << 1;
    }

    /**
     * BlipBuffer stores 16 bit mono samples, convert to byte[] format: 16 bit stereo
     */
    public static int readSamples16bitMono_StereoOut(BlipBuffer blipBuffer, byte[] out, int pos, int countMono) {
        final int availMonoSamples = blipBuffer.samplesAvail();
        if (countMono > availMonoSamples)
            countMono = availMonoSamples;

        if (countMono > 0) {
            final int[] deltaBuf = blipBuffer.buf;
            // Integrate
            int accum = blipBuffer.accum.get();
            int i = 0;
            do {
                accum += deltaBuf[i] - (accum >> DC_BLOCK_SHIFT);
                int s = accum >> 15;

                // clamp to 16 bits
                s = clampToShort(s);

                setSigned16LE((short) s, out, pos);
                setSigned16LE((short) s, out, pos + 2);
                pos += 4;
            }
            while (++i < countMono);
            blipBuffer.accum.set(accum);
            blipBuffer.removeSamples(countMono);
        }
        return countMono;
    }

    /**
     * Rebuild the samples as 16 bit signed and store them in the output buffer.
     */
    public static int readSamples16bitMono(BlipBuffer blipBuffer, int[] out, int pos, int countMono) {
        final int availMonoSamples = blipBuffer.samplesAvail();
        if (countMono > availMonoSamples)
            countMono = availMonoSamples;
        assert pos == 0;
        assert out.length >= countMono;
        if (countMono > 0) {
            final int[] buf = blipBuffer.buf;
            // Integrate
            int accum = blipBuffer.accum.get();
            int i = 0;
            do {
                accum += buf[i] - (accum >> DC_BLOCK_SHIFT);
                int sample = accum >> ACCUM_FRACTION_BITS;

                // clamp to 16 bits
                sample = clampToShort(sample);

                out[pos++] = sample;
            }
            while (++i < countMono);
            blipBuffer.accum.set(accum);
            blipBuffer.removeSamples(countMono);
        }
        return countMono;
    }

    public static int readSamples8bit(BlipBuffer blipBuffer, byte[] out, int pos, int count) {
        final int avail = blipBuffer.samplesAvail();
        if (count > avail)
            count = avail;

        if (count > 0) {
            // Integrate
            final int[] buf = blipBuffer.buf;
            int accum = blipBuffer.accum.get();
            pos <<= 1;
            int i = 0;
            do {
                accum += buf[i] - (accum >> DC_BLOCK_SHIFT);
                int s = accum >> ACCUM_FRACTION_BITS;
                if ((byte) s != s) {
                    s = clampToByte(s);
//                    System.out.println(s + "->" + val);
                }
                out[pos++] = (byte) s;
            }
            while (++i < count);
            blipBuffer.accum.set(accum);

            blipBuffer.removeSamples(count);
        }
        return count;
    }

    // Reads at most count samples into out at offset pos*2 (2 bytes per sample)
    // and returns number of samples actually read.
    public int readSamples(BlipBuffer blipBuffer, byte[] out, int pos, int count) {
        final int avail = blipBuffer.samplesAvail();
        if (count > avail)
            count = avail;

        if (count > 0) {
            // Integrate
            final int[] buf = blipBuffer.buf;
            int accum = blipBuffer.accum.get();
            pos <<= 1;
            int i = 0;
            do {
                accum += buf[i] - (accum >> DC_BLOCK_SHIFT);
                int s = accum >> ACCUM_FRACTION_BITS;

                // clamp to 16 bits
                s = clampToShort(s);

                // write as little-endian
                out[pos] = (byte) (s >> 8);
                out[pos + 1] = (byte) s;
                pos += 2;
            }
            while (++i < count);
            blipBuffer.accum.set(accum);

            blipBuffer.removeSamples(count);
        }
        return count;
    }
}
