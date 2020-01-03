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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class PrefStoreTest {

    @Before
    public void before() {
        PrefStore.PREF_FILENAME = "noFile";
        PrefStore.initPrefs();
    }

    @Test
    public void firstRun() {
        String openFile = "test01";
        PrefStore.addRecentFile(openFile);
        List<String> l = PrefStore.getRecentFilesList();
        Assert.assertEquals(l.get(0), openFile);
    }

    @Test
    public void firstRunFill() {
        for (int i = 0; i < PrefStore.recentFileTotal; i++) {
            PrefStore.addRecentFile("test" + i);
        }

        List<String> l = PrefStore.getRecentFilesList();
        for (int i = 0; i < PrefStore.recentFileTotal; i++) {
            Assert.assertEquals(l.get(i), "test" + i);
        }
    }

    @Test
    public void wrap() {
        firstRunFill();
        PrefStore.addRecentFile("wrap01");
        List<String> l = PrefStore.getRecentFilesList();
        Assert.assertEquals(l.get(0), "wrap01");
    }

}
