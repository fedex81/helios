package omegadrive.sound.fm.nukeykt;

import omegadrive.sound.fm.FmProvider;

import java.util.Arrays;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 * <p>
 * TODO check this:
 * https://github.com/nukeykt/Nuked-OPN2/issues/4
 */
public class Ym2612Nuke implements FmProvider {
    private IYm3438 ym3438;
    private int count = 0;
    private IYm3438.IYm3438_Type chip;
    private int[][] ym3438_accm = new int[24][2];
    private int[] buffer = new int[65000];

    private int CLOCK_HZ = 1_670_000;
    private double CYCLE_PER_MICROS = CLOCK_HZ / 1_000_000d;

    public Ym2612Nuke() {
        ym3438 = new Ym3438();
        chip = new IYm3438.IYm3438_Type();
    }


    @Override
    public int reset() {
        ym3438.OPN2_Reset(chip);
        count = 0;
        Arrays.fill(buffer, 0);
        return 0;
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
        offset *= 2;
        int end = count * 2 + offset;
        int len = count * 2;
        int readIdxEnd = (readIndex + len) % buffer.length;
        if (readIdxEnd < readIndex) {
            len = buffer.length - readIndex;
            System.arraycopy(buffer, readIndex, buf_lr, offset, len);
        } else {
            System.arraycopy(buffer, readIndex, buf_lr, offset, len);
        }
        readIndex = readIdxEnd;
    }

    @Override
    public int read() {
        return ym3438.OPN2_Read(chip, 0x4000);
    }

    @Override
    public int init(int clock, int rate) {
        return 0; //TODO
    }

    int ym3438_cycles = 0;
    int[] ym3438_sample = new int[2];
    int writeIndex = 0;
    int readIndex = 0;

    @Override
    public void tick(double microsPerTick) {
        int[] res = getSample();
        if (res != null) {
            writeIndex %= buffer.length;
            buffer[writeIndex] = ym3438_sample[0] * 5;
            buffer[writeIndex + 1] = ym3438_sample[1] * 5;
            writeIndex += 2;
        }
    }

    private int[] getSample() {
        ym3438.OPN2_Clock(chip, ym3438_accm[ym3438_cycles]);
        ym3438_cycles = (ym3438_cycles + 1) % 24;
        if (ym3438_cycles == 0) {
            ym3438_sample[0] = 0;
            ym3438_sample[1] = 0;
            for (int j = 0; j < 24; j++) {
                ym3438_sample[0] += ym3438_accm[j][0];
                ym3438_sample[1] += ym3438_accm[j][1];
            }
            return ym3438_sample;
        }
        return null;
    }
}
