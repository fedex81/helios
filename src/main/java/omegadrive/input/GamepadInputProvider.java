package omegadrive.input;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;
import omegadrive.joypad.JoypadProvider;
import omegadrive.util.PriorityThreadFactory;
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
            Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY - 1, GamepadInputProvider.class.getSimpleName()));
    private long POLLING_INTERVAL_MS = 10;

    private JoypadProvider joypadProvider;
    private Controller controller;
    private volatile boolean stop = false;
    private volatile int playerNumber = 1;
    private String pov = Axis.POV.getName();

    public GamepadInputProvider(Controller controller, JoypadProvider joypadProvider) {
        this.joypadProvider = joypadProvider;
        this.controller = controller;
        this.setPlayers(1);
        this.executorService.submit(inputRunnable());
    }

    private Runnable inputRunnable() {
        return () -> {
            LOG.info("Starting controller polling, interval (ms): " + POLLING_INTERVAL_MS);
            do {
                handleEvents();
                try {
                    Thread.sleep(POLLING_INTERVAL_MS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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
        double value = event.getComponent().getPollData();
        int emuValue = value == ON ? EMU_ON : EMU_OFF;

        if (X == id) {
            if (playerNumber == 1) {
                joypadProvider.setA(emuValue);
            } else {
                joypadProvider.setA2(emuValue);
            }
        }
        if (A == id) {
            if (playerNumber == 1) {
                joypadProvider.setB(emuValue);
            } else {
                joypadProvider.setB2(emuValue);
            }
        }
        if (B == id) {
            if (playerNumber == 1) {
                joypadProvider.setC(emuValue);
            } else {
                joypadProvider.setC2(emuValue);
            }
        }
        if (START == id) {
            if (playerNumber == 1) {
                joypadProvider.setS(emuValue);
            } else {
                joypadProvider.setS2(emuValue);
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
        } else {
//            System.out.println(id + "," + value);
        }
    }
}
