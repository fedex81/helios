/*
 * KeyboardInput
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

package omegadrive.input;

import omegadrive.joypad.JoypadProvider;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Objects;

public class KeyboardInput extends KeyAdapter {

    protected static KeyboardInput currentAdapter;

    protected JoypadProvider provider;

    @Override
    public void keyPressed(KeyEvent e) {
        keyHandler(provider, e, true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        keyHandler(provider, e, false);
    }

    public static KeyAdapter createKeyAdapter(JoypadProvider provider) {
        Objects.requireNonNull(provider);
        if (currentAdapter == null) {
            currentAdapter = new KeyboardInput();
        }
        currentAdapter.provider = provider;
        return currentAdapter;
    }

    protected static void keyHandler(JoypadProvider joypad, KeyEvent e, boolean pressed) {
        JoypadProvider.JoypadAction action = pressed ? JoypadProvider.JoypadAction.PRESSED : JoypadProvider.JoypadAction.RELEASED;
        JoypadProvider.JoypadNumber number = null;
        JoypadProvider.JoypadButton button = null;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.U;
                break;
            case KeyEvent.VK_LEFT:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.L;
                break;
            case KeyEvent.VK_RIGHT:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.R;
                break;
            case KeyEvent.VK_DOWN:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.D;
                break;
            case KeyEvent.VK_ENTER:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.S;
                break;
            case KeyEvent.VK_A:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.A;
                break;
            case KeyEvent.VK_S:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.B;
                break;
            case KeyEvent.VK_D:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.C;
                break;
            case KeyEvent.VK_Q:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.X;
                break;
            case KeyEvent.VK_W:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.Y;
                break;
            case KeyEvent.VK_E:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.Z;
                break;
        }
        if (number != null && button != null) {
            joypad.setButtonAction(number, button, action);
        }
    }

}
