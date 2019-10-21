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

import com.google.common.collect.ImmutableMap;
import omegadrive.SystemLoader;
import omegadrive.cart.mapper.MapperSelector;
import omegadrive.cart.mapper.sms.SmsMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SMS roms db from the MEKA project
 * https://raw.githubusercontent.com/ocornut/meka/0f1bf8f876a99cb23c440043d2aadfd683c5c812/meka/meka.nam
 */
public class SmsLoader {

    static String fileName = MapperSelector.ROM_DB_BASE_FOLDER + "meka.nam";
    static Map<String, String> mapperIdMap = ImmutableMap.of(
            SmsMapper.Type.CODEM.name(), "/EMU_MAPPER=3",
            SmsMapper.Type.KOREA.name(), "/EMU_MAPPER=9"
    );
    private static Logger LOG = LogManager.getLogger(SmsLoader.class.getSimpleName());

    public static void main(String[] args) {
        Map<String, MapperSelector.Entry> m = loadData(SystemLoader.SystemType.SMS);
        System.out.println(m);
    }

    public static Map<String, MapperSelector.Entry> loadData(SystemLoader.SystemType systemType) {
        Map<String, MapperSelector.Entry> map = new HashMap<>();
        long start = System.currentTimeMillis();
        try {
            Path p = Paths.get(fileName);
            List<String> smsList = Files.readAllLines(p).stream().
                    filter(l -> l.startsWith(systemType.name())).collect(Collectors.toList());
            for (Map.Entry<String, String> e : mapperIdMap.entrySet()) {
                smsList.stream().filter(l -> l.contains(e.getValue())).forEach(l -> {
                    MapperSelector.Entry en = getEntry(l, e.getKey());
                    map.put(en.crc32, en);
                });
            }
        } catch (Exception e) {
            LOG.error("Unable to parse: " + fileName, e);
        }
        LOG.info("Data loaded in ms: " + (System.currentTimeMillis() - start));
        return map;
    }

    private static MapperSelector.Entry getEntry(String str, String mapper) {
        String baseTok = str.split("/")[0];
        String[] tok = baseTok.split("\\s+");
        String[] tok1 = baseTok.split("  ");
        MapperSelector.Entry e = new MapperSelector.Entry();
        e.title = tok1[tok1.length - 1].trim();
        e.crc32 = tok[1].trim();
        e.mapperName = mapper;
        return e;
    }

}
