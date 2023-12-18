package omegadrive.bus.megacd;

import omegadrive.bus.md.GenesisBus;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport;
import omegadrive.vdp.model.BaseVdpProvider;
import org.slf4j.Logger;
import s32x.util.S32xUtil;

import java.nio.ByteBuffer;

import static omegadrive.bus.megacd.MegaCdDict.*;
import static omegadrive.bus.megacd.MegaCdMemoryContext.*;
import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;
import static omegadrive.util.S32xUtil.*;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MegaCdSubCpuBus extends GenesisBus {

    private static final Logger LOG = LogHelper.getLogger(MegaCdSubCpuBus.class.getSimpleName());
    private ByteBuffer subCpuRam, gateRegs;
    private LogHelper logHelper = new LogHelper();
    private MegaCdMemoryContext memCtx;
    private S32xUtil.CpuDeviceAccess cpu;

    public MegaCdSubCpuBus(MegaCdMemoryContext ctx) {
        subCpuRam = ByteBuffer.wrap(ctx.prgRam);
        gateRegs = ByteBuffer.wrap(ctx.gateRegs);
        memCtx = ctx;
        cpu = S32xUtil.CpuDeviceAccess.SUB_M68K;
        writeBuffer(gateRegs, 1, 1, Size.BYTE); //not reset
    }

    @Override
    public int read(int address, Size size) {
        address &= MD_PC_MASK;
        if (address >= START_MCD_SUB_WORD_RAM && address < END_MCD_SUB_WORD_RAM) {
            return memCtx.readWordRam(cpu, address, size);
        } else if (address >= START_MCD_SUB_PRG_RAM && address < END_MCD_SUB_PRG_RAM) {
            return readBuffer(subCpuRam, address & MCD_PRG_RAM_MASK, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS && address < END_MCD_SUB_GATE_ARRAY_REGS) {
            return handleMegaCdExpRead(address, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Read Reserved: {} {} {}", address, size);
        }
        return super.read(address, size);
    }

    @Override
    public void write(int address, int data, Size size) {
        address &= MD_PC_MASK;
        if (address >= START_MCD_SUB_WORD_RAM && address < END_MCD_SUB_WORD_RAM) {
            memCtx.writeWordRam(cpu, address, data, size);
            return;
        } else if (address >= START_MCD_SUB_PRG_RAM && address < END_MCD_SUB_PRG_RAM) {
            writeBuffer(subCpuRam, address & MCD_PRG_RAM_MASK, data, size);
            return;
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS && address <= END_MCD_SUB_GATE_ARRAY_REGS) {
            handleMegaCdExpWrite(address, data, size);
            return;
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Write Reserved: {} {} {}", address, data, size);
        }
        super.write(address, data, size);
    }

    private int handleMegaCdExpRead(int address, Size size) {
        MegaCdDict.RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpu, address);
        logAccess(regSpec, cpu, address, 0, size, true);
        int res = readBuffer(gateRegs, address & MCD_GATE_REGS_MASK, size);
        if (regSpec == MegaCdDict.RegSpecMcd.INVALID) {
            LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
            return 0;
        }
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        MegaCdDict.RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpu, address);
        MegaCdDict.logAccess(regSpec, cpu, address, data, size, false);
        if (regSpec == MegaCdDict.RegSpecMcd.INVALID) {
            LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
            return;
        }
        switch (regSpec.deviceType) {
            case SYS -> handleSysRegWrite(regSpec, address, data, size);
            case COMM -> handleCommRegWrite(regSpec, address, data, size);
            case CD -> handleCdRegWrite(regSpec, address, data, size);
        }
    }

    private void handleSysRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        assert size != Size.LONG;
        int regEven = (address & MCD_GATE_REGS_MASK) & ~1;
        int regVal = readBuffer(gateRegs, regEven, Size.WORD);
        writeBuffer(gateRegs, address & SUB_CPU_REGS_MASK, data, size);
        int newVal = readBuffer(gateRegs, regEven, Size.WORD);
        if (regVal == newVal) {
            return;
        }
        switch (regSpec) {
            case MCD_RESET:
                writeBuffer(gateRegs, regEven, regVal, Size.WORD);
                int sreset = data & 1;
                //set RES0
                setBit(gateRegs, 1, 0, sreset, Size.BYTE);
                //set LEDR
                setBit(gateRegs, 1, 7, (data >> 7) & 1, Size.BYTE);
                LOG.info("S SubCpu reset: {}", (sreset == 0 ? "Reset" : "Ignore"));
                if (sreset == 0) {
                    //TODO reset CD drive, part of cddinit, does it take 100ms??
                    LOG.info("S subCpu peripheral reset started");
                    setBit(gateRegs, 1, 0, 1, Size.BYTE);//cd drive done resetting
                }
                break;
            case MCD_MEM_MODE:
                WramSetup ws = memCtx.update(cpu, newVal);
                if (ws == WramSetup.W_2M_MAIN) { //set DMNA=0
                    setBitVal(gateRegs, regEven + 1, 1, 0, Size.BYTE);
                }
                break;
            case MCD_COMM_FLAGS:
                //sub can only write to LSB
                assert size == Size.BYTE && (address & 1) == 1;
                LOG.info("S write COMM_FLAG: {} {}", th(data), size);
                break;
            case MCD_INT_MASK:
                LOG.info("S Write Interrupt mask control: {}, {}, {}", th(address), th(data), size);
                break;
            default:
                LOG.error("S write unhandled MEGA_CD_EXP reg: {} ({}), {} {}", th(address), regSpec, th(data), size);
        }
    }

    private void handleCdRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        switch (regSpec) {
            case MCD_CDD_CONTROL:
                LOG.warn("S Write CDD control: {}, {}, {}", th(address), th(data), size);
                break;
            default:
                LOG.error("S write unknown MEGA_CD_EXP reg: {} ({}), {} {}", th(address), regSpec, th(data), size);
        }
    }

    private void handleCommRegWrite(MegaCdDict.RegSpecMcd regSpec, int address, int data, Size size) {
        if (address >= START_MCD_SUB_GA_COMM_W && address < END_MCD_SUB_GA_COMM_W) { //MAIN COMM
            LOG.info("S Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBuffer(gateRegs, address & SUB_CPU_REGS_MASK, data, size);
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
        int regVal = readBuffer(gateRegs, 0, Size.WORD) | 1;
        writeBuffer(gateRegs, 0, regVal, Size.WORD);
        LOG.info("S subCpu reset done");
    }

    @Override
    public void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        super.onVdpEvent(event, value);
        if (event == BaseVdpAdapterEventSupport.VdpEvent.V_BLANK_CHANGE) {
            boolean val = (boolean) value;
            if (val) {
                if ((Util.readBufferByte(gateRegs, 0x33) & 4) > 0) {
                    LOG.info("S VBlank On, int#2");
                    MC68000Wrapper m68k = (MC68000Wrapper) getBusDeviceIfAny(M68kProvider.class).get();
                    m68k.raiseInterrupt(2);
                }
            }
        }
    }

    public void logAccess(RegSpecMcd regSpec, S32xUtil.CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        logHelper.logWarnOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }
}
