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

public class GbPad extends BasePadAdapter {

    private static Logger LOG = LogManager.getLogger(GbPad.class.getSimpleName());

    private KeyListener p1Listener;

    private Component source = new Label();

    public GbPad(KeyListener p1Listener) {
        this.p1Listener = p1Listener;
    }

    @Override
    public void init() {
        p1Type = JoypadType.BUTTON_2;
        p2Type = JoypadType.BUTTON_2;
        LOG.info("Joypad1: {}", p1Type);
        stateMap1 = Maps.newHashMap(releasedMap);
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
        if (btnMap != null && btnMap.containsKey(button)) {
            event.setKeyCode(btnMap.get(button));
            if (action == JoypadAction.PRESSED) {
                p1Listener.keyPressed(event);
            } else {
                p1Listener.keyReleased(event);
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
