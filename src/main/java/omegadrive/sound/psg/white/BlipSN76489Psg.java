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

package omegadrive.sound.psg.white;

import omegadrive.sound.psg.BlipPsgHandler;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

import static omegadrive.sound.SoundProvider.getPsgSoundClock;
import static omegadrive.util.SoundUtil.AF_8bit_Mono;

public class BlipSN76489Psg extends BlipPsgHandler {

    private final static Logger LOG = LogHelper.getLogger(BlipSN76489Psg.class.getSimpleName());

    private static final AudioFormat audioFormat = AF_8bit_Mono;
    protected SN76489 psg;

    public static BlipSN76489Psg createInstance(RegionDetector.Region region, int outputSampleRate) {
        BlipSN76489Psg s = new BlipSN76489Psg();
        s.psg = new SN76489();
        s.psg.init((int) getPsgSoundClock(region), outputSampleRate);
        return s;
    }

    private BlipSN76489Psg() {
        super("psg-sn", audioFormat);
    }

    @Override
    public void write(int data) {
        psg.write(data);
    }

    @Override
    public void updateMono8(byte[] out, int offset, int num) {
        psg.update(out, offset, num);
    }
}
