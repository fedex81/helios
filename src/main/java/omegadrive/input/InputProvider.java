package omegadrive.input;

import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import omegadrive.joypad.JoypadProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;

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

    String OS = "linux"; //TODO

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
    int EMU_ON = 0;
    int EMU_OFF = 1;

    static InputProvider createInstance(JoypadProvider joypadProvider) {
        Controller controller = detectController();
        InputProvider provider = NO_OP;
        if (controller != null) {
            provider = new GamepadInputProvider(controller, joypadProvider);
            LOG.info("Using Controller: " + controller.getName());
        } else {
            LOG.info("Unable to find a controller");
        }
        return provider;
    }

    static void bootstrap() {
        String lib = new File(".").getAbsolutePath() + File.separator + "privateLib"
                + File.separator + OS;
        System.out.println(lib);
        System.setProperty("net.java.games.input.librarypath", lib);
        LOG.info("Loading system library from: " + lib);
    }

    static Controller detectController() {
        Controller[] ca = ControllerEnvironment.getDefaultEnvironment().getControllers();
        return Arrays.stream(ca).findFirst().orElse(null);
    }

    void handleEvents();

    void setPlayers(int number);

    void reset();
}
