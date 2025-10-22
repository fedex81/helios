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
                accumL += deltaBufL[i] - (accumL >> 9);
                accumR += deltaBufR[i] - (accumR >> 9);
                int sl = accumL >> 15;
                int sr = accumR >> 15;

                // clamp to 16 bits
                if ((short) sl != sl)
                    sl = clampToShort(sl);
                if ((short) sr != sr)
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
                accum += deltaBuf[i] - (accum >> 9);
                int s = accum >> 15;

                // clamp to 16 bits
                if ((short) s != s)
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
                accum += buf[i] - (accum >> 9);
                int sample = accum >> 15;

                // clamp to 16 bits
                if ((short) sample != sample)
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
                accum += buf[i] - (accum >> 9);
                int s = accum >> 15;
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
                accum += buf[i] - (accum >> 9);
                int s = accum >> 15;

                // clamp to 16 bits
                if ((short) s != s)
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
