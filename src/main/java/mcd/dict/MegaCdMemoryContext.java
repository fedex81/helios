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
import java.util.Arrays;
import java.util.function.BiConsumer;

import static mcd.dict.MegaCdDict.MCD_SUB_BRAM_SIZE;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_SIZE;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._1M;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;
import static mcd.dict.MegaCdRegWriteHandlers.setByteHandlersMain;
import static mcd.dict.MegaCdRegWriteHandlers.setByteHandlersSub;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.Util.th;
import static omegadrive.util.Util.writeData;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MegaCdMemoryContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 9209612516906245680L;

    private static final Logger LOG = LogHelper.getLogger(MegaCdMemoryContext.class.getSimpleName());
    public static final int MCD_WORD_RAM_2M_SIZE = 0x40_000;
    public static final int MCD_WORD_RAM_1M_SIZE = MCD_WORD_RAM_2M_SIZE >> 1;
    public static final int MCD_PRG_RAM_SIZE = 0x80_000;
    public static final int MCD_WORD_RAM_1M_MASK = MCD_WORD_RAM_1M_SIZE - 1;
    public static final int MCD_WORD_RAM_2M_MASK = MCD_WORD_RAM_2M_SIZE - 1;
    public static final int MCD_PRG_RAM_MASK = MCD_PRG_RAM_SIZE - 1;

    public static final int NUM_SYS_REG_NON_SHARED = MCD_COMM0.addr;

    public static final int MCD_PRAM_WRITE_PROTECT_AREA_END = 0x1FE00;
    public static final int MCD_PRAM_WRITE_PROTECT_BLOCK_SIZE = 0x200;
    public static final int MCD_PRAM_WRITE_PROTECT_BLOCK_MASK = MCD_PRAM_WRITE_PROTECT_BLOCK_SIZE - 1;

    public final byte[] prgRam, commonGateRegs, backupRamArr;
    public final byte[][] sysGateRegs;
    public final byte[][] wordRam01 = new byte[2][1];

    public final byte[] writeableHint = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    public transient final ByteBuffer[] sysGateRegsBuf;
    public transient final ByteBuffer commonGateRegsBuf, backupRam;

    public int writeProtectRam = 0;

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
        backupRamArr = new byte[MCD_SUB_BRAM_SIZE];
        commonGateRegsBuf = ByteBuffer.wrap(commonGateRegs);
        backupRam = ByteBuffer.wrap(backupRamArr);
        sysGateRegsBuf = new ByteBuffer[2];
        sysGateRegsBuf[0] = ByteBuffer.wrap(sysGateRegs[0]);
        sysGateRegsBuf[1] = ByteBuffer.wrap(sysGateRegs[1]);
    }

    public void writeWordRam(CpuDeviceAccess cpu, int address, int value, Size size) {
        switch (size) {
            case WORD -> writeWordRamWord(cpu, address, value);
            case LONG -> {
                writeWordRamWord(cpu, address, value >> 16);
                writeWordRamWord(cpu, address + 2, (short) value);
            }
            case BYTE -> {
                int bank = getBank(wramSetup, cpu, address);
                int addr = getAddress(wramSetup, address, bank) | (address & 1);
                Util.writeData(wordRam01[bank], addr, value, Size.BYTE);
            }
            default -> {
                assert false;
            }
        }
    }

    //TODO test long access
    public int readWordRam(CpuDeviceAccess cpu, int address, Size size) {
        return switch (size) {
            case WORD -> readWordRamWord(cpu, address);
            case LONG -> (readWordRamWord(cpu, address) << 16) | readWordRamWord(cpu, address + 2);
            case BYTE -> {
                int shift = ~address & 1;
                yield readWordRamWord(cpu, address) >> (shift << 3);
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
            return Size.WORD.getMask();
        }
    }

    private void writeWordRamBank(int bank, int address, int value) {
        Util.writeData(wordRam01[bank], getAddress(wramSetup, address, bank), value, Size.WORD);
    }

    public int readWordRamBank(int bank, int address) {
        return Util.readData(wordRam01[bank], getAddress(wramSetup, address, bank), Size.WORD);
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

    public static int getAddress(WramSetup wramSetup, int address, int bank) {
        if (wramSetup.mode == _2M) {
            address = ((address & MCD_WORD_RAM_2M_MASK) >> 1) - bank;
        } else {
            address = (address & MCD_WORD_RAM_1M_MASK);
        }
        assert (address & 1) == 0;
        return address;
    }

    public WramSetup update(CpuDeviceAccess c, int reg2) {
        int mode = reg2 & 4;
        int dmna = reg2 & 2;
        int ret = reg2 & 1;
        WramSetup prev = wramSetup;
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
        if (prev != wramSetup) {
            LogHelper.logInfo(LOG, "{} WRAM setup changed: {} -> {}", c, prev, wramSetup);
        }
        int wpVal = (reg2 >> 8) & 0xFF;
        if (wpVal != writeProtectRam) {
            LOG.info("M PROG-RAM Write protection: {} -> {}", th(writeProtectRam), th(wpVal));
            writeProtectRam = wpVal;
        }
        return wramSetup;
    }

    public void writeProgRam(int address, int val, Size size) {
        if (address < MCD_PRAM_WRITE_PROTECT_AREA_END) {
            if (((address >> 8) & MCD_PRAM_WRITE_PROTECT_BLOCK_MASK) >= (writeProtectRam << 1)) {
                writeData(prgRam, address, val, size);
            } else {
                LogHelper.logWarnOnce(LOG, "Ignoring PRG-RAM write: {} {}, wp {}", th(address), size, th(writeProtectRam));
            }
            return;
        }
        writeData(prgRam, address, val, size);
    }

    public ByteBuffer getGateSysRegs(CpuDeviceAccess cpu) {
        assert cpu == M68K || cpu == SUB_M68K;
        return sysGateRegsBuf[cpu == M68K ? 0 : 1];
    }

    public enum SharedBit {
        RET(0, MCD_MEM_MODE.addr + 1), DMNA(1, MCD_MEM_MODE.addr + 1),
        MODE(2, MCD_MEM_MODE.addr + 1), DD0(0, MCD_CDC_MODE.addr), DD1(1, MCD_CDC_MODE.addr), DD2(2, MCD_CDC_MODE.addr);

        public final int pos, regBytePos;

        SharedBit(int p, int rbp) {
            this.pos = p;
            this.regBytePos = rbp;
        }
    }

    public void setSharedBits(CpuDeviceAccess cpu, int byteVal, SharedBit... bitDef) {
        Arrays.stream(bitDef).forEach(bd -> setSharedBit(cpu, byteVal, bd));
    }

    /**
     * Given the byte value, only set the bit at bitPos as defined in SharedBit
     */
    public void setSharedBit(CpuDeviceAccess cpu, int byteVal, SharedBit bitDef) {
        assert cpu == M68K || cpu == SUB_M68K;
        CpuDeviceAccess other = cpu == M68K ? SUB_M68K : M68K;
        ByteBuffer regs = getGateSysRegs(other);
        BufferUtil.setBit(regs, bitDef.regBytePos, bitDef.pos, (byteVal >> bitDef.pos) & 1, Size.BYTE);
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
