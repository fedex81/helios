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

package omegadrive.sound.fm.ym2612.nukeykt;

import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.MdFmProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NTSC_MCLOCK_MHZ = 53693175;
 * PAL_MCLOCK_MHZ = 53203424;
 * <p>
 * NTSC:
 * FM CLOCK = MCLOCK/7 = 7670453
 * NUKE_CLOCK = FM_CLOCK/6 = 1278409
 * CHIP_OUTPUT_RATE = NUKE_CLOCK/24 = 53267
 * <p>
 * TODO check with no-audio, the buffer keeps growing
 * TODO check 50hz, buffer keeps growing
 */
public class Ym2612Nuke3 implements MdFmProvider {
    public static final double FM_CALCS_PER_MICROS = (1_000_000.0 / SoundProvider.SAMPLE_RATE_HZ);
    static final int VOLUME_SHIFT = 5;
    private static final Logger LOG = LogManager.getLogger(Ym2612Nuke3.class.getSimpleName());
    public static int sampleRate = 0;
    public static int chipRate = 0;
    private final int[][] ym3438_accm = new int[24][2];
    private static final boolean DEBUG = false;
    volatile double fmCalcsPerMicros = FM_CALCS_PER_MICROS;
    int ym3438_cycles = 0;
    double cycleAccum = 0;
    int sample = 0;
    int lastSample = 0;
    long maxQueueLen = 0;
    private IYm3438 ym3438;
    private IYm3438.IYm3438_Type chip;
    private AtomicLong queueLen = new AtomicLong();
    private Queue<Integer> sampleQueue = new ConcurrentLinkedQueue<>();
    int bufferSize;
    double HALF_LIMIT = 0.025;

    public Ym2612Nuke3(IYm3438.IYm3438_Type chip) {
        this.ym3438 = new Ym3438();
        this.chip = chip;
        this.ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
    }

    public Ym2612Nuke3(IYm3438 impl) {
        this.ym3438 = impl;
        this.ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
    }

    public IYm3438.IYm3438_Type getChip() {
        return chip;
    }

    @Override
    public void write(int addr, int data) {
        ym3438.OPN2_Write(chip, addr, data);
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return 0;
    }

    @Override
    public int read() {
        return ym3438.OPN2_Read(chip, 0x4000);
    }

    @Override
    public void init(int clock, int rate) {
    }

    @Override
    public void reset() {
        ym3438.OPN2_Reset(chip);
        sampleQueue.clear();
        queueLen.set(0);
        ym3438_cycles = 0;
        sample = 0;
        Arrays.stream(ym3438_accm).forEach(row -> Arrays.fill(row, 0));
    }

    double upperLimit = FM_CALCS_PER_MICROS * (1 + HALF_LIMIT);

    //Output frequency: 53.267 kHz (NTSC), 52.781 kHz (PAL)
    @Override
    public void tick(double microsPerTick) {
        cycleAccum += microsPerTick;
        spinOnce();
        addSample();
    }

    double lowerLimit = FM_CALCS_PER_MICROS * (1 - HALF_LIMIT);
    double fastPace = 0.001;
    double slowPace = fastPace / 2;

    public Ym2612Nuke3(int bufferSize) {
        this(new IYm3438.IYm3438_Type());
        this.bufferSize = bufferSize;
//        this(new Ym3438Jna());
    }

    @Override
    public int update(int[] buf_lr, int offset, int count) {
        offset <<= 1;
        int end = (count << 1) + offset;
        int sampleNumMono;
        final long initialQueueSize = queueLen.get();
        long queueIndicativeLen = initialQueueSize;
        if (initialQueueSize < 50) {
            return 0;
        }
        Integer sample;
        int i = offset;
        for (; i < end && queueIndicativeLen > 0; i += 2) {
            sample = sampleQueue.poll();
            if (sample == null) {
                LOG.warn("Null sample QL{} P{}", queueIndicativeLen, i);
                break;
            }
            sample <<= VOLUME_SHIFT;
            queueIndicativeLen = queueLen.decrementAndGet();
            buf_lr[i] = sample;
            buf_lr[i + 1] = sample;
            lastSample = sample;
        }
        sampleNumMono = (int) (initialQueueSize - queueIndicativeLen);
        if (DEBUG && queueIndicativeLen > maxQueueLen) {
            maxQueueLen = queueIndicativeLen;
            LOG.info("Len {}-{}, Prod {}, Req {}", initialQueueSize, maxQueueLen, sampleNumMono, count);
        }
        return sampleNumMono;
    }

    private void addSample() {
        if (cycleAccum > fmCalcsPerMicros) {
            sampleRate++;
            sampleQueue.offer(sample);
            queueLen.addAndGet(1);
            cycleAccum -= fmCalcsPerMicros;
        }
    }

    private void spinOnce() {
        ym3438.OPN2_Clock(chip, ym3438_accm[ym3438_cycles]);
        ym3438_cycles = (ym3438_cycles + 1) % 24;
        if (ym3438_cycles == 0) {
            chipRate++;
            int sampleL = 0;
            int sampleR = 0;
            for (int j = 0; j < 24; j++) {
                sampleL += ym3438_accm[j][0];
                sampleR += ym3438_accm[j][1];
            }
            //mono
            sample = (sampleL + sampleR) >> 1;
        }
    }

    @Override
    public void newFrame() {
        adaptiveRateControl();
    }

    private void adaptiveRateControl() {
        boolean starving = queueLen.get() < (bufferSize * 2);
        boolean badStarving = queueLen.get() < (bufferSize) && fmCalcsPerMicros < FM_CALCS_PER_MICROS;
        boolean growsTooFast = queueLen.get() > (bufferSize * 3) && fmCalcsPerMicros > FM_CALCS_PER_MICROS;
        boolean steadyState = !starving && !growsTooFast;
        double fm = fmCalcsPerMicros;
        if (steadyState) {
            fm += fmCalcsPerMicros > FM_CALCS_PER_MICROS ? -slowPace : slowPace;
        } else {
            fm += starving ? -fastPace : fastPace;
            fm += badStarving ? -fastPace : 0;
            fm += growsTooFast ? fastPace : 0;
        }
        //limit
        fm = fm > upperLimit ? upperLimit : (fm < lowerLimit ? lowerLimit : fm);
        fmCalcsPerMicros = fm;
        if (DEBUG) {
            LOG.info("{}hz, q_av {}, b_size {}, steady {}", sampleRate, queueLen.get(), bufferSize, steadyState);
        }
        sampleRate = 0;
    }
}
