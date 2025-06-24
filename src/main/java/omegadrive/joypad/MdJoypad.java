/*
 * MdJoypad
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
import omegadrive.UserConfigHolder;
import omegadrive.input.InputProvider.PlayerNumber;
import omegadrive.system.SystemProvider;
import omegadrive.system.SystemProvider.SystemEvent;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import static omegadrive.input.InputProvider.PlayerNumber.P1;
import static omegadrive.input.InputProvider.PlayerNumber.P2;
import static omegadrive.joypad.JoypadProvider.JoypadAction.RELEASED;
import static omegadrive.joypad.JoypadProvider.JoypadButton.*;
import static omegadrive.joypad.MdInputModel.*;
import static omegadrive.util.Util.th;

/**
 * MdJoypad
 *
 * @author Federico Berti
 * <p>
 * Most of the code adapted from GenesisPlusGx joypad.c
 * <p>
 * see MdJoypadTest
 */
public class MdJoypad extends BasePadAdapter {

    private static final Logger LOG = LogHelper.getLogger(MdJoypad.class.getSimpleName());

    private static final int M68K_CYCLES_PAD_RESET = 12000; //~1.6ms @ 7.5mhz

    protected static boolean WWF32X_HACK =
            Boolean.parseBoolean(System.getProperty("helios.md.pad.wwf32x.hack", "false"));
    protected static final boolean ASTERIX_HACK =
            Boolean.parseBoolean(System.getProperty("helios.md.pad.asterix.hack", "false"));

    protected static final boolean verbose = false;

    protected final MdPadContext ctx1 = new MdPadContext(1);
    protected final MdPadContext ctx2 = new MdPadContext(2);
    protected final MdPadContext ctx3 = new MdPadContext(3);

    private final SystemProvider.SystemClock clock;

    public static MdJoypad create(SystemProvider.SystemClock clock) {
        return new MdJoypad(clock);
    }

    public MdJoypad(SystemProvider.SystemClock clock) {
        this.clock = clock;
        p1Type = JoypadType.BUTTON_6;
        p2Type = JoypadType.BUTTON_6;
    }

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
        for (PlayerNumber pn : PlayerNumber.values()) {
            Object obj = UserConfigHolder.userEventObjectMap.get(pn, SystemEvent.PAD_SETUP_CHANGE);
            if (obj != null) {
                setPadSetupChange(pn, obj.toString());
            }
        }
        reset();
        assert p1Type != null && p2Type != null;
    }

    @Override
    public void reset() {
        super.reset();
        ctx1.data = ctx2.data = ctx3.data = DATA_TH_HIGH;
    }

    @Override
    public void setPadSetupChange(PlayerNumber pn, String info) {
        JoypadType jt = JoypadType.valueOf(info);
        switch (pn) {
            case P1 -> p1Type = jt;
            case P2 -> p2Type = jt;
        }
        LOG.info("Setting {} joypad type to: {}", pn, jt);
    }

    public int readDataRegister1() {
        return readPad(P1, p1Type, ctx1);
    }

    public int readDataRegister2() {
        return readPad(P2, p2Type, ctx2);
    }

    public int readDataRegister3() {
        if (verbose) LOG.info("read p3: result: {}", th(0xFF));
        return 0xFF;
    }

    public void writeDataRegister1(int data) {
        writePad(ctx1, p1Type, data);
    }

    public void writeDataRegister2(int data) {
        writePad(ctx2, p2Type, data);
    }

    private void writeControlPad(MdPadContext ctx, JoypadType type, int control) {
        ctx.control = control;
        //set thPin as input -> TH goes high
        if (WWF32X_HACK && isCtrlThInput.test(control)) {
            ctx.data |= DATA_TH_HIGH;
            if (verbose) LOG.warn("writeCtrlReg: data {}, {}", th(control), ctx);
        }
        writeControlCheck(ctx, control);
    }

    private void writePad(MdPadContext ctx, JoypadType type, int data) {
        checkResetState(ctx, type);
        boolean thPinHigh = isDataThHigh.test(data);
        boolean wasThPinHigh = isDataThHigh.test(ctx.data);
        if (thPinHigh != wasThPinHigh) {
            ctx.readStep = (ctx.readStep + 1) & 7;
            if (type != JoypadType.BUTTON_6) {
                ctx.readStep &= 1;
            }
        }
        ctx.data = data;
        ctx.readMask = 0x80 | (ctx.control & data); //bit 7 is latched from the latest data port write
        if (verbose) LOG.info("writeDataReg: data {}, {}", th(data), ctx);
    }

    private void resetPad(MdPadContext ctx) {
        ctx.data = ASTERIX_HACK ? DATA_TH_LOW : DATA_TH_HIGH;
        ctx.readMask = 0x80 | (ctx.control & ctx.data); //bit 7 is latched from the latest data port write
        ctx.readStep = ASTERIX_HACK ? 1 : 0;
        ctx.latestWriteCycleCounter = 0;
    }

    private void checkResetState(MdPadContext ctx, JoypadType type) {
        if (type != JoypadType.BUTTON_6) {
            return;
        }
        int fc = clock.getCycleCounter();
        if (fc - ctx.latestWriteCycleCounter > M68K_CYCLES_PAD_RESET) {
            if (verbose) LOG.info("{} {}", fc - ctx.latestWriteCycleCounter, ctx);
            resetPad(ctx);
        }
        ctx.latestWriteCycleCounter = fc;
    }

    private int readPad(PlayerNumber n, JoypadType type, MdPadContext ctx) {
        int res;
        if (type == JoypadType.NONE) {
            if (verbose) LOG.info("readDataReg: data {}, {}", th(0xFF), ctx);
            return 0xFF;
        }
        if (WWF32X_HACK && isCtrlThInput.test(ctx.control)) {
            res = (ctx.data | ~ctx.control) & 0xFF;
            if (verbose) LOG.info("readDataReg: data {}, {}", th(res), ctx);
            return res;
        }
        int step = ctx.readStep;
        switch (SixButtonState.vals[step]) {
            case SA0000_5:
                assert !isDataThHigh.test(ctx.data); //thLow
                res = getSA0000(n);
                break;
            case CBMXYZ_6:
                assert isDataThHigh.test(ctx.data); //thHigh
                res = getCBMXYZ(n);
                break;
            case SA1111_7:
                assert !isDataThHigh.test(ctx.data); //thLow
                res = getSA1111(n);
                break;
            default:
                assert ((step & 1) == 0) == isDataThHigh.test(ctx.data) : ctx;
                res = (step & 1) == 0 ? getCBRLDU(n) : getSA00DU(n);
                break;
        }

        res = (res & ~ctx.readMask) | (ctx.data & ctx1.readMask);
        if (verbose) LOG.info("readDataReg: data {}, {}", th(res), ctx);
        return res;
    }

    private void writeControlCheck(MdPadContext ctx, int value) {
        if (value != CTRL_PIN_OUTPUT && value != CTRL_PIN_INPUT) {
            if (verbose) LOG.warn("writeCtrlReg: data {}, {}", th(value), ctx);
        } else {
            if (verbose) LOG.info("writeCtrlReg: data {}, {}", th(value), ctx);
        }
    }

    public void writeControlRegister1(int value) {
        writeControlPad(ctx1, p1Type, value);
    }

    public void writeControlRegister2(int value) {
        writeControlPad(ctx2, p2Type, value);
    }

    public void writeControlRegister3(int value) {
        writeControlPad(ctx3, JoypadType.BUTTON_3, value);
    }

    private int getSA0000(PlayerNumber n) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4);
    }

    private int getSA1111(PlayerNumber n) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4) | 0xF;
    }

    private int getSA00DU(PlayerNumber n) {
        return (getValue(n, S) << 5) | (getValue(n, A) << 4) | (getValue(n, D) << 1) | (getValue(n, U));
    }

    private int getCBRLDU(PlayerNumber n) {
        return (getValue(n, C) << 5) | (getValue(n, B) << 4) | (getValue(n, R) << 3) |
                (getValue(n, L) << 2) | (getValue(n, D) << 1) | (getValue(n, U));
    }

    //6 buttons
    private int getCBMXYZ(PlayerNumber n) {
        return (getValue(n, C) << 5) | (getValue(n, B) << 4) | (getValue(n, M) << 3) |
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
        if (verbose) LOG.info("readCtrlReg: data {}, {}", th(ctx.control), ctx);
        return ctx.control;
    }

    @Override
    public void newFrame() {
        super.newFrame();
        resetPad(ctx1);
        resetPad(ctx2);
        if (verbose) LOG.info("new frame");
    }
}