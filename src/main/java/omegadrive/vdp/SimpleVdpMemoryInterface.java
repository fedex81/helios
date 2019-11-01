package omegadrive.vdp;

import omegadrive.vdp.model.VdpMemory;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class SimpleVdpMemoryInterface implements VdpMemory {

    private int[] cram;
    private int[] vram;

    public static VdpMemory createInstance(int vramSize) {
        SimpleVdpMemoryInterface i = new SimpleVdpMemoryInterface();
        i.vram = new int[vramSize];
        return i;
    }

    public static VdpMemory createInstance(int vramSize, int cramSize) {
        SimpleVdpMemoryInterface i = new SimpleVdpMemoryInterface();
        i.vram = new int[vramSize];
        i.cram = new int[cramSize];
        return i;
    }

    @Override
    public int[] getCram() {
        return cram;
    }

    @Override
    public int[] getVram() {
        return vram;
    }
}
