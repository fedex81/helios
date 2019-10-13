/*
 * Ym2413Provider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/10/19 17:32
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
import omegadrive.util.RegionDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Queue;

public class Ym2413Provider implements FmProvider {

    private static Logger LOG = LogManager.getLogger(Ym2413Provider.class.getSimpleName());

    public static final double FM_RATE = 49716.0;

    // Input clock
    private static final int CLOCK_HZ = 3579545;
    static double FM_CALCS_PER_MS = SoundProvider.SAMPLE_RATE_HZ / 1000.0;
    static int AUDIO_SCALE_BITS = 4;

    double rateAccum = 0;
    double ratio = SoundProvider.SAMPLE_RATE_HZ / FM_RATE;

    //TODO review perf of locking
    private Queue<Integer> sampleQueue = new ArrayDeque<>();
    private volatile long queueLen = 0;
    private Object lock = new Object();

    private OPLL opll;


    @Override
    public void reset() {
        for (int i = 0x10; i < 0x40; i++) {
            Emu2413.OPLL_writeIO(opll, 0, i);
            Emu2413.OPLL_writeIO(opll, 1, 0);
        }
        Emu2413.OPLL_reset_patch(opll);
        Emu2413.OPLL_reset(opll);
    }

    public static FmProvider createInstance(RegionDetector.Region region, int sampleRate) {
        Ym2413Provider p = new Ym2413Provider();
        p.init(CLOCK_HZ, sampleRate);
        return p;
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

    private int lastSample = 0;

    //this should be called 49716 times per second
    @Override
    public int update(int[] buf_lr, int offset, int count) {
        offset <<= 1;
        int end = (count << 1) + offset;
        int sampleNum;
        int sample = 0;
        synchronized (lock) {
            long initialQueueSize = queueLen;
            for (int i = offset; i < end && queueLen > 0; i += 2) {
                sample = sampleQueue.poll();
                queueLen--;
                buf_lr[i] = sample;
                buf_lr[i + 1] = sample;
            }
            sampleNum = (int) (initialQueueSize - queueLen);
        }
        if (sampleNum < count) {
            sample = lastSample;
//            LOG.info("Count {}, num {}, lastSample {}", count, sampleNum, sample);
            for (int i = offset + (sampleNum << 1); i < end; i += 2) {
                buf_lr[i] = sample;
                buf_lr[i + 1] = sample;
            }
        }
        return sampleNum;
    }

    @Override
    public void init(int clock, int rate) {
        FM_CALCS_PER_MS = rate / 1000.0;
        Emu2413.OPLL_init();
        opll = Emu2413.OPLL_new();
    }

    @Override
    public void tick(double microsPerTick) {
        rateAccum += ratio;
        int res = Emu2413.OPLL_calc(opll);
        if (rateAccum > 1) {
            synchronized (lock) {
                lastSample = res << AUDIO_SCALE_BITS;
                sampleQueue.offer(lastSample);
                queueLen++;
            }
            rateAccum -= 1;
        }
    }

    public enum FmReg {ADDR_LATCH_REG, DATA_REG}
}