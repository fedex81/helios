package omegadrive.system.gb;

import eu.rekawek.coffeegb.Gameboy;
import eu.rekawek.coffeegb.sound.SoundOutput;
import omegadrive.sound.fm.ExternalAudioProvider;
import omegadrive.util.RegionDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;

/**
 * NesSoundWrapper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class GbSoundWrapper extends ExternalAudioProvider implements SoundOutput {

    private static final Logger LOG = LogManager.getLogger(GbSoundWrapper.class.getSimpleName());
    private int tick, divider;

    public GbSoundWrapper(RegionDetector.Region region, AudioFormat audioFormat) {
        super(region, audioFormat);
        this.divider = (int) (Gameboy.TICKS_PER_SEC / audioFormat.getSampleRate());
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
