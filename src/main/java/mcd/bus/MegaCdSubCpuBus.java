package mcd.bus;

import mcd.asic.AsicModel;
import mcd.cdc.Cdc;
import mcd.cdd.Cdd;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.Device;
import omegadrive.bus.md.BusArbiter;
import omegadrive.bus.md.GenesisBus;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.util.ArrayEndianUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport.VdpEvent;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static mcd.MegaCd.MCD_SUB_68K_CLOCK_MHZ;
import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_LEVEL2;
import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_TIMER;
import static mcd.dict.MegaCdDict.BitRegDef.IEN2;
import static mcd.dict.MegaCdDict.BitRegDef.IFL2;
import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.pcm.McdPcm.MCD_PCM_DIVIDER;
import static mcd.util.BuramHelper.readBackupRam;
import static mcd.util.BuramHelper.writeBackupRam;
import static mcd.util.McdRegBitUtil.setBitDefInternal;
import static mcd.util.McdRegBitUtil.setSharedBit;
import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MegaCdSubCpuBus extends GenesisBus implements StepDevice {

    private static final Logger LOG = LogHelper.getLogger(MegaCdSubCpuBus.class.getSimpleName());

    private class TimerContext {
        public int counter, rate;
    }

    //32552/434 = 75 Hz
    public static final int PCM_CLOCK_DIVIDER_TO_75HZ = 434;

    private class Counter32p5Khz {
        //mcd_verificator requires such precise value
        public static final int limit = MCD_PCM_DIVIDER;
        public int cycleAccumulator = limit;
        public int cycleAcc75 = PCM_CLOCK_DIVIDER_TO_75HZ;
        //debug
        private int ticks32p5, ticks75, frames;
    }

    private ByteBuffer subCpuRam, sysGateRegs, commonGateRegs;
    private LogHelper logHelper = new LogHelper();
    private MegaCdMemoryContext memCtx;
    private CpuDeviceAccess cpuType;

    @Deprecated
    private McdSubInterruptHandler interruptHandler;
    private McdPcm pcm;

    private AsicModel.AsicOp asic;
    private Cdd cdd;
    private Cdc cdc;

    private MC68000Wrapper subCpu;
    private TimerContext timerContext;
    private Counter32p5Khz counter32p5Khz;

    public MegaCdSubCpuBus(MegaCdMemoryContext ctx) {
        cpuType = CpuDeviceAccess.SUB_M68K;
        subCpuRam = ByteBuffer.wrap(ctx.prgRam);
        sysGateRegs = ctx.getGateSysRegs(cpuType);
        commonGateRegs = ctx.commonGateRegsBuf;
        memCtx = ctx;
        timerContext = new TimerContext();
        counter32p5Khz = new Counter32p5Khz();
        //NOTE: starts at zero and becomes 1 after ~100ms

        writeSysRegWord(memCtx, cpuType, MCD_RESET, 1); //not reset
        writeSysRegWord(memCtx, cpuType, MCD_MEM_MODE, 1);
        writeCommonRegWord(memCtx, cpuType, MCD_CDD_CONTROL, 0x100);
        attachDevice(BusArbiter.NO_OP);
    }

    @Override
    public GenesisBusProvider attachDevice(Device device) {
        super.attachDevice(device);
        if (device instanceof McdSubInterruptHandler ih) {
            this.interruptHandler = ih;
        }
        if (device instanceof Cdc cdc) {
            this.cdc = cdc;
        }
        if (device instanceof Cdd cdd) {
            this.cdd = cdd;
        }
        if (device instanceof McdPcm pcm) {
            this.pcm = pcm;
        }
        if (device instanceof AsicModel.AsicOp asic) {
            this.asic = asic;
        }
        if (device instanceof MC68000Wrapper c) {
            assert subCpu == null;
            this.subCpu = c;
        }
        return this;
    }

    @Override
    public int read(int address, Size size) {
        assert assertCheckBusOp68k(address, size);
        address &= MD_PC_MASK;
        int res = size.getMask();
        if (address >= START_MCD_SUB_WORD_RAM_2M && address < END_MCD_SUB_WORD_RAM_2M) {
            if (memCtx.wramSetup.mode == WordRamMode._2M) {
                res = memCtx.readWordRam(cpuType, address, size);
            } else {
                //dot mapped window
                res = readDotMapped(address, size);
            }
        } else if (address >= START_MCD_SUB_WORD_RAM_1M && address < END_MCD_SUB_WORD_RAM_1M) {
            assert memCtx.wramSetup.mode == WordRamMode._1M;
            res = memCtx.readWordRam(cpuType, address, size);
        } else if (address >= START_MCD_SUB_PRG_RAM && address < END_MCD_SUB_PRG_RAM) {
            res = readBuffer(subCpuRam, address & MCD_PRG_RAM_MASK, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS && address < END_MCD_SUB_GATE_ARRAY_REGS) {
            res = handleMegaCdExpRead(address, size);
        } else if (address >= START_MCD_SUB_PCM_AREA && address < START_MCD_SUB_GATE_ARRAY_REGS) {
            res = pcm.read(address, size);
        } else if (address >= START_MCD_SUB_BRAM_AREA && address < END_MCD_SUB_BRAM_AREA) {
            res = readBackupRam(memCtx.backupRam, address, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Read Reserved: {} {}", th(address), size);
        } else {
            LogHelper.logWarnOnce(LOG, "S Unexpected read access: {} {}", th(address), size);
        }
        return res & size.getMask();
    }


    final BusWriteRunnable wramRunnable = new BusWriteRunnable() {
        @Override
        public void run() {
            memCtx.writeWordRam(cpuType, address, data, size);
        }
    };

    AtomicReference<Runnable> wramRunLater = new AtomicReference<>();

    @Override
    public void write(int address, int data, Size size) {
        assert assertCheckBusOp68k(address, size);
        address &= MD_PC_MASK;
        if (address >= START_MCD_SUB_WORD_RAM_2M && address < END_MCD_SUB_WORD_RAM_2M) {
            if (memCtx.wramSetup.mode == WordRamMode._2M) {
                if (memCtx.wramSetup.cpu == SUB_M68K) {
                    memCtx.writeWordRam(cpuType, address, data, size);
                } else {
                    wramRunnable.address = address;
                    wramRunnable.data = data;
                    wramRunnable.size = size;
//                    boolean res = wramRunLater.compareAndSet(null, wramRunnable);
//                    assert res;
//                    MC68000Wrapper.subCpuBusHalt = true;
//                    LOG.info("{} blocked due to write to WRAM {}, {}", cpuType, memCtx.wramSetup, wramRunnable);
                }
            } else {
                //dot mapped window
                writeDotMapped(address, data, size);
            }
        } else if (address >= START_MCD_SUB_WORD_RAM_1M && address < END_MCD_SUB_WORD_RAM_1M) {
            assert memCtx.wramSetup.mode == WordRamMode._1M;
            memCtx.writeWordRam(cpuType, address, data, size);
        } else if (address >= START_MCD_SUB_PRG_RAM && address < END_MCD_SUB_PRG_RAM) {
            memCtx.writeProgRam(address & MCD_PRG_RAM_MASK, data, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS && address <= END_MCD_SUB_GATE_ARRAY_REGS) {
            handleMegaCdExpWrite(address, data, size);
        } else if (address >= START_MCD_SUB_PCM_AREA && address < START_MCD_SUB_GATE_ARRAY_REGS) {
            pcm.write(address, data, size);
        } else if (address >= START_MCD_SUB_BRAM_AREA && address < END_MCD_SUB_BRAM_AREA) {
            writeBackupRam(memCtx.backupRam, address, data, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Write Reserved: {} {} {}", address, data, size);
        } else {
            LogHelper.logWarnOnce(LOG, "S Unexpected write access: {} {}", th(address), size);
        }
    }

    private int readDotMapped(int address, Size size) {
        return switch (size) {
            case BYTE -> readDotMappedByte(address);
            case WORD -> (readDotMappedByte(address) << 8) | readDotMappedByte(address + 1);
            //batman returns (E), using clr.l
            case LONG -> (readDotMappedByte(address) << 24) | (readDotMappedByte(address + 1) << 16) |
                    (readDotMappedByte(address + 2) << 8) | (readDotMappedByte(address + 3) << 0);
        };
    }

    private void writeDotMapped(int address, int data, Size size) {
        switch (size) {
            case BYTE -> writeDotMappedByte(address, data);
            case WORD -> {
                writeDotMappedByte(address, data >> 8);
                writeDotMappedByte(address + 1, data);
            }
            case LONG -> {
                writeDotMappedByte(address, data >> 24);
                writeDotMappedByte(address + 1, data >> 16);
                writeDotMappedByte(address + 2, data >> 8);
                writeDotMappedByte(address + 3, data >> 0);
            }
        }
    }

    private int readDotMappedByte(int address) {
        byte[] wramBank = memCtx.wordRam01[memCtx.wramSetup.cpu == cpuType ? 0 : 1];
        int addr = (address & MCD_WORD_RAM_1M_MASK) >> 1;
        int shift = (~address & 1) << 2;
        return (wramBank[addr] >> shift) & 0xF;
    }

    private void writeDotMappedByte(int address, int data) {
        byte[] wramBank = memCtx.wordRam01[memCtx.wramSetup.cpu == cpuType ? 0 : 1];
        int addr = (address & MCD_WORD_RAM_1M_MASK) >> 1;
        boolean doWrite = switch (asic.getStampPriorityMode()) {
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

    private int handleMegaCdExpRead(int address, Size size) {
        return switch (size) {
            case WORD, BYTE -> handleMegaCdExpReadInternal(address, size);
            case LONG -> ((handleMegaCdExpReadInternal(address, Size.WORD) & 0xFFFF) << 16) |
                    (handleMegaCdExpReadInternal(address + 2, Size.WORD) & 0xFFFF);
        };
    }

    private int handleMegaCdExpReadInternal(int address, Size size) {
        RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpuType, address);
        logAccess(regSpec, cpuType, address, 0, size, true);
        if (regSpec == MegaCdDict.RegSpecMcd.INVALID) {
            LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
            return 0;
        }
        int res = switch (regSpec.deviceType) {
            case CDD -> cdd.read(regSpec, address, size);
            case CDC -> cdc.read(regSpec, address, size);
            case ASIC -> asic.read(regSpec, address, size);
            default -> readBuffer(memCtx.getRegBuffer(cpuType, regSpec),
                    address & MDC_SUB_GATE_REGS_MASK, size);
        };
        checkRegLongAccess(regSpec, size);
        if (regSpec == MCD_COMM_FLAGS) {
//            LogHelper.logInfo(LOG, "S read COMM_FLAG {}: {} {}", th(address), th(res), size);
        }
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        switch (size) {
            case WORD, BYTE -> handleMegaCdExpWriteInternal(address, data, size);
            case LONG -> {
                handleMegaCdExpWrite(address, data >> 16, Size.WORD);
                handleMegaCdExpWrite(address + 2, data, Size.WORD);
            }
        }
    }

    private void handleMegaCdExpWriteInternal(int address, int data, Size size) {
        MegaCdDict.RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpuType, address);
        MegaCdDict.logAccess(regSpec, cpuType, address, data, size, false);
        if (regSpec == MegaCdDict.RegSpecMcd.INVALID) {
            LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
            return;
        }
        if (regSpec.deviceType == McdRegType.CDD || regSpec.deviceType == McdRegType.CDC) {
//            forceLog(regSpec, cpuType, address, data, size, false);
        }
        checkRegLongAccess(regSpec, size);
        switch (regSpec.deviceType) {
            case SYS -> handleSysRegWrite(regSpec, address & MDC_SUB_GATE_REGS_MASK, data, size);
            case COMM -> handleCommRegWrite(regSpec, address, data, size);
            case CDD -> cdd.write(regSpec, address, data, size);
            case CDC -> cdc.write(regSpec, address, data, size);
            case ASIC -> asic.write(regSpec, address, data, size);
            default -> LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
        }
    }

    private static void forceLog(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        LogHelper.doLog = true;
        LOG.info("{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), ": " + th(value));
        LogHelper.doLog = false;
    }

    private void handleSysRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        assert size != Size.LONG;
        ByteBuffer regs = memCtx.getRegBuffer(cpuType, regSpec);
        switch (regSpec) {
            case MCD_RESET -> handleReg0Write(address, data, size);
            case MCD_MEM_MODE -> handleReg2Write(address, data, size);
            case MCD_COMM_FLAGS -> {
                //sub can only write to LSB (odd byte), WORD write becomes a BYTE write
//                assert size == Size.BYTE ? (address & 1) == 1 : true;
                address |= 1;
                LogHelper.logInfo(LOG, "S write COMM_FLAG {}: {} {}", th(address), th(data), size);
                writeReg(memCtx, cpuType, regSpec, address, data, Size.BYTE);
                writeReg(memCtx, M68K, regSpec, address, data, Size.BYTE);
            }
            case MCD_INT_MASK -> {
                LogHelper.logInfo(LOG, "S Write Interrupt mask control: {}, {}, {}", th(address), th(data), size);
                boolean changed = writeBufferRaw(regs, address, data, size);
                if (changed) {
                    int reg = Util.readBufferByte(commonGateRegs, MCD_INT_MASK.addr + 1);
                    McdSubInterruptHandler.printEnabledInterrupts(reg);
                    //Note, according to hw manual, mcd-ver
                    //IEN2 shift from bit#2 to bit#7
                    int val = IEN2.getBitMask() * ((reg >> 2) & 1);
                    setBitDefInternal(memCtx, M68K, IEN2, val);
                    //disable IEN2 -> resets IFL2
                    if (val == 0) {
                        setBitDefInternal(memCtx, M68K, IFL2, 0);
                    }
                }
            }
            case MCD_TIMER_INT3 -> {
                //byte writes to MSB go to LSB
                int addr = size == Size.BYTE ? address | 1 : address;
                timerContext.rate = data & 0xFF;
                timerContext.counter = timerContext.rate;
                writeReg(memCtx, cpuType, regSpec, addr, data, size);
            }
            case MCD_FONT_COLOR -> { //4c
                //byte access always goes to the odd byte
                address = size == Size.BYTE ? address | 1 : address;
                writeReg(memCtx, cpuType, regSpec, address, data & 0xFF, size);
                recalcFontData(regs);
            }
            case MCD_FONT_BIT -> { //4e
                writeReg(memCtx, cpuType, regSpec, address, data, size);
                recalcFontData(regs);
            }
            default -> {
                logHelper.logWarningOnce(LOG,
                        "S write unhandled MEGA_CD_EXP reg: {} ({}), {} {}", th(address), regSpec, th(data), size);
                writeReg(memCtx, cpuType, regSpec, address, data, size);
//                assert false;
            }
        }
    }

    private void recalcFontData(ByteBuffer regs) {
        int colorVal = readBuffer(regs, MCD_FONT_COLOR.addr, Size.WORD);
        short fontData = (short) readBuffer(regs, MCD_FONT_BIT.addr, Size.WORD);
        int background = colorVal & 0xF;
        int foreground = (colorVal >> 4) & 0xF;
        int limit = 8; //4 regs, 2 bytes wide
        for (int i = 0; i < limit; i += 2) {
            int offset = 12 - (i << 1);
            int n1 = getBitFromWord(fontData, offset | 0) > 0 ? foreground : background;
            int n2 = getBitFromWord(fontData, offset | 1) > 0 ? foreground : background;
            int n3 = getBitFromWord(fontData, offset | 2) > 0 ? foreground : background;
            int n4 = getBitFromWord(fontData, offset | 3) > 0 ? foreground : background;
            writeBufferRaw(regs, MCD_FONT_DATA0.addr + i, (n4 << 12) | (n3 << 8) | (n2 << 4) | n1, Size.WORD);
        }
    }

    private void handleReg0Write(int address, int data, Size size) {
        int res = memCtx.handleRegWrite(cpuType, MCD_RESET, address, data, size);
        int sreset = res & 1;
        if ((address & 1) == 0 && size == Size.BYTE) {
            return;
        }
        LOG.info("S SubCpu reset: {}", (sreset == 0 ? "Reset" : "Ignore"));
        if (sreset == 0) {
            //TODO reset CD drive, part of cddinit, does it take 100ms??
            LOG.info("S subCpu peripheral reset started");
            setBit(sysGateRegs, MCD_RESET.addr + 1, 0, 1, Size.BYTE);//cd drive done resetting
            softReset();
        }
    }

    private void handleReg2Write(int address, int data, Size size) {
        int resWord = memCtx.handleRegWrite(cpuType, MCD_MEM_MODE, address, data, size);
        WramSetup prev = memCtx.wramSetup;
        WramSetup ws = memCtx.update(cpuType, resWord);
        handleWramSetupChange(prev, ws);
        if (ws == WramSetup.W_2M_MAIN) { //set DMNA=0
            setBitVal(sysGateRegs, MCD_MEM_MODE.addr + 1, 1, 0, Size.BYTE);
            setSharedBit(memCtx, cpuType, 0, SharedBitDef.DMNA);
        }
        asic.setStampPriorityMode((resWord >> 3) & 3);
    }

    public void handleWramSetupChange(WramSetup prev, WramSetup ws) {
        if (prev != ws) {
            if (ws.cpu == SUB_M68K) {
                Runnable r = wramRunLater.getAndSet(null);
                if (r != null) {
                    r.run();
                    MC68000Wrapper.subCpuBusHalt = false;
                    LOG.info("{} released as WRAM set to {}, cpu running: {}, busWrite: {}",
                            cpuType, memCtx.wramSetup, !subCpu.isStopped(), r);
                }
            }
        }
    }

    private void handleCommRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        if (address >= START_MCD_SUB_GA_COMM_W && address < END_MCD_SUB_GA_COMM_W) { //MAIN COMM
            LogHelper.logInfo(LOG, "S Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeReg(memCtx, cpuType, regSpec, address & MDC_SUB_GATE_REGS_MASK, data, size);
            return;
        }
        if (address >= START_MCD_SUB_GA_COMM_R && address < START_MCD_SUB_GA_COMM_W) { //SUB COMM READ ONLY
            LOG.error("S illegal write read-only MEGA_CD_COMM reg: {}", th(address));
            return;
        }
    }

    @Override
    public void resetFrom68k() {
        //do nothing??
    }

    public void resetDone() {
        //TODO when RES0 goes to 1, does SRES (main side) follow??
        setBit(sysGateRegs, MCD_RESET.addr + 1, 0, 1, Size.BYTE);
        setBit(memCtx.getRegBuffer(M68K, MCD_RESET), MCD_RESET.addr + 1, 0, 1, Size.BYTE);
        LOG.info("S subCpu reset done");
    }

    @Override
    public void onVdpEvent(VdpEvent event, Object value) {
        super.onVdpEvent(event, value);
        //vBlankOn fire LEV2
        if (event == VdpEvent.V_BLANK_CHANGE && (boolean) value) {
            interruptHandler.raiseInterrupt(INT_LEVEL2);
        }
    }

    private void timerStep() {
        if (timerContext.rate > 0 && --timerContext.counter == 0) {
            timerContext.counter = timerContext.rate;
            interruptHandler.raiseInterrupt(INT_TIMER);
        }
    }

    public McdSubInterruptHandler getInterruptHandler() {
        return interruptHandler;
    }

    public static final double counterCddaLimit = MCD_SUB_68K_CLOCK_MHZ / 44100.0;
    public static final double counterCdcDmaLimit = 6;
    private double counterCddaAcc = counterCddaLimit;
    private double counterCdcDma = counterCdcDmaLimit;

    @Override
    public void step(int cpuCycles) {
        counter32p5Khz.cycleAccumulator -= cpuCycles;
        if (counter32p5Khz.cycleAccumulator <= 0) {
            counter32p5Khz.cycleAcc75--;
            counter32p5Khz.ticks32p5++;
            pcm.step(0);
            cdc.step(0);
            timerStep();
            asic.step(0);
            counter32p5Khz.cycleAccumulator += Counter32p5Khz.limit;
            if (counter32p5Khz.cycleAcc75 <= 0) { //75hz
                cdd.step(0);
                counter32p5Khz.ticks75++;
                counter32p5Khz.cycleAcc75 += PCM_CLOCK_DIVIDER_TO_75HZ;
            }
        }
        counterCddaAcc -= cpuCycles;
        if (counterCddaAcc <= 0) {
            counterCddaAcc += counterCddaLimit;
            cdd.stepCdda();
        }

        counterCdcDma -= cpuCycles;
        while (counterCdcDma <= 0) {
            counterCdcDma += counterCdcDmaLimit;
            cdc.dma();
        }
    }

    int subCpuResetFrameCount = 0;

    public void onNewFrame() {
        super.onNewFrame();
        counter32p5Khz.frames++;
        if ((counter32p5Khz.frames + 1) % 60 == 0) {
            boolean rangeOk = Math.abs(75 - counter32p5Khz.ticks75) < 2 &&
                    Math.abs(McdPcm.PCM_SAMPLE_RATE_HZ - counter32p5Khz.ticks32p5) < 500.0;
            if (!rangeOk) {
                String str = "SubCpu timing off!!!, 32.5Khz: {}, 75hz:{}";
                //known issue for PAL, warn once
                LogHelper.logWarnOnce(LOG, str, counter32p5Khz.ticks32p5, counter32p5Khz.ticks75);
            }
            counter32p5Khz.ticks32p5 = counter32p5Khz.ticks75 = 0;
        }
        if (subCpuResetFrameCount > 0) {
            if (--subCpuResetFrameCount == 0) {
                releaseSubCpuReset();
            }
        }
        if (MegaCdMainCpuBus.subCpuReset && subCpuResetFrameCount == 0) {
            subCpuResetFrameCount = 6; //~100ms
        }
    }

    private void releaseSubCpuReset() {
        MegaCdMainCpuBus.subCpuReset = false;
        subCpu.reset();
        resetDone();
        //get SBRQ from main
        int bval = readBufferByte(memCtx.getGateSysRegs(M68K), MCD_RESET.addr + 1);
        int sbusreq = (bval >> 1) & 1;
        subCpu.setStop(sbusreq > 0);
    }

    public int getLedState() {
        return readBufferByte(memCtx.getGateSysRegs(SUB_M68K), MCD_RESET.addr) & 3;
    }

    public void softReset() {
        /* RESET register always return 1 */
        writeReg(memCtx, cpuType, MCD_RESET, MCD_RESET.addr + 1, 1, Size.BYTE);
        writeSysRegWord(memCtx, cpuType, MCD_CDC_MODE, 0);
        writeSysRegWord(memCtx, cpuType, MCD_STOPWATCH, 0);
        //reset all sub regs
        Arrays.fill(commonGateRegs.array(), MCD_TIMER_INT3.addr, commonGateRegs.capacity(), (byte) 0);
        /* SUB-CPU side default values */
        writeSysRegWord(memCtx, cpuType, MCD_CDC_HOST, 0xffff);
        writeSysRegWord(memCtx, cpuType, MCD_CDC_DMA_ADDRESS, 0xffff);

        writeCommonRegWord(memCtx, cpuType, MCD_CDD_CONTROL, 0x100);
        writeCommonRegWord(memCtx, cpuType, MCD_CDD_COMM4, 0x000f); //LSB status checksum
        writeCommonRegWord(memCtx, cpuType, MCD_CDD_COMM5, 0xffff);
        writeCommonRegWord(memCtx, cpuType, MCD_CDD_COMM6, 0xffff);
        writeCommonRegWord(memCtx, cpuType, MCD_CDD_COMM7, 0xffff);
        writeCommonRegWord(memCtx, cpuType, MCD_CDD_COMM8, 0xffff);
        writeCommonRegWord(memCtx, cpuType, MCD_CDD_COMM9, 0xffff);//LSB command checksum

        interruptHandler.reset();
        cdd.reset();
        cdc.reset();
        asic.reset();
        pcm.reset();
    }

    public void logAccess(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        logHelper.logWarningOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }

    public static void logAccessReg(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        LogHelper.logWarnOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }

    public static void logAccessReg(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, Size size, boolean read) {
        LogHelper.logWarnOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address));
    }
}
