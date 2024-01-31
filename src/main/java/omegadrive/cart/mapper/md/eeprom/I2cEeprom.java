package omegadrive.cart.mapper.md.eeprom;

import omegadrive.cart.loader.MdRomDbModel.EepromLineMap;
import omegadrive.cart.loader.MdRomDbModel.EepromType;
import omegadrive.cart.loader.MdRomDbModel.RomDbEntry;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import static omegadrive.cart.mapper.md.eeprom.I2cEeprom.EepromState.*;
import static omegadrive.util.ArrayEndianUtil.getByteInWordBE;
import static omegadrive.util.ArrayEndianUtil.setByteInWordBE;
import static omegadrive.util.Util.th;

/****************************************************************************
 *  Genesis Plus
 *  I2C serial EEPROM (24Cxx) boards
 *
 *  Copyright (C) 2007-2016  Eke-Eke (Genesis Plus GX)
 *
 *  Redistribution and use of this code or any derivative works are permitted
 *  provided that the following conditions are met:
 *
 *   - Redistributions may not be sold, nor may they be used in a commercial
 *     product or activity.
 *
 *   - Redistributions that are modified from the original source must include the
 *     complete source code, including the source code for all components used by a
 *     binary built from the modified sources. However, as a special exception, the
 *     source code distributed need not include anything that is normally distributed
 *     (in either source or binary form) with the major components (compiler, kernel,
 *     and so on) of the operating system on which the executable runs, unless that
 *     component itself accompanies the executable.
 *
 *   - Redistributions must reproduce the above copyright notice, this list of
 *     conditions and the following disclaimer in the documentation and/or other
 *     materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************************/

/**
 * I2cEeprom
 * <p>
 * From GenesisGxPlus:
 * https://github.com/ekeeke/Genesis-Plus-GX/blob/master/core/cart_hw/eeprom_i2c.c
 * <p>
 * 2022-06
 * - java translation and adaptation
 * <p>
 * Copyright (C) 2022 Federico Berti
 *
 * <p>
 * TODO jcart, add more tests
 */
/* Some notes from 8BitWizard (http://gendev.spritesmind.net/forum/viewtopic.php?t=206):
 *
 * Mode 1 (7-bit) - the chip takes a single byte with a 7-bit memory address and a R/W bit (X24C01)
 * Mode 2 (8-bit) - the chip takes a 7-bit device address and R/W bit followed by an 8-bit memory address;
 * the device address may contain up to three more memory address bits (24C01 - 24C16).
 * You can also string eight 24C01, four 24C02, two 24C08, or various combinations, set their address config lines correctly,
 * and the result appears exactly the same as a 24C16
 * Mode 3 (16-bit) - the chip takes a 7-bit device address and R/W bit followed by a 16-bit memory address (24C32 and larger)
 *
 */
public class I2cEeprom implements EepromBase {

    private final static Logger LOG = LogHelper.getLogger(I2cEeprom.class.getSimpleName());
    private static final boolean verbose = false;
    private static final boolean logReadWrite = verbose || false;

    protected final EepromContext ctx = new EepromContext();
    private byte[] sram;

    protected static class EepromContext {
        public int scl, prevScl;
        public int sda, prevSda;
        public int cycles, rw, writeLatch;
        public byte buffer;
        public int wordAddress = 0, deviceAddress = 0;
        public EepromState state = STAND_BY;
        public EepromType spec;
        public EepromLineMap lineMap;
    }

    public static EepromBase createInstance(RomDbEntry entry, byte[] sram) {
        EepromBase eb = NO_OP;
        if (entry.hasEeprom()) {
            I2cEeprom e = new I2cEeprom();
            e.ctx.lineMap = entry.eeprom.getEepromLineMap();
            e.ctx.spec = entry.eeprom.getEepromType();
            e.sram = sram;
            LOG.info("Init {}", entry.eeprom);
            eb = e;
        }
        return eb;
    }

    @Override
    public int readEeprom(int address, Size size) {
        int res = eeprom_i2c_out() << ctx.lineMap.sda_out_bit;
        assert size != Size.LONG;
        if (size == Size.BYTE) {
            res = getByteInWordBE(res, address & 1);
        }
        if (logReadWrite)
            System.out.println("R," + th(address & 0xFF) + "," + size.name().charAt(0) + "," + th(res));
        return res;
    }

    @Override
    public void writeEeprom(int address, int data, Size size) {
        if (logReadWrite)
            System.out.println("W," + th(address & 0xFF) + "," + size.name().charAt(0) + "," + th(data));
        assert size != Size.LONG;
        if (size == Size.BYTE) {
            ctx.writeLatch = setByteInWordBE(ctx.writeLatch, data, address & 1);
            data = ctx.writeLatch;
        }
        writeWordEeprom(data);
    }

    private void writeWordEeprom(int data) {
        ctx.scl = (data >> ctx.lineMap.scl_in_bit) & 1;
        ctx.sda = (data >> ctx.lineMap.sda_in_bit) & 1;
        eeprom_i2c_update();
    }

    private int eeprom_i2c_out() {
        int res = 0;
        /* check EEPROM ctx.state */
        if (ctx.state == READ_DATA) {
            /* READ cycle */
            if (ctx.cycles < 9) {
                /* return memory array (max 64kB) DATA bits */
                int index = ctx.wordAddress & 0xffff;
                /* return memory array (max 64kB) DATA bits */
                res = (sram[(ctx.deviceAddress | ctx.wordAddress) & 0xFFFF] >> (8 - ctx.cycles)) & 1;
                if (verbose) LOG.info("{}, read address {} val {}, bitVal: {}, on cycle {}", ctx.state,
                        th(index), th(sram[index]), th(res), ctx.cycles);
            }
        } else if (ctx.cycles == 9) {
            /* ACK cycle */
            if (verbose) LOG.info("{}, SDA {}, ack on cycle {}, res: {}", ctx.state, ctx.sda, ctx.cycles, 0);
        } else {
            /* return latched /SDA input by default */
            res = (ctx.sda >> ctx.lineMap.sda_out_bit) & 1;
        }
        return res;
    }

    void eeprom_i2c_update() {
        /* EEPROM current ctx.state */
        switch (ctx.state) {
            /* Standby Mode */
            case STAND_BY: {
                checkStart();
                break;
            }

            /* Suspended Mode */
            case WAIT_STOP: {
                checkStop();
                break;
            }

            /* Get Word Address (7-bit): Mode 1 (X24C01) only
             * and R/W bit
             */
            case GET_WORD_ADR_7BITS: {
                checkStart();
                checkStop();

                /* look for SCL HIGH to LOW transition */
                if (ctx.prevScl > ctx.scl) {
                    if (ctx.cycles < 9) {
                        /* increment cycle counter */
                        ctx.cycles++;
                    } else {
                        /* next sequence */
                        ctx.cycles = 1;
                        ctx.state = ctx.rw > 0 ? READ_DATA : WRITE_DATA;

                        /* clear write ctx.buffer */
                        ctx.buffer = 0x00;
                    }
                }

                /* look for SCL LOW to HIGH transition */
                else if (ctx.prevScl < ctx.scl) {
                    if (ctx.cycles < 8) {
                        /* latch Word Address bits 6-0 */
                        ctx.wordAddress |= (ctx.sda << (7 - ctx.cycles));
                    } else if (ctx.cycles == 8) {
                        /* latch R/W bit */
                        ctx.rw = ctx.sda;
                    }
                }

                break;
            }

            /* Get Device Address (0-3 bits, depending on the array size) : Mode 2 & Mode 3 (24C01 - 24C512) only
             * or/and Word Address MSB: Mode 2 only (24C04 - 24C16) (0-3 bits, depending on the array size)
             * and R/W bit
             */
            case GET_DEVICE_ADR: {
                checkStart();
                checkStop();

                /* look for SCL HIGH to LOW transition */
                if (ctx.prevScl > ctx.scl) {
                    if (ctx.cycles < 9) {
                        /* increment cycle counter */
                        ctx.cycles++;
                    } else {
                        /* shift Device Address bits */
                        ctx.deviceAddress <<= ctx.spec.addressBits;

                        /* next sequence */
                        ctx.cycles = 1;
                        if (ctx.rw > 0) {
                            ctx.state = READ_DATA;
                        } else {
                            ctx.wordAddress = 0;
                            ctx.state = (ctx.spec.addressBits == 16) ? GET_WORD_ADR_HIGH : GET_WORD_ADR_LOW;
                        }
                    }
                }

                /* look for SCL LOW to HIGH transition */
                else if (ctx.prevScl < ctx.scl) {
                    if ((ctx.cycles > 4) && (ctx.cycles < 8)) {
                        /* latch Device Address bits */
                        ctx.deviceAddress |= (ctx.sda << (7 - ctx.cycles));
                    } else if (ctx.cycles == 8) {
                        /* latch R/W bit */
                        ctx.rw = ctx.sda;
                    }
                }

                break;
            }

            /* Get Word Address MSB (4-8 bits depending on the array size)
             * Mode 3 only (24C32 - 24C512)
             */
            case GET_WORD_ADR_HIGH: {
                checkStart();
                checkStop();

                /* look for SCL HIGH to LOW transition */
                if (ctx.prevScl > ctx.scl) {
                    if (ctx.cycles < 9) {
                        /*increment cycle counter*/
                        ctx.cycles++;
                    } else {
                        /* next sequence */
                        ctx.cycles = 1;
                        ctx.state = GET_WORD_ADR_LOW;
                    }
                }

                /* look for SCL LOW to HIGH transition */
                else if (ctx.prevScl < ctx.scl) {
                    if (ctx.cycles < 9) {
                        if (ctx.spec.sizeMask < (1 << (16 - ctx.cycles))) {
                            /* ignored bit: Device Address bits should be right-shifted*/
                            ctx.deviceAddress >>= 1;
                        } else {
                            /* latch Word Address high bits */
                            ctx.wordAddress |= (ctx.sda << (16 - ctx.cycles));
                        }
                    }
                }

                break;
            }

            /* Get Word Address LSB: 7bits (24C01) or 8bits (24C02-24C512)
             * MODE-2 and MODE-3 only (24C01 - 24C512)
             */
            case GET_WORD_ADR_LOW: {
                checkStart();
                checkStop();

                /* look for SCL HIGH to LOW transition */
                if (ctx.prevScl > ctx.scl) {
                    if (ctx.cycles < 9) {
                        /* increment cycle counter */
                        ctx.cycles++;
                    } else {
                        /* next sequence */
                        ctx.cycles = 1;
                        ctx.state = WRITE_DATA;

                        /* clear write ctx.buffer */
                        ctx.buffer = 0x00;
                    }
                }

                /* look for SCL LOW to HIGH transition */
                else if (ctx.prevScl < ctx.scl) {
                    if (ctx.cycles < 9) {
                        if (ctx.spec.sizeMask < (1 << (8 - ctx.cycles))) {
                            /* ignored bit (24C01): Device Address bits should be right-shifted */
                            ctx.deviceAddress >>= 1;
                        } else {
                            /* latch Word Address low bits */
                            ctx.wordAddress |= (ctx.sda << (8 - ctx.cycles));
                        }
                    }
                }

                break;
            }

            /*
             * Read Sequence
             */
            case READ_DATA: {
                checkStart();
                checkStop();

                /* look for SCL HIGH to LOW transition */
                if (ctx.prevScl > ctx.scl) {
                    if (ctx.cycles < 9) {
                        /* increment cycle counter */
                        ctx.cycles++;
                    } else {
                        /* next read sequence */
                        ctx.cycles = 1;
                    }
                }

                /* look for SCL LOW to HIGH transition */
                else if (ctx.prevScl < ctx.scl) {
                    if (ctx.cycles == 9) {
                        /* check if ACK is received */
                        if (ctx.sda > 0) {
                            /* end of read sequence */
                            ctx.state = WAIT_STOP;
                        } else {
                            /* increment Word Address (roll up at maximum array size) */
                            ctx.wordAddress = (ctx.wordAddress + 1) & ctx.spec.sizeMask;
                        }
                    }
                }

                break;
            }

            /*
             * Write Sequence
             */
            case WRITE_DATA: {
                checkStart();
                checkStop();

                /* look for SCL HIGH to LOW transition */
                if (ctx.prevScl > ctx.scl) {
                    if (ctx.cycles < 9) {
                        /* increment cycle counter */
                        ctx.cycles++;
                    } else {
                        /* next write sequence */
                        ctx.cycles = 1;
                    }
                }

                /* look for SCL LOW to HIGH transition */
                else if (ctx.prevScl < ctx.scl) {
                    if (ctx.cycles < 9) {
                        /* latch DATA bits 7-0 to write ctx.buffer */
                        ctx.buffer |= (ctx.sda << (8 - ctx.cycles));
                    } else {
                        /* write back to memory array (max 64kB) */
                        sram[(ctx.deviceAddress | ctx.wordAddress) & 0xffff] = ctx.buffer;

                        /* clear write ctx.buffer */
                        ctx.buffer = 0;

                        /* increment Word Address (roll over at maximum page size) */
                        ctx.wordAddress = (ctx.wordAddress & ~ctx.spec.pagewriteMask) |
                                ((ctx.wordAddress + 1) & ctx.spec.pagewriteMask);
                    }
                }

                break;
            }
        }

        /* save SCL & SDA previous ctx.state */
        ctx.prevScl = ctx.scl;
        ctx.prevSda = ctx.sda;
    }

    // Check for START.
    private void checkStart() {
        if ((ctx.prevScl & ctx.scl) > 0 && (ctx.prevSda > ctx.sda)) {
            ctx.cycles = 0;
            if (ctx.spec.addressBits == 7) {
                ctx.state = EepromState.GET_WORD_ADR_7BITS;
                ctx.wordAddress = 0;
            } else {
                ctx.deviceAddress = 0;
                ctx.state = GET_DEVICE_ADR;
            }
            if (verbose) LOG.info("START {}", ctx.state);
        }
    }

    private void checkStop() {
        /* detect SDA LOW to HIGH transition while SCL is held HIGH */
        if (((ctx.prevScl & ctx.scl) > 0) && (ctx.sda > ctx.prevSda)) {
            ctx.state = EepromState.STAND_BY;
            if (verbose) LOG.info("STOP {}", ctx.state);
        }
    }

    @Override
    public void setSram(byte[] sram) {
        this.sram = sram;
    }

    enum EepromState {
        STAND_BY, WAIT_STOP, GET_DEVICE_ADR, GET_WORD_ADR_7BITS, GET_WORD_ADR_HIGH,
        GET_WORD_ADR_LOW, WRITE_DATA, READ_DATA
    }
}