package mcd.bus;

import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.bus.md.BusArbiter;
import omegadrive.bus.md.GenesisBus;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport;
import omegadrive.vdp.model.BaseVdpProvider;
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
public class MegaCdSubCpuBus extends GenesisBus {

    private static final Logger LOG = LogHelper.getLogger(MegaCdSubCpuBus.class.getSimpleName());
    private ByteBuffer subCpuRam, sysGateRegs, commonGateRegs;
    private LogHelper logHelper = new LogHelper();
    private MegaCdMemoryContext memCtx;
    private CpuDeviceAccess cpu;

    private McdPcm pcm;

    public MegaCdSubCpuBus(MegaCdMemoryContext ctx) {
        cpu = CpuDeviceAccess.SUB_M68K;
        subCpuRam = ByteBuffer.wrap(ctx.prgRam);
        sysGateRegs = ctx.getGateSysRegs(cpu);
        commonGateRegs = ByteBuffer.wrap(ctx.commonGateRegs);
        memCtx = ctx;
        //NOTE: starts at zero and becomes 1 after ~100ms
        writeBufferRaw(sysGateRegs, MCD_RESET.addr, 1, Size.WORD); //not reset
        writeBufferRaw(sysGateRegs, MCD_MEM_MODE.addr, 1, Size.WORD);
        writeBufferRaw(commonGateRegs, MCD_CDD_CONTROL.addr, 0x100, Size.WORD);
        attachDevice(BusArbiter.NO_OP);
    }

    public void setPcm(McdPcm pcm) {
        this.pcm = pcm;
    }

    @Override
    public int read(int address, Size size) {
        address &= MD_PC_MASK;
        int res = size.getMask();
        if (address >= START_MCD_SUB_WORD_RAM_2M && address < END_MCD_SUB_WORD_RAM_2M) {
            assert memCtx.wramSetup.mode == WordRamMode._2M;
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
            assert memCtx.wramSetup.mode == WordRamMode._2M;
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
            case CD -> handleCdRegWrite(regSpec, address, data, size);
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
            memCtx.setSharedBit(cpu, SharedBit.DMNA, 0);
        }
    }

    private void handleCdRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        LOG.warn("S Write CDD {} : {}, {}, {}", regSpec, th(address), th(data), size);
        writeBufferRaw(commonGateRegs, address & SUB_CPU_REGS_MASK, data, size);
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

    //TODO this should fire at 75hz
    private static boolean fireCddInt = false;

    @Override
    public void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        super.onVdpEvent(event, value);
        if (event == BaseVdpAdapterEventSupport.VdpEvent.V_BLANK_CHANGE) {
            boolean val = (boolean) value;
            if (val && checkInterruptEnabled(2)) {
                LOG.info("S VBlank On, int#2");
                m68kInterrupt(2);
            }
            if (!val && fireCddInt) {
                LOG.info("CDD Int On, int#4");
                m68kInterrupt(4);
            }
        }
    }


    private boolean checkInterruptEnabled(int m68kLevel) {
        int reg = Util.readBufferByte(commonGateRegs, 0x33);
        return (reg & (m68kLevel * m68kLevel)) > 0;
    }

    private void m68kInterrupt(int num) {
        getSubCpu().raiseInterrupt(num);
    }

    public MC68000Wrapper getSubCpu() {
        return (MC68000Wrapper) getBusDeviceIfAny(M68kProvider.class).get();
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
