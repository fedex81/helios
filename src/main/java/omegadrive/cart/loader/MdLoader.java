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

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import omegadrive.cart.loader.MdRomDbModel.RomDbEntry;
import omegadrive.cart.mapper.MapperSelector;
import omegadrive.util.FileUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static omegadrive.cart.loader.MdRomDbModel.NO_ENTRY;

/**
 * Roms db from blastem, genesis plus gx
 * https://www.retrodev.com/repos/blastem/file/tip/rom.db
 * https://github.com/ekeeke/Genesis-Plus-GX/blob/cea418ece8152520faf1a9aea2d17a89906a6dc7/core/cart_hw/eeprom_i2c.c
 * <p>
 * Last updated: 202206
 */
public class MdLoader {

    private static final Logger LOG = LogManager.getLogger(MdLoader.class.getSimpleName());
    static String fileName = MapperSelector.ROM_DB_BASE_FOLDER + "md_romdb.json";

    private static final Map<String, RomDbEntry> map = new HashMap<>();

    private static Map<String, RomDbEntry> getMap() {
        if (map.isEmpty()) {
            init();
        }
        return map;
    }

    public static RomDbEntry getEntry(String serial) {
        final String sn = serial.substring(3, serial.length() - 3).trim();
        return getMap().getOrDefault(sn, NO_ENTRY);
    }

    private static void init() {
        String json = FileUtil.readFileContentAsString(fileName);
        if (Strings.isNullOrEmpty(json)) {
            LOG.warn("Missing romDb file: {}", fileName);
            map.put("NONE", NO_ENTRY);
            return;
        }
        Gson gson = new Gson();
        Type listOfMyClassObject = new TypeToken<ArrayList<RomDbEntry>>() {
        }.getType();
        List<RomDbEntry> l = gson.fromJson(json, listOfMyClassObject);
        map.clear();
        l.forEach(e -> map.put(e.id, e));
    }
}