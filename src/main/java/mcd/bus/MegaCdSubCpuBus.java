package mcd.bus;

import mcd.asic.Asic;
import mcd.cdc.Cdc;
import mcd.cdd.Cdd;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.Device;
import omegadrive.bus.md.BusArbiter;
import omegadrive.bus.md.GenesisBus;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport.VdpEvent;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdMemoryContext.*;
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

    //TODO this should be using MCD 68K @ 12.5 Mhz
    //MD M68K 7.67Mhz
    //pcm sample rate: 32.552 Khz
    //m68kCyclesPerSample = 235.62
    private class Counter35Khz {
        //mcd_verificator requires such precise value
        public static final double limit = 233.47 * 1.003;
        public double cycleAccumulator = limit;
        //debug
        private int ticks, cyclesFrame;
    }

    private ByteBuffer subCpuRam, sysGateRegs, commonGateRegs;
    private LogHelper logHelper = new LogHelper();
    private MegaCdMemoryContext memCtx;
    private CpuDeviceAccess cpu;

    private McdPcm pcm;

    private Asic asic;
    private Cdd cdd;
    private Cdc cdc;

    private TimerContext timerContext;
    private Counter35Khz counter35Khz;

    public MegaCdSubCpuBus(MegaCdMemoryContext ctx) {
        cpu = CpuDeviceAccess.SUB_M68K;
        subCpuRam = ByteBuffer.wrap(ctx.prgRam);
        sysGateRegs = ctx.getGateSysRegs(cpu);
        commonGateRegs = ByteBuffer.wrap(ctx.commonGateRegs);
        memCtx = ctx;
        timerContext = new TimerContext();
        counter35Khz = new Counter35Khz();
        //NOTE: starts at zero and becomes 1 after ~100ms
        writeBufferRaw(sysGateRegs, MCD_RESET.addr, 1, Size.WORD); //not reset
        writeBufferRaw(sysGateRegs, MCD_MEM_MODE.addr, 1, Size.WORD);
        writeBufferRaw(commonGateRegs, MCD_CDD_CONTROL.addr, 0x100, Size.WORD);
        attachDevice(BusArbiter.NO_OP);
    }

    @Override
    public GenesisBusProvider attachDevice(Device device) {
        super.attachDevice(device);
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
            res = memCtx.readWordRam(cpu, address, size);
        } else if (address >= START_MCD_SUB_WORD_RAM_1M && address < END_MCD_SUB_WORD_RAM_1M) {
            assert memCtx.wramSetup.mode == WordRamMode._1M;
            res = memCtx.readWordRam(cpu, address, size);
        } else if (address >= START_MCD_SUB_PRG_RAM && address < END_MCD_SUB_PRG_RAM) {
            res = readBuffer(subCpuRam, address & MCD_PRG_RAM_MASK, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS && address < END_MCD_SUB_GATE_ARRAY_REGS) {
            res = handleMegaCdExpRead(address, size);
        } else if (address >= START_MCD_SUB_PCM_AREA && address < START_MCD_SUB_GATE_ARRAY_REGS) {
            res = pcm.read(address, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Read Reserved: {} {}", th(address), size);
        } else {
            assert false : th(address);
        }
        return res & size.getMask();
    }

    @Override
    public void write(int address, int data, Size size) {
        address &= MD_PC_MASK;
        if (address >= START_MCD_SUB_WORD_RAM_2M && address < END_MCD_SUB_WORD_RAM_2M) {
//            assert memCtx.wramSetup.mode == WordRamMode._2M; //TODO mcd-verificator
            memCtx.writeWordRam(cpu, address, data, size);
        } else if (address >= START_MCD_SUB_WORD_RAM_1M && address < END_MCD_SUB_WORD_RAM_1M) {
            assert memCtx.wramSetup.mode == WordRamMode._1M;
            memCtx.writeWordRam(cpu, address, data, size);
        } else if (address >= START_MCD_SUB_PRG_RAM && address < END_MCD_SUB_PRG_RAM) {
            writeBufferRaw(subCpuRam, address & MCD_PRG_RAM_MASK, data, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS && address <= END_MCD_SUB_GATE_ARRAY_REGS) {
            handleMegaCdExpWrite(address, data, size);
        } else if (address >= START_MCD_SUB_PCM_AREA && address < START_MCD_SUB_GATE_ARRAY_REGS) {
            pcm.write(address, data, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Write Reserved: {} {} {}", address, data, size);
        } else {
            assert false;
        }
    }

    private int handleMegaCdExpRead(int address, Size size) {
        RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpu, address);
        logAccess(regSpec, cpu, address, 0, size, true);
        ByteBuffer regs = memCtx.getRegBuffer(cpu, regSpec);
        int res = readBuffer(regs, address & MCD_GATE_REGS_MASK, size);
        if (regSpec == MegaCdDict.RegSpecMcd.INVALID) {
            LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
            return 0;
        }
        if (regSpec == MCD_CDC_REG_DATA) {
            res = cdc.read(regSpec, address, size);
        }
        checkRegLongAccess(regSpec, size);
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        MegaCdDict.RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpu, address);
        MegaCdDict.logAccess(regSpec, cpu, address, data, size, false);
        if (regSpec == MegaCdDict.RegSpecMcd.INVALID) {
            LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
            return;
        }
        checkRegLongAccess(regSpec, size);
        switch (regSpec.deviceType) {
            case SYS -> handleSysRegWrite(regSpec, address, data, size);
            case COMM -> handleCommRegWrite(regSpec, address, data, size);
            case CDD -> handleCddRegWrite(regSpec, address, data, size);
            case CDC -> cdc.write(regSpec, address, data, size);
            case ASIC -> handleAsicRegWrite(regSpec, address, data, size);
            default -> LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
        }
    }

    private void handleSysRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        assert size != Size.LONG;
        ByteBuffer regs = memCtx.getRegBuffer(cpu, regSpec);
        switch (regSpec) {
            case MCD_RESET -> handleReg0Write(address, data, size);
            case MCD_MEM_MODE -> handleReg2Write(address, data, size);
            case MCD_COMM_FLAGS -> {
                //sub can only write to LSB
                assert size == Size.BYTE && (address & 1) == 1;
                LOG.info("S write COMM_FLAG: {} {}", th(data), size);
                writeBufferRaw(regs, address & MCD_GATE_REGS_MASK, data, size);
                writeBufferRaw(memCtx.getRegBuffer(M68K, regSpec), address & MCD_GATE_REGS_MASK, data, size);
            }
            case MCD_INT_MASK -> {
                LOG.info("S Write Interrupt mask control: {}, {}, {}", th(address), th(data), size);
                writeBufferRaw(regs, address & MCD_GATE_REGS_MASK, data, size);
            }
            case MCD_TIMER_INT3 -> {
                //byte writes to MSB go to LSB
                int addr = size == Size.BYTE ? (address & MCD_GATE_REGS_MASK) | 1 : address & MCD_GATE_REGS_MASK;
                timerContext.rate = data & 0xFF;
                timerContext.counter = timerContext.rate;
                writeBufferRaw(regs, addr, data, size);
            }
            default -> {
                logHelper.logWarningOnce(LOG,
                        "S write unhandled MEGA_CD_EXP reg: {} ({}), {} {}", th(address), regSpec, th(data), size);
                writeBufferRaw(regs, address & MCD_GATE_REGS_MASK, data, size);
//                assert false;
            }
        }
    }

    private void handleReg0Write(int address, int data, Size size) {
        int res = memCtx.handleRegWrite(cpu, MCD_RESET, address, data, size);
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
        int res = memCtx.handleRegWrite(cpu, MCD_MEM_MODE, address, data, size);
        WramSetup ws = memCtx.update(cpu, res);
        if (ws == WramSetup.W_2M_MAIN) { //set DMNA=0
            setBitVal(sysGateRegs, MCD_MEM_MODE.addr + 1, 1, 0, Size.BYTE);
            memCtx.setSharedBit(cpu, 0, SharedBit.DMNA);
        }
        asic.setStampPriorityMode((res >> 3) & 3);
    }

    private void handleCddRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        LOG.info("S Write CDD {} : {}, {}, {}", regSpec, th(address), th(data), size);
        cdd.write(regSpec, address, data, size);
        switch (regSpec) {
            case MCD_CDD_CONTROL -> {
                int v = readBuffer(commonGateRegs, regSpec.addr, Size.WORD);
                if (((v & 4) > 0) && checkInterruptEnabled(4)) { //HOCK set
                    m68kInterrupt(4); //trigger CDD interrupt
                    fireCddInt = true;
                }
            }
        }
    }

    private void handleAsicRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        asic.write(regSpec, address, data, size);
    }

    private void handleCommRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        if (address >= START_MCD_SUB_GA_COMM_W && address < END_MCD_SUB_GA_COMM_W) { //MAIN COMM
            LOG.info("S Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBufferRaw(commonGateRegs, address & SUB_CPU_REGS_MASK, data, size);
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


    private static boolean fireCddInt = false;
    private static boolean fireAsicInt = false;

    //75hz at NTSC 60 fps
    //hBlankOff = 262 ( 225 displayOn, 37 display off)
    //hblankOff rate: 15_720hz
    //CDD int should fire every 210 hblankoff (or hblankOn)
    static final int hblankPerCdd = 210;
    //md-asic-demo requires > 180
    static final int asicCalcDuration = 180;
    private int cddCounter = hblankPerCdd;
    private int asicCounter = asicCalcDuration;


    @Override
    public void onVdpEvent(VdpEvent event, Object value) {
        super.onVdpEvent(event, value);
        if (event == VdpEvent.V_BLANK_CHANGE) {
            boolean val = (boolean) value;
            if (val && checkInterruptEnabled(2)) {
                LOG.info("S VBlank On, int#2");
                m68kInterrupt(2);
            }
        } else if (event == VdpEvent.H_BLANK_CHANGE) {
            boolean val = (boolean) value;
            if (!val && fireAsicInt) {
                if (--asicCounter == 0) {
                    LOG.info("ASIC Int On, int#1, if enabled");
                    if (checkInterruptEnabled(1)) {
                        m68kInterrupt(1);
                    }
                    asicEvent(commonGateRegs, 0);
                    asicCounter = asicCalcDuration;
                }
            }
            if (val && fireCddInt) {
                if (--cddCounter == 0) {
                    if (cdd.isCommandPending()) {
                        LOG.info("CDD Int On, int#4");
                        m68kInterrupt(4);
                    }
                    cddCounter = hblankPerCdd;
                }
            }
        }
    }

    public static void asicEvent(ByteBuffer commonGateRegs, int event) {
        fireAsicInt = event == 1; //0 end, 1 start
        setBit(commonGateRegs, MCD_IMG_STAMP_SIZE.addr, 15, event, Size.WORD);
    }

    public boolean checkInterruptEnabled(int m68kLevel) {
        int reg = Util.readBufferByte(commonGateRegs, 0x33);
        return checkInterruptEnabled(reg, m68kLevel);
    }

    private static boolean checkInterruptEnabled(int reg, int m68kLevel) {
        return (reg & (1 << (m68kLevel))) > 0;
    }

    private void m68kInterrupt(int num) {
        getSubCpu().raiseInterrupt(num);
    }

    private void timerStep() {
        if (timerContext.rate > 0 && --timerContext.counter == 0) {
            timerContext.counter = timerContext.rate;
            if (checkInterruptEnabled(3)) {
                m68kInterrupt(3);
                LOG.info("Timer Int On, int#3");
            }
        }
    }

    public MC68000Wrapper getSubCpu() {
        return (MC68000Wrapper) getBusDeviceIfAny(M68kProvider.class).get();
    }

    @Override
    public void step(int cycles) {
        counter35Khz.cycleAccumulator -= cycles;
        counter35Khz.cyclesFrame += cycles;
        if (counter35Khz.cycleAccumulator < 0) {
            counter35Khz.ticks++;
            pcm.step(0);
            cdc.step(0);
            cdd.step(0);
            timerStep();
            counter35Khz.cycleAccumulator += Counter35Khz.limit;
        }
    }

    public void onNewFrame() {
        super.onNewFrame();
        counter35Khz.cyclesFrame = counter35Khz.ticks = 0;
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
