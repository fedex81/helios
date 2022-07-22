package omegadrive.system.gb;

import eu.rekawek.coffeegb.Gameboy;
import eu.rekawek.coffeegb.sound.SoundOutput;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.GenericAudioProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

/**
 * NesSoundWrapper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class GbSoundWrapper extends GenericAudioProvider implements SoundOutput {

    private static final Logger LOG = LogHelper.getLogger(GbSoundWrapper.class.getSimpleName());
    public static AudioFormat gbAudioFormat = new AudioFormat(SoundProvider.SAMPLE_RATE_HZ, 8, 2, true, false);
    private final int divider;
    private int tick;

    public GbSoundWrapper(RegionDetector.Region region) {
        super(gbAudioFormat);
        this.divider = (int) (Gameboy.TICKS_PER_SEC / gbAudioFormat.getSampleRate());
    }

    @Override
    public void play(int left, int right) {
        if (tick++ != 0) {
            tick %= divider;
            return;
        }
        addStereoSample(left, right);
    }
}
