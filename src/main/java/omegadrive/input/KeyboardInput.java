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
public class KeyboardInput extends KeyAdapter {

    protected static KeyboardInput currentAdapter;

    protected JoypadProvider provider;

    @Override
    public void keyPressed(KeyEvent e) {
        keyHandler(provider, e, true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        keyHandler(provider, e, false);
    }

    public static KeyAdapter createKeyAdapter(JoypadProvider provider) {
        Objects.requireNonNull(provider);
        if (currentAdapter == null) {
            currentAdapter = new KeyboardInput();
        }
        currentAdapter.provider = provider;
        return currentAdapter;
    }

    protected static void keyHandler(JoypadProvider joypad, KeyEvent e, boolean pressed) {
        JoypadProvider.JoypadAction action = pressed ? JoypadProvider.JoypadAction.PRESSED : JoypadProvider.JoypadAction.RELEASED;
        JoypadProvider.JoypadNumber number = null;
        JoypadProvider.JoypadButton button = null;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.U;
                break;
            case KeyEvent.VK_LEFT:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.L;
                break;
            case KeyEvent.VK_RIGHT:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.R;
                break;
            case KeyEvent.VK_DOWN:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.D;
                break;
            case KeyEvent.VK_ENTER:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.S;
                break;
            case KeyEvent.VK_A:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.A;
                break;
            case KeyEvent.VK_S:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.B;
                break;
            case KeyEvent.VK_D:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.C;
                break;
            case KeyEvent.VK_Q:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.X;
                break;
            case KeyEvent.VK_W:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.Y;
                break;
            case KeyEvent.VK_E:
                number = JoypadProvider.JoypadNumber.P1;
                button = JoypadProvider.JoypadButton.Z;
                break;
        }
        if (number != null && button != null) {
            joypad.setButtonAction(number, button, action);
        }
    }

}
