/*
 * VRC7SoundChip
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 04/10/19 14:12
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

package omegadrive.sound.fm.ym2413.vrc7;

/*
 * HalfNES by Andrew Hoffman
 * Licensed under the GNU GPL Version 3. See LICENSE file
 */

import java.util.Arrays;

/**
 * @author Andrew
 */
public class VRC7SoundChip {

    public static final int FM_HZ = 1789773; //NTSC ONLY
    public static final int CHANNELS = 9;

    public final static int BIT0 = 1, BIT1 = 2, BIT2 = 4, BIT3 = 8, BIT4 = 16,
            BIT5 = 32, BIT6 = 64, BIT7 = 128, BIT8 = 256, BIT9 = 512,
            BIT10 = 1024, BIT11 = 2048, BIT12 = 4096, BIT13 = 8192,
            BIT14 = 16384, BIT15 = 32768;
    private final static int[] LOGSIN = genlogsintbl(), EXP = genexptbl(),
            AM = genamtbl();
    private final static double[] MULTIPLIER = {0.5, 1, 2, 3, 4, 5,
            6, 7, 8, 9, 10, 10, 12, 12, 15, 15}, VIBRATO = genvibtbl();
    private final static int[] KEYSCALE = {0, 1536, 2048, 2368, 2560,
            2752, 2880, 3008, 3072, 3200, 3264, 3328, 3392, 3456, 3520, 3584
    };
    final private static int ZEROVOL = 8388608; //2^23
    final private static int MAXVOL = 0;
    private final static int[] ATTACKVAL = {0, 0, 0, 0,
            98, 120, 146, 171,
            195, 216, 293, 341,
            390, 471, 602, 683,
            780, 964, 1168, 1366,
            1560, 1927, 2315, 2731,
            3075, 3855, 4682, 5461,
            6242, 8035, 9364, 10921,
            12480, 15423, 18727, 21856,
            24960, 30847, 37413, 43713,
            51130, 61580, 74991, 87425,
            99841, 123161, 149319, 173949,
            200870, 241044, 281218, 312464,
            337461, 401739, 496266, 562435,
            602609, 766957, 937392, 1205218,
            8388607, 8388607, 8388607, 8388607,
            8388607, 8388607, 8388607, 8388607,
            8388607, 8388607, 8388607, 8388607,
            8388607, 8388607, 8388607, 8388607};
    private final static int[] DECAYVAL = {0, 0, 0, 0,
            8, 10, 12, 14, //+2
            16, 20, 24, 28, //+4
            32, 40, 48, 56, //+8
            65, 77, 96, 112, //+16
            129, 161, 193, 224, //+32
            258, 321, 386, 449, //+64
            516, 643, 771, 898, //+128
            1032, 1285, 1542, 1796, //+256
            2064, 2570, 3084, 3591, //+512
            4211, 5268, 6167, 7183, //+1024
            8255, 10282, 12407, 14360, //+2048
            16510, 20552, 24668, 28745, //+4096
            33020, 41154, 49336, 57391, //+8192
            66169, 82308, 98673, 114783, //+16384
            132859, 132859, 132859, 132859,
            132859, 132859, 132859, 132859,
            132859, 132859, 132859, 132859,
            132859, 132859, 132859, 132859,};
    public final int[] usertone = {0x49, 0x4c, 0x4c, 0x12, 0x00, 0x00, 0x00, 0x00};

    public final int[][] instdata = { //instrument parameters
            usertone, //modifiable user tone register is instrument 0
            //finally accurate dumped hardcoded instruments from ROM! (Mar 2019)
            {0x03, 0x21, 0x05, 0x06, 0xE8, 0x81, 0x42, 0x27}, //Bell
            {0x13, 0x41, 0x14, 0x0D, 0xD8, 0xF6, 0x23, 0x12}, //Guitar
            {0x11, 0x11, 0x08, 0x08, 0xFA, 0xB2, 0x20, 0x12}, //Wurlitzer
            {0x31, 0x61, 0x0c, 0x07, 0xA8, 0x64, 0x61, 0x27}, //Flute
            {0x32, 0x21, 0x1E, 0x06, 0xE1, 0x76, 0x01, 0x28}, //Clarinet
            {0x02, 0x01, 0x06, 0x00, 0xA3, 0xE2, 0xF4, 0xF4}, //Synth
            {0x21, 0x61, 0x1D, 0x07, 0x82, 0x81, 0x11, 0x07}, //Trumpet
            {0x23, 0x21, 0x22, 0x17, 0xA2, 0x72, 0x01, 0x17}, //Organ
            {0x35, 0x11, 0x25, 0x00, 0x40, 0x73, 0x72, 0x01}, //Bells
            {0xB5, 0x01, 0x0F, 0x0F, 0xA8, 0xA5, 0x51, 0x02}, //Vibes
            {0x17, 0xC1, 0x24, 0x07, 0xF8, 0xF8, 0x22, 0x12}, //Vibraphone
            {0x71, 0x23, 0x11, 0x06, 0x65, 0x74, 0x18, 0x16}, //Tutti
            {0x01, 0x02, 0xD3, 0x05, 0xC9, 0x95, 0x03, 0x02}, //Fretless
            {0x61, 0x63, 0x0C, 0x00, 0x94, 0xC0, 0x33, 0xF6}, //Synth Bass
            {0x21, 0x72, 0x0D, 0x00, 0xC1, 0xD5, 0x56, 0x06}  //Sweep
    };
    private final EnvState[] modenv_state = new EnvState[CHANNELS], carenv_state = new EnvState[CHANNELS];
    private final int[] vol = new int[CHANNELS], freq = new int[CHANNELS],
            octave = new int[CHANNELS], instrument = new int[CHANNELS],
            mod = new int[CHANNELS],
            oldmodout = new int[CHANNELS], out = new int[CHANNELS];
    private final boolean[] key = new boolean[CHANNELS], chSust = new boolean[CHANNELS];
    private final double[] phase = new double[CHANNELS];
    private final int[] modenv_vol = new int[CHANNELS], carenv_vol = new int[CHANNELS];
    boolean rhythmOn = false;
    int ch = 0;
    int lpaccum = 0;
    int lpaccum2 = 0;
    private int fmctr = 0, amctr = 0; //free running counter for indices
    private int s; // sign flag: positive (1), negative (-1) or muted (0)

    public VRC7SoundChip() {
        Arrays.fill(modenv_state, EnvState.CUTOFF);
        Arrays.fill(carenv_state, EnvState.CUTOFF);
        Arrays.fill(modenv_vol, 511);
        Arrays.fill(carenv_vol, 511);
    }

    public static int clamp(final int a) {
        return (a != (a & 0xff)) ? ((a < 0) ? 0 : 255) : a;
    }

    private static double[] genvibtbl() {
        //vibrato wavetable. Yes this is a waste of memory. sue me.
        //from looking at genplus gx code, the vibrato depth is supposed
        //to vary per octave, but exactly how is complex.

        double l = FM_HZ / CHANNELS;
        double f = 6.4;
        int depth = 10; //blatant guess
        double[] tbl = new double[(int) Math.ceil(l / f)];
        for (int x = 0; x < tbl.length; ++x) {
            tbl[x] = (depth * tri(2 * Math.PI * f * x / l));
        }
        return tbl;
    }

    private static int[] genamtbl() {
        double l = FM_HZ / CHANNELS;
        double f = 3.7;
        int depth = 128;
        int[] tbl = new int[(int) Math.ceil(l / f)];//one full cycle of wave
        for (int x = 0; x < tbl.length; ++x) {
            tbl[x] = (int) (depth * tri(2 * Math.PI * f * x / l) + depth);
            //should be a triangle wave?
        }
        return tbl;
    }

    private static double tri(double x) {
        //triangle wave function.
        x %= 2 * Math.PI;
        if (x < (Math.PI / 2)) {
            return x / (Math.PI);
        } else if (x < (3 * Math.PI) / 2) {
            return 1 - (x / (Math.PI));
        } else {
            return x / (Math.PI) - 2;
        }
    }

    private static int[] genlogsintbl() {
        int[] tbl = new int[256];
        for (int i = 0; i < tbl.length; ++i) {
            //y = round(-log(sin((x+0.5)*pi/256/2))/log(2)*256)
            //see https://docs.google.com/Doc?id=dd8kqn9f_13cqjkf4gp for info
            tbl[i] = (int) Math.round(-Math.log(Math.sin((i + 0.5) * Math.PI / 256 / 2)) / Math.log(2) * 256);
        }
        return tbl;
    }

    private static int[] genexptbl() {
        int[] tbl = new int[256];
        for (int i = 0; i < tbl.length; ++i) {
            //y = round((power(2, x/256)-1)*1024)
            tbl[i] = (int) Math.round((Math.pow(2, i / 256.) - 1) * 1024.);
        }
        return tbl;
    }

    public final void write(int register, int data) {
        switch (register) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                //parameters for instrument 0 (user settable instrument)
                usertone[register & 7] = data;
                break;
            case 0xE:
                //rhythm control
                boolean val = (data & 0x20) > 0;
                if (val != rhythmOn) {
                    System.out.println("Rhythm On: " + rhythmOn);
                    rhythmOn = val;
                }
                break;
            case 0x10:
            case 0x11:
            case 0x12:
            case 0x13:
            case 0x14:
            case 0x15:
            case 0x16:
            case 0x17:
            case 0x18:
                //frequency registers for ch. 0-8
                int n = register - 0x10;
                freq[n] = (freq[n] & 0xf00) | data;
                break;
            case 0x20:
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24:
            case 0x25:
            case 0x26:
            case 0x27:
            case 0x28:
//f: Upper bit of frequency
//o: Octave Select
//t: Channel keying on/off (key on = note starts, key off: note decays).
//s: bit 5 is _channel_ sustain,
//?: bit 6 and 7 are unused?
                int m = register - 0x20;
                octave[m] = (data >> 1) & 7;
                freq[m] = (freq[m] & 0xff) | ((data & 1) << 8);
                if (((data & (BIT4)) != 0) && !key[m]) {
                    //when note is keyed on
                    carenv_state[m] = EnvState.CUTOFF;
                    modenv_state[m] = EnvState.CUTOFF;
                    // printarray(key);
                }
                //TODO: when key is released,
                //modulator release shouldn't do anything if sustain is on
                //http://famitracker.com/forum/posts.php?id=6804
                key[m] = ((data & (BIT4)) != 0);
                chSust[m] = ((data & (BIT5)) != 0);
                break;
            case 0x30:
            case 0x31:
            case 0x32:
            case 0x33:
            case 0x34:
            case 0x35:
            case 0x36:
            case 0x37:
            case 0x38:
                //top 4 bits instrument number, bottom 4 volume
                int j = register - 0x30;
                vol[j] = data & 0xf;
                //System.err.println(j + " " + hex(data));
                instrument[j] = (data >> 4) & 0xf;
                break;
            default:
                System.err.println(Integer.toHexString(register) + " doesn't exist " + Integer.toHexString(data));
        }
    }

    public final void clock(final int cycle) {
        /*
         real chip runs at ~3.6 mhz from a separate oscillator
         but this emulation operates at NTSC NES clock freq of 1.789
         this would be a problem if anyone wanted to use it on a PAL system
         real chip takes 72 cycles to advance through all ch (49716 hz)
         I update all ch every 36 NES clocks (about the same speed) but I do the
         carrier and the modulator in the same cycle instead of separately
         */

        for (int i = 0; i < cycle; ++i) {
            ch = (ch + 1) % 36;
            if (ch < CHANNELS) {
                operate();
            }
        }
    }

    private void operate() {
        fmctr = (fmctr + 1) % VIBRATO.length;
        amctr = (amctr + 1) % AM.length;
        phase[ch] += (1 / 512.) * (freq[ch] << (octave[ch]));
        //Tuned this with audacity so it's definitely ok this time.
        phase[ch] %= 1024;
        int[] inst = instdata[instrument[ch]];
        //envelopes
        int modEnvelope = setenvelope(inst, modenv_state, modenv_vol, ch, false)
                << 2;
        int carEnvelope = setenvelope(inst, carenv_state, carenv_vol, ch, true)
                << 2;
        //key scaling
        int keyscale = KEYSCALE[freq[ch] >> 5] - 512 * (7 - octave[ch]);
        if (keyscale < 0) {
            keyscale = 0;
        }
        int modks = inst[2] >> 6;
        modks = (modks == 0) ? 0 : (keyscale >> (3 - modks));
        int carks = (inst[3] >> 6);
        carks = (carks == 0) ? 0 : (keyscale >> (3 - carks));
        int fb = (~inst[3] & 7);
        //now the operator cells
        //invaluable info: http://gendev.spritesmind.net/forum/viewtopic.php?t=386
        //http://www.smspower.org/maxim/Documents/YM2413ApplicationManual
        //http://forums.nesdev.com/viewtopic.php?f=3&t=9102
        final double modVibrato = ((inst[0] & (BIT6)) != 0) ? VIBRATO[fmctr] * (1 << octave[ch]) : 0;
        final double modFreqMultiplier = MULTIPLIER[inst[0] & 0xf];
        final int modFeedback = (fb == 7) ? 0 : (mod[ch] + oldmodout[ch]) >> (2 + fb);
        //no i don't know why it adds the last 2 old outputs but MAME
        //does it that way and the feedback doesn't sound right w/o it
        final int mod_f = modFeedback + (int) (modVibrato + modFreqMultiplier * phase[ch]);
        //each of these values is an attenuation value
        final int modVol = (inst[2] & 0x3f) * 32;//modulator vol
        final int modAM = ((inst[0] & (BIT7)) != 0) ? AM[amctr] : 0;
        final boolean modRectify = ((inst[3] & (BIT3)) != 0);
        //calculate modulator operator value
        mod[ch] = operator(mod_f, (modVol + modEnvelope + modks + modAM), modRectify) << 2;
        oldmodout[ch] = mod[ch];
        //now repeat most of that for the carrier
        final double carVibrato = ((inst[1] & (BIT6)) != 0) ? VIBRATO[fmctr] * (freq[ch] << octave[ch]) / 512. : 0;
        final double carFreqMultiplier = MULTIPLIER[inst[1] & 0xf];
        final int carFeedback = (mod[ch] + oldmodout[ch]) >> 1; //inaccurately named
        final int car_f = carFeedback + (int) (carVibrato + carFreqMultiplier * phase[ch]);
        final int carVol = vol[ch] * 128; //4 bits for carrier vol not 6
        final int carAM = ((inst[1] & (BIT7)) != 0) ? AM[amctr] : 0;
        final boolean carRectify = ((inst[3] & (BIT4)) != 0);
        out[ch] = operator(car_f, (carVol + carEnvelope + carks + carAM), carRectify) << 2;
        outputSample(ch);
    }

    private int operator(final int phase, final int gain, final boolean rectify) {
        return exp((logsin(phase, rectify) + gain));
    }

    private int exp(int val) {
        //perform e^x function on 13 bit fp output value using the hardware table on the chip
        //value should never be negative; if it is, find out why.
//        if (val < 0) {
//            val = 0;
//            System.err.println("why");
//
//        }
        //values saturate instead of rolling over in the actual hardware
        if (val > (BIT13) - 1) {
            val = (BIT13) - 1;
        }
        //val &= (BIT12); //uncomment for sega smash pack vol. 1 for dreamcast
        int mantissa = EXP[(-val & 0xff)];
        int exponent = (-val) >> 8;
//        int a = (int) Math.scalb(mantissa + 1024, exponent) * s; //correct but slow
        return ((((mantissa + 1024) >> (-exponent)))) * s; //not correct for negative #s
    }

    private int logsin(final int x, final boolean rectify) {
        //s stores sign of the output, in actual hw the sign bit bypasses
        //everything else and goes directly to the dac.
        switch ((x >> 8) & 3) {
            case 0:
                s = 1;
                return LOGSIN[(x & 0xff)];
            case 1:
                s = 1;
                return LOGSIN[255 - (x & 0xff)];
            case 2:
                s = rectify ? 0 : -1;
                return LOGSIN[(x & 0xff)];
            case 3:
            default:
                s = rectify ? 0 : -1;
                return LOGSIN[255 - (x & 0xff)];
        }
    }

    private void outputSample(int ch) {
        int sample = out[ch] * 24;
        //two stage low pass filter (looked @ schematic of hybrid on PCB)
        sample += lpaccum;
        lpaccum -= sample >> 2;
        int j = lpaccum;
        j += lpaccum2;
        lpaccum2 -= j >> 2;
    }

    public final int getval() {
        return lpaccum2;
    }

    //    Twiddler t = new Twiddler(0.4);
    private int setenvelope(final int[] instrument, final EnvState[] state,
                            final int[] vol, final int ch, final boolean isCarrier) {
        final boolean keyscaleRate = ((instrument[(isCarrier ? 1 : 0)] & (BIT4)) != 0);
        final int ksrShift = keyscaleRate ?
                (octave[ch] << 1) + (freq[ch] >> 8)
                : octave[ch] >> 1;
        //^ the key scaling bit (java should really have unions, this is such a mess)
        /*
         Most of these constants were computed backwards from a
         table in a badly translated YM2413 technical manual
         (that was given in ms to decay 40db) and are thus only approximate.
         The key scaling stuff is similarly just a best guess.
         I suspect it's a 23 bit int value and each envelope update changes
         at least 1 LSB
         */

        //from docs on the OPL3: envelope starts at 511 (max attenuation)
        //and counts down to zero (no attenuation)
        //on real HW the envelope out is probably the upper 9 bits of a 23 bit
        //attenuation register (this would add 1 LSB per clock at slowest rate)
        //System.err.println(state[ch]);
        switch (state[ch]) {
            default:
            case CUTOFF:
                if (vol[ch] < ZEROVOL) {
                    vol[ch] += 16384; //decay to off in 10ms
                    /*
                     programmer's manual seems to say it takes a few ms to decay
                     before the new note starts its attack run.
                     need to listen really hard to the HW recordings and tune
                     this by ear
                     */
                } else {
                    vol[ch] = ZEROVOL;
                    if (key[ch]) {
                        /*the programmer's manual suggests that sound has to
                         decay back to zero volume when keyed on before the attack
                         happens, but other references don't say this
                         */
                        state[ch] = EnvState.ATTACK;
                        phase[ch] = 0;
                        //reset phase to avoid popping? can't tell if the chip does this.
                    }
                }
                break;
            case ATTACK:
                if (vol[ch] > MAXVOL + 8) {
                    vol[ch] -= ATTACKVAL[
                            (instrument[(isCarrier ? 5 : 4)] >> 4) * 4
                                    + ksrShift
                            ];
                } else {
                    state[ch] = EnvState.DECAY;
                }
                if (!key[ch]) {
                    state[ch] = EnvState.RELEASE;
                }
                break;
            case DECAY:
                if (vol[ch] < ((instrument[(isCarrier ? 7 : 6)] >> 4)) * 524288) { // <-- check this 524288
                    //the higher the sustain value is, the lower the volume when
                    //it switches to sustain.
                    vol[ch] += DECAYVAL[(instrument[(isCarrier ? 5 : 4)] & 0xf) * 4
                            + ksrShift];
                } else {
                    state[ch] = EnvState.RELEASE;
                }
                if (!key[ch]) {
                    state[ch] = EnvState.RELEASE;
                }
                break;
            case RELEASE:

                //there are 3 things we need to know:
                //1. Is the key on?
                //2. Is the channel sustain bit set?
                //3. Is bit 5 in patch register 0 or 1 set?
                //What makes this especially confusing is that the sustain bit in the patch
                //is bit 5 and the sustain bit in the channel is *also* a bit 5,
                //in its respective register (ugh)
                //for consistency with it though let's call the channel sustain SUS
                //and the patch register D5
                boolean d5 = ((instrument[isCarrier ? 1 : 0] & (BIT5)) != 0);
                boolean SUS = chSust[ch];
                if (key[ch]) {
                    //if we're keyed on
                    if (d5) {
                        //sustained tone
                        //don't decay at all
                    } else {
                        //percussive tone
                        //decay at release rate
                        vol[ch] += DECAYVAL[(instrument[(isCarrier ? 7 : 6)] & 0xf) * 4
                                + ksrShift];
                    }
                } else {
                    //key is off
                    if (d5) {
                        //sustained tone
                        if (SUS) {
                            //decay at rate of 1.2 seconds to cut off
                            vol[ch] += DECAYVAL[5 * 4
                                    + ksrShift];
                        } else {
                            //decay at release rate
                            vol[ch] += DECAYVAL[(instrument[(isCarrier ? 7 : 6)] & 0xf) * 4
                                    + ksrShift];

                        }
                    } else {
                        //percussive tone
                        if (SUS) {
                            //decay at rate of 1.2 seconds to cut off
                            vol[ch] += DECAYVAL[5 * 4
                                    + ksrShift];
                        } else {
                            //decay at release rate prime, 310ms to cutoff
                            //according to the docs,
                            //or a rate of 7 according
                            //to tests on nesdev forums.
                            vol[ch] += DECAYVAL[7 * 4
                                    + ksrShift];
                            //maybe we do apply key scaling to these still
                        }
                    }
                }
                break;
            //there was also something about one of the decay values not working
            //if a modulator or something
            //on the famitracker forums or somewhere
        }
        if (vol[ch] < MAXVOL) {
            vol[ch] = MAXVOL;
        }
        if (vol[ch] > ZEROVOL) {
            vol[ch] = ZEROVOL;
        }
        if (state[ch] == EnvState.ATTACK) {
            //logarithmic envelope attack
            //48 dB - (48 dB * ln(EGC) / ln(1<<23)) from Disch's doc
            //also accounting for env ctr is running down not up
            //this is slow and probably wrong vs real chip
            //but stays for now bc it works and sounds ok
            int output = 8388608 - (int) (8388608 * Math.log(8388608 - vol[ch]) / Math.log(8388608));
            return output >> 14;
        }
        return vol[ch] >> 14;
    }

    //Emulates the YM2413 sound chip, pretty much only found in Lagrange Point
    //sound test in lagrange point: hold A and B on controller 2 and reset.
    //the VRC7 version is permanently forced into percussion mode
    //but there's no percussion output pin,
    //so it only supports 6 output channels instead of 9.
    private enum EnvState {

        CUTOFF, ATTACK, DECAY, RELEASE
    }
}
//these 2 tables calculated with excel based on the envelope length table
//in the YM2413 programming guide.

//decay table is SO CLOSE to neat powers of 2
//and it really has to be created with simple logic
//on the real chip where there is no table of envelope values.
