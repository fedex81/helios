/*
 * JoypadProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/10/19 16:20
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

import omegadrive.Device;
import omegadrive.input.InputProvider;

import java.awt.event.KeyEvent;

import static omegadrive.joypad.JoypadProvider.JoypadButton.*;

public interface JoypadProvider extends Device {

    JoypadButton[] directionButton = {D, L, R, U};

    enum JoypadAction {
        PRESSED,
        RELEASED
    }

    enum JoypadType {
        NONE,
        BUTTON_2,
        BUTTON_3,
        BUTTON_6
    }

    enum JoypadButton {
        A, B, C, X, Y, Z, M("MODE"), S("START"), U("UP"), D("DOWN"), L("LEFT"), R("RIGHT"),
        K0, K1, K2, K3, K4, K5, K6, K7, K8, K9, K_AST, K_HASH;

        public static final JoypadButton[] vals = JoypadButton.values();

        String mnemonic;

        JoypadButton() {
            this.mnemonic = name();
        }

        JoypadButton(String s) {
            this.mnemonic = s;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public boolean isDirection() {
            return JoypadButton.D == this || JoypadButton.U == this ||
                    JoypadButton.R == this || JoypadButton.L == this;
        }
    }

    enum JoypadDirection {
        UP_DOWN(U, D),
        LEFT_RIGHT(L, R);

        JoypadButton b1, b2;

        JoypadDirection(JoypadButton b1, JoypadButton b2) {
            this.b1 = b1;
            this.b2 = b2;
        }
    }

    void setButtonAction(InputProvider.PlayerNumber number, JoypadButton button, JoypadAction action);

    boolean hasDirectionPressed(InputProvider.PlayerNumber number);

    String getState(InputProvider.PlayerNumber number);

    void newFrame();

    default void setButtonAction(InputProvider.PlayerNumber number, JoypadButton button, JoypadAction action, KeyEvent event) {
        setButtonAction(number, button, action);
    }

    default void setPadSetupChange(InputProvider.PlayerNumber playerNumber, String info, boolean force) {
        //DO NOTHING
    }
}
