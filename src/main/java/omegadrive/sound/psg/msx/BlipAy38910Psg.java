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

package omegadrive.sound.psg.msx;


import omegadrive.sound.BlipSoundProvider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

public class BlipAy38910Psg implements PsgProvider {

    private final static Logger LOG = LogHelper.getLogger(BlipAy38910Psg.class.getSimpleName());
    private static int DEFAULT_CLOCK_RATE = AbstractSoundManager.SAMPLE_RATE_HZ;
    private BlipSoundProvider blipProvider;
    protected Ay38910 psg;
    private long tickCnt = 0;

    public static BlipAy38910Psg createInstance(RegionDetector.Region region, int outputSampleRate) {
        BlipAy38910Psg s = new BlipAy38910Psg();
        s.psg = new Ay38910(outputSampleRate);
        s.blipProvider = new BlipSoundProvider("psg_ay", RegionDetector.Region.USA, AbstractSoundManager.audioFormat,
                DEFAULT_CLOCK_RATE);
        LOG.info("PSG instance, region: {}, sampleRate: {}", region, outputSampleRate);
        return s;
    }

    @Override
    public void write(int data) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(int register, int data) {
        psg.out(register, data);
    }

    @Override
    public int read(int register) {
        return psg.in(register);
    }

    @Override
    public void updateMono8(byte[] output, int offset, int end) {
//        for (int i = offset; i < end; i++) {
//            output[i] = (byte) psg.getSoundSigned();
//        }
        LogHelper.logWarnOnceForce(LOG, "Invalid method call: updateMono8");
    }


    @Override
    public void updateRate(RegionDetector.Region region, int clockRate) {
        blipProvider.updateRegion(region, clockRate);
    }

    @Override
    public void tick() {
        tickCnt++;
        byte val = (byte) psg.getSoundSigned();
        blipProvider.playSample(val << 8, val << 8);
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
