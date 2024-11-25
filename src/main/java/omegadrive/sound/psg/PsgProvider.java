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

import omegadrive.sound.SoundDevice;
import omegadrive.sound.psg.msx.Ay38910Psg;
import omegadrive.sound.psg.white.BlipSN76489Psg;
import omegadrive.util.RegionDetector;

import static omegadrive.sound.SoundProvider.LOG;
import static omegadrive.sound.SoundProvider.getPsgSoundClock;

public interface PsgProvider extends SoundDevice {

    static PsgProvider createSnInstance(RegionDetector.Region region, int sampleRate) {
        int clockHz = (int) getPsgSoundClock(region);
        LOG.info("PSG instance, region: {}, clockHz: {}, sampleRate: {}", region, clockHz, sampleRate);
        return BlipSN76489Psg.createInstance(region, sampleRate);
    }

    static PsgProvider createAyInstance(RegionDetector.Region region, int sampleRate) {
        int clockHz = (int) getPsgSoundClock(region);
        LOG.info("PSG instance, clockHz: {}, sampleRate: {}", clockHz, sampleRate);
        return Ay38910Psg.createInstance(sampleRate);
    }

    //SN style PSG
    void write(int data);

    //AY style psg
    default void write(int register, int data) {
        write(data);
    }

    //AY style psg
    default int read(int register) {
        return 0xFF;
    }

    @Override
    default SoundDeviceType getType() {
        return SoundDeviceType.PSG;
    }

    PsgProvider NO_SOUND = new PsgProvider() {
        @Override
        public void write(int data) {
        }

        @Override
        public void updateMono8(byte[] output, int offset, int end) {
        }
    };

    default void onNewFrame() {
    }

    default void tick() {
    }

    default void updateRate(RegionDetector.Region region, int clockRate) {
    }
}
