package omegadrive.vdp;

import omegadrive.SystemProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.RegionDetector;
import omegadrive.vdp.gen.VdpInterruptHandler;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.IVdpFifo;
import omegadrive.vdp.model.VdpMemoryInterface;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
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
            public void renderScreen(int[][] screenData) {

            }

            @Override
            public void handleNewRom(Path file) {

            }

            @Override
            public void handleCloseRom() {

            }

            @Override
            public void handleCloseApp() {

            }

            @Override
            public void handleLoadState(Path file) {

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
            public void toggleMute() {

            }

            @Override
            public void toggleSoundRecord() {

            }

            @Override
            public void setFullScreen(boolean value) {

            }

            @Override
            public void init() {

            }

            @Override
            public void setPlayers(int i) {

            }

            @Override
            public void setDebug(boolean value) {

            }

            @Override
            public String getRomName() {
                return null;
            }

            @Override
            public void handleSaveState(Path p) {

            }

            @Override
            public void handlePause() {

            }

            @Override
            public void reset() {

            }
        };
    }
}
