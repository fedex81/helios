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
    private int ipl1;
    private int ipl2;

    protected BusArbiter() {
    }

    public static BusArbiter createInstance(VdpProvider vdp, M68kProvider m68k, Z80Provider z80) {
        BusArbiter b = new BusArbiter();
        b.vdp = vdp;
        b.m68k = m68k;
        b.z80 = z80;
        return b;
    }

    public void checkVdpInterrupts() {
        ipl1 = isVdpVInt() ? 1 : 0;
        ipl2 = isVdpHInt() || isVdpVInt() ? 1 : 0;
    }

    public boolean isVdpInterruptPending() {
        return ipl1 + ipl2 > 0;
    }

    //from 68k
    public void setVdpInterruptAck() {
        logInfo("PreAck: Arbiter IPL: {}", ((ipl1 << 1) + (ipl2 << 2)));
        logInfo("PreAck: Vdp State: vint: {}, hint: {}", isVdpVInt(), isVdpHInt());
        if (isVdpVInt()) {
            vdp.setVip(false);
            logInfo("Ack VDP VINT");
            ipl1 = 0;
            ipl2 = 0;
        } else if (isVdpHInt()) {
            vdp.setHip(false);
            logInfo("Ack VDP HINT");
            ipl2 = 0;
        }
        logInfo("PostAck: Arbiter IPL: {}", ((ipl1 << 1) + (ipl2 << 2)));
        logInfo("PostAck: Vdp State: vint: {}, hint: {}", isVdpVInt(), isVdpHInt());
    }

    protected boolean isVdpVInt() {
        return vdp.getVip() && vdp.isIe0();
    }

    private boolean isVdpHInt() {
        return vdp.getHip() && vdp.isIe1();
    }

    private boolean shouldAck = false;
    private boolean shouldRaise = false;

    public boolean handleVdpInterrupts() {
        checkVdpInterrupts();
        evaluateRaiseInterrupt();
        evaluateAckInterrupt();
        if (!isVdpInterruptPending() || shouldAck || shouldRaise) {
            return false;
        }
        shouldRaise = true;
        return true;
    }

    private void evaluateAckInterrupt() {
        if (shouldAck) {
            setVdpInterruptAck();
            shouldAck = false;
        }
    }

    private void evaluateRaiseInterrupt() {
        if (shouldRaise) {
            logInfo("Raise: Arbiter IPL: {}", ((ipl1 << 1) + (ipl2 << 2)));
            logInfo("Raise: Vdp State: vint: {}, hint: {}", isVdpVInt(), isVdpHInt());
            raiseInterrupts68k();
            raiseInterruptsZ80();
            shouldAck = true;
            shouldRaise = false;
        }
    }

    protected boolean raiseInterrupts68k() {
        int level = ipl2 > 0 ? M68kProvider.HBLANK_INTERRUPT_LEVEL : 0;
        //VINT has priority
        level = ipl1 > 0 ? M68kProvider.VBLANK_INTERRUPT_LEVEL : level;
        if (level > 0) {
            logInfo("raise 68k intLevel: {}", level);
            return m68k.raiseInterrupt(level);
        }
        return false;
    }

    private void raiseInterruptsZ80() {
        boolean shouldRaiseZ80 = ipl1 > 0;
        if (shouldRaiseZ80) {
            logInfo("Z80 raise interrupt");
            z80.interrupt();
        }

    }

    private void logInfo(String str, Object... args) {
        if (verbose) {
            String head = "vdpHV: {}, {} ";
            String msg = ParameterizedMessage.format(str, args);
            LOG.info(new ParameterizedMessage(head + msg, Long.toHexString(vdp.getHCounter()), Long.toHexString(vdp.getVCounter())));
        }
    }

    public void handleVdpInterruptsZ80() {
        //TODO needed? delete?
    }
}
