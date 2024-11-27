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

import omegadrive.sound.BlipBaseSound;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.ym2612.Ym2612RegSupport;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector.Region;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * NTSC_MCLOCK_MHZ = 53693175;
 * PAL_MCLOCK_MHZ = 53203424;
 * <p>
 * NTSC:
 * FM CLOCK = MCLOCK/7 = 7670453
 * NUKE_CLOCK = FM_CLOCK/6 = 1278409
 * CHIP_OUTPUT_RATE = NUKE_CLOCK/24 = 53267 (52781 Hz (PAL))
 * <p>
 */
public class Ym2612Nuke extends BlipBaseSound.BlipBaseSoundImpl implements MdFmProvider {

    private static final Logger LOG = LogHelper.getLogger(Ym2612Nuke.class.getSimpleName());

    private final static int AUDIO_SCALE_BITS = 3;

    private static final int NTSC_BLIP_INPUT_RATE_HZ = 53400;
    private static final int PAL_BLIP_INPUT_RATE_HZ = 53100;

    private final IYm3438 ym3438;
    private IYm3438.IYm3438_Type chip;
    private Ym3438Context state;
    private final Ym2612RegSupport regSupport;

    /**
     * Audio sync mode: 0=perfect sync (slow), 2=less than perfect sync (faster)
     * values greater than 2 are generally not worth it.
     */
    private final static int syncAudioMode =
            Integer.parseInt(System.getProperty("helios.md.fm.sync.mode", "2"));
    private final static int syncAudioCycles = Math.max(1, 24 * syncAudioMode);
    private int syncAudioCnt = 0;
    protected double microsPerInputSample;


    public Ym2612Nuke(AudioFormat audioFormat, Region region) {
        this(new IYm3438.IYm3438_Type(), region, audioFormat, SoundProvider.getFmSoundClock(region));
    }

    private Ym2612Nuke(IYm3438.IYm3438_Type chip, Region region, AudioFormat audioFormat, double sourceSampleRate) {
        super("nuke2612", region, region.isPal() ? PAL_BLIP_INPUT_RATE_HZ : NTSC_BLIP_INPUT_RATE_HZ, Channel.STEREO, audioFormat);
        this.ym3438 = new Ym3438();
        this.chip = chip;
        this.ym3438.OPN2_SetChipType(IYm3438.ym3438_mode_readmode);
        this.state = new Ym3438Context();
        state.chip = chip;
        this.regSupport = new Ym2612RegSupport();
        this.microsPerInputSample = (1_000_000.0 / (sourceSampleRate / 6));
        LOG.info("FM instance, clock: {}, sampleRate: {}", sourceSampleRate, audioFormat.getSampleRate());
    }

    @Override
    public void setMicrosPerTick(double microsPerTick) {
        microsPerInputSample = microsPerTick;
    }

    @Override
    public void reset() {
        ym3438.OPN2_Reset(chip);
        state.reset();
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

    @Override
    public int read() {
        spin();
        return ym3438.OPN2_Read(chip, 0x4000);
    }

    @Override
    public void tick() {
        if (++syncAudioCnt == syncAudioCycles) {
            spin();
        }
    }

    private void spin() {
        for (int i = 0; i < syncAudioCnt; i++) {
            spinOnce();
        }
        syncAudioCnt = 0;
    }

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
            state.ym3438_diffLR_sampleL = (((sampleL - sampleR) & 0xFFFF) << 16) | (sampleL & 0xFFFF);

            blipProvider.playSample(prevL << AUDIO_SCALE_BITS, prevR << AUDIO_SCALE_BITS);
            tickCnt++;
        }
    }

    @Override
    public int getSample16bit(boolean left) {
        throw new IllegalArgumentException("not supported");
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

        @Serial
        private static final long serialVersionUID = -2921159132727518547L;

        int ym3438_cycles = 0;
        int ym3438_diffLR_sampleL = 0;
        final int[][] ym3438_accm = new int[24][2];
        IYm3438.IYm3438_Type chip;

        public void reset() {
            ym3438_cycles = 0;
            ym3438_diffLR_sampleL = 0;
            Arrays.stream(ym3438_accm).forEach(row -> Arrays.fill(row, 0));
        }
    }
}
