/*
 * Copyright (c) 2018-2019 Federico Berti
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

package omegadrive.sound.vgm;

import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.YM2612;
import uk.co.omgdrv.simplevgm.Runner;
import uk.co.omgdrv.simplevgm.VGMPlayer;
import uk.co.omgdrv.simplevgm.model.VgmFmProvider;
import uk.co.omgdrv.simplevgm.model.VgmPsgProvider;
import uk.co.omgdrv.simplevgm.psg.SmsApu;

import java.nio.file.Path;
import java.nio.file.Paths;

public class VgmPlay {

    public static void main(String[] args) throws Exception {
        Path folder = Paths.get("ab.vgz");
        System.out.println(folder.toAbsolutePath());
        VgmPsgProvider vgmPsgProvider = new SmsApu();
        VgmFmProvider vgmFmProvider = createVgmFmProvider(new YM2612());
//        VgmFmProvider vgmFmProvider = createVgmFmProvider(new Ym2612Nuke());
        VGMPlayer vgmPlayer = VGMPlayer.createInstance(vgmPsgProvider, vgmFmProvider, 44100);
        Runner.playRecursive(vgmPlayer, folder);

//        Runner.main(new String[]{"mss.vgm"});
    }

    private static VgmFmProvider createVgmFmProvider(FmProvider ym2612) {
        return new VgmFmProvider() {
            @Override
            public int reset() {
                ym2612.reset();
                return 0;
            }

            @Override
            public int init(int clock, int rate) {
                return ym2612.init(clock, rate);
            }

            @Override
            public void update(int[] ints, int offset, int end) {
                ym2612.update(ints, offset, end);
            }

            @Override
            public void write0(int address, int data) {
                ym2612.write(FmProvider.FM_ADDRESS_PORT0, address);
                ym2612.write(FmProvider.FM_DATA_PORT0, data);
            }

            @Override
            public void write1(int address, int data) {
                ym2612.write(FmProvider.FM_ADDRESS_PORT1, address);
                ym2612.write(FmProvider.FM_DATA_PORT1, data);
            }

            private void logWrite(int address, int data) {
                String str = String.format("write addr: %s, data: %s",
                        Integer.toHexString(address), Integer.toHexString(data));
                System.out.println(str);
            }
        };
    }
}