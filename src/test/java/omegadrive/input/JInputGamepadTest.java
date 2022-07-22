/*
 * GamepadTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/10/19 17:33
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

import omegadrive.input.swing.GamepadSetupView;
import omegadrive.util.Util;

import java.io.File;
import java.util.List;

import static omegadrive.input.InputProvider.PlayerNumber;
import static omegadrive.input.InputProvider.createInstance;
import static omegadrive.util.SystemTestUtil.createTestJoypadProvider;

public class JInputGamepadTest {

    public static void main(String[] args) {
        String lib = new File(".").getAbsolutePath() + File.separator + "lib"
                + File.separator + "linux";
        System.out.println(lib);
        System.setProperty("tinylog.configuration", "./res/tinylog-info.properties");
        System.setProperty("jinput.enable", "true");
        System.setProperty("jinput.detect.debug", "true");
        System.setProperty("net.java.games.input.librarypath", lib);
        InputProvider inputProvider = createInstance(createTestJoypadProvider());
        List<String> l = inputProvider.getAvailableControllers();
        GamepadSetupView gsw = GamepadSetupView.createInstance(inputProvider);
        if (l.size() > 2) {
            inputProvider.setPlayerController(PlayerNumber.P1, inputProvider.getAvailableControllers().get(2));
            pollInputs(inputProvider, gsw);
        }
    }

    private static void pollInputs(InputProvider inputProvider, GamepadSetupView gsw) {
        while (true) {
            inputProvider.handleAllEvents(gsw);
            gsw.updateDone();
            Util.sleep(20);
        }
    }
}