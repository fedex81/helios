/*
 * KeyboardInput
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/10/19 17:32
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

package omegadrive.input;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import omegadrive.joypad.JoypadProvider.JoypadButton;
import omegadrive.ui.KeyBindingsHandler;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import javax.swing.*;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static java.awt.event.KeyEvent.*;
import static javax.swing.KeyStroke.getKeyStroke;
import static omegadrive.input.InputProvider.PlayerNumber;
import static omegadrive.joypad.JoypadProvider.JoypadButton.*;
import static omegadrive.system.SystemProvider.SystemEvent.*;

public class KeyboardInputHelper {

    public static final Map<String, Integer> DEFAULT_P1_KEY_BINDINGS = ImmutableMap.<String, Integer>builder().
            put(U.name(), VK_UP).
            put(D.name(), VK_DOWN).
            put(L.name(), VK_LEFT).
            put(R.name(), VK_RIGHT).
            put(A.name(), VK_I).
            put(B.name(), VK_O).
            put(C.name(), VK_P).
            put(M.name(), VK_L).
            put(S.name(), VK_ENTER).build();
    public static final Map<String, Integer> DEFAULT_P2_KEY_BINDINGS = ImmutableMap.<String, Integer>builder().
            put(U.name(), VK_T).
            put(D.name(), VK_G).
            put(L.name(), VK_F).
            put(R.name(), VK_G).
            put(A.name(), VK_C).
            put(B.name(), VK_V).
            put(C.name(), VK_B).
            put(M.name(), VK_N).
            put(S.name(), VK_SPACE).build();
    public static final Table<PlayerNumber, String, Integer> keyboardStringBindings = HashBasedTable.create();
    public static final Table<PlayerNumber, Integer, String> keyboardInverseStringBindings = HashBasedTable.create();
    public static final Table<PlayerNumber, Integer, JoypadButton> keyboardInverseBindings = HashBasedTable.create();
    public static final Table<PlayerNumber, JoypadButton, Integer> keyboardBindings = HashBasedTable.create();
    public static final InputMap DEFAULT_INPUT_MAP = new InputMap();
    protected static final Logger LOG = LogHelper.getLogger(KeyboardInputHelper.class.getSimpleName());

    static {
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_ENTER, ALT_DOWN_MASK), TOGGLE_FULL_SCREEN);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_T, CTRL_DOWN_MASK), TOGGLE_THROTTLE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_D, CTRL_DOWN_MASK), SHOW_FPS);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_P, CTRL_DOWN_MASK), TOGGLE_PAUSE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_M, CTRL_DOWN_MASK), SOUND_ENABLED);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_R, CTRL_DOWN_MASK), RESET);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_R, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), SOFT_RESET);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_9, CTRL_DOWN_MASK), QUICK_LOAD);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_1, CTRL_DOWN_MASK), QUICK_SAVE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_7, CTRL_DOWN_MASK), LOAD_STATE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_6, CTRL_DOWN_MASK), SAVE_STATE);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_L, CTRL_DOWN_MASK), NEW_ROM);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_ESCAPE, CTRL_DOWN_MASK), CLOSE_ROM);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_S, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), TOGGLE_SOUND_RECORD);
        DEFAULT_INPUT_MAP.put(getKeyStroke(VK_ESCAPE, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), CLOSE_APP);
        updatePlayerMappings(PlayerNumber.P1, DEFAULT_P1_KEY_BINDINGS);
        updatePlayerMappings(PlayerNumber.P2, DEFAULT_P2_KEY_BINDINGS);
        KeyBindingsHandler.getInstance(); //force init
    }

    public static void init() {
        //force init
    }

    public static void updatePlayerMappings(PlayerNumber number, Map<String, Integer> map) {
        keyboardStringBindings.row(number).clear();
        keyboardInverseStringBindings.row(number).clear();
        keyboardInverseBindings.row(number).clear();
        keyboardBindings.row(number).clear();
        map.entrySet().stream().forEach(e -> {
            keyboardStringBindings.put(number, e.getKey(), e.getValue());
            keyboardInverseStringBindings.put(number, e.getValue(), e.getKey());
            keyboardInverseBindings.put(number, e.getValue(), JoypadButton.valueOf(e.getKey()));
            keyboardBindings.put(number, JoypadButton.valueOf(e.getKey()), e.getValue());
        });
        consistencyCheck(number);
    }

    private static void consistencyCheck(PlayerNumber number) {
        Set<Integer> pSet = new TreeSet<>(keyboardBindings.row(number).values());
        for (PlayerNumber pn : PlayerNumber.values()) {
            if (pn == number) {
                continue;
            }
            Set<Integer> pSet2 = new TreeSet<>(keyboardBindings.row(pn).values());
            Set<Integer> s = Sets.intersection(pSet, pSet2);
            if (!s.isEmpty()) {
                LOG.error("Illegal controller setup, {} vs {}", number, pn);
                for (Integer key : s) {
                    KeyStroke ks = KeyStroke.getKeyStroke((char) key.intValue());
                    LOG.error("{}  button {}->{}, {} button {}->{}", number,
                            keyboardInverseBindings.get(number, key).getMnemonic(), ks,
                            pn, keyboardInverseBindings.get(pn, key).getMnemonic(), ks);
                }
            }
        }
    }
}
