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
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static omegadrive.joypad.JoypadProvider.JoypadType.*;
import static omegadrive.util.Util.th;

/**
 * wwf raw 32x, GreatestHeavyweights, Sf2, sgdk joytest
 * TODO
 * - Decap Attack, 3btn and 6btn ko
 * - GreatestHeavyweights, 3Btn ok, 6btn ko
 */
public class GenesisJoypadTest {

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

    @Test
    public void testWwfRaw32x() {
        testWwfRaw32xInternal(BUTTON_3);
        testWwfRaw32xInternal(BUTTON_6);
    }

    //TODO fix
    @Test
    @Disabled
    public void testGreatestHeavyweights() {
        testGreatestHeavyweightsInternal(BUTTON_3);
        testGreatestHeavyweightsInternal(BUTTON_6);
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
    }

    /**
     * //6 BUTTON sequence
     * Setting ctrlPort1 to 41
     * Setting ctrlPort1 to 41
     * writeDataReg: data 0, MdPadContext{control=41, data=0, readStep=1, high=false, player=1}
     * readDataReg: 33, MdPadContext{control=41, data=0, readStep=1, high=false, player=1}
     * writeDataReg: data 40, MdPadContext{control=41, data=40, readStep=2, high=true, player=1}
     * <p>
     * Setting ctrlPort1 to 40
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=2, high=true, player=1}
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=2, high=true, player=1}
     * readDataReg: ff, MdPadContext{control=40, data=40, readStep=2, high=true, player=1}
     * writeDataReg: data 0, MdPadContext{control=40, data=0, readStep=3, high=false, player=1}
     * readDataReg: 33, MdPadContext{control=40, data=0, readStep=3, high=false, player=1}
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=4, high=true, player=1}
     * <p>
     * Setting ctrlPort1 to 40
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=4, high=true, player=1}
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=4, high=true, player=1}
     * readDataReg: ff, MdPadContext{control=40, data=40, readStep=4, high=true, player=1}
     * writeDataReg: data 0, MdPadContext{control=40, data=0, readStep=5, high=false, player=1}
     * readDataReg: 33, MdPadContext{control=40, data=0, readStep=5, high=false, player=1}
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=6, high=true, player=1}
     * readDataReg: ff, MdPadContext{control=40, data=40, readStep=6, high=true, player=1}
     * writeDataReg: data 0, MdPadContext{control=40, data=0, readStep=7, high=false, player=1}
     * readDataReg: 33, MdPadContext{control=40, data=0, readStep=7, high=false, player=1}
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=8, high=true, player=1}
     * readDataReg: ff, MdPadContext{control=40, data=40, readStep=8, high=true, player=1}
     * writeDataReg: data 0, MdPadContext{control=40, data=0, readStep=0, high=false, player=1}
     * readDataReg: 33, MdPadContext{control=40, data=0, readStep=0, high=false, player=1}
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=1, high=true, player=1}
     * readDataReg: ff, MdPadContext{control=40, data=40, readStep=1, high=true, player=1}
     * writeDataReg: data 0, MdPadContext{control=40, data=0, readStep=2, high=false, player=1}
     * readDataReg: 33, MdPadContext{control=40, data=0, readStep=2, high=false, player=1}
     * writeDataReg: data 40, MdPadContext{control=40, data=40, readStep=3, high=true, player=1}
     */
    private void testGreatestHeavyweightsInternal(JoypadType type) {
        GenesisJoypad j = createBoth(type);

        j.newFrame();
        j.writeControlRegister1(0x41);
        j.writeControlRegister1(0x41);

        j.writeDataRegister1(0);
        Assert.assertEquals(0x33, j.readDataRegister1());
        j.writeDataRegister1(0x40);

        j.writeControlRegister1(0x40);
        j.writeDataRegister1(0x40);
        j.writeDataRegister1(0x40);

        Assert.assertEquals(0xFF, j.readDataRegister1());
        j.writeDataRegister1(0);
        Assert.assertEquals(0x33, j.readDataRegister1());
        j.writeDataRegister1(0x40);

        j.writeControlRegister1(0x40);
        j.writeDataRegister1(0x40);
        j.writeDataRegister1(0x40);
        Assert.assertEquals(0xFF, j.readDataRegister1());
        j.writeDataRegister1(0);
        Assert.assertEquals(0x33, j.readDataRegister1());
        j.writeDataRegister1(0x40);
        j.writeDataRegister1(0x40);
        Assert.assertEquals(0xFF, j.readDataRegister1());
        j.writeDataRegister1(0);
        Assert.assertEquals(type == BUTTON_6 ? 0x30 : 0x33, j.readDataRegister1());
        j.writeDataRegister1(0x40);
        Assert.assertEquals(0xFF, j.readDataRegister1());
        j.writeDataRegister1(0);
        Assert.assertEquals(type == BUTTON_6 ? 0x3f : 0x33, j.readDataRegister1());
        j.writeDataRegister1(0x40);
        j.writeDataRegister1(0x40);
        Assert.assertEquals(0xFF, j.readDataRegister1());
        j.writeDataRegister1(0);
        Assert.assertEquals(0x33, j.readDataRegister1());
        j.writeDataRegister1(0x40);
    }

    private static GenesisJoypad createBoth(JoypadType type) {
        return create(type, type);
    }

    private static GenesisJoypad create(JoypadType p1Type, JoypadType p2Type) {
        GenesisJoypad j = new GenesisJoypad();
        j.setPadSetupChange(InputProvider.PlayerNumber.P1, p1Type.name());
        j.setPadSetupChange(InputProvider.PlayerNumber.P2, p2Type.name());
        j.init();
        return j;
    }
}
