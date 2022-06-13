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
 * GenesisJoypad
 *
 * @author Federico Berti
 * <p>
 * Most of the code adapted from GenesisPlusGx joypad.c
 * <p>
 * wwf raw 32x, GreatestHeavyweights, Sf2, sgdk joytest
 * TODO
 * - Decap Attack, 3btn and 6btn ko
 * - GreatestHeavyweights, 3Btn ok, 6btn ko
 */
public class GenesisJoypad extends BasePadAdapter {

    private static final Logger LOG = LogManager.getLogger(GenesisJoypad.class.getSimpleName());

    private static final boolean verbose = false;

    public static JoypadType P1_DEFAULT_TYPE = JoypadType.BUTTON_6;
    public static JoypadType P2_DEFAULT_TYPE = JoypadType.BUTTON_6;

    static class MdPadContext {
        int control = 0, //SGDK needs 0 here, otherwise it is considered a RESET
                data,
                readStep;
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
        reset();
    }

    @Override
    public void reset() {
        super.reset();
        ctx1.data = ctx2.data = ctx3.data = 0x40;
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

    public int readDataRegister1() {
        //TODO check
//        int mask = 0x80 | ctx1.control;
//        int data = readPad(P1, p1Type, ctx1);
//        return (ctx1.data & mask) | (data & ~mask);
        return readPad(P1, p1Type, ctx1);
    }

    public int readDataRegister2() {
//        int mask = 0x80 | ctx2.control;
//        int data = readPad(P2, p2Type, ctx2);
//        return (ctx2.data & mask) | (data & ~mask);
        return readPad(P2, p2Type, ctx2);
    }

    public int readDataRegister3() {
        if (verbose) LOG.info("read p3: result: {}", th(0xFF));
        return 0xFF;
    }

    public void writeDataRegister1(int data) {
        writePad(ctx1, p1Type, ctx1.control, data);
    }

    public void writeDataRegister2(int data) {
        writePad(ctx2, p2Type, ctx2.control, data);
    }

    private void writePad(MdPadContext ctx, JoypadType type, int control, int data) {
        boolean thPinHigh = (control & 0x40) > 0;
        if (thPinHigh) {
            data &= 0xC0; //keep D7 bit
            if (type == JoypadType.BUTTON_6 && ctx.readStep < 8) {
                //0->1 transition
                if ((ctx.data & 0x40) == 0 && data > 0) {
                    ctx.readStep += 2;
                }
            }
        } else {
            data |= 0x40;
        }
        ctx.data = data;
        ctx.control = control;
        if (verbose) LOG.info("writeDataReg: data {}, {}", th(data), ctx);
    }

    private int readPad(PlayerNumber n, JoypadType type, MdPadContext ctx) {
        if (type == JoypadType.NONE) {
            return 0xFF;
        }
        if ((ctx.control & 0x40) != 0x40) { //WwfRaw 32x
            return logReadDataReg((ctx.data | ~ctx.control) & 0xFF, ctx);
        }
        int data = ctx.data | 0x3f;
        int step = ctx.readStep | (data >> 6);

        switch (step) {
            case 4:
                return get00SA0000(n, ctx);
            case 7:
                return get11CBMXYZ(n, ctx);
            case 6:
                return get00SA1111(n, ctx);
            default:
                return (step & 1) > 0 ? get11CBRLDU(n, ctx) : get00SA00DU(n, ctx);
        }
    }

    private void writeControlCheck(int port, int value) {
        if (verbose) LOG.info("Setting ctrlPort{} to {}", port, th(value));
        if (value != 0x40 && value != 0) {
            LOG.warn("Setting ctrlPort{} to {}", port, th(value));
        }
    }

    public void writeControlRegister1(int value) {
        writeControlCheck(ctx1.player, value);
        writePad(ctx1, p1Type, value, ctx1.data);
    }

    public void writeControlRegister2(int value) {
        writeControlCheck(ctx2.player, value);
        writePad(ctx2, p2Type, value, ctx2.data);
    }

    public void writeControlRegister3(int value) {
        writeControlCheck(ctx3.player, value);
    }

    private int get00SA0000(PlayerNumber n, MdPadContext ctx) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4);
    }

    private int get00SA1111(PlayerNumber n, MdPadContext ctx) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4) | 0xF;
    }

    //6 buttons
    private int get00SA00DU(PlayerNumber n, MdPadContext ctx) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4) | (getValue(n, D) << 1) | (getValue(n, U));
    }

    private int get11CBRLDU(PlayerNumber n, MdPadContext ctx) {
        return 0x80 | (getValue(n, C) << 5) | (getValue(n, B) << 4) | (getValue(n, R) << 3) |
                (getValue(n, L) << 2) | (getValue(n, D) << 1) | (getValue(n, U));
    }

    //6 buttons
    private int get11CBMXYZ(PlayerNumber n, MdPadContext ctx) {
        return 0x80 | (getValue(n, C) << 5) | (getValue(n, B) << 4) | (getValue(n, M) << 3) |
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
        if (verbose) LOG.info("Read ctrlPort{}: {}", ctx.player, ctx);
        return ctx.control;
    }

    private int logReadDataReg(int res, MdPadContext ctx) {
        if (verbose) LOG.info("readDataReg: {}, {}", th(res), ctx);
        return res;
    }

    @Override
    public void newFrame() {
        super.newFrame();
        ctx1.readStep = ctx2.readStep = ctx3.readStep = 0;
        if (verbose) LOG.info("new frame");
    }
}