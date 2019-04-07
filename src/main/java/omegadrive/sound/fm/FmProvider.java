/*
 * FmProvider
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

package omegadrive.sound.fm;

import omegadrive.Device;
import omegadrive.util.RegionDetector;

import static omegadrive.sound.SoundProvider.LOG;
import static omegadrive.sound.SoundProvider.getFmSoundClock;

public interface FmProvider extends Device {

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

    static FmProvider createInstance(RegionDetector.Region region, int sampleRate) {
        double clock = getFmSoundClock(region);
        FmProvider fmProvider = new YM2612();
        fmProvider.init((int) clock, sampleRate);
//        FmProvider fmProvider = new Ym2612Nuke();
        LOG.info("FM instance, clock: " + clock + ", sampleRate: " + sampleRate);
        return fmProvider;
    }

    int read();

    int init(int clock, int rate);

    void write(int addr, int data);

    int readRegister(int type, int regNumber);

    void update(int[] buf_lr, int offset, int count);

    default void output(int[] buf_lr) {
        update(buf_lr, 0, buf_lr.length / 2);
    }

    void tick(double microsPerTick);

    FmProvider NO_SOUND = new FmProvider() {

        @Override
        public int read() {
            return 0;
        }

        @Override
        public int init(int clock, int rate) {
            return 0;
        }

        @Override
        public void write(int addr, int data) {

        }

        @Override
        public int readRegister(int type, int regNumber) {
            return 0;
        }

        @Override
        public void update(int[] buf_lr, int offset, int end) {

        }

        @Override
        public void tick(double microsPerTick) {

        }

        @Override
        public void output(int[] buf_lr) {

        }
    };


}
