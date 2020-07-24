/*
 * PrefStore
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 10/10/19 19:50
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

package omegadrive.ui;

import omegadrive.util.FileLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class PrefStore {
    protected static String PREF_FILENAME = "./helios.prefs";

    private static Logger LOG = LogManager.getLogger(PrefStore.class.getSimpleName());

    private static String RECENT_FILE = "recent";
    public static int recentFileTotal = 10;
    private static Properties uiProperties = new Properties();

    public static String lastRomFolder = FileLoader.basePath;
    public static String lastSaveFolder = FileLoader.basePath;

    private static LinkedHashMap<Integer, String> map = new LinkedHashMap<>(recentFileTotal, 1, true);

    public static void initPrefs() {
        uiProperties.clear();
        map.clear();
        try (
                FileReader reader = new FileReader(PREF_FILENAME)
        ) {
            uiProperties.load(reader);
        } catch (Exception e) {
            LOG.error("Unable to load properties file: " + PREF_FILENAME);
        } finally {
            bootstrapIfNecessary();
        }
    }

    private static void bootstrapIfNecessary() {
        for (int i = recentFileTotal - 1; i >= 0; i--) {
            String key = RECENT_FILE + "." + i;
            uiProperties.putIfAbsent(key, "");
            addRecentFile(uiProperties.getProperty(key));
        }
    }

    public static void addRecentFile(String path) {
        map.put(path.hashCode(), path);
        //discard the oldest (first) element
        if (map.size() > PrefStore.recentFileTotal) {
            map.remove(map.keySet().toArray()[0]);
        }
    }


    public static List<String> getRecentFilesList() {
        List<String> l = new ArrayList<>(map.values());
        Collections.reverse(l);
        return l;
    }

    private static void toProps() {
        uiProperties.clear();
        List<String> l = getRecentFilesList();
        Iterator<String> it = l.iterator();
        for (int i = 0; i < l.size(); i++) {
            String val = it.hasNext() ? it.next() : "";
            uiProperties.put(RECENT_FILE + "." + i, val);
        }
    }

    public static void close() {
        try (
                FileWriter writer = new FileWriter(PREF_FILENAME)
        ) {
            toProps();
            uiProperties.store(writer, "");
            writer.flush();
        } catch (Exception e) {
            LOG.error("Unable to store to properties file: " + PREF_FILENAME, e);
        }
    }
}
