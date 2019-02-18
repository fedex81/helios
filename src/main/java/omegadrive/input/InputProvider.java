package omegadrive.input;

import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import omegadrive.joypad.JoypadProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * java.util.logging.SimpleFormatter.format=%1$tF %1$tT %4$.1s %2$s %5$s%6$s%n
 */
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
