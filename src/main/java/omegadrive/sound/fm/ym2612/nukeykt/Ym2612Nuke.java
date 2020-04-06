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
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.atomic.SpscAtomicArrayQueue;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NTSC_MCLOCK_MHZ = 53693175;
 * PAL_MCLOCK_MHZ = 53203424;
 * <p>
 * NTSC:
 * FM CLOCK = MCLOCK/7 = 7670453
 * NUKE_CLOCK = FM_CLOCK/6 = 1278409
 * CHIP_OUTPUT_RATE = NUKE_CLOCK/24 = 53267
 * <p>
 */
public class Ym2612Nuke implements MdFmProvider {

    private static final boolean DEBUG = false;

    public static final double FM_CALCS_PER_MICROS = (1_000_000.0 / SoundProvider.SAMPLE_RATE_HZ);
    private static final int VOLUME_SHIFT = 5;
    private static int MIN_AUDIO_SAMPLES = 50; //~1ms
    private static final Logger LOG = LogManager.getLogger(Ym2612Nuke.class.getSimpleName());

    private IYm3438 ym3438;
    private IYm3438.IYm3438_Type chip;
    private final int[][] ym3438_accm = new int[24][2];
    int maxQueueLen = 0;
    private AtomicInteger queueLen = new AtomicInteger();
    volatile double fmCalcsPerMicros = FM_CALCS_PER_MICROS;
    int ym3438_cycles = 0;
    double cycleAccum = 0;
    int sample = 0;
    private Queue<Integer> sampleQueue =
            new SpscAtomicArrayQueue<>(SoundProvider.SAMPLE_RATE_HZ);
    private AudioRateControl audioRateControl;
    private int sampleRatePerFrame = 0;
    private long chipRate;

    public Ym2612Nuke(IYm3438.IYm3438_Type chip) {
        this.ym3438 = new Ym3438();
        this.chip = chip;
        this.ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
    }

    public Ym2612Nuke(IYm3438 impl) {
        this.ym3438 = impl;
        this.ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
    }

    public IYm3438.IYm3438_Type getChip() {
        return chip;
    }

    public void setChip(IYm3438.IYm3438_Type chip) {
        this.chip = chip;
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

    public Ym2612Nuke(int bufferSize) {
        this(new IYm3438.IYm3438_Type());
        this.audioRateControl = new AudioRateControl(bufferSize);
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

    //Output frequency: 53.267 kHz (NTSC), 52.781 kHz (PAL)
    @Override
    public void tick(double microsPerTick) {
        cycleAccum += microsPerTick;
        spinOnce();
        addSample();
    }

    @Override
    public void init(int clock, int rate) {
        LOG.info("Init with clock {}hz, sampleRate {}hz", clock, rate);
    }

    @Override
    public int update(int[] buf_lr, int offset, int count) {
        offset <<= 1;
        int end = (count << 1) + offset;
        int sampleNumMono;
        final int initialQueueSize = queueLen.get();
        int queueIndicativeLen = initialQueueSize;
        if (initialQueueSize < MIN_AUDIO_SAMPLES) {
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
            queueIndicativeLen = queueLen.decrementAndGet();
            sample <<= VOLUME_SHIFT;
            buf_lr[i] = sample;
            buf_lr[i + 1] = sample;
        }
        sampleNumMono = initialQueueSize - queueIndicativeLen;
        if (DEBUG && queueIndicativeLen > maxQueueLen) {
            maxQueueLen = queueIndicativeLen;
            LOG.info("Len {}-{}, Prod {}, Req {}", initialQueueSize, maxQueueLen, sampleNumMono, count);
        }
        return sampleNumMono;
    }

    private void addSample() {
        if (cycleAccum > fmCalcsPerMicros) {
            sampleRatePerFrame++;
            sampleQueue.offer(Util.getFromIntegerCache(sample));
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
    public void onNewFrame() {
        fmCalcsPerMicros = audioRateControl.adaptiveRateControl(queueLen.get(), fmCalcsPerMicros, sampleRatePerFrame);
        sampleRatePerFrame = 0;
    }
}
