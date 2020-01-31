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

import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Roms db from blastem
 * https://www.retrodev.com/repos/blastem/file/tip/rom.db
 */
public class MdRomDbModel {

    public static final EEPROM NO_EEPROM = new EEPROM();

    public static final String START_OBJ_TOKEN = "{";
    public static final String END_OBJ_TOKEN = "}";
    public static final String COMMENT_TOKEN = "#";
    public static final String FIELD_SEP_TOKEN = " ";

    private static EEPROM toEEPROM(Map<String, Object> map) {
        if (map.isEmpty()) {
            return NO_EEPROM;
        }
        EEPROM eeprom = new EEPROM();
        eeprom.data = Maps.transformValues(map, String::valueOf);
        return eeprom;
    }

    public static class Base {
        Map<String, Object> data = new HashMap<>();

        public Map<String, Object> getData() {
            return data;
        }

        protected String getStringValue(String key, String defaultValue) {
            return String.valueOf(data.getOrDefault(key, defaultValue));
        }

        protected int getIntValue(String key, int defaultValue) {
            return Integer.valueOf(String.valueOf(data.getOrDefault(key, defaultValue)));
        }

        protected Map<String, Object> getMapValue(String key) {
            return (Map<String, Object>) data.getOrDefault(key, Collections.emptyMap());
        }
    }

    public static class Entry extends Base {
        public String getId() {
            return getStringValue("id", "");
        }

        public String getName() {
            return getStringValue("name", "");
        }

        public boolean hasEeprom() {
            return getEeprom() != NO_EEPROM;
        }

        public EEPROM getEeprom() {
            return toEEPROM(getMapValue("EEPROM"));
        }

        @Override
        public String toString() {
            return "Entry{id=" + getId() + ", name=" + getName() + ", eeprom=" + getEeprom() + "}";
        }
    }

    public static class EEPROM extends Base {
        public String getType() {
            return getStringValue("type", "NO_EEPROM");
        }

        public int getSize() {
            return getIntValue("size", 0);
        }

        @Override
        public String toString() {
            return "EEPROM{type=" + getType() + ", size=" + getSize() + "}";
        }
    }
}
