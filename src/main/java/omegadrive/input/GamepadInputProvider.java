package omegadrive.input;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;
import omegadrive.joypad.JoypadProvider;
import omegadrive.util.PriorityThreadFactory;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.java.games.input.Component.Identifier.Button.*;

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
    }

    @Override
    public void reset() {
//        stop = true;
    }

    private void setDirectionOff() {
        if (playerNumber == 1) {
            joypadProvider.setU(EMU_OFF);
            joypadProvider.setD(EMU_OFF);
            joypadProvider.setL(EMU_OFF);
            joypadProvider.setR(EMU_OFF);
        } else if (playerNumber == 2) {
            joypadProvider.setU2(EMU_OFF);
            joypadProvider.setD2(EMU_OFF);
            joypadProvider.setL2(EMU_OFF);
            joypadProvider.setR2(EMU_OFF);
        }
    }

    private void handleEvent(Event event) {
        Component.Identifier id = event.getComponent().getIdentifier();
        double value = event.getValue();
        int emuButtonValue = value == ON ? EMU_ON : EMU_OFF;
        if (InputProvider.DEBUG_DETECTION) {
            LOG.info(id + ": " + value);
        }
        // xbox360: linux || windows
        if (X == id || _2 == id) {
            if (playerNumber == 1) {
                joypadProvider.setA(emuButtonValue);
            } else {
                joypadProvider.setA2(emuButtonValue);
            }
        }
        if (A == id || _0 == id) {
            if (playerNumber == 1) {
                joypadProvider.setB(emuButtonValue);
            } else {
                joypadProvider.setB2(emuButtonValue);
            }
        }
        if (B == id || _1 == id) {
            if (playerNumber == 1) {
                joypadProvider.setC(emuButtonValue);
            } else {
                joypadProvider.setC2(emuButtonValue);
            }
        }
        if (START == id || _7 == id) {
            if (playerNumber == 1) {
                joypadProvider.setS(emuButtonValue);
            } else {
                joypadProvider.setS2(emuButtonValue);
            }
        }
        if (pov.equals(id.getName())) {
            if (value == Component.POV.OFF) {
                setDirectionOff();
            }
            if (value == Component.POV.DOWN) {
                if (playerNumber == 1) {
                    joypadProvider.setD(EMU_ON);
                } else {
                    joypadProvider.setD2(EMU_ON);
                }
            }
            if (value == Component.POV.DOWN_LEFT) {
                if (playerNumber == 1) {
                    joypadProvider.setD(EMU_ON);
                    joypadProvider.setL(EMU_ON);
                } else {
                    joypadProvider.setD2(EMU_ON);
                    joypadProvider.setL2(EMU_ON);
                }
            }
            if (value == Component.POV.DOWN_RIGHT) {
                if (playerNumber == 1) {
                    joypadProvider.setD(EMU_ON);
                    joypadProvider.setR(EMU_ON);
                } else {
                    joypadProvider.setD2(EMU_ON);
                    joypadProvider.setR2(EMU_ON);
                }
            }
            if (value == Component.POV.UP) {
                if (playerNumber == 1) {
                    joypadProvider.setU(EMU_ON);
                } else {
                    joypadProvider.setU2(EMU_ON);
                }
            }
            if (value == Component.POV.UP_LEFT) {
                if (playerNumber == 1) {
                    joypadProvider.setU(EMU_ON);
                    joypadProvider.setL(EMU_ON);
                } else {
                    joypadProvider.setU2(EMU_ON);
                    joypadProvider.setL2(EMU_ON);
                }
            }
            if (value == Component.POV.UP_RIGHT) {
                if (playerNumber == 1) {
                    joypadProvider.setU(EMU_ON);
                    joypadProvider.setR(EMU_ON);
                } else {
                    joypadProvider.setU2(EMU_ON);
                    joypadProvider.setR2(EMU_ON);
                }
            }
            if (value == Component.POV.LEFT) {
                if (playerNumber == 1) {
                    joypadProvider.setL(EMU_ON);
                } else {
                    joypadProvider.setL2(EMU_ON);
                }
            }
            if (value == Component.POV.RIGHT) {
                if (playerNumber == 1) {
                    joypadProvider.setR(EMU_ON);
                } else {
                    joypadProvider.setR2(EMU_ON);
                }
            }
        }
    }
}
