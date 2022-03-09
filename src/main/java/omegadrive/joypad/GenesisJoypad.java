/*
 * GenesisJoypad
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/10/19 17:32
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import omegadrive.input.InputProvider.PlayerNumber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static omegadrive.input.InputProvider.PlayerNumber.P1;
import static omegadrive.input.InputProvider.PlayerNumber.P2;
import static omegadrive.joypad.JoypadProvider.JoypadAction.RELEASED;
import static omegadrive.joypad.JoypadProvider.JoypadButton.*;
import static omegadrive.util.Util.th;

/**
 * 6-button controller steps
 * Bit 5	Bit 4	Bit 3	Bit 2	Bit 1	Bit 0
 * 1st step (write $40)	    C	B	    Right	Left	Down	Up
 * 2nd step (write $00)	Start	A	    0	    0	    Down	Up
 * 3rd step (write $40)	    C	B	    Right	Left	Down	Up
 * 4th step (write $00)	Start	A	    0	    0	    Down	Up
 * 5th step (write $40)	    C	B	    Right	Left	Down	Up
 * 6th step (write $00)	Start	A	    0	    0	    0	    0
 * 7th step (write $40)	    C	B	    Mode	X	    Y	    Z
 * 8th step (write $00)	Start	A	    1	    1	    1	    1
 * <p>
 */
public class GenesisJoypad extends BasePadAdapter {

    private static final Logger LOG = LogManager.getLogger(GenesisJoypad.class.getSimpleName());

    private static final boolean verbose = false;

    static final int SIX_BUTTON_STEPS = 9;
    static final int SIX_BUTTON_START_A_ONLY_STEP = 6;
    static final int SIX_BUTTON_XYZ_STEP = 7;
    static final int SIX_BUTTON_START_A_FINAL_STEP = 8;

    public static JoypadType P1_DEFAULT_TYPE = JoypadType.BUTTON_6;
    public static JoypadType P2_DEFAULT_TYPE = JoypadType.BUTTON_6;

    static class MdPadContext {
        int control = 0, //SGDK needs 0 here, otherwise it is considered a RESET
                data, readStep;
        boolean high;
        int player;

        MdPadContext(int p) {
            this.player = p;
        }

        @Override
        public String toString() {
            return "MdPadContext{" +
                    "control=" + th(control) +
                    ", data=" + th(data) +
                    ", readStep=" + readStep +
                    ", high=" + high +
                    ", player=" + player +
                    '}';
        }
    }

    protected MdPadContext ctx1 = new MdPadContext(1);
    protected MdPadContext ctx2 = new MdPadContext(2);
    protected MdPadContext ctx3 = new MdPadContext(3);

    @Override
    public void init() {
        writeDataRegister1(0x40);
        writeDataRegister2(0x40);
        stateMap1 = Maps.newHashMap(ImmutableMap.<JoypadButton, JoypadAction>builder().
                put(D, RELEASED).put(U, RELEASED).
                put(L, RELEASED).put(R, RELEASED).
                put(S, RELEASED).put(A, RELEASED).
                put(B, RELEASED).put(C, RELEASED).
                put(M, RELEASED).put(X, RELEASED).
                put(Y, RELEASED).put(Z, RELEASED).build());
        stateMap2 = Maps.newHashMap(stateMap1);
        setPadSetupChange(P1, P1_DEFAULT_TYPE.name());
        setPadSetupChange(P2, P2_DEFAULT_TYPE.name());
    }

    @Override
    public void setPadSetupChange(PlayerNumber pn, String info) {
        JoypadType jt = JoypadType.valueOf(info);
        switch (pn) {
            case P1:
                P1_DEFAULT_TYPE = p1Type = jt;
                break;
            case P2:
                P2_DEFAULT_TYPE = p2Type = jt;
                break;
        }
        LOG.info("Setting {} joypad type to: {}", pn, jt);
    }

    public void writeDataRegister1(int data) {
        writeDataRegister(ctx1, data);
    }

    public void writeDataRegister2(int data) {
        writeDataRegister(ctx2, data);
    }

    private void writeDataRegister(MdPadContext ctx, int data) {
        ctx.data = data & 0xFF;
        boolean h = (ctx.data & 0x40) == 0x40;
        if (h != ctx.high) {
            ctx.readStep = (ctx.readStep + 1) % SIX_BUTTON_STEPS;
            ctx.high = h;
        }
        if (verbose) LOG.info("writeDataReg: data {}, {}", th(data), ctx);
    }

    public int readDataRegister1() {
        return readDataRegister(P1, p1Type, ctx1);
    }

    public int readDataRegister2() {
        return readDataRegister(P2, p2Type, ctx2);
    }

    public int readDataRegister3() {
        if (verbose) LOG.info("read p3: result: {}", th(0xFF));
        return 0xFF;
    }

    private int readDataRegister(PlayerNumber n, JoypadType type, MdPadContext ctx) {
        if (verbose) LOG.info("readDataReg: {}", ctx);
        if ((ctx.control & 0x40) != 0x40) {
            return (ctx.data | ~ctx.control) & 0xFF;
        }
        boolean is6Button = type == JoypadType.BUTTON_6;
        if (!is6Button) {
            return (ctx.high ? get11CBRLDU(n) : get00SA00DU(n));
        }
        if (!ctx.high) {
            switch (ctx.readStep) {
                case SIX_BUTTON_START_A_FINAL_STEP:
                    return get00SA1111(n);
                case SIX_BUTTON_START_A_ONLY_STEP:
                    return get00SA0000(n);
                default:
                    return get00SA00DU(n);
            }
        } else {
            return (ctx.readStep == SIX_BUTTON_XYZ_STEP ? get11CBMXYZ(n) : get11CBRLDU(n));
        }
    }

    private void writeControlCheck(int port, int data) {
        if (verbose) LOG.info("Setting ctrlPort{} to {}", port, th(data));
        if (data != 0x40 && data != 0) {
            LOG.warn("Setting ctrlPort{} to {}", port, th(data));
        }
    }

    public void writeControlRegister1(int data) {
        writeControlRegister(ctx1, data);
    }

    public void writeControlRegister2(int data) {
        writeControlRegister(ctx2, data);
    }

    public void writeControlRegister3(int data) {
        writeControlRegister(ctx3, data);
    }

    private void writeControlRegister(MdPadContext ctx, int data) {
        writeControlCheck(ctx.player, data);
        ctx.control = data;
    }

    private int get00SA0000(PlayerNumber n) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4);
    }

    private int get00SA1111(PlayerNumber n) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4) | 0xF;
    }

    //6 buttons
    private int get00SA00DU(PlayerNumber n) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4) | (getValue(n, D) << 1) | (getValue(n, U));
    }

    private int get11CBRLDU(PlayerNumber n) {
        return 0xC0 | (getValue(n, C) << 5) | (getValue(n, B) << 4) | (getValue(n, R) << 3) |
                (getValue(n, L) << 2) | (getValue(n, D) << 1) | (getValue(n, U));
    }

    //6 buttons
    private int get11CBMXYZ(PlayerNumber n) {
        return 0xC0 | (getValue(n, C) << 5) | (getValue(n, B) << 4) | (getValue(n, M) << 3) |
                (getValue(n, X) << 2) | (getValue(n, Y) << 1) | (getValue(n, Z));
    }

    public int readControlRegister1() {
        return readControlRegister(ctx1);
    }

    public int readControlRegister2() {
        return readControlRegister(ctx2);
    }

    public int readControlRegister3() {
        return readControlRegister(ctx3);
    }

    private int readControlRegister(MdPadContext ctx) {
        if (verbose) LOG.info("Read ctrlPort{}: ", ctx.player, ctx);
        return ctx.control;
    }

    @Override
    public void newFrame() {
        super.newFrame();
        ctx1.readStep = p1Type == JoypadType.BUTTON_6 && ctx1.high ? 1 : 0;
        ctx2.readStep = p2Type == JoypadType.BUTTON_6 && ctx2.high ? 1 : 0;
//        LOG.info("new frame");
    }
}