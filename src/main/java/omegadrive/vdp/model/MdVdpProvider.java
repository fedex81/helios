/*
 * MdVdpProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 28/05/19 17:15
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

package omegadrive.vdp.model;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.util.LogHelper;
import omegadrive.vdp.md.MdVdp;
import omegadrive.vdp.md.VdpFifo;
import org.slf4j.Logger;

public interface MdVdpProvider extends BaseVdpProvider {

    Logger LOG = LogHelper.getLogger(MdVdpProvider.class.getSimpleName());

    int MAX_SPRITES_PER_FRAME_H40 = 80;
    int MAX_SPRITES_PER_FRAME_H32 = 64;
    int MAX_SPRITES_PER_LINE_H40 = 20;
    int MAX_SPRITES_PER_LINE_H32 = 16;
    int VDP_VIDEO_COLS = 320;

    //	The CRAM contains 128 bytes, addresses 0 to 7F
    int VDP_CRAM_SIZE = 0x80;

    int VDP_CRAM_MASK = VDP_CRAM_SIZE - 1;

    //	The VSRAM contains 80 bytes, addresses 0 to 4F
    int VDP_VSRAM_SIZE = 0x50;

    int VDP_VRAM_SIZE = 0x10000;

    int VDP_VRAM_MASK = VDP_VRAM_SIZE - 1;

    int VDP_REGISTERS_SIZE = 24;

    enum VdpRamType {
        VRAM,
        CRAM,
        VSRAM
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

        private final VdpRamType ramType;
        private final int addressMode;

        private static final VramMode[] lookup = new VramMode[0x10];

        static {
            for (VramMode v : VramMode.values()) {
                lookup[v.addressMode] = v;
            }
        }

        VramMode(int addressMode, VdpRamType ramType) {
            this.ramType = ramType;
            this.addressMode = addressMode;
        }

        public static VramMode getVramMode(int addressMode, boolean verbose) {
            VramMode m = lookup[addressMode];
            if (verbose && m == null) {
                LOG.warn("Unexpected value: {}, vramMode is null", addressMode);
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

        private static final VdpRegisterName[] lookup = VdpRegisterName.values();

        public static VdpRegisterName getRegisterName(int index) {
            return lookup[index];
        }
    }

    enum VdpPortType {DATA, CONTROL}

    enum VdpBusyState {
        MEM_TO_VRAM, VRAM_FILL,
        VRAM_COPY, FIFO_FULL, NOT_BUSY;

        private static final VdpBusyState[] values = VdpBusyState.values();

        public static VdpBusyState getVdpBusyState(VdpDmaHandler.DmaMode mode) {
            if (mode == null) {
                return NOT_BUSY;
            }
            return values[mode.ordinal()];
        }
    }


    static MdVdpProvider createVdp(MdMainBusProvider bus) {
        return MdVdp.createInstance(bus);
    }

    //write a word
    void writeVdpPortWord(VdpPortType type, int data);

    int readVdpPortWord(VdpPortType type);

    int getVCounter();

    int getHCounter();

    int getAddressRegister();

    void setAddressRegister(int value);

    void setDmaFlag(int value);

    /**
     * State of the Vertical interrupt pending flag
     *
     * @return
     */
    boolean getVip();

    /**
     * Set the Vertical interrupt pending flag
     *
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

    VdpFifo getFifo();

    VramMode getVramMode();

    InterlaceMode getInterlaceMode();

    boolean isDisplayEnabled();

    default int getRegisterData(VdpRegisterName registerName) {
        return getRegisterData(registerName.ordinal());
    }

    default void updateRegisterData(VdpRegisterName registerName, int data) {
        updateRegisterData(registerName.ordinal(), data);
    }

    default void writeControlPort(int data) {
        writeVdpPortWord(VdpPortType.CONTROL, data);
    }

    default void writeDataPort(int data) {
        writeVdpPortWord(VdpPortType.DATA, data);
    }

    default void fifoPush(int addressRegister, int data) {
        getFifo().push(getVramMode(), addressRegister, data);
    }
}
