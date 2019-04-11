/*
 * InputProvider
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

import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import omegadrive.joypad.JoypadProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

public interface InputProvider {

    Logger LOG = LogManager.getLogger(InputProvider.class.getSimpleName());

    String OS_NAME = System.getProperty("os.name").toLowerCase();
    String NATIVE_SUBDIR = OS_NAME.contains("win") ? "windows" :
            (OS_NAME.contains("mac") ? "osx" : "linux");


    boolean DEBUG_DETECTION = Boolean.valueOf(System.getProperty("jinput.detect.debug", "false"));

    InputProvider NO_OP = new InputProvider() {
        @Override
        public void handleEvents() {

        }

        @Override
        public void setPlayers(int number) {

        }

        @Override
        public void reset() {

        }
    };

    float ON = 1.0f;

    static InputProvider createInstance(JoypadProvider joypadProvider) {
        Controller controller = detectController();
        InputProvider provider = NO_OP;
        if (controller != null) {
            provider = GamepadInputProvider.createOrGetInstance(controller, joypadProvider);
            LOG.info("Using Controller: " + controller.getName());
        } else {
            LOG.info("Unable to find a controller");
        }
        return provider;
    }

    static void bootstrap() {
        String lib = new File(".").getAbsolutePath() + File.separator + "privateLib"
                + File.separator + NATIVE_SUBDIR;
//        System.out.println(lib);
        System.setProperty("net.java.games.input.librarypath", lib);
        LOG.info("Loading system library from: " + lib);
        //disable java.util.logging
        java.util.logging.LogManager.getLogManager().reset();
        LOG.info("Disabling java.util.logging");

    }

    static Controller detectController() {
        Controller[] ca = ControllerEnvironment.getDefaultEnvironment().getControllers();
        Optional<Controller> cntOpt = Optional.ofNullable(Arrays.stream(ca).filter(c -> c.getType() == Controller.Type.GAMEPAD).findFirst().orElse(null));
        if (DEBUG_DETECTION || !cntOpt.isPresent()) {
            LOG.info("Controller detection: " + GamepadTest.detectControllerVerbose());
        }
        return cntOpt.orElse(null);
    }

    void handleEvents();

    void setPlayers(int number);

    void reset();
}
