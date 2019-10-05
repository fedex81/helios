/*
 * PrefStore
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 05/10/19 14:22
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.util.Properties;

public class PrefStore {
    private static final String PREF_FILENAME = "./helios.prefs";

    private static Logger LOG = LogManager.getLogger(PrefStore.class.getSimpleName());

    private static String RECENT_FILE = "recent";
    private static int recentFileTotal = 10;
    private static Properties uiProperties = new Properties();

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
            uiProperties.putIfAbsent(key, "");
        }
    }


}
