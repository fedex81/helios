/*
 * BlipSN76489Psg
 * Copyright (c) 2025 Federico Berti
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

package omegadrive.sound.psg.msx;

import omegadrive.SystemLoader.SystemType;
import omegadrive.sound.psg.BlipCapableDevice;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

import static omegadrive.util.SoundUtil.AF_8bit_Mono;

public class BlipAy38910Psg extends BlipCapableDevice implements PsgProvider {

    private final static Logger LOG = LogHelper.getLogger(BlipAy38910Psg.class.getSimpleName());
    private static final AudioFormat audioFormat = AF_8bit_Mono;

    protected Ay38910 psg;

    public static BlipAy38910Psg createInstance(SystemType systemType, RegionDetector.Region region, int outputSampleRate) {
        BlipAy38910Psg s = new BlipAy38910Psg(systemType);
        s.psg = new Ay38910(outputSampleRate);
        return s;
    }

    private BlipAy38910Psg(SystemType systemType) {
        super(systemType, systemType + "-psg-ay", audioFormat);
    }

    @Override
    public int read(int register) {
        return psg.in(register);
    }

    @Override
    public void write(int register, int data) {
        psg.out(register, data);
    }

    @Override
    public void fillBuffer(byte[] output, int offset, int end) {
        for (int i = offset; i < end; i++) {
            output[i] = (byte) psg.getSoundSigned();
        }
    }

    @Override
    public SoundDeviceType getType() {
        return SoundDeviceType.PSG;
    }
}
