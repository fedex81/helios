package omegadrive.sound.fm.ym2612.ymfm;

import java.util.Arrays;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class YmfmOutput {
    public static final int NUM_OUTPUTS = 2;

    public final int[] data = new int[NUM_OUTPUTS];

    public YmfmOutput clear() {
        Arrays.fill(data, 0);
        return this;
    }
}
