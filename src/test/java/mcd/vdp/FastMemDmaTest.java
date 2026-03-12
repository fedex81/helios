/*
 * Copyright (c) 2018-2019 Federico Berti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package mcd.vdp;

import mcd.McdWordRamTest;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import omegadrive.vdp.MdVdpTestUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static omegadrive.vdp.MdVdpTestUtil.setVdpRegister;
import static omegadrive.vdp.model.MdVdpProvider.VdpRamType.VRAM;
import static omegadrive.vdp.model.MdVdpProvider.VdpRegisterName.*;

public class FastMemDmaTest extends McdVdpTestBase {

    private static final Logger LOG = LogHelper.getLogger(FastMemDmaTest.class.getSimpleName());

    private int VRAM_DMA_CMD = 0x40000080;

    @Test
    public void testFastMemDmaTest_2M() {
        McdWordRamTest.setWramMain2M(lc);
        testFastMemDmaInternal(0x21_FFFC);
    }

    /**
     * 21_FFFC >> 1 = 10_FFFE in DMA_SRC reg
     * 10_FFFE + 1 = 10_FFFF
     * 10_FFFF + 1 = 10_0000 (as 10_ is fixed)
     */
    @Test
    public void testFastMemDmaTest_1M() {
        McdWordRamTest.setWram1M_W0Main(lc);
        MdRuntimeData.setAccessTypeExt(BufferUtil.CpuDeviceAccess.M68K);
        testFastMemDmaInternal(0x21_FFFC);

    }

    public void testFastMemDmaInternal(int baseWramTest) {
        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdpProvider);
        MdVdpTestUtil.vdpEnableDma(vdpProvider, true);
        int dmaLen = 4;
        int autoInc = 2;
        int dmaSrc = (baseWramTest + 2) >> 1;
        int dmaDest = 0x1000;
        int firstWord = 0x1111;

        int dmaSrcAfterWrap = (baseWramTest & 0xFF_0000) | ((baseWramTest + 2) & 0xFFFF);

        lc.mainBus.write(baseWramTest, 0x1111_2222, Size.LONG);
        lc.mainBus.write(baseWramTest + 4, 0x3333_4444, Size.LONG);
        lc.mainBus.write(dmaSrcAfterWrap, 0x5555_6666, Size.LONG);

        setVdpRegister(vdpProvider, AUTO_INCREMENT, autoInc);
        setVdpRegister(vdpProvider, DMA_LENGTH_LOW, dmaLen & 0xFF);
        setVdpRegister(vdpProvider, DMA_LENGTH_HIGH, (dmaLen >> 8) & 0xFF);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        setVdpRegister(vdpProvider, DMA_SOURCE_LOW, dmaSrc & 0xFF);
        setVdpRegister(vdpProvider, DMA_SOURCE_MID, (dmaSrc >> 8) & 0xFF);
        setVdpRegister(vdpProvider, DMA_SOURCE_HIGH, (dmaSrc >> 16) & 0xFF);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        int vdpCmd = VRAM_DMA_CMD | dmaDest;

        vdpProvider.writeControlPort(vdpCmd >> 16);
        vdpProvider.writeControlPort(vdpCmd & 0xFFFF);

        MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        String str = MdVdpTestUtil.printVdpMemory(vdpProvider.getVdpMemory(), VRAM, 0, 0x10);
        System.out.println(str);
        vdpProvider.getVdpMemory().writeVideoRamWord(VRAM, firstWord, 0);

        str = MdVdpTestUtil.printVdpMemory(vdpProvider.getVdpMemory(), VRAM, 0, 0x10);
        System.out.println(str);
    }
}
