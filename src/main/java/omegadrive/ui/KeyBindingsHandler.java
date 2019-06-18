/*
 * KeyBindingsHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/06/19 17:15
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

import omegadrive.system.SystemProvider.SystemEvent;
import omegadrive.util.FileLoader;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.awt.event.KeyEvent.*;
import static javax.swing.KeyStroke.getKeyStroke;
import static omegadrive.system.SystemProvider.SystemEvent.*;

public class KeyBindingsHandler {

    public static final InputMap DEFAULT_INPUT_MAP = new InputMap();

    private static final String DIV = "=";
    private static final String configFile = "key.config";
    private static InputMap keyMap;

    static {
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_F, CTRL_DOWN_MASK), TOGGLE_FULL_SCREEN);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_D, CTRL_DOWN_MASK), TOGGLE_DEBUG_LOGGING);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_1, CTRL_DOWN_MASK), SET_PLAYERS_1);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_2, CTRL_DOWN_MASK), SET_PLAYERS_2);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_P, CTRL_DOWN_MASK), TOGGLE_PAUSE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_M, CTRL_DOWN_MASK), TOGGLE_MUTE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_R, CTRL_DOWN_MASK), RESET);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_9, CTRL_DOWN_MASK), QUICK_LOAD);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_8, CTRL_DOWN_MASK), QUICK_SAVE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_7, CTRL_DOWN_MASK), LOAD_STATE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_6, CTRL_DOWN_MASK), SAVE_STATE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_L, CTRL_DOWN_MASK), NEW_ROM);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_ESCAPE, CTRL_DOWN_MASK), CLOSE_ROM);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_R, CTRL_DOWN_MASK | ALT_DOWN_MASK), TOGGLE_SOUND_RECORD);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_ESCAPE, CTRL_DOWN_MASK | ALT_DOWN_MASK), CLOSE_APP);
        loadKeyMap();
    }

    private static void loadKeyMap() {
        List<String> l = FileLoader.loadFileContent(configFile);
        keyMap = parseConfig(l);
    }

    private static InputMap parseConfig(List<String> str) {
        InputMap m = new InputMap();
        try {
            str.forEach(l -> {
                String[] s = l.split(DIV);
                m.put(getKeyStroke(s[1]), SystemEvent.valueOf(s[0]));
            });
        } catch (Exception e) {
            e.printStackTrace();
            return DEFAULT_INPUT_MAP;
        }
        return m.size() == 0 ? DEFAULT_INPUT_MAP : m;
    }

    public static SystemEvent getSystemEventIfAny(KeyStroke keyStroke) {
        return (SystemEvent) Optional.ofNullable(keyMap.get(keyStroke)).orElse(SystemEvent.NONE);
    }

    public static KeyStroke getKeyStrokeForEvent(SystemEvent event) {
        return Arrays.stream(keyMap.allKeys()).filter(ks -> event == getSystemEventIfAny(ks)).
                findFirst().orElse(null);
    }

    private static List<String> toConfigList(InputMap m) {
        List<String> l = new ArrayList<>();
        for (KeyStroke ks : m.allKeys()) {
            l.add(m.get(ks).toString() + DIV + ks.toString());
        }
        Collections.sort(l);
        return l;
    }

    private static String toConfigString(InputMap m) {
        return toConfigList(m).stream().collect(Collectors.joining("\n"));
    }
}
