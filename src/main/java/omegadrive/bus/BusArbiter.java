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
 */
public class BusArbiter {

    private static Logger LOG = LogManager.getLogger(BusArbiter.class.getSimpleName());

    protected VdpProvider vdp;
    protected M68kProvider m68k;
    protected Z80Provider z80;

    private static boolean verbose = false;

    //68k pins, vpd -> 68k, ipl1 = 2, ipl2 = 4
//    private int ipl1;
//    private int ipl2;

    private int vdpInterruptState = 0;

    private IntState vInt = IntState.ACKED;
    private IntState hInt = IntState.ACKED;

    enum IntState {PENDING, ASSERTED, ACKED}


    private boolean shouldRaiseZ80;

    protected BusArbiter() {
    }

    public static BusArbiter createInstance(VdpProvider vdp, M68kProvider m68k, Z80Provider z80) {
        BusArbiter b = new BusArbiter();
        b.vdp = vdp;
        b.m68k = m68k;
        b.z80 = z80;
        return b;
    }

    public boolean checkVdpInterrupts() {
        boolean change = false;

        if (isVdpVInt() && vInt == IntState.ACKED) {
            vInt = IntState.PENDING;
            change = true;
        } else if (isVdpHInt() && hInt == IntState.ACKED) {
            hInt = IntState.PENDING;
            change = true;
        }
        return change;
    }

    private int getVdpInterruptState() {
        return isVdpHInt() ? M68kProvider.HBLANK_INTERRUPT_LEVEL : (isVdpVInt() ? M68kProvider.VBLANK_INTERRUPT_LEVEL : 0);
    }

    //from 68k
    public boolean setVdpInterruptAck() {
        boolean change = false;
        int vdpState = getVdpInterruptState();
        if (isVdpVInt()) {
            vdp.setVip(false);
            vInt = IntState.ACKED;
            logInfo("Ack VDP VINT");
            change = true;
        } else if (isVdpHInt()) {
            vdp.setHip(false);
            hInt = IntState.ACKED;
            logInfo("Ack VDP HINT");
            change = true;
        }
        return change;
    }

    protected boolean isVdpVInt() {
        return vdp.getVip() && vdp.isIe0();
    }

    private boolean isVdpHInt() {
        return vdp.getHip() && vdp.isIe1();
    }

    public boolean handleVdpInterrupts() {
        if (checkVdpInterrupts()) {
            return false;
        }
        if (evaluateRaiseInterrupt()) {
            return false;
        }
        return evaluateAckInterrupt();
    }

    private boolean evaluateAckInterrupt() {
        if (hInt == IntState.ASSERTED || vInt == IntState.ASSERTED) {
            setVdpInterruptAck();
            return true;
        }
        return false;
    }

    private boolean evaluateRaiseInterrupt() {
        boolean change = false;
        if (vInt == IntState.PENDING) {
            boolean shouldAck = raiseInterrupts68k(M68kProvider.VBLANK_INTERRUPT_LEVEL);
            if (shouldAck) {
                vInt = IntState.ASSERTED;
                change = true;
            }
            raiseInterruptsZ80();
        } else if (hInt == IntState.PENDING) {
            boolean shouldAck = raiseInterrupts68k(M68kProvider.HBLANK_INTERRUPT_LEVEL);
            if (shouldAck) {
                hInt = IntState.ASSERTED;
                change = true;
            }
        }
        return change;
    }

    protected boolean raiseInterrupts68k(int level) {
            boolean res = m68k.raiseInterrupt(level);
            if (res) {
                logInfo("raise 68k intLevel: {}", level);
            }
            return res;
    }

    private void raiseInterruptsZ80() {
        if (shouldRaiseZ80) {
            logInfo("Z80 raise interrupt");
            z80.interrupt();
            shouldRaiseZ80 = false;
        }

    }

    private void logInfo(String str, Object... args) {
        if (verbose) {
            String msg = ParameterizedMessage.format(str, args);
            LOG.info(new ParameterizedMessage(msg + vdp.getVdpStateString(), Long.toHexString(vdp.getHCounter()), Long.toHexString(vdp.getVCounter())));
        }
    }

    public void handleVdpInterruptsZ80() {
        //TODO needed? delete?
    }
}
