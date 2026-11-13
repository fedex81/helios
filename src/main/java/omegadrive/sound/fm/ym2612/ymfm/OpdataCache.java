package omegadrive.sound.fm.ym2612.ymfm;

import static omegadrive.sound.fm.ym2612.ymfm.Ymfm.EG_STATES;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class OpdataCache {
    public static final int PHASE_STEP_DYNAMIC = 1;

    public int[] waveform;
    public int waveformOffset;
    public int phaseStep;
    public int totalLevel;
    public int blockFreq;
    public int detune;
    public int multiple;
    public int egSustain;
    public final int[] egRate = new int[EG_STATES];
    public int egShift = 0;
}
