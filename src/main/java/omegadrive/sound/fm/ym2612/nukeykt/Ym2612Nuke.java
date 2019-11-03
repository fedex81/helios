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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

/**
 *
 * TODO check this:
 * https://github.com/nukeykt/Nuked-OPN2/issues/4
 *
 * //NTSC_MCLOCK_MHZ = 53693175;
 * //    //PAL_MCLOCK_MHZ = 53203424;
 *
 * NTSC:
 * FM CLOCK = MCLOCK/7 = 7670453
 * NUKE_CLOCK = FM_CLOCK/6 = 1278409
 * CHIP_OUTPUT_RATE = NUKE_CLOCK/24 = 53267
 */
public class Ym2612Nuke implements MdFmProvider {
    static final double FM_CALCS_PER_MICROS = (1_000_000.0 / SoundProvider.SAMPLE_RATE_HZ);// + 0.03;

    private IYm3438 ym3438;
    private IYm3438.IYm3438_Type chip;
    private static final Logger LOG = LogManager.getLogger(Ym2612Nuke.class.getSimpleName());
    int ym3438_cycles = 0;
    double cycleAccum = 0;

    public static int sampleRate = 0;
    public static int chipRate = 0;
    static final int VOLUME_SHIFT = 3;
    private final int[][] ym3438_accm = new int[24][2];

    public Ym2612Nuke() {
        this(new IYm3438.IYm3438_Type());
//        this(new Ym3438Jna());
    }

    public Ym2612Nuke(IYm3438.IYm3438_Type chip) {
        this.ym3438 = new Ym3438();
        this.chip = chip;
        this.ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
    }

    public Ym2612Nuke(IYm3438 impl) {
        this.ym3438 = impl;
        this.ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
    }

    @Override
    public void reset() {
        synchronized (lock) {
            ym3438.OPN2_Reset(chip);
            sampleQueue.clear();
            queueLen = 0;
            ym3438_cycles = 0;
            Arrays.stream(ym3438_accm).forEach(row -> Arrays.fill(row, 0));
        }
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

    int sample = 0;
    private Queue<Integer> sampleQueue = new ArrayDeque<>();
    private volatile long queueLen = 0;
    private Object lock = new Object();

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
                LOG.info("Dropping {} samples", queueLen);
                sampleQueue.clear();
                queueLen = 0;
            }
        }
        return sampleNum;
    }

    //Output frequency: 53.267 kHz (NTSC), 52.781 kHz (PAL)
    @Override
    public void tick(double microsPerTick) {
        cycleAccum += microsPerTick;
        spinOnce();
        if (cycleAccum > FM_CALCS_PER_MICROS) {
            sampleRate++;
            synchronized (lock) {
                sampleQueue.offer(sample);
                queueLen++;
            }
            cycleAccum -= FM_CALCS_PER_MICROS;
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
}
