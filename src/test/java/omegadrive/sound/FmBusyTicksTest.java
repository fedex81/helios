package omegadrive.sound;

import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.MdFmProvider.FmCore;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke;
import omegadrive.sound.fm.ym2612.ymfm.Ym2612Ymfm;
import omegadrive.sound.javasound.AbstractSoundManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static omegadrive.sound.fm.MdFmProvider.FM_ADDRESS_PORT0;
import static omegadrive.sound.fm.MdFmProvider.FM_DATA_PORT0;
import static omegadrive.sound.fm.MdFmProvider.FmCore.NUKE;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class FmBusyTicksTest {

    private static final int FM_CLOCK = 7_670_453;
    private static final int TICKS_PER_SAMPLE = 24;
    private static final int LAST_BUSY_TICK = 33;
    private static final int FIRST_CLEAR_TICK = 34; //Nuke: 32 FM ticks + 2 pipeline

    private static MdFmProvider create(FmCore core) {
        var af = AbstractSoundManager.audioFormat;
        return core == NUKE ? new Ym2612Nuke(af, FM_CLOCK) : new Ym2612Ymfm(af, FM_CLOCK);
    }

    private static void advanceTicks(FmCore core, MdFmProvider fm, int ticks) {
        int steps = core == NUKE ? ticks : ticks / TICKS_PER_SAMPLE;
        for (int i = 0; i < steps; i++) {
            fm.step();
        }
    }

    private boolean busy(MdFmProvider fm) {
        return (fm.read() & MdFmProvider.FM_STATUS_BUSY_BIT_MASK) != 0;
    }

    private static MdFmProvider writeAndAdvance(FmCore core, int ticksAfterWrite) {
        MdFmProvider fm = create(core);
        fm.reset();
        advanceTicks(core, fm, 4 * TICKS_PER_SAMPLE);
        fm.write(FM_ADDRESS_PORT0, 0x30);
        fm.write(FM_DATA_PORT0, 0);
        advanceTicks(core, fm, ticksAfterWrite);
        return fm;
    }

    @ParameterizedTest
    @EnumSource(FmCore.class)
    public void stillBusyUntilLastBusyTick(FmCore core) {
        Assertions.assertTrue(busy(writeAndAdvance(core, LAST_BUSY_TICK)));
    }

    @ParameterizedTest
    @EnumSource(FmCore.class)
    public void clearFromFirstClearTick(FmCore core) {
        if (core == NUKE) {
            Assertions.assertTrue(busy(writeAndAdvance(core, LAST_BUSY_TICK)));
            Assertions.assertFalse(busy(writeAndAdvance(core, FIRST_CLEAR_TICK)));
        } else {
            //NOTE: Ymfm does not clear the busy flag after 32 FM ticks, but after 48 FM ticks
            //due to having a step granularity of 24 FM ticks.
            int ymfmLastBusyTick = 47;
            int ymfmFirstClearTick = ymfmLastBusyTick + 1;
            Assertions.assertTrue(busy(writeAndAdvance(core, LAST_BUSY_TICK)));
            Assertions.assertTrue(busy(writeAndAdvance(core, FIRST_CLEAR_TICK)));
            Assertions.assertTrue(busy(writeAndAdvance(core, ymfmLastBusyTick)));
            Assertions.assertFalse(busy(writeAndAdvance(core, ymfmFirstClearTick)));
        }
    }
}
