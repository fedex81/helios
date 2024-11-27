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

package omegadrive.sound;


import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector.Region;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

public interface BlipBaseSound extends SoundDevice {

    enum Channel {NONE, MONO, STEREO}

    void updateRate(Region region, int clockRate);

    void tick();

    void onNewFrame();

    int getSample16bit(boolean left);

    abstract class BlipBaseSoundImpl implements BlipBaseSound {
        private final static Logger LOG = LogHelper.getLogger(BlipBaseSound.class.getSimpleName());
        protected BlipSoundProvider blipProvider;
        protected Region region;
        protected String name;
        protected Channel channel;
        protected long tickCnt = 0;

        protected int prevL, prevR;

        protected BlipBaseSoundImpl(String name, Region region, int blipClockRate, Channel channel, AudioFormat audioFormat) {
            this.region = region;
            this.name = name;
            this.channel = channel;
            blipProvider = new BlipSoundProvider(name, region, audioFormat, blipClockRate);
        }

        @Override
        public void updateRate(Region region, int clockRate) {
            blipProvider.updateRegion(region, clockRate);
        }

        @Override
        public void tick() {
            tickCnt++;
            int left = getSample16bit(true);
            int right = left;
            if (channel == Channel.STEREO) {
                right = getSample16bit(false);
            }
            filterAndSet(left, right);
            blipProvider.playSample(prevL, prevR);
        }

        //1st order lpf: p[n]=αp[n−1]+(1−α)pi[n] with α = 0.5
        //The cutoff frequency fco = fs*(1−α)/2πα, where fs is your sampling frequency.
        //fco ~= 8.5 khz
        protected void filterAndSet(int sampleL, int sampleR) {
            sampleL = (sampleL + prevL) >> 1;
            sampleR = (sampleR + prevR) >> 1;
            prevL = sampleL;
            prevR = sampleR;
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
//                LOG.info("{} ticksPerFrame: {}", name, tickCnt);
                tickCnt = 0;
            } else {
                LogHelper.logWarnOnceForce(LOG, "newFrame called with tickCnt: {}", tickCnt);
            }
        }
    }
}
