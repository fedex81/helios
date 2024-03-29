/*
 * MsxPad
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


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import static omegadrive.input.InputProvider.PlayerNumber;
import static omegadrive.joypad.JoypadProvider.JoypadAction.RELEASED;
import static omegadrive.joypad.JoypadProvider.JoypadButton.*;

/**
 * http://fms.komkon.org/MSX/Docs/Portar.txt
 */
public class MsxPad extends BasePadAdapter {

    private static final Logger LOG = LogHelper.getLogger(MsxPad.class.getSimpleName());

    @Override
    public void init() {
        p1Type = JoypadType.BUTTON_2;
        p2Type = JoypadType.BUTTON_2;
        LOG.info("Joypad1: {} - Joypad2: {}", p1Type, p2Type);
        stateMap1 = Maps.newHashMap(ImmutableMap.<JoypadButton, JoypadAction>builder().
                put(D, RELEASED).put(U, RELEASED).
                put(L, RELEASED).put(R, RELEASED).
                put(A, RELEASED).put(B, RELEASED).
                build());
        stateMap2 = Maps.newHashMap(stateMap1);
    }

    private int getData(PlayerNumber number) {
        return 0x40 | (getValue(number, B) << 5) | (getValue(number, A) << 4) | (getValue(number, R) << 3) |
                (getValue(number, L) << 2) | (getValue(number, D) << 1) | (getValue(number, U));
    }

    public int readDataRegister1() {
        return value1;
    }

    public int readDataRegister2() {
        return value2;
    }

    @Override
    public void newFrame() {
        value1 = getData(PlayerNumber.P1);
        value2 = getData(PlayerNumber.P2);
    }
}
