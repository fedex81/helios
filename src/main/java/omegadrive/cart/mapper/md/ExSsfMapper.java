/*
 * ExSsfMapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 13:51
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

package omegadrive.cart.mapper.md;

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Extended-SSF mapper description
 * <p>
 * -------cartridge memory map-------
 * 0x000000: BANK0
 * 0x080000: BANK1
 * 0x100000: BANK2
 * 0x180000: BANK3
 * 0x200000: BANK4
 * 0x280000: BANK5
 * 0x300000: BANK6
 * 0x380000: BANK7
 * <p>
 * <p>
 * ---------Control registers---------
 * 0xA130F0: [P.WLC... ...RRRRR] CTRL0
 * 0xA130F2: [........ ...RRRRR] CTRL1
 * 0xA130F4: [........ ...RRRRR] CTRL2
 * 0xA130F6: [........ ...RRRRR] CTRL3
 * 0xA130F8: [........ ...RRRRR] CTRL4
 * 0xA130FA: [........ ...RRRRR] CTRL5
 * 0xA130FC: [........ ...RRRRR] CTRL6
 * 0xA130FE: [........ ...RRRRR] CTRL7
 * P is protection bit. P always should be set for access to the reg 0xA130F0 (CTRL0).Word access only
 * W Memory write protection (0=protected, 1=unprotected)
 * L LED (0=off, 1=on)
 * C #CART signal control. This bit goes directly to the #CART wire.
 * It can map expansion port to the cartridge memory space if set
 * R 512Kbyte bank. 32 banks total
 * <p>
 * Every CTRLx register controls corresponding 512K cartridge BANKx. CTRL0 has extra functions listed above.
 * All registers is write only
 * Mapper supports up to 16Mbyte of memory (32 banks total). Backup ram mapped to the last 31th bank
 *
 * - extended ssf vs ssf mapper, https://krikzz.com/pub/support/mega-everdrive/x3x5x7/dev/extended_ssf-v2.txt
 * https://github.com/krikzz/MEGA-PRO/blob/master/samples/mappers-se/extended-ssf.txt
 */
//TODO savestate stuff
public class ExSsfMapper extends Ssf2Mapper {

    private final boolean verbose = false;

    public static final int BANK_SET_START_ADDRESS = 0xA130F0;
    public static final int BANK_SET_END_ADDRESS = 0xA130FE;
    public static final int MATH_ARG_HI = 0xA130D0; //read/write
    public static final int MATH_ARG_LO = 0xA130D2; //read/write
    public static final int MATH_MUL_HI = 0xA130D4; //write only
    public static final int MATH_MUL_LO = 0xA130D6; //write only
    public static final int MATH_DIV_HI = 0xA130D8; //write only
    public static final int MATH_DIV_LO = 0xA130DA; //write only
    private static final Logger LOG = LogManager.getLogger(ExSsfMapper.class.getSimpleName());
    private long[] mathReg = new long[0xB];
    private int[] moreBanks = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
    private int[][] moreRam = new int[moreBanks.length][BANK_SIZE];
    private int reg_ctrl0 = 0; //protection on, read-only
    private boolean mapRom = true;

    @Override
    public long readData(long address, Size size) {
        address &= 0xFF_FFFF;
        if (address >= BANKABLE_START_ADDRESS && address <= GenesisBusProvider.DEFAULT_ROM_END_ADDRESS) {
            if (mapRom) {
                return super.readData(address, size);
            }
            LogHelper.printLevel(LOG, Level.INFO, "Bank read: {}", address, verbose);
            int bankSelector = (int) (address >> BANK_SHIFT);
            address &= (BANK_SIZE - 1);
            return Util.readData(moreRam[moreBanks[bankSelector]], size, (int) address);
        } else if (address >= MATH_ARG_HI && address <= MATH_ARG_LO) {
//            LOG.info("Read: {}, {}, {}", Long.toHexString(address), Long.toHexString(mathReg[(int) (address & 0xF)]), opType);
            return mathReg[(int) (address & 0xF)];
        }
        return baseMapper.readData(address, size);
    }

    private OP_TYPE opType = OP_TYPE.NONE;

    public static ExSsfMapper createInstance(RomMapper baseMapper, IMemoryProvider memoryProvider) {
        ExSsfMapper mapper = new ExSsfMapper();
        mapper.baseMapper = baseMapper;
        mapper.memory = memoryProvider;
        LOG.info("ExSsfMapper created and enabled");
        return mapper;
    }

    @Override
    public void writeData(long address, long data, Size size) {
        address &= 0xFF_FFFF;
        if (address >= BANKABLE_START_ADDRESS && address <= GenesisBusProvider.DEFAULT_ROM_END_ADDRESS) {
            LogHelper.printLevel(LOG, Level.INFO, "Bank write: {}", address, verbose);
            int bankSelector = (int) (address >> BANK_SHIFT);
            address &= (BANK_SIZE - 1);
            Util.writeData(moreRam[moreBanks[bankSelector]], size, (int) address, data);
            return;
        } else if (address >= MATH_ARG_HI && address <= MATH_DIV_LO) {
            writeMathRegs(address, data);
            opType = address <= MATH_ARG_LO ? OP_TYPE.NONE : (address <= MATH_MUL_LO ? OP_TYPE.MULT : OP_TYPE.DIV);
            recalc(opType);
            return;
        } else if (address >= BANK_SET_START_ADDRESS && address <= BANK_SET_END_ADDRESS) {
            //odd addresses go to SSF2 mapper
            if ((address & 1) == 1) {
                writeBankData(address, data);
            } else {
                writeBankDataExSsf(address, data);
            }
            return;
        }
        super.writeData(address, data, size);
    }

    private void writeBankDataExSsf(long addressL, long data) {
        int val = (int) (addressL & 0xF);
        int index = val >> 1;
        int dataI = (int) (data & 0x3F);
        moreBanks[index] = dataI;
        LogHelper.printLevel(LOG, Level.INFO, "Setting bankSelector {}: {}, {}", index, addressL, dataI, verbose);
        if (index == 0) {
            reg_ctrl0 = (int) (data & 0xFFFF);
            mapRom = (reg_ctrl0 >> 15) == 0;
        }
    }

    public void writeMathRegs(long address, long data) {
//        LOG.info("Write: {}, {}", Long.toHexString(address), Long.toHexString(data));
        long d = Integer.toUnsignedLong((int) data);
        int addr = (int) address;
        switch (addr) {
            case MATH_DIV_LO:
            case MATH_MUL_LO:
            case MATH_ARG_LO:
                mathReg[addr & 0xF] = d & 0xFFFF;
                break;
            case MATH_DIV_HI:
            case MATH_MUL_HI:
            case MATH_ARG_HI:
                mathReg[addr & 0xF] = d;
                mathReg[(addr + 2) & 0xF] = d & 0xFFFF;
                break;
        }
    }

    private void recalc(OP_TYPE opType) {
        if (opType == OP_TYPE.MULT) {
            mathReg[0] = (mathReg[0] * mathReg[4]) & 0xFFFF_FFFF;
            mathReg[2] = (mathReg[2] * mathReg[6]) & 0xFFFF;
        } else if (opType == OP_TYPE.DIV) {
            mathReg[0] = mathReg[0] / Math.max(1, mathReg[8]);
            mathReg[2] = mathReg[2] / Math.max(1, mathReg[0xA]);
        }
    }

    enum OP_TYPE {NONE, MULT, DIV}


}
