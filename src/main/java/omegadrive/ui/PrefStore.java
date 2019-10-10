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

import com.google.common.base.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PrefStore {
    private static final String PREF_FILENAME = "./helios.prefs";

    private static Logger LOG = LogManager.getLogger(PrefStore.class.getSimpleName());

    private static String RECENT_FILE = "recent";
    public static int recentFileTotal = 10;
    private static Properties uiProperties = new Properties();
    private static int nextSlot;

    public static void initPrefs() {
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
        for (int i = 0; i < recentFileTotal; i++) {
            String key = RECENT_FILE + "." + i;
            Object res = uiProperties.putIfAbsent(key, "");
            if (res == null && nextSlot < 0) {
                nextSlot = i;
            }
        }
        nextSlot = nextSlot < 0 ? 0 : nextSlot;
    }

    public static void addRecentFile(String path) {
        if (uiProperties.contains(path)) {
            return;
        }
        for (int i = 0; i < recentFileTotal; i++) {
            String key = RECENT_FILE + "." + i;
            boolean free = Strings.isNullOrEmpty(uiProperties.getProperty(key, ""));
            if (free) {
                uiProperties.put(key, path);
                return;
            }
        }
        uiProperties.put(RECENT_FILE + "." + nextSlot, path);
        nextSlot = (nextSlot + 1) % recentFileTotal;
    }

    public static List<String> getRecentFilesList() {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < recentFileTotal; i++) {
            String key = RECENT_FILE + "." + i;
            l.add(uiProperties.getProperty(key, ""));
        }
        return l;
    }

    public static void close() {
        try (
                FileWriter writer = new FileWriter(PREF_FILENAME)
        ) {
            uiProperties.store(writer, "");
            writer.flush();
        } catch (Exception e) {
            LOG.error("Unable to load properties file: " + PREF_FILENAME);
        }
    }

}
