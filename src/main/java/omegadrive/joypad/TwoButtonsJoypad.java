/*
 * TwoButtonsJoypad
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


import com.google.common.collect.Maps;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import static omegadrive.input.InputProvider.PlayerNumber;
import static omegadrive.joypad.JoypadProvider.JoypadButton.*;

/**
 * * 0xDC / 0xC0 : Joypad Port 1 (read-only)
 * * bit 0 : Joypad 1 Up
 * * bit 1 : Joypad 1 Down
 * * bit 2 : Joypad 1 Left
 * * bit 3 : Joypad 1 Right
 * * bit 4 : Joypad 1 Button 1
 * * bit 5 : Joypad 1 Button 2
 * * bit 6 : Joypad 2 Up
 * * bit 7 : Joypad 2 Down
 * * Low logic port. 0 = pressed, 1 = released
 * * <p>
 * * 0xDD / 0xC1 : Joypad Port 2 (read-only)
 * * bit 0 : Joypad 2 Left
 * * bit 1 : Joypad 2 Right
 * * bit 2 : Joypad 2 Button 1
 * * bit 3 : Joypad 2 Button 2
 * * bit 4 : Reset Button
 * * Low logic port. 0 = pressed, 1 = released
 * * With no external devices attached, bits 7,6,5 return 0,1,1 respectively.
 * *
 * <p>
 * /TODO reset button
 */
public class TwoButtonsJoypad extends BasePadAdapter {

    private static final Logger LOG = LogHelper.getLogger(TwoButtonsJoypad.class.getSimpleName());

    //only for SMS, 1 - unpressed, 0 - pressed
    private int pauseButton1 = 1;
    private int pauseButton2 = 1;

    @Override
    public void init() {
        p1Type = JoypadType.BUTTON_2;
        p2Type = JoypadType.BUTTON_2;
        LOG.info("Joypad1: {} - Joypad2: {}", p1Type, p2Type);
        stateMap1 = Maps.newHashMap(releasedMap);
        stateMap2 = Maps.newHashMap(releasedMap);
    }

    public int readDataRegister1() {
        return value1;
    }

    public int readDataRegister2() {
        return value2;
    }

    //only for SMS
    public int readDataRegister3() {
        return pauseButton1 + pauseButton2;
    }

    private int get2D_2U_1B_1A_1R_1L_1D_1U() {
        return (getValue(PlayerNumber.P2, D) << 7) | (getValue(PlayerNumber.P2, U) << 6) |
                (getValue(PlayerNumber.P1, B) << 5) | (getValue(PlayerNumber.P1, A) << 4) | (getValue(PlayerNumber.P1, R) << 3) |
                (getValue(PlayerNumber.P1, L) << 2) | (getValue(PlayerNumber.P1, D) << 1) | (getValue(PlayerNumber.P1, U));
    }

    //TODO reset
    //011 RESET 2B 2A 2R 2L
    private int getR_2B_2A_2R_2L() {
        return 0xE0 | 1 << 4 | (getValue(PlayerNumber.P2, B) << 3) | (getValue(PlayerNumber.P2, A) << 2) |
                (getValue(PlayerNumber.P2, R) << 1) | (getValue(PlayerNumber.P2, L));
    }

    @Override
    public void newFrame() {
        value1 = get2D_2U_1B_1A_1R_1L_1D_1U();
        value2 = getR_2B_2A_2R_2L();
        pauseButton1 = getValue(PlayerNumber.P1, S);
        pauseButton2 = getValue(PlayerNumber.P2, S);
    }
}
