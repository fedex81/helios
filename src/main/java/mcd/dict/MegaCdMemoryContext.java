package mcd.dict;

import omegadrive.util.BufferUtil;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_COMM0;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._1M;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;
import static mcd.dict.MegaCdRegWriteHandlers.setByteHandlersMain;
import static mcd.dict.MegaCdRegWriteHandlers.setByteHandlersSub;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.Util.th;

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

    public static final int NUM_SYS_REG_NON_SHARED = MCD_COMM0.addr;

    public byte[] prgRam;
    public byte[][] sysGateRegs;
    public byte[] commonGateRegs;
    public byte[][] wordRam01 = new byte[2][1];

    public transient final ByteBuffer[] sysGateRegsBuf;
    public transient final ByteBuffer commonGateRegsBuf;

    public WramSetup wramSetup = WramSetup.W_2M_MAIN;

    public enum WordRamMode {_1M, _2M}

    //bit0: mode,
    public enum WramSetup {
        W_2M_MAIN(_2M, M68K),
        W_2M_SUB(_2M, SUB_M68K),
        W_1M_WR0_MAIN(_1M, M68K),
        W_1M_WR0_SUB(_1M, SUB_M68K);

        public final CpuDeviceAccess cpu;
        public final WordRamMode mode;

        WramSetup(WordRamMode mode, CpuDeviceAccess c) {
            this.cpu = c;
            this.mode = mode;
        }
    }

    public MegaCdMemoryContext() {
        prgRam = new byte[MCD_PRG_RAM_SIZE];
        wordRam01[0] = new byte[MCD_WORD_RAM_1M_SIZE];
        wordRam01[1] = new byte[MCD_WORD_RAM_1M_SIZE];
        sysGateRegs = new byte[2][NUM_SYS_REG_NON_SHARED];
        commonGateRegs = new byte[MDC_SUB_GATE_REGS_SIZE];
        commonGateRegsBuf = ByteBuffer.wrap(commonGateRegs);
        sysGateRegsBuf = new ByteBuffer[2];
        sysGateRegsBuf[0] = ByteBuffer.wrap(sysGateRegs[0]);
        sysGateRegsBuf[1] = ByteBuffer.wrap(sysGateRegs[1]);
    }

    public void writeWordRam(CpuDeviceAccess cpu, int address, int value, Size size) {
        //TODO is something doing this? LONG access in 2M
        assert size == Size.LONG ? wramSetup.mode == _1M : true;
        switch (size) {
            case WORD -> writeWordRamWord(cpu, address, value);
            case LONG -> {
                writeWordRamWord(cpu, address, value >> 16);
                writeWordRamWord(cpu, address + 2, (short) value);
            }
            default -> {
                assert false;
            }
        }
    }

    //TODO test long access
    public int readWordRam(CpuDeviceAccess cpu, int address, Size size) {
        //TODO is something doing this? LONG access in 2M
        assert size == Size.LONG ? wramSetup.mode == _1M : true;
        return switch (size) {
            case WORD -> readWordRamWord(cpu, address);
            case LONG -> (readWordRamWord(cpu, address) << 16) | readWordRamWord(cpu, address + 2);
            default -> {
                assert false;
                yield size.getMask();
            }
        };
    }

    public void writeWordRamWord(CpuDeviceAccess cpu, int address, int value) {
        if (cpu == wramSetup.cpu || wramSetup.mode == _1M) {
            writeWordRamBank(getBank(wramSetup, cpu, address), address, value);
        } else {
            logWarnOnce(LOG, "{} writing WRAM but setup is: {}", cpu, wramSetup);
            assert false;
        }
    }


    public int readWordRamWord(CpuDeviceAccess cpu, int address) {
        if (cpu == wramSetup.cpu || wramSetup.mode == _1M) {
            return readWordRamBank(getBank(wramSetup, cpu, address), address);
        } else {
            logWarnOnce(LOG, "{} reading WRAM but setup is: {}", cpu, wramSetup);
            assert false;
            return Size.WORD.getMask();
        }
    }

    private void writeWordRamBank(int bank, int address, int value) {
        Util.writeData(wordRam01[bank], Size.WORD, getAddress(wramSetup, address, bank), value);
    }

    private int readWordRamBank(int bank, int address) {
        return Util.readData(wordRam01[bank], Size.WORD, getAddress(wramSetup, address, bank));
    }

    public static int getBank(WramSetup wramSetup, CpuDeviceAccess cpu, int address) {
        if (wramSetup.mode == _2M) {
            return ((address & MCD_WORD_RAM_2M_MASK) & 2) >> 1;
        }
        return getBank1M(wramSetup, cpu);
    }

    public static int getBank1M(WramSetup wramSetup, CpuDeviceAccess cpu) {
        assert wramSetup.mode == _1M;
        return wramSetup.cpu == cpu ? 0 : 1;
    }

    private static int getAddress(WramSetup wramSetup, int address, int bank) {
        int a = address;
        if (wramSetup.mode == _2M) {
            address = ((address & MCD_WORD_RAM_2M_MASK) >> 1) - bank;
        } else {
            address = (address & MCD_WORD_RAM_1M_MASK);
        }
        if (address == 0) {
            System.out.println(th(a) + "," + th(address));
        }
        return address;
    }

    public WramSetup update(CpuDeviceAccess c, int reg2) {
        int mode = reg2 & 4;
        int dmna = reg2 & 2;
        int ret = reg2 & 1;
        if (mode > 0) {
            if (c == SUB_M68K) {
                LOG.warn("{} Switch bank requested, ret: {}, current setup {}", c, ret, wramSetup);
            }
            //DMNA has no effect, ie. MAIN cannot switch banks directly, it needs to ask SUB to do it
            wramSetup = ret == 0 ? WramSetup.W_1M_WR0_MAIN : WramSetup.W_1M_WR0_SUB;
            LOG.warn("Setting wordRam to {}", wramSetup);
            return wramSetup;
        } else if (mode == 0) {
            if (wramSetup.mode == _1M) {
                wramSetup = WramSetup.W_2M_MAIN;
            }
            if (c == M68K) {
                wramSetup = dmna > 0 ? WramSetup.W_2M_SUB : wramSetup;
            } else if (c == SUB_M68K) {
                wramSetup = ret > 0 ? WramSetup.W_2M_MAIN : wramSetup;
            }
        }
        return wramSetup;
    }

    public ByteBuffer getGateSysRegs(CpuDeviceAccess cpu) {
        assert cpu == M68K || cpu == SUB_M68K;
        return sysGateRegsBuf[cpu == M68K ? 0 : 1];
    }

    public enum SharedBit {RET, DMNA, MODE}

    public void setSharedBit(CpuDeviceAccess cpu, SharedBit bit, int val) {
        assert val <= 1;
        assert cpu == M68K || cpu == SUB_M68K;
        CpuDeviceAccess other = cpu == M68K ? SUB_M68K : M68K;
        ByteBuffer regs = getGateSysRegs(other);
        switch (bit) {
            case MODE -> BufferUtil.setBit(regs, 3, 2, val, Size.BYTE);
            case DMNA -> BufferUtil.setBit(regs, 3, 1, val, Size.BYTE);
            case RET -> BufferUtil.setBit(regs, 3, 0, val, Size.BYTE);
        }
    }

    public ByteBuffer getRegBuffer(CpuDeviceAccess cpu, MegaCdDict.RegSpecMcd regSpec) {
        if (regSpec.addr >= NUM_SYS_REG_NON_SHARED) {
            return commonGateRegsBuf;
        }
        return getGateSysRegs(cpu);
    }

    public int handleRegWrite(CpuDeviceAccess cpu, MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        var bh = cpu == M68K ? setByteHandlersMain : setByteHandlersSub;
        BiConsumer<MegaCdMemoryContext, Integer>[] setByteRegHandler = bh[regSpec.addr];
        ByteBuffer b = getRegBuffer(cpu, regSpec);
        assert setByteRegHandler != null;
        switch (size) {
            case WORD -> {
                setByteRegHandler[1].accept(this, data & 0xFF); //LSB
                setByteRegHandler[0].accept(this, (data >> 8) & 0xFF); //MSB
            }
            case BYTE -> setByteRegHandler[address & 1].accept(this, data);
        }
        return readBuffer(b, regSpec.addr, Size.WORD);
    }
}
