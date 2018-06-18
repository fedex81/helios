package omegadrive.vdp;

import omegadrive.bus.BusProvider;
import omegadrive.util.Size;
import omegadrive.util.VideoMode;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface VdpProvider {

    int MAX_SPRITES_PER_FRAME_H40 = 80;
    int MAX_SPRITES_PER_FRAME_H32 = 64;
    int MAX_SPRITES_PER_LINE_H40 = 20;
    int MAX_SPRITES_PER_LiNE_H32 = 16;
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

    int V28_CELL = 224;
    int V30_CELL = 240;
    int H40 = 320;
    int H32 = 256;

    static VdpProvider createVdp(BusProvider bus) {
        return new GenesisVdp(bus);
    }

    void init();

    void run(int cycles);

    void dmaFill();

    long readDataPort(Size size);

    void writeDataPort(int data, Size size);

    int readControl();

    void writeControlPort(long data);

    int getVCounter();

    int getHCounter();

    boolean isIe0();

    boolean isIe1();

    int getVip();

    void setVip(int value);

    VideoMode getVideoMode();
}
