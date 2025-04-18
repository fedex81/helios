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

import omegadrive.util.LogHelper;
import omegadrive.vdp.model.MdVdpProvider;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;

public class VdpDmaBandwidthTest2 extends BaseVdpDmaBandwidthTest {

    private static final Logger LOG = LogHelper.getLogger(VdpDmaBandwidthTest2.class.getSimpleName());

    @Ignore("TODO fix")
    @Test
    public void test68kVramDmaPerLineBlankingH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.VRAM, true, true);
    }

    @Test
    public void test68kVramDmaPerLineActiveScreenH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.VRAM, true, false);
    }

    @Ignore("TODO fix")
    @Test
    public void test68kVramDmaPerLineBlankingH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.VRAM, false, true);
    }

    @Test
    public void test68kVramDmaPerLineActiveScreenH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.VRAM, false, false);
    }

    @Test
    public void test68kCramDmaPerLineBlankingH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.CRAM, true, true);
    }

    @Test
    public void test68kCramDmaPerLineActiveScreenH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.CRAM, true, false);
    }

    @Test
    public void test68kCramDmaPerLineBlankingH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.CRAM, false, true);
    }

    @Test
    public void test68kCramDmaPerLineActiveScreenH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.CRAM, false, false);
    }

    @Test
    public void test68kVsramDmaPerLineBlankingH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.VSRAM, true, true);
    }

    @Test
    public void test68kVsramDmaPerLineActiveScreenH32() {
        MdVdpTestUtil.setH32(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.VSRAM, true, false);
    }

    @Test
    public void test68kVsramDmaPerLineBlankingH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.VSRAM, false, true);
    }

    @Test
    public void test68kVsramDmaPerLineActiveScreenH40() {
        MdVdpTestUtil.setH40(vdpProvider);
        test68kDmaPerLine(MdVdpProvider.VdpRamType.VSRAM, false, false);
    }

}