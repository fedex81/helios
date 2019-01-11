package omegadrive.input;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.JoypadProvider.JoypadAction;
import omegadrive.joypad.JoypadProvider.JoypadButton;
import omegadrive.joypad.JoypadProvider.JoypadNumber;
import omegadrive.util.PriorityThreadFactory;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.java.games.input.Component.Identifier.Button.*;
import static omegadrive.joypad.JoypadProvider.JoypadNumber.P1;
import static omegadrive.joypad.JoypadProvider.JoypadNumber.P2;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * TODO spurious resets shouldnt kill the polling thread
 * TODO save the current runnable anc cancel it
 */
public class GamepadInputProvider implements InputProvider {

    private static Logger LOG = LogManager.getLogger(GamepadInputProvider.class.getSimpleName());

    private static ExecutorService executorService =
            Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MIN_PRIORITY, GamepadInputProvider.class.getSimpleName()));
    private long POLLING_INTERVAL_MS = Long.valueOf(System.getProperty("jinput.polling.interval.ms", "5"));

    private volatile JoypadProvider joypadProvider;
    private Controller controller;
    private volatile boolean stop = false;
    private volatile int playerNumber = 1;
    private volatile JoypadNumber joypadNumber = P1;
    private String pov = Axis.POV.getName();

    private static InputProvider INSTANCE = NO_OP;


    public static InputProvider createOrGetInstance(Controller controller, JoypadProvider joypadProvider) {
        if (INSTANCE == NO_OP) {
            GamepadInputProvider g = new GamepadInputProvider();
            g.joypadProvider = joypadProvider;
            g.controller = controller;
            g.setPlayers(1);
            g.executorService.submit(g.inputRunnable());
            INSTANCE = g;
        }
        ((GamepadInputProvider) INSTANCE).joypadProvider = joypadProvider;
        return INSTANCE;
    }

    private GamepadInputProvider() {
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

    @Override
    public void handleEvents() {
        boolean ok = controller.poll();
        if (!ok) {
            return;
        }
        EventQueue eventQueue = controller.getEventQueue();
        boolean hasEvents;
        do {
            Event event = new Event();
            hasEvents = eventQueue.getNextEvent(event);
            if (hasEvents) {
                handleEvent(event);
            }
        } while (hasEvents);
    }

    @Override
    public void setPlayers(int number) {
        LOG.info("Setting number of players to: " + number);
        this.playerNumber = number;
        this.joypadNumber = P1.ordinal() == playerNumber ? P1 : P2;
    }

    @Override
    public void reset() {
//        stop = true;
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
        }
        // xbox360: linux || windows
        if (X == id || _2 == id) {
            joypadProvider.setButtonAction(joypadNumber, JoypadButton.A, action);
        }
        if (A == id || _0 == id) {
            joypadProvider.setButtonAction(joypadNumber, JoypadButton.B, action);
        }
        if (B == id || _1 == id) {
            joypadProvider.setButtonAction(joypadNumber, JoypadButton.C, action);
        }
        if (START == id || _7 == id) {
            joypadProvider.setButtonAction(joypadNumber, JoypadButton.S, action);
        }
        if (pov.equals(id.getName())) {
            if (value == Component.POV.OFF) {
                setDirectionOff();
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
}
