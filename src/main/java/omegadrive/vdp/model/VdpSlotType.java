package omegadrive.vdp.model;

import java.util.Arrays;
import java.util.stream.IntStream;

import static omegadrive.vdp.VdpProvider.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public enum VdpSlotType {
    NONE,
    EXTERNAL;

    @Deprecated
    public static VdpSlotType[] h32CounterSlots = new VdpSlotType[H32_PIXELS];
    @Deprecated
    public static VdpSlotType[] h40CounterSlots = new VdpSlotType[H40_PIXELS];

    public static VdpSlotType[] h32Slots = new VdpSlotType[H32_SLOTS];
    public static VdpSlotType[] h40Slots = new VdpSlotType[H40_SLOTS];

    private static int[] externalSlotsH32 = {
            32 * 0 + 14, 32 * 0 + 22, 32 * 0 + 30,
            32 * 1 + 14, 32 * 1 + 22, 32 * 1 + 30,
            32 * 2 + 14, 32 * 2 + 22, 32 * 2 + 30,
            32 * 3 + 14, 32 * 3 + 22, 32 * 3 + 30,
            141, 142, 156, 170
    };

    private static int[] externalSlotsH40 = {
            32 * 0 + 14, 32 * 0 + 22, 32 * 0 + 30,
            32 * 1 + 14, 32 * 1 + 22, 32 * 1 + 30,
            32 * 2 + 14, 32 * 2 + 22, 32 * 2 + 30,
            32 * 3 + 14, 32 * 3 + 22, 32 * 3 + 30,
            32 * 4 + 14, 32 * 4 + 22, 32 * 4 + 30,
            173, 174, 198
    };

    static {
        Arrays.fill(h32Slots, VdpSlotType.NONE);
        Arrays.fill(h40Slots, VdpSlotType.NONE);
        Arrays.fill(h32CounterSlots, VdpSlotType.NONE);
        Arrays.fill(h40CounterSlots, VdpSlotType.NONE);
        IntStream.of(externalSlotsH32).forEach(i -> h32Slots[i] = VdpSlotType.EXTERNAL);
        IntStream.of(externalSlotsH40).forEach(i -> h40Slots[i] = VdpSlotType.EXTERNAL);
        IntStream.of(externalSlotsH32).forEach(i -> h32CounterSlots[i * 2 + 1] = VdpSlotType.EXTERNAL);
        IntStream.of(externalSlotsH40).forEach(i -> h40CounterSlots[i * 2 + 1] = VdpSlotType.EXTERNAL);
    }
}
