/*
 * ColecoPad
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;

import static omegadrive.input.InputProvider.PlayerNumber;
import static omegadrive.joypad.JoypadProvider.JoypadAction.RELEASED;
import static omegadrive.joypad.JoypadProvider.JoypadButton.*;

public class ColecoPad extends BasePadAdapter {

    private static Logger LOG = LogManager.getLogger(ColecoPad.class.getSimpleName());

    private Map<JoypadButton, JoypadAction> stateMapKeypad = Maps.newHashMap(ImmutableMap.<JoypadButton, JoypadAction>builder().
            put(K0, RELEASED).put(K1, RELEASED).
            put(K2, RELEASED).put(K3, RELEASED).
            put(K4, RELEASED).put(K5, RELEASED).
            put(K6, RELEASED).put(K7, RELEASED).
            put(K8, RELEASED).put(K9, RELEASED).
            put(K_AST, RELEASED).put(K_HASH, RELEASED).build());

    private Map<JoypadButton, Integer> valueMapKeypad = Maps.newHashMap(ImmutableMap.<JoypadButton, Integer>builder().
            put(K0, 10).put(K1, 13).
            put(K2, 7).put(K3, 12).
            put(K4, 2).put(K5, 3).
            put(K6, 14).put(K7, 5).
            put(K8, 1).put(K9, 11).
            put(K_AST, 9).put(K_HASH, 6).build());


    private boolean mode80 = false;

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

    @Override
    public void writeDataRegister1(long data) {
        mode80 = (data & 0xC0) != 0xC0;
    }

    @Override
    public int readDataRegister1() {
        return mode80 ? getMode80(PlayerNumber.P1) : getModeC0(PlayerNumber.P1);
    }

    @Override
    public int readDataRegister2() {
        return mode80 ? getMode80(PlayerNumber.P2) : getModeC0(PlayerNumber.P2);
    }

    @Override
    public void setButtonAction(PlayerNumber number, JoypadButton button, JoypadAction action) {
        JoypadAction res = getMap(number).computeIfPresent(button, (k, v) -> action);
        if (res == null && PlayerNumber.P1 == number) {
            stateMapKeypad.computeIfPresent(button, (k, v) -> action);
        }
    }

    /**
     * 'C0' mode  (port C0 written to)
     * <p>
     * <p>
     * This mode allows you to read the stick and left button:
     */
    private int getModeC0(PlayerNumber number) {
        return 0x30 | (getValue(number, A) << 6) | (getValue(number, L) << 3) |
                (getValue(number, D) << 2) | (getValue(number, R) << 1) | (getValue(number, U));
    }

    /**
     * '80' mode  (port 80 written to)
     * <p>
     * <p>
     * bit #6= status of right button
     * <p>
     * The keypad returns a 4-bit binary word for a button pressed:
     */
    private int getMode80(PlayerNumber number) {
        int res = 0x30 | (getValue(number, B) << 6);
        if (number == PlayerNumber.P1) {
            Optional<JoypadButton> pressedBtn = stateMapKeypad.entrySet().stream().
                    filter(e -> e.getValue() == JoypadAction.PRESSED).map(Map.Entry::getKey).findFirst();
            res |= pressedBtn.map(valueMapKeypad::get).orElse(0xF);
        } else {
            res |= 0xF; //TODO p2
        }
        return res;
    }

    //UNUSED

    @Override
    public void newFrame() {
    }
}
