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

import omegadrive.input.InputProvider.PlayerNumber;
import omegadrive.input.KeyboardInputHelper;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static omegadrive.ui.KeyBindingsHandler.*;

public class KeyBindingsHandlerTest {

    private static List<String> toConfigList() {
        List<String> l = new ArrayList<>();
        for (KeyStroke ks : keyMap.allKeys()) {
            l.add(keyMap.get(ks).toString() + DIV + ks.toString());
        }
        Collections.sort(l);
        KeyboardInputHelper.keyboardBindings.cellSet().stream().forEach(cell -> {
            String tk = PLAYER_LINE_HEAD + cell.getRowKey().name().substring(1) + ".";
            tk += cell.getColumnKey().getMnemonic() + DIV;
            tk += KeyEvent.getKeyText(cell.getValue()).toUpperCase();
            l.add(tk);
        });
        return l;
    }

    private static String toConfigString() {
        return toConfigList().stream().collect(Collectors.joining("\n"));
    }

    @Test
    public void testParsing() {
        KeyBindingsHandler.getInstance();
        String str1 = toConfigString();
        List<String> l = Arrays.asList(str1.split("\\n"));
        parseConfig(l);
        parsePlayerConfig(l, PlayerNumber.P1);
        parsePlayerConfig(l, PlayerNumber.P2);
        String str2 = toConfigString();
        Assert.assertEquals(str1, str2);
        System.out.println(str1);
    }
}
