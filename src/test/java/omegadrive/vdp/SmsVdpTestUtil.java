package omegadrive.vdp;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class SmsVdpTestUtil {

    public static boolean isVintOn(SmsVdp vdp) {
        return (vdp.getStatus() & SmsVdp.STATUS_VINT) > 0;
    }

    public static int runToVint(SmsVdp vdp, boolean disableVIntPending) {
        int cycles = 0;
        do {
            vdp.runSlot();
            cycles++;
        } while (!isVintOn(vdp));
        if (disableVIntPending) {
            vdp.controlRead();
        }
        return cycles;
    }
}
