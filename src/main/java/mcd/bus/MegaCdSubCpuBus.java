package mcd.bus;

import mcd.asic.Asic;
import mcd.asic.AsicModel.AsicEvent;
import mcd.cdc.Cdc;
import mcd.cdd.Cdd;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.Device;
import omegadrive.bus.md.BusArbiter;
import omegadrive.bus.md.GenesisBus;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport.VdpEvent;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_LEVEL2;
import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_TIMER;
import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.pcm.McdPcm.MCD_PCM_DIVIDER;
import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.Util.th;

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

    private Asic asic;
    private Cdd cdd;
    private Cdc cdc;

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
        writeBufferRaw(sysGateRegs, MCD_RESET.addr, 1, Size.WORD); //not reset
        writeBufferRaw(sysGateRegs, MCD_MEM_MODE.addr, 1, Size.WORD);
        writeBufferRaw(commonGateRegs, MCD_CDD_CONTROL.addr, 0x100, Size.WORD);
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
        if (device instanceof Asic asic) {
            this.asic = asic;
        }
        return this;
    }

    @Override
    public int read(int address, Size size) {
        address &= MD_PC_MASK;
        int res = size.getMask();
        if (address >= START_MCD_SUB_WORD_RAM_2M && address < END_MCD_SUB_WORD_RAM_2M) {
//            assert memCtx.wramSetup.mode == WordRamMode._2M; //TODO mcd-verificator
            res = memCtx.readWordRam(cpuType, address, size);
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
            res = readBuffer(memCtx.backupRam, address & MCD_SUB_BRAM_MASK, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Read Reserved: {} {}", th(address), size);
        } else {
            LogHelper.logWarnOnce(LOG, "S Unexpected read access: {} {}", th(address), size);
        }
        return res & size.getMask();
    }

    @Override
    public void write(int address, int data, Size size) {
        address &= MD_PC_MASK;
        if (address >= START_MCD_SUB_WORD_RAM_2M && address < END_MCD_SUB_WORD_RAM_2M) {
//            assert memCtx.wramSetup.mode == WordRamMode._2M; //TODO mcd-verificator
            memCtx.writeWordRam(cpuType, address, data, size);
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
            writeBufferRaw(memCtx.backupRam, address & MCD_SUB_BRAM_MASK, data, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Write Reserved: {} {} {}", address, data, size);
        } else {
            LogHelper.logWarnOnce(LOG, "S Unexpected write access: {} {}", th(address), size);
        }
    }

    private int handleMegaCdExpRead(int address, Size size) {
        RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpuType, address);
        logAccess(regSpec, cpuType, address, 0, size, true);
        ByteBuffer regs = memCtx.getRegBuffer(cpuType, regSpec);
        int res = readBuffer(regs, address & MDC_SUB_GATE_REGS_MASK, size);
        if (regSpec == MegaCdDict.RegSpecMcd.INVALID) {
            LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
            return 0;
        }
        if (regSpec == MCD_CDC_REG_DATA) {
            res = cdc.read(regSpec, address, size);
        }
        if (regSpec.deviceType == McdRegType.CDD || regSpec.deviceType == McdRegType.CDC) {
//            forceLog(regSpec, cpuType, address, res, size, true);
        }
        checkRegLongAccess(regSpec, size);
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
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
                address |= 1;
                LogHelper.logInfo(LOG, "S write COMM_FLAG: {} {}", th(data), size);
                writeBufferRaw(regs, address, data, Size.BYTE);
                writeBufferRaw(memCtx.getRegBuffer(M68K, regSpec), address, data, Size.BYTE);
            }
            case MCD_INT_MASK -> {
                LogHelper.logInfo(LOG, "S Write Interrupt mask control: {}, {}, {}", th(address), th(data), size);
                boolean changed = writeBufferRaw(regs, address, data, size);
                if (changed) {
                    int reg = Util.readBufferByte(commonGateRegs, MCD_INT_MASK.addr + 1);
                    McdSubInterruptHandler.printEnabledInterrupts(reg);
                    //Note, according to hw manual
                    setBit(memCtx.getGateSysRegs(M68K), MCD_RESET.addr, 7, (reg >> 2) & 1, Size.BYTE);
                }
            }
            case MCD_TIMER_INT3 -> {
                //byte writes to MSB go to LSB
                int addr = size == Size.BYTE ? address | 1 : address;
                timerContext.rate = data & 0xFF;
                timerContext.counter = timerContext.rate;
                writeBufferRaw(regs, addr, data, size);
            }
            default -> {
                logHelper.logWarningOnce(LOG,
                        "S write unhandled MEGA_CD_EXP reg: {} ({}), {} {}", th(address), regSpec, th(data), size);
                writeBufferRaw(regs, address, data, size);
//                assert false;
            }
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
        }
    }

    private void handleReg2Write(int address, int data, Size size) {
        int resWord = memCtx.handleRegWrite(cpuType, MCD_MEM_MODE, address, data, size);
        WramSetup ws = memCtx.update(cpuType, resWord);
        if (ws == WramSetup.W_2M_MAIN) { //set DMNA=0
            setBitVal(sysGateRegs, MCD_MEM_MODE.addr + 1, 1, 0, Size.BYTE);
            memCtx.setSharedBit(cpuType, 0, SharedBit.DMNA);
        }
        asic.setStampPriorityMode((resWord >> 3) & 3);
    }

    private void handleCommRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        if (address >= START_MCD_SUB_GA_COMM_W && address < END_MCD_SUB_GA_COMM_W) { //MAIN COMM
            logHelper.logWarningOnce(LOG, "S Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBufferRaw(commonGateRegs, address & MDC_SUB_GATE_REGS_MASK, data, size);
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
        int regVal = readBuffer(sysGateRegs, 0, Size.WORD) | 1;
        writeBufferRaw(sysGateRegs, 0, regVal, Size.WORD);
        LOG.info("S subCpu reset done");
    }

    //75hz at NTSC 60 fps
    //hBlankOff = 262 ( 225 displayOn, 37 display off)
    //hblankOff rate: 15_720hz
    //CDD int should fire every 210 hblankoff (or hblankOn)

    //md-asic-demo requires > 180
    static final int asicCalcDuration = 180;
    private int asicCounter = asicCalcDuration;


    @Override
    public void onVdpEvent(VdpEvent event, Object value) {
        super.onVdpEvent(event, value);
        //vBlankOn fire LEV2
        if (event == VdpEvent.V_BLANK_CHANGE && (boolean) value) {
            int ifl2 = readBuffer(memCtx.getGateSysRegs(M68K), MCD_RESET.addr, Size.BYTE) & 1;
            if (ifl2 > 0) {
                interruptHandler.raiseInterrupt(INT_LEVEL2);
            }
        } else if (event == VdpEvent.H_BLANK_CHANGE) {
            boolean val = (boolean) value;
            if (!val) {
                if (--asicCounter == 0) {
                    asic.asicEvent(AsicEvent.AS_STOP);
                    asicCounter = asicCalcDuration;
                }
            }
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

    @Override
    public void step(int cpuCycles) {
        counter32p5Khz.cycleAccumulator -= cpuCycles;
        if (counter32p5Khz.cycleAccumulator <= 0) {
            counter32p5Khz.cycleAcc75--;
            counter32p5Khz.ticks32p5++;
            pcm.step(0);
            cdc.step(0);
            timerStep();
            counter32p5Khz.cycleAccumulator += Counter32p5Khz.limit;
            if (counter32p5Khz.cycleAcc75 <= 0) { //75hz
                cdd.step(0);
                counter32p5Khz.ticks75++;
                counter32p5Khz.cycleAcc75 += PCM_CLOCK_DIVIDER_TO_75HZ;
            }
        }
    }

    public void onNewFrame() {
        super.onNewFrame();
        counter32p5Khz.frames++;
        if ((counter32p5Khz.frames + 1) % 60 == 0) {
            boolean rangeOk = Math.abs(75 - counter32p5Khz.ticks75) < 2 &&
                    Math.abs(McdPcm.PCM_SAMPLE_RATE_HZ - counter32p5Khz.ticks32p5) < 500.0;
            if (!rangeOk) {
                LOG.warn("SubCpu timing off!!!, 32.5Khz: {}, 75hz:{}", counter32p5Khz.ticks32p5, counter32p5Khz.ticks75);
            }
            counter32p5Khz.ticks32p5 = counter32p5Khz.ticks75 = 0;
        }
    }

    public void logAccess(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        logHelper.logWarningOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }

    public static void logAccessReg(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        LogHelper.logWarnOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }
}
