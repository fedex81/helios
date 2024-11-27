/*
 * SN76489Psg
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

package omegadrive.sound.psg.white;


import omegadrive.sound.BlipBaseSound;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

import static omegadrive.sound.SoundProvider.getPsgSoundClock;

public class BlipSN76489Psg extends BlipBaseSound.BlipBaseSoundImpl implements PsgProvider {

    private final static Logger LOG = LogHelper.getLogger(BlipSN76489Psg.class.getSimpleName());

    private static int DEFAULT_CLOCK_RATE = AbstractSoundManager.SAMPLE_RATE_HZ;

    protected SN76489 psg;
    final byte[] output = new byte[1];

    protected BlipSN76489Psg(String name, RegionDetector.Region region, int blipClockRate, AudioFormat audioFormat) {
        super(name, region, blipClockRate, Channel.MONO, audioFormat);
    }

    public static BlipSN76489Psg createInstance(RegionDetector.Region region, AudioFormat audioFormat) {
        BlipSN76489Psg s = new BlipSN76489Psg("psg_sn", region, DEFAULT_CLOCK_RATE, audioFormat);
        s.psg = new SN76489();
        s.psg.init((int) getPsgSoundClock(region), (int) audioFormat.getSampleRate());
        LOG.info("PSG instance, region: {}, clockHz: {}, sampleRate: {}", region, (int) getPsgSoundClock(region),
                audioFormat.getSampleRate());
        return s;
    }

    @Override
    public void write(int data) {
        psg.write(data);
    }
    @Override
    public int getSample16bit(boolean left) {
        psg.update(output, 0, 1);
        return output[0] << 6;
    }

    @Override
    public void reset() {
        psg.reset();
    }
}
