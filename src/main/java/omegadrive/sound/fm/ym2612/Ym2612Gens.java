/*
 * Ym2612Nuke
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 25/10/19 16:39
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

package omegadrive.sound.fm.ym2612;

import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.javasound.JavaSoundManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;

//TODO WIP
public class Ym2612Gens implements MdFmProvider {
    static final double FM_CALCS_PER_MICROS = (1_000_000.0 / SoundProvider.SAMPLE_RATE_HZ);// + 0.03;
    static final int VOLUME_SHIFT = 1;
    private static final Logger LOG = LogManager.getLogger(Ym2612Gens.class.getSimpleName());
    public static int sampleRate = 0;
    public volatile static int usedSampleRate = 0;
    public volatile static int soundThreadRate = 0;
    public static int chipRate = 0;
    double cycleAccum = 0;
    int[] buf = new int[2];
    double totalMicros = 0;
    private YM2612 ym2612;
    private Queue<Integer> sampleQueue = new ArrayDeque<>();
    private volatile long queueLen = 0;
    private Object lock = new Object();

    public Ym2612Gens() {
        ym2612 = new YM2612();
    }

    @Override
    public void reset() {
        ym2612.reset();
    }

    @Override
    public void write(int addr, int data) {
        ym2612.write(addr, data);
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return 0;
    }

    @Override
    public int read() {
        return ym2612.read();
    }

    @Override
    public void init(int clock, int rate) {
        ym2612.init(clock, rate);
    }

    @Override
    public int update(int[] buf_lr, int offset, int count) {
        offset <<= 1;
        int end = (count << 1) + offset;
        int sampleNum;
        synchronized (lock) {
            long initialQueueSize = queueLen;
            int sample = 0;
            for (int i = offset; i < end && queueLen > 0; i += 2) {
                sample = sampleQueue.poll() << VOLUME_SHIFT;
                queueLen--;
                buf_lr[i] = sample;
                buf_lr[i + 1] = sample;
            }
            sampleNum = (int) (initialQueueSize - queueLen);
            if (queueLen > 0) {
                LOG.info("Samples dropped: {}", queueLen);
                sampleQueue.clear();
                queueLen = 0;
            }
            usedSampleRate += sampleNum;
            soundThreadRate++;
        }
        return sampleNum;
    }

    //Output frequency: 53.267 kHz (NTSC), 52.781 kHz (PAL)
    @Override
    public void tick(double microsPerTick) {
        cycleAccum += microsPerTick;
        totalMicros += microsPerTick;
        spinOnce(microsPerTick);
        if (cycleAccum > FM_CALCS_PER_MICROS) {
            sampleRate++;
            buf[0] = buf[1] = 0;
            ym2612.update(buf, 0, 1);

            synchronized (lock) {
                sampleQueue.offer(buf[0] + buf[1] >> 1);
                queueLen++;
                printInfo();
            }
            cycleAccum -= FM_CALCS_PER_MICROS;
        }
    }

    private void printInfo() {
        if (totalMicros > 1_000_000) {
            LOG.info("1s {}, fmQueueLen {}, fmProd {}, fmCons {}, threadRate {}, soundSleepMs {}",
                    totalMicros, queueLen, sampleRate, usedSampleRate, soundThreadRate,
                    Duration.ofNanos(JavaSoundManager.sleepTotal).toMillis());
            totalMicros -= 1_000_000;
            sampleRate = 0;
            usedSampleRate = 0;
            soundThreadRate = 0;
            JavaSoundManager.sleepTotal = 0;
        }
    }

    private void spinOnce(double microsPerTick) {
        ym2612.tick(microsPerTick);
    }
}
