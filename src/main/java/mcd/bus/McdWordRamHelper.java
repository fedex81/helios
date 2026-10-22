package mcd.bus;

import mcd.asic.AsicModel.StampPriorityMode;
import mcd.dict.MegaCdMemoryContext;
import mcd.util.McdWramCell;
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
import static mcd.util.McdWramCell.linearCellMap;
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

    public static final int MCD_WORD_RAM_CELL_IMAGE_START = MCD_WORD_RAM_1M_SIZE; //0x20_000

    public enum UpdateImpl {BLASTEM, GPGX}

    private UpdateImpl updateImpl = UpdateImpl.GPGX;
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

    public void setUpdateImpl(UpdateImpl updateImpl) {
        this.updateImpl = updateImpl;
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
            //TODO BatmanReturns ASIC writes here
            logWarnOnce(LOG, "{} writing WRAM but setup is: {}", cpu, memoryContext.wramSetup);
//            LOG.info("WRAM DIAG {} WRITE MISMATCH at {} val {} but setup is {}", cpu, th(address), th(value), memoryContext.wramSetup);
//            assert false;
        }
    }


    public int readWordRamWord(CpuDeviceAccess cpu, int address) {
        if (cpu == memoryContext.wramSetup.cpu || memoryContext.wramSetup.mode == _1M) {
            return readWordRamBank(getBank(memoryContext.wramSetup, cpu, address), address);
        } else {
            //TODO BatmanReturns, ASIC reads from here
            logWarnOnce(LOG, "{} reading WRAM but setup is: {}", cpu, memoryContext.wramSetup);
//            LOG.info("WRAM DIAG {} READ MISMATCH at {} but setup is {} -> returns FFFF", cpu, th(address), memoryContext.wramSetup);
            return Size.WORD.getMask();
        }
    }


    public int readWordRamAsic(int address) {
        return readWordRamBank(getBank(memoryContext.wramSetup, SUB_M68K, address), address);
    }

    public void writeWordRamAsic(int address, int value) {
        writeWordRamBank(getBank(memoryContext.wramSetup, SUB_M68K, address), address, value);
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
        int data = reg2 & 0xFF;
        int cur = readBuffer(memoryContext.getGateSysRegs(c), MCD_MEM_MODE.addr + 1, Size.BYTE);
        int reg1 = cur;
        WramSetup prev = memoryContext.wramSetup;
        if (c == M68K) {
            if ((reg1 & 4) != 0) {
                //1M mode
                if ((data & 2) != 0) {
                    gpgxDmna = 1; //writing to DMNA in 1M -> returns WRAM to SUB in 2M
                } else {
                    data |= 0x02; //writing 0 to DMNA in 1M actually sets DMNA
                    reg1 = (reg1 & ~0xc2) | (data & 0xc2); //update BK0-` & DMNA
                }
            } else {
                //2M mode
                if ((data & 2) != 0) { //writing 0 to DMNA in 2M does nothing
                    gpgxDmna = 1; //WRAM to SUB
                    reg1 = (reg1 & ~0xc3) | (data & 0xc2); //clear RET, update BK0-1, DMNA
                } else {
                    reg1 = (reg1 & ~0xc0) | (data & 0xc0); //update BK0-1 only
                }
            }
        } else {
            if (((data ^ reg1) & 5) != 0) { //MODE or RET changed
                if ((data & 4) != 0) {
                    //1M mode or 2M->1M switch
                    if ((data & 1) != 0) {
                        gpgxDmna = 0; //RET=1 in 1M returns WRAM to MAIN in 2M
                    }
                    reg1 = (reg1 & ~0x1f) | (data & 0x1d); //PM/MODE/RET, mask DMNA
                    finishGpgx(c, prev, reg1);
                    return memoryContext.wramSetup;
                } else {
                    //1M -> 2M switch (MODE just cleared) or 2M
                    if ((reg1 & 4) != 0) {
                        //1M->2M
                        data |= (~gpgxDmna & 1); //RET bit set during 1M mode?
                        if ((data & 1) == 0) {
                            data |= 2; //set DMNA
                            reg1 = (reg1 & ~0x1f) | (data & 0x1f);
                            finishGpgx(c, prev, reg1);
                            return memoryContext.wramSetup;
                        }
                    }
                    //RET set in 2m Mode
                    if ((data & 1) != 0) {
                        gpgxDmna = 0; //WRAM to main
                        reg1 = (reg1 & ~0x1f) | (data & 0x1d); //clear DMNA
                        finishGpgx(c, prev, reg1);
                        return memoryContext.wramSetup;
                    }
                }
            }
            //update PM0-1, MODE bits only
            reg1 = (reg1 & ~0x1c) | (data & 0x1c);
        }
        finishGpgx(c, prev, reg1);
        return memoryContext.wramSetup;
    }

    private void finishGpgx(CpuDeviceAccess c, WramSetup prev, int reg1) {
        //write the full reg3 low byte to both CPU buffers
        setSharedBitBothCpu(memoryContext, RET, reg1);
        setSharedBitBothCpu(memoryContext, DMNA, reg1);
        setSharedBitBothCpu(memoryContext, MODE, reg1);
        if (c == SUB_M68K) {
            setBitDefInternal(memoryContext, SUB_M68K, PM0, reg1);
            setBitDefInternal(memoryContext, SUB_M68K, PM1, reg1);
        }
        if (c == M68K) {
            setBitDefInternal(memoryContext, M68K, BK0, reg1);
            setBitDefInternal(memoryContext, M68K, BK1, reg1);
        }

        //deriveWramSetupGpgx
        if ((reg1 & MODE.getBitMask()) != 0) {
            memoryContext.wramSetup = (reg1 & RET.getBitMask()) != 0 ? W_1M_WR0_SUB : W_1M_WR0_MAIN;
        } else {
            memoryContext.wramSetup = gpgxDmna == 0 ? W_2M_MAIN : W_2M_SUB;
        }
        if (prev != memoryContext.wramSetup) {
            LogHelper.logInfo(LOG, "{} WRAM setup changed: {} -> {}", c, prev, memoryContext.wramSetup);
        }
    }


//    private void writeRegLowByteGpgx(int reg1){
//        setSharedBitBothCpu(memoryContext, RET, reg1);
//        setSharedBitBothCpu(memoryContext, DMNA, reg1);
//        setSharedBitBothCpu(memoryContext, MODE, reg1);
//        setBitDefInternal(memoryContext, SUB_M68K, PM0, reg1);
//        setBitDefInternal(memoryContext, SUB_M68K, PM1, reg1);
//        setBitDefInternal(memoryContext, M68K, BK0, reg1);
//        setBitDefInternal(memoryContext, M68K, BK1, reg1);
//    }
//
//    private void deriveWramSetupGpgx(int reg1){
//        if((reg1 & MODE.getBitMask()) != 0){
//            memoryContext.wramSetup = (reg1 & RET.getBitMask()) != 0 ? W_1M_WR0_SUB : W_1M_WR0_MAIN;
//        } else {
//            memoryContext.wramSetup = gpgxDmna == 0 ? W_2M_MAIN : W_2M_SUB;
//        }
//    }
//
//    private void finishGpgx(CpuDeviceAccess c, WramSetup prev){
//        if(prev != memoryContext.wramSetup){
//            LogHelper.logInfo(LOG, "{} WRAM setup changed: {} -> {}", c, prev, memoryContext.wramSetup);
//        }
//    }

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

//    public void writeCellMapped(int addr, int data, Size size) {
//        assert memoryContext.wramSetup.mode == _1M;
//        switch (size) {
//            case BYTE -> {
//                int val = readCellMapped(addr, Size.WORD);
//                ArrayEndianUtil.setByteInWordBE(val, data, addr & 1);
//                writeCell(addr & ~1, val);
//            }
//            case WORD -> writeCell(addr, data);
//            case LONG -> {
//                writeCell(addr, data >> 16);
//                writeCell(addr + 2, data);
//            }
//        }
//    }

    public static int cellImageTranslateWordIndex(int cellOffset) {
        int wordOfCell = cellOffset & 2;
        int res;
        if (cellOffset < 0x10_000) {
            //64x32 cell view
            int lineOfColumn = cellOffset & 0x3FC;
            int column = cellOffset & 0xFC00;
            res = (lineOfColumn << 6) | (column << 8) | wordOfCell;
        } else if (cellOffset < 0x18_000) {
            //64x16 cell view
            int lineOfColumn = cellOffset & 0x1FC;
            int column = cellOffset & 0x7e00;
            res = 0x10_000 | (lineOfColumn << 6) | (column << 7) | wordOfCell;
        } else if (cellOffset < 0x1C_000) {
            //64x8 cell view
            int lineOfColumn = cellOffset & 0xFC;
            int column = cellOffset & 0x3f00;
            res = 0x18_000 | (lineOfColumn << 6) | (column << 6) | wordOfCell;
        } else {
            //64x4 cell view
            int lineOfColumn = cellOffset & 0x7C;
            int column = cellOffset & 0x1f80;
            res = (cellOffset & 0x1e000) | (lineOfColumn << 6) | (column << 6) | wordOfCell;
        }
        return res;
    }

    private int readWordRamByWordIndexInterleaved(int wordIndex) {
        int bank = wordIndex & 1;
        int byteOffset = (wordIndex >> 1) << 1;
        return Util.readDataWord(wordRam01[bank], byteOffset & MCD_WORD_RAM_1M_MASK);
    }

    public int readWordCellImage(CpuDeviceAccess cpu, int cellOffset, Size size) {
        assert memoryContext.wramSetup.mode == _1M;
        int base = cellImageTranslateWordIndex(cellOffset & MCD_WORD_RAM_1M_MASK);
        return switch (size) {
            case WORD -> readWordRamByWordIndexInterleaved(base);
            case LONG -> readWordRamByWordIndexInterleaved(base << 16) |
                    readWordCellImage(cpu, cellOffset + 2, Size.WORD);
            case BYTE -> {
                int word = readWordRamByWordIndexInterleaved(base);
                yield (word >> ((~cellOffset & 1) << 3)) & 0xFF;
            }
        };
    }

    public int readWordRamMainCell(int address, Size size) {
        if (memoryContext.wramSetup.mode != _1M) {
            return readWordRam(M68K, address, size);
        }
        return switch (size) {
            case WORD -> readWordRamCellWord(address);
            case LONG -> (readWordRamCellWord(address) << 16) | readWordRamCellWord(address + 2);
            case BYTE -> {
                int bank = getBank1M(memoryContext.wramSetup, M68K);
                int lin = McdWramCell.cellToLinear(address & MCD_WORD_RAM_1M_MASK, false);
                yield Util.readDataByte(wordRam01[bank], lin & MCD_WORD_RAM_1M_MASK);
            }
        };
    }

    public void writeWordRamMainCell(int address, int value, Size size) {
        if (memoryContext.wramSetup.mode != _1M) {
            writeWordRam(M68K, address, value, size);
            return;
        }
        switch (size) {
            case WORD -> writeWordRamCellWord(address, value);
            case LONG -> {
                writeWordRamCellWord(address, value >> 16);
                writeWordRamCellWord(address + 2, value);
            }
            case BYTE -> {
                int bank = getBank1M(memoryContext.wramSetup, M68K);
                int lin = McdWramCell.cellToLinear(address & MCD_WORD_RAM_1M_MASK, false);
                Util.writeDataByte(wordRam01[bank], lin & MCD_WORD_RAM_1M_MASK, value);
            }
        }
    }

    private void writeCell(int a, int value) {
        int assignedBank = memoryContext.wramSetup.cpu == M68K ? 0 : 1;
        int otherBank = ~assignedBank & 1;
        writeWordRamBank(otherBank, linearCellMap[a & MCD_WORD_RAM_1M_MASK], value);
    }

    private int readWordRamCellWord(int address) {
        int bank = getBank1M(memoryContext.wramSetup, M68K);
        int lin = McdWramCell.cellToLinear(address & MCD_WORD_RAM_1M_MASK, true);
        return Util.readDataWord(wordRam01[bank], lin & MCD_WORD_RAM_1M_MASK & ~1);
    }

    private void writeWordRamCellWord(int address, int value) {
        int bank = getBank1M(memoryContext.wramSetup, M68K);
        int lin = McdWramCell.cellToLinear(address & MCD_WORD_RAM_1M_MASK, true);
        Util.writeDataWord(wordRam01[bank], lin & MCD_WORD_RAM_1M_MASK & ~1, value);
    }

    public void resetState() {
        mainHasWord2m = true;
        bankToggle = false;
        mainSwapRequest = false;
        gpgxDmna = 0;
        memoryContext.wramSetup = W_2M_MAIN;
        writeBufferRaw(memoryContext.getGateSysRegs(M68K), MCD_MEM_MODE.addr + 1, 1, Size.BYTE);
        writeBufferRaw(memoryContext.getGateSysRegs(SUB_M68K), MCD_MEM_MODE.addr + 1, 1, Size.BYTE);
    }
}
