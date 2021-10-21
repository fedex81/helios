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

import omegadrive.cart.mapper.MapperSelector;
import omegadrive.util.FileLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Roms db from blastem
 * https://www.retrodev.com/repos/blastem/file/tip/rom.db
 */
public class MdLoader {

    public static final MdRomDbModel.Entry NO_ENTRY = new MdRomDbModel.Entry();
    private static final Logger LOG = LogManager.getLogger(MdLoader.class.getSimpleName());
    static String fileName = MapperSelector.ROM_DB_BASE_FOLDER + "rom.db";
    private static final Set<MdRomDbModel.Entry> entrySet = new HashSet<>();
    private static final Map<String, MdRomDbModel.Entry> map = new HashMap<>();

    private static Map<String, MdRomDbModel.Entry> getMap() {
        if (map.isEmpty()) {
            init();
        }
        return map;
    }

    public static MdRomDbModel.Entry getEntry(String serial) {
        final String sn = serial.substring(3, serial.length() - 3).trim();
        return getMap().getOrDefault(sn, NO_ENTRY);
    }

    public static void main(String[] args) {
        init();
        entrySet.stream().forEach(System.out::println);
    }

    private static void init() {
        List<String> lines = FileLoader.readFileContent(fileName);
        processData(lines);
        map.clear();
        entrySet.forEach(e -> map.put(e.getId(), e));
    }

    private static void processData(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            boolean start = s.length() > 0 && Character.isLetterOrDigit(s.charAt(0));
            if (start) {
                try {
                    i = processEntry(lines, i);
                } catch (Exception e) {
                    LOG.error("Unable to process entry {}, at line: {}", s, i, e);
                }
            }
        }
    }

    private static int processEntry(List<String> lines, int start) {
        String id = lines.get(start).replace(MdRomDbModel.START_OBJ_TOKEN, "").trim();
        MdRomDbModel.Entry e = new MdRomDbModel.Entry();
        e.data.put("id", id);
        entrySet.add(e);
        return processLine(lines, e.data, start);
    }

    private static int processLine(List<String> lines, Map<String, Object> map, int start) {
        boolean stop = false;
        int i = start + 1;
        for (; !stop; i++) {
            String line = lines.get(i).trim();
            if (line.startsWith(MdRomDbModel.COMMENT_TOKEN) || line.isEmpty()) {
                continue;
            }
            if (line.contains(MdRomDbModel.START_OBJ_TOKEN)) {
                String id = line.replace(MdRomDbModel.START_OBJ_TOKEN, "").trim();
                i = processObject(lines, map, id, i);
            } else if (line.startsWith(MdRomDbModel.END_OBJ_TOKEN)) {
                stop = true;
            } else {
                int idx = line.indexOf(MdRomDbModel.FIELD_SEP_TOKEN);
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String value = line.substring(idx).trim();
                    map.put(key, value);
                } else {
                    LOG.warn("Unable to parse line: {}", line);
                }
            }
        }
        return i - 1;
    }

    private static int processObject(List<String> lines, Map<String, Object> parentMap, String id, int start) {
        Map<String, Object> childMap = new HashMap<>();
        parentMap.put(id.toUpperCase(), childMap);
        start = processLine(lines, childMap, start);
        return start;
    }
}
