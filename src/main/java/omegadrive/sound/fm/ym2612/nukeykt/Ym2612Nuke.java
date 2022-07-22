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

import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.VariableSampleRateSource;
import omegadrive.sound.fm.ym2612.Ym2612RegSupport;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.Serializable;
import java.util.Arrays;

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
public class Ym2612Nuke extends VariableSampleRateSource implements MdFmProvider {

    private static final Logger LOG = LogHelper.getLogger(Ym2612Nuke.class.getSimpleName());

    private final static int AUDIO_SCALE_BITS = 3;

    private final IYm3438 ym3438;
    private IYm3438.IYm3438_Type chip;
    private Ym3438Context state;
    private final Ym2612RegSupport regSupport;

    private final static int syncAudioMode =
            Integer.parseInt(System.getProperty("helios.md.fm.sync.mode", "2"));
    private final static int syncAudioCycles = Math.max(1, 24 * syncAudioMode);
    private int syncAudioCnt = 0;
    private int prevL, prevR;

    private double cycleAccum = 0;

    public Ym2612Nuke(AudioFormat audioFormat, double sourceSampleRate) {
        this(new IYm3438.IYm3438_Type(), audioFormat, sourceSampleRate);
    }

    // sourceSampleRate ~= 7.6 mhz
    private Ym2612Nuke(IYm3438.IYm3438_Type chip, AudioFormat audioFormat, double sourceSampleRate) {
        super(sourceSampleRate / 6, audioFormat, "fmNuke", AUDIO_SCALE_BITS);
        this.ym3438 = new Ym3438();
        this.chip = chip;
        this.ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
        this.state = new Ym3438Context();
        state.chip = chip;
        this.regSupport = new Ym2612RegSupport();
    }

    @Override
    public void setMicrosPerTick(double microsPerTick) {
        setMicrosPerInputSample(microsPerTick);
    }

    @Override
    public void reset() {
        super.reset();
        ym3438.OPN2_Reset(chip);
        state.reset();
        cycleAccum = 0;
        syncAudioCnt = prevL = prevR = 0;
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return regSupport.readRegister(type, regNumber);
    }

    @Override
    public void write(int addr, int data) {
        spin();
        ym3438.OPN2_Write(chip, addr, data);
        regSupport.write(addr, data);
    }

    private void addSample() {
        if (cycleAccum > fmCalcsPerMicros) {
            super.addStereoSample(prevL, prevR);
            cycleAccum -= fmCalcsPerMicros;
        }
    }

    @Override
    public int read() {
        spin();
        return ym3438.OPN2_Read(chip, 0x4000);
    }

    //Output frequency: 53.267 kHz (NTSC), 52.781 kHz (PAL)
    @Override
    public void tick() {
        if (++syncAudioCnt == syncAudioCycles) {
            spin();
        }
    }

    private final void spin() {
        for (int i = 0; i < syncAudioCnt; i++) {
            cycleAccum += microsPerInputSample;
            spinOnce();
            addSample();
        }
        syncAudioCnt = 0;
    }

    @Override
    protected void spinOnce() {
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
            filterAndSet(sampleL, sampleR);
        }
    }

    //1st order lpf: p[n]=αp[n−1]+(1−α)pi[n] with α = 0.5
    //The cutoff frequency fco = fs*(1−α)/2πα, where fs is your sampling frequency.
    //fco ~= 8.5 khz
    private void filterAndSet(int sampleL, int sampleR) {
        sampleL = (sampleL + prevL) >> 1;
        sampleR = (sampleR + prevR) >> 1;
        state.ym3438_diffLR_sampleL = (((sampleL - sampleR) & 0xFFFF) << 16) | (sampleL & 0xFFFF);
        prevL = sampleL;
        prevR = sampleR;
    }

    public void setState(Ym3438Context state) {
        spin();
        if (state != null) {
            this.state = state;
            this.chip = state.chip;
            this.syncAudioCnt = state.ym3438_cycles;
        } else {
            LOG.warn("Unable to restore state, FM will not work");
            this.chip.reset();
            this.state.reset();
        }
    }

    public Ym3438Context getState() {
        return state;
    }

    public static class Ym3438Context implements Serializable {

        private static final long serialVersionUID = -2921159132727518547L;

        int ym3438_cycles = 0;
        int ym3438_diffLR_sampleL = 0;
        int[][] ym3438_accm = new int[24][2];
        IYm3438.IYm3438_Type chip;

        public void reset() {
            ym3438_cycles = 0;
            ym3438_diffLR_sampleL = 0;
            Arrays.stream(ym3438_accm).forEach(row -> Arrays.fill(row, 0));
        }
    }
}
