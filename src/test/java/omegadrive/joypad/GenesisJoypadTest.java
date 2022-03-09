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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static omegadrive.util.Util.th;

public class GenesisJoypadTest {

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
    @Test
    public void testWwfRaw32x() {
        int r2;
        GenesisJoypad j = new GenesisJoypad();
        j.init();
        j.writeControlRegister2(0x39);
        System.out.println("ctrl: " + Integer.toBinaryString(0x39));
        j.writeDataRegister2(0);
        r2 = (int) j.readDataRegister2();
        System.out.println("data: " + Integer.toBinaryString(j.ctx2.data));
        System.out.println("r2:   " + Integer.toBinaryString(r2) + "," + th(r2));
        j.writeDataRegister2(r2 | (1 << 3));
        r2 = (int) j.readDataRegister2();
        System.out.println("data: " + Integer.toBinaryString(j.ctx2.data));
        System.out.println("r2:   " + Integer.toBinaryString(r2) + "," + th(r2));
        j.writeDataRegister2(r2 | 1);
        System.out.println("data: " + Integer.toBinaryString(j.ctx2.data));
        r2 = (int) j.readDataRegister2();
        System.out.println("r2:   " + Integer.toBinaryString(r2) + "," + th(r2) + ", " + ((r2 & 6) == 6));
        // bits 0110 are input bits and they are not being driven, assumes pulled-up
        Assertions.assertTrue((r2 & 6) == 6);
        // bits 1001 are output bits and are being set to 1 via the data port, they should be 1
        Assertions.assertTrue((r2 & 9) == 9);
        //bits 11_0000 are output bits and are not set, they should be 0
        Assertions.assertTrue((r2 & 0x30) == 0);
        // bits 1100_0000 are input bits and they are not being driven, assumes pulled-up
        Assertions.assertTrue((r2 & 0xC0) == 0xC0);
    }
}
