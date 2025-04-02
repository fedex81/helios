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

import omegadrive.SystemLoader;
import omegadrive.system.MediaSpecHolder;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

public class PrefStore {
    protected static String PREF_FILENAME = "./helios.prefs";

    private static final Logger LOG = LogHelper.getLogger(PrefStore.class.getSimpleName());

    private static final String RECENT_FILE = "recent";
    private static final String UI_SWING_THEME = "helios.ui.swing.theme";
    public static final int recentFileTotal = 10;
    private static final Properties uiProperties = new Properties();
    public static String lastSaveFile = FileUtil.basePath, lastRomFile = FileUtil.basePath;

    private static final Map<Integer, String> map = new LinkedHashMap<>(recentFileTotal, 1, true);
    private static int uiSwingThemeIndex = 0;

    public static void initPrefs() {
        uiProperties.clear();
        map.clear();
        try (
                FileReader reader = new FileReader(PREF_FILENAME)
        ) {
            uiProperties.load(reader);
        } catch (Exception e) {
            LOG.error("Unable to load properties file: {}", PREF_FILENAME);
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
        uiProperties.putIfAbsent(UI_SWING_THEME, String.valueOf(uiSwingThemeIndex));
    }

    public static void addRecentFile(String path) {
        map.put(path.hashCode(), path);
        //discard the oldest (first) element
        if (map.size() > PrefStore.recentFileTotal) {
            Integer key = map.keySet().stream().findFirst().orElse(0);
            map.remove(key);
        }
    }


    public static List<String> getRecentFilesList() {
        List<String> l = new ArrayList<>(map.values());
        Collections.reverse(l);
        return l;
    }

    public static MediaSpecHolder getRomSpecFromRecentItem(String text) {
        int idx = text.indexOf(',');
        int tknLimit = idx > 0 ? 2 : 0;
        String[] tkn = text.split(",", tknLimit);
        SystemLoader.SystemType st;
        String file;
        try {
            //"<systemType>,<filePath>", ie: "MD,<filePath>"
            st = SystemLoader.SystemType.valueOf(getSystemStringFromRecentItem(text));
            file = tkn[1];
        } catch (Exception e) {
            //"<filePath>"
            st = SystemLoader.SystemType.NONE;
            file = tkn[0];
        }
        return MediaSpecHolder.of(Path.of(file), st);
    }

    public static String getSystemStringFromRecentItem(String text) {
        String sys = SystemLoader.SystemType.NONE.name();
        int idx = text.indexOf(',');
        int tknLimit = idx > 0 ? 2 : 0;
        if (tknLimit == 0) {
            return sys;
        }
        try {
            String[] tkn = text.split(",", tknLimit);
            //"<systemType>,<filePath>", ie: "MD,<filePath>"
            sys = tkn[0];
        } catch (Exception ignored) {
        }
        return sys;
    }

    public static int getSwingUiThemeIndex() {
        return Integer.parseInt(uiProperties.getProperty(UI_SWING_THEME));
    }

    public static void setSwingUiThemeIndex(int value) {
        uiSwingThemeIndex = value;
    }

    private static void toProps() {
        uiProperties.clear();
        List<String> l = getRecentFilesList();
        Iterator<String> it = l.iterator();
        for (int i = 0; i < l.size(); i++) {
            String val = it.hasNext() ? it.next() : "";
            uiProperties.put(RECENT_FILE + "." + i, val);
        }
        uiProperties.setProperty(UI_SWING_THEME, String.valueOf(uiSwingThemeIndex));
    }

    public static void close() {
        try (
                FileWriter writer = new FileWriter(PREF_FILENAME)
        ) {
            toProps();
            uiProperties.store(writer, "");
            writer.flush();
        } catch (Exception e) {
            LOG.error("Unable to store to properties file: {}", PREF_FILENAME, e);
        }
    }
}
