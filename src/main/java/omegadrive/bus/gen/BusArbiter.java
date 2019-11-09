/*
 * BusArbiter
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:37
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

package omegadrive.bus.gen;

import omegadrive.Device;
import omegadrive.m68k.M68kProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

public class BusArbiter implements Device {

    /**
     *
     * A very short Z80 interrupt routine would be triggered multiple times
     * if it finishes within 228 Z80 clock cycles. I think (but cannot recall the specifics)
     * that some games have delay loops in the interrupt handler for this very reason.
     * http://gendev.spritesmind.net/forum/viewtopic.php?t=740
     *
     * VDPTEST
     * http://gendev.spritesmind.net/forum/viewtopic.php?t=787
     *
     */
    private final static Logger LOG = LogManager.getLogger(BusArbiter.class.getSimpleName());
    private static final VdpState[] stateVdpVals = VdpState.values();
    public static boolean verbose = false;
    private VdpState stateVdp = VdpState.NORMAL;

    public void setStop68k(int mask) {
        if (mask != mask68kState) {
            mask68kState = mask;
            stateVdp = stateVdpVals[mask];
            state68k = stateVdp != VdpState.NORMAL ? M68kState.HALTED : M68kState.RUNNING;
//            LOG.info("68k State{} , {}", mask, state68k);
        }
    }

    public boolean is68kRunning() {
        return state68k == M68kState.RUNNING;
    }

    protected GenesisVdpProvider vdp;
    protected M68kProvider m68k;
    protected Z80Provider z80;

    private IntState int68k = IntState.ACKED;
    private M68kState state68k = M68kState.RUNNING;

    enum IntState {NONE, PENDING, ASSERTED, ACKED}
    private int mask68kState = 0;
    private boolean vIntFrameExpired;
    private int vIntOnLine;

    private IntState z80Int = IntState.ACKED;

    protected BusArbiter() {
    }

    public static BusArbiter createInstance(GenesisVdpProvider vdp, M68kProvider m68k, Z80Provider z80) {
        BusArbiter b = new BusArbiter();
        b.vdp = vdp;
        b.m68k = m68k;
        b.z80 = z80;
        return b;
    }

    public void handleInterruptZ80() {
        checkInterruptZ80();
        switch (z80Int) {
            case PENDING:
                logInfo("Z80 INT pending");
                raiseInterruptsZ80();
                break;
            case ASSERTED:
                //fall through
            case ACKED:
                logInfo("Z80 INT acked");
                z80Int = IntState.NONE;
        }
    }

    public void handleInterrupts68k() {
        switch (int68k) {
            case NONE:
                checkInterrupts68k();
                break;
            case PENDING:
                raiseInterrupts68k();
                break;
            case ASSERTED:
                ackInterrupts68k();
                break;
            case ACKED:
                int68k = IntState.NONE;
                break;
        }
    }

    public void checkInterrupts68k() {
        if (isVdpVInt() || isVdpHInt()) {
            int68k = IntState.PENDING;
            logInfo("68k int{}: {}", getLevel68k(), int68k);
        }
    }

    /**
     * it means z80 interrupt occurs once per frame (on line 224)
     * and remains active during one full line if not acknowledged by Z80
     * once the exception is processed on z80 side, interrupt is cleared until next frame
     * if Z80 interrupt is masked, interrupt remains pending
     * for one line and should be processed if unmasked during this period
     */
    public boolean checkInterruptZ80() {
        boolean change = false;
        int vc = vdp.getVCounter();
        boolean vIntJustTriggered = z80Int == IntState.NONE && isVdpVInt() && !vIntFrameExpired && vc == vIntOnLine;
        boolean vIntExpired = z80Int == IntState.PENDING && vc != vIntOnLine;

        if (vIntJustTriggered) {
            z80Int = IntState.PENDING;
            logInfo("Z80 INT triggered");
        } else if (vIntExpired) {
            vIntFrameExpired = true;
            z80Int = IntState.NONE;
            logInfo("Z80 INT expired");
        }
        return change;
    }

    public void ackInterrupts68k() {
        int level = getLevel68k();
        ackVdpInt(level);
        int68k = IntState.ACKED;
        logInfo("68k int{}: {}", level, int68k);
    }

    private void ackVdpInt(int level) {
        if (level == M68kProvider.VBLANK_INTERRUPT_LEVEL) {
            vdp.setVip(false);
        } else if (level == M68kProvider.HBLANK_INTERRUPT_LEVEL) {
            vdp.setHip(false);
        }
    }

    protected boolean isVdpVInt() {
        return vdp.getVip() && vdp.isIe0();
    }

    private boolean isVdpHInt() {
        return vdp.getHip() && vdp.isIe1();
    }

    private void raiseInterrupts68k() {
        int level = getLevel68k();
        boolean nonMasked = m68k.raiseInterrupt(level);
        if (nonMasked) {
            int68k = IntState.ASSERTED;
            logInfo("68k int{}: {}", level, int68k);
        }
    }

    private boolean raiseInterruptsZ80() {
        boolean res = z80.interrupt(true);
        if (res) {
            z80Int = IntState.ASSERTED;
            logInfo("Z80 INT: {}", z80Int);
        }
        return res;
    }

    private int getLevel68k() {
        //TODO titan2 this can return 0, why investigate
        return isVdpVInt() ? M68kProvider.VBLANK_INTERRUPT_LEVEL : (isVdpHInt() ? M68kProvider.HBLANK_INTERRUPT_LEVEL : 0);
    }

    private void logInfo(String str, Object... args) {
        if (verbose) {
            String msg = ParameterizedMessage.format(str, args);
            LOG.info(new ParameterizedMessage(msg + vdp.getVdpStateString(), Long.toHexString(vdp.getHCounter()), Long.toHexString(vdp.getVCounter())));
        }
    }

    enum M68kState {RUNNING, HALTED}

    //TODO fifo full shoud not stop 68k
    //TODO fifo full and 68k uses vdp -> stop 68k
    enum VdpState {
        NORMAL, DMA_IN_PROGRESS, FIFO_FULL
    }

    public void newFrame() {
        vIntFrameExpired = false;
        vIntOnLine = vdp.getVideoMode().isV28() ? GenesisVdpProvider.V28_VBLANK_SET : GenesisVdpProvider.V30_VBLANK_SET;
        logInfo("NewFrame");
    }

}
