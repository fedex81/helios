/*
 * SmsLoader
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

package omegadrive.cart.loader;

import omegadrive.cart.loader.MdRomDbModel.RomDbEntry.EepromEntry;

import java.util.StringJoiner;

/**
 * Roms db from blastem, genesis plus gx
 * https://www.retrodev.com/repos/blastem/file/tip/rom.db
 * https://github.com/ekeeke/Genesis-Plus-GX/blob/cea418ece8152520faf1a9aea2d17a89906a6dc7/core/cart_hw/eeprom_i2c.c
 * <p>
 * Last updated: 202206
 */
public class MdRomDbModel {

    public static final RomDbEntry NO_ENTRY = new RomDbEntry();
    public static final EepromEntry NO_EEPROM = new EepromEntry();

    public enum EepromType {
        X24C01(7, 0x7F, 3);

        public final int addressBits, sizeMask, pagewriteMask;

        EepromType(int ab, int sm, int pm) {
            this.addressBits = ab;
            this.sizeMask = sm;
            this.pagewriteMask = pm;
        }
    }

    public enum EepromLineMap {
        SEGA(1, 0, 0),
        EA(6, 7, 7);

        public final int scl_in_bit, sda_in_bit, sda_out_bit;

        EepromLineMap(int scl_in_bit, int sda_in_bit, int sda_out_bit) {
            this.scl_in_bit = scl_in_bit;
            this.sda_in_bit = sda_in_bit;
            this.sda_out_bit = sda_out_bit;
        }
    }

    public static class RomDbEntry {
        public String id, name, forceRegion, notes;
        public Integer sp, check;
        public Boolean force3Btn;
        //note: json sets this to null when missing
        public EepromEntry eeprom;

        public boolean hasEeprom() {
            return eeprom != null && eeprom != NO_EEPROM;
        }

        public static class EepromEntry {
            public String type, lineMap;

            @Override
            public String toString() {
                return new StringJoiner(", ", EepromEntry.class.getSimpleName() + "[", "]")
                        .add("type='" + type + "'")
                        .add("lineMap='" + lineMap + "'")
                        .toString();
            }

            public EepromLineMap getEepromLineMap() {
                EepromLineMap l = EepromLineMap.valueOf(lineMap);
                assert l != null;
                return l;
            }

            public EepromType getEepromType() {
                EepromType t = EepromType.valueOf(type);
                assert t != null;
                return t;
            }

            public int getEepromSize() {
                return getEepromType().sizeMask + 1;
            }
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", RomDbEntry.class.getSimpleName() + "[", "]")
                    .add("id='" + id + "'")
                    .add("name='" + name + "'")
                    .add("forceRegion='" + forceRegion + "'")
                    .add("sp=" + sp)
                    .add("check=" + check)
                    .add("force3Btn=" + force3Btn)
                    .add("eeprom=" + eeprom)
                    .toString();
        }
    }
}
