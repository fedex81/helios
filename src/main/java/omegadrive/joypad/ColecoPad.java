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
 * ColecoPad
 *
 * @author Federico Berti
 * <p>
 */
public class ColecoPad implements JoypadProvider {

    private static Logger LOG = LogManager.getLogger(ColecoPad.class.getSimpleName());

    JoypadType p1Type = JoypadType.BUTTON_2;
    JoypadType p2Type = JoypadType.BUTTON_2;

    private Map<JoypadButton, JoypadAction> stateMap1 = Maps.newHashMap(ImmutableMap.<JoypadButton, JoypadAction>builder().
            put(D, RELEASED).put(U, RELEASED).
            put(L, RELEASED).put(R, RELEASED).
            put(A, RELEASED).put(B, RELEASED).build());

    private Map<JoypadButton, JoypadAction> stateMap2 = Maps.newHashMap(stateMap1);

    private int value1 = 0xFF;
    private int value2 = 0xFF;

    private boolean mode80 = false;

    private static JoypadButton[] directionButton = {D, L, R, U};

    public void initialize() {
        LOG.info("Joypad1: {} - Joypad2: {}", p1Type, p2Type);
    }

    public int readDataRegister1() {
        return mode80 ? getMode80(JoypadNumber.P1) : getModeC0(JoypadNumber.P1);
    }

    public int readDataRegister2() {
        return mode80 ? getMode80(JoypadNumber.P2) : getModeC0(JoypadNumber.P2);
    }

    /**
     * 'C0' mode  (port C0 written to)
     * <p>
     * <p>
     * This mode allows you to read the stick and left button:
     * <p>
     * <p>
     * Bit 6=Left button
     * Bit 0=Left
     * Bit 1=Down
     * Bit 2=Right
     * Bit 3=Up
     *
     * @return
     */
    private int getModeC0(JoypadNumber number) {
        return 0x30 | (getValue(number, A) << 6) | (getValue(number, U) << 3) |
                (getValue(number, R) << 2) | (getValue(number, D) << 1) | (getValue(number, L));
    }

    /**
     * '80' mode  (port 80 written to)
     * <p>
     * <p>
     * bit #6= status of right button
     * <p>
     * The keypad returns a 4-bit binary word for a button pressed:
     * <p>
     * bit #
     * <p>
     * btn:  0   1   2   3
     * -----------------------
     * 0     0   1   0   1
     * 1     1   0   1   1
     * 2     1   1   1   0
     * 3     0   0   1   1
     * 4     0   1   0   0
     * 5     1   1   0   0
     * 6     0   1   1   1
     * 7     1   0   1   0
     * 8     1   0   0   0
     * 9     1   1   0   1
     * *     1   0   0   1
     * #     0   1   1   0
     * <p>
     * <p>
     * Re-arranged, in order:
     * <p>
     * <p>
     * <p>
     * btn:  0   1   2   3   hex#
     * -------------------------------
     * inv.  0   0   0   0    0
     * 8     1   0   0   0    1
     * 4     0   1   0   0    2
     * 5     1   1   0   0    3
     * inv.  0   0   1   0    4
     * 7     1   0   1   0    5
     * #     0   1   1   0    6
     * 2     1   1   1   0    7
     * inv.  0   0   0   1    8
     * *     1   0   0   1    9
     * 0     0   1   0   1    A
     * 9     1   1   0   1    B
     * 3     0   0   1   1    C
     * 1     1   0   1   1    D
     * 6     0   1   1   1    E
     * inv.  1   1   1   1    F    (No buttons down)
     *
     * @return
     */
    private int getMode80(JoypadNumber number) {
        return 0x30 | (getValue(number, B) << 6) | 0xF;
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
    }


    public void writeDataRegister1(long data) {
        mode80 = (data & 0x80) == 0x80;
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
