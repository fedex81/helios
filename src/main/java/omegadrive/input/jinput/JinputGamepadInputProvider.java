/*
 * JinputGamepadInputProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 14/10/19 15:19
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.java.games.input.*;
import omegadrive.input.InputProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.JoypadProvider.JoypadAction;
import omegadrive.joypad.JoypadProvider.JoypadButton;
import omegadrive.util.LogHelper;
import omegadrive.util.PriorityThreadFactory;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.java.games.input.Component.Identifier.Button.Axis;
import static omegadrive.input.jinput.JinputGamepadMapping.DEFAULT_PAD_NAME;

/**
 * TODO both keyboard and joypad can be STICK
 */
public class JinputGamepadInputProvider implements InputProvider {

    private static final Logger LOG = LogHelper.getLogger(JinputGamepadInputProvider.class.getSimpleName());

    private final static boolean USE_POLLING_THREAD = Boolean.parseBoolean(System.getProperty("jinput.polling.thread", "false"));
    private final static long POLLING_INTERVAL_MS = Long.parseLong(System.getProperty("jinput.polling.interval.ms", "5"));
    private static ExecutorService executorService;

    private volatile JoypadProvider joypadProvider;
    private volatile boolean stop = false;
    public static final String POV = Axis.POV.getName();

    private static InputProvider INSTANCE = NO_OP;
    public static final int AXIS_p1 = 1;
    public static final int AXIS_0 = 0;
    public static final int AXIS_m1 = -1;

    private final List<String> controllerNames;
    private final Map<String, Controller> controllers;

    private UpdatedGamepadCtx gamepadCtx;

    private static final BiFunction<List<Controller>, Integer, String> toNameFn = (l, i)
            -> l.get(i).getName() + " #" + i;

    private final Map<PlayerNumber, String> playerControllerMap = Maps.newHashMap(
            ImmutableMap.of(PlayerNumber.P1, KEYBOARD_CONTROLLER,
                    PlayerNumber.P2, KEYBOARD_CONTROLLER
            ));

    public static class UpdatedGamepadCtx {
        public Controller c;
        public net.java.games.input.Event event;
    }

    public JinputGamepadInputProvider() {
        controllerNames = new ArrayList<>(DEFAULT_CONTROLLERS);
        controllers = new HashMap<>();
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
            assert controllers.size() == DEFAULT_CONTROLLERS.size();
            IntStream.range(0, controllers.size()).
                    forEach(i -> {
                        String name = toNameFn.apply(controllers, i);
                        g.controllers.put(name, controllers.get(i));
                        g.controllerNames.add(name);
                    });
            g.gamepadCtx = new UpdatedGamepadCtx();
            g.initPollingThreadMaybe();
            INSTANCE = g;
        }
        ((JinputGamepadInputProvider) INSTANCE).joypadProvider = joypadProvider;
        return INSTANCE;
    }

    private void initPollingThreadMaybe() {
        if (USE_POLLING_THREAD) {
            if (executorService == null) {
                executorService = Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MIN_PRIORITY, JinputGamepadInputProvider.class.getSimpleName()));
            }
            executorService.submit(Util.wrapRunnableEx(inputRunnable()));
        }
    }


    public static List<Controller> detectControllers() {
        Controller[] ca = ControllerEnvironment.getDefaultEnvironment().getControllers();
        List<Controller> l = Arrays.stream(ca).filter(c ->
                        c.getType() == Controller.Type.GAMEPAD).
                collect(Collectors.toList());
        if (DEBUG_DETECTION || l.isEmpty()) {
            LOG.info("Controller detection: {}", detectAllControllerVerbose());
        }
        return l;
    }

    private Runnable inputRunnable() {
        return () -> {
            LOG.info("Starting controller polling, interval (ms): {}", POLLING_INTERVAL_MS);
            do {
                handleEventsInternal();
                Util.sleep(POLLING_INTERVAL_MS);
            } while (!stop);
            LOG.info("Controller polling stopped");
        };
    }

    private boolean resetDirections = false;

    @Override
    public void handleEvents() {
        if (!USE_POLLING_THREAD) {
            handleEventsInternal();
        }
    }

    @Override
    public void handleAllEvents(InputEventCallback callback) {
        for (var entry : controllers.entrySet()) {
            Controller controller = entry.getValue();
            boolean ok = controller.poll();
            if (!ok) {
                return;
            }
            net.java.games.input.EventQueue eventQueue = controller.getEventQueue();

            boolean hasEvents;
            do {
                net.java.games.input.Event event = new Event();
                hasEvents = eventQueue.getNextEvent(event);
                if (hasEvents) {
                    gamepadCtx.c = controller;
                    gamepadCtx.event = event;
                    callback.update(gamepadCtx);
                }
            } while (hasEvents);
        }
    }

    private void handleEventsInternal() {
        for (var entryC : controllers.entrySet()) {
            Controller controller = entryC.getValue();
            String ctrlName = entryC.getKey();
            boolean ok = controller.poll();
            if (!ok) {
                return;
            }
            int count = 0;
            for (Map.Entry<PlayerNumber, String> entry : playerControllerMap.entrySet()) {
                PlayerNumber player = entry.getKey();
                if (!ctrlName.equalsIgnoreCase(entry.getValue())) {
                    continue;
                }
                resetDirections = joypadProvider.hasDirectionPressed(player);
                EventQueue eventQueue = controller.getEventQueue();

                boolean hasEvents;
                do {
                    Event event = new Event();
                    hasEvents = eventQueue.getNextEvent(event);
                    if (player != null && hasEvents) {
                        handleEvent(player, ctrlName, event);
                        count++;
                    }
                } while (hasEvents);
                if (InputProvider.DEBUG_DETECTION && count > 0) {
                    LOG.info(joypadProvider.getState(player));
                }
            }
        }
    }

    @Override
    public void setPlayerController(PlayerNumber player, String controllerName) {
        playerControllerMap.put(player, controllerName);
    }

    @Override
    public void reset() {
//        stop = true;
    }

    @Override
    public List<String> getAvailableControllers() {
        return controllerNames;
    }

    private void setDirectionOff(PlayerNumber playerNumber) {
        joypadProvider.setButtonAction(playerNumber, JoypadButton.D, JoypadAction.RELEASED);
        joypadProvider.setButtonAction(playerNumber, JoypadButton.U, JoypadAction.RELEASED);
        joypadProvider.setButtonAction(playerNumber, JoypadButton.L, JoypadAction.RELEASED);
        joypadProvider.setButtonAction(playerNumber, JoypadButton.R, JoypadAction.RELEASED);
    }

    private void handleEvent(PlayerNumber playerNumber, String ctrlName, Event event) {
        Component.Identifier id = event.getComponent().getIdentifier();
        double value = event.getValue();
        JoypadAction action = value == ON ? JoypadAction.PRESSED : JoypadAction.RELEASED;
        if (InputProvider.DEBUG_DETECTION) {
            LOG.info("{}: {}", id, value);
            System.out.println(id + ": " + value);
        }
        Map<Component.Identifier, Object> map = getDeviceMappings(controllers.get(ctrlName).getName());
        Object res = map.getOrDefault(id, null);
        if (res instanceof JoypadButton) {
            joypadProvider.setButtonAction(playerNumber, (JoypadButton) res, action);
        } else if (res instanceof JoypadProvider.JoypadDirection) {
            handleDPad(playerNumber, id, value);
        } else {
            //LOG.debug("Unhandled event: {}", event);
        }
    }

    private Map<Component.Identifier, Object> getDeviceMappings(String ctrlName) {
        Map<Component.Identifier, Object> map = JinputGamepadMapping.deviceMappings.row(ctrlName);
        if (map.isEmpty()) {
            map = JinputGamepadMapping.deviceMappings.row(DEFAULT_PAD_NAME);
        }
        return map;
    }

    private void handleDPad(PlayerNumber playerNumber, Component.Identifier id, double value) {
        if (Axis.X == id) {
            int ival = (int) value;
            switch (ival) {
                case AXIS_0:
                    joypadProvider.setButtonAction(playerNumber, JoypadButton.R, JoypadAction.RELEASED);
                    joypadProvider.setButtonAction(playerNumber, JoypadButton.L, JoypadAction.RELEASED);
                    break;
                case AXIS_m1:
                    joypadProvider.setButtonAction(playerNumber, JoypadButton.L, JoypadAction.PRESSED);
                    break;
                case AXIS_p1:
                    joypadProvider.setButtonAction(playerNumber, JoypadButton.R, JoypadAction.PRESSED);
                    break;
            }
        }
        if (Axis.Y == id) {
            int ival = (int) value;
            switch (ival) {
                case AXIS_0:
                    joypadProvider.setButtonAction(playerNumber, JoypadButton.U, JoypadAction.RELEASED);
                    joypadProvider.setButtonAction(playerNumber, JoypadButton.D, JoypadAction.RELEASED);
                    break;
                case AXIS_m1:
                    joypadProvider.setButtonAction(playerNumber, JoypadButton.U, JoypadAction.PRESSED);
                    break;
                case AXIS_p1:
                    joypadProvider.setButtonAction(playerNumber, JoypadButton.D, JoypadAction.PRESSED);
                    break;
            }
        }

        if (POV.equals(id.getName())) {
            JoypadAction action = JoypadAction.PRESSED;
            //release directions previously pressed - only on the first event
            boolean off = resetDirections || value == Component.POV.OFF;
            if (off) {
                setDirectionOff(playerNumber);
                if (resetDirections) {
                    resetDirections = false;
                }
            }
            if (value == Component.POV.DOWN) {
                joypadProvider.setButtonAction(playerNumber, JoypadButton.D, action);
            }
            if (value == Component.POV.UP) {
                joypadProvider.setButtonAction(playerNumber, JoypadButton.U, action);
            }
            if (value == Component.POV.LEFT) {
                joypadProvider.setButtonAction(playerNumber, JoypadButton.L, action);
            }
            if (value == Component.POV.RIGHT) {
                joypadProvider.setButtonAction(playerNumber, JoypadButton.R, action);
            }
            if (value == Component.POV.DOWN_LEFT) {
                joypadProvider.setButtonAction(playerNumber, JoypadButton.D, action);
                joypadProvider.setButtonAction(playerNumber, JoypadButton.L, action);
            }
            if (value == Component.POV.DOWN_RIGHT) {
                joypadProvider.setButtonAction(playerNumber, JoypadButton.D, action);
                joypadProvider.setButtonAction(playerNumber, JoypadButton.R, action);
            }
            if (value == Component.POV.UP_LEFT) {
                joypadProvider.setButtonAction(playerNumber, JoypadButton.U, action);
                joypadProvider.setButtonAction(playerNumber, JoypadButton.L, action);
            }
            if (value == Component.POV.UP_RIGHT) {
                joypadProvider.setButtonAction(playerNumber, JoypadButton.U, action);
                joypadProvider.setButtonAction(playerNumber, JoypadButton.R, action);
            }
        }
    }

    public static String detectControllerVerbose(Controller[] ca) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < ca.length; i++) {
            /* Get the name of the controller */
            sb.append("\n").append(ca[i].getName()).append("\n");
            sb.append("Position: [").append(i).append("]\n");
            sb.append("Type: ").append(ca[i].getType().toString()).append("\n");

            /* Get this controllers components (buttons and axis) */
            Component[] components = ca[i].getComponents();
            sb.append("Component Count: ").append(components.length).append("\n");
            for (int j = 0; j < components.length; j++) {

                /* Get the components name */
                sb.append("Component ").append(j).append(": ").append(components[j].getName()).append("\n");
                sb.append("    Identifier: ").append(components[j].getIdentifier().getName()).append("\n");
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

    public static String detectControllerVerbose(Controller controller) {
        return detectControllerVerbose(new Controller[]{controller});
    }

    public static String detectAllControllerVerbose() {
        return detectControllerVerbose(ControllerEnvironment.getDefaultEnvironment().getControllers());
    }
}
