package omegadrive.joypad;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static omegadrive.joypad.JoypadProvider.JoypadAction.PRESSED;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public abstract class BasePadAdapter implements JoypadProvider {

    private static Logger LOG = LogManager.getLogger(BasePadAdapter.class.getSimpleName());

    JoypadProvider.JoypadType p1Type;
    JoypadProvider.JoypadType p2Type;
    Map<JoypadButton, JoypadAction> stateMap1 = Collections.emptyMap();
    Map<JoypadButton, JoypadAction> stateMap2 = Collections.emptyMap();
    int value1 = 0xFF;
    int value2 = 0xFF;

    protected Map<JoypadButton, JoypadAction> getMap(JoypadNumber number) {
        switch (number) {
            case P1:
                return stateMap1;
            case P2:
                return stateMap2;
            default:
                LOG.error("Unexpected joypad number: {}", number);
                break;
        }
        return Collections.emptyMap();
    }

    protected int getValue(JoypadNumber number, JoypadButton button) {
        return getMap(number).get(button).ordinal();
    }

    @Override
    public void setButtonAction(JoypadNumber number, JoypadButton button, JoypadAction action) {
        getMap(number).put(button, action);
    }

    public boolean hasDirectionPressed(JoypadNumber number) {
        return Arrays.stream(directionButton).anyMatch(b -> getMap(number).get(b) == PRESSED);
    }

    @Override
    public String getState(JoypadNumber number) {
        return getMap(number).toString();
    }

    // ADAPTER

    public void writeDataRegister1(long data) {
    }

    public void writeDataRegister2(long data) {
    }

    public long readControlRegister1() {
        return 0xFF;
    }

    public long readControlRegister2() {
        return 0xFF;
    }

    public long readControlRegister3() {
        return 0xFF;
    }

    public int readDataRegister1() {
        return 0xFF;
    }

    public int readDataRegister2() {
        return 0xFF;
    }

    public int readDataRegister3() {
        return 0xFF;
    }

    public void writeControlRegister1(long data) {
    }

    public void writeControlRegister2(long data) {
    }

    public void writeControlRegister3(long data) {
    }
}
