package omegadrive.util;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class SoundFilterUtil {

    //dc blocker alpha
    private static final double alpha = 0.95;

    /**
     * DC blocker + low pass filter
     */
    public static void dcBlockerLpf(int[] in, int[] out, int[] prevLR, int len) {
        out[0] = prevLR[0];
        out[1] = prevLR[1];
        for (int i = 2; i < len; i += 2) {
            out[i] = (int) (in[i] - in[i - 2] + out[i - 2] * alpha); //left
            out[i + 1] = (int) (in[i + 1] - in[i - 1] + out[i - 1] * alpha); //right
            out[i] = (out[i] + out[i - 2]) >> 1; //lpf
            out[i + 1] = (out[i + 1] + out[i - 1]) >> 1;
        }
        prevLR[0] = out[len - 2];
        prevLR[1] = out[len - 1];
    }

    /**
     * DC blocker + low pass filter
     */
    public static int dcBlockerLpfMono(int[] in, int[] out, int prevSample, int len) {
        out[0] = prevSample;
        for (int i = 1; i < len; i++) {
            out[i] = (int) (in[i] - in[i - 1] + out[i - 1] * alpha);
            out[i] = (out[i] + out[i - 1]) >> 1; //lpf
        }
        return out[len - 1];
    }
}
