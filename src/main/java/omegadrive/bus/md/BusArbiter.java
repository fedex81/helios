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

package omegadrive.bus.md;

import omegadrive.Device;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.util.LogHelper;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider.VdpBusyState;
import org.slf4j.Logger;

import static omegadrive.util.BufferUtil.CpuDeviceAccess;

public interface BusArbiter extends Device, BaseVdpProvider.VdpEventListener {

    BusArbiter NO_OP = createNoOp();
    int VDP_IPL1 = 2;
    int VDP_IPL2 = 4;

    enum IntState {NONE, PENDING, ASSERTED}

    enum CpuType {M68K, Z80}

    enum CpuState {RUNNING, HALTED}

    enum InterruptEvent {Z80_INT_ON, Z80_INT_OFF}

    static BusArbiter createInstance(GenesisVdpProvider vdp, M68kProvider m68k, Z80Provider z80) {
        BusArbiterImpl b = new BusArbiterImpl();
        b.vdp = vdp;
        b.m68k = m68k;
        b.z80 = z80;
        vdp.addVdpEventListener(b);
        return b;
    }

    VdpBusyState getVdpBusyState();

    void setVdpBusyState(VdpBusyState state);

    void handleInterrupts(CpuDeviceAccess cpu);

    boolean is68kRunning();

    void addCyclePenalty(CpuType cpuType, int value);

    void ackInterrupt68k(int level);

    void runLater68k(Runnable r);

    static BusArbiter createNoOp() {
        return new BusArbiterImpl() {
            @Override
            public void ackInterrupt68k(int level) {
            }

            @Override
            public void addCyclePenalty(CpuType cpuType, int value) {
            }
        };
    }
}

class BusArbiterImpl implements BusArbiter {

    private final static Logger LOG = LogHelper.getLogger(BusArbiter.class.getSimpleName());

    private static final boolean verbose = false;
    private int vdpLevel = 0;
    private IntState int68k = IntState.NONE;
    private VdpBusyState vdpBusyState = VdpBusyState.NOT_BUSY;
    private CpuState state68k = CpuState.RUNNING;
    private IntState z80Int = IntState.NONE;
    private InterruptEvent z80IntLineVdp = InterruptEvent.Z80_INT_OFF;

    protected GenesisVdpProvider vdp;
    protected M68kProvider m68k;
    protected Z80Provider z80;

    private Runnable runLater68k;

    protected BusArbiterImpl() {
    }

    @Override
    public VdpBusyState getVdpBusyState() {
        return vdpBusyState;
    }

    @Override
    public void setVdpBusyState(VdpBusyState state) {
        if (vdpBusyState != state) {
            state68k = state == VdpBusyState.MEM_TO_VRAM
                    ? CpuState.HALTED : CpuState.RUNNING;
            if (verbose) logInfo("Vdp State {} -> {} , 68k {}", vdpBusyState, state, state68k);
            vdpBusyState = state;
            if (state68k == CpuState.RUNNING && vdpBusyState == VdpBusyState.NOT_BUSY
                    && runLater68k != null) {
                Runnable runnable = runLater68k;
                runLater68k = null;
                runnable.run();
            }
        }
    }

    @Override
    public void handleInterrupts(CpuDeviceAccess cpu) {
        switch (cpu) {
            case M68K -> handleInterrupts68k();
            case Z80 -> handleInterruptZ80();
            default -> LOG.error("Unable to handle interrupts: {}", cpu);
        }
    }

    /**
     * /INT is an active-low level sensitive input. When pulled low, the Z80 wil start interrupt processing,
     * and repeatedly do this after each instruction executed until /INT goes high again.
     * This type of interrupt can be controlled by the Z80, using the DI (disable interrupt)
     * and EI (enable interrupt) instructions.
     * <p>
     * When interrupts are disabled via DI, the Z80 will respond to /INT as soon
     * as interrupts are enabled again. If /INT goes high before they are enabled,
     * then the interrupt is 'lost' and the Z80 never responds to it.
     * <p>
     * Typically the external hardware which triggered the interrupt will have
     * some facility to pull /INT high again, either after a set period of time
     * (auto-acknowledge) or through a dedicated memory address or control register
     * that can be accessed by the interrupt handler, to indicate that the interrupt is being serviced.
     * <p>
     * The Z80 has three processing modes for maskable interrupts. The mode resets to 0 by default,
     * and can be changed using the IM 0, IM 1, or IM 2 instructions.
     * https://www.smspower.org/Development/InterruptMechanism
     * <p>
     * A very short Z80 interrupt routine would be triggered multiple times
     * if it finishes within 228 Z80 clock cycles. I think (but cannot recall the specifics)
     * that some games have delay loops in the interrupt handler for this very reason.
     * http://gendev.spritesmind.net/forum/viewtopic.php?t=740
     * <p>
     * VDPTEST
     * http://gendev.spritesmind.net/forum/viewtopic.php?t=787
     */
    private void handleInterruptZ80() {
        boolean vIntExpired = z80Int == IntState.ASSERTED && z80IntLineVdp == InterruptEvent.Z80_INT_OFF;

        if (z80IntLineVdp == InterruptEvent.Z80_INT_ON) {
            raiseInterruptsZ80();
        } else if (vIntExpired) {
            resetZ80Int();
        }
    }

    private void resetZ80Int() {
        if (verbose && z80Int != IntState.NONE) {
            logInfo("Z80 INT expired, state {}", z80Int);
        }
        z80Int = IntState.NONE;
        z80.interrupt(false);
    }

    private void raiseInterruptsZ80() {
        z80.interrupt(true);
        if (z80Int != IntState.ASSERTED) {
            z80Int = IntState.ASSERTED;
            if (verbose) logInfo("Z80 INT: {}", z80Int);
        }
    }

    @Override
    public boolean is68kRunning() {
        return state68k == CpuState.RUNNING;
    }

    private void setZ80Int(InterruptEvent event) {
        z80IntLineVdp = event;
        if (verbose) logInfo("Z80Int line: {}", event);
    }

    @Override
    public void addCyclePenalty(CpuType cpuType, int value) {
        switch (cpuType) {
            case M68K:
                m68k.addCyclePenalty(value);
                break;
            case Z80:
                z80.addCyclePenalty(value);
                break;
            default:
                break;
        }
    }

    /**
     * TODO, from ares
     * // Note: A pending interrupt will be delayed when a register write
     * // is used to enable that interrupt. Further testing is required,
     * // but the current method should be sufficient to handle most cases.
     * // Required for Sesame Street Counting Cafe et al.
     * irq.delay = 2; // 4 pixel delay (4-6 M68k clocks) from this write
     */
    private void handleInterrupts68k() {
        switch (int68k) {
            case NONE:
                checkInterrupts68k();
                break;
            case PENDING:
                raiseInterrupts68k();
                break;
        }
    }

    private void raiseInterrupts68k() {
        boolean res = m68k.raiseInterrupt(getLevel68k());
        if (verbose && res) logInfo("68k int{}: {}", getLevel68k(), "was " + IntState.ASSERTED);
    }

    @Override
    public void ackInterrupt68k(int level) {
        //ackLev4 can become ackLev6 (Fatal Rewind), but ackLev6 shouldn't become ackLev4 (Lotus2)
        int ackLevel = vdpLevel | level;
        if (ackLevel != level) {
            LOG.warn("68k level: {} but vdp thinks: {}, setting {}=false", level, ackLevel,
                    ackLevel == M68kProvider.VBLANK_INTERRUPT_LEVEL ? "vip" : "hip");
        }
        switch (ackLevel) {
            case M68kProvider.VBLANK_INTERRUPT_LEVEL:
                vdp.setVip(false);
                vdpLevel = 0;
                break;
            case M68kProvider.HBLANK_INTERRUPT_LEVEL:
                vdp.setHip(false);
                vdpLevel = 0;
                break;
            default:
                LOG.error("Unexpected vdpLevel {}, 68kLevel: {}", vdpLevel, level);
                break;
        }
        int68k = IntState.NONE;
        if (verbose) logInfo("68k int{}: {} (ACKED)", level, int68k);
    }

    private void checkInterrupts68k() {
        if (isVdpVInt(vdp) || isVdpHInt()) {
            int68k = IntState.PENDING;
            vdpLevel |= isVdpVInt(vdp) ? (VDP_IPL1 + VDP_IPL2) : isVdpHInt() ? VDP_IPL2 : 0;
            if (verbose) logInfo("68k int{}, vdpLevel{}: {}", getLevel68k(), vdpLevel, int68k);
        }
    }

    @Override
    public void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        switch (event) {
            case INTERRUPT:
                InterruptEvent ievent = (InterruptEvent) value;
                setZ80Int(ievent);
                break;
            case NEW_FRAME:
                if (verbose) logInfo("NewFrame");
                break;
        }
    }

    @Override
    public void runLater68k(Runnable r) {
        runLater68k = r;
        state68k = CpuState.HALTED;
        if (verbose) logInfo("68k State {} , vdp {}", state68k, vdpBusyState);
    }

    public static boolean isVdpVInt(GenesisVdpProvider vdp) {
        return vdp.getVip() && vdp.isIe0();
    }


    private boolean isVdpHInt() {
        return vdp.getHip() && vdp.isIe1();
    }

    private int getLevel68k() {
        //TODO titan2 this can return 0, why investigate
        return isVdpVInt(vdp) ? M68kProvider.VBLANK_INTERRUPT_LEVEL : (isVdpHInt() ? M68kProvider.HBLANK_INTERRUPT_LEVEL : 0);
    }

    private void logInfo(String str, Object... args) {
        LOG.info("{}{}", LogHelper.formatMessage(str, args), vdp.getVdpStateString());
    }
}