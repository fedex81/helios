package omegadrive.joypad;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface JoypadProvider {

    enum JoypadAction {
        PRESSED,
        RELEASED
    }

    enum JoypadNumber {
        P1, P2
    }

    enum JoypadType {
        BUTTON_3,
        BUTTON_6
    }

    enum JoypadButton {
        A, B, C, X, Y, Z, M, S, U, D, L, R
    }

    void initialize();

    int readDataRegister1();

    int readDataRegister2();

    int readDataRegister3();

    long readControlRegister1();

    long readControlRegister2();

    long readControlRegister3();

    void writeDataRegister1(long data);

    void writeDataRegister2(long data);

    void writeControlRegister1(long data);

    void writeControlRegister2(long data);

    void writeControlRegister3(long data);

    void setButtonAction(JoypadNumber number, JoypadButton button, JoypadAction action);

    boolean hasDirectionPressed(JoypadNumber number);

    String getState(JoypadNumber number);
}
