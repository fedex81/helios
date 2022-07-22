package omegadrive.bus.z80;

import omegadrive.SystemLoader;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.TwoButtonsJoypad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.system.Sms;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class SmsBusTest {

    private final static Logger LOG = LogHelper.getLogger(SmsBusTest.class);
    private SmsBus bus;

    @Before
    public void before() {
        SystemProvider sp = Sms.createNewInstance(SystemLoader.SystemType.SMS, DisplayWindow.HEADLESS_INSTANCE);
        IMemoryProvider mem = MemoryProvider.createSmsInstance();
        JoypadProvider jp = new TwoButtonsJoypad();
        bus = new SmsBus() {
            @Override
            protected int getHCount() {
                return ++hCounter;
            }

            @Override
            protected void setupCartHw() {
            }
        };
        bus.attachDevice(sp).attachDevice(mem).attachDevice(jp);
        bus.init();
    }

    @Test
    public void testRegionDetectionDomestic() {
        bus.countryValue = SmsBus.DOMESTIC;
        boolean isDomestic = testRegionDetectInternal();
        Assert.assertTrue(isDomestic);
    }

    @Test
    public void testRegionDetectionOverseas() {
        bus.countryValue = SmsBus.OVERSEAS;
        boolean isDomestic = testRegionDetectInternal();
        Assert.assertFalse(isDomestic);
    }

    @Test
    public void testThDomestic() {
        bus.countryValue = SmsBus.DOMESTIC;
        bus.writeIoPort(0x3F, 0b1111_0101); //Output 1s on both TH lines
        int res = bus.readIoPort(0xDD);
        Assert.assertEquals(res >> 6, 0b00); //levels are low

        bus.writeIoPort(0x3F, 0b1111_1111); //both TH lines as inputs
        res = bus.readIoPort(0xDD);
        Assert.assertEquals(res >> 6, 0b11); //levels are high
    }

    @Test
    public void testPullTriggerLatchesHCounter() {
        bus.countryValue = SmsBus.OVERSEAS;

        bus.writeIoPort(0x3F, 0b1111_1111);
        int prevHCount = bus.hCounter;

        bus.writeIoPort(0x3F, 0b0101_1111); //toggle inputs 1->0
        Assert.assertNotEquals(bus.hCounter, prevHCount); //new hc latched
        prevHCount = bus.hCounter;

        bus.writeIoPort(0x3F, 0b1111_1111); //toggle inputs 0->1
        Assert.assertNotEquals(bus.hCounter, prevHCount); //new hc latched
        prevHCount = bus.hCounter;

        bus.writeIoPort(0x3F, 0b1111_1111); //all inputs high
        prevHCount = bus.hCounter;

        bus.writeIoPort(0x3F, 0b1101_1111); //toggle A input 1->0
        Assert.assertNotEquals(bus.hCounter, prevHCount); //new hc latched
        prevHCount = bus.hCounter;
    }

    //http://www.smspower.org/Development/RegionDetection
    private boolean testRegionDetectInternal() {
        bus.writeIoPort(0x3F, 0b1111_0101); //Output 1s on both TH lines
        int res = bus.readIoPort(0xDD);
        int mask = 0b1100_0000;
        if ((res & mask) != mask) {
            return true; //If the input does not match the output then it's a Japanese system
        }
        bus.writeIoPort(0x3F, 0b0101_0101); //Output 0s on both TH lines
        res = bus.readIoPort(0xDD);
        mask = 0b1100_0000;
        if ((res & mask) != 0) {
            return true; //If the input does not match the output then it's a Japanese system
        }
        bus.writeIoPort(0x3F, 0b1111_1111);
        return false; //not japan
    }

}
