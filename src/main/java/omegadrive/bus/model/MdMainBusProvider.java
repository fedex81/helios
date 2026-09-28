/*
 * MdBusProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:16
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

package omegadrive.bus.model;

import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.LogHelper;
import omegadrive.vdp.model.MdVdpProvider;
import org.slf4j.Logger;

import static omegadrive.memory.MemoryProvider.M68K_RAM_SIZE;

/**
 * A bus with M68K, Z80 and Vdp connected and a busArbiter
 */
public interface MdMainBusProvider extends MdM68kBusProvider, Z80BusProvider {

    int[] EMPTY = new int[0];
    //http://gendev.spritesmind.net/forum/viewtopic.php?f=25&t=1283
    int Z80_ADDRESS_SPACE_START = 0xA00000;
    int Z80_ADDRESS_SPACE_END = 0xA0FFFF;
    int IO_ADDRESS_SPACE_START = 0xA10000;
    int IO_ADDRESS_SPACE_END = 0xA10FFF;
    int INTERNAL_REG_ADDRESS_SPACE_START = 0xA11000;
    int INTERNAL_REG_ADDRESS_SPACE_END = 0xBFFFFF;
    int MEMORY_MODE_START = 0xA11000; //DRAM control reg.
    int MEMORY_MODE_END = 0xA110FF;
    int Z80_BUS_REQ_CONTROL_START = 0xA11100;
    int Z80_BUS_REQ_CONTROL_END = 0xA111FF;
    int Z80_RESET_CONTROL_START = 0xA11200;
    int Z80_RESET_CONTROL_END = 0xA112FF;
    int MEGA_CD_EXP_START = 0xA12000;
    int MEGA_CD_EXP_END = 0xA120FF;
    int TIME_LINE_START = 0xA13000;
    int SRAM_LOCK = 0xA130F1;
    int TIME_LINE_END = 0xA130FF;
    int TMSS_AREA1_START = 0xA14000;
    int TMSS_AREA1_END = 0xA14003;
    int TMSS_AREA2_START = 0xA14100;
    int TMSS_AREA2_END = 0xA14101;
    int SVP_REG_AREA_START = 0xA15000;
    int SVP_REG_AREA_END = 0xA15008;
    int VDP_ADDRESS_SPACE_START = 0xC00000;
    int VDP_ADDRESS_SPACE_END = 0xDFFFFF;
    int ADDRESS_UPPER_LIMIT = 0xFFFFFF;
    int ADDRESS_RAM_MAP_START = 0xE00000;
    int M68K_TO_Z80_MEMORY_MASK = 0x7FFF;
    int VDP_VALID_ADDRESS_MASK = 0xE700E0;

    int DEFAULT_ROM_END_ADDRESS = 0x3F_FFFF;

    int M68K_RAM_MASK = M68K_RAM_SIZE - 1;

    int NUM_MAPPER_BANKS = 8;

    Logger LOG = LogHelper.getLogger(MdMainBusProvider.class.getSimpleName());


    /**
     * 0, REG_ROM(7)
     * 10000, REG_ROM(7)
     * 20000, REG_ROM(7)
     * 30000, REG_ROM(7)
     * 40000, REG_ROM(7)
     * 50000, REG_ROM(7)
     * 60000, REG_ROM(7)
     * 70000, REG_ROM(7)
     * 80000, REG_ROM(7)
     * 90000, REG_ROM(7)
     * a0000, REG_ROM(7)
     * b0000, REG_ROM(7)
     * c0000, REG_ROM(7)
     * d0000, REG_ROM(7)
     * e0000, REG_ROM(7)
     * f0000, REG_ROM(7)
     * 100000, REG_ROM(7)
     * 110000, REG_ROM(7)
     * 120000, REG_ROM(7)
     * 130000, REG_ROM(7)
     * 140000, REG_ROM(7)
     * 150000, REG_ROM(7)
     * 160000, REG_ROM(7)
     * 170000, REG_ROM(7)
     * 180000, REG_ROM(7)
     * 190000, REG_ROM(7)
     * 1a0000, REG_ROM(7)
     * 1b0000, REG_ROM(7)
     * 1c0000, REG_ROM(7)
     * 1d0000, REG_ROM(7)
     * 1e0000, REG_ROM(7)
     * 1f0000, REG_ROM(7)
     * 200000, REG_ROM(7)
     * 210000, REG_ROM(7)
     * 220000, REG_ROM(7)
     * 230000, REG_ROM(7)
     * 240000, REG_ROM(7)
     * 250000, REG_ROM(7)
     * 260000, REG_ROM(7)
     * 270000, REG_ROM(7)
     * 280000, REG_ROM(7)
     * 290000, REG_ROM(7)
     * 2a0000, REG_ROM(7)
     * 2b0000, REG_ROM(7)
     * 2c0000, REG_ROM(7)
     * 2d0000, REG_ROM(7)
     * 2e0000, REG_ROM(7)
     * 2f0000, REG_ROM(7)
     * 300000, REG_ROM(7)
     * 310000, REG_ROM(7)
     * 320000, REG_ROM(7)
     * 330000, REG_ROM(7)
     * 340000, REG_ROM(7)
     * 350000, REG_ROM(7)
     * 360000, REG_ROM(7)
     * 370000, REG_ROM(7)
     * 380000, REG_ROM(7)
     * 390000, REG_ROM(7)
     * 3a0000, REG_ROM(7)
     * 3b0000, REG_ROM(7)
     * 3c0000, REG_ROM(7)
     * 3d0000, REG_ROM(7)
     * 3e0000, REG_ROM(7)
     * 3f0000, REG_ROM(7)
     * 400000, REG_RESERVED(0)
     * 410000, REG_RESERVED(0)
     * 420000, REG_RESERVED(0)
     * 430000, REG_RESERVED(0)
     * 440000, REG_RESERVED(0)
     * 450000, REG_RESERVED(0)
     * 460000, REG_RESERVED(0)
     * 470000, REG_RESERVED(0)
     * 480000, REG_RESERVED(0)
     * 490000, REG_RESERVED(0)
     * 4a0000, REG_RESERVED(0)
     * 4b0000, REG_RESERVED(0)
     * 4c0000, REG_RESERVED(0)
     * 4d0000, REG_RESERVED(0)
     * 4e0000, REG_RESERVED(0)
     * 4f0000, REG_RESERVED(0)
     * 500000, REG_RESERVED(0)
     * 510000, REG_RESERVED(0)
     * 520000, REG_RESERVED(0)
     * 530000, REG_RESERVED(0)
     * 540000, REG_RESERVED(0)
     * 550000, REG_RESERVED(0)
     * 560000, REG_RESERVED(0)
     * 570000, REG_RESERVED(0)
     * 580000, REG_RESERVED(0)
     * 590000, REG_RESERVED(0)
     * 5a0000, REG_RESERVED(0)
     * 5b0000, REG_RESERVED(0)
     * 5c0000, REG_RESERVED(0)
     * 5d0000, REG_RESERVED(0)
     * 5e0000, REG_RESERVED(0)
     * 5f0000, REG_RESERVED(0)
     * 600000, REG_RESERVED(0)
     * 610000, REG_RESERVED(0)
     * 620000, REG_RESERVED(0)
     * 630000, REG_RESERVED(0)
     * 640000, REG_RESERVED(0)
     * 650000, REG_RESERVED(0)
     * 660000, REG_RESERVED(0)
     * 670000, REG_RESERVED(0)
     * 680000, REG_RESERVED(0)
     * 690000, REG_RESERVED(0)
     * 6a0000, REG_RESERVED(0)
     * 6b0000, REG_RESERVED(0)
     * 6c0000, REG_RESERVED(0)
     * 6d0000, REG_RESERVED(0)
     * 6e0000, REG_RESERVED(0)
     * 6f0000, REG_RESERVED(0)
     * 700000, REG_RESERVED(0)
     * 710000, REG_RESERVED(0)
     * 720000, REG_RESERVED(0)
     * 730000, REG_RESERVED(0)
     * 740000, REG_RESERVED(0)
     * 750000, REG_RESERVED(0)
     * 760000, REG_RESERVED(0)
     * 770000, REG_RESERVED(0)
     * 780000, REG_RESERVED(0)
     * 790000, REG_RESERVED(0)
     * 7a0000, REG_RESERVED(0)
     * 7b0000, REG_RESERVED(0)
     * 7c0000, REG_RESERVED(0)
     * 7d0000, REG_RESERVED(0)
     * 7e0000, REG_RESERVED(0)
     * 7f0000, REG_RESERVED(0)
     * 800000, REG_RESERVED(0)
     * 810000, REG_RESERVED(0)
     * 820000, REG_RESERVED(0)
     * 830000, REG_RESERVED(0)
     * 840000, REG_RESERVED(0)
     * 850000, REG_RESERVED(0)
     * 860000, REG_RESERVED(0)
     * 870000, REG_RESERVED(0)
     * 880000, REG_RESERVED(0)
     * 890000, REG_RESERVED(0)
     * 8a0000, REG_RESERVED(0)
     * 8b0000, REG_RESERVED(0)
     * 8c0000, REG_RESERVED(0)
     * 8d0000, REG_RESERVED(0)
     * 8e0000, REG_RESERVED(0)
     * 8f0000, REG_RESERVED(0)
     * 900000, REG_RESERVED(0)
     * 910000, REG_RESERVED(0)
     * 920000, REG_RESERVED(0)
     * 930000, REG_RESERVED(0)
     * 940000, REG_RESERVED(0)
     * 950000, REG_RESERVED(0)
     * 960000, REG_RESERVED(0)
     * 970000, REG_RESERVED(0)
     * 980000, REG_RESERVED(0)
     * 990000, REG_RESERVED(0)
     * 9a0000, REG_RESERVED(0)
     * 9b0000, REG_RESERVED(0)
     * 9c0000, REG_RESERVED(0)
     * 9d0000, REG_RESERVED(0)
     * 9e0000, REG_RESERVED(0)
     * 9f0000, REG_RESERVED(0)
     * a00000, REG_Z80(1)
     * a10000, REG_IO_OR_REG(2)
     * a20000, REG_INTERNAL_REG(3)
     * a30000, REG_INTERNAL_REG(3)
     * a40000, REG_INTERNAL_REG(3)
     * a50000, REG_INTERNAL_REG(3)
     * a60000, REG_INTERNAL_REG(3)
     * a70000, REG_INTERNAL_REG(3)
     * a80000, REG_INTERNAL_REG(3)
     * a90000, REG_INTERNAL_REG(3)
     * aa0000, REG_INTERNAL_REG(3)
     * ab0000, REG_INTERNAL_REG(3)
     * ac0000, REG_INTERNAL_REG(3)
     * ad0000, REG_INTERNAL_REG(3)
     * ae0000, REG_INTERNAL_REG(3)
     * af0000, REG_INTERNAL_REG(3)
     * b00000, REG_INTERNAL_REG(3)
     * b10000, REG_INTERNAL_REG(3)
     * b20000, REG_INTERNAL_REG(3)
     * b30000, REG_INTERNAL_REG(3)
     * b40000, REG_INTERNAL_REG(3)
     * b50000, REG_INTERNAL_REG(3)
     * b60000, REG_INTERNAL_REG(3)
     * b70000, REG_INTERNAL_REG(3)
     * b80000, REG_INTERNAL_REG(3)
     * b90000, REG_INTERNAL_REG(3)
     * ba0000, REG_INTERNAL_REG(3)
     * bb0000, REG_INTERNAL_REG(3)
     * bc0000, REG_INTERNAL_REG(3)
     * bd0000, REG_INTERNAL_REG(3)
     * be0000, REG_INTERNAL_REG(3)
     * bf0000, REG_INTERNAL_REG(3)
     * c00000, REG_VDP(4)
     * c10000, REG_VDP(4)
     * c20000, REG_VDP(4)
     * c30000, REG_VDP(4)
     * c40000, REG_VDP(4)
     * c50000, REG_VDP(4)
     * c60000, REG_VDP(4)
     * c70000, REG_VDP(4)
     * c80000, REG_VDP(4)
     * c90000, REG_VDP(4)
     * ca0000, REG_VDP(4)
     * cb0000, REG_VDP(4)
     * cc0000, REG_VDP(4)
     * cd0000, REG_VDP(4)
     * ce0000, REG_VDP(4)
     * cf0000, REG_VDP(4)
     * d00000, REG_VDP(4)
     * d10000, REG_VDP(4)
     * d20000, REG_VDP(4)
     * d30000, REG_VDP(4)
     * d40000, REG_VDP(4)
     * d50000, REG_VDP(4)
     * d60000, REG_VDP(4)
     * d70000, REG_VDP(4)
     * d80000, REG_VDP(4)
     * d90000, REG_VDP(4)
     * da0000, REG_VDP(4)
     * db0000, REG_VDP(4)
     * dc0000, REG_VDP(4)
     * dd0000, REG_VDP(4)
     * de0000, REG_VDP(4)
     * df0000, REG_VDP(4)
     * e00000, REG_RAM(5)
     * e10000, REG_RAM(5)
     * e20000, REG_RAM(5)
     * e30000, REG_RAM(5)
     * e40000, REG_RAM(5)
     * e50000, REG_RAM(5)
     * e60000, REG_RAM(5)
     * e70000, REG_RAM(5)
     * e80000, REG_RAM(5)
     * e90000, REG_RAM(5)
     * ea0000, REG_RAM(5)
     * eb0000, REG_RAM(5)
     * ec0000, REG_RAM(5)
     * ed0000, REG_RAM(5)
     * ee0000, REG_RAM(5)
     * ef0000, REG_RAM(5)
     * f00000, REG_RAM(5)
     * f10000, REG_RAM(5)
     * f20000, REG_RAM(5)
     * f30000, REG_RAM(5)
     * f40000, REG_RAM(5)
     * f50000, REG_RAM(5)
     * f60000, REG_RAM(5)
     * f70000, REG_RAM(5)
     * f80000, REG_RAM(5)
     * f90000, REG_RAM(5)
     * fa0000, REG_RAM(5)
     * fb0000, REG_RAM(5)
     * fc0000, REG_RAM(5)
     * fd0000, REG_RAM(5)
     * fe0000, REG_RAM(5)
     * ff0000, REG_RAM(5)
     */
    enum MdBusRegion {
        REG_RESERVED, REG_Z80, REG_IO_OR_REG, REG_INTERNAL_REG, REG_VDP, REG_RAM, REG_UNMAPPED, REG_ROM;
    }

    //NOTE can't use REG_RESERVED.ordinal() as a switch would require a constant expression
    byte REGION_RESERVED = 0;
    byte REGION_Z80 = 1;
    byte REGION_IO_OR_REG = 2;
    byte REGION_INTERNAL_REG = 3;
    final byte REGION_VDP = 4;
    byte REGION_RAM = 5;
    byte REGION_UNMAPPED = 6;

    //ROM, SRAM, gets adjusted at runtime based on the rom
    byte REGION_ROM = 7;

    default void buildRegionTable(byte[] regionByPage) {
        for (int page = 0; page < regionByPage.length; page++) {
            int base = page << 16;
            byte region;
            if (base > DEFAULT_ROM_END_ADDRESS && base < Z80_ADDRESS_SPACE_START) {
                region = REGION_RESERVED;
            } else if (base >= Z80_ADDRESS_SPACE_START && base <= Z80_ADDRESS_SPACE_END) {
                region = REGION_Z80;
            } else if (base == (IO_ADDRESS_SPACE_START & 0xFF_0000)) {
                region = REGION_IO_OR_REG;
            } else if (base >= INTERNAL_REG_ADDRESS_SPACE_START && base <= INTERNAL_REG_ADDRESS_SPACE_END) {
                region = REGION_INTERNAL_REG;
            } else if (base >= VDP_ADDRESS_SPACE_START && base <= VDP_ADDRESS_SPACE_END) {
                region = REGION_VDP;
            } else if (base >= ADDRESS_RAM_MAP_START && base <= ADDRESS_UPPER_LIMIT) {
                region = REGION_RAM;
            } else if (base < DEFAULT_ROM_END_ADDRESS) {
                region = REGION_ROM;
            } else {
                region = REGION_UNMAPPED;
            }
            regionByPage[page] = region;
//            System.out.println(th(base) + ", " + MdBusRegion.values()[region] + "(" + region + ")");
        }
    }


    void handleVdpInterruptsZ80();


    /**
     * VRES is fed to 68000 for 128 VCLKs (16.7us); ZRES is fed
     * to the z80 and ym2612, and remains asserted until the 68000 does something to
     * deassert it; VDP and IO chip are unaffected.
     */
    void resetFrom68k();

    void setVdpBusyState(MdVdpProvider.VdpBusyState state);

    boolean isZ80Running();

    boolean isZ80ResetState();

    boolean isZ80BusRequested();

    void setZ80ResetState(boolean z80ResetState);

    void setZ80BusRequested(boolean z80BusRequested);

    PsgProvider getPsg();

    FmProvider getFm();

    SystemProvider getSystem();

    MdVdpProvider getVdp();

    default int getOpenBusWord() {
        throw new RuntimeException("Open bus not supported");
    }

    default int[] getMapperData() {
        return EMPTY;
    }

    default void setMapperData(int[] data) {
        //DO NOTHING
    }

    //Z80 for genesis doesnt do IO
    default int readIoPort(int port) {
        //TF4 calls this by mistake
        //LOG.debug("inPort: {}", port);
        return 0xFF;
    }

    //Z80 for genesis doesnt do IO
    default void writeIoPort(int port, int value) {
        LOG.warn("outPort: {}, data: {}", port, value);
    }

    default void handleInterrupts(Z80Provider.Interrupt type) {
        LOG.warn("Ignoring interrupt: {}", type);
    }

    default boolean isSvp() {
        return false;
    }


    MdCartInfoProvider getCartridgeInfoProvider();
}
