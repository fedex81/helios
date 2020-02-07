/*
 * KeyBindingsHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/07/19 20:22
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

import omegadrive.input.InputProvider;
import omegadrive.input.InputProvider.PlayerNumber;
import omegadrive.input.KeyboardInputHelper;
import omegadrive.joypad.JoypadProvider.JoypadButton;
import omegadrive.system.SystemProvider.SystemEvent;
import omegadrive.util.FileLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.*;

import static javax.swing.KeyStroke.getKeyStroke;

public class KeyBindingsHandler {

    private static Logger LOG = LogManager.getLogger(KeyBindingsHandler.class.getSimpleName());

    protected static final String DIV = "=";
    protected static final String PLAYER_DIV = "\\.";
    protected static final String PLAYER_LINE_HEAD = "P.";
    private static final String configFile = "key.config";
    protected static InputMap keyMap;

    static {
        init();
    }

    public static void init() {
        loadKeyMap();
    }

    private static void loadKeyMap() {
        List<String> l = Collections.emptyList();
        try {
            l = FileLoader.readFileContent(configFile);
            LOG.info("Loading key config file: " + configFile);
        } catch (Throwable t) {
            LOG.warn("Unable to load key config file: " + configFile);
        }
        keyMap = parseConfig(l);
        final List<String> l1 = l;
        Arrays.stream(InputProvider.PlayerNumber.values()).forEach(p -> parsePlayerConfig(l1, p));
    }

    protected static InputMap parseConfig(List<String> str) {
        InputMap m = new InputMap();
        try {
            str.forEach(l -> {
                l = l.trim();
                boolean validLine = !l.isEmpty() && !l.startsWith("#") && !l.startsWith(PLAYER_LINE_HEAD);
                if (validLine) {
                    String[] s = l.split(DIV);
                    m.put(getKeyStroke(s[1]), SystemEvent.valueOf(s[0]));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            return KeyboardInputHelper.DEFAULT_INPUT_MAP;
        }
        return m.size() == 0 ? KeyboardInputHelper.DEFAULT_INPUT_MAP : m;
    }

    protected static void parsePlayerConfig(List<String> str, PlayerNumber number) {
        try {
            Map<String, Integer> playerKeyMap = new HashMap<>();
            str.forEach(l -> {
                l = l.trim();
                boolean validLine = !l.isEmpty() && !l.startsWith("#") && l.startsWith(PLAYER_LINE_HEAD);
                if (validLine) {
                    String[] s = l.split(PLAYER_DIV);
                    PlayerNumber pn = PlayerNumber.valueOf(s[0] + s[1]);
                    if (pn == number) {
                        String[] s1 = s[2].split(DIV);
                        Optional<JoypadButton> btn = getJoypadButton(s1[0]);
                        if (btn.isPresent()) {
                            Optional<KeyStroke> ks = Optional.ofNullable(getKeyStroke(s1[1]));
                            if (ks.isPresent()) {
                                playerKeyMap.put(btn.get().name(), ks.get().getKeyCode());
                            } else {
                                LOG.warn("Unable to parse line: {}", l);
                            }
                        }
                    }
                }
            });
            if (!playerKeyMap.isEmpty()) {
                KeyboardInputHelper.updatePlayerMappings(number, playerKeyMap);
            }
        } catch (Exception e) {
            LOG.warn("Unable to parse key config for player {}", number);
        }
    }

    private static Optional<JoypadButton> getJoypadButton(String configToken) {
        JoypadButton btn = null;
        for (JoypadButton b : JoypadButton.values()) {
            if (b.getMnemonic().equalsIgnoreCase(configToken)) {
                btn = b;
            }
        }
        return Optional.ofNullable(btn);
    }

    public static SystemEvent getSystemEventIfAny(KeyStroke keyStroke) {
        return (SystemEvent) Optional.ofNullable(keyMap.get(keyStroke)).orElse(SystemEvent.NONE);
    }

    public static KeyStroke getKeyStrokeForEvent(SystemEvent event) {
        return Arrays.stream(keyMap.allKeys()).filter(ks -> event == getSystemEventIfAny(ks)).
                findFirst().orElse(null);
    }
}
