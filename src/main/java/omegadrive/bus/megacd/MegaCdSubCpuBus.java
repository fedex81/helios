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

    private static final int START_MCD_SUB_PRG_RAM = 0;
    private static final int END_MCD_SUB_PRG_RAM = START_MCD_SUB_PRG_RAM + MCD_PRG_RAM_SIZE;

    public static final int START_MCD_SUB_GATE_ARRAY_REGS = 0xFF_8000;
    private static final int END_MCD_SUB_GATE_ARRAY_REGS = 0xFF_81FF;

    public static final int START_MCD_SUB_GA_COMM_W = START_MCD_SUB_GATE_ARRAY_REGS + 0x20;
    private static final int END_MCD_SUB_GA_COMM_W = START_MCD_SUB_GA_COMM_W + 0x10;
    public static final int START_MCD_SUB_GA_COMM_R = START_MCD_SUB_GATE_ARRAY_REGS + 0x10;
    public static final int END_MCD_SUB_GA_COMM_R = END_MCD_SUB_GA_COMM_W;
    private static final int SUB_CPU_REGS_MASK = MDC_SUB_GATE_REGS_SIZE - 1;

    private static final int START_MCD_SUB_WORD_RAM = 0x80_000;
    private static final int END_MCD_SUB_WORD_RAM = START_MCD_SUB_WORD_RAM + MCD_WORD_RAM_2M_SIZE;
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
        int res = readBuffer(gateRegs, address & SUB_CPU_REGS_MASK, size);
        if (address >= START_MCD_SUB_GA_COMM_R && address < END_MCD_SUB_GA_COMM_R) { //MAIN,SUB COMM
            logHelper.logWarnOnce(LOG, "S Read MEGA_CD_COMM: {}, {}, {}", th(address), th(res), size);
            return res;
        }
        logHelper.logWarnOnce(LOG, "S Read MEGA_CD_EXP: {}, {}, {}", th(address),
                th(res), size);
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        LOG.info("S Write MEGA_CD_EXP: {}, {}, {}", th(address),
                th(data), size);
        if (address >= START_MCD_SUB_GA_COMM_W && address < END_MCD_SUB_GA_COMM_W) { //MAIN COMM
            LOG.info("S Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBuffer(gateRegs, address & SUB_CPU_REGS_MASK, data, size);
            return;
        }
        if (address >= START_MCD_SUB_GA_COMM_R && address < START_MCD_SUB_GA_COMM_W) { //SUB COMM READ ONLY
            LOG.error("S illegal write read-only MEGA_CD_COMM reg: {}", th(address));
            return;
        }
        assert size != Size.LONG;
        int regEven = (address & MCD_GATE_REGS_MASK) & ~1;
        int regVal = readBuffer(gateRegs, regEven, Size.WORD);
        writeBuffer(gateRegs, address & SUB_CPU_REGS_MASK, data, size);
        int newVal = readBuffer(gateRegs, regEven, Size.WORD);
        if (regVal == newVal) {
            return;
        }
        switch (regEven) {
            case 0:
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
            case 2:
                WramSetup ws = memCtx.update(cpu, newVal);
                if (ws == WramSetup.W_2M_MAIN) { //set DMNA=0
                    setBitVal(gateRegs, regEven + 1, 1, 0, Size.BYTE);
                }
                break;
            case 0xE:
                //sub can only write to LSB
                assert size == Size.BYTE && (address & 1) == 1;
                LOG.info("S write COMM_FLAG: {} {}", th(data), size);
                break;
            case 0x32:
                LOG.info("S Write Interrupt mask control: {}, {}, {}", th(address), th(data), size);
                break;
            case 0x36:
                LOG.info("S Write CDD control: {}, {}, {}", th(address), th(data), size);
                break;
            default:
                LOG.error("S write unknown MEGA_CD_EXP reg: {}", th(address));
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
}
