package omegadrive.bus.megacd;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;
import s32x.util.S32xUtil.CpuDeviceAccess;

import java.io.Serial;
import java.io.Serializable;

import static omegadrive.util.Util.th;
import static s32x.util.S32xUtil.CpuDeviceAccess.M68K;
import static s32x.util.S32xUtil.CpuDeviceAccess.SUB_M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MegaCdMemoryContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 9209612516906245680L;

    private static final Logger LOG = LogHelper.getLogger(MegaCdMemoryContext.class.getSimpleName());

    public static final MegaCdMemoryContext NO_CONTEXT = new MegaCdMemoryContext();
    public static final int MCD_WORD_RAM_2M_SIZE = 0x40_000;
    public static final int MCD_WORD_RAM_1M_SIZE = MCD_WORD_RAM_2M_SIZE >> 1;
    public static final int MCD_PRG_RAM_SIZE = 0x80_000;
    public static final int MCD_GATE_REGS_SIZE = 0x40;

    public static final int MDC_SUB_GATE_REGS_SIZE = 0x200;

    public static final int MCD_GATE_REGS_MASK = MCD_GATE_REGS_SIZE - 1;
    public static final int MCD_WORD_RAM_1M_MASK = MCD_WORD_RAM_1M_SIZE - 1;
    public static final int MCD_WORD_RAM_2M_MASK = MCD_WORD_RAM_2M_SIZE - 1;
    public static final int MCD_PRG_RAM_MASK = MCD_PRG_RAM_SIZE - 1;

    public byte[] prgRam, mainGateRegs, subGateRegs;
    public byte[][] wordRam01 = new byte[2][1];

    public WramSetup wramSetup = WramSetup.W_2M_MAIN;

    //bit0: mode,
    public enum WramSetup {
        W_2M_MAIN(M68K),
        W_2M_SUB(SUB_M68K);
        public CpuDeviceAccess cpu;

        WramSetup(CpuDeviceAccess c) {
            this.cpu = c;
        }
    }

    public MegaCdMemoryContext() {
        prgRam = new byte[MCD_PRG_RAM_SIZE];
        wordRam01[0] = new byte[MCD_WORD_RAM_1M_SIZE];
        wordRam01[1] = new byte[MCD_WORD_RAM_1M_SIZE];
        mainGateRegs = new byte[MCD_GATE_REGS_SIZE];
        subGateRegs = new byte[MDC_SUB_GATE_REGS_SIZE];
    }

    public void writeWordRam(CpuDeviceAccess cpu, int address, int value, Size size) {
        if (cpu == wramSetup.cpu) {
            writeWordRam(address & MCD_WORD_RAM_2M_MASK >> 17, address, value, size);
        } else {
            System.out.println("wtf");
        }
    }

    public int readWordRam(CpuDeviceAccess cpu, int address, Size size) {
        if (cpu == wramSetup.cpu) {
            return readWordRam(address & MCD_WORD_RAM_2M_MASK >> 17, address, size);
        } else {
            System.out.println("wtf");
            return size.getMask();
        }
    }

    private void writeWordRam(int bank, int address, int value, Size size) {
        Util.writeData(wordRam01[bank], size, address & MCD_WORD_RAM_1M_MASK, value);
    }

    private int readWordRam(int bank, int address, Size size) {
        return Util.readData(wordRam01[bank], size, address & MCD_WORD_RAM_1M_MASK);
    }

    public WramSetup update(CpuDeviceAccess c, int reg2) {
        int mode = reg2 & 4;
        int dmna = reg2 & 2;
        int ret = reg2 & 1;
        if (mode > 0) {
            System.out.println("TODO");
            return wramSetup;
        }
        //ret > 0, signal to SUB the swap ??
        wramSetup = dmna == 0 ? WramSetup.W_2M_MAIN : WramSetup.W_2M_SUB;
        return wramSetup;
    }

    public static void main(String[] args) {
        int base = 0x600_000;
        int start = base + MCD_WORD_RAM_1M_SIZE - 5;
        for (int i = start; i < start + 10; i++) {
            System.out.println(th(i) + "," + ((i & MCD_WORD_RAM_2M_MASK) >> 17));
        }
    }
}
