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
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.io.File;
import java.util.List;

public interface InputProvider {

    Logger LOG = LogHelper.getLogger(InputProvider.class.getSimpleName());

    String KEYBOARD_CONTROLLER = "Default (Keyboard)";
    String NO_CONTROLLER = "Disable";

    List<String> DEFAULT_CONTROLLERS = ImmutableList.of(NO_CONTROLLER, KEYBOARD_CONTROLLER);

    boolean DEBUG_DETECTION = Boolean.parseBoolean(System.getProperty("jinput.detect.debug", "false"));
    boolean JINPUT_ENABLE = Boolean.parseBoolean(System.getProperty("jinput.enable", "false"));
    String JINPUT_NATIVES_PATH = System.getProperty("jinput.native.location", "lib");

    enum PlayerNumber {
        P1, P2
    }

    interface InputEventCallback {
        void update(Object ctx);
    }

    InputProvider NO_OP = new InputProvider() {
        @Override
        public void handleEvents() {

        }

        @Override
        public void handleAllEvents(InputEventCallback callback) {

        }

        @Override
        public void setPlayerController(PlayerNumber player, String controllerName) {

        }

        @Override
        public void reset() {

        }

        @Override
        public List<String> getAvailableControllers() {
            return DEFAULT_CONTROLLERS;
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
                + File.separator + Util.NATIVE_SUBDIR;
//        System.out.println(lib);
        System.setProperty("net.java.games.input.librarypath", lib);
        LOG.info("Loading system library from: {}", lib);
        //disable java.util.logging
        java.util.logging.LogManager.getLogManager().reset();
        LOG.info("Disabling java.util.logging");
    }


    void handleEvents();

    void handleAllEvents(InputEventCallback callback);

    void setPlayerController(PlayerNumber player, String controllerName);

    void reset();

    List<String> getAvailableControllers();
}
