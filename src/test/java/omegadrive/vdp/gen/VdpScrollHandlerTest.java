/*
 * VdpScrollHandlerTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 12:48
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

package omegadrive.vdp.gen;

import omegadrive.util.RegionDetector;
import omegadrive.util.VideoMode;
import omegadrive.vdp.gen.VdpScrollHandler.ScrollContext;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.IVdpFifo;
import omegadrive.vdp.model.InterlaceMode;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.junit.Test;

import java.util.Random;

import static omegadrive.vdp.model.GenesisVdpProvider.VdpRegisterName.*;

public class VdpScrollHandlerTest {

    static Random r = new Random(1981);

    private static GenesisVdpProvider createVdpProvider() {
        return new VdpAdaptor() {

            private int[] vdpReg = new int[24];

            @Override
            public void updateRegisterData(int reg, int data) {
                vdpReg[reg] = data;
            }

            @Override
            public int getRegisterData(int reg) {
                return vdpReg[reg];
            }
        };
    }

    public void testLooping() {

    }

    @Test
    public void testScrolling() {
        testTileLocation();
    }

    private void testTileLocation() {
        VdpMemoryInterface v = GenesisVdpMemoryInterface.createInstance();
        fillVdpMemory(v);
        GenesisVdpProvider vdp = createVdpProvider();
        VideoMode vm = VideoMode.PAL_H40_V30;
        int planeANameTable = 0xC000;
        int planeBNameTable = 0xE000;
        int[] planeSizeRegVals = {0, 1, 2, 3, 16, 33, 48};

        vdp.updateRegisterData(PLANE_A_NAMETABLE, planeANameTable / 0x400);
        vdp.updateRegisterData(PLANE_B_NAMETABLE, planeANameTable / 0x2000);

        VdpRenderHandlerImpl renderHandler = new VdpRenderHandlerImpl(vdp, v);
        renderHandler.setVideoMode(vm);

        ScrollContext sca = new ScrollContext();
        ScrollContext scb = new ScrollContext();
        sca.planeA = true;
        scb.planeA = false;
        ScrollContext[] contexts = {sca, scb};

        for (int planeSizeReg : planeSizeRegVals) {
            vdp.updateRegisterData(PLANE_SIZE, planeSizeReg);
            for (VdpScrollHandler.VSCROLL vscroll : VdpScrollHandler.VSCROLL.values()) {
                for (VdpScrollHandler.HSCROLL hscroll : VdpScrollHandler.HSCROLL.values()) {
                    vdp.updateRegisterData(MODE_3, vscroll.getRegValue() << 2 | hscroll.getRegValue());
                    for (ScrollContext sc : contexts) {
                        boolean isPlaneA = sc.planeA;
                        int nameTable = isPlaneA ? planeANameTable : planeBNameTable;
                        for (int line = 0; line < vm.getDimension().height; line++) {
                            renderHandler.renderPlane(line, nameTable, sc);
                        }
                    }
                }

            }
        }
    }

    private void fillVdpMemory(VdpMemoryInterface v) {
        for (int i = 0; i < v.getVram().length; i++) {
            v.getVram()[i] = r.nextInt(0x100);
        }
        for (int i = 0; i < v.getVsram().length; i++) {
            v.getVsram()[i] = r.nextInt(0x100);
        }
        for (int i = 0; i < GenesisVdpProvider.VDP_CRAM_SIZE; i++) {
            v.writeCramByte(i, r.nextInt(0x10) << 8 | r.nextInt(0x10));
        }
    }

    static class VdpAdaptor implements GenesisVdpProvider {

        @Override
        public int readDataPort() {
            return 0;
        }

        @Override
        public void writeDataPort(long data) {

        }

        @Override
        public int readControl() {
            return 0;
        }

        @Override
        public void writeControlPort(long data) {

        }

        @Override
        public int getVCounter() {
            return 0;
        }

        @Override
        public int getHCounter() {
            return 0;
        }

        @Override
        public int getAddressRegister() {
            return 0;
        }

        @Override
        public void setAddressRegister(int value) {

        }

        @Override
        public boolean isIe0() {
            return false;
        }

        @Override
        public boolean isIe1() {
            return false;
        }

        @Override
        public void setDmaFlag(int value) {

        }

        @Override
        public boolean getVip() {
            return false;
        }

        @Override
        public void setVip(boolean value) {

        }

        @Override
        public boolean getHip() {
            return false;
        }

        @Override
        public void setHip(boolean value) {

        }

        @Override
        public boolean isShadowHighlight() {
            return false;
        }

        @Override
        public IVdpFifo getFifo() {
            return null;
        }

        @Override
        public VramMode getVramMode() {
            return null;
        }

        @Override
        public InterlaceMode getInterlaceMode() {
            return null;
        }

        @Override
        public void init() {

        }

        @Override
        public int runSlot() {
            return 0;
        }

        @Override
        public int getRegisterData(int reg) {
            return 0;
        }

        @Override
        public void updateRegisterData(int reg, int data) {

        }

        @Override
        public boolean isDisplayEnabled() {
            return false;
        }

        @Override
        public VideoMode getVideoMode() {
            return null;
        }

        @Override
        public VdpMemoryInterface getVdpMemory() {
            return null;
        }

        @Override
        public int[][] getScreenData() {
            return new int[0][];
        }

        @Override
        public void setRegion(RegionDetector.Region region) {

        }
    }
}
