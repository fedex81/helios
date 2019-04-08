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
import omegadrive.sound.psg.white1.SN76496;
import omegadrive.sound.psg.white2.SN76489Psg;
import omegadrive.util.RegionDetector;

import static omegadrive.sound.SoundProvider.*;

public interface PsgProvider extends Device {

    int PSG_OUTPUT_SAMPLE_SIZE = 8;
    int PSG_OUTPUT_CHANNELS = 1;

    boolean USE_NEW_PSG = true;

    static PsgProvider createSnInstance(RegionDetector.Region region, int sampleRate) {
        int clockHz = (int) getPsgSoundClock(region);
        LOG.info("PSG instance, clockHz: " + clockHz + ", sampleRate: " + sampleRate);
        PsgProvider psgProvider = SN76489Psg.createInstance(clockHz, sampleRate);
        if (!USE_NEW_PSG) {
            psgProvider = new SN76496(getPsgSoundClockScaled(region), sampleRate);
            psgProvider.init();
        }
        return psgProvider;
    }

    static PsgProvider createAyInstance(RegionDetector.Region region, int sampleRate) {
        int clockHz = (int) getPsgSoundClock(region);
        LOG.info("PSG instance, clockHz: " + clockHz + ", sampleRate: " + sampleRate);
        PsgProvider psgProvider = Ay38910Psg.createInstance(clockHz, sampleRate);
        return psgProvider;
    }

    //SN style PSG
    void write(int data);

    //AY style psg
    default void write(int register, int data){
        write(data);
    }

    //AY style psg
    default int read(int register){
        return 0xFF;
    }

    void output(byte[] output);

    void output(byte[] output, int offset, int end);

    PsgProvider NO_SOUND = new PsgProvider() {

        @Override
        public void write(int data) {

        }

        @Override
        public void output(byte[] ouput) {

        }

        @Override
        public void output(byte[] output, int offset, int end) {

        }
    };
}
