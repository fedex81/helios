package omegadrive.joypad;

//	http://md.squee.co/315-5309
//	http://md.squee.co/Howto:Read_Control_Pads

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
 * GenesisJoypad
 *
 * @author Federico Berti
 * <p>
 * <p>
 * 6-button controller steps
 *                      Bit 5	Bit 4	Bit 3	Bit 2	Bit 1	Bit 0
 * 1st step (write $40)	    C	B	    Right	Left	Down	Up
 * 2nd step (write $00)	Start	A	    0	    0	    Down	Up
 * 3rd step (write $40)	    C	B	    Right	Left	Down	Up
 * 4th step (write $00)	Start	A	    0	    0	    Down	Up
 * 5th step (write $40)	    C	B	    Right	Left	Down	Up
 * 6th step (write $00)	Start	A	    0	    0	    0	    0
 * 7th step (write $40)	    C	B	    Mode	X	    Y	    Z
 * <p>
 * https://www.plutiedev.com/controllers
 *
 * TODO sgdk_joytest
 */
public class GenesisJoypad implements JoypadProvider {

    private static Logger LOG = LogManager.getLogger(GenesisJoypad.class.getSimpleName());

    static int SIX_BUTTON_STEPS = 8;  //7+1
    static int SIX_BUTTON_START_A_ONLY_STEP = 6;
    static int SIX_BUTTON_XYZ_STEP = 7;

    //SGDK needs 0 here, otherwise it is considered a RESET
    long control1 = 0;
    long control2 = 0;
    long control3 = 0;

    int readStep1 = 0;
    int readStep2 = 0;

    boolean asserted1;
    boolean asserted2;

    JoypadType p1Type = JoypadType.BUTTON_6;
    JoypadType p2Type = JoypadType.BUTTON_3;

    private Map<JoypadButton, JoypadAction> stateMap1 = Maps.newHashMap(ImmutableMap.<JoypadButton, JoypadAction>builder().
            put(D, RELEASED).put(U, RELEASED).
            put(L, RELEASED).put(R, RELEASED).
            put(S, RELEASED).put(A, RELEASED).
            put(B, RELEASED).put(C, RELEASED).
            put(M, RELEASED).put(X, RELEASED).
            put(Y, RELEASED).put(Z, RELEASED).build());

    private Map<JoypadButton, JoypadAction> stateMap2 = Maps.newHashMap(stateMap1);


    private static JoypadButton[] directionButton = {D, L, R, U};

    public void initialize() {
        writeDataRegister1(0x40);
        writeDataRegister2(0x40);
        LOG.info("Joypad1: {} - Joypad2: {}", p1Type, p2Type);
    }

    public void writeDataRegister1(long data) {
        asserted1 = (data & 0x40) == 0;
        boolean reset = data == 0 && readStep1 == 0;
        readStep1 = reset ? 0 : (readStep1 + 1) % SIX_BUTTON_STEPS;
//        LOG.info("write p1: asserted : {}, step : {}, data: {}", asserted1, readStep1, data);
    }

    public void writeDataRegister2(long data) {
        asserted2 = (data & 0x40) == 0;
        boolean reset = data == 0 && readStep2 == 0;
        readStep2 = reset ? 0 : (readStep2 + 1) % SIX_BUTTON_STEPS;
    }

    public int readDataRegister1() {
        return readDataRegister(JoypadNumber.P1, p1Type, asserted1, readStep1);
//        LOG.info("read p1: asserted : {}, step : {}, result: {}", asserted1, readStep1, res);
    }

    public int readDataRegister2() {
        return readDataRegister(JoypadNumber.P2, p2Type, asserted2, readStep2);
    }


    private int readDataRegister(JoypadNumber n, JoypadType type, boolean asserted, int readStep) {
        boolean is6Button = type == JoypadType.BUTTON_6;
        if (asserted) {
            return is6Button && readStep == SIX_BUTTON_START_A_ONLY_STEP ? get00SA0000(n) : get00SA00DU(n);
        } else {
            return is6Button && readStep == SIX_BUTTON_XYZ_STEP ? get11CBMXYZ(n) : get11CBRLDU(n);
        }
    }



    public int readDataRegister3() {
        return 0x3F;
    }

    private void writeControlCheck(int port, long data) {
        if (data != 0x40 && data != 0) {
            LOG.info("Setting ctrlPort{} to {}", port, Long.toHexString(data));
        }
    }

    public void writeControlRegister1(long data) {
        writeControlCheck(1, data);
        control1 = data;
    }

    public void writeControlRegister2(long data) {
        writeControlCheck(2, data);
        control2 = data;
    }

    public void writeControlRegister3(long data) {
        writeControlCheck(3, data);
        control3 = data;
    }


    private int get00SA0000(JoypadNumber n) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4);
    }

    //6 buttons
    private int get00SA00DU(JoypadNumber n) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4) | (getValue(n, D) << 1) | (getValue(n, U));
    }

    private int get11CBRLDU(JoypadNumber n) {
        return 0xC0 | (getValue(n, C) << 5) | (getValue(n, B) << 4) | (getValue(n, R) << 3) |
                (getValue(n, L) << 2) | (getValue(n, D) << 1) | (getValue(n, U));
    }

    //6 buttons
    private int get11CBMXYZ(JoypadNumber n) {
        return 0xC0 | (getValue(n, C) << 5) | (getValue(n, B) << 4) | (getValue(n, M) << 3) |
                (getValue(n, X) << 2) | (getValue(n, Y) << 1) | (getValue(n, Z));
    }

    private int getValue(JoypadNumber number, JoypadButton button) {
        return getMap(number).get(button).ordinal();
    }

    @Override
    public void setButtonAction(JoypadNumber number, JoypadButton button, JoypadAction action) {
        getMap(number).put(button, action);
    }

    public long readControlRegister1() {
        return control1;
    }

    public long readControlRegister2() {
        return control2;
    }

    public long readControlRegister3() {
        return control3;
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

    @Override
    public void newFrame() {
        readStep1 = 0;
        readStep2 = 0;
//        LOG.info("new frame");
    }
}
