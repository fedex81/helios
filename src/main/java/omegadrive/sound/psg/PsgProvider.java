/*
 * PsgProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

package omegadrive.sound.psg;

import omegadrive.Device;
import omegadrive.sound.psg.msx.Ay38910Psg;
import omegadrive.sound.psg.white.SN76489Psg;
import omegadrive.util.RegionDetector;

import static omegadrive.sound.SoundProvider.LOG;
import static omegadrive.sound.SoundProvider.getPsgSoundClock;

public interface PsgProvider extends Device {

    static PsgProvider createSnInstance(RegionDetector.Region region, int sampleRate) {
        int clockHz = (int) getPsgSoundClock(region);
        LOG.info("PSG instance, clockHz: " + clockHz + ", sampleRate: " + sampleRate);
        return SN76489Psg.createInstance(clockHz, sampleRate);
    }

    static PsgProvider createAyInstance(RegionDetector.Region region, int sampleRate) {
        int clockHz = (int) getPsgSoundClock(region);
        LOG.info("PSG instance, clockHz: " + clockHz + ", sampleRate: " + sampleRate);
        PsgProvider psgProvider = Ay38910Psg.createInstance(sampleRate);
        return psgProvider;
    }

    //SN style PSG
    void write(int data);

    void output(byte[] output, int offset, int end);

    //AY style psg
    default void write(int register, int data) {
        write(data);
    }

    //AY style psg
    default int read(int register) {
        return 0xFF;
    }

    default void output(byte[] output) {
        output(output, 0, output.length);
    }

    PsgProvider NO_SOUND = new PsgProvider() {

        @Override
        public void write(int data) {

        }

        @Override
        public void output(byte[] output, int offset, int end) {

        }
    };
}
