/*
 * VdpTestUtil
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 14:30
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
import omegadrive.memory.IMemoryProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.RegionDetector;
import omegadrive.vdp.gen.VdpInterruptHandler;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.IVdpFifo;
import omegadrive.vdp.model.VdpMemoryInterface;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VdpTestUtil {


    static int VDP_SLOT_CYCLES = 2;


    public static void runCounterToStartFrame(VdpInterruptHandler h) {
        boolean isStart;
        do {
            h.increaseHCounter();
            isStart = h.gethCounterInternal() == 0 && h.getvCounterInternal() == 0;
        } while (!isStart);
        h.setHIntPending(false);
        h.setvIntPending(false);
    }

    public static void runToStartFrame(GenesisVdpProvider vdp) {
        do {
            vdp.run(VDP_SLOT_CYCLES);
        } while (!isVBlank(vdp));
        do {
            vdp.run(VDP_SLOT_CYCLES);
        } while (isVBlank(vdp));

        boolean isStart;
        do {
            vdp.run(VDP_SLOT_CYCLES);
            //TODO
            isStart = vdp.getHCounter() == 0 && vdp.getVCounter() == 0;
        } while (!isStart);
        vdp.setVip(false);
        vdp.setHip(false);
    }

    public static void runVdpSlot(GenesisVdpProvider vdp) {
        vdp.run(VDP_SLOT_CYCLES);
    }

    public static void runVdpUntilFifoEmpty(GenesisVdpProvider vdp) {
        IVdpFifo fifo = vdp.getFifo();
        while (!fifo.isEmpty()) {
            vdp.run(VDP_SLOT_CYCLES);
        }
    }

    public static int runVdpUntilDmaDone(GenesisVdpProvider vdp) {
        int slots = 0;
        boolean dmaDone;
        do {
            slots++;
            vdp.run(VDP_SLOT_CYCLES);
            dmaDone = (vdp.readControl() & 0x2) == 0;
        } while (!dmaDone);
        return slots;
    }

    public static void runVdpUntilVBlank(GenesisVdpProvider vdp) {
        //if we already are in vblank, run until vblank is over
        do {
            vdp.run(VDP_SLOT_CYCLES);
        } while (isVBlank(vdp));
        do {
            vdp.run(VDP_SLOT_CYCLES);
        } while (!isVBlank(vdp));
    }

    public static boolean isVBlank(GenesisVdpProvider vdp) {
        return (vdp.readControl() & 0x8) == 8;
    }

    public static boolean isHBlank(GenesisVdpProvider vdp) {
        return (vdp.readControl() & 0x4) == 4;
    }

    public static void setH32(GenesisVdpProvider vdp) {
        //        Set Video mode: PAL_H32_V28
        vdp.writeControlPort(0x8C00);
        vdp.resetVideoMode(true);
    }

    public static void setH40(GenesisVdpProvider vdp) {
        //        Set Video mode: PAL_H40_V28
        vdp.writeControlPort(0x8C81);
        vdp.resetVideoMode(true);
    }

    public static String printVdpMemory(VdpMemoryInterface memoryInterface, GenesisVdpProvider.VdpRamType type, int from, int to) {
        Function<Integer, Integer> getByteFn = addr -> {
            int word = memoryInterface.readVideoRamWord(type, addr);
            return addr % 2 == 0 ? word >> 8 : word & 0xFF;
        };
        Function<Integer, String> toStringFn = v -> {
            String s = Integer.toHexString(v).toUpperCase();
            return s.length() < 2 ? '0' + s : s;
        };
        return IntStream.range(from, to).mapToObj(addr -> toStringFn.apply(getByteFn.apply(addr))).
                collect(Collectors.joining(","));
    }

    public static String print68kMemory(IMemoryProvider memoryProvider, int from, int to) {
        Function<Integer, String> toStringFn = v -> {
            String s = Integer.toHexString(memoryProvider.readRamByte(v)).toUpperCase();
            return s.length() < 2 ? '0' + s : s;
        };
        return IntStream.range(from, to).mapToObj(addr -> toStringFn.apply(addr)).
                collect(Collectors.joining(","));
    }

    public static SystemProvider createTestGenesisProvider() {
        return new SystemProvider() {
            @Override
            public RegionDetector.Region getRegion() {
                return null;
            }

            @Override
            public void copyScreenData(int[][] screenData) {

            }

            @Override
            public void handleSystemEvent(SystemEvent event, Object parameter) {

            }

            @Override
            public boolean isRomRunning() {
                return false;
            }

            @Override
            public boolean isSoundWorking() {
                return false;
            }


            @Override
            public void init() {

            }

            @Override
            public String getRomName() {
                return null;
            }


            @Override
            public SystemLoader.SystemType getSystemType() {
                return SystemLoader.SystemType.GENESIS;
            }

            @Override
            public void reset() {

            }
        };
    }
}
