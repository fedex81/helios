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

package omegadrive.vdp;

import omegadrive.util.LogHelper;
import omegadrive.vdp.md.BaseVdpDmaHandlerTest;
import org.junit.Test;
import org.slf4j.Logger;

import static omegadrive.vdp.model.MdVdpProvider.VdpRamType.VRAM;
import static omegadrive.vdp.model.MdVdpProvider.VdpRegisterName.*;

public class FastMemDmaTest extends BaseVdpDmaHandlerTest {

    private static final Logger LOG = LogHelper.getLogger(FastMemDmaTest.class.getSimpleName());

    private int VRAM_DMA_CMD = 0x40000080;

    /**
     * TODO setup a megaCd system
     */
    @Test
    public void testFastMemDmaTest() {
        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdpProvider);
        MdVdpTestUtil.vdpEnableDma(vdpProvider, true);
        int dmaLen = 4;
        int autoInc = 2;
        int dmaSrc = 0x21_0002 >> 1;
        int dmaDest = 0x1000;
        setVdpRegDma(AUTO_INCREMENT, autoInc);
        setVdpRegDma(DMA_LENGTH_LOW, dmaLen & 0xFF);
        setVdpRegDma(DMA_LENGTH_HIGH, (dmaLen >> 8) & 0xFF);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        setVdpRegDma(DMA_SOURCE_LOW, dmaSrc & 0xFF);
        setVdpRegDma(DMA_SOURCE_MID, (dmaSrc >> 8) & 0xFF);
        setVdpRegDma(DMA_SOURCE_HIGH, (dmaSrc >> 16) & 0xFF);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        int vdpCmd = VRAM_DMA_CMD | dmaDest;

        vdpProvider.writeControlPort(vdpCmd >> 16);
        vdpProvider.writeControlPort(vdpCmd & 0xFFFF);

        MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        String str = MdVdpTestUtil.printVdpMemory(memoryInterface, VRAM, 0, 0x10);
        System.out.println(str);
    }
}
