/*
 * Ym2612Nuke
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 04/10/19 11:08
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

package omegadrive.sound.fm.nukeykt;

import omegadrive.sound.fm.MdFmProvider;

/**
 *
 * TODO check this:
 * https://github.com/nukeykt/Nuked-OPN2/issues/4
 */
public class Ym2612Nuke implements MdFmProvider {
    private IYm3438 ym3438;
    private IYm3438.IYm3438_Type chip;

    private int[][] ym3438_accm = new int[24][2];
    int ym3438_cycles = 0;
    int[] ym3438_sample = new int[2];
    double cycleAccum = 0;

    private int CLOCK_HZ = 0;
    private double CYCLE_PER_MICROS = 0;

    public Ym2612Nuke() {
        ym3438 = new Ym3438();
        chip = new IYm3438.IYm3438_Type();
        ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
    }


    @Override
    public void reset() {
        ym3438.OPN2_Reset(chip);
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
    public void update(int[] buf_lr, int offset, int count) {
        offset <<= 1;
        int end = (count << 1) + offset;
        for (int i = offset; i < end; i += 2) {
            buf_lr[i] = ym3438_sample[0];
            buf_lr[i + 1] = ym3438_sample[1];
        }
    }

    @Override
    public int read() {
        return ym3438.OPN2_Read(chip, 0x4000);
    }

    @Override
    public void init(int clock, int rate) {
        CLOCK_HZ = clock / 6; //nuke runs at ~ 1.67 mhz
        CYCLE_PER_MICROS = clock / 6_000_000.0;
    }


    @Override
    public void tick(double microsPerTick) {
        cycleAccum += microsPerTick * CYCLE_PER_MICROS;
        while (cycleAccum > 1) {
            spinOnce();
            cycleAccum--;
        }
    }


    private void spinOnce() {
        ym3438.OPN2_Clock(chip, ym3438_accm[ym3438_cycles]);
        ym3438_cycles = (ym3438_cycles + 1) % 24;
        if (ym3438_cycles == 0) {
            ym3438_sample[0] = 0;
            ym3438_sample[1] = 0;
            for (int j = 0; j < 24; j++) {
                ym3438_sample[0] += ym3438_accm[j][0];
                ym3438_sample[1] += ym3438_accm[j][1];
            }
        }
    }
}
