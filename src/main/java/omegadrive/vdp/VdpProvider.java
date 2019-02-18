package omegadrive.vdp;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;
import omegadrive.bus.BusProvider;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.IVdpFifo;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.Map;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface VdpProvider {

    Logger LOG = LogManager.getLogger(VdpProvider.class.getSimpleName());

    enum VdpRamType {
        VRAM,
        CRAM,
        VSRAM;
    }

    //    Bits CD3-CD0
// 0000b : VRAM read
// 0001b : VRAM write
// 0011b : CRAM write
// 0100b : VSRAM read
// 0101b : VSRAM write
// 1000b : CRAM read
    enum VramMode {
        vramRead(0b0000, VdpRamType.VRAM),
        vramWrite(0b0001, VdpRamType.VRAM),
        cramWrite(0b0011, VdpRamType.CRAM),
        vsramRead(0b0100, VdpRamType.VSRAM),
        vsramWrite(0b0101, VdpRamType.VSRAM),
        cramRead(0b1000, VdpRamType.CRAM),
        vramRead_8bit(0b1100, VdpRamType.VRAM);

        private VdpRamType ramType;
        private int addressMode;

        VramMode(int addressMode, VdpRamType ramType) {
            this.ramType = ramType;
            this.addressMode = addressMode;
        }

        public static VramMode getVramMode(int addressMode, boolean verbose) {
            VramMode m = null;
            switch (addressMode) {
                case 0b0000:
                    m = VramMode.vramRead;
                    break;
                case 0b0001:
                    m = VramMode.vramWrite;
                    break;
                case 0b0011:
                    m = VramMode.cramWrite;
                    break;
                case 0b0100:
                    m = VramMode.vsramRead;
                    break;
                case 0b0101:
                    m = VramMode.vsramWrite;
                    break;
                case 0b1000:
                    m = VramMode.cramRead;
                    break;
                case 0b1100:
                    m = VramMode.vramRead_8bit;
                    break;
                default:
                    if (verbose) {
                        LOG.warn("Unexpected value: {}, vramMode is null", addressMode);
                    }
            }
            return m;
        }

        public static VramMode getVramMode(int addressMode) {
            return getVramMode(addressMode, false);
        }

        public int getAddressMode() {
            return addressMode;
        }

        public VdpRamType getRamType() {
            return ramType;
        }

        public boolean isWriteMode() {
            return this == vramWrite || this == vsramWrite || this == cramWrite;
        }
    }

    enum VdpRegisterName {
        MODE_1, // 0
        MODE_2, // 1
        PLANE_A_NAMETABLE, //2
        WINDOW_NAMETABLE, //3
        PLANE_B_NAMETABLE, //4
        SPRITE_TABLE_LOC, //5
        SPRITE_PATTERN_BASE_ADDR, //6
        BACKGROUND_COLOR, //7
        UNUSED1, //8
        UNUSED2, //9
        HCOUNTER_VALUE, //10 - 0xA
        MODE_3, // 11 - 0xB
        MODE_4, //12 - 0xC
        HORIZONTAL_SCROLL_DATA_LOC, //13 - 0xD
        NAMETABLE_PATTERN_BASE_ADDR, // 14 -0xE
        AUTO_INCREMENT, //15 - 0xF
        PLANE_SIZE, //16 - 0x10
        WINDOW_PLANE_HOR_POS, //17 - 0x11
        WINDOW_PLANE_VERT_POS, //18 - 0x12
        DMA_LENGTH_LOW, //19 - 0x13
        DMA_LENGTH_HIGH, //20 - 0x14
        DMA_SOURCE_LOW, //21 - 0x15
        DMA_SOURCE_MID, //22 - 0x16
        DMA_SOURCE_HIGH //23 - 0x17
        ;

        private static Map<Integer, VdpRegisterName> lookup = ImmutableBiMap.copyOf(
                Maps.toMap(EnumSet.allOf(VdpRegisterName.class), VdpRegisterName::ordinal)).inverse();

        public static VdpRegisterName getRegisterName(int index) {
            return lookup.get(index);
        }
    }

    enum VdpPortType {DATA, CONTROL}

    enum InterlaceMode {
        NONE,
        MODE_1,
        INVALID, MODE_2;
        private static Map<Integer, InterlaceMode> lookup = ImmutableBiMap.copyOf(
                Maps.toMap(EnumSet.allOf(InterlaceMode.class), InterlaceMode::ordinal)).inverse();

        public static InterlaceMode getInterlaceMode(int index) {
            return lookup.get(index);
        }
    }

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
    int V28_VBLANK_SET = 0xE0;
    int V30_VBLANK_SET = 0xF0;
    int H32_VCOUNTER_INC_ON = 0x10A;
    int H40_VCOUNTER_INC_ON = 0x14A;
    int V28_PAL_JUMP = 0x102;
    int V28_NTSC_JUMP = 0xEA;
    int V30_PAL_JUMP = 0x10A;
    int V30_NTSC_JUMP = -1; //never
    int H32_SLOTS = H32_PIXELS / 2;
    int H40_SLOTS = H40_PIXELS / 2;

    static VdpProvider createVdp(BusProvider bus) {
        return GenesisVdp.createInstance(bus);
    }

    void init();

    void run(int cycles);

    //always a word
    int readDataPort();

    void writeDataPort(long data);

    int readControl();

    void writeControlPort(long data);

    int getVCounter();

    int getHCounter();

    int getAddressRegister();

    void setAddressRegister(int value);

    boolean isIe0();

    boolean isIe1();

    int getRegisterData(int reg);

    void updateRegisterData(int reg, int data);

    void setDmaFlag(int value);

    /**
     * State of the Vertical interrupt pending flag
     *
     * @return
     */
    boolean getVip();

    /**
     * Set the Vertical interrupt pending flag
     * @return
     */
    void setVip(boolean value);

    /**
     * State of the Horizontal interrupt pending flag
     *
     * @return
     */
    boolean getHip();

    /**
     * Set the Horizontal interrupt pending flag
     *
     * @return
     */
    void setHip(boolean value);

    boolean isShadowHighlight();

    boolean isDisplayEnabled();

    VideoMode getVideoMode();

    IVdpFifo getFifo();

    VdpMemoryInterface getVdpMemory();

    VramMode getVramMode();

    InterlaceMode getInterlaceMode();

    default int getRegisterData(VdpRegisterName registerName) {
        return getRegisterData(registerName.ordinal());
    }

    default void updateRegisterData(VdpRegisterName registerName, int data) {
        updateRegisterData(registerName.ordinal(), data);
    }

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
