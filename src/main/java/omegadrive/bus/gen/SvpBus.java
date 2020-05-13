package omegadrive.bus.gen;

import omegadrive.cart.mapper.RomMapper;
import omegadrive.util.Size;

/**
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public interface SvpBus extends RomMapper {

    int SVP_ROM_START_ADDRESS_BYTE = 0x800;
    int SVP_ROM_START_ADDRESS_WORD = SVP_ROM_START_ADDRESS_BYTE >> 1;
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
        public long readData(long address, Size size) {
            return 0;
        }

        @Override
        public void writeData(long address, long data, Size size) {

        }

        @Override
        public void svpWriteDataWord(long addressWord, int data) {

        }

        @Override
        public int svpReadDataWord(long addressWord) {
            return 0;
        }

        @Override
        public long m68kSvpReadData(long address, Size size) {
            return 0;
        }

        @Override
        public void m68kSvpWriteData(long address, long data, Size size) {

        }
    };

    void svpWriteDataWord(long addressWord, int data);

    int svpReadDataWord(long addressWord);

    long m68kSvpReadData(long address, Size size);

    void m68kSvpWriteData(long address, long data, Size size);

    default long m68kSvpRegRead(int address, Size size) {
        return m68kSvpReadData(address, size);
    }

    default void m68kSvpRegWrite(int address, long data, Size size) {
        m68kSvpWriteData(address, data, size);
    }
}
