package omegadrive.joypad;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static omegadrive.joypad.JoypadProvider.JoypadAction.PRESSED;
import static omegadrive.joypad.JoypadProvider.JoypadAction.RELEASED;
import static omegadrive.joypad.JoypadProvider.JoypadButton.*;

/**
 * TwoButtonsJoypad
 *
 * @author Federico Berti
 * <p>
 * <p>
 * **
 * * 0xDC / 0xC0 : Joypad Port 1 (read-only)
 * * bit 0 : Joypad 1 Up
 * * bit 1 : Joypad 1 Down
 * * bit 2 : Joypad 1 Left
 * * bit 3 : Joypad 1 Right
 * * bit 4 : Joypad 1 Button 1
 * * bit 5 : Joypad 1 Button 2
 * * bit 6 : Joypad 2 Up
 * * bit 7 : Joypad 2 Down
 * * Low logic port. 0 = pressed, 1 = released
 * * <p>
 * * 0xDD / 0xC1 : Joypad Port 2 (read-only)
 * * bit 0 : Joypad 2 Left
 * * bit 1 : Joypad 2 Right
 * * bit 2 : Joypad 2 Button 1
 * * bit 3 : Joypad 2 Button 2
 * * bit 4 : Reset Button
 * * Low logic port. 0 = pressed, 1 = released
 * * With no external devices attached, bits 7,6,5 return 0,1,1 respectively.
 * *
 * <p>
 * /TODO reset button
 */
public class TwoButtonsJoypad implements JoypadProvider {

    private static Logger LOG = LogManager.getLogger(TwoButtonsJoypad.class.getSimpleName());

    JoypadType p1Type = JoypadType.BUTTON_2;
    JoypadType p2Type = JoypadType.BUTTON_2;

    private Map<JoypadButton, JoypadAction> stateMap1 = Maps.newHashMap(ImmutableMap.<JoypadButton, JoypadAction>builder().
            put(D, RELEASED).put(U, RELEASED).
            put(L, RELEASED).put(R, RELEASED).
            put(A, RELEASED).put(B, RELEASED).build());

    private Map<JoypadButton, JoypadAction> stateMap2 = Maps.newHashMap(stateMap1);

    private int value1 = 0xFF;
    private int value2 = 0xFF;

    private static JoypadButton[] directionButton = {D, L, R, U};

    public void initialize() {
        LOG.info("Joypad1: {} - Joypad2: {}", p1Type, p2Type);
    }

    public int readDataRegister1() {
        return value1;
    }

    public int readDataRegister2() {
        return value2;
    }

    private int get2D_2U_1B_1A_1R_1L_1D_1U() {
        return (getValue(JoypadNumber.P2, D) << 7) | (getValue(JoypadNumber.P2, U) << 6) |
                (getValue(JoypadNumber.P1, B) << 5) | (getValue(JoypadNumber.P1, A) << 4) | (getValue(JoypadNumber.P1, R) << 3) |
                (getValue(JoypadNumber.P1, L) << 2) | (getValue(JoypadNumber.P1, D) << 1) | (getValue(JoypadNumber.P1, U));
    }

    //TODO reset
    //011 RESET 2B 2A 2R 2L
    private int getR_2B_2A_2R_2L() {
        return 0xE0 | 1 << 4 | (getValue(JoypadNumber.P2, B) << 3) | (getValue(JoypadNumber.P2, A) << 2) |
                (getValue(JoypadNumber.P2, R) << 1) | (getValue(JoypadNumber.P2, L));
    }

    private int getValue(JoypadNumber number, JoypadButton button) {
        return getMap(number).get(button).ordinal();
    }

    @Override
    public void setButtonAction(JoypadNumber number, JoypadButton button, JoypadAction action) {
        getMap(number).put(button, action);
    }

    public boolean hasDirectionPressed(JoypadNumber number) {
        return Arrays.stream(directionButton).anyMatch(b -> getMap(number).get(b) == PRESSED);
    }

    private Map<JoypadButton, JoypadAction> getMap(JoypadNumber number) {
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

    @Override
    public String getState(JoypadNumber number) {
        return getMap(number).toString();
    }

    //UNUSED

    @Override
    public void newFrame() {
        value1 = get2D_2U_1B_1A_1R_1L_1D_1U();
        value2 = getR_2B_2A_2R_2L();
    }


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


    public int readDataRegister3() {
        return 0x3F;
    }

    public void writeControlRegister1(long data) {
    }

    public void writeControlRegister2(long data) {
    }

    public void writeControlRegister3(long data) {
    }
}
