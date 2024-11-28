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
import omegadrive.util.RegionDetector;

public interface PsgProvider extends SoundDevice {
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
        public void tick() {
        }

        @Override
        public void write(int data) {
        }

        @Override
        public SampleBufferContext getFrameData() {
            return null;
        }

        @Override
        public void updateRate(RegionDetector.Region region, int clockRate) {
        }
    };
}
