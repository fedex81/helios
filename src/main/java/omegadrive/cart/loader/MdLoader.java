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

import omegadrive.cart.MdCartInfoProvider;
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

    public static final Entry NO_EEPROM = new Entry();
    static String fileName = MapperSelector.ROM_DB_BASE_FOLDER + "rom.db";
    private static Logger LOG = LogManager.getLogger(MdLoader.class.getSimpleName());
    private static Set<Entry> entrySet = new HashSet<>();
    private static Map<String, Entry> map = new HashMap<>();

    private static Map<String, Entry> getMap() {
        if (map.isEmpty()) {
            init();
        }
        return map;
    }

    public static Entry getEntry(String serial) {
        final String sn = serial.substring(3, serial.length() - 3).trim();
        return getMap().getOrDefault(sn, NO_EEPROM);
    }

    public static void main(String[] args) {
        init();
        entrySet.stream().forEach(System.out::println);
    }

    private static void init() {
        List<String> lines = FileLoader.loadFileContent(fileName);
        processData(lines);
        map.clear();
        entrySet.forEach(e -> map.put(e.id, e));
    }

    public static void testLoading(MdCartInfoProvider cart) {
        init();
        String s = cart.getSerial();
        final String sn = s.substring(3, s.length() - 3).trim();
        entrySet.forEach(e -> {
            if (e.id.equalsIgnoreCase(sn)) {
                LOG.info("Matching entry: {}", e);
            }
        });
    }

    private static void processData(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            boolean start = s.length() > 0 && Character.isLetterOrDigit(s.charAt(0));
            if (start) {
                i = processEntry(lines, i);
            }
        }
    }

    private static int processEntry(List<String> lines, int start) {
        String id = lines.get(start).replace("{", "").trim();
        Entry e = new Entry();
        e.id = id;
        boolean stop = false;
        int i = start + 1;
        for (; !stop; i++) {
            String line = lines.get(i);
            if (line.contains("name ")) {
                String s = lines.get(i).replace("name ", "").trim();
                e.name = s;
            } else if (line.trim().startsWith("EEPROM")) {
                i = processEEPROM(e, lines, i);
            } else if (line.startsWith("}")) {
                stop = true;
                entrySet.add(e);
            }
        }
        return i - 1;
    }

    private static int processEEPROM(Entry e, List<String> lines, int start) {
        EEPROM eeprom = null;
        boolean stop = false;
        int i = start + 1;
        for (; !stop; i++) {
            String line = lines.get(i);
            if (line.contains("type ")) {
                eeprom = new EEPROM();
                String s = line.replace("type ", "").trim();
                eeprom.type = s;
            } else if (line.contains("size ")) {
                String s = line.replace("size ", "").trim();
                eeprom.size = Integer.valueOf(s);
            } else if (line.trim().startsWith("}")) {
                stop = true;
                e.eeprom = eeprom;
            }
        }
        return i - 1;
    }

    public static class Entry {
        public String id;
        public String name;
        public EEPROM eeprom;

        @Override
        public String toString() {
            return "Entry{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", eeprom=" + eeprom +
                    '}';
        }
    }

    public static class EEPROM {
        public String type;
        public int size;

        @Override
        public String toString() {
            return "EEPROM{" +
                    "type='" + type + '\'' +
                    ", size=" + size +
                    '}';
        }
    }

}
