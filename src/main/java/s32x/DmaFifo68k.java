package s32x;

import omegadrive.Device;
import omegadrive.util.*;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import org.slf4j.Logger;
import s32x.S32XMMREG.RegContext;
import s32x.dict.S32xDict.RegSpecS32x;
import s32x.savestate.Gs32xStateHandler;
import s32x.sh2.device.DmaC;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;

import static omegadrive.util.Util.readBufferWord;
import static omegadrive.util.Util.th;
import static s32x.dict.S32xDict.RegSpecS32x.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 * <p>
 * 32X Hardware Manual Supplement 1
 */
public class DmaFifo68k implements Device {

    private static final Logger LOG = LogHelper.getLogger(DmaFifo68k.class.getSimpleName());
    public static final int M68K_FIFO_FULL_BIT = 7;
    public static final int M68K_68S_BIT_POS = 2;
    public static final int SH2_FIFO_FULL_BIT = 15;
    public static final int SH2_FIFO_EMPTY_BIT = 14;
    public static final int DREQ0_CHANNEL = 0;
    public static final int DMA_FIFO_SIZE = 8;
    public static final int M68K_DMA_FIFO_LEN_MASK = 0xFFFC;
    private final ByteBuffer sysRegsMd, sysRegsSh2;
    private DmaC[] dmac;
    private DmaFifo68kContext ctx;
    public static boolean rv = false;
    private static final boolean verbose = false;

    static class DmaFifo68kContext implements Serializable {
        @Serial
        private static final long serialVersionUID = 5546641086954149096L;
        private final Fifo<Integer> fifo = Fifo.createIntegerFixedSizeFifo(DMA_FIFO_SIZE);
        private boolean m68S = false;
        private boolean rv = false;
    }

    public DmaFifo68k(RegContext regContext) {
        this.sysRegsMd = regContext.sysRegsMd;
        this.sysRegsSh2 = regContext.sysRegsSh2;
        this.ctx = new DmaFifo68kContext();
        Gs32xStateHandler.addDevice(this);
        ctx.rv = rv;
    }

    public int read(RegSpecS32x regSpec, CpuDeviceAccess cpu, int address, Size size) {
        int res = switch (cpu.regSide) {
            case MD -> readMd(address, size);
            case SH2 -> readSh2(regSpec, address, size);
        };
        if (verbose) LOG.info("{} DMA read {}: {} {}", cpu, regSpec.getName(), th(res), size);
        return res;
    }


    public void write(RegSpecS32x regSpec, CpuDeviceAccess cpu, int address, int value, Size size) {
        if (verbose) LOG.info("{} DMA write {}: {} {}", cpu, regSpec.getName(), th(value), size);
        switch (cpu.regSide) {
            case MD -> writeMd(regSpec, address, value, size);
            default -> LOG.error("Invalid {} DMA write {}: {} {}", cpu, regSpec.getName(), th(value), size);
        }
    }

    private void writeMd(RegSpecS32x regSpec, int address, int value, Size size) {
        assert size != Size.LONG;
        switch (regSpec) {
            case MD_DMAC_CTRL:
                handleDreqCtlWriteMd(address, value, size);
                break;
            case MD_FIFO_REG:
                assert MdRuntimeData.getAccessTypeExt() != CpuDeviceAccess.Z80;
                handleFifoRegWriteMd(value, size);
                BufferUtil.writeBufferRaw(sysRegsMd, address, value, size);
                break;
            case MD_DREQ_LEN:
                value &= M68K_DMA_FIFO_LEN_MASK;
                //fall-through
            case MD_DREQ_DEST_ADDR_H:
            case MD_DREQ_DEST_ADDR_L:
            case MD_DREQ_SRC_ADDR_H:
            case MD_DREQ_SRC_ADDR_L:
                assert MdRuntimeData.getAccessTypeExt() != CpuDeviceAccess.Z80;
                //NOTE after burner 68k byte writes: (MD_DREQ_DEST_ADDR_H +1) 0xd,2,BYTE
                BufferUtil.writeBufferRaw(sysRegsMd, address, value, size);
                BufferUtil.writeBufferRaw(sysRegsSh2, address, value, size);
                break;
            default:
                LOG.error("{} check DMA write {}: {} {}", CpuDeviceAccess.M68K, regSpec.getName(), th(value), size);
                break;
        }
    }

    private void handleDreqCtlWriteMd(int reg, int value, Size size) {
        assert size != Size.LONG;
        boolean changed = MD_DMAC_CTRL.regSpec.write(sysRegsMd, reg, value, size);
        if (changed) {
            int res = readBufferWord(sysRegsMd, MD_DMAC_CTRL.addr);
            boolean wasDmaOn = ctx.m68S;
            ctx.m68S = (res & 4) > 0;
            rv = (res & 1) > 0;
            //sync sh2 reg, only lsb 3 bits
            BufferUtil.writeBufferRaw(sysRegsSh2, SH2_DREQ_CTRL.addr + 1, res & MD_DMAC_CTRL.regSpec.writableBitMask, Size.BYTE);
            //NOTE bit 1 is called DMA, only relevant when using SEGA CD (see picodrive)
//            assert (res & 2) == 0;
            if (verbose)
                LOG.info("{} write DREQ_CTL, dmaOn: {} , RV: {}", MdRuntimeData.getAccessTypeExt(), ctx.m68S, rv);
            if (wasDmaOn && !ctx.m68S) {
                LOG.info("{} Setting 68S = 0, stops DMA while running", MdRuntimeData.getAccessTypeExt());
                dmaEnd();
            }
            updateFifoState();
        }
    }

    private void handleFifoRegWriteMd(int value, Size size) {
        assert size == Size.WORD;
        if (ctx.m68S) {
            if (!ctx.fifo.isFull()) {
                ctx.fifo.push(Util.getFromIntegerCache(value));
                updateFifoState();
            } else {
                LOG.error("DMA Fifo full, discarding data");
            }
        } else {
            LOG.error("DMA off, ignoring FIFO write: {}", th(value));
        }
    }

    public void dmaEnd() {
        ctx.fifo.clear();
        updateFifoState();
        evaluateDreqTrigger(true); //force clears the dreqLevel in DMAC
        //set 68S to 0
        BufferUtil.setBit(sysRegsMd, sysRegsSh2, SH2_DREQ_CTRL.addr + 1, M68K_68S_BIT_POS, 0, Size.BYTE);
        ctx.m68S = false;
    }

    public void updateFifoState() {
        boolean changed = BufferUtil.setBitRegFromWord(sysRegsMd, MD_DMAC_CTRL, M68K_FIFO_FULL_BIT, ctx.fifo.isFullBit());
        if (changed) {
            BufferUtil.setBit(sysRegsSh2, SH2_DREQ_CTRL.addr, SH2_FIFO_FULL_BIT,
                    ctx.fifo.isFull() ? 1 : 0, Size.WORD);
            if (verbose) {
                LOG.info("68k DMA Fifo FULL state changed: {}", BufferUtil.toHexString(sysRegsMd, MD_DMAC_CTRL.addr, Size.WORD));
                LOG.info("Sh2 DMA Fifo FULL state changed: {}", BufferUtil.toHexString(sysRegsSh2, SH2_DREQ_CTRL.addr, Size.WORD));
            }
        }
        changed = BufferUtil.setBitRegFromWord(sysRegsSh2, SH2_DREQ_CTRL, SH2_FIFO_EMPTY_BIT, ctx.fifo.isEmptyBit());
        if (changed) {
            if (verbose)
                LOG.info("Sh2 DMA Fifo empty state changed: {}", BufferUtil.toHexString(sysRegsSh2, SH2_DREQ_CTRL.addr, Size.WORD));
        }
        evaluateDreqTrigger(ctx.m68S);
    }

    //NOTE: there are two ctx.fifos of size 4, dreq is triggered when at least one ctx.fifo is full
    private void evaluateDreqTrigger(boolean pm68S) {
        final int lev = ctx.fifo.getLevel();
        if (pm68S && (lev & 3) == 0) { //lev can be 0,4,8
            boolean enable = lev > 0; //lev can be 4,8
            dmac[CpuDeviceAccess.MASTER.ordinal()].dmaReqTrigger(DREQ0_CHANNEL, enable);
            dmac[CpuDeviceAccess.SLAVE.ordinal()].dmaReqTrigger(DREQ0_CHANNEL, enable);
            if (verbose) LOG.info("DMA ctx.fifo dreq: {}", enable);
        }
    }

    private int readMd(int reg, Size size) {
        return BufferUtil.readBuffer(sysRegsMd, reg, size);
    }


    private int readSh2(RegSpecS32x regSpec, int address, Size size) {
        if (regSpec == SH2_DREQ_CTRL) {
            return BufferUtil.readBuffer(sysRegsSh2, address, size);
        } else if (regSpec == SH2_FIFO_REG) {
            assert size == Size.WORD;
            int res = 0;
            if (ctx.m68S && !ctx.fifo.isEmpty()) {
                res = ctx.fifo.pop();
                updateFifoState();
            } else {
                LOG.error("Dreq0: {}, ctx.fifoEmpty: {}", ctx.m68S, ctx.fifo.isEmpty());
            }
            return res;
        }
        return BufferUtil.readBuffer(sysRegsMd, address, size);
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        Device.super.saveContext(buffer);
        ctx.rv = rv;
        buffer.put(Util.serializeObject(ctx));
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        Device.super.loadContext(buffer);
        Serializable s = Util.deserializeObject(buffer);
        assert s instanceof DmaFifo68kContext;
        ctx = (DmaFifo68kContext) s;
        rv = ctx.rv;
    }

    public void setDmac(DmaC... dmac) {
        this.dmac = dmac;
    }

    public DmaC[] getDmac() {
        return dmac;
    }
}