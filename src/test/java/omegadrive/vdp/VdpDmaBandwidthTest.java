/*
 * VdpDmaBandwidthTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 14:04
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

import omegadrive.vdp.model.GenesisVdpProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

public class VdpDmaBandwidthTest extends BaseVdpDmaBandwidthTest {

    private static Logger LOG = LogManager.getLogger(VdpDmaBandwidthTest.class.getSimpleName());

    @Test
    public void test68kToCramBlankingH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.CRAM, BLANKING_VRAM_DMA_PER_LINE_H40_WORDS + 1, false, true);
    }

    @Test
    public void test68kToCramBlankingH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.CRAM, BLANKING_VRAM_DMA_PER_LINE_H32_WORDS + 1, true, true);
    }

    @Test
    public void test68kToCramActiveScreenH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.CRAM, ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H32_WORDS + 1, true, false);
    }

    @Test
    public void test68kToCramActiveScreenH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.CRAM, ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H40_WORDS + 1, false, false);
    }

    @Test
    public void test68kToVsramBlankingH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.VSRAM, BLANKING_VRAM_DMA_PER_LINE_H40_WORDS + 1, false, true);
    }

    @Test
    public void test68kToVsramBlankingH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.VSRAM, BLANKING_VRAM_DMA_PER_LINE_H32_WORDS + 1, true, true);
    }

    @Test
    public void test68kToVsramActiveScreenH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.VSRAM, ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H32_WORDS + 1, true, false);
    }

    @Test
    public void test68kToVsramActiveScreenH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.VSRAM, ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H40_WORDS + 1, false, false);
    }

    @Test
    public void test68kToVramBlankingH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.VRAM, BLANKING_VRAM_DMA_PER_LINE_H40_WORDS / 2, false, true);
    }

    @Test
    public void test68kToVramBlankingH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.VRAM, BLANKING_VRAM_DMA_PER_LINE_H32_WORDS / 2, true, true);
    }

    @Test
    public void test68kToVramActiveScreenH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.VRAM, (ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H32_WORDS + 3) / 2, true, false);
    }

    @Test
    public void test68kToVramActiveScreenH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDma(GenesisVdpProvider.VdpRamType.VRAM, (ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H40_WORDS + 3) / 2, false, false);
    }

    @Test
    public void testDMACopyDuringActiveScreenH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        testDMACopyInternal(ACTIVE_SCREEN_DMA_COPY_PER_LINE_H32 + 1, true, false);
    }

    @Test
    public void testDMACopyDuringActiveScreenH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        testDMACopyInternal(ACTIVE_SCREEN_DMA_COPY_PER_LINE_H40 + 1, false, false);
    }

    @Test
    public void testDMACopyDuringVBlankH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        testDMACopyInternal(BLANKING_DMA_VRAM_PER_LINE_H32 + 1, true, true);
    }

    @Test
    public void testDMACopyDuringVBlankH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        testDMACopyInternal(BLANKING_DMA_VRAM_PER_LINE_H40 + 1, false, true);
    }

    @Test
    public void testDMAFillDuringVblankH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        int dmaLen = BLANKING_VRAM_DMA_PER_LINE_H32_WORDS + 1;
        long dmaFillCommand = 0x40020082; //DMA fill at VRAM address 0x8002
        setupDMAFillInternal(dmaFillCommand, 2, dmaLen);
        startDmaFill(dmaLen, true, true);
    }

    @Test
    public void testDMAFillDuringVblankH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        int dmaLen = BLANKING_VRAM_DMA_PER_LINE_H40_WORDS + 1;
        long dmaFillCommand = 0x40020082; //DMA fill at VRAM address 0x8002
        setupDMAFillInternal(dmaFillCommand, 2, dmaLen);
        startDmaFill(dmaLen, false, true);
    }

    @Test
    public void testDMAFillDuringActiveScreenH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        testDMAFillDuringActiveScreen(ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H32_WORDS + 1, true);
    }

    @Test
    public void testDMAFillDuringActiveScreenH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        testDMAFillDuringActiveScreen(ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H40_WORDS + 1, false);
    }
}