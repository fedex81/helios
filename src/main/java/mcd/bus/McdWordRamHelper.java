package mcd.bus;

import mcd.asic.AsicModel.StampPriorityMode;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.*;
import org.slf4j.Logger;

import java.util.Objects;

import static mcd.dict.MegaCdDict.BitRegDef.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_MEM_MODE;
import static mcd.dict.MegaCdDict.SharedBitDef.*;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._1M;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;
import static mcd.dict.MegaCdMemoryContext.WramSetup.*;
import static mcd.util.McdRegBitUtil.setBitDefInternal;
import static mcd.util.McdRegBitUtil.setSharedBitBothCpu;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdWordRamHelper {

    private static final Logger LOG = LogHelper.getLogger(McdWordRamHelper.class.getSimpleName());

    public static final boolean WRAM_DIAG = false;

    public enum UpdateImpl {BLASTEM, GPGX}

    private UpdateImpl updateImpl = UpdateImpl.BLASTEM;
    private final MegaCdMemoryContext memoryContext;
    private final byte[][] wordRam01;

    private boolean mainHasWord2m = true;
    private boolean bankToggle = false;
    private boolean mainSwapRequest = false;

    private int gpgxDmna = 0;

    public McdWordRamHelper(MegaCdMemoryContext memoryContext, byte[][] wordRam01) {
        this.memoryContext = memoryContext;
        this.wordRam01 = wordRam01;
    }

    public void writeWordRam(CpuDeviceAccess cpu, int address, int value, Size size) {
        switch (size) {
            case WORD -> writeWordRamWord(cpu, address, value);
            case LONG -> {
                writeWordRamWord(cpu, address, value >> 16);
                writeWordRamWord(cpu, address + 2, (short) value);
            }
            case BYTE -> {
                int bank = getBank(memoryContext.wramSetup, cpu, address);
                int addr = getAddress(memoryContext.wramSetup, address) | (address & 1);
                Util.writeDataByte(wordRam01[bank], addr, value);
            }
            default -> {
                assert false;
            }
        }
    }

    public int readWordRam(CpuDeviceAccess cpu, int address, Size size) {
        return switch (size) {
            case WORD -> readWordRamWord(cpu, address);
            case LONG -> (readWordRamWord(cpu, address) << 16) | readWordRamWord(cpu, address + 2);
            case BYTE ->
                //int shift = (~address & 1) << 3;
                    readWordRamWord(cpu, address & ~1) >> ((~address & 1) << 3);
        };
    }

    public void writeWordRamWord(CpuDeviceAccess cpu, int address, int value) {
        if (cpu == memoryContext.wramSetup.cpu || memoryContext.wramSetup.mode == _1M) {
            writeWordRamBank(getBank(memoryContext.wramSetup, cpu, address), address, value);
        } else {
            //BIOS JP when playing CDDA
            logWarnOnce(LOG, "{} writing WRAM but setup is: {}", cpu, memoryContext.wramSetup);
            LOG.info("WRAM DIAG {} WRITE MISMATCH at {} val {} but setup is {}", cpu, th(address), th(value), memoryContext.wramSetup);
//            assert false;
        }
    }


    public int readWordRamWord(CpuDeviceAccess cpu, int address) {
        if (cpu == memoryContext.wramSetup.cpu || memoryContext.wramSetup.mode == _1M) {
            return readWordRamBank(getBank(memoryContext.wramSetup, cpu, address), address);
        } else {
            logWarnOnce(LOG, "{} reading WRAM but setup is: {}", cpu, memoryContext.wramSetup);
            LOG.info("WRAM DIAG {} READ MISMATCH at {} but setup is {} -> returns FFFF", cpu, th(address), memoryContext.wramSetup);
            return Size.WORD.getMask();
        }
    }

    public void writeWordRamBank(int bank, int address, int value) {
        Util.writeDataWord(wordRam01[bank], getAddress(memoryContext.wramSetup, address), value);
    }

    public int readWordRamBank(int bank, int address) {
        return Util.readDataWord(wordRam01[bank], getAddress(memoryContext.wramSetup, address));
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

    public static int getAddress(WramSetup wramSetup, int address) {
        if (wramSetup.mode == _2M) {
            return ((address & MCD_WORD_RAM_2M_MASK) >> 1) & ~1; //TODO test
        }
        return address & MCD_WORD_RAM_1M_MASK;
    }

    public int writeReg2(CpuDeviceAccess cpu, int address, int data, Size size) {
        int regWord = readBuffer(memoryContext.getGateSysRegs(cpu),
                MCD_MEM_MODE.addr, Size.WORD);
        boolean lsbWritten = size == Size.WORD || (address & 1) == 1;
        boolean msbWritten = size == Size.WORD || (address & 1) == 0;
        int msbData = (size == Size.WORD ? data >> 8 : data) & 0xFF;
        if (msbWritten && cpu == M68K) {
            regWord = (regWord & 0xFF) | (msbData << 8);
        }
        if (lsbWritten) {
            regWord = (regWord & 0xFF00) | (data & 0xFF); //overlay the new byte
        }
        if (Objects.requireNonNull(updateImpl) != UpdateImpl.GPGX) {
            updateBlastem(cpu, regWord);
        } else {
            updateGpgx(cpu, regWord);
        }
        //TODO check: writable per mcd-verificator
        if (msbWritten && cpu == M68K) {
            updateWriteProtect((regWord >> 8) & 0xFF);
        }
        return readBuffer(memoryContext.getGateSysRegs(cpu), MCD_MEM_MODE.addr, Size.WORD);
    }

    private WramSetup updateBlastem(CpuDeviceAccess c, int reg2) {
        int reg2Lsb = reg2 & 0xFF;
        int ret = reg2Lsb & RET.getBitMask();
        int dmna = reg2Lsb & DMNA.getBitMask();
        int mode = reg2Lsb & MODE.getBitMask();
        int priority = reg2Lsb & (PM0.getBitMask() | PM1.getBitMask());
        WramSetup prev = memoryContext.wramSetup;
        int ga = 0;
        if (c == SUB_M68K) {
            if (ret != 0) {
                mainHasWord2m = true; //SUB setting RET hands 2M to MAIN
            }
            boolean oldBankToggle = bankToggle;
            bankToggle = ret != 0;
            if (mode != 0) {
                //1M mode: keep PRIORITY|RET|MODE, add DMNA if a swap is pending
                if (oldBankToggle != bankToggle) {
                    mainSwapRequest = false;
                }
                ga = priority | ret | mode;
                if (mainSwapRequest) {
                    ga |= DMNA.getBitMask();
                }
            } else {
                //2M mode: keep PRIO, RET/DMNA reflect ownership
                ga = priority | (mainHasWord2m ? RET.getBitMask() : DMNA.getBitMask());
            }
        } else {
            int curMode = (memoryContext.wramSetup.mode == _1M) ? MODE.getBitMask() : 0;
            int v = readBuffer(memoryContext.getGateSysRegs(M68K), MCD_MEM_MODE.addr + 1, Size.BYTE);
            int curPrio = v & (PM0.getBitMask() | PM1.getBitMask());
            if (curMode != 0) {
                //1M Mode
                if (dmna == 0) {
                    dmna = DMNA.getBitMask(); //DMNA stays set
                    mainSwapRequest = true; //MAIN asks SUB to switch banks
                } else {
                    mainHasWord2m = false;
                }
                ga = curPrio | curMode | (bankToggle ? RET.getBitMask() : 0) | dmna;
            } else {
                //2M mode
                if (dmna != 0) {
                    //MAIN hands 2M WRAM to SUB
                    mainHasWord2m = false;
                }
                ga = curPrio | (mainHasWord2m ? RET.getBitMask() : DMNA.getBitMask());
            }
        }
        setSharedBitBothCpu(memoryContext, RET, ga);
        setSharedBitBothCpu(memoryContext, DMNA, ga);
        setSharedBitBothCpu(memoryContext, MODE, ga);
        setBitDefInternal(memoryContext, SUB_M68K, PM0, ga);
        setBitDefInternal(memoryContext, SUB_M68K, PM1, ga);
        if (c == M68K) {
            setBitDefInternal(memoryContext, M68K, BK0, ga);
            setBitDefInternal(memoryContext, M68K, BK1, ga);
        }
        if ((ga & MODE.getBitMask()) != 0) {
            memoryContext.wramSetup = bankToggle ? W_1M_WR0_SUB : W_1M_WR0_MAIN;
        } else {
            memoryContext.wramSetup = mainHasWord2m ? W_2M_MAIN : W_2M_SUB;
        }
        if (prev != memoryContext.wramSetup) {
            LogHelper.logInfo(LOG, "{} WRAM setup changed: {} -> {}", c, prev, memoryContext.wramSetup);
        }
        return memoryContext.wramSetup;
    }

    private WramSetup updateGpgx(CpuDeviceAccess c, int reg2) {
        return null;
    }

    private void updateWriteProtect(int wpVal) {
        writeBufferRaw(memoryContext.getGateSysRegs(M68K), MCD_MEM_MODE.addr, wpVal, Size.BYTE);
        writeBufferRaw(memoryContext.getGateSysRegs(SUB_M68K), MCD_MEM_MODE.addr, wpVal, Size.BYTE);
        if (wpVal != memoryContext.writeProtectRam) {
            LOG.info("M PROG-RAM Write protection: {} -> {}", th(memoryContext.writeProtectRam), th(wpVal));
            memoryContext.writeProtectRam = wpVal;
        }
    }

    public int readDotMapped(int address, Size size) {
        return switch (size) {
            case BYTE -> readDotMappedByte(address);
            case WORD -> (readDotMappedByte(address) << 8) | readDotMappedByte(address + 1);
            //batman returns (E), using clr.l
            case LONG -> (readDotMappedByte(address) << 24) | (readDotMappedByte(address + 1) << 16) |
                    (readDotMappedByte(address + 2) << 8) | (readDotMappedByte(address + 3) << 0);
        };
    }

    public void writeDotMapped(StampPriorityMode spm, int address, int data, Size size) {
        switch (size) {
            case BYTE -> writeDotMappedByte(spm, address, data);
            case WORD -> {
                writeDotMappedByte(spm, address, data >> 8);
                writeDotMappedByte(spm, address + 1, data);
            }
            case LONG -> {
                writeDotMappedByte(spm, address, data >> 24);
                writeDotMappedByte(spm, address + 1, data >> 16);
                writeDotMappedByte(spm, address + 2, data >> 8);
                writeDotMappedByte(spm, address + 3, data >> 0);
            }
        }
    }

    private int readDotMappedByte(int address) {
        assert MdRuntimeData.getAccessTypeExt() == SUB_M68K;
        byte[] wramBank = wordRam01[memoryContext.wramSetup.cpu == SUB_M68K ? 0 : 1];
        int addr = (address & MCD_WORD_RAM_1M_MASK) >> 1;
        int shift = (~address & 1) << 2;
        return (wramBank[addr] >> shift) & 0xF;
    }

    private void writeDotMappedByte(StampPriorityMode stampPriorityMode, int address, int data) {
        byte[] wramBank = wordRam01[memoryContext.wramSetup.cpu == SUB_M68K ? 0 : 1];
        int addr = (address & MCD_WORD_RAM_1M_MASK) >> 1;
        boolean doWrite = switch (stampPriorityMode) {
            case PM_OFF -> true;
            //if current nibble == 0, write
            case UNDERWRITE -> ArrayEndianUtil.getNibbleInByteBE(wramBank[addr], address & 1) == 0;
            //if data > 0, overwrite the existing value
            case OVERWRITE -> ArrayEndianUtil.getNibbleInByteBE(data, address & 1) > 0;
            default -> {
                assert false;
                yield false;
            }
        };
        if (doWrite) {
            wramBank[addr] = (byte) ArrayEndianUtil.setNibbleInByteBE(wramBank[addr], data, address & 1);
        }
    }
}
