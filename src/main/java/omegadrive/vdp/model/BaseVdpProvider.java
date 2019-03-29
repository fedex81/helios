package omegadrive.vdp.model;

import omegadrive.Device;
import omegadrive.util.VideoMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface BaseVdpProvider extends Device {

    Logger LOG = LogManager.getLogger(BaseVdpProvider.class.getSimpleName());

    int MAX_SPRITES_PER_FRAME_H40 = 80;
    int MAX_SPRITES_PER_FRAME_H32 = 64;
    int MAX_SPRITES_PER_LINE_H40 = 20;
    int MAX_SPRITES_PER_LINE_H32 = 16;
    int VERTICAL_LINES_V30 = 240;
    int VERTICAL_LINES_V28 = 224;
    int VDP_VIDEO_ROWS = 256;
    int VDP_VIDEO_COLS = 320;

    //	The CRAM contains 128 bytes, addresses 0 to 7F
    int VDP_CRAM_SIZE = 0x80;

    //	The VSRAM contains 80 bytes, addresses 0 to 4F
    int VDP_VSRAM_SIZE = 0x50;

    int VDP_VRAM_SIZE = 0x10000;

    int VDP_REGISTERS_SIZE = 24;

    int V24_CELL = 192;
    int V28_CELL = 224;
    int V30_CELL = 240;
    int H40 = 320;
    int H32 = 256;
    int H32_TILES = H32 / 8;
    int H40_TILES = H40 / 8;

    //vdp counter data
    int PAL_SCANLINES = 313;
    int NTSC_SCANLINES = 262;
    int H32_PIXELS = 342;
    int H40_PIXELS = 420;
    int H32_JUMP = 0x127;
    int H40_JUMP = 0x16C;
    int H32_HBLANK_SET = 0x126;
    int H40_HBLANK_SET = 0x166;
    int H32_HBLANK_CLEAR = 0xA;
    int H40_HBLANK_CLEAR = 0xB;
    int V24_VBLANK_SET = 0xC0;
    int V28_VBLANK_SET = 0xE0;
    int V30_VBLANK_SET = 0xF0;
    int H32_VCOUNTER_INC_ON = 0x10A;
    int H40_VCOUNTER_INC_ON = 0x14A;
    int V24_NTSC_JUMP = 0xDA;
    int V28_PAL_JUMP = 0x102;
    int V28_NTSC_JUMP = 0xEA;
    int V30_PAL_JUMP = 0x10A;
    int V30_NTSC_JUMP = -1; //never
    int H32_SLOTS = H32_PIXELS / 2;
    int H40_SLOTS = H40_PIXELS / 2;

    void init();

    boolean run(int cycles);

    int getRegisterData(int reg);

    void updateRegisterData(int reg, int data);

    boolean isDisplayEnabled();

    VideoMode getVideoMode();

    VdpMemoryInterface getVdpMemory();

    int[][] getScreenData();

    //after loading a state
    default void reload() {
        //DO NOTHING
    }

    default void dumpScreenData() {
        throw new UnsupportedOperationException("Not supported");
    }

    default String getVdpStateString() {
        return "vdpState: unsupported";
    }

    default void resetVideoMode(boolean force) {
        throw new UnsupportedOperationException("Not supported");
    }

}
