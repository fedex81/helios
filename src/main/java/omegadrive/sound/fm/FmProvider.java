/*
 * FmProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 20:41
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

public interface FmProvider extends Device {

    FmProvider NO_SOUND = new FmProvider() {

        @Override
        public int read() {
            return 0;
        }

        @Override
        public void init() {

        }

        @Override
        public void reset() {

        }

        @Override
        public void init(int clock, int rate) {
        }

        @Override
        public void write(int addr, int data) {

        }

        @Override
        public int readRegister(int type, int regNumber) {
            return 0;
        }

        @Override
        public int update(int[] buf_lr, int offset, int end) {
            return 0;
        }

        @Override
        public void tick(double microsPerTick) {

        }

        @Override
        public void output(int[] buf_lr) {

        }
    };

    void reset();

    int update(int[] buf_lr, int offset, int count);

    void init(int clock, int rate);

    int readRegister(int type, int regNumber);

    default void writeRegister(int type, int regNumber, int data) {

    }

    void tick(double microsPerTick);

    default void write(int addr, int data) {
        throw new RuntimeException("Invalid");
    }

    default int read() {
        throw new RuntimeException("Invalid");
    }

    default void output(int[] buf_lr) {
        update(buf_lr, 0, buf_lr.length / 2);
    }

    default void init() {
        throw new RuntimeException("Invalid");
    }


}
