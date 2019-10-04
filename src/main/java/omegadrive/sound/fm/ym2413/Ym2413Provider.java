/*
 * Ym2413Provider
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

package omegadrive.sound.fm.ym2413;


import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * // Input clock
 * private static final int clk = 3579545 hz
 * // Sampling rate
 * public static final int rate = 49716 hz
 */
public class Ym2413Provider implements FmProvider {

    public static final short[] ym2413_inst = {
            /* MULT  MULT modTL DcDmFb AR/DR AR/DR SL/RR SL/RR */
            /*   0     1     2     3     4     5     6    7    */
            /* These YM2413(OPLL) patch dumps are done via audio analysis (and a/b testing?) from Jarek and are known to be inaccurate */
            0x49, 0x4c, 0x4c, 0x12, 0x00, 0x00, 0x00, 0x00,  //0
            0x61, 0x61, 0x1e, 0x17, 0xf0, 0x78, 0x00, 0x17,  //1
            0x13, 0x41, 0x1e, 0x0d, 0xd7, 0xf7, 0x13, 0x13,  //2
            0x13, 0x01, 0x99, 0x04, 0xf2, 0xf4, 0x11, 0x23,  //3
            0x21, 0x61, 0x1b, 0x07, 0xaf, 0x64, 0x40, 0x27,  //4
            0x22, 0x21, 0x1e, 0x06, 0xf0, 0x75, 0x08, 0x18,  //5
            0x31, 0x22, 0x16, 0x05, 0x90, 0x71, 0x00, 0x13,  //6
            0x21, 0x61, 0x1d, 0x07, 0x82, 0x80, 0x10, 0x17,  //7
            0x23, 0x21, 0x2d, 0x16, 0xc0, 0x70, 0x07, 0x07,  //8
            0x61, 0x61, 0x1b, 0x06, 0x64, 0x65, 0x10, 0x17,  //9
            0x61, 0x61, 0x0c, 0x18, 0x85, 0xf0, 0x70, 0x07,  //A
            0x23, 0x01, 0x07, 0x11, 0xf0, 0xa4, 0x00, 0x22,  //B
            0x97, 0xc1, 0x24, 0x07, 0xff, 0xf8, 0x22, 0x12,  //C
            0x61, 0x10, 0x0c, 0x05, 0xf2, 0xf4, 0x40, 0x44,  //D
            0x01, 0x01, 0x55, 0x03, 0xf3, 0x92, 0xf3, 0xf3,  //E
            0x61, 0x41, 0x89, 0x03, 0xf1, 0xf4, 0xf0, 0x13,  //F

            /* drum instruments definitions */
            /* MULTI MULTI modTL  xxx  AR/DR AR/DR SL/RR SL/RR */
            /*   0     1     2     3     4     5     6    7    */
            /* Drums dumped from the VRC7 using debug mode, these are likely also correct for ym2413(OPLL) but need verification */
            0x01, 0x01, 0x18, 0x0f, 0xdf, 0xf8, 0x6a, 0x6d,/* BD */
            0x01, 0x01, 0x00, 0x00, 0xc8, 0xd8, 0xa7, 0x68,/* HH, SD */
            0x05, 0x01, 0x00, 0x00, 0xf8, 0xaa, 0x59, 0x55  /* TOM, TOP CYM */
    };
    private static final int MIX_RANGE = 0x7FFF;
    private static final int AUDIO_SCALE = MIX_RANGE >> 9;
    // Input clock
    private static final int CLOCK_HZ = 3579545;
    public static int fmUpdates = 0;
    double countFm = 0, countOut = 0;
    double microsPerFmSample = 1_000_000d / Emu2413.rate; //20.1 micros
    double microsPerOutputSample = 1_000_000d / SoundProvider.SAMPLE_RATE_HZ;
    private OPLL opll;
    private volatile int soundSample;
    private AtomicInteger lastSample = new AtomicInteger();

    public Ym2413Provider() {
        init(0, 0);
    }

    @Override
    public void reset() {
        for (int i = 0x10; i < 0x40; i++) {
            Emu2413.OPLL_writeIO(opll, 0, i);
            Emu2413.OPLL_writeIO(opll, 1, 0);
        }
        Emu2413.OPLL_reset_patch(opll);
        Emu2413.OPLL_reset(opll);
    }

    @Override
    public int read() {
        return 0;
    }

    @Override
    public void init(int clock, int rate) {
        Emu2413.default_inst = ym2413_inst;
        Emu2413.OPLL_init();
        opll = Emu2413.OPLL_new();
        reset();
    }

    @Override
    public void write(int addr, int data) {
        Emu2413.OPLL_writeIO(opll, 0, addr);
        Emu2413.OPLL_writeIO(opll, 1, data);
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return 0;
    }

    @Override
    public void update(int[] buf_lr, int offset, int end) {

        offset *= 2;
        end = end * 2 + offset;

        while (offset < end) {
            int val = lastSample.get();
            buf_lr[offset] = val;
            buf_lr[offset + 1] = val;
            offset += 2;
        }
    }

    private int getSample() {
        return soundSample;
    }

    private int calcSample() {
        int val = 0;
        for (int j = 0; j < 9; j++) {
            val += opll.slot[(j << 1) | 1].output[1];
        }
        val = (val * 16); //signed output
        soundSample = val;
        lastSample.set(val);
        return val;
    }

    @Override
    public void tick(double microsPerTick) {
        int res = Emu2413.OPLL_calc(opll);
        lastSample.set(res);
        System.out.println(res);
    }

//    @Override
//    public void tick(double microsPerTick) {
//        countFm += microsPerTick;
//        countOut += microsPerTick;
//        if(countFm > microsPerFmSample) {
//            lastSample.set(Emu2413.OPLL_calc(opll)*16);
//            countFm -= microsPerFmSample;
//            fmUpdates++;
//        }
//        if(countOut > microsPerOutputSample) {
//            countOut -= microsPerOutputSample;
//            soundSample = lastSample.get();
//        }
//    }

    public enum FmReg {ADDR_LATCH_REG, DATA_REG}
}
