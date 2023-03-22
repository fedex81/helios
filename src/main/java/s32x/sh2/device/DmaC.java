package s32x.sh2.device;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.DmaFifo68k;
import s32x.Sh2MMREG;
import s32x.bus.Sh2Bus;
import s32x.bus.Sh2MemoryParallel;
import s32x.dict.S32xDict;
import s32x.util.Md32xRuntimeData;
import s32x.util.S32xUtil;

import java.nio.ByteBuffer;

import static omegadrive.util.Util.readBufferLong;
import static omegadrive.util.Util.th;
import static s32x.dict.Sh2Dict.RegSpecSh2;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 * <p>
 * 32X Hardware Manual Supplement 1/2,  Chaotix, Primal Rage, and Virtua Racing
 */
public class DmaC implements S32xUtil.Sh2Device {

    private static final Logger LOG = LogHelper.getLogger(DmaC.class.getSimpleName());

    private static final int SH2_CHCR_TRANSFER_END_BIT = 1;
    private static final boolean verbose = false;

    private final ByteBuffer regs;

    private final IntControl intControl;
    private final Sh2Bus memory;
    private final DmaFifo68k dma68k;
    private final S32xUtil.CpuDeviceAccess cpu;
    private final DmaHelper.DmaChannelSetup[] dmaChannelSetup;
    private boolean oneDmaInProgress = false;

    public DmaC(S32xUtil.CpuDeviceAccess cpu, IntControl intControl, Sh2Bus memory, DmaFifo68k dma68k, ByteBuffer regs) {
        this.cpu = cpu;
        this.regs = regs;
        this.memory = memory;
        this.dma68k = dma68k;
        this.intControl = intControl;
        this.dmaChannelSetup = new DmaHelper.DmaChannelSetup[]{DmaHelper.createChannel(0), DmaHelper.createChannel(1)};
    }

    @Override
    public void write(RegSpecSh2 regSpec, int pos, int value, Size size) {
        if (verbose) LOG.info("{} DMA write {}: {} {}", cpu, regSpec.getName(), th(value), size);
        S32xUtil.writeBufferRaw(regs, pos, value, size);
        switch (cpu.regSide) {
            case SH2 -> {
                assert pos == regSpec.addr : th(pos) + ", " + th(regSpec.addr);
                writeSh2(cpu, regSpec, value, size);
            }
            default -> throw new RuntimeException();
        }
    }

    @Override
    public int read(RegSpecSh2 regSpec, int reg, Size size) {
        return S32xUtil.readBuffer(regs, reg, size);
    }

    @Override
    public void step(int cycles) {
        //NOTE: dma runs 1/3 speed, but without memory access delays, seems to be close enough
        if (!oneDmaInProgress) {
            return;
        }
        for (DmaHelper.DmaChannelSetup c : dmaChannelSetup) {
            if (c.dmaInProgress && (c.chcr_autoReq || c.dreqLevel)) {
                dmaOneStep(c);
            }
        }
    }

    private void writeSh2(S32xUtil.CpuDeviceAccess cpu, RegSpecSh2 regSpec, int value, Size size) {
        switch (regSpec) {
            case DMA_CHCR0, DMA_CHCR1 -> {
                assert size == Size.LONG;
                handleChannelControlWrite(regSpec.addr, value);
            }
            case DMA_DMAOR -> {
                assert size == Size.LONG;
                handleOperationRegWrite(value);
            }
        }
    }

    private void handleChannelControlWrite(int reg, int value) {
        int channel = (reg >> 4) & 1;
        DmaHelper.DmaChannelSetup chan = dmaChannelSetup[channel];
        boolean wasEn = chan.chcr_dmaEn;
        DmaHelper.updateChannelControl(chan, value);
        if (wasEn != chan.chcr_dmaEn) {
            if (chan.chcr_dmaEn) {
                checkDmaStart(chan);
            } else {
                dmaEnd(chan, false);
            }
        }
    }

    private void handleOperationRegWrite(int value) {
        DmaHelper.updateChannelDmaor(dmaChannelSetup[0], value);
        DmaHelper.updateChannelDmaor(dmaChannelSetup[1], value);
        boolean dme = (value & 1) > 0;
        if (dme) {
            checkDmaStart(dmaChannelSetup[0]);
            checkDmaStart(dmaChannelSetup[1]);
        } else {
            dmaEnd(dmaChannelSetup[0], false);
            dmaEnd(dmaChannelSetup[1], false);
        }
    }

    private void checkDmaStart(DmaHelper.DmaChannelSetup chan) {
        if (chan.chcr_dmaEn && chan.dmaor_dme && !chan.chcr_tranEndOk && (chan.chcr_autoReq || chan.dreqLevel)) {
            if (!chan.dmaInProgress) {
                chan.dmaInProgress = true;
                updateOneDmaInProgress();
                if (verbose) LOG.info("{} DMA start: {}", cpu, chan);
            }
        }
    }

    @Deprecated
    public void dmaReqTriggerPwm(int channel, boolean enable) {
        DmaHelper.DmaChannelSetup d = dmaChannelSetup[channel];
        d.dreqLevel = enable;
        if (enable) {
            checkDmaStart(d);
            if (d.dmaInProgress) {
                Md32xRuntimeData.setAccessTypeExt(cpu);
                dmaOneStep(d);
                d.dreqLevel = false;
                d.dmaInProgress = false;
                updateOneDmaInProgress();
            }
        }
        if (verbose) LOG.info("{} DreqPwm{} Level: {}", cpu, channel, enable);
    }

    public void dmaReqTrigger(int channel, boolean enable) {
        dmaChannelSetup[channel].dreqLevel = enable;
        if (enable) {
            checkDmaStart(dmaChannelSetup[channel]);
        }
        if (verbose) LOG.info("{} Dreq{} Level: {}", cpu, channel, enable);
    }

    //TODO 4. When the cache is used as on-chip RAM, the DMAC cannot access this RAM.
    private void dmaOneStep(DmaHelper.DmaChannelSetup c) {
        int len = readBufferForChannel(c.channel, DMA_TCR0.addr, Size.LONG) & 0xFF_FFFF;
        int srcAddress = readBufferForChannel(c.channel, DMA_SAR0.addr, Size.LONG);
        int destAddress = readBufferForChannel(c.channel, DMA_DAR0.addr, Size.LONG);
        int steps = c.transfersPerStep;
        assert cpu == Md32xRuntimeData.getAccessTypeExt();
//        assert (destAddress >> Sh2Prefetch.PC_CACHE_AREA_SHIFT) != 0 : th(srcAddress) +"," + th(destAddress);
//        assert (srcAddress >> Sh2Prefetch.PC_CACHE_AREA_SHIFT) != 0 : th(srcAddress) +"," + th(destAddress);
        destAddress |= S32xDict.SH2_CACHE_THROUGH_OFFSET;
        srcAddress |= S32xDict.SH2_CACHE_THROUGH_OFFSET;

        assert !(memory instanceof Sh2MemoryParallel);
        do {
            int val = memory.read(srcAddress, c.trnSize);
            memory.write(destAddress, val, c.trnSize);
            if (verbose)
                LOG.info("{} DMA write, src: {}, dest: {}, val: {}, dmaLen: {}", cpu, th(srcAddress), th(destAddress),
                        th((int) val), th(len));
            srcAddress += c.srcDelta;
            destAddress += c.destDelta;
            len = (len - 1) & 0xFF_FFFF;
        } while (--steps > 0 && len >= 0);
        writeBufferForChannel(c.channel, DMA_DAR0.addr, destAddress, Size.LONG);
        writeBufferForChannel(c.channel, DMA_SAR0.addr, srcAddress, Size.LONG);

        if (len <= 0) {
            if (verbose)
                LOG.info("{} DMA end, src: {}, dest: {}, dmaLen: {}", cpu, th(srcAddress), th(destAddress), th(len));
            dmaEnd(c, true);
            len = 0;
        }
        writeBufferForChannel(c.channel, DMA_TCR0.addr, Math.max(len, 0), Size.LONG);
    }

    private void dmaEnd(DmaHelper.DmaChannelSetup c, boolean normal) {
        if (c.dmaInProgress) {
            c.dmaInProgress = false;
            c.dreqLevel = false;
            updateOneDmaInProgress();
            if (!c.chcr_autoReq) {
                dma68k.dmaEnd();
            }
            //transfer ended normally, ie. TCR = 0
            if (normal) {
                int chcr = setDmaChannelBitVal(c.channel, DMA_CHCR0.addr + 2, SH2_CHCR_TRANSFER_END_BIT, 1, Size.WORD);
                DmaHelper.updateChannelControl(c, chcr);
                if (c.chcr_intEn) {
                    intControl.setOnChipDeviceIntPending(Sh2DeviceHelper.Sh2DeviceType.DMA, c.channel == 0 ? IntControl.OnChipSubType.DMA_C0 : IntControl.OnChipSubType.DMA_C1);
                }
            }
            if (verbose) LOG.info("{} DMA stop, aborted: {}, {}", cpu, !normal, c);
        }
    }

    private void updateOneDmaInProgress() {
        oneDmaInProgress = dmaChannelSetup[0].dmaInProgress || dmaChannelSetup[1].dmaInProgress;
    }

    @Override
    public void reset() {
        writeBufferForChannel(0, DMA_CHCR0.addr, 0, Size.LONG);
        writeBufferForChannel(1, DMA_CHCR0.addr, 0, Size.LONG);
        S32xUtil.writeBufferRaw(regs, DMA_DRCR0.addr, 0, Size.BYTE);
        S32xUtil.writeBufferRaw(regs, DMA_DRCR1.addr, 0, Size.BYTE);
        S32xUtil.writeBufferRaw(regs, DMA_DMAOR.addr, 0, Size.LONG);
        oneDmaInProgress = false;
    }

    public DmaHelper.DmaChannelSetup[] getDmaChannelSetup() {
        return dmaChannelSetup;
    }

    private void setDmaChannelBit(int channel, int regChan0, int bitPos, int bitVal, Size size) {
        S32xUtil.setBit(regs, (regChan0 + (channel << 4)) & Sh2MMREG.SH2_REG_MASK, bitPos, bitVal, size);
    }

    private int setDmaChannelBitVal(int channel, int regChan0, int bitPos, int bitVal, Size size) {
        return S32xUtil.setBitVal(regs, (regChan0 + (channel << 4)) & Sh2MMREG.SH2_REG_MASK, bitPos, bitVal, size);
    }

    private int readVcrDma(int channel) {
        return readBufferLong(regs, (DMA_VRCDMA0.addr + (channel << 3)) & Sh2MMREG.SH2_REG_MASK) & 0xFF;
    }

    //channel1 = +0x10
    private int readBufferForChannel(int channel, int regChan0, Size size) {
        return S32xUtil.readBuffer(regs, (regChan0 + (channel << 4)) & Sh2MMREG.SH2_REG_MASK, size);
    }

    private void writeBufferForChannel(int channel, int regChan0, int value, Size size) {
        S32xUtil.writeBufferRaw(regs, (regChan0 + (channel << 4)) & Sh2MMREG.SH2_REG_MASK, value, size);
    }
}