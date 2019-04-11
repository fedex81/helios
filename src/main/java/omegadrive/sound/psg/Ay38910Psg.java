/*
 * Ay38910Psg
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:06
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

package omegadrive.sound.psg;


import omegadrive.sound.psg.white2.SN76489;

public class Ay38910Psg implements PsgProvider {

    private Ay38910 psg;

    public static Ay38910Psg createInstance(int sampleRate) {
        Ay38910Psg s = new Ay38910Psg();
        s.psg = new Ay38910(sampleRate);
        s.reset();
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
    public void output(byte[] output) {
        output(output, 0, output.length);
    }

    @Override
    public void output(byte[] output, int offset, int end) {
        for (int i = offset; i < end; i++) {
            output[i] = (byte) psg.getSoundSigned();
        }
    }

    @Override
    public void reset() {
        psg.reset();
    }
}
