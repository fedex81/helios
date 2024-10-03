package mcd.cdc;

import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static mcd.cdc.CdcImpl.verbose;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_CDC_MODE;
import static mcd.dict.MegaCdDict.START_MCD_SUB_WORD_RAM_1M;
import static mcd.dict.MegaCdDict.START_MCD_SUB_WORD_RAM_2M;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CdcTransferHelper implements CdcModel.CdcTransferAction {

    private final static Logger LOG = LogHelper.getLogger(CdcTransferHelper.class.getSimpleName());

    //$FF800A CDC DMA ADDRESS register
    //PCM address: 10 valid bits (0x3FF), reg value is then << 3 => mask = (0x400 << 3) - 1 = 0x1FFF
    public static final int PCM_ADDRESS_MASK = 0x1FFF;
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
        CdcModel.CdcTransfer t = cdc.getContext().transfer;
        if (t.enable == 0) return;
        t.active = 1;
        t.busy = 1;
        assert t.destination.isValid() : t.destination;
        //DSR is set when destination is mainCpuRead or subCpuRead
        t.ready = (t.destination.isDma()) ? 0 : 1;
        t.completed = 0;
        CdcModel.CdcContext ctx = cdc.getContext();
        ctx.irq.transfer.pending = 0;
        cdc.recalcRegValue(MCD_CDC_MODE);
        t.source &= 0x3fff;
        if (verbose)
            LOG.info("CdcTransfer start, dest: {}, cdcRam: {}, len: {}\n{}", t.destination, th(t.source), t.length, cdc.getContext());
//        cdc.poll(); //TODO gengx not using it
    }

    @Override
    public void stop() {
        if (t.active > 0) {
            if (verbose)
                LOG.info("CdcTransfer stop, dest: {}, cdcRam: {}, len: {}\n{}", t.destination, th(t.source), t.length, cdc.getContext());
        }
        t.active = 0;
        t.busy = 0;
        t.ready = 0;
        cdc.recalcRegValue(MCD_CDC_MODE);
    }

    @Override
    public int read() {
        if (t.destination.isDma()) {
            if (t.ready == 0) return 0xFFFF;
        }
        int data = ram.getShort(t.source);
        if (verbose) LOG.info("CDC,RAM_R,ram[{}]={}", th(t.source), th(data));
        t.source = (t.source + 2) & 0x3FFF;
        t.length -= 2;
        if (t.length <= 0) {
            t.length = 0;
            complete();
        } else if (t.length <= 2) {
            assert t.ready == 1;
            t.completed = 1;
            cdc.recalcRegValue(MCD_CDC_MODE);
        }
        return data;
    }

    @Override
    public void dma() {
        if (t.active == 0 || !t.destination.isDma()) {
            return;
        }
        assert t.destination.isValid();
        int data = ram.getShort(t.source);
        switch (t.destination) {
            case DMA_SUB_WRAM_7 -> { //WRAM
                int baseAddr = memoryContext.wramSetup.mode == MegaCdMemoryContext.WordRamMode._1M
                        ? START_MCD_SUB_WORD_RAM_1M : START_MCD_SUB_WORD_RAM_2M;
                memoryContext.wramHelper.writeWordRamWord(SUB_M68K, baseAddr | t.address, data);
                if (verbose) LOG.info("CDC,DMA_WRAM,wram[{}]={}", th(baseAddr | t.address), th(data));
            }
            case DMA_PROGRAM_5 -> {  //PRG-RAM
                memoryContext.writeProgRam(t.address, data, Size.WORD);
                //mcd.write(1, 1, 0x000000 | (n19) address & ~1, data);
            }
            case DMA_PCM_4 -> {
                assert t.length > 0;
                //TODO hack testDma2 error 0x12
//                if(t.length == 8 && t.address == 8){
//                    t.length++;
//                }
                //TODO hack testDma2 error 0x12

                //PCM DMA requires two 8-bit writes per transfer
                //address gets halved by the PCM chip, hence the double increment
                writePcm(t.address, data >> 8);
                if (t.length - 1 >= 0) {
                    writePcm(t.address + 2, data & 0xFF);
                    t.address += 2;
                }
            }
            default -> {
                LOG.warn("TODO CDC DMA mode: {}", t.destination);
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

    //Brutal, intro
    private void writePcm(int address, int data) {
        address &= PCM_ADDRESS_MASK;
        if (verbose) LOG.info("CDC,DMA_PCM,pcm_ram[{}]={},srcAddrWord={},len={}",
                th(address & PCM_ADDRESS_MASK), th(data & 0xFF), th(t.source), th(t.length));
        McdPcm.pcm.pcmDataWrite(address & PCM_ADDRESS_MASK, data, Size.BYTE);
    }

    @Override
    public void complete() {
        t.active = t.busy = t.ready = 0;
        t.completed = 1;
        cdc.getContext().irq.transfer.pending = 1;
        cdc.recalcRegValue(MCD_CDC_MODE);
        cdc.poll();
        if (verbose)
            LOG.info("CdcTransfer complete, dest: {}, cdcRam: {}, len: {}\n{}", t.destination, th(t.source), t.length, cdc.getContext());
    }
}
