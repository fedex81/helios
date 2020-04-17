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
import omegadrive.sound.fm.AudioRateControl;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.ym2612.Ym2612RegSupport;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.atomic.SpscAtomicArrayQueue;

import java.io.Serializable;
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
    private Ym3438Context state;

    private AtomicInteger queueLen = new AtomicInteger();
    volatile double fmCalcsPerMicros = FM_CALCS_PER_MICROS;
    double cycleAccum = 0;
    private Queue<Integer> sampleQueue =
            new SpscAtomicArrayQueue<>(SoundProvider.SAMPLE_RATE_HZ);
    private AudioRateControl audioRateControl;
    private int sampleRatePerFrame = 0;
    private Ym2612RegSupport regSupport;

    private Ym2612Nuke(IYm3438.IYm3438_Type chip) {
        this.ym3438 = new Ym3438();
        this.chip = chip;
        this.ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
        this.state = new Ym3438Context();
        state.chip = chip;
        this.regSupport = new Ym2612RegSupport();
    }

    @Override
    public void init(int clock, int rate) {
        LOG.info("Init with clock {}hz, sampleRate {}hz", clock, rate);
    }

    public Ym3438Context getState() {
        return state;
    }

    @Override
    public void reset() {
        ym3438.OPN2_Reset(chip);
        sampleQueue.clear();
        queueLen.set(0);
        state.reset();
    }

    @Override
    public void write(int addr, int data) {
        ym3438.OPN2_Write(chip, addr, data);
        regSupport.write(addr, data);
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return regSupport.readRegister(type, regNumber);
    }

    @Override
    public int read() {
        return ym3438.OPN2_Read(chip, 0x4000);
    }

    public Ym2612Nuke(int bufferSize) {
        this(new IYm3438.IYm3438_Type());
        this.audioRateControl = new AudioRateControl(bufferSize);
    }

    private void addSample() {
        if (cycleAccum > fmCalcsPerMicros) {
            sampleRatePerFrame++;
            sampleQueue.offer(Util.getFromIntegerCache(state.ym3438_sample));
            queueLen.addAndGet(1);
            cycleAccum -= fmCalcsPerMicros;
        }
    }

    //Output frequency: 53.267 kHz (NTSC), 52.781 kHz (PAL)
    @Override
    public void tick(double microsPerTick) {
        cycleAccum += microsPerTick;
        spinOnce();
        addSample();
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
        Integer isample;
        int i = offset, sample;
        for (; i < end && queueIndicativeLen > 0; i += 2) {
            isample = sampleQueue.poll();
            if (isample == null) {
                LOG.warn("Null sample QL{} P{}", queueIndicativeLen, i);
                break;
            }
            sample = isample;
            queueIndicativeLen = queueLen.decrementAndGet();
            sample <<= VOLUME_SHIFT;
            buf_lr[i] = sample;
            buf_lr[i + 1] = sample;
        }
        sampleNumMono = initialQueueSize - queueIndicativeLen;
        return sampleNumMono;
    }

    private void spinOnce() {
        ym3438.OPN2_Clock(chip, state.ym3438_accm[state.ym3438_cycles]);
        state.ym3438_cycles = (state.ym3438_cycles + 1) % 24;
        if (state.ym3438_cycles == 0) {
//            chipRate++;
            int sampleL = 0;
            int sampleR = 0;
            for (int j = 0; j < 24; j++) {
                sampleL += state.ym3438_accm[j][0];
                sampleR += state.ym3438_accm[j][1];
            }
            //mono
            state.ym3438_sample = (sampleL + sampleR) >> 1;
        }
    }

    @Override
    public void onNewFrame() {
        fmCalcsPerMicros = audioRateControl.adaptiveRateControl(queueLen.get(), fmCalcsPerMicros, sampleRatePerFrame);
        sampleRatePerFrame = 0;
    }

    public void setState(Ym3438Context state) {
        if (state != null) {
            this.state = state;
            this.chip = state.chip;
        } else {
            LOG.warn("Unable to restore state, FM will not work");
            this.chip.reset();
            this.state.reset();
        }
    }

    public static class Ym3438Context implements Serializable {

        private static final long serialVersionUID = -2921159132727518547L;

        int ym3438_cycles = 0;
        int ym3438_sample = 0;
        int[][] ym3438_accm = new int[24][2];
        IYm3438.IYm3438_Type chip;

        public void reset() {
            ym3438_cycles = 0;
            ym3438_sample = 0;
            Arrays.stream(ym3438_accm).forEach(row -> Arrays.fill(row, 0));
        }
    }
}
