/*
 * DACTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 15:33
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

package omegadrive.sound;

import omegadrive.SystemLoader;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DACTest {

    private static Path fileFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "dac");

    static SoundProvider sp = AbstractSoundManager.createSoundProvider(SystemLoader.SystemType.GENESIS, RegionDetector.Region.EUROPE);

    public static void main(String[] args) throws IOException {
        System.out.println(new File(".").getAbsolutePath());
        List<String> lines = Files.readAllLines(fileFolder.resolve("sonic1_dac_timings.txt"));
        List<Integer> samples = lines.stream().map(l -> Integer.valueOf(l.split(",")[2])).collect(Collectors.toList());
        List<String> lines1 = Files.readAllLines(fileFolder.resolve("sf2_dac_sample01.txt"));
        List<Integer> samples1 = lines1.stream().map(l -> Integer.valueOf(l.split(",")[2])).collect(Collectors.toList());
        sp.getFm().reset();

        playDAC(samples, 1);
        playDAC(samples1, 5);

        sp.reset();
        sp.close();
    }

    private static void playDAC(List<Integer> samples, int multisampling) {
        setDacEnable(true);
        for (int i = 0; i < samples.size(); i++) {
            final int sample = samples.get(i);
            IntStream.range(0, multisampling).forEach(k -> writeDacSample(sample));
        }
        setDacEnable(false);
        Util.sleep(2_000);
    }

    private static void setDacEnable(boolean value) {
        sp.getFm().write(MdFmProvider.FM_ADDRESS_PORT0, 0x2B);
        sp.getFm().write(MdFmProvider.FM_DATA_PORT0, value ? 0x80 : 0);
    }

    private static void writeDacSample(int data) {
        sp.getFm().write(MdFmProvider.FM_ADDRESS_PORT0, 0x2A);
        sp.getFm().write(MdFmProvider.FM_DATA_PORT0, data);
    }
}
