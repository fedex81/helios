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

public class ExternalPad extends BasePadAdapter {

    private static final Logger LOG = LogManager.getLogger(ExternalPad.class.getSimpleName());

    private KeyListener p1Listener, p2Listener;

    private Component source = new Label();

    private ExternalPad(KeyListener p1, KeyListener p2, JoypadType type) {
        this.p1Listener = p1;
        this.p2Listener = p2;
        //TODO disable p2 key bindings
        if (p2 == null) {
            KeyboardInputHelper.keyboardInverseBindings.rowMap().remove(PlayerNumber.P2);
        }
        p1Type = type;
        p2Type = type;
        LOG.info("Joypad1: {} - Joypad2: {}", p1Type, p2Type);
    }

    public static BasePadAdapter createTwoButtonsPad(KeyListener p1) {
        return new ExternalPad(p1, null, JoypadType.BUTTON_2);
    }

    public static BasePadAdapter createTwoButtonsPad(KeyListener p1, KeyListener p2) {
        return new ExternalPad(p1, p2, JoypadType.BUTTON_2);
    }

    public static BasePadAdapter createSixButtonsPad(KeyListener p1, KeyListener p2) {
        return new ExternalPad(p1, p2, JoypadType.BUTTON_6);
    }

    @Override
    public void init() {
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
        KeyListener keyListener = number == PlayerNumber.P1 ? p1Listener : p2Listener;
        if (btnMap != null && btnMap.containsKey(button)) {
            event.setKeyCode(btnMap.get(button));
            if (action == JoypadAction.PRESSED) {
                keyListener.keyPressed(event);
            } else {
                keyListener.keyReleased(event);
            }
        }
    }
}
