/*
 * GenesisJoypad
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/10/19 17:32
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.joypad;

import omegadrive.input.InputProvider;
import omegadrive.joypad.JoypadProvider.JoypadType;
import omegadrive.system.SystemProvider;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static omegadrive.joypad.JoypadProvider.JoypadType.*;
import static omegadrive.system.SystemProvider.NO_CLOCK;
import static omegadrive.util.Util.th;

/**
 * wwf raw 32x, GreatestHeavyweights, Sf2, sgdk joytest,
 * Decap Attack, Asterix
 */
public class GenesisJoypadTest {

    private SystemProvider.SystemClock clock;
    private int cycleCounter = 0;

    @BeforeEach
    public void beforeEach() {
        clock = new SystemProvider.SystemClock() {
            @Override
            public int getCycleCounter() {
                return cycleCounter;
            }

            @Override
            public long getFrameCounter() {
                return 0;
            }
        };
    }

    @Test
    public void testInitAndReset() {
        testInitAndResetInternal(BUTTON_3);
        testInitAndResetInternal(BUTTON_6);
    }

    @Test
    public void testNewFrame() {
        testNewFrameInternal(BUTTON_3);
        testNewFrameInternal(BUTTON_6);
    }

    @Test
    public void testInitDataPortValue() {
        testInitDataPortValueInternal(BUTTON_3);
        testInitDataPortValueInternal(BUTTON_6);
    }

    @Test
    public void testDisabled() {
        testDisabledInternal(BUTTON_3);
        testDisabledInternal(BUTTON_6);
    }

    /**
     * 0 TH = 1 : ?1CBRLDU    3-button pad return value
     * 1 TH = 0 : ?0SA00DU    3-button pad return value (D3,D2 are forced to 0, indicates the presence of a controller)
     * 2 TH = 1 : ?1CBRLDU    3-button pad return value
     * 3 TH = 0 : ?0SA00DU    3-button pad return value
     * 4 TH = 1 : ?1CBRLDU    3-button pad return value
     * 5 TH = 0 : ?0SA0000    D3-0 are forced to '0' (indicate 6 buttons)
     * 6 TH = 1 : ?1CBMXYZ    Extra buttons returned in D3-0
     * 7 TH = 0 : ?0SA1111    D3-0 are forced to '1'
     * (0 TH = 1 : ?1CBRLDU    3-button pad return value)
     */
    @Test
    public void testSimpleSequence() {
        int[] sequence3 = {0x7f, 0x33, 0x7f, 0x33, 0x7f, 0x33, 0x7f, 0x33};
        int[] sequence6 = {0x7f, 0x33, 0x7f, 0x33, 0x7f, 0x30, 0x7f, 0x3f};
        testSimpleSequenceInternal(BUTTON_3, sequence3);
        testSimpleSequenceInternal(BUTTON_6, sequence6);
    }

    private void testSimpleSequenceInternal(JoypadType type, int[] sequence) {
        System.out.println(type);
        GenesisJoypad j = createBoth(type, clock);
        Assert.assertEquals(0, j.ctx1.control);

        int thControl = 0x40;
        int res, thHigh = 0x40, thLow = 0;
        j.writeControlRegister1(thControl);
        j.newFrame();
        for (int i = 0; i < 2; i++) {
            //idle state thHigh
            res = j.readDataRegister1();
            Assert.assertEquals(thHigh, res & thHigh);
            Assert.assertEquals(sequence[0], res);
            //no button pressed
            int cnt = 1;
            do {
                System.out.println(cnt);
                int thVal = (cnt & 1) == 0 ? thHigh : thLow;
                j.writeDataRegister1(thVal);
                res = j.readDataRegister1();
                Assert.assertEquals(thVal, res & thVal);
                Assert.assertEquals(sequence[cnt], res);
                cnt++;
            } while (cnt < 8);
            j.newFrame();
        }
    }

    @Test
    public void testWwfRaw32x() {
        testWwfRaw32xInternal(BUTTON_3);
        testWwfRaw32xInternal(BUTTON_6);
    }


    @Test
    public void testGreatestHeavyweights() {
        testGreatestHeavyweightsInternal(BUTTON_3, new int[]{0x33, 0x33, 0x33});
        testGreatestHeavyweightsInternal(BUTTON_6, new int[]{0x30, 0x3f, 0x33});
    }

    private void testInitAndResetInternal(JoypadType type) {
        GenesisJoypad j = createBoth(type);

        Assert.assertEquals(0, j.ctx1.control);
        Assert.assertEquals(0, j.ctx2.control);

        j.writeControlRegister1(0x40);

        j.reset();

        Assert.assertEquals(0x40, j.ctx1.control);
        Assert.assertEquals(0, j.ctx2.control);
    }

    private void testNewFrameInternal(JoypadType type) {
        GenesisJoypad j = createBoth(type);
        j.newFrame();
        //transition 0->1
        j.writeControlRegister1(0x40);
        j.writeDataRegister1(0);
        j.writeDataRegister1(0x40);
        if (type == BUTTON_6) {
            Assert.assertNotEquals(0, j.ctx1.readStep);
        }
        j.newFrame();
        Assert.assertEquals(0, j.ctx1.readStep);
    }

    //Samurai Spirit, Power Instinct
    private void testInitDataPortValueInternal(JoypadType type) {
        GenesisJoypad j = createBoth(type);

        Assertions.assertEquals(0x40, j.ctx1.data);
        Assertions.assertEquals(0x40, j.ctx2.data);
        Assertions.assertEquals(0x40, j.ctx3.data);

        j.newFrame();

        j.reset();
        Assertions.assertEquals(0x40, j.ctx1.data);
        Assertions.assertEquals(0x40, j.ctx2.data);
        Assertions.assertEquals(0x40, j.ctx3.data);
    }

    private void testDisabledInternal(JoypadType type) {
        GenesisJoypad j = createBoth(type);
        j.writeControlRegister1(0x40);
        j.writeControlRegister2(0x40);
        j.writeDataRegister1(0x40);
        j.writeDataRegister2(0x40);
        j.writeDataRegister1(0);
        j.writeDataRegister2(0);
        Assertions.assertEquals(0x33, j.readDataRegister1());
        Assertions.assertEquals(0x33, j.readDataRegister2());

        j.setPadSetupChange(InputProvider.PlayerNumber.P2, NONE.name());
        Assertions.assertEquals(0x33, j.readDataRegister1());
        Assertions.assertEquals(0xFF, j.readDataRegister2());

        j.setPadSetupChange(InputProvider.PlayerNumber.P1, NONE.name());
        Assertions.assertEquals(0xFF, j.readDataRegister1());
        Assertions.assertEquals(0xFF, j.readDataRegister2());

        j.setPadSetupChange(InputProvider.PlayerNumber.P1, type.name());
        Assertions.assertEquals(0x33, j.readDataRegister1());
        Assertions.assertEquals(0xFF, j.readDataRegister2());

        j.setPadSetupChange(InputProvider.PlayerNumber.P2, type.name());
        Assertions.assertEquals(0x33, j.readDataRegister1());
        Assertions.assertEquals(0x33, j.readDataRegister2());
    }

    /**
     * 00880ea0   13fc 0000 00a10009       move.b   #$0000,$00a10009 [NEW] //Setting ctrlPort1 to 0
     * 00880ea8   13fc 0039 00a1000b       move.b   #$0039,$00a1000b [NEW] //Setting ctrlPort2 to 39
     * 00880eb0   13fc 0000 00a10005       move.b   #$0000,$00a10005 [NEW]
     * 00880eb8   08f9 0003 00a10005       bset     #$3,$00a10005 [NEW]
     * 00880ec0   08f9 0000 00a10005       bset     #$0,$00a10005 [NEW]
     * 00880ec8   1039 00a10005            move.b   $00a10005,d0 [NEW]
     * 00880ece   0200 0006                andi.b   #$06,d0 [NEW]
     * 00880ed2   0c00 0006                cmpi.b   #$06,d0 [NEW]
     * 00880ed6   6700 0004               beq.w    $00880edc [NEW]    <-- should be taking this branch
     * 00880eda   4e75                    rts [NEW]
     * 00880c6c   6000 05ec               bra.w    $0088125a [NEW]
     * 0088125a   6500 0572               bcs.w    $008817ce [NEW]
     * 008817ce   46fc 2700                move     #$2700,sr [NEW]
     * 008817d2   6000 fffffffe               bra.w    $008817d2 [NEW]
     */
    private void testWwfRaw32xInternal(JoypadType type) {
        GenesisJoypad.WWF32X_HACK = true;
        int r2;
        GenesisJoypad j = createBoth(type);
        j.writeControlRegister2(0x39);
        System.out.println("ctrl:   " + Integer.toBinaryString(0x39));
        j.writeDataRegister2(0);
        r2 = j.readDataRegister2();
        System.out.println("data:  " + Integer.toBinaryString(j.ctx2.data));
        System.out.println("r2:   " + Integer.toBinaryString(r2) + "," + th(r2));
        j.writeDataRegister2(r2 | (1 << 3));
        r2 = j.readDataRegister2();
        System.out.println("data:  " + Integer.toBinaryString(j.ctx2.data));
        System.out.println("r2:   " + Integer.toBinaryString(r2) + "," + th(r2));
        j.writeDataRegister2(r2 | 1);
        System.out.println("data:  " + Integer.toBinaryString(j.ctx2.data));
        r2 = j.readDataRegister2();
        System.out.println("r2:   " + Integer.toBinaryString(r2) + "," + th(r2) + ", " + ((r2 & 6) == 6));
        // bits 0110 are input bits and they are not being driven, assumes pulled-up
        Assertions.assertTrue((r2 & 6) == 6);
        // bits 1001 are output bits and are being set to 1 via the data port, they should be 1
        Assertions.assertTrue((r2 & 9) == 9);
        //bits 11_0000 are output bits and are not set, they should be 0
        Assertions.assertTrue((r2 & 0x30) == 0);
        // bits 1100_0000 are input bits and they are not being driven, assumes pulled-up
        Assertions.assertEquals(0xC0, r2 & 0xC0);
        GenesisJoypad.WWF32X_HACK = false;
    }

    /**
     * new frame
     * writeCtrlReg: data 40, MdPadContext{control=40, data=40, readStep=0, player=1}
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=0, player=1}
     * <p>
     * wait for a long time, resets readStep to 0
     * <p>
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=0, player=1}
     * readDataReg: data 33, MdPadContext{control=40, data=40, readStep=0, player=1}
     */
    private void testGreatestHeavyweightsInternal(JoypadType type, int[] expect) {
        GenesisJoypad j = createBoth(type, clock);

        j.newFrame();
        j.writeControlRegister1(0x40);
        j.writeDataRegister1(0x40);

        //wait
        cycleCounter += 16_000;

        j.writeDataRegister1(0x40);
        Assert.assertEquals(0x7f, j.readDataRegister1());

        j.writeDataRegister1(0);
        Assert.assertEquals(0x33, j.readDataRegister1());

        j.writeDataRegister1(0x40);
        Assert.assertEquals(0x7f, j.readDataRegister1());

        j.writeDataRegister1(0);
        Assert.assertEquals(0x33, j.readDataRegister1());

        j.writeDataRegister1(0x40);
        Assert.assertEquals(0x7f, j.readDataRegister1());

        j.writeDataRegister1(0);
        Assert.assertEquals(expect[0], j.readDataRegister1());

        j.writeDataRegister1(0x40);
        Assert.assertEquals(0x7f, j.readDataRegister1());

        j.writeDataRegister1(0);
        Assert.assertEquals(expect[1], j.readDataRegister1());

        j.writeDataRegister1(0x40);
        Assert.assertEquals(0x7f, j.readDataRegister1());

        j.writeDataRegister1(0);
        Assert.assertEquals(expect[2], j.readDataRegister1());
    }


    /**
     * Decap' Attack controller code is buggy and wrongly writes 0x00 to I/O CTRL port before acquiring START & A buttons
     * (instead of just writing to I/O DATA port and leave CTRL port untouched like all other games do), which causes
     * TH pin to be configured as an input and consequently pulled HIGH (as verified on real hardware and emulated
     * by Genesis Plus GX since 1cf6882), causing the controller to report B & C buttons status on TL/TR pins
     * instead of START & A buttons.
     *
     * It works on real hardware because there is some delay before TH internal state actually changes from LOW to HIGH
     * when TH pin direction is switched to input and I was able to verify with a test ROM that START & A buttons
     * remain accessible on I/O DATA port for a few cycles before B & C buttons start being reported instead.
     * That game accidentally takes advantage of this because it reads the I/O DATA port immediately without any delay.
     */
    /**
     * new frame
     * writeDataReg: data 0, MdPadContext{control=40, data=0, readStep=1, player=1}
     * writeCtrlReg: data 0, MdPadContext{control=40, data=0, readStep=1, player=1}
     * readDataReg: data 33, MdPadContext{control=0, data=0, readStep=1, player=1}
     * <p>
     * writeDataReg: data 40, MdPadContext{control=0, data=40, readStep=2, player=1}
     * writeCtrlReg: data 40, MdPadContext{control=0, data=40, readStep=2, player=1}
     * readDataReg: data 3f, MdPadContext{control=40, data=40, readStep=2, player=1}
     * <p>
     * writeCtrlReg: data 40, MdPadContext{control=40, data=40, readStep=2, player=1}
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=2, player=1}
     * readDataReg: data 7f, MdPadContext{control=40, data=40, readStep=2, player=1}
     * <p>
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=2, player=1}
     * readDataReg: data 7f, MdPadContext{control=40, data=40, readStep=2, player=1}
     * <p>
     * writeDataReg: data 0, MdPadContext{control=40, data=0, readStep=3, player=1}
     * readDataReg: data 30, MdPadContext{control=40, data=0, readStep=3, player=1}
     * <p>
     * writeDataReg: data 0, MdPadContext{control=40, data=0, readStep=3, player=1}
     * readDataReg: data 30, MdPadContext{control=40, data=0, readStep=3, player=1}
     * <p>
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=4, player=1}
     * new frame
     */
    @Test
    public void testDecapAttack() {
        testDecapAttackInternal(BUTTON_3);
        testDecapAttackInternal(BUTTON_6);
    }

    private void testDecapAttackInternal(JoypadType type) {
        GenesisJoypad j = createBoth(type);
        Assert.assertEquals(0, j.ctx1.control);
        j.writeControlRegister1(0x40);

        j.newFrame();
        //due to delays this write will cause the data register to change after the next read is performed
        j.writeDataRegister1(0);
        j.writeControlRegister1(0);
        Assert.assertEquals(0x33, j.readDataRegister1());

        j.writeDataRegister1(0x40);
        //reset ctrlReg to 0x40, all is fine from now on
        j.writeControlRegister1(0x40);
        Assert.assertEquals(0x3F, j.readDataRegister1());

        j.writeControlRegister1(0x40);
        j.writeDataRegister1(0x40);
        Assert.assertEquals(0x7F, j.readDataRegister1());
        j.writeDataRegister1(0x40);
        Assert.assertEquals(0x7F, j.readDataRegister1());

        j.writeDataRegister1(0);
        Assert.assertEquals(0x33, j.readDataRegister1());

        j.writeDataRegister1(0);
        Assert.assertEquals(0x33, j.readDataRegister1());

        j.writeDataRegister1(0x40);
    }

    private static GenesisJoypad createBoth(JoypadType type) {
        return create(type, type, NO_CLOCK);
    }

    private static GenesisJoypad createBoth(JoypadType type, SystemProvider.SystemClock clock) {
        return create(type, type, clock);
    }

    private static GenesisJoypad create(JoypadType p1Type, JoypadType p2Type, SystemProvider.SystemClock clock) {
        GenesisJoypad j = new GenesisJoypad(clock);
        j.setPadSetupChange(InputProvider.PlayerNumber.P1, p1Type.name());
        j.setPadSetupChange(InputProvider.PlayerNumber.P2, p2Type.name());
        j.init();
        return j;
    }
}
