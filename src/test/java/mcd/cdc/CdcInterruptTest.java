package mcd.cdc;

import mcd.McdDeviceHelper;
import mcd.bus.McdSubInterruptHandler;
import mcd.cdd.Cdd;
import mcd.cdd.ExtendedCueSheet;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.SystemLoader;
import omegadrive.system.MediaSpecHolder;
import omegadrive.util.BufferUtil;
import omegadrive.util.MdRuntimeData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDC;
import static omegadrive.system.SysUtil.RomFileType.ISO;
import static omegadrive.system.SystemProvider.NO_CLOCK;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class CdcInterruptTest {

    static final String TEST_ISO = "src/test/resources/megacd/cdd/test.iso.gz";

    RecordingIntHanlder ih;
    private CdcImpl cdc;

    @BeforeEach
    public void setup() {
        MdRuntimeData.releaseInstance();
        MdRuntimeData.newInstance(SystemLoader.SystemType.MEGACD, NO_CLOCK);
        MdRuntimeData.setAccessTypeExt(BufferUtil.CpuDeviceAccess.M68K);
        MegaCdMemoryContext mc = new MegaCdMemoryContext(MediaSpecHolder.NO_ROM);
        ih = new RecordingIntHanlder();
        cdc = new CdcImpl(mc, ih, McdPcm.create(false));
    }

    @AfterEach
    public void tearDown() {
        MdRuntimeData.releaseInstance();
    }

    @Test
    public void testPollKeepsCdcIntAssertedWhilePending() {
        cdc.getContext().irq.decoder.enable = 1;
        cdc.getContext().irq.decoder.pending = 1;

        cdc.poll();
        Assertions.assertTrue(ih.isAsserted(INT_CDC));

        //still active, subcpu has not serviced it yet
        cdc.poll();
        Assertions.assertTrue(ih.isAsserted(INT_CDC));

        //same
        cdc.poll();
        Assertions.assertTrue(ih.isAsserted(INT_CDC));
    }

    @Test
    public void testPollLowersCdcIntWhenNotPending() {
        cdc.getContext().irq.decoder.enable = 1;
        cdc.getContext().irq.decoder.pending = 1;

        cdc.poll();
        Assertions.assertTrue(ih.isAsserted(INT_CDC));

        //int serviced
        cdc.getContext().irq.decoder.pending = 0;
        cdc.poll();
        Assertions.assertFalse(ih.isAsserted(INT_CDC));

    }

    @Test
    public void testPollIgnoresPendingWhenDisabled() {
        cdc.getContext().irq.decoder.enable = 0;
        cdc.getContext().irq.decoder.pending = 1;

        cdc.poll();
        Assertions.assertFalse(ih.isAsserted(INT_CDC));
    }


    @Test
    public void testDecoderIrqClearedOnlyAfterActivePeriodExpires() {
        McdDeviceHelper.McdLaunchContext lc = McdDeviceHelper.setupDevicesTest();
        Path p = Paths.get(TEST_ISO);
        ExtendedCueSheet cue = new ExtendedCueSheet(p, ISO);
        lc.cdd.tryInsert(cue);

        Cdc c = lc.cdc;
        CdcModel.CdcContext cc = c.getContext();
        cc.decoder.enable = 1;
        cc.control.writeRequest = 0;
        cc.irq.decoder.enable = 1;

        c.cdc_decoder_update(0, 1, Cdd.CddStatus.Playing);
        Assertions.assertEquals(1, cc.irq.decoder.pending);

        c.clearDecoderIrqIfExpired();
        Assertions.assertEquals(1, cc.irq.decoder.pending);

        for (int i = 0; i < Cdc.DECODER_ACTIVE_STEPS; i++) {
            c.step(0);
        }
        c.clearDecoderIrqIfExpired();
        Assertions.assertEquals(1, cc.irq.decoder.pending);

        c.step(0);
        c.clearDecoderIrqIfExpired();
        Assertions.assertEquals(0, cc.irq.decoder.pending);

    }

    static class RecordingIntHanlder implements McdSubInterruptHandler {
        final boolean[] asserted = new boolean[SubCpuInterrupt.values().length];
        int raiseCount, lowerCount;

        @Override
        public void handleInterrupts() {


        }

        @Override
        public void raiseInterrupt(SubCpuInterrupt intp) {
            asserted[intp.ordinal()] = true;
            raiseCount++;
        }

        @Override
        public void lowerInterrupt(SubCpuInterrupt intp) {
            asserted[intp.ordinal()] = false;
            lowerCount++;
        }

        @Override
        public void setIFL2Asserted(boolean asserted) {

        }

        @Override
        public boolean isIFL2Asserted() {
            return false;
        }

        boolean isAsserted(SubCpuInterrupt intp) {
            return asserted[intp.ordinal()];
        }
    }
}
