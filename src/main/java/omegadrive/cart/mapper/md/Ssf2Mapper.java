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

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;

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
 * 0xA130F3 -> 0x080000 - 0x0FFFFF
 * 0xA130F5 -> 0x100000 - 0x17FFFF
 * 0xA130F7 -> 0x180000 - 0x1FFFFF
 * 0xA130F9 -> 0x200000 - 0x27FFFF
 * 0xA130FB -> 0x280000 - 0x2FFFFF
 * 0xA130FD -> 0x300000 - 0x37FFFF
 * 0xA130FF -> 0x380000 - 0x3FFFFF
 * <p>
 * https://github.com/Emu-Docs/Emu-Docs/blob/master/Genesis/ssf2.txt
 **/
public abstract class Ssf2Mapper implements RomMapper {

    private static final Logger LOG = LogHelper.getLogger(Ssf2Mapper.class.getSimpleName());

    public static final int BANK_SET_START_ADDRESS = 0xA130F3;
    public static final int BANK_SET_END_ADDRESS = 0xA130FF;

    public static final int BANK_SIZE = 0x80000;
    public static final int BANK_MASK = BANK_SIZE - 1;
    public static final int BANKABLE_START_ADDRESS = 0x80000;
    public static final int BANK_SHIFT = 19;

    private int[] banks = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
    protected RomMapper baseMapper;
    protected IMemoryProvider memory;
    private final static boolean verbose = false;

    @Override
    public long readData(long addressL, Size size) {
        if (addressL >= BANKABLE_START_ADDRESS && addressL <= GenesisBusProvider.DEFAULT_ROM_END_ADDRESS) {
            int address = (int) (addressL & 0xFF_FFFF);
            //bankSelector = address >> BANK_SHIFT;
            address = (banks[address >> BANK_SHIFT] << BANK_SHIFT) | (address & 0x7_FFFF);
            if (verbose) LOG.info("Bank read: {} -> {}",
                    addressL, address);
            return Util.readDataMask(memory.getRomData(), size, address, memory.getRomMask());
        }
        return baseMapper.readData(addressL, size);
    }

    @Override
    public void writeData(long addressL, long data, Size size) {
        if (addressL >= BANK_SET_START_ADDRESS && addressL <= BANK_SET_END_ADDRESS) {
            writeBankData(addressL, data);
            return;
        }
        baseMapper.writeData(addressL, data, size);
    }

    @Override
    public void writeBankData(long addressL, long data) {
        int val = (int) (addressL & 0xF);
        int index = val >> 1;
        if (val % 2 == 1 && index > 0) {
            int dataI = (int) (data & 0x3F);
            banks[index] = dataI;
            if (verbose) LOG.info("Bank write to: {}, {}", addressL, dataI);
        } else if (val == 1) { //0xA130F1 goes to timeControlWrite
            baseMapper.writeData(addressL, data, Size.BYTE);
        }
    }


    public int[] getState() {
        return banks;
    }

    public void setState(int[] bankData) {
        this.banks = Arrays.copyOf(bankData, bankData.length);
    }
}
