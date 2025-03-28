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

import omegadrive.sound.fm.ym2612.YM2612;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke;
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

    // Note Maxim doc on YM2612 is wrong: overflowB is bit 1 and overflowA is bit 0
//    Status
//    D7	D6	D5	D4	D3	D2	 D1	        D0
//    Busy		              Overflow B  Overflow A
    int FM_STATUS_TIMER_A_BIT_MASK = 0x1;
    int FM_STATUS_TIMER_B_BIT_MASK = 0x2;
    int FM_STATUS_BUSY_BIT_MASK = 0x80;

    // 27H
// D7	D6	  D5	  D4	        D3	      D2	      D1	D0
//Ch3 mode	Reset B	Reset A	  Enable B	Enable A	Load B	Load A
    int FM_MODE_LOAD_A_MASK = 0x1;
    int FM_MODE_LOAD_B_MASK = 0x2;
    int FM_MODE_ENABLE_A_MASK = 0x4;
    int FM_MODE_ENABLE_B_MASK = 0x8;
    int FM_MODE_RESET_A_MASK = 0x10;
    int FM_MODE_RESET_B_MASK = 0x20;

    static MdFmProvider createFastInstance(RegionDetector.Region region, AudioFormat audioFormat) {
        LOG.warn("YM2612, creating fast instance, less accurate");
        return createInstance(region, audioFormat, true);
    }

    static MdFmProvider createInstance(RegionDetector.Region region, AudioFormat audioFormat) {
        return createInstance(region, audioFormat, false);
    }

    private static MdFmProvider createInstance(RegionDetector.Region region, AudioFormat audioFormat, boolean fast) {
        double clock = getFmSoundClock(region);
        MdFmProvider fmProvider = fast ? new YM2612((int) clock, (int) audioFormat.getSampleRate()) :
                new Ym2612Nuke(AbstractSoundManager.audioFormat, clock);
        LOG.info("FM instance {}, clock: {}, sampleRate: {}", fmProvider.getClass().getSimpleName(), clock, audioFormat.getSampleRate());
        return fmProvider;
    }
}
