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
import org.slf4j.Logger;

import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;
import static omegadrive.util.Util.th;

/**
 * ExSsfMapper
 * <p>
 * NOTE: we only support CTRL0 remapping
 *
 * @author Federico Berti
 */
public class ExSsfMapper extends Ssf2Mapper {
    private static final Logger LOG = LogHelper.getLogger(ExSsfMapper.class.getSimpleName());
    private final static boolean verbose = false;
    public static final int BANK_SET_START_ADDRESS = 0xA130F0;
    public static final int BANK_SET_END_ADDRESS = 0xA130FE;

    @Override
    public long readData(long address, Size size) {
        address &= MD_PC_MASK;
        if (address >= BANKABLE_START_ADDRESS && address <= GenesisBusProvider.DEFAULT_ROM_END_ADDRESS) {
            return super.readData(address, size);
        } else if (address < BANKABLE_START_ADDRESS) { //exSSf can remap < 0x80000

            int addressI = (int) ((banks[0] << BANK_SHIFT) | (address & BANK_MASK));
            if (verbose) LOG.info("Bank read: {} {} -> {}", th(addressI), size, th(address));
            return Util.readDataMask(memory.getRomData(), size, addressI, memory.getRomMask());
        }
        return baseMapper.readData(address, size);
    }

    public static ExSsfMapper createInstance(RomMapper baseMapper, IMemoryProvider memoryProvider) {
        ExSsfMapper mapper = new ExSsfMapper();
        mapper.baseMapper = baseMapper;
        mapper.memory = memoryProvider;
        LOG.info("ExSsfMapper created and enabled");
        return mapper;
    }

    @Override
    public void writeData(long address, long data, Size size) {
        address &= MD_PC_MASK;
        if (address >= BANK_SET_START_ADDRESS && address <= BANK_SET_END_ADDRESS) {
            int ctrlNum = (int) (address & 7);
            //odd addresses go to SSF2 mapper
            if ((ctrlNum & 1) == 1) {
                assert size == Size.BYTE;
                writeBankData(address, data);
            } else {
                assert size != Size.LONG;
                if (size == Size.BYTE) {
                    if (ctrlNum == 0) {
                        writeBankDataExSsf(address, data);
                    } else {
                        writeBankData(address + 1, data & 0xFF);
                    }
                } else if (size == Size.WORD) {
//                    assert false : "untested";
                    writeBankData(address + 1, data & 0xFF);
                }
            }
            return;
        }
        super.writeData(address, data, size);
    }

    private void writeBankDataExSsf(long addressL, long data) {
        int val = (int) (addressL & 0xF);
        int index = val >> 1;
        int dataI = (int) (data & bankSelMask);
        banks[0] = dataI;
        if (verbose) LOG.info("Setting bankSelector {}: {}, {}", index, th(addressL), th(dataI));
    }

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

     for (int j = 2; j <= BANK_REG_MASK; j+=2) {
     mapper.writeData(BANK_SET_START_ADDRESS + j, i, Size.WORD);
     Assert.assertEquals(val, banksState[0]);
     Assert.assertEquals(i & mapper.bankSelMask, banksState[j >> 1]);
     }
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
     */
}
