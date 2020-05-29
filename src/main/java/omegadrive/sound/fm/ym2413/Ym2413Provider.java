/*
 * Ym2413Provider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 27/10/19 13:13
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


import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.VariableSampleRateSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;

public class Ym2413Provider extends VariableSampleRateSource {

    private static final Logger LOG = LogManager.getLogger(Ym2413Provider.class.getSimpleName());

    public static final double FM_RATE = 49716.0;
    // Input clock
    private static final int CLOCK_HZ = 3579545;
    double ratio, rateAccum, adjustedRatio;

    private OPLL opll;
    private int sample;

    protected Ym2413Provider(AudioFormat audioFormat) {
        super(FM_RATE, audioFormat, "fmDsa");
        ratio = microsPerOutputSample / microsPerInputSample;
    }

    public static FmProvider createInstance(AudioFormat audioFormat) {
        Ym2413Provider p = new Ym2413Provider(audioFormat);
        p.init(CLOCK_HZ, (int) audioFormat.getSampleRate());
        return p;
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

    //this should be called 49716 times per second
    @Override
    public void tick(double microsPerTick) {
        rateAccum += adjustedRatio;
        spinOnce();
        if (rateAccum > 1) {
            addSample(sample);
            rateAccum -= 1;
        }
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

    @Override
    public void init(int clock, int rate) {
        Emu2413.OPLL_init();
        opll = Emu2413.OPLL_new();
    }

    @Override
    protected void spinOnce() {
        sample = Emu2413.OPLL_calc(opll);
    }

    @Override
    public void onNewFrame() {
        super.onNewFrame();
        adjustedRatio = microsPerInputSample / fmCalcsPerMicros;
    }

    public enum FmReg {ADDR_LATCH_REG, DATA_REG}
}