package omegadrive.sound.fm.ym2612.ymfm;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class Ymfm {


    public static final int EG_DEPRESS = 0;
    public static final int EG_ATTACK = 1;
    public static final int EG_DECAY = 2;
    public static final int EG_SUSTAIN = 3;
    public static final int EG_RELEASE = 4;
    public static final int EG_REVERB = 5;
    public static final int EG_STATES = 6;

    public static final int ACCESS_IO = 0;
    public static final int ACCESS_ADPCM_A = 1;
    public static final int ACCESS_ADPCM_B = 2;
    public static final int ACCESS_PCM = 3;
    public static final int ACCESS_CLASSES = 4;

    public static final int KEY_ON_NORMAL = 0;
    public static final int KEY_ON_RHYTHM = 1;
    public static final int KEY_ON_CSM = 2;


    public static int bitfield(int value, int start, int len) {
        return (value >>> start) & ((1 << len) - 1);
    }

    public static int bitfield(int value, int start) {
        return (value >>> start) & 1;
    }

    public static int clamp(int value, int minval, int maxval) {
        if (value < minval)
            return minval;
        if (value > maxval)
            return maxval;
        return value;
    }
}
