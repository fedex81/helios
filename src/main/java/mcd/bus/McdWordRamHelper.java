package mcd.bus;

import mcd.asic.AsicModel.StampPriorityMode;
import mcd.dict.MegaCdMemoryContext;
import mcd.util.McdRegBitUtil;
import omegadrive.util.*;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import org.slf4j.Logger;

import static mcd.dict.MegaCdDict.SharedBitDef.*;
import static mcd.dict.MegaCdMemoryContext.MCD_WORD_RAM_1M_MASK;
import static mcd.dict.MegaCdMemoryContext.MCD_WORD_RAM_2M_MASK;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._1M;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;
import static mcd.util.McdWramCell.linearCellMap;
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
    private MegaCdMemoryContext memoryContext;
    private byte[][] wordRam01;

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
                int addr = getAddress(memoryContext.wramSetup, address, bank) | (address & 1);
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
//            assert false;
        }
    }


    public int readWordRamWord(CpuDeviceAccess cpu, int address) {
        if (cpu == memoryContext.wramSetup.cpu || memoryContext.wramSetup.mode == _1M) {
            return readWordRamBank(getBank(memoryContext.wramSetup, cpu, address), address);
        } else {
            logWarnOnce(LOG, "{} reading WRAM but setup is: {}", cpu, memoryContext.wramSetup);
            return Size.WORD.getMask();
        }
    }

    public void writeWordRamBank(int bank, int address, int value) {
        Util.writeDataWord(wordRam01[bank], getAddress(memoryContext.wramSetup, address, bank), value);
    }

    public int readWordRamBank(int bank, int address) {
        return Util.readDataWord(wordRam01[bank], getAddress(memoryContext.wramSetup, address, bank));
    }

    public static int getBank(MegaCdMemoryContext.WramSetup wramSetup, CpuDeviceAccess cpu, int address) {
        if (wramSetup.mode == _2M) {
            return ((address & MCD_WORD_RAM_2M_MASK) & 2) >> 1;
        }
        return getBank1M(wramSetup, cpu);
    }

    public static int getBank1M(MegaCdMemoryContext.WramSetup wramSetup, CpuDeviceAccess cpu) {
        assert wramSetup.mode == _1M;
        return wramSetup.cpu == cpu ? 0 : 1;
    }

    public static int getAddress(MegaCdMemoryContext.WramSetup wramSetup, int address, int bank) {
        if (wramSetup.mode == _2M) {
            address = ((address & MCD_WORD_RAM_2M_MASK) >> 1) - bank;
        } else {
            address = (address & MCD_WORD_RAM_1M_MASK);
        }
        return address;
    }

    public MegaCdMemoryContext.WramSetup update(CpuDeviceAccess c, int reg2) {
        int mode = reg2 & MODE.getBitMask();
        int dmna = reg2 & DMNA.getBitMask();
        int ret = reg2 & RET.getBitMask();
        MegaCdMemoryContext.WramSetup prev = memoryContext.wramSetup;
        if (mode > 0) {
            if (c == SUB_M68K) {
                LogHelper.logWarnOnceWhenEn(LOG, "{} Switch bank requested, ret: {}, current setup {}", c, ret, memoryContext.wramSetup);
                memoryContext.wramSetup = ret == 0 ? MegaCdMemoryContext.WramSetup.W_1M_WR0_MAIN : MegaCdMemoryContext.WramSetup.W_1M_WR0_SUB;
                if (ret > 0) {
                    dmna = 0;
                }
                McdRegBitUtil.setSharedBitBothCpu(memoryContext, DMNA, dmna << 1);
                LogHelper.logWarnOnceWhenEn(LOG, "Setting wordRam to {}", memoryContext.wramSetup);
            }
            if (c == M68K) {
                boolean swapRequest = dmna == 0;
                if (swapRequest) {
                    ret = ~ret & 1;
                    if (ret > 0) {
                        dmna = ret;
                    }
                    McdRegBitUtil.setSharedBitBothCpu(memoryContext, DMNA, dmna << 1);
                    McdRegBitUtil.setSharedBitBothCpu(memoryContext, RET, ret);
                    //DMNA has no effect, ie. MAIN cannot switch banks directly, it needs to ask SUB to do it
                    memoryContext.wramSetup = ret == 0 ? MegaCdMemoryContext.WramSetup.W_1M_WR0_MAIN : MegaCdMemoryContext.WramSetup.W_1M_WR0_SUB;
                    LogHelper.logWarnOnceWhenEn(LOG, "Setting wordRam to {}", memoryContext.wramSetup);
                }
            }
            return memoryContext.wramSetup;
        } else if (mode == 0) {
            if (memoryContext.wramSetup.mode == _1M) {
                memoryContext.wramSetup = MegaCdMemoryContext.WramSetup.W_2M_MAIN;
            }
            if (c == M68K) {
                memoryContext.wramSetup = dmna > 0 ? MegaCdMemoryContext.WramSetup.W_2M_SUB : memoryContext.wramSetup;
            } else if (c == SUB_M68K) {
                memoryContext.wramSetup = ret > 0 ? MegaCdMemoryContext.WramSetup.W_2M_MAIN : memoryContext.wramSetup;
            }
        }
        if (prev != memoryContext.wramSetup) {
            LogHelper.logInfo(LOG, "{} WRAM setup changed: {} -> {}", c, prev, memoryContext.wramSetup);
        }
        int wpVal = (reg2 >> 8) & 0xFF;
        if (wpVal != memoryContext.writeProtectRam) {
            LOG.info("M PROG-RAM Write protection: {} -> {}", th(memoryContext.writeProtectRam), th(wpVal));
            memoryContext.writeProtectRam = wpVal;
        }
        return memoryContext.wramSetup;
    }


    public void writeCellMapped(int addr, int data, Size size) {
        assert memoryContext.wramSetup.mode == _1M;
        switch (size) {
            case BYTE -> {
                int val = readCellMapped(addr, Size.WORD);
                ArrayEndianUtil.setByteInWordBE(val, data, addr & 1);
                writeCell(addr & ~1, val);
            }
            case WORD -> writeCell(addr, data);
            case LONG -> {
                writeCell(addr, data >> 16);
                writeCell(addr + 2, data);
            }
        }
    }

    public int readCellMapped(int address, Size size) {
        assert MdRuntimeData.getAccessTypeExt() == M68K;
        int assignedBank = memoryContext.wramSetup.cpu == M68K ? 0 : 1;
        int otherBank = ~assignedBank & 1;
        int word = readWordRamBank(otherBank, address & ~1);
        if (size == Size.BYTE) {
            logWarnOnce(LOG, "readCellMapped BYTE");
            return (address & 1) == 0 ? word >> 8 : word;
        } else if (size == Size.LONG) { //TODO check, Dune
            word = (word << 16) | readCellMapped(address + 2, Size.WORD);
            logWarnOnce(LOG, "readCellMapped LONG");
        }
        return word;
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

    private void writeCell(int a, int value) {
        int assignedBank = memoryContext.wramSetup.cpu == M68K ? 0 : 1;
        int otherBank = ~assignedBank & 1;
        writeWordRamBank(otherBank, linearCellMap[a & MCD_WORD_RAM_1M_MASK], value);
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
