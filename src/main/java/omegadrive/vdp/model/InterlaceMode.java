package omegadrive.vdp.model;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;

import java.util.EnumSet;
import java.util.Map;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public enum InterlaceMode {
    NONE,
    MODE_1,
    INVALID,
    MODE_2(6, 1);

    private int tileShift = 5;
    private int verticalScrollShift = 0;

    InterlaceMode() {
    }

    InterlaceMode(int tileShift, int verticalScrollShift) {
        this.tileShift = tileShift;
        this.verticalScrollShift = verticalScrollShift;
    }

    private static Map<Integer, InterlaceMode> lookup = ImmutableBiMap.copyOf(
            Maps.toMap(EnumSet.allOf(InterlaceMode.class), InterlaceMode::ordinal)).inverse();

    public static InterlaceMode getInterlaceMode(int index) {
        return lookup.get(index);
    }

    public int tileShift() {
        return tileShift;
    }

    public int verticalScrollShift() {
        return verticalScrollShift;
    }
}
