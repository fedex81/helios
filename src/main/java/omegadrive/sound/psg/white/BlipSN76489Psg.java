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


import omegadrive.sound.BlipSoundProvider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import static omegadrive.sound.SoundProvider.getPsgSoundClock;

public class BlipSN76489Psg implements PsgProvider {

    private static int DEFAULT_CLOCK_RATE = AbstractSoundManager.SAMPLE_RATE_HZ;
    private BlipSoundProvider blipProvider;
    protected SN76489 psg;

    public static BlipSN76489Psg createInstance(RegionDetector.Region region, int outputSampleRate) {
        BlipSN76489Psg s = new BlipSN76489Psg();
        s.psg = new SN76489();
        s.psg.init((int) getPsgSoundClock(region), outputSampleRate);
        s.blipProvider = new BlipSoundProvider("psg_sn", RegionDetector.Region.USA, AbstractSoundManager.audioFormat,
                DEFAULT_CLOCK_RATE);
        LOG.info("PSG instance, region: {}, clockHz: {}, sampleRate: {}", region, (int) getPsgSoundClock(region), outputSampleRate);
        return s;
    }

    @Override
    public void write(int data) {
        psg.write(data);
    }

    @Override
    public void updateRate(RegionDetector.Region region, int clockRate) {
        blipProvider.updateRegion(region, clockRate);
    }

    @Override
    public void updateMono8(byte[] output, int offset, int end) {
        LogHelper.logWarnOnceForce(LOG, "Invalid method call: updateMono8");
    }

    long tickCnt = 0;
    final byte[] output = new byte[1];

    private final static Logger LOG = LogHelper.getLogger(BlipSN76489Psg.class.getSimpleName());

    @Override
    public void tick() {
        tickCnt++;
        psg.update(output, 0, 1);
        blipProvider.playSample(output[0] << 8, output[0] << 8);
    }

    @Override
    public SampleBufferContext getFrameData() {
        onNewFrame();
        return blipProvider.getDataBuffer();
    }

    @Override
    public void onNewFrame() {
        if (tickCnt > 0) { //TODO fix
            blipProvider.newFrame();
//            System.out.println(tickCnt);
            tickCnt = 0;
        } else {
            LogHelper.logWarnOnceForce(LOG, "newFrame called with tickCnt: {}", tickCnt);
        }
    }

    @Override
    public void reset() {
        psg.reset();
    }
}
