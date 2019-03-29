package omegadrive.input;

import omegadrive.joypad.JoypadProvider;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Objects;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class ColecoKeyboardInput extends KeyboardInput {

    @Override
    public void keyPressed(KeyEvent e) {
        keyHandlerColeco(provider, e, true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        keyHandlerColeco(provider, e, false);
    }

    public static KeyAdapter createColecoKeyAdapter(JoypadProvider provider) {
        Objects.requireNonNull(provider);
        if (currentAdapter == null) {
            currentAdapter = new ColecoKeyboardInput();
        }
        currentAdapter.provider = provider;
        return currentAdapter;
    }

    private static void keyHandlerColeco(JoypadProvider joypad, KeyEvent e, boolean pressed) {
        KeyboardInput.keyHandler(joypad, e, pressed);

        JoypadProvider.JoypadAction action = pressed ? JoypadProvider.JoypadAction.PRESSED : JoypadProvider.JoypadAction.RELEASED;
        JoypadProvider.JoypadNumber number = JoypadProvider.JoypadNumber.P1;
        JoypadProvider.JoypadButton button = null;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_0:
                button = JoypadProvider.JoypadButton.K0;
                break;
            case KeyEvent.VK_1:
                button = JoypadProvider.JoypadButton.K1;
                break;
            case KeyEvent.VK_2:
                button = JoypadProvider.JoypadButton.K2;
                break;
            case KeyEvent.VK_3:
                button = JoypadProvider.JoypadButton.K3;
                break;
            case KeyEvent.VK_4:
                button = JoypadProvider.JoypadButton.K4;
                break;
            case KeyEvent.VK_5:
                button = JoypadProvider.JoypadButton.K5;
                break;
            case KeyEvent.VK_6:
                button = JoypadProvider.JoypadButton.K6;
                break;
            case KeyEvent.VK_7:
                button = JoypadProvider.JoypadButton.K7;
                break;
            case KeyEvent.VK_8:
                button = JoypadProvider.JoypadButton.K8;
                break;
            case KeyEvent.VK_9:
                button = JoypadProvider.JoypadButton.K9;
                break;
            case KeyEvent.VK_MINUS:
                button = JoypadProvider.JoypadButton.K_AST;
                break;
            case KeyEvent.VK_EQUALS:
                button = JoypadProvider.JoypadButton.K_HASH;
                break;
        }
        if (number != null && button != null) {
            joypad.setButtonAction(number, button, action);
        }
    }
}
