/*
 * KeyboardInput
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

package omegadrive.input;

import omegadrive.SystemLoader;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.JoypadProvider.JoypadButton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static omegadrive.input.InputProvider.PlayerNumber;

public class KeyboardInput extends KeyAdapter {

    protected static Logger LOG = LogManager.getLogger(KeyboardInput.class.getSimpleName());

    protected JoypadProvider provider;
    protected SystemLoader.SystemType systemType;

    @Override
    public void keyPressed(KeyEvent e) {
        keyHandler(provider, e, true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        keyHandler(provider, e, false);
    }

    public static KeyAdapter createKeyAdapter(SystemLoader.SystemType systemType, JoypadProvider provider) {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(systemType);
        KeyboardInput res = null;
        switch (systemType){
            case COLECO:
                res = new ColecoKeyboardInput();
                break;
            case MSX:
                MsxKeyboardInput m = new MsxKeyboardInput();
                MsxKeyboardInput.currentAdapter = m;
                res = m;
                break;
            default:
               res = new KeyboardInput();
               break;
        }
        res.provider = provider;
        LOG.info("Setting keyAdapter for {}", systemType);
        return res;
    }

    protected static void keyHandler(JoypadProvider joypad, KeyEvent e, boolean pressed) {
        JoypadProvider.JoypadAction action = pressed ? JoypadProvider.JoypadAction.PRESSED : JoypadProvider.JoypadAction.RELEASED;
        int kc = e.getKeyCode();
        Optional<Map.Entry<PlayerNumber, JoypadButton>> optEntry =
                KeyboardInputHelper.keyboardInverseBindings.column(kc).entrySet().stream().findFirst();
        if (e.getModifiers() == 0 && optEntry.isPresent()) {
            joypad.setButtonAction(optEntry.get().getKey(), optEntry.get().getValue(), action, e);
        }
    }
}
