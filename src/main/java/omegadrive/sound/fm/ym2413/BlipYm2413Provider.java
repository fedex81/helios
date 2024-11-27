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


import omegadrive.sound.BlipBaseSound;
import omegadrive.sound.fm.FmProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

public class BlipYm2413Provider extends BlipBaseSound.BlipBaseSoundImpl implements FmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipYm2413Provider.class.getSimpleName());

    public static final double FM_RATE = 49740.0;
    // Input clock
    private static final int CLOCK_HZ = 3579545;
    final double ratio;
    double rateAccum;
    private OPLL opll;
    private int sample;

    protected BlipYm2413Provider(AudioFormat audioFormat) {
        super("fm2413", RegionDetector.Region.JAPAN, (int) FM_RATE, Channel.MONO, audioFormat);
        ratio = FM_RATE / audioFormat.getSampleRate();
    }

    public static FmProvider createInstance(AudioFormat audioFormat) {
        BlipYm2413Provider p = new BlipYm2413Provider(audioFormat);
        p.init();
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
    public void tick() {
        rateAccum += ratio;
        spinOnce();
        if (rateAccum > 1) {
            blipProvider.playSample(sample << 4, sample << 4);
            tickCnt++;
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
    public void init() {
        Emu2413.OPLL_init();
        opll = Emu2413.OPLL_new();
    }

    private void spinOnce() {
        sample = Emu2413.OPLL_calc(opll);
    }

    @Override
    public int getSample16bit(boolean left) {
        throw new IllegalArgumentException("not supported");
    }

    public enum FmReg {ADDR_LATCH_REG, DATA_REG}
}