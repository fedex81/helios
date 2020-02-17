/*
 * InputProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 14/10/19 15:26
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

import com.google.common.collect.ImmutableList;
import omegadrive.input.jinput.JinputGamepadInputProvider;
import omegadrive.joypad.JoypadProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.List;

public interface InputProvider {

    Logger LOG = LogManager.getLogger(InputProvider.class.getSimpleName());

    String KEYBOARD_CONTROLLER = "Default (Keyboard)";
    String NO_CONTROLLER = "Disable";

    List<String> DEFAULT_CONTROLLERS = ImmutableList.of(NO_CONTROLLER, KEYBOARD_CONTROLLER);

    String OS_NAME = System.getProperty("os.name").toLowerCase();
    String NATIVE_SUBDIR = OS_NAME.contains("win") ? "windows" :
            (OS_NAME.contains("mac") ? "osx" : "linux");


    boolean DEBUG_DETECTION = Boolean.valueOf(System.getProperty("jinput.detect.debug", "false"));
    boolean JINPUT_ENABLE = Boolean.valueOf(System.getProperty("jinput.enable", "false"));
    String JINPUT_NATIVES_PATH = System.getProperty("jinput.native.location", "lib");

    enum PlayerNumber {
        P1, P2
    }

    InputProvider NO_OP = new InputProvider() {
        @Override
        public void handleEvents() {

        }

        @Override
        public void setPlayerController(PlayerNumber player, String controllerName) {

        }

        @Override
        public void reset() {

        }

        @Override
        public List<String> getAvailableControllers() {
            return Collections.emptyList();
        }
    };

    float ON = 1.0f;

    static InputProvider createInstance(JoypadProvider joypadProvider) {
        InputProvider ip = InputProvider.NO_OP;
        if (JINPUT_ENABLE) {
            try {
                ip = JinputGamepadInputProvider.getInstance(joypadProvider);
            } catch (Exception | Error e) {
                LOG.warn("Unable to load jinput: {}: {}", e.getClass().getName(), e.getMessage());
            }
        }
        return ip;
    }

    static void bootstrap() {
        String lib = new File(".").getAbsolutePath() + File.separator + JINPUT_NATIVES_PATH
                + File.separator + NATIVE_SUBDIR;
//        System.out.println(lib);
        System.setProperty("net.java.games.input.librarypath", lib);
        LOG.info("Loading system library from: " + lib);
        //disable java.util.logging
        java.util.logging.LogManager.getLogManager().reset();
        LOG.info("Disabling java.util.logging");
    }


    void handleEvents();

    void setPlayerController(PlayerNumber player, String controllerName);

    void reset();

    List<String> getAvailableControllers();
}
