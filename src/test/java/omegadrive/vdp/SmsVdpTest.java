package omegadrive.vdp;

import omegadrive.SystemLoader;
import omegadrive.system.Sms;
import omegadrive.util.RegionDetector;
import org.junit.Assert;
import org.junit.Test;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class SmsVdpTest {

    /**
     * 53693175/5/262/342/2 = 59.92hz
     * 53203424/5/313/342/2 = 49.70hz
     * 53693175/5/3/262/59.92 ~ 228.010
     * 53203424/5/3/313/49.70 ~ 228.006
     */
    static final int PAL_FRAME_CYCLES = Sms.VDP_CLK_PAL / (2 * RegionDetector.Region.EUROPE.getFps());
    static final int NTSC_FRAME_CYCLES = Sms.VDP_CLK_NTSC / (2 * RegionDetector.Region.USA.getFps());

    @Test
    public void testNtsc() {
        testInternal(RegionDetector.Region.USA, false);
        testInternal(RegionDetector.Region.JAPAN, false);
    }

    @Test
    public void testPal() {
        testInternal(RegionDetector.Region.EUROPE, true);
    }

    private void testInternal(RegionDetector.Region region, boolean isPal) {
        SmsVdp vdp = new SmsVdp(SystemLoader.SystemType.SMS, region);
        vdp.controlWrite(0b0110_0000);
        vdp.dataWrite((byte) 0x81); //screen on
        SmsVdpTestUtil.runToVint(vdp, true);
        SmsVdpTestUtil.runToVint(vdp, true);
        int cycles = SmsVdpTestUtil.runToVint(vdp, true);
        boolean couldBePal = Math.abs(NTSC_FRAME_CYCLES - cycles) > Math.abs(PAL_FRAME_CYCLES - cycles);
        double hz = 1.0 * (couldBePal ? Sms.MCLK_PAL : Sms.MCLK_NTSC) / (cycles * 10);
        System.out.println(region + " effective frequency hz: " + hz);
        Assert.assertEquals(isPal, couldBePal);
    }
}
