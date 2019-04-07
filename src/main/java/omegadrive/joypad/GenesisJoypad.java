/*
 * GenesisJoypad
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.joypad;

//	http://md.squee.co/315-5309
//	http://md.squee.co/Howto:Read_Control_Pads

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static omegadrive.joypad.JoypadProvider.JoypadAction.RELEASED;
import static omegadrive.joypad.JoypadProvider.JoypadButton.*;

/**
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
public class GenesisJoypad extends BasePadAdapter {

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

    @Override
    public void init() {
        writeDataRegister1(0x40);
        writeDataRegister2(0x40);
        p1Type = JoypadType.BUTTON_6;
        p2Type = JoypadType.BUTTON_3;
        LOG.info("Joypad1: {} - Joypad2: {}", p1Type, p2Type);
        stateMap1 = Maps.newHashMap(ImmutableMap.<JoypadButton, JoypadAction>builder().
                put(D, RELEASED).put(U, RELEASED).
                put(L, RELEASED).put(R, RELEASED).
                put(S, RELEASED).put(A, RELEASED).
                put(B, RELEASED).put(C, RELEASED).
                put(M, RELEASED).put(X, RELEASED).
                put(Y, RELEASED).put(Z, RELEASED).build());
        stateMap2 = Maps.newHashMap(stateMap1);
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

    @Override
    public long readControlRegister1() {
        return control1;
    }

    @Override
    public long readControlRegister2() {
        return control2;
    }

    @Override
    public long readControlRegister3() {
        return control3;
    }

    @Override
    public void newFrame() {
        readStep1 = 0;
        readStep2 = 0;
//        LOG.info("new frame");
    }
}
