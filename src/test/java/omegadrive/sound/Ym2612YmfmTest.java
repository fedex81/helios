package omegadrive.sound;

import omegadrive.sound.fm.ym2612.ymfm.Ym2612;
import omegadrive.sound.fm.ym2612.ymfm.YmfmInterface;
import omegadrive.sound.fm.ym2612.ymfm.YmfmOutput;
import omegadrive.sound.fm.ym2612.ymfm.YmfmSavedState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class Ym2612YmfmTest {

    private static final int FM_CLOCK = 7670453;

    private static Ym2612 newChip() {
        Ym2612 chip = new Ym2612(new YmfmInterface());
        chip.reset();
        return chip;
    }

    private static void w(Ym2612 chip, int reg, int val) {
        chip.write(0, reg);
        chip.write(1, val);
    }

    private static int generatePeak(Ym2612 chip, int samples) {
        YmfmOutput out = new YmfmOutput();
        int peak = 0;
        for (int i = 0; i < samples; i++) {
            chip.generate(out);
            peak = Math.max(peak, Math.abs(out.data[0]));
            peak = Math.max(peak, Math.abs(out.data[1]));
        }
        return peak;
    }


    @Test
    public void testNativeSampleRate() {
        Ym2612 chip = newChip();
        Assertions.assertEquals(FM_CLOCK / 144, chip.sampleRate(FM_CLOCK));
    }

    @Test
    public void testQuietBeforeKeyOn() {
        Ym2612 chip = newChip();
        int peak = generatePeak(chip, 2000);
        Assertions.assertTrue(peak < 1000);
    }

    @Test
    public void testAudibleAfterKeyOn() {
        Ym2612 chip = newChip();

        w(chip, 0x22, 0);
        w(chip, 0x27, 0);
        w(chip, 0x28, 0);

        w(chip, 0xb0, 7);
        w(chip, 0xb4, 0xc0);

        for (int op = 0; op < 4; op++) {
            int o = op * 4;
            w(chip, 0x30 + o, 0);
            w(chip, 0x40 + o, 0x10);
            w(chip, 0x50 + o, 0x1f);
            w(chip, 0x60 + o, 0);
            w(chip, 0x70 + o, 0);
            w(chip, 0x80 + o, 0xf);
            w(chip, 0x90 + o, 0);
        }

        w(chip, 0xa4, 0x22);
        w(chip, 0xa0, 0x69);

        w(chip, 0x28, 0xf0);

        int peak = generatePeak(chip, chip.sampleRate(FM_CLOCK) / 100);
        Assertions.assertTrue(peak > 0);
    }

    @Test
    public void testRunningTimerFirstAfterStateRestore() {
        Ym2612 chip = newChip();
        w(chip, 0x24, 0xfa); //timerA = 1000 -> ovf after 24 samples
        w(chip, 0x25, 0);
        w(chip, 0x27, 0x15);
        generatePeak(chip, 5);

        YmfmSavedState save = YmfmSavedState.forSaving();
        chip.saveRestore(save);
        Ym2612 restored = newChip();
        restored.saveRestore(YmfmSavedState.forRestoring(save.toByteArray()));
        generatePeak(restored, 18); //5+18
        //TimerA ovf should NOT be set
        Assertions.assertEquals(0, restored.readStatus() & 1);
        generatePeak(restored, 1); //23+1
        //TimerA ovf should be set (24 samples)
        Assertions.assertEquals(1, restored.readStatus() & 1);
    }
}
