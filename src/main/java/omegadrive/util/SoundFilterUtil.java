package omegadrive.util;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class SoundFilterUtil {

    public static int interpolateInterleavedStereo(int[] src, int[] dest) {
        return interpolateInterleavedStereo(src, src.length, dest, dest.length);
    }

    public static int interpolateInterleavedStereo(int[] src, int[] dest, int destSize) {
        return interpolateInterleavedStereo(src, src.length, dest, destSize);
    }

    /**
     * Performs high-fidelity, copy-less fractional linear interpolation for interleaved stereo audio frames.
     * Maps the source chunk smoothly onto a dynamically adjusted target size within the destination array.
     *
     * @param src      The source array containing interleaved stereo samples [L, R, L, R...]
     * @param srcSize  The total number of samples inside the source chunk
     * @param dest     The destination array where resampled stereo data will be written starting at index 0
     * @param destSize The exact number of samples (not frames) to write into the destination array
     * @return The total number of samples written to the destination array (equals destSize)
     */
    public static int interpolateInterleavedStereo(int[] src, int srcSize, int[] dest, int destSize) {
        if (src == null || srcSize < 2 || dest == null || destSize < 2) {
            return 0;
        }

        // Each stereo frame consists of 2 samples (Left + Right)
        int srcFrames = srcSize / 2;
        int destFrames = destSize / 2;

        // Ensure we don't overrun the physical memory boundaries of the destination array
        if (destFrames * 2 > dest.length) {
            destFrames = dest.length / 2;
        }

        // Dynamic scale factor calculation handles gentle fraction adjustments
        double scaleFactor = (double) destFrames / (double) srcFrames;

        for (int i = 0; i < destFrames; i++) {
            double posInOriginal = (double) i / scaleFactor;
            int idx0 = (int) Math.floor(posInOriginal);

            // Calculate starting index for the current stereo frame in source
            int srcLeft0 = idx0 * 2;

            if (idx0 >= srcFrames - 1) {
                // Smooth boundary clamp: use the final valid frame of the source block
                int lastFrameIdx = (srcFrames - 1) * 2;
                dest[i * 2] = src[lastFrameIdx];     // Left
                dest[i * 2 + 1] = src[lastFrameIdx + 1]; // Right
            } else {
                int idx1 = idx0 + 1;
                if (idx1 >= srcFrames) {
                    idx1 = srcFrames - 1;
                }
                int srcLeft1 = idx1 * 2;

                // Promote integers to floats natively for audio blending math
                float left0 = (float) src[srcLeft0];
                float left1 = (float) src[srcLeft1];
                float right0 = (float) src[srcLeft0 + 1];
                float right1 = (float) src[srcLeft1 + 1];

                float fraction = (float) (posInOriginal - idx0);
                float oneMinusFraction = 1.0f - fraction;

                // Linearly interpolate Left and Right streams independently
                float interpLeft = (left0 * oneMinusFraction) + (left1 * fraction);
                float interpRight = (right0 * oneMinusFraction) + (right1 * fraction);

                // Commit back to the destination array as standard integers
                dest[i * 2] = (int) interpLeft;
                dest[i * 2 + 1] = (int) interpRight;
            }
        }

        return destFrames * 2;
    }

    /**
     * <p>
     * The core equation used in your DC blocker loop is:
     * <pre>
     * y[n] = x[n] - x[n-1] + alpha * y[n-1]
     * </pre>
     * </p>
     *
     * <p>Where:</p>
     * <ul>
     *   <li><b>x[n]</b> is the current input sample.</li>
     *   <li><b>x[n-1]</b> is the previous input sample.</li>
     *   <li><b>y[n-1]</b> is the previous filter output sample.</li>
     *   <li><b>alpha</b> acts as a "leak" factor.</li>
     * </ul>
     *
     * <p>
     * If {@code alpha = 1.0}, the filter has perfect memory, turning it into a pure
     * differentiator that completely blocks DC but causes instability in integer math.
     * If {@code alpha = 0.0}, the filter has no memory, completely disabling the
     * feedback loop. Therefore, {@code alpha} is practically always set to a value
     * between 0.90 and 0.99.
     * </p>
     */
    private static final double alpha = 0.95;


    /**
     * Applies a combined DC blocker (high-pass) and a single-pole low-pass filter (LPF)
     * to a stereo audio buffer.
     * <p>
     * The method processes samples in two distinct stages to prevent filter cross-contamination:
     * </p>
     * <ol>
     *   <li>
     *     <b>DC Blocker Stage:</b> Evaluated using isolated tracking variables to maintain true historical state.
     *     <pre>y[n] = x[n] - x[n-1] + &alpha; &middot; y[n-1]</pre>
     *   </li>
     *   <li>
     *     <b>Low Pass Filter Stage:</b> Blends the newly calculated, raw DC-blocked sample with the
     *     previous low-pass output sample via a 1-bit right shift (average).
     *     <pre>out[n] = (dcBlocked[n] + out[n-2]) &gt;&gt; 1</pre>
     *   </li>
     * </ol>
     *
     */
    public static void dcBlockerLpf(int[] in, int[] out, DcBlockLpfHistory fh, int len) {
        // 1. Load high-precision history for the DC Blocker
        int lastInL = fh.lastInL;
        int lastInR = fh.lastInR;
        int lastOutL = fh.lastOutL;
        int lastOutR = fh.lastOutR;

        // 2. Load history for the LPF
        int lastLpfL = fh.lastLpfL;
        int lastLpfR = fh.lastLpfR;

        for (int i = 0; i < len; i += 2) {
            // High-precision DC Blocker calculation
            int dcBlockedL = (int) (in[i] - lastInL + (lastOutL * alpha));
            int dcBlockedR = (int) (in[i + 1] - lastInR + (lastOutR * alpha));

            // Update DC history for next iteration
            lastInL = in[i];
            lastInR = in[i + 1];
            lastOutL = dcBlockedL;
            lastOutR = dcBlockedR;

            // Low Pass Filter Stage: Perfectly continuous blend
            lastLpfL = (dcBlockedL + lastLpfL) >> 1;
            lastLpfR = (dcBlockedR + lastLpfR) >> 1;

            out[i] = lastLpfL;
            out[i + 1] = lastLpfR;
        }

        // Pack back into the history container for the next chunk
        fh.lastInL = lastInL;
        fh.lastInR = lastInR;
        fh.lastOutL = lastOutL;
        fh.lastOutR = lastOutR;

        fh.lastLpfL = lastLpfL;
        fh.lastLpfR = lastLpfR;
    }

    /**
     * Applies a 1st-order Low-Pass Filter (Exponential Moving Average) to an interleaved stereo stream.
     * Rearranged mathematically to require only one multiplication per sample.
     * Formula: p[n] = p[n-1] + alpha * (pi[n] - p[n-1])
     *
     * @param in       The source array containing interleaved stereo samples [L, R, L, R...]
     * @param out      The destination array where filtered stereo data will be written
     * @param h        The history container preserving state continuity across chunks
     * @param alphaLPF The filter coefficient (0.0 to 1.0). Lower values create a stronger filter effect.
     * @param len      The total number of samples to process (must be a multiple of 2)
     */
    public static void lowPassFilter(int[] in, int[] out, LpfHistory h, float alphaLPF, int len) {
        // Unpack history into local registers for performance
        int lastLpfL = h.lastLpfL;
        int lastLpfR = h.lastLpfR;

        for (int i = 0; i < len; i += 2) {
            // Apply exponential moving average to Left and Right channels
            lastLpfL += alphaLPF * (in[i] - lastLpfL);
            lastLpfR += alphaLPF * (in[i + 1] - lastLpfR);

            // Store back as integers
            out[i] = lastLpfL;
            out[i + 1] = lastLpfR;
        }

        // Pack values back into the container for the next block boundary
        h.lastLpfL = lastLpfL;
        h.lastLpfR = lastLpfR;
    }

    public static class LpfHistory {
        // --- Low Pass Filter Integer States ---
        public int lastLpfL = 0;
        public int lastLpfR = 0;

        public void reset() {
            this.lastLpfL = 0;
            this.lastLpfR = 0;
        }
    }

    public static final class DcBlockLpfHistory extends LpfHistory {

        // --- DC Blocker High-Precision Floating States ---
        public int lastInL = 0;
        public int lastInR = 0;
        public int lastOutL = 0;
        public int lastOutR = 0;

        public void reset() {
            super.reset();
            this.lastInL = 0;
            this.lastInR = 0;
            this.lastOutL = 0;
            this.lastOutR = 0;
        }
    }


}
