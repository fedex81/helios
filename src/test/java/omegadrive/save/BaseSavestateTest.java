/*
 * SmsSavestateTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 24/10/19 18:49
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

package omegadrive.save;

import omegadrive.Device;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.JunitTestUtil;
import z80core.Z80State;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;

@org.junit.jupiter.api.Disabled
public abstract class BaseSavestateTest {

    public static Path baseSaveStateFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "savestate");

    protected abstract void testLoadSaveInternal(Path saveFile);

    protected void testLoadAndSave(Path saveStateFolder, String fileExt) throws IOException {
        Set<Path> files = MdSavestateTest.getSavestateList(saveStateFolder, fileExt);
        assertFalse(files.isEmpty());
        for (Path saveFile : files) {
            System.out.println("Testing: " + saveFile.getFileName());
            testLoadSaveInternal(saveFile);
        }
    }

    protected void compareMemory(IMemoryProvider mem1, IMemoryProvider mem2) {
        IntStream.range(0, mem1.getRamSize()).forEach(i ->
                JunitTestUtil.assertEquals("8k Ram" + i, mem1.readRamByte(i), mem2.readRamByte(i)));
    }

    protected void compareZ80(Z80Provider z80p1, Z80Provider z80p2) {
        Z80State s1 = z80p1.getZ80State();
        Z80State s2 = z80p2.getZ80State();
        JunitTestUtil.assertEquals("AF", s1.getRegAF(), s2.getRegAF());
        JunitTestUtil.assertEquals("BC", s1.getRegBC(), s2.getRegBC());
        JunitTestUtil.assertEquals("DE", s1.getRegDE(), s2.getRegDE());
        JunitTestUtil.assertEquals("HL", s1.getRegHL(), s2.getRegHL());
        JunitTestUtil.assertEquals("I", s1.getRegI(), s2.getRegI());
        JunitTestUtil.assertEquals("IX", s1.getRegIX(), s2.getRegIX());
        JunitTestUtil.assertEquals("IY", s1.getRegIY(), s2.getRegIY());
        JunitTestUtil.assertEquals("PC", s1.getRegPC(), s2.getRegPC());
        JunitTestUtil.assertEquals("SP", s1.getRegSP(), s2.getRegSP());
        JunitTestUtil.assertEquals("AFx", s1.getRegAFx(), s2.getRegAFx());
        JunitTestUtil.assertEquals("BCx", s1.getRegBCx(), s2.getRegBCx());
        JunitTestUtil.assertEquals("DEx", s1.getRegDEx(), s2.getRegDEx());
        JunitTestUtil.assertEquals("HLx", s1.getRegHLx(), s2.getRegHLx());
        JunitTestUtil.assertEquals("IFF1", s1.isIFF1(), s2.isIFF1());
        JunitTestUtil.assertEquals("IFF2", s1.isIFF2(), s2.isIFF2());
        JunitTestUtil.assertEquals("IM", s1.getIM(), s2.getIM());
    }

    protected <T extends Device> T getDevice(BaseBusProvider b, Class<T> clazz) {
        return b.getBusDeviceIfAny(clazz).get();
    }
}
