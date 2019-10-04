/*
 * OPLL
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 04/10/19 14:18
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

package omegadrive.sound.fm.ym2413;

import java.io.Serializable;

// Port of emu2413.c v0.61 -- YM2413 emulator written by Mitsutaka Okazaki
// zlib license

/**
 * Ported by the nintaco team: https://nintaco.com
 * Original C implementation: https://github.com/digital-sound-antiques/emu2413
 * <p>
 * ---
 * 2019-10-01 Federico Berti
 * - back-ported 0.63 changes: Support per-channel output
 * - update 2413 instruments
 * - adaptation work
 */
public class OPLL implements Serializable {

    private static final long serialVersionUID = 0;

    public int adr;
    public int out;

    public int realstep;
    public int oplltime;
    public int opllstep;
    public int[] pan = new int[16];

    // Register
    public int[] reg = new int[0x40];
    public boolean[] slot_on_flag = new boolean[18];

    // Pitch Modulator
    public int pm_phase;
    public int lfo_pm;

    // Amp Modulator
    public int am_phase;
    public int lfo_am;

    // Noise Generator
    public int noise_seed;

    // Channel Data
    public int[] patch_number = new int[9];
    public boolean[] key_status = new boolean[9];

    // Slot
    public OPLL_SLOT[] slot = new OPLL_SLOT[18];

    // Voice Data
    public OPLL_PATCH[] patch = new OPLL_PATCH[19 * 2];
    public int[] patch_update = new int[2]; // flag for check patch update

    /* Output of each channels / 0-8:TONE, 9:BD 10:HH 11:SD, 12:TOM, 13:CYM */
    int[] ch_out = new int[14];


    public OPLL() {
        for (int i = slot.length - 1; i >= 0; i--) {
            slot[i] = new OPLL_SLOT();
        }
        for (int i = patch.length - 1; i >= 0; i--) {
            patch[i] = new OPLL_PATCH();
        }
    }

    public static class OPLL_SLOT implements Serializable {

        private static final long serialVersionUID = 0;

        public OPLL_PATCH patch;

        public boolean type;     // false : modulator, true : carrier

        // OUTPUT
        public int feedback;
        public int[] output = new int[2];   // Output value of slot

        // for Phase Generator (PG)
        public int[] sintbl;     // Wavetable
        public int phase;        // Phase
        public int dphase;       // Phase increment amount
        public int pgout;        // output

        // for Envelope Generator (EG)
        public int fnum;         // F-Number
        public int block;        // Block
        public int volume;       // Current volume
        public boolean sustine;  // Sustine true = ON, false = OFF
        public int tll;             // Total Level + Key scale level
        public int rks;          // Key scale offset (Rks)
        public int eg_mode;      // Current state
        public int eg_phase;     // Phase
        public int eg_dphase;    // Phase increment amount
        public int egout;        // output
    }

    public static final class OPLL_PATCH implements Serializable {

        private static final long serialVersionUID = 0;

        public int TL;
        public int FB;
        public int EG;
        public int ML;
        public int AR;
        public int DR;
        public int SL;
        public int RR;
        public int KR;
        public int KL;
        public int AM;
        public int PM;
        public int WF;

        public static final void copy(OPLL_PATCH source, OPLL_PATCH destination) {
            destination.TL = source.TL;
            destination.FB = source.FB;
            destination.EG = source.EG;
            destination.ML = source.ML;
            destination.AR = source.AR;
            destination.DR = source.DR;
            destination.SL = source.SL;
            destination.RR = source.RR;
            destination.KR = source.KR;
            destination.KL = source.KL;
            destination.AM = source.AM;
            destination.PM = source.PM;
            destination.WF = source.WF;
        }
    }

    // Definition of envelope mode
    public interface OPLL_EG_STATE {
        int READY = 0;
        int ATTACK = 1;
        int DECAY = 2;
        int SUSHOLD = 3;
        int SUSTINE = 4;
        int RELEASE = 5;
        int SETTLE = 6;
        int FINISH = 7;
    }
}
