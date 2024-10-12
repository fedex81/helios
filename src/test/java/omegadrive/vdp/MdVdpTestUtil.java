/*
 * VdpTestUtil
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 12:47
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

import omegadrive.SystemLoader;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.system.BaseSystem;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider;
import omegadrive.util.RegionDetector;
import omegadrive.util.VideoMode;
import omegadrive.vdp.md.MdVdp;
import omegadrive.vdp.md.VdpFifo;
import omegadrive.vdp.md.VdpInterruptHandler;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.InterlaceMode;
import omegadrive.vdp.model.MdVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static omegadrive.util.Util.th;
import static omegadrive.vdp.model.MdVdpProvider.VdpPortType.CONTROL;
import static omegadrive.vdp.model.MdVdpProvider.VdpRegisterName.*;

public class MdVdpTestUtil {

    static final int DISP_EN_BIT_POS = 6;
    static final int DMA_EN_BIT_POS = 4;
    static final int MODE_5_BIT_POS = 2;
    static final int CTRL_PORT_MODE_2 = 0x8100;
    static final int CTRL_PORT_MODE_4 = 0x8C00;

    static Function<Integer, Integer> toMaskFn = bp -> 1 << bp;
    static Function<Integer, Integer> toResetMaskFn = bp -> ~(1 << bp);

    public static void runCounterToStartFrame(VdpInterruptHandler h) {
        boolean isStart;
        do {
            h.increaseHCounter();
            isStart = h.gethCounterInternal() == 0 && h.getvCounterInternal() == 0;
        } while (!isStart);
        h.setHIntPending(false);
        h.setvIntPending(false);
    }


    public static int runToStartNextLine(MdVdpProvider vdp) {
        boolean isStart;
        int cnt = 0;
        do {
            cnt++;
            vdp.runSlot();
            isStart = vdp.getHCounter() == 0;
        } while (!isStart);
        return cnt;
    }

    //NOTE: overwrites m3 (counter latch) to off
    public static void runToStartFrame(MdVdpProvider vdp) {
        do {
            vdp.runSlot();
        } while (!isVBlank(vdp));
        do {
            vdp.runSlot();
        } while (isVBlank(vdp));
        //TODO detect event START_FRAME
        int reg1 = vdp.getRegisterData(MODE_1);
        if ((reg1 & 2) > 0) {
            System.out.println("m3 (counter latch) is on!");
            vdp.updateRegisterData(MODE_1, reg1 & 0xFD);
        }
        runToCounter(vdp, 0, 0);
    }

    public static void runToCounter(MdVdpProvider vdp, int hCounter, int vCounter) {
        boolean isStart;
        do {
            vdp.runSlot();
            isStart = vdp.getHCounter() == hCounter && vdp.getVCounter() == vCounter;
        } while (!isStart);
        vdp.setVip(false);
        vdp.setHip(false);
    }

    public static void runVdpSlot(MdVdpProvider vdp) {
        vdp.runSlot();
    }

    public static void runVdpUntilFifoEmpty(MdVdpProvider vdp) {
        VdpFifo fifo = vdp.getFifo();
        while (!fifo.isEmpty()) {
            vdp.runSlot();
        }
    }

    public static int runVdpUntilDmaDone(MdVdpProvider vdp) {
        int slots = 0;
        boolean dmaDone;
        do {
            slots++;
            vdp.runSlot();
            dmaDone = (vdp.readVdpPortWord(CONTROL) & 0x2) == 0;
        } while (!dmaDone);
        return slots;
    }

    public static void runVdpUntilVBlank(MdVdpProvider vdp) {
        //if we already are in vblank, run until vblank is over
        do {
            vdp.runSlot();
        } while (isVBlank(vdp));
        do {
            vdp.runSlot();
        } while (!isVBlank(vdp));
    }

    public static boolean isVBlank(MdVdpProvider vdp) {
        return (vdp.readVdpPortWord(CONTROL) & 0x8) == 8;
    }

    public static boolean isHBlank(MdVdpProvider vdp) {
        return (vdp.readVdpPortWord(CONTROL) & 0x4) == 4;
    }

    public static void setH32(MdVdpProvider vdp) {
        //        Set Video mode: PAL_H32_V28
        int val = vdp.getRegisterData(MODE_4);
        vdp.writeControlPort(CTRL_PORT_MODE_4 | (val & 0x7E));
        vdp.resetVideoMode(true);
    }

    public static void setH40(MdVdpProvider vdp) {
        //        Set Video mode: PAL_H40_V28
        int val = vdp.getRegisterData(MODE_4);
        vdp.writeControlPort(CTRL_PORT_MODE_4 | (val | 0x81));
        vdp.resetVideoMode(true);
    }

    public static void vdpMode5(MdVdpProvider vdp) {
        int val = vdp.getRegisterData(MODE_2) | toMaskFn.apply(MODE_5_BIT_POS);
        vdp.writeControlPort(CTRL_PORT_MODE_2 | val);
    }

    public static void vdpDisplayEnable(MdVdpProvider vdp, boolean en) {
        int val = vdp.getRegisterData(MODE_2) & toResetMaskFn.apply(DISP_EN_BIT_POS);
        int den = en ? toMaskFn.apply(DISP_EN_BIT_POS) : 0;
        vdp.writeControlPort(CTRL_PORT_MODE_2 | (val | den));
    }

    public static void vdpDisplayEnableAndMode5(MdVdpProvider vdp) {
        vdpDisplayEnable(vdp, true);
        vdpMode5(vdp);
    }

    public static void vdpEnableDma(MdVdpProvider vdp, boolean en) {
        int val = vdp.getRegisterData(MODE_2) & toResetMaskFn.apply(DMA_EN_BIT_POS);
        int den = en ? toMaskFn.apply(DMA_EN_BIT_POS) : 0;
        vdp.writeControlPort(CTRL_PORT_MODE_2 | (val | den));
    }

    public static String printVdpMemory(VdpMemoryInterface memoryInterface, MdVdpProvider.VdpRamType type, int from, int to) {
        Function<Integer, Integer> getByteFn = addr -> {
            int word = memoryInterface.readVideoRamWord(type, addr);
            return addr % 2 == 0 ? word >> 8 : word & 0xFF;
        };
        Function<Integer, String> toStringFn = v -> {
            String s = th(v).toUpperCase();
            return s.length() < 2 ? '0' + s : s;
        };
        return IntStream.range(from, to).mapToObj(addr -> toStringFn.apply(getByteFn.apply(addr))).
                collect(Collectors.joining(","));
    }

    public static String print68kMemory(IMemoryProvider memoryProvider, int from, int to) {
        Function<Integer, String> toStringFn = v -> {
            String s = th(memoryProvider.readRamByte(v)).toUpperCase();
            return s.length() < 2 ? '0' + s : s;
        };
        return IntStream.range(from, to).mapToObj(addr -> toStringFn.apply(addr)).
                collect(Collectors.joining(","));
    }

    public final static VideoMode[] holder = {null};

    public static void updateHCounter(BaseVdpProvider vdp, int hLineCounter) {
        vdp.updateRegisterData(HCOUNTER_VALUE.ordinal(), hLineCounter);
    }

    public static void updateVideoMode(BaseVdpProvider vdp, VideoMode videoMode) {
        MdVdpTestUtil.holder[0] = videoMode;
        vdp.updateRegisterData(0, 0);
    }

    public static MdVdp getVdpProvider(SystemProvider systemProvider) throws Exception {
        BaseSystem system = (BaseSystem) systemProvider;
        Field f = BaseSystem.class.getDeclaredField("vdp");
        f.setAccessible(true);
        return (MdVdp) f.get(system);
    }

    private void dumpTiles() {
        int cnt = 0;
        int start = 47744;
        System.out.print(start + ":");
        for (int i = start; i < 48500; i++) {
            int cramIdx = 0; //getPixelIndexColor(i, 0 , 0);
            int cramIdx2 = 0; //getPixelIndexColor(i, 1 , 0);
            if (cnt > 0 && cnt % 8 == 0) {
                if (cnt % (8 * 16) == 0) {
                    System.out.println();
                }
                System.out.println();
                System.out.print(i + ":");
            }
            System.out.printf("%2d %2d ", cramIdx, cramIdx2);
            cnt += 2;
        }
        System.out.println();
    }

    public static BaseVdpProvider createBaseTestVdp() {
        BaseVdpProvider vdp = new MdVdpTestUtil.VdpAdaptor() {

            private List<VdpEventListener> list = new ArrayList<>();
            private int[] vdpReg = new int[24];

            @Override
            public List<VdpEventListener> getVdpEventListenerList() {
                return list;
            }

            @Override
            public VideoMode getVideoMode() {
                return holder[0];
            }

            @Override
            public int getRegisterData(int reg) {
                return vdpReg[reg];
            }

            @Override
            public void updateRegisterData(int reg, int data) {
                if (reg == HCOUNTER_VALUE.ordinal()) {
                    fireVdpEvent(VdpEvent.REG_H_LINE_COUNTER_CHANGE, data);
                } else if (reg < 2) {
                    fireVdpEvent(VdpEvent.VIDEO_MODE, holder[0]);
                }
                vdpReg[reg] = data;
            }
        };
        return vdp;
    }

    public static SystemProvider createTestMdProvider() {
        return createTestMdProvider(MemoryProvider.createMdInstance());
    }

    public static SystemProvider createTestMdProvider(IMemoryProvider memoryProvider) {
        return new SystemProvider() {

            private RomContext romContext;

            {
                romContext = new RomContext(SysUtil.RomSpec.NO_ROM);
                romContext.cartridgeInfoProvider = MdCartInfoProvider.createMdInstance(memoryProvider, RomContext.NO_ROM);
            }

            @Override
            public void handleSystemEvent(SystemEvent event, Object parameter) {

            }

            @Override
            public boolean isRomRunning() {
                return false;
            }

            @Override
            public RomContext getRomContext() {
                return romContext;
            }

            @Override
            public SystemLoader.SystemType getSystemType() {
                return SystemLoader.SystemType.MD;
            }

        };
    }


    public static class VdpAdaptor implements MdVdpProvider {

        @Override
        public void writeVdpPortWord(VdpPortType type, int data) {
        }

        @Override
        public int readVdpPortWord(VdpPortType type) {
            return 0;
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
        public VdpFifo getFifo() {
            return null;
        }

        @Override
        public VramMode getVramMode() {
            return null;
        }

        @Override
        public InterlaceMode getInterlaceMode() {
            return InterlaceMode.NONE;
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
        public VideoMode getVideoMode() {
            return null;
        }

        @Override
        public VdpMemoryInterface getVdpMemory() {
            return null;
        }

        @Override
        public void setRegion(RegionDetector.Region region) {
        }

        @Override
        public boolean isDisplayEnabled() {
            return false;
        }

        @Override
        public int[] getScreenDataLinear() {
            return new int[0];
        }
    }
}
