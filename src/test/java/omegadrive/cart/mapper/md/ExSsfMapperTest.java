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

import omegadrive.util.Size;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import static omegadrive.cart.mapper.md.ExSsfMapper.BANK_SET_START_ADDRESS;
import static omegadrive.cart.mapper.md.Ssf2Mapper.BANK_REG_MASK;


public class ExSsfMapperTest {

    /**
     * NOTE: Sega Channel expect 6 bits for bankSel but exSsf specifies 5 bits (64 vs 32)
     */
    @Test
    public void testExSsf() {
        ExSsfMapper mapper = new ExSsfMapper();
        int[] banksState = mapper.getState();
        for (int i = 0; i < 0x100; i++) {
            mapper.writeData(BANK_SET_START_ADDRESS, i, Size.BYTE);
            int val = (i & mapper.bankSelMask);
            Assert.assertEquals(val, banksState[0]);
            //write to SSF2 as BYTE
            for (int j = 3; j <= BANK_REG_MASK; j += 2) {
                mapper.writeData(BANK_SET_START_ADDRESS + j, i, Size.BYTE);
                Assert.assertEquals(val, banksState[0]);
                Assert.assertEquals(i & mapper.bankSelMask, banksState[j >> 1]);
            }
            //write directly to EXSSF as BYTE on even addresses, check it ends up in SSF2 on odd addresses (hack)
            //write directly to EXSSF as WORD on even addresses, check it ends up in SSF2 on odd addresses
            for (int j = 2; j <= BANK_REG_MASK; j += 2) {
                mapper.writeData(BANK_SET_START_ADDRESS + j, i, Size.BYTE);
                Assert.assertEquals(val, banksState[0]);
                Assert.assertEquals(i & mapper.bankSelMask, banksState[j >> 1]);

                mapper.writeData(BANK_SET_START_ADDRESS + j, i, Size.WORD);
                Assert.assertEquals(val, banksState[0]);
                Assert.assertEquals(i & mapper.bankSelMask, banksState[j >> 1]);
            }
        }
    }

}
