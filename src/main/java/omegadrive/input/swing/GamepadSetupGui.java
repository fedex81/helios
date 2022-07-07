package omegadrive.input.swing;

import javax.swing.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class GamepadSetupGui {
    private JPanel topPanel;
    private JComboBox controllerSelectorComboBox;
    private JPanel mainPanel;
    private JPanel consolePadPanel;
    private JPanel hwPadPanel;
    private JScrollPane hwDetailsPanel;
    private JTextArea detailsTextArea;
    private JPanel bottomPanel;
    private JButton saveButton;
    private JButton saveExitButton;
    private JButton exitButton;
    private JPanel basePanel;
    private JScrollPane joyButtonsPanel;

    public JPanel getBasePanel() {
        return basePanel;
    }

    public JComboBox getControllerSelectorComboBox() {
        return controllerSelectorComboBox;
    }

    public JPanel getConsolePadPanel() {
        return consolePadPanel;
    }

    public JPanel getHwPadPanel() {
        return hwPadPanel;
    }

    public JTextArea getDetailsTextArea() {
        return detailsTextArea;
    }

    public JButton getSaveButton() {
        return saveButton;
    }

    public JButton getSaveExitButton() {
        return saveExitButton;
    }

    public JButton getExitButton() {
        return exitButton;
    }

    public JScrollPane getJoyButtonsScrollPane() {
        return joyButtonsPanel;
    }
}