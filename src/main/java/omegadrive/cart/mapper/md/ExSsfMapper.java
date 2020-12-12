/*
 * Ssf2Mapper
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

import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/**
 * A page is specified with 6 bits (bits 7 and 6 are always 0) thus allowing a possible 64 pages
 * (SSFII only has 10, though.)
 * <p>
 * 7  6  5  4  3  2  1  0
 * +-----------------------+
 * |??|??|??|??|??|??|WP|MD|
 * +-----------------------+
 * <p>
 * MD:     Mode -- 0 = ROM, 1 = RAM
 * WP:     Write protect -- 0 = writable, 1 = not writable
 * <p>
 * 0xA130F3 ->	0x080000 - 0x0FFFFF
 * 0xA130F5 -> 0x100000 - 0x17FFFF
 * 0xA130F7 -> 0x180000 - 0x1FFFFF
 * 0xA130F9 -> 0x200000 - 0x27FFFF
 * 0xA130FB -> 0x280000 - 0x2FFFFF
 * 0xA130FD -> 0x300000 - 0x37FFFF
 * 0xA130FF -> 0x380000 - 0x3FFFFF
 * <p>
 * https://github.com/Emu-Docs/Emu-Docs/blob/master/Genesis/ssf2.txt
 **/
public class ExSsfMapper extends Ssf2Mapper {

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
    private int[] banks = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
    private boolean verbose = false;
    private int[][] moreRam = new int[banks.length][BANK_SIZE];
    private OP_TYPE opType = OP_TYPE.NONE;

    public static ExSsfMapper createInstance(RomMapper baseMapper, IMemoryProvider memoryProvider) {
        ExSsfMapper mapper = new ExSsfMapper();
        mapper.baseMapper = baseMapper;
        mapper.memory = memoryProvider;
        LOG.info("ExSsfMapper created and enabled");
        return mapper;
    }

    @Override
    public long readData(long address, Size size) {
        address = address & 0xFF_FFFF;
        if (address >= BANKABLE_START_ADDRESS && address <= GenesisBusProvider.DEFAULT_ROM_END_ADDRESS) {
            LogHelper.printLevel(LOG, Level.INFO, "Bank read: {}", address, verbose);
            int bankSelector = (int) (address / BANK_SIZE);
            address &= (BANK_SIZE - 1);
            return Util.readData(moreRam[banks[bankSelector]], size, (int) address);
        } else if (address >= MATH_ARG_HI && address <= MATH_ARG_LO) {
//            LOG.info("Read: {}, {}, {}", Long.toHexString(address), Long.toHexString(mathReg[(int) (address & 0xF)]), opType);
            return mathReg[(int) (address & 0xF)];
        }
        return super.readData(address, size);
    }

    @Override
    public void writeData(long address, long data, Size size) {
        address = address & 0xFF_FFFF;
        if (address >= BANKABLE_START_ADDRESS && address <= GenesisBusProvider.DEFAULT_ROM_END_ADDRESS) {
            LogHelper.printLevel(LOG, Level.INFO, "Bank write: {}", address, verbose);
            int bankSelector = (int) (address / BANK_SIZE);
            address &= (BANK_SIZE - 1);
            Util.writeData(moreRam[banks[bankSelector]], size, (int) address, data);
            return;
        } else if (address >= MATH_ARG_HI && address <= MATH_DIV_LO) {
            writeMathRegs(address, data);
            opType = address <= MATH_ARG_LO ? OP_TYPE.NONE : (address <= MATH_MUL_LO ? OP_TYPE.MULT : OP_TYPE.DIV);
            recalc(opType);
            return;
        } else if (address >= BANK_SET_START_ADDRESS && address <= BANK_SET_END_ADDRESS) {
            writeBankData(address, data);
            return;
        }
        super.writeData(address, data, size);
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

    @Override
    public void writeBankData(long addressL, long data) {
        int val = (int) (addressL & 0xF);
        int index = val >> 1;
        if (val % 2 == 0) {
            int dataI = (int) (data & 0x3F);
            banks[index] = dataI;
            LogHelper.printLevel(LOG, Level.INFO, "Setting bankSelector {}: {}, {}", index, addressL, dataI, verbose);
        }
    }

    public int[] getState() {
        return banks;
    }

    public void setState(int[] bankData) {
        this.banks = Arrays.copyOf(bankData, bankData.length);
    }

    enum OP_TYPE {NONE, MULT, DIV}
}
