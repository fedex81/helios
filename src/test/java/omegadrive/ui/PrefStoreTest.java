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

import omegadrive.SystemLoader.SystemType;
import omegadrive.system.MediaSpecHolder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static omegadrive.SystemLoader.SystemType.NONE;
import static omegadrive.SystemLoader.SystemType.S32X;

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

    private static void addSet(String val) {
        PrefStore.addRecentFile(val);
    }

    private static String toOrderedString() {
        return Arrays.toString(PrefStore.getRecentFilesList().toArray());
    }

    @Test
    public void firstRunFill() {
        for (int i = 0; i < PrefStore.recentFileTotal; i++) {
            addSet("test" + i);
        }
        String exp = "[test9, test8, test7, test6, test5, test4, test3, test2, test1, test0]";
        Assert.assertEquals(exp, toOrderedString());
    }

    @Test
    public void wrap() {
        firstRunFill();
        addSet("wrap01");
        String exp = "[wrap01, test9, test8, test7, test6, test5, test4, test3, test2, test1]";
        Assert.assertEquals(exp, toOrderedString());
    }

    @Test
    public void swap() {
        firstRunFill();
        for (int i = 0; i < PrefStore.recentFileTotal; i++) {
            addSet("test" + i);
        }
        addSet("test5");
        List<String> l = PrefStore.getRecentFilesList();
        Assert.assertEquals(l.get(0), "test5");
        String exp = "[test5, test9, test8, test7, test6, test4, test3, test2, test1, test0]";
        Assert.assertEquals(exp, toOrderedString());
    }

    @Test
    public void testItemName() {
        Function<SystemType, MediaSpecHolder> toRomSpec =
                v -> MediaSpecHolder.of(Paths.get("file" + v.hashCode()), v);
        Arrays.stream(SystemType.values()).forEach(v -> {
            if (v != NONE) {
                addSet(toRomSpec.apply(v).toString());
            }
        });
        PrefStore.getRecentFilesList().forEach(str -> {
            MediaSpecHolder r = PrefStore.getRomSpecFromRecentItem(str);
            Assertions.assertNotEquals(NONE, r.systemType);
        });

        PrefStore.initPrefs();
        String[] names = {"file1", ",file2", "oops,file3"};
        Arrays.stream(names).forEach(PrefStoreTest::addSet);
        List<String> l = PrefStore.getRecentFilesList();
        for (int i = 0; i < names.length; i++) {
            String v = l.get(i);
            MediaSpecHolder romSpec = PrefStore.getRomSpecFromRecentItem(v);
            Assertions.assertEquals(NONE, romSpec.systemType);
        }
    }

    @Test
    public void testItemNameParse() {
        PrefStore.initPrefs();
        String path = "Darxide (Europe) (En,Fr,De,Es).32x.zip";
        Path p = Paths.get(path);
        List<SystemType> typ = List.of(NONE, S32X);
        String[] names = new String[]{typ.get(0).name() + "," + path, typ.get(1).name() + "," + path};
        Arrays.stream(names).forEach(PrefStoreTest::addSet);
        List<String> l = PrefStore.getRecentFilesList();
        List<SystemType> expTyp = new ArrayList<>(typ);
        Collections.reverse(expTyp);
        for (int i = 0; i < names.length; i++) {
            String v = l.get(i);
            MediaSpecHolder romSpec = PrefStore.getRomSpecFromRecentItem(v);
            Assertions.assertEquals(expTyp.get(i), romSpec.systemType);
            Assertions.assertEquals(p, romSpec.getBootableMedia().romFile);
        }
    }
}
