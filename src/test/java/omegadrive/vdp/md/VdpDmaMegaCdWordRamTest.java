package omegadrive.vdp.md;

import omegadrive.bus.md.MdBus;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.BufferUtil;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.model.MdVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mcd.bus.MegaCdMainCpuBus.isWordRamDmaSource;
import static omegadrive.SystemLoader.SystemType.MD;
import static omegadrive.system.SystemProvider.NO_CLOCK;
import static omegadrive.vdp.model.MdVdpProvider.VdpRamType.VRAM;
import static omegadrive.vdp.model.MdVdpProvider.VdpRegisterName.*;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class VdpDmaMegaCdWordRamTest {
    private static final boolean CD_BOOT = false;
    private static final boolean MODE1 = true;

    @Test
    public void testMemoryMapWindows() {
        //MODE1 DMA from cart 0x20 is not WRAM
        Assertions.assertFalse(isWordRamDmaSource(0x10, MODE1));
        //CDBOOT DMA from 0x60 is not WRAM
        Assertions.assertFalse(isWordRamDmaSource(0x30, CD_BOOT));
        //not WRAM as well
        Assertions.assertFalse(isWordRamDmaSource(0, CD_BOOT));
        Assertions.assertFalse(isWordRamDmaSource(0, MODE1));
    }


    private static final int DEST = 0x1000;
    private static final int INC = 2;
    private static final int SRC_HIGH = 0;

    private static int srcWord(int k) {
        return 0xA000 | (k & 0xFFF);
    }

    private MdVdpProvider vdp;
    private VdpMemoryInterface vdpMem;
    private MdMainBusProvider bus;

    @BeforeEach
    public void setup() {
        MdRuntimeData.releaseInstance();
        MdRuntimeData.newInstance(MD, NO_CLOCK);
        MdRuntimeData.setAccessTypeExt(BufferUtil.CpuDeviceAccess.M68K);
    }


    @Test
    public void testFastSourceN4() {
        fastSourceSystem();
        primeVram();
        int openBus = bus.getOpenBusWord();
        runDma(4);
        //srcWord3 is dropped, openBus gets written at the beginning
        Assertions.assertEquals(openBus, word(DEST));
        Assertions.assertEquals(srcWord(0), word(DEST + 2));
        Assertions.assertEquals(srcWord(1), word(DEST + 4));
        Assertions.assertEquals(srcWord(2), word(DEST + 6));
    }

    @Test
    public void testFastSourceN1() {
        fastSourceSystem();
        primeVram();
        int openBus = bus.getOpenBusWord();
        runDma(1);
        //only openBus gets written, rest of VRAM untouched
        Assertions.assertEquals(openBus, word(DEST));
        Assertions.assertEquals(primed(2), word(DEST + 2));
    }

    private void fastSourceSystem() {
        vdpMem = MdVdpMemoryInterface.createInstance();
        bus = SystemTestUtil.setupNewMdSystem(MemoryProvider.createMdInstance(), vdpMem, new FastSourceBus());
        vdp = bus.getVdp();
    }

    private void primeVram() {
        for (int i = 0; i < 16; i++) {
            vdpMem.writeVideoRamByte(VRAM, DEST + i, (byte) (0x11 + i));
        }
    }

    private void runDma(int len) {
        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdp);
        MdVdpTestUtil.vdpEnableDma(vdp, true);
        MdVdpTestUtil.setVdpRegister(vdp, AUTO_INCREMENT, INC);
        MdVdpTestUtil.setVdpRegister(vdp, DMA_LENGTH_LOW, len & 0xFF);
        MdVdpTestUtil.setVdpRegister(vdp, DMA_LENGTH_HIGH, (len >> 8) & 0xFF);
        MdVdpTestUtil.setVdpRegister(vdp, DMA_SOURCE_LOW, 0);
        MdVdpTestUtil.setVdpRegister(vdp, DMA_SOURCE_MID, 0);
        MdVdpTestUtil.setVdpRegister(vdp, DMA_SOURCE_HIGH, SRC_HIGH);
        int cd = 1;
        int ctrlHigh = ((cd & 3) << 14) | (DEST & 0x3FFF);
        int ctrlLow = 0x80 | ((DEST << 14) & 3) | ((cd >> 2) << 4);
        vdp.writeControlPort(ctrlHigh);
        vdp.writeControlPort(ctrlLow);
        MdVdpTestUtil.runVdpUntilDmaDone(vdp);
    }

    private int word(int addr) {
        return vdpMem.readVideoRamWord(VRAM, addr) & 0xFFFF;
    }

    private int primed(int byteOff) {
        return (((0x11 + byteOff) << 8) | (0x11 + byteOff + 1)) & 0xFFFF;
    }

    private static class FastSourceBus extends MdBus {
        @Override
        public boolean isVdpDmaFastSource(int srcAddress) {
            return true;
        }

        @Override
        public int read(int address, Size size) {
            if (size == Size.WORD) {
                return 0xA000 | ((address >> 1) & 0xFFF);
            }
            return super.read(address, size);
        }
    }

}
