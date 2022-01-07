/*
 * JinputGamepadMapping
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/10/19 15:20
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
    public static final String GAMESIR_G3S_PAD_NAME = "xiaoji Gamesir-G3s 1.02";
    public static final String GOOGLE_STADIA_PAD_NAME = "Google Inc. Stadia Controller";
    public static final String DEFAULT_PAD_NAME = "Default Pad Name";

    public static Table<String, Component.Identifier, Object> deviceMappings = HashBasedTable.create();
    private static final Logger LOG = LogManager.getLogger(JinputGamepadMapping.class.getSimpleName());

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
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.LEFT_THUMB, JoypadButton.C);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.X, JoypadButton.X);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.Y, JoypadButton.Y);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.RIGHT_THUMB, JoypadButton.Z);

        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.START, JoypadButton.S);
        deviceMappings.put(XBOX360_COMPAT_PAD_NAME, Button.SELECT, JoypadButton.M);

        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Axis.POV, JoypadDirection.UP_DOWN);
        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Axis.POV, JoypadDirection.LEFT_RIGHT);

        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Button.X, JoypadButton.A);
        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Button.A, JoypadButton.B);
        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Button.B, JoypadButton.C);
        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Button.LEFT_THUMB, JoypadButton.X);
        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Button.RIGHT_THUMB, JoypadButton.Y);
        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Button.Y, JoypadButton.Z);

        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Button.START, JoypadButton.S);
        deviceMappings.put(GAMESIR_G3S_PAD_NAME, Button.SELECT, JoypadButton.M);

        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Axis.POV, JoypadDirection.UP_DOWN);
        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Axis.POV, JoypadDirection.LEFT_RIGHT);

        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Button.X, JoypadButton.A);
        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Button.A, JoypadButton.B);
        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Button.B, JoypadButton.C);
        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Button.LEFT_THUMB, JoypadButton.X);
        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Button.RIGHT_THUMB, JoypadButton.Y);
        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Button.Y, JoypadButton.Z);

        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Button.START, JoypadButton.S);
        deviceMappings.put(GOOGLE_STADIA_PAD_NAME, Button.SELECT, JoypadButton.M);

        deviceMappings.put(DEFAULT_PAD_NAME, Axis.POV, JoypadDirection.UP_DOWN);
        deviceMappings.put(DEFAULT_PAD_NAME, Axis.POV, JoypadDirection.LEFT_RIGHT);

        deviceMappings.put(DEFAULT_PAD_NAME, Button.X, JoypadButton.A);
        deviceMappings.put(DEFAULT_PAD_NAME, Button.A, JoypadButton.B);
        deviceMappings.put(DEFAULT_PAD_NAME, Button.B, JoypadButton.C);
        deviceMappings.put(DEFAULT_PAD_NAME, Button.LEFT_THUMB, JoypadButton.X);
        deviceMappings.put(DEFAULT_PAD_NAME, Button.RIGHT_THUMB, JoypadButton.Y);
        deviceMappings.put(DEFAULT_PAD_NAME, Button.Y, JoypadButton.Z);

        deviceMappings.put(DEFAULT_PAD_NAME, Button.START, JoypadButton.S);
        deviceMappings.put(DEFAULT_PAD_NAME, Button.SELECT, JoypadButton.M);
    }
}
