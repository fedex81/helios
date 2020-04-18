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
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static omegadrive.ui.KeyBindingsHandler.*;

public class KeyBindingsHandlerTest {

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
