package omegadrive.vdp.model;

import java.awt.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 * <p>
 * http://bifi.msxnet.org/msxnet/tech/tms9918a.txt
 */
public interface Tms9918a extends BaseVdpProvider {
    enum TmsMode {
        MODE_0,
        MODE_1,
        MODE_2,
        MODE_3
    }

    enum TmsRegisterName {
        VIDEO_MODE_1, // 0
        VIDEO_MODE_2, // 1
        TILEMAP_NAMETABLE, //2
        COLORMAP_NAMETABLE, //3
        TILE_START_ADDRESS, //4
        SPRITE_TABLE_LOC, //5
        SPRITE_TILE_BASE_ADDR, //6
        BACKGROUND_COLOR, //7
    }

    Color[] colors = {
            (new Color(0, 0, 0, 0)),        // 0
            (new Color(0, 0, 0)),            // 1
            (new Color(33, 200, 66)),        // 2
            (new Color(94, 220, 120)),    // 3
            (new Color(84, 85, 237)),        // 4
            (new Color(125, 118, 252)),    // 5
            (new Color(212, 82, 77)),        // 6
            (new Color(66, 235, 245)),    // 7
            (new Color(252, 85, 84)),        // 8
            (new Color(255, 121, 120)),    // 9
            (new Color(212, 193, 84)),    // A
            (new Color(230, 206, 128)),    // B
            (new Color(33, 176, 59)),        // C
            (new Color(201, 91, 186)),    // D
            (new Color(204, 204, 204)),    // E
            (new Color(255, 255, 255)),    // F
    };

    int RAM_SIZE = 0xFFFF;
    int REGISTERS = 8;

    default int getRegisterData(TmsRegisterName registerName) {
        return getRegisterData(registerName.ordinal());
    }

    default void updateRegisterData(TmsRegisterName registerName, int data) {
        updateRegisterData(registerName.ordinal(), data);
    }
}
