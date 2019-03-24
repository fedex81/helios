package omegadrive.vdp.model;

import java.util.Arrays;
import java.util.stream.IntStream;

import static omegadrive.vdp.model.GenesisVdpProvider.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public enum VdpSlotType {
    NONE,
    REFRESH,
    EXTERNAL;

    @Deprecated
    public static VdpSlotType[] h32CounterSlots = new VdpSlotType[H32_PIXELS];
    @Deprecated
    public static VdpSlotType[] h40CounterSlots = new VdpSlotType[H40_PIXELS];

    public static VdpSlotType[] h32Slots = new VdpSlotType[H32_SLOTS];
    public static VdpSlotType[] h40Slots = new VdpSlotType[H40_SLOTS];

    private static int[] refreshSlotsH32 = {
            32 * 0 + 38,
            32 * 1 + 38,
            32 * 2 + 38,
            32 * 3 + 38,
    };

    private static int[] refreshSlotsH40 = {
            32 * 0 + 38,
            32 * 1 + 38,
            32 * 2 + 38,
            32 * 3 + 38,
            32 * 4 + 38,
    };

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
        IntStream.of(externalSlotsH32).forEach(i -> h32Slots[i] = VdpSlotType.EXTERNAL);
        IntStream.of(externalSlotsH40).forEach(i -> h40Slots[i] = VdpSlotType.EXTERNAL);
        IntStream.of(refreshSlotsH32).forEach(i -> h32Slots[i] = VdpSlotType.REFRESH);
        IntStream.of(refreshSlotsH40).forEach(i -> h40Slots[i] = VdpSlotType.REFRESH);
    }
}
