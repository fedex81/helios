package omegadrive.bus.model;

import omegadrive.cart.mapper.RomMapper;
import omegadrive.util.Size;

/**
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public interface SvpBus extends RomMapper {

    int SVP_CMD_SENT_FLAG_BYTE = 0x30fe06;
    int SVP_CMD_ID_FLAG_BYTE = 0x30fe08;
    int SVP_MAP_DRAM_START_ADDR_WORD = 0x18_0000;
    int SVP_MAP_DRAM_START_ADDR_BYTE = SVP_MAP_DRAM_START_ADDR_WORD << 1;
    int SVP_MAP_DRAM_END_ADDR_WORD = 0x19_0000;
    int SVP_MAP_DRAM_END_ADDR_BYTE = SVP_MAP_DRAM_END_ADDR_WORD << 1;
    int SVP_MAP_IRAM_START_ADDR_WORD = 0x1C_8000;
    int SVP_MAP_IRAM_START_ADDR_BYTE = SVP_MAP_DRAM_START_ADDR_WORD << 1;
    int SVP_MAP_IRAM_END_ADDR_WORD = 0x1C_8400;
    int SVP_MAP_IRAM_END_ADDR_BYTE = SVP_MAP_DRAM_END_ADDR_WORD << 1;
    int SVP_MAP_DRAM_CELL_1_START_BYTE = 0x39_0000;
    int SVP_MAP_DRAM_CELL_1_START_WORD = SVP_MAP_DRAM_CELL_1_START_BYTE >> 1;
    int SVP_MAP_DRAM_CELL_2_START_BYTE = 0x3A_0000;
    int SVP_MAP_DRAM_CELL_2_START_WORD = SVP_MAP_DRAM_CELL_2_START_BYTE >> 1;
    int SVP_MAP_DRAM_CELL_1_END_BYTE = 0x3A_0000;
    int SVP_MAP_DRAM_CELL_1_END_WORD = SVP_MAP_DRAM_CELL_1_END_BYTE >> 1;
    int SVP_MAP_DRAM_CELL_2_END_BYTE = 0x3B_0000;
    int SVP_MAP_DRAM_CELL_2_END_WORD = SVP_MAP_DRAM_CELL_2_END_BYTE >> 1;


    SvpBus NO_OP = new SvpBus() {
        @Override
        public int readData(int address, Size size) {
            return 0;
        }

        @Override
        public void writeData(int address, int data, Size size) {

        }

        @Override
        public int m68kSvpReadData(int address, Size size) {
            return 0;
        }

        @Override
        public void m68kSvpWriteData(int address, int data, Size size) {

        }
    };

    int m68kSvpReadData(int address, Size size);

    void m68kSvpWriteData(int address, int data, Size size);

    default int m68kSvpRegRead(int address, Size size) {
        return m68kSvpReadData(address, size);
    }

    default void m68kSvpRegWrite(int address, int data, Size size) {
        m68kSvpWriteData(address, data, size);
    }
}
