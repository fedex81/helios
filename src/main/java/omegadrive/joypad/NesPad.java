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
import omegadrive.input.KeyboardInputHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Map;

import static omegadrive.input.InputProvider.PlayerNumber;

public class NesPad extends BasePadAdapter {

    private static final Logger LOG = LogManager.getLogger(NesPad.class.getSimpleName());

    private KeyListener p1HalfNes, p2HalfNes;

    private Component source = new Label();

    public NesPad(KeyListener p1HalfNes, KeyListener p2HalfNes) {
        this.p1HalfNes = p1HalfNes;
        this.p2HalfNes = p2HalfNes;
    }

    @Override
    public void init() {
        p1Type = JoypadType.BUTTON_2;
        p2Type = JoypadType.BUTTON_2;
        LOG.info("Joypad1: {} - Joypad2: {}", p1Type, p2Type);
        stateMap1 = Maps.newHashMap(releasedMap);
        stateMap2 = Maps.newHashMap(releasedMap);
    }

    @Override
    public void setButtonAction(PlayerNumber number, JoypadButton button, JoypadAction action) {
        KeyEvent event = new KeyEvent(source,
                action == JoypadAction.PRESSED ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, 0, '?');
        setButtonAction(number, button, action, event);
    }

    @Override
    public void setButtonAction(PlayerNumber number, JoypadButton button, JoypadAction action, KeyEvent event) {
        super.setButtonAction(number, button, action);
        Map<JoypadButton, Integer> btnMap = KeyboardInputHelper.keyboardBindings.row(number);
        KeyListener keyListener = number == PlayerNumber.P1 ? p1HalfNes : p2HalfNes;
        if (btnMap != null && btnMap.containsKey(button)) {
            event.setKeyCode(btnMap.get(button));
            if (action == JoypadAction.PRESSED) {
                keyListener.keyPressed(event);
            } else {
                keyListener.keyReleased(event);
            }
        }
    }

    @Override
    public int readDataRegister1() {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public int readDataRegister2() {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public int readDataRegister3() {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public void newFrame() {
    }
}
