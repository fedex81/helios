package omegadrive.cart.mapper.md.eeprom;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import static omegadrive.cart.mapper.md.eeprom.SpiEeprom.LINE_STATE.ASSERT_LINE;
import static omegadrive.cart.mapper.md.eeprom.SpiEeprom.LINE_STATE.CLEAR_LINE;
import static omegadrive.cart.mapper.md.eeprom.SpiEeprom.STMSTATE.*;
import static omegadrive.util.Util.getBitFromByte;
import static omegadrive.util.Util.th;
/**************************************************************************
 license:BSD-3-Clause
 copyright-holders:Fabio Priuli, MetalliC

 MegaDrive / Genesis Cart + STM95 EEPROM device
 see https://github.com/mamedev/mame/blob/23b19b492e794b5da4cd3e978e1ed2fa75edef20/src/devices/bus/megadrive/stm95.cpp#L4
 **************************************************************************/

/**
 * 2023-10
 * - java translation and adaptation
 * <p>
 * Copyright (C) 2023 Federico Berti
 */
public class SpiEeprom implements EepromBase {

    private static final Logger LOG = LogHelper.getLogger(SpiEeprom.class.getSimpleName());

    private static int M95320_SIZE = 0x1000;
    public static int SIZE_BYTES = 0x10_000;

    byte[] eeprom_data;

    int latch;
    int reset_line;
    int sck_line;
    int WEL;

    STMSTATE stm_state;
    int stream_pos;
    int stream_data;
    int eeprom_addr;

    public static final LINE_STATE[] lsvals = LINE_STATE.values();

    enum LINE_STATE {
        CLEAR_LINE,             // clear (a fired or held) line
        ASSERT_LINE,                // assert an interrupt immediately
        HOLD_LINE                   // hold interrupt line until acknowledged
    }

    enum STMSTATE {
        IDLE,
        CMD_WRSR,
        CMD_RDSR,
        M95320_CMD_READ,
        CMD_WRITE,
        READING,
        WRITING
    }

    public SpiEeprom() {
        stm_state = IDLE;
        stream_pos = 0;
        eeprom_data = new byte[0];
    }


    void set_cs_line(LINE_STATE state) {
        reset_line = state.ordinal();
        if (state != CLEAR_LINE) {
            stream_pos = 0;
            stm_state = IDLE;
        }
    }

    void set_si_line(LINE_STATE state) {
        latch = state.ordinal();
        assert latch < 2;
    }

    void set_halt_line(LINE_STATE state) {
//        LOG.warn("Not implemented");
    }

    @Override
    public int readEeprom(int address, Size size) {
        assert eeprom_data.length > 0 && size == Size.BYTE;
        return get_so_line();
    }

    @Override
    public void writeEeprom(int address, int data, Size size) {
        assert eeprom_data.length > 0;
        set_si_line(getLineStateVal(data, 0));
        set_sck_line(getLineStateVal(data, 1));
        set_halt_line(getLineStateVal(data, 2));
        set_cs_line(getLineStateVal(data, 3));
//                LOG.info("A13 write {} byte, data: {}\n", th(offset), th(data));
    }

    @Override
    public void setSram(byte[] sram) {
        eeprom_data = sram;
        assert sram.length > 0;
    }

    /**
     * 1 -> ASSERT_LINE
     * 0 -> CLEAR_LINE
     */
    private SpiEeprom.LINE_STATE getLineStateVal(int data, int bit) {
        return lsvals[getBitFromByte((byte) data, bit)];
    }

    int get_so_line() {
        if (stm_state == READING || stm_state == CMD_RDSR)
            return (stream_data >> 8) & 1;
        else
            return 0;
    }

    void set_sck_line(LINE_STATE state) {
        if (reset_line == CLEAR_LINE.ordinal()) {
            if (state == ASSERT_LINE && sck_line == CLEAR_LINE.ordinal()) {
                switch (stm_state) {
                    case IDLE:
                        stream_data = shiftLeftOrBit(stream_data, latch & 1);
                        stream_pos++;
                        if (stream_pos == 8) {
                            stream_pos = 0;
                            //printf("STM95 EEPROM: got cmd %02X\n", stream_data&0xff);
                            switch (stream_data & 0xff) {
                                case 0x01:  // write status register
                                    if (WEL != 0)
                                        stm_state = CMD_WRSR;
                                    WEL = 0;
                                    break;
                                case 0x02:  // write
                                    if (WEL != 0)
                                        stm_state = CMD_WRITE;
                                    stream_data = 0;
                                    WEL = 0;
                                    break;
                                case 0x03:  // read
                                    stm_state = M95320_CMD_READ;
                                    stream_data = 0;
                                    break;
                                case 0x04:  // write disable
                                    WEL = 0;
                                    break;
                                case 0x05:  // read status register
                                    stm_state = CMD_RDSR;
                                    stream_data = WEL << 1;
                                    break;
                                case 0x06:  // write enable
                                    WEL = 1;
                                    break;
                                default:
                                    LOG.error("STM95 EEPROM: unknown cmd {}\n", th(stream_data & 0xff));
                            }
                        }
                        break;
                    case CMD_WRSR:
                        stream_pos++;       // just skip, don't care block protection
                        if (stream_pos == 8) {
                            stm_state = IDLE;
                            stream_pos = 0;
                        }
                        break;
                    case CMD_RDSR:
                        stream_data = shiftLeftOrBit(stream_data, 0);
                        stream_pos++;
                        if (stream_pos == 8) {
                            stm_state = IDLE;
                            stream_pos = 0;
                        }
                        break;
                    case M95320_CMD_READ:
                        stream_data = shiftLeftOrBit(stream_data, latch & 1);
                        stream_pos++;
                        if (stream_pos == 16) {
                            eeprom_addr = stream_data & (M95320_SIZE - 1);
                            stream_data = (eeprom_data[eeprom_addr] & 0xFF);
//                            LOG.info("EEPROM start reading addr: {}, streamData: {}", th(eeprom_addr), th(stream_data));
                            stm_state = READING;
                            stream_pos = 0;
                        }
                        break;
                    case READING:
                        stream_data = shiftLeftOrBit(stream_data, 0);
                        stream_pos++;
                        if (stream_pos == 8) {
//                            LOG.info("EEPROM done reading addr: {}, streamData: {}", th(eeprom_addr), th(stream_data));
                            if (++eeprom_addr == M95320_SIZE) {
                                eeprom_addr = 0;
                            }
                            stream_data |= (eeprom_data[eeprom_addr] & 0xFF);
                            stream_pos = 0;
                        }
                        break;
                    case CMD_WRITE:
                        stream_data = shiftLeftOrBit(stream_data, latch & 1);
                        stream_pos++;
                        if (stream_pos == 16) {
                            eeprom_addr = stream_data & (M95320_SIZE - 1);
                            stm_state = WRITING;
                            stream_pos = 0;
//                            LOG.info("EEPROM start writing addr: {}", th(eeprom_addr));
                        }
                        break;
                    case WRITING:
                        stream_data = shiftLeftOrBit(stream_data, latch & 1);
                        stream_pos++;
                        if (stream_pos == 8) {
                            eeprom_data[eeprom_addr] = (byte) stream_data;
//                            LOG.info("EEPROM write addr: {} data: {}", th(eeprom_addr), th(stream_data & 0xFF));
                            if (++eeprom_addr == M95320_SIZE)
                                eeprom_addr = 0;
                            stream_pos = 0;
                        }
                        break;
                }
            }
        }
        sck_line = state.ordinal();
    }

    int shiftLeftOrBit(int val, int bit) {
        assert bit < 2;
        val = (val << 1) | bit;
//        LOG.info("EEPROM stream data: {}", th(val));
        return val;
    }
}
