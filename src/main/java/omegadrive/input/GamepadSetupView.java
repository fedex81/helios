package omegadrive.input;

import net.java.games.input.Component;
import net.java.games.input.Event;
import net.java.games.input.*;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.JoypadProvider.JoypadButton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static omegadrive.input.InputProvider.ON;
import static omegadrive.input.jinput.JinputGamepadInputProvider.*;
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

    private static final Logger LOG = LogManager.getLogger(GamepadSetupView.class.getSimpleName());

    private static final Controller NO_CONTROLLER = new AbstractController("NONE",
            new Component[0], new Controller[0], new Rumbler[0]) {
        @Override
        protected boolean getNextDeviceEvent(Event event) {
            return false;
        }
    };


    static class JInputId {
        Component cmp;

        public JInputId(Component cmp) {
            this.cmp = cmp;
        }

        @Override
        public String toString() {
            return GamepadSetupView.toString(cmp);
        }
    }

    static class ActiveControllerCtx {
        public Controller c;
        public String[] joyNames;
        public JLabel[] padLabels, consolePadLabels;
        public JComboBox<String>[] padSelectedBox;
        public JoypadButton[] jb;
        public Map<String, Boolean> invertedMap;

        public ActiveControllerCtx(Controller c) {
            this.c = c;
            joyNames = Arrays.stream(c.getComponents()).map(cm ->
                    cm.getIdentifier().getClass().getSimpleName() + ": " + cm.getName()).toArray(String[]::new);
            jb = Arrays.stream(JoypadButton.values()).filter(jb -> !jb.name().startsWith("K")).
                    toArray(JoypadButton[]::new);
            invertedMap = new HashMap<>();
        }
    }

    private JFrame frame;
    private JPanel panel, mapPanel;
    public InputProvider inputProvider;
    private List<Controller> controllers;
    private JComboBox<Controller> controllerSelector;
    private ActiveControllerCtx ctrCtx;

    private GamepadSetupView(InputProvider inputProvider) {
        controllers = new ArrayList<>(detectControllers());
        controllers.add(0, NO_CONTROLLER);
        this.inputProvider = inputProvider;
        init();
    }

    public static GamepadSetupView createInstance(InputProvider inputProvider) {
        return new GamepadSetupView(inputProvider);
    }

    private void init() {
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame();
            this.panel = new JPanel();
            this.panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(Color.GRAY);
            controllerSelector = new JComboBox<>(controllers.toArray(Controller[]::new));
            controllerSelector.addActionListener(e -> rebuildPanel((Controller) controllerSelector.getSelectedItem()));
            rebuildPanel(NO_CONTROLLER);
            frame.setTitle("Gamepad Setup Viewer");
            frame.pack();
            frame.setVisible(true);
        });
    }

    private void rebuildPanel(Controller c) {
        frame.remove(panel);
        panel.removeAll();

        this.panel = new JPanel();
        this.panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        ctrCtx = new ActiveControllerCtx(c);
        JLabel lbl = new JLabel(ctrCtx.c.getType() + "," + ctrCtx.c.getPortType() + "," + ctrCtx.c.getName() + ","
                + ctrCtx.c.getComponents().length + " components");
        lbl.setOpaque(true);
        lbl.setBackground(Color.ORANGE);
        panel.add(controllerSelector, 0);
        panel.add(lbl, 1);
        JPanel pnl1 = new JPanel(new GridLayout(1, 2));
        int numVirtualButtons = ctrCtx.jb.length;
        mapPanel = new JPanel(new GridLayout(numVirtualButtons + 1, 2));
        mapPanel.add(new JLabel("Console Button"));
        mapPanel.add(new JLabel("Maps to Joypad Button"));
        JPanel allJoyButtons = new JPanel(new GridLayout(numVirtualButtons + 1, 1));
        allJoyButtons.add(new JLabel("All Joypad Buttons"));
        int idx = 0;
        Component[] cmps = ctrCtx.c.getComponents();
        ctrCtx.padLabels = new JLabel[cmps.length];
        ctrCtx.consolePadLabels = new JLabel[numVirtualButtons];
        ctrCtx.padSelectedBox = new JComboBox[numVirtualButtons];
        for (int i = 0; i < numVirtualButtons; i++) {
            ctrCtx.consolePadLabels[idx] = new JLabel(ctrCtx.jb[idx].getMnemonic());
            ctrCtx.consolePadLabels[idx].setOpaque(true);
            ctrCtx.padSelectedBox[idx] = new JComboBox<>(ctrCtx.joyNames);
            mapPanel.add(ctrCtx.consolePadLabels[idx]);
            mapPanel.add(ctrCtx.padSelectedBox[idx]);
            idx++;
        }
        idx = 0;
        for (Component cmp : cmps) {
            String name = new JInputId(cmp).toString();
            ctrCtx.padLabels[idx] = new JLabel(name);
            ctrCtx.padLabels[idx].setOpaque(true);
            JPanel miniPanel = new JPanel();
            miniPanel.add(ctrCtx.padLabels[idx]);
            if (cmp.getIdentifier() instanceof Component.Identifier.Axis) {
                JCheckBox invertCb = new JCheckBox("Inverted");
                invertCb.addChangeListener(e -> ctrCtx.invertedMap.put(name, invertCb.isSelected()));
                miniPanel.add(invertCb);
                ctrCtx.invertedMap.put(name, false);
            }
            allJoyButtons.add(miniPanel);
            idx++;
        }
        pnl1.add(mapPanel);
        pnl1.add(allJoyButtons);
        this.panel.add(pnl1);
        this.panel.setSize(new Dimension(400, 400));
        frame.add(panel);
        frame.setMinimumSize(panel.getSize());
        frame.pack();
    }

    public void updateDone() {
        if (panel != null) {
            panel.repaint();
        }
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
        String name = toString(ctx.event.getComponent());
        Component.Identifier id = ctx.event.getComponent().getIdentifier();
        double value = ctx.event.getValue();
        JoypadProvider.JoypadAction action = getAction(id, value);
        Color baseColor = mapPanel.getBackground();
        for (int i = 0; i < ctrCtx.joyNames.length; i++) {
            ctrCtx.padLabels[i].setBackground(baseColor);
            if (ctrCtx.joyNames[i].equalsIgnoreCase(name)) {
                ctrCtx.padLabels[i].setBackground(action == PRESSED ? Color.GREEN : baseColor);
            }
        }
        handleConsoleButtonsHighlight(ctx);
    }

    private void handleConsoleButtonsHighlight(UpdatedGamepadCtx ctx) {
        String name = toString(ctx.event.getComponent());
        Component.Identifier id = ctx.event.getComponent().getIdentifier();
        double value = ctx.event.getValue();
        JoypadProvider.JoypadAction action = getAction(id, value);
        Color baseColor = mapPanel.getBackground();
        Color matchColor = action == PRESSED ? Color.GREEN : baseColor;
        for (int i = 0; i < ctrCtx.consolePadLabels.length; i++) {
            JoypadButton jb = getJoypadButton(ctrCtx.consolePadLabels[i].getText());
            Color actualColor = baseColor;
            String sel = Objects.toString(ctrCtx.padSelectedBox[i].getSelectedItem(), "EMPTY");
            if (sel.equalsIgnoreCase(name)) {
                if (isDirection(jb) && id instanceof Component.Identifier.Axis) {
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
            if (b.getMnemonic().equals(text)) {
                return b;
            }
        }
        return null;
    }

    private static boolean isDirection(JoypadButton jb) {
        return JoypadButton.D == jb || JoypadButton.U == jb ||
                JoypadButton.R == jb || JoypadButton.L == jb;
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
        } else {
            throw new RuntimeException(id + ": " + value);
        }
    }

    public static String toString(Component cmp) {
        return cmp.getIdentifier().getClass().getSimpleName() + ": " + cmp.getName();
    }
}