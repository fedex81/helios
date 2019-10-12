/*
 * JinputGamepadMapping
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 12/10/19 18:12
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

package omegadrive.input.jinput;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import net.java.games.input.Component;
import net.java.games.input.Component.Identifier.Button;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static net.java.games.input.Component.Identifier.Button.Axis;
import static omegadrive.joypad.JoypadProvider.JoypadButton;
import static omegadrive.joypad.JoypadProvider.JoypadDirection;

public class JinputGamepadMapping {

    public static final String SONY_PSX_CLASSIC_PAD_NAME = "Sony Interactive Entertainment Controller";
    public static final String XBOX360_COMPAT_PAD_NAME = "Microsoft X-Box 360 pad";
    public static Table<String, Component.Identifier, Object> deviceMappings = HashBasedTable.create();
    private static Logger LOG = LogManager.getLogger(JinputGamepadMapping.class.getSimpleName());

    static {
        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Axis.Y, JoypadDirection.UP_DOWN);
        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Axis.X, JoypadDirection.LEFT_RIGHT);

        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Button.X, JoypadButton.A);
        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Button.C, JoypadButton.B);
        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Button.B, JoypadButton.C);
        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Button.LEFT_THUMB, JoypadButton.X);
        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Button.RIGHT_THUMB, JoypadButton.Y);
        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Button.A, JoypadButton.Z);

        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Button.RIGHT_THUMB2, JoypadButton.S);
        deviceMappings.put(SONY_PSX_CLASSIC_PAD_NAME, Button.LEFT_THUMB2, JoypadButton.M);

        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Axis.Y, JoypadDirection.UP_DOWN);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Axis.X, JoypadDirection.LEFT_RIGHT);

        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.A, JoypadButton.A);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.B, JoypadButton.B);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.C, JoypadButton.C);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.X, JoypadButton.X);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.Y, JoypadButton.Y);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.RIGHT_THUMB, JoypadButton.Z);

        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.START, JoypadButton.S);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.SELECT, JoypadButton.M);
    }


//    private void handleEvent(Event event) {
//        Component.Identifier id = event.getComponent().getIdentifier();
//        double value = event.getValue();
//        JoypadAction action = value == ON ? JoypadAction.PRESSED : JoypadAction.RELEASED;
//        if (InputProvider.DEBUG_DETECTION) {
//            LOG.info(id + ": " + value);
//            System.out.println(id + ": " + value);
//        }
//        // xbox360: linux || windows
//        if (X == id || _2 == id) {
//            joypadProvider.setButtonAction(joypadNumber, JoypadButton.A, action);
//        }
//        if (A == id || _0 == id) {
//            joypadProvider.setButtonAction(joypadNumber, JoypadButton.B, action);
//        }
//        if (B == id || _1 == id) {
//            joypadProvider.setButtonAction(joypadNumber, JoypadButton.C, action);
//        }
//        //TODO WIN
//        if (LEFT_THUMB == id) {
//            joypadProvider.setButtonAction(joypadNumber, JoypadButton.X, action);
//        }
//        if (RIGHT_THUMB == id) {
//            joypadProvider.setButtonAction(joypadNumber, JoypadButton.Y, action);
//        }
//        if (Y == id) {
//            joypadProvider.setButtonAction(joypadNumber, JoypadButton.Z, action);
//        }
//        if (SELECT == id || LEFT_THUMB2 == id) {
//            joypadProvider.setButtonAction(joypadNumber, JoypadButton.M, action);
//        }
//        //TODO WIN
//        //TODO psClassis USB: start button = RIGHT_THUMB2
//        if (START == id || _7 == id || RIGHT_THUMB2 == id) {
//            joypadProvider.setButtonAction(joypadNumber, JoypadButton.S, action);
//        }
//        handleDPad(id, value);
//    }
}
