package omegadrive.input.swing;

import net.java.games.input.Component;
import net.java.games.input.Component.Identifier.Key;
import net.java.games.input.Controller;
import omegadrive.input.InputProvider;
import omegadrive.input.KeyboardInputHelper;
import omegadrive.input.jinput.JinputGamepadInputProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.JoypadProvider.JoypadButton;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.awt.event.KeyEvent.KEY_PRESSED;
import static omegadrive.input.jinput.JinputGamepadInputProvider.*;
import static omegadrive.input.swing.JInputUtil.NO_CONTROLLER;
import static omegadrive.input.swing.JInputUtil.*;
import static omegadrive.joypad.JoypadProvider.JoypadAction.PRESSED;
import static omegadrive.joypad.JoypadProvider.JoypadAction.RELEASED;

/**
 * GamepadSetupView
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * TODO pov, load/store, sensible defaults
 */
public class GamepadSetupView implements InputEventCallback {

    private static final Logger LOG = LogHelper.getLogger(GamepadSetupView.class.getSimpleName());

    enum ButtonAction {EXIT, SAVE_EXIT, SAVE}

    private JFrame frame;
    public final InputProvider inputProvider;
    private final List<Controller> controllers;
    private JComboBox<Controller> controllerSelector;
    private JInputUtil.ActiveControllerCtx ctrCtx;
    private final GamepadSetupGui swingGui;

    private GamepadSetupView(InputProvider inputProvider) {
        controllers = new ArrayList<>(detectControllers());
        controllers.add(0, NO_CONTROLLER);
        controllers.add(1, VIRTUAL_KEYBOARD);
        this.inputProvider = inputProvider;
        this.swingGui = new GamepadSetupGui();
        init();
    }

    public static GamepadSetupView createInstance(InputProvider inputProvider) {
        return new GamepadSetupView(inputProvider);
    }

    private void init() {
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame();
            frame.add(swingGui.getBasePanel());
            controllerSelector = swingGui.getControllerSelectorComboBox();
            controllerSelector.removeAll();
            controllers.forEach(c -> controllerSelector.insertItemAt(c, controllerSelector.getItemCount()));
            controllerSelector.setSelectedIndex(0);
            controllerSelector.addActionListener(e -> rebuildPanel((Controller) controllerSelector.getSelectedItem()));
            swingGui.getExitButton().addActionListener(e -> buttonEvent(e, ButtonAction.EXIT));
            swingGui.getSaveButton().addActionListener(e -> buttonEvent(e, ButtonAction.SAVE));
            swingGui.getSaveExitButton().addActionListener(e -> buttonEvent(e, ButtonAction.SAVE_EXIT));
            AWTEventListener awtEventListener = e -> {
                if (e instanceof KeyEvent ke) {
                    keyboardEvent(ke, ke.getID() == KEY_PRESSED ? AXIS_p1 : AXIS_0);
                }
            };
            Toolkit.getDefaultToolkit().addAWTEventListener(awtEventListener, AWTEvent.KEY_EVENT_MASK);
            rebuildPanel(NO_CONTROLLER);
            frame.setTitle("Gamepad Setup Viewer");
            frame.pack();
            frame.setVisible(true);
        });
    }

    private void keyboardEvent(KeyEvent e, double value) {
        Key key = awtJInputKeyMap.getOrDefault(e.getKeyCode(), null);
        if (key == null) {
            LOG.warn("Ignoring keyEvent: {}", e);
            return;
        }
        update(VIRTUAL_KEYBOARD, "Key: " + key.getName(), key, value);
    }

    private void buttonEvent(ActionEvent e, ButtonAction action) {
        switch (action) {
            case EXIT, SAVE_EXIT -> frame.dispose();
            case SAVE -> System.out.println("oops");
        }
    }

    private void rebuildPanel(Controller c) {
        ctrCtx = new ActiveControllerCtx(c);
        JInputId[] cmps = new JInputId[0];
        if (c == VIRTUAL_KEYBOARD) {
            cmps = Arrays.stream(VIRTUAL_KEYS).map(k -> new JInputId(k.getName(), k.getClass())).toArray(JInputId[]::new);
        } else if (c.getComponents().length > 0) {
            cmps = Arrays.stream(c.getComponents()).map(cmp -> new JInputId(cmp.getIdentifier())).toArray(JInputId[]::new);
        }
        ctrCtx.joyNames = Arrays.stream(cmps).map(Objects::toString).toArray(String[]::new);
        int numVirtualButtons = ctrCtx.jb.length;
        JPanel consolePad = swingGui.getConsolePadPanel();
        consolePad.removeAll();
        JPanel hwPad = swingGui.getHwPadPanel();
        hwPad.removeAll();
        JScrollPane hwPadButtonsScrollPane = swingGui.getJoyButtonsScrollPane();

        JPanel hwPadButtonsPanel = new JPanel();
        hwPadButtonsPanel.setLayout(new GridLayout(cmps.length, 1));
        hwPadButtonsScrollPane.getViewport().setView(hwPadButtonsPanel);
        consolePad.setLayout(new GridLayout(numVirtualButtons, 1));
        hwPad.setLayout(new GridLayout(numVirtualButtons, 1));
        int idx = 0;

        ctrCtx.padLabels = new JLabel[cmps.length];
        ctrCtx.consolePadLabels = new JLabel[numVirtualButtons];
        ctrCtx.padSelectedBox = new JComboBox[numVirtualButtons];
        for (int i = 0; i < numVirtualButtons; i++) {
            ctrCtx.consolePadLabels[idx] = new JLabel(ctrCtx.jb[idx].getMnemonic());
            ctrCtx.consolePadLabels[idx].setOpaque(true);
            ctrCtx.padSelectedBox[idx] = new JComboBox<>(ctrCtx.joyNames);
            consolePad.add(ctrCtx.consolePadLabels[idx]);
            hwPad.add(ctrCtx.padSelectedBox[idx]);
            idx++;
        }
        idx = 0;
        for (JInputId cmpId : cmps) {
            String name = cmpId.toString();
            ctrCtx.padLabels[idx] = new JLabel(name);
            ctrCtx.padLabels[idx].setOpaque(true);
            JPanel miniPanel = new JPanel();
            miniPanel.add(ctrCtx.padLabels[idx]);
            if (cmpId.type == Component.Identifier.Axis.class) {
                JCheckBox invertCb = new JCheckBox("Inverted");
                invertCb.addChangeListener(e -> ctrCtx.invertedMap.put(name, invertCb.isSelected()));
                miniPanel.add(invertCb);
                ctrCtx.invertedMap.put(name, false);
            }
            hwPadButtonsPanel.add(miniPanel);
            idx++;
        }
        hwPadButtonsScrollPane.repaint();
        swingGui.getDetailsTextArea().setEditable(false);
        swingGui.getDetailsTextArea().setText(JinputGamepadInputProvider.detectControllerVerbose(ctrCtx.c));
        frame.invalidate();
        frame.pack();
        SwingUtilities.invokeLater(() -> {
            for (var e : KeyboardInputHelper.DEFAULT_P1_KEY_BINDINGS.entrySet()) {
                Key k = awtJInputKeyMap.getOrDefault(e.getValue(), null);
                JoypadButton b = getJoypadButton(e.getKey());
                int jidx = -1;
                for (int i = 0; i < ctrCtx.jb.length; i++) {
                    if (ctrCtx.jb[i] == b) {
                        jidx = i;
                        break;
                    }
                }
                ctrCtx.padSelectedBox[jidx].setSelectedItem(toString(k));
            }
        });
    }

    public void updateDone() {
        if (swingGui.getBasePanel() != null) {
            swingGui.getBasePanel().repaint();
        }
    }

    private void update(Controller c, String name, Component.Identifier id, double value) {
        assert ctrCtx != null;
        if (!Objects.equals(controllerSelector.getSelectedItem(), c)) {
            return;
        }
        JoypadProvider.JoypadAction action = getAction(id, value);
        Color baseColor = swingGui.getBasePanel().getBackground();
        for (int i = 0; i < ctrCtx.joyNames.length; i++) {
            ctrCtx.padLabels[i].setBackground(baseColor);
            if (ctrCtx.joyNames[i].equalsIgnoreCase(name)) {
                ctrCtx.padLabels[i].setBackground(action == PRESSED ? Color.GREEN : baseColor);
            }
        }
        handleConsoleButtonsHighlight(c, name, id, value);
    }

    @Override
    public void update(Object callback) {
        if (!(callback instanceof UpdatedGamepadCtx ctx)) {
            LOG.warn("Ignoring unexpected callback: {}", callback.getClass());
            return;
        }
        assert ctrCtx != null;
        if (!Objects.equals(controllerSelector.getSelectedItem(), ctx.c)) {
            return;
        }
        Component.Identifier id = ctx.event.getComponent().getIdentifier();
        update(ctx.c, toString(id), id, ctx.event.getValue());
    }

    private void handleConsoleButtonsHighlight(Controller c, String name, Component.Identifier id, double value) {
        JoypadProvider.JoypadAction action = getAction(id, value);
        Color baseColor = swingGui.getBasePanel().getBackground();
        Color matchColor = action == PRESSED ? Color.GREEN : baseColor;
        for (int i = 0; i < ctrCtx.consolePadLabels.length; i++) {
            JoypadButton jb = getJoypadButton(ctrCtx.consolePadLabels[i].getText());
            assert jb != null;
            Color actualColor = baseColor;
            String sel = Objects.toString(ctrCtx.padSelectedBox[i].getSelectedItem(), "EMPTY");
            if (sel.equalsIgnoreCase(name)) {
                if (jb.isDirection() && id instanceof Component.Identifier.Axis) {
                    boolean inverted = ctrCtx.invertedMap.getOrDefault(name, false);
                    if (jb == JoypadButton.R || jb == JoypadButton.D) {
                        double match = inverted ? AXIS_m1 : AXIS_p1;
                        if (value == match) {
                            actualColor = matchColor;
                        }
                    } else if (jb == JoypadButton.L || jb == JoypadButton.U) {
                        double match = inverted ? AXIS_p1 : AXIS_m1;
                        if (value == match) {
                            actualColor = matchColor;
                        }
                    }
                } else {
                    actualColor = matchColor;
                }
            }
            ctrCtx.consolePadLabels[i].setBackground(actualColor);
        }
    }

    private JoypadButton getJoypadButton(String text) {
        for (JoypadButton b : JoypadButton.vals) {
            if (b.getMnemonic().equals(text) || b.name().equals(text)) {
                return b;
            }
        }
        return null;
    }

    private JoypadProvider.JoypadAction getAction(Component.Identifier id, double value) {
        if (id instanceof Component.Identifier.Axis) {
            if (POV.equalsIgnoreCase(id.getName())) {
                throw new RuntimeException(POV);
            } else {
                return value == AXIS_0 ? RELEASED : PRESSED;
            }
        } else if (id instanceof Component.Identifier.Button) {
            return value == ON ? JoypadProvider.JoypadAction.PRESSED : JoypadProvider.JoypadAction.RELEASED;
        } else if (id instanceof Key) {
            return value == ON ? JoypadProvider.JoypadAction.PRESSED : JoypadProvider.JoypadAction.RELEASED;
        } else {
            throw new RuntimeException(id + ": " + value);
        }
    }

    public static String toString(Component.Identifier cmpId) {
        return toString(cmpId.getName(), cmpId.getClass());
    }

    public static String toString(String name, Class<?> clazz) {
        return clazz.getSimpleName() + ": " + name;
    }
}