package mcd.dict;

import mcd.bus.McdWordRamHelper;
import mcd.cdd.CdBiosHelper;
import mcd.util.BuramHelper;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import static mcd.dict.MegaCdDict.MCD_SUB_BRAM_SIZE;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_SIZE;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_COMM0;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._1M;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;
import static mcd.dict.MegaCdRegWriteHandlers.setByteHandlersMain;
import static mcd.dict.MegaCdRegWriteHandlers.setByteHandlersSub;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.readBuffer;
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

    public final McdWordRamHelper wramHelper;
    public final static int WRITABLE_HINT_UNUSED = 0xFFFF_FFFF;
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
        BuramHelper.check_format_bram(backupRam);
        sysGateRegsBuf = new ByteBuffer[2];
        sysGateRegsBuf[0] = ByteBuffer.wrap(sysGateRegs[0]);
        sysGateRegsBuf[1] = ByteBuffer.wrap(sysGateRegs[1]);
        wramHelper = new McdWordRamHelper(this, wordRam01);
    }

    public void writeProgRam(int address, int val, Size size) {
        if (address < MCD_PRAM_WRITE_PROTECT_AREA_END) {
            if (((address >> 8) & MCD_PRAM_WRITE_PROTECT_BLOCK_MASK) >= (writeProtectRam << 1)) {
                writeData(prgRam, address, val, size);
                CdBiosHelper.checkMemRegion(prgRam, address);
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
