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


import omegadrive.sound.BlipBaseSound;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

public class BlipAy38910Psg extends BlipBaseSound.BlipBaseSoundImpl implements PsgProvider {

    private final static Logger LOG = LogHelper.getLogger(BlipAy38910Psg.class.getSimpleName());
    private static int DEFAULT_CLOCK_RATE = AbstractSoundManager.SAMPLE_RATE_HZ;
    protected Ay38910 psg;

    protected BlipAy38910Psg(String name, RegionDetector.Region region, int blipClockRate) {
        super(name, region, blipClockRate, Channel.MONO, AbstractSoundManager.audioFormat);
    }

    public static BlipAy38910Psg createInstance(RegionDetector.Region region, int outputSampleRate) {
        BlipAy38910Psg s = new BlipAy38910Psg("psg_ay", region, DEFAULT_CLOCK_RATE);
        s.psg = new Ay38910(outputSampleRate);
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

    public int getSample16bit(boolean left) {
        return (byte) psg.getSoundSigned() << 8;
    }

    @Override
    public SoundDeviceType getType() {
        return SoundDeviceType.PSG;
    }

    @Override
    public void reset() {
        psg.reset();
    }
}
