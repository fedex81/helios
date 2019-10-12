/*
 * JinputGamepadInputProvider
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

import net.java.games.input.*;
import omegadrive.input.InputProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.JoypadProvider.JoypadAction;
import omegadrive.joypad.JoypadProvider.JoypadButton;
import omegadrive.joypad.JoypadProvider.JoypadNumber;
import omegadrive.util.PriorityThreadFactory;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static net.java.games.input.Component.Identifier.Button.Axis;
import static omegadrive.joypad.JoypadProvider.JoypadNumber.P1;
import static omegadrive.joypad.JoypadProvider.JoypadNumber.P2;

public class JinputGamepadInputProvider implements InputProvider {

    private static Logger LOG = LogManager.getLogger(JinputGamepadInputProvider.class.getSimpleName());

    private static ExecutorService executorService =
            Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MIN_PRIORITY, JinputGamepadInputProvider.class.getSimpleName()));
    private long POLLING_INTERVAL_MS = Long.valueOf(System.getProperty("jinput.polling.interval.ms", "5"));

    private volatile JoypadProvider joypadProvider;
    private Controller controller;
    private volatile boolean stop = false;
    private volatile int playerNumber = 1;
    private volatile JoypadNumber joypadNumber = P1;
    private String pov = Axis.POV.getName();

    private static InputProvider INSTANCE = NO_OP;
    private static final int AXIS_p1 = 1;
    private static final int AXIS_0 = 0;
    private static final int AXIS_m1 = -1;

    private List<String> controllers;

    private JinputGamepadInputProvider() {
        controllers = new ArrayList<>();
    }

    public static InputProvider getInstance(JoypadProvider joypadProvider) {
        List<Controller> list = detectControllers();
        InputProvider provider = NO_OP;
        if (!list.isEmpty()) {
            provider = createOrGetInstance(list, joypadProvider);
        } else {
            LOG.info("Unable to find a controller");
        }
        return provider;
    }

    private static InputProvider createOrGetInstance(List<Controller> controllers, JoypadProvider joypadProvider) {
        if (INSTANCE == NO_OP) {
            JinputGamepadInputProvider g = new JinputGamepadInputProvider();
            g.joypadProvider = joypadProvider;
            g.controller = controllers.iterator().next();
            g.controllers = controllers.stream().map(Controller::getName).collect(Collectors.toList());
            g.setPlayers(1);
            executorService.submit(g.inputRunnable());
            INSTANCE = g;
            LOG.info("Using Controller: " + g.controller.getName());
        }
        ((JinputGamepadInputProvider) INSTANCE).joypadProvider = joypadProvider;
        return INSTANCE;
    }

    static List<Controller> detectControllers() {
        Controller[] ca = ControllerEnvironment.getDefaultEnvironment().getControllers();
        List<Controller> l = Arrays.stream(ca).filter(c -> c.getType() == Controller.Type.GAMEPAD).collect(Collectors.toList());
        if (DEBUG_DETECTION || l.isEmpty()) {
            LOG.info("Controller detection: " + detectControllerVerbose());
        }
        return l;
    }

    private Runnable inputRunnable() {
        return () -> {
            LOG.info("Starting controller polling, interval (ms): " + POLLING_INTERVAL_MS);
            do {
                handleEvents();
                Util.sleep(POLLING_INTERVAL_MS);
            } while (!stop);
            LOG.info("Controller polling stopped");
        };
    }

    private boolean resetDirections = false;

    @Override
    public void handleEvents() {
        boolean ok = controller.poll();
        if (!ok) {
            return;
        }
        int count = 0;
        EventQueue eventQueue = controller.getEventQueue();
        resetDirections = joypadProvider.hasDirectionPressed(joypadNumber);
        boolean hasEvents;
        do {
            Event event = new Event();
            hasEvents = eventQueue.getNextEvent(event);
            if (hasEvents) {
                handleEvent(event);
                count++;
            }
        } while (hasEvents);
        if (InputProvider.DEBUG_DETECTION && count > 0) {
            LOG.info(joypadProvider.getState(joypadNumber));
        }
    }

    @Override
    public void setPlayers(int number) {
        LOG.info("Setting number of players to: " + number);
        this.playerNumber = number;
        this.joypadNumber = P1.ordinal() == playerNumber - 1 ? P1 : P2;
    }

    @Override
    public void reset() {
//        stop = true;
    }

    @Override
    public List<String> getAvailableControllers() {
        return controllers;
    }

    private void setDirectionOff() {
        joypadProvider.setButtonAction(joypadNumber, JoypadButton.D, JoypadAction.RELEASED);
        joypadProvider.setButtonAction(joypadNumber, JoypadButton.U, JoypadAction.RELEASED);
        joypadProvider.setButtonAction(joypadNumber, JoypadButton.L, JoypadAction.RELEASED);
        joypadProvider.setButtonAction(joypadNumber, JoypadButton.R, JoypadAction.RELEASED);
    }

    private void handleEvent(Event event) {
        Component.Identifier id = event.getComponent().getIdentifier();
        double value = event.getValue();
        JoypadAction action = value == ON ? JoypadAction.PRESSED : JoypadAction.RELEASED;
        if (InputProvider.DEBUG_DETECTION) {
            LOG.info(id + ": " + value);
            System.out.println(id + ": " + value);
        }
        Object res = JinputGamepadMapping.deviceMappings.row(controller.getName()).getOrDefault(id, null);
        if (res != null && res instanceof JoypadButton) {
            joypadProvider.setButtonAction(joypadNumber, (JoypadButton) res, action);
        } else if (res != null && res instanceof JoypadProvider.JoypadDirection) {
            handleDPad(id, value);
        } else {
            LOG.debug("Unhandled event: {}", event);
        }
    }

    private void handleDPad(Component.Identifier id, double value) {
        if (Axis.X == id) {
            int ival = (int) value;
            switch (ival) {
                case AXIS_0:
                    joypadProvider.setButtonAction(joypadNumber, JoypadButton.R, JoypadAction.RELEASED);
                    joypadProvider.setButtonAction(joypadNumber, JoypadButton.L, JoypadAction.RELEASED);
                    break;
                case AXIS_m1:
                    joypadProvider.setButtonAction(joypadNumber, JoypadButton.L, JoypadAction.PRESSED);
                    break;
                case AXIS_p1:
                    joypadProvider.setButtonAction(joypadNumber, JoypadButton.R, JoypadAction.PRESSED);
                    break;
            }
        }
        if (Axis.Y == id) {
            int ival = (int) value;
            switch (ival) {
                case AXIS_0:
                    joypadProvider.setButtonAction(joypadNumber, JoypadButton.U, JoypadAction.RELEASED);
                    joypadProvider.setButtonAction(joypadNumber, JoypadButton.D, JoypadAction.RELEASED);
                    break;
                case AXIS_m1:
                    joypadProvider.setButtonAction(joypadNumber, JoypadButton.U, JoypadAction.PRESSED);
                    break;
                case AXIS_p1:
                    joypadProvider.setButtonAction(joypadNumber, JoypadButton.D, JoypadAction.PRESSED);
                    break;
            }
        }

        if (pov.equals(id.getName())) {
            JoypadAction action = JoypadAction.PRESSED;
            //release directions previously pressed - only on the first event
            boolean off = resetDirections || value == Component.POV.OFF;
            if (off) {
                setDirectionOff();
                if (resetDirections) {
                    resetDirections = false;
                }
            }
            if (value == Component.POV.DOWN) {
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.D, action);
            }
            if (value == Component.POV.UP) {
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.U, action);
            }
            if (value == Component.POV.LEFT) {
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.L, action);
            }
            if (value == Component.POV.RIGHT) {
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.R, action);
            }
            if (value == Component.POV.DOWN_LEFT) {
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.D, action);
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.L, action);
            }
            if (value == Component.POV.DOWN_RIGHT) {
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.D, action);
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.R, action);
            }
            if (value == Component.POV.UP_LEFT) {
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.U, action);
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.L, action);
            }
            if (value == Component.POV.UP_RIGHT) {
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.U, action);
                joypadProvider.setButtonAction(joypadNumber, JoypadButton.R, action);
            }
        }
    }

    private static String detectControllerVerbose() {
        Controller[] ca = ControllerEnvironment.getDefaultEnvironment().getControllers();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < ca.length; i++) {
            /* Get the name of the controller */
            sb.append("\n" + ca[i].getName() + "\n");
            sb.append("Position: [" + i + "]\n");
            sb.append("Type: " + ca[i].getType().toString() + "\n");

            /* Get this controllers components (buttons and axis) */
            Component[] components = ca[i].getComponents();
            sb.append("Component Count: " + components.length + "\n");
            for (int j = 0; j < components.length; j++) {

                /* Get the components name */
                sb.append("Component " + j + ": " + components[j].getName() + "\n");
                sb.append("    Identifier: " + components[j].getIdentifier().getName() + "\n");
                sb.append("    ComponentType: ");
                if (components[j].isRelative()) {
                    sb.append("Relative");
                } else {
                    sb.append("Absolute");
                }
                if (components[j].isAnalog()) {
                    sb.append(" Analog");
                } else {
                    sb.append(" Digital");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
