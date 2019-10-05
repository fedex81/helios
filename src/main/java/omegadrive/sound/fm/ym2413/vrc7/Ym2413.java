/*
 * Ym2413
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 05/10/19 14:22
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

package omegadrive.sound.fm.ym2413.vrc7;

import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;

/**
 *
 */
public class Ym2413 extends VRC7SoundChip implements FmProvider {

    static final double FM_CLOCKS_PER_MICROS = 1.789d; //FM_HZ / 1_000_000d;

    public final int[] instZero = {0x49, 0x4c, 0x4c, 0x12, 0x00, 0x00, 0x00, 0x00};

    public final int[][] ym2413_instruments = { //instrument parameters
            instZero, //modifiable user tone register is instrument 0
            {0x61, 0x61, 0x1e, 0x17, 0xf0, 0x78, 0x00, 0x17},  //1
            {0x13, 0x41, 0x1e, 0x0d, 0xd7, 0xf7, 0x13, 0x13},  //2
            {0x13, 0x01, 0x99, 0x04, 0xf2, 0xf4, 0x11, 0x23},  //3
            {0x21, 0x61, 0x1b, 0x07, 0xaf, 0x64, 0x40, 0x27},  //4
            {0x22, 0x21, 0x1e, 0x06, 0xf0, 0x75, 0x08, 0x18},  //5
            {0x31, 0x22, 0x16, 0x05, 0x90, 0x71, 0x00, 0x13},  //6
            {0x21, 0x61, 0x1d, 0x07, 0x82, 0x80, 0x10, 0x17},  //7
            {0x23, 0x21, 0x2d, 0x16, 0xc0, 0x70, 0x07, 0x07},  //8
            {0x61, 0x61, 0x1b, 0x06, 0x64, 0x65, 0x10, 0x17},  //9
            {0x61, 0x61, 0x0c, 0x18, 0x85, 0xf0, 0x70, 0x07},  //A
            {0x23, 0x01, 0x07, 0x11, 0xf0, 0xa4, 0x00, 0x22},  //B
            {0x97, 0xc1, 0x24, 0x07, 0xff, 0xf8, 0x22, 0x12},  //C
            {0x61, 0x10, 0x0c, 0x05, 0xf2, 0xf4, 0x40, 0x44},  //D
            {0x01, 0x01, 0x55, 0x03, 0xf3, 0x92, 0xf3, 0xf3},  //E
            {0x61, 0x41, 0x89, 0x03, 0xf1, 0xf4, 0xf0, 0x13},  //F
    };
    double countOut = 0;
    volatile int sample = 0;
    double microsPerOutputSample = 1_000_000d / SoundProvider.SAMPLE_RATE_HZ;
    private int dckiller = -6392; //removes icky power on thump

    public Ym2413() {
        super();
        initInstruments();
    }

    private void initInstruments() {
        System.arraycopy(instZero, 0, usertone, 0, instZero.length);
        for (int i = 1; i < instdata.length; i++) {
            System.arraycopy(instdata[i], 0, ym2413_instruments[i], 0, ym2413_instruments[i].length);
        }
    }

    @Override
    public int read() {
        return getval();
    }

    @Override
    public void init(int clock, int rate) {

    }

    @Override
    public void reset() {

    }

    @Override
    public int readRegister(int type, int regNumber) {
        return 0;
    }

    @Override
    public int update(int[] buf_lr, int offset, int count) {
        offset *= 2;
        count = count * 2 + offset;

        int sampleL = sample;
        while (offset < count) {
            buf_lr[offset] = sampleL;
            buf_lr[offset + 1] = sampleL;
            offset += 2;
        }
        return count;
    }

    private int highpass_filter(int sample) {
        //for killing the dc in the signal
        sample -= dckiller;
        dckiller += sample >> 8;//the actual high pass part
        dckiller += (sample > 0 ? 1 : -1);//guarantees the signal decays to exactly zero
        return sample;
    }

    private int lowpass_filter(int sample) {
        return lpaccum += 0.5 * (sample - lpaccum); //y = y + a * (x - y)
    }

    @Override
    public void tick(double microsPerTick) {
        clock(1);
        countOut += microsPerTick;
        if (countOut > microsPerOutputSample) {
            sample = lowpass_filter(highpass_filter(getval()));
            countOut -= microsPerOutputSample;
//            Ym2413Provider.fmUpdates++;
        }
    }
}
