/*
 * MdFmProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 04/10/19 11:10
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

package omegadrive.sound.fm;

import omegadrive.sound.fm.ym2612.nukeykt.BlipYm2612Nuke;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.util.RegionDetector;

import javax.sound.sampled.AudioFormat;

import static omegadrive.sound.SoundProvider.LOG;
import static omegadrive.sound.SoundProvider.getFmSoundClock;

public interface MdFmProvider extends FmProvider {

    int FM_ADDRESS_PORT0 = 0;
    int FM_ADDRESS_PORT1 = 2;
    int FM_DATA_PORT0 = 1;
    int FM_DATA_PORT1 = 3;

    static MdFmProvider createInstance(RegionDetector.Region region, AudioFormat audioFormat) {
        double clock = getFmSoundClock(region);
        MdFmProvider fmProvider = new BlipYm2612Nuke(AbstractSoundManager.audioFormat, region, clock);
        LOG.info("FM instance, clock: {}, sampleRate: {}", clock, audioFormat.getSampleRate());
        return fmProvider;
    }
}
