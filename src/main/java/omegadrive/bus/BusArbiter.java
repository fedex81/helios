package omegadrive.bus;

import omegadrive.m68k.M68kProvider;
import omegadrive.vdp.VdpProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 *
 * TODO
 * z80 INT should last one scanline?
 * http://gendev.spritesmind.net/forum/viewtopic.php?t=740
 *
 * VDPTEST
 * http://gendev.spritesmind.net/forum/viewtopic.php?t=787
 *
 */
public class BusArbiter {

    private static Logger LOG = LogManager.getLogger(BusArbiter.class.getSimpleName());

    protected VdpProvider vdp;
    protected M68kProvider m68k;
    protected Z80Provider z80;

    public static boolean verbose = false;

    private IntState int68k = IntState.ACKED;
    private M68kState state68k = M68kState.RUNNING;
    private int mask68kState = 0;

    enum IntState {NONE, PENDING, ASSERTED, ACKED}

    enum M68kState {RUNNING, HALTED}

    private IntState z80Int = IntState.ACKED;
    private int z80IntVcounter = -1;

    private static int FIFO_FULL_MASK = 0x01;
    private static int DMA_IN_PROGRESS_MASK = 0x02;


    protected BusArbiter() {
    }

    public static BusArbiter createInstance(VdpProvider vdp, M68kProvider m68k, Z80Provider z80) {
        BusArbiter b = new BusArbiter();
        b.vdp = vdp;
        b.m68k = m68k;
        b.z80 = z80;
        return b;
    }

    public void handleInterruptZ80() {
        switch (z80Int) {
            case NONE:
                checkInterruptZ80();
                break;
            case PENDING:
                raiseInterruptsZ80();
                logInfo("Z80 raise");
                break;
            case ASSERTED:
                //fall through
            case ACKED:
                logInfo("Z80 acked");
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

    public boolean checkInterruptZ80() {
        boolean change = false;
        boolean processZ80 = /*z80IntVcounter > 0 ||*/ isVdpVInt();
        if (processZ80) {
            logInfo("Z80 check");
            int vc = vdp.getVCounter();
            if (z80IntVcounter == -1) {
                z80IntVcounter = vc;
                logInfo("Z80 INT detected");
            }
            if (z80IntVcounter == vc) {
                logInfo("Z80 INT still pending");
                z80Int = IntState.PENDING;
                change = true;
            } else {
                logInfo("Z80 INT over");
                z80IntVcounter = -1;
            }
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

    private void raiseInterruptsZ80() {
        z80.interrupt();
        z80Int = IntState.ASSERTED;
        logInfo("Z80 INT: {}", z80Int);
    }

    private int getLevel68k() {
        return isVdpVInt() ? M68kProvider.VBLANK_INTERRUPT_LEVEL : (isVdpHInt() ? M68kProvider.HBLANK_INTERRUPT_LEVEL : 0);
    }

    private void logInfo(String str, Object... args) {
        if (verbose) {
            String msg = ParameterizedMessage.format(str, args);
            LOG.info(new ParameterizedMessage(msg + vdp.getVdpStateString(), Long.toHexString(vdp.getHCounter()), Long.toHexString(vdp.getVCounter())));
        }
    }

    public void setStop68k(int mask) {
        if (mask != mask68kState) {
            mask68kState = mask;
            state68k = mask == 0 ? M68kState.RUNNING : M68kState.HALTED;
//            LOG.info("68k State{} , {}", mask, state68k);
        }
    }

    public boolean shouldStop68k() {
        return state68k != M68kState.RUNNING;
    }
}
