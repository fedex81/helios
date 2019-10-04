/*
 * Ym2413Provider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 04/10/19 14:25
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

public class Ym2413Provider implements FmProvider {

    public static final double FM_RATE = 49716.0;

    // Input clock
    private static final int CLOCK_HZ = 3579545;
    static double FM_CALCS_PER_MS = SoundProvider.SAMPLE_RATE_HZ / 1000.0;
    static int AUDIO_SCALE_BITS = 2;
    static double ymRatePerMs = FM_RATE / 1000.0;
    final static double rateRatio = FM_CALCS_PER_MS / ymRatePerMs;
    double rateRatioAcc = 0;
    double sampleRateCalcAcc = 0;
    private OPLL opll;

    //TODO
    @Override
    public void update(int[] buf_lr, int offset, int samples441) {
        offset <<= 1; //stereo
        sampleRateCalcAcc += samples441 / rateRatio;
        int total = (int) sampleRateCalcAcc + 1; //needed to match the offsets
        for (int i = 0; i < total; i++) {
            int res = Emu2413.OPLL_calc(opll) << AUDIO_SCALE_BITS;
            rateRatioAcc += rateRatio;
            if (rateRatioAcc > 1) {
                buf_lr[offset++] = res;
                buf_lr[offset++] = res;
                rateRatioAcc--;
            }
        }
        sampleRateCalcAcc -= total;
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
    public void init(int clock, int rate) {
        Emu2413.OPLL_init();
        opll = Emu2413.OPLL_new();
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return 0;
    }

    @Override
    public void write(int addr, int data) {
        switch (FmReg.values()[addr]) {
            case ADDR_LATCH_REG:
                Emu2413.OPLL_writeIO(opll, 0, data);
                break;
            case DATA_REG:
                Emu2413.OPLL_writeIO(opll, 1, data);
                break;
        }
    }

    //TODO
    @Override
    public void tick(double microsPerTick) {
        int res = Emu2413.OPLL_calc(opll);
//        lastSample.set(res);
//        System.out.println(res);
    }

    public enum FmReg {ADDR_LATCH_REG, DATA_REG}
}