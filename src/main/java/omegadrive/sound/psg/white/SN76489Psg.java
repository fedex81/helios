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


import omegadrive.sound.psg.PsgProvider;

public class SN76489Psg implements PsgProvider {

    protected SN76489 psg;

    public static SN76489Psg createInstance(int clockSpeed, int sampleRate) {
        SN76489Psg s = new BlipSN76489Psg();
        s.psg = new SN76489();
        s.psg.init(clockSpeed, sampleRate);
        return s;
    }

    @Override
    public void write(int data) {
        psg.write(data);
    }

    @Override
    public void updateMono8(byte[] output, int offset, int end) {
        psg.update(output, offset, end - offset);
    }

    @Override
    public void reset() {
        psg.reset();
    }
}
