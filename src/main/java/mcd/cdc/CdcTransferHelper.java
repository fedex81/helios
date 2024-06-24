package mcd.cdc;

import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_CDC_MODE;
import static mcd.dict.MegaCdDict.START_MCD_SUB_WORD_RAM_1M;
import static mcd.dict.MegaCdDict.START_MCD_SUB_WORD_RAM_2M;
import static mcd.pcm.McdPcm.PCM_START_WAVE_DATA_WINDOW;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CdcTransferHelper implements CdcModel.CdcTransferAction {

    private final static Logger LOG = LogHelper.getLogger(CdcTransferHelper.class.getSimpleName());

    private Cdc cdc;
    private CdcModel.CdcTransfer t;
    private MegaCdMemoryContext memoryContext;
    private ByteBuffer ram;


    public CdcTransferHelper(Cdc cdc, MegaCdMemoryContext memoryContext, ByteBuffer ram) {
        this.cdc = cdc;
        assert cdc.getContext() != null;
        this.t = cdc.getContext().transfer;
        this.memoryContext = memoryContext;
        this.ram = ram;
    }

    @Override
    public void start() {
        LOG.info("Transfer start");
        CdcModel.CdcTransfer t = cdc.getContext().transfer;
        if (t.enable == 0) return;
        t.active = 1;
        t.busy = 1;
        t.ready = (t.destination == 2 || t.destination == 3) ? 1 : 0;
        t.completed = 0;
        cdc.getContext().irq.transfer.pending = 0;
        cdc.recalcRegValue(MCD_CDC_MODE);
        cdc.poll();
        if (t.length == 127) {
            System.out.println("here");
            hack++;
        }
    }

    @Override
    public void stop() {
        LOG.info("Transfer stop");
        t.active = 0;
        t.busy = 0;
        t.ready = 0;
        cdc.recalcRegValue(MCD_CDC_MODE);
    }

    @Override
    public int read() {
        LOG.info("Transfer read");
        if (t.ready == 0) return 0xFFFF;
        int data = ram.getShort(t.source);
        LOG.info("CDC,RAM_R,ram[{}]={}", th(t.source), th(data));
        t.source = (t.source + 2) & 0x3FFF;
        t.length -= 2;
        if (t.length <= 0) {
            t.length = 0;
            complete();
            hack = hack == 4 ? hack + 1 : hack;
        }
        return data;
    }

    public static int hack = 0;

    @Override
    public void dma() {
        if (t.active == 0) {
            return;
        }
        if (t.destination != 4 && t.destination != 5 && t.destination != 7) return;
        int data = ram.getShort(t.source);
        switch (t.destination) {
            case 7 -> { //WRAM
                int baseAddr = memoryContext.wramSetup.mode == MegaCdMemoryContext.WordRamMode._1M
                        ? START_MCD_SUB_WORD_RAM_1M : START_MCD_SUB_WORD_RAM_2M;
                memoryContext.writeWordRamWord(SUB_M68K, baseAddr | t.address, data);
                LOG.info("CDC,DMA_WRAM,wram[{}]={}", th(baseAddr | t.address), th(data));
            }
            case 5 -> {  //PRG-RAM
                memoryContext.writeProgRam(t.address, data, Size.WORD);
                //mcd.write(1, 1, 0x000000 | (n19) address & ~1, data);
            }
//            case 4:  //PCM (0x1000 - 0x1fff = PCM RAM active 4KB bank)
//                mcd.pcm.write(0x1000 | n12(address >> 1) | 1, data.byte(1));
//                mcd.pcm.write(0x1000 | n12(address >> 1) | 0, data.byte(0));
//                address += 2;  //PCM DMA requires two 8-bit writes per transfer
//                break;
            case 4 -> {
                //PCM DMA requires two 8-bit writes per transfer
                McdPcm.pcm.write(PCM_START_WAVE_DATA_WINDOW | t.address, data >> 8, Size.BYTE);
                McdPcm.pcm.write(PCM_START_WAVE_DATA_WINDOW | t.address + 1, data & 0xFF, Size.BYTE);
                t.address += 2;
            }
            default -> {
                LOG.info("TODO CDC DMA mode: {}", t.destination);
                assert false;
            }
        }
        t.source = (t.source + 2) & 0x3FFF;
        t.address += 2;
        t.length -= 2;
        if (t.length <= 0) {
            t.length = 0;
            complete();
        }
        cdc.recalcRegValue(MCD_CDC_MODE);
    }

    @Override
    public void complete() {
        LOG.info("Transfer complete");
        t.active = t.busy = t.ready = 0;
        t.completed = 1;
        cdc.getContext().irq.transfer.pending = 1;
        cdc.recalcRegValue(MCD_CDC_MODE);
        cdc.poll();
    }
}
