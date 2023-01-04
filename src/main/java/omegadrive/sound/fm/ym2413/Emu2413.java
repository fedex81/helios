package omegadrive.sound.fm.ym2413;

import static java.lang.Math.*;
import static omegadrive.sound.fm.ym2413.OPLL.OPLL_EG_STATE.*;
import static omegadrive.sound.fm.ym2413.OPLL.OPLL_PATCH;
import static omegadrive.sound.fm.ym2413.OPLL.OPLL_SLOT;

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

public final class Emu2413 {

  //unused
  public static final short[] vrc7_inst = {
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          0x03, 0x21, 0x05, 0x06, 0xB8, 0x82, 0x42, 0x27,
          0x13, 0x41, 0x13, 0x0D, 0xD8, 0xD6, 0x23, 0x12,
          0x31, 0x11, 0x08, 0x08, 0xFA, 0x9A, 0x22, 0x02,
          0x31, 0x61, 0x18, 0x07, 0x78, 0x64, 0x30, 0x27,
          0x22, 0x21, 0x1E, 0x06, 0xF0, 0x76, 0x08, 0x28,
          0x02, 0x01, 0x06, 0x00, 0xF0, 0xF2, 0x03, 0xF5,
          0x21, 0x61, 0x1D, 0x07, 0x82, 0x81, 0x16, 0x07,
          0x23, 0x21, 0x1A, 0x17, 0xCF, 0x72, 0x25, 0x17,
          0x15, 0x11, 0x25, 0x00, 0x4F, 0x71, 0x00, 0x11,
          0x85, 0x01, 0x12, 0x0F, 0x99, 0xA2, 0x40, 0x02,
          0x07, 0xC1, 0x69, 0x07, 0xF3, 0xF5, 0xA7, 0x12,
          0x71, 0x23, 0x0D, 0x06, 0x66, 0x75, 0x23, 0x16,
          0x01, 0x02, 0xD3, 0x05, 0xA3, 0x92, 0xF7, 0x52,
          0x61, 0x63, 0x0C, 0x00, 0x94, 0xAF, 0x34, 0x06,
          0x21, 0x62, 0x0D, 0x00, 0xB1, 0xA0, 0x54, 0x17,
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  };

  /* YM2413 TONES by Mitsutaka Okazaki
   * The following patches are referred from VRC7
   * - @5: Clarinet
   * - @7: Trumpet
   * - Drums: BD/SD/HH/TM/TC
   * https://siliconpr0n.org/archive/doku.php?id=vendor:yamaha:opl2#opll_vrc7_patch_format
   */
  public static final short[] ym2413_inst = {
          /* MULT  MULT modTL DcDmFb AR/DR AR/DR SL/RR SL/RR */
          /*   0     1     2     3     4     5     6    7    */
          0x49, 0x4c, 0x4c, 0x32, 0x00, 0x00, 0x00, 0x00,  //0
          0x61, 0x61, 0x1E, 0x17, 0xF0, 0x7F, 0x00, 0x17,  //1
          0x13, 0x41, 0x17, 0x0E, 0xFF, 0xFF, 0x23, 0x13,  //2
          0x23, 0x01, 0x9A, 0x04, 0xA3, 0xf4, 0xF0, 0x23,  //3
          0x11, 0x61, 0x0e, 0x07, 0xfa, 0x64, 0x70, 0x17,  //4
          0x32, 0x21, 0x1e, 0x06, 0xe1, 0x76, 0x01, 0x28,  //5
          0x21, 0x22, 0x16, 0x05, 0xf0, 0x71, 0x00, 0x18,  //6
          0x21, 0x61, 0x1d, 0x07, 0x82, 0x81, 0x11, 0x07,  //7
          0x23, 0x21, 0x2d, 0x16, 0x90, 0x90, 0x00, 0x07,  //8
          0x21, 0x21, 0x1b, 0x06, 0x64, 0x65, 0x10, 0x17,  //9
          0x21, 0x21, 0x0b, 0x1a, 0x85, 0xa0, 0x70, 0x07,  //A
          0x23, 0x01, 0x83, 0x10, 0xff, 0xb4, 0x10, 0xf4,  //B
          0x97, 0xc1, 0x20, 0x07, 0xff, 0xf4, 0x22, 0x22,  //C
          0x61, 0x00, 0x0c, 0x05, 0xd2, 0xf6, 0x40, 0x43,  //D
          0x01, 0x01, 0x56, 0x03, 0xf4, 0xf0, 0x03, 0x02,  //E
          0x21, 0x41, 0x89, 0x03, 0xf1, 0xf4, 0xf0, 0x23,  //F

          /* drum instruments definitions */
          /* MULTI MULTI modTL  xxx  AR/DR AR/DR SL/RR SL/RR */
          /*   0     1     2     3     4     5     6    7    */
          /* Drums dumped from the VRC7 using debug mode, these are likely also correct for ym2413(OPLL) but need verification */
          0x01, 0x01, 0x18, 0x0f, 0xdf, 0xf8, 0x6a, 0x6d,/* BD */
          0x01, 0x01, 0x00, 0x00, 0xc8, 0xd8, 0xa7, 0x68,/* HH, SD */
          0x05, 0x01, 0x00, 0x00, 0xf8, 0xaa, 0x59, 0x55  /* TOM, TOP CYM */
  };

  public static final int CHANNELS = 9;
  public static final int SLOTS = CHANNELS * 2;
  public static final int PATCHES = SLOTS + 1;

  // Size of Sintable ( 8 -- 18 can be used. 9 recommended.)
  private static final int PG_BITS = 9;
  private static final int PG_WIDTH = 1 << PG_BITS;

  // Phase increment counter
  private static final int DP_BITS = 18;
  private static final int DP_WIDTH = 1 << DP_BITS;
  private static final int DP_BASE_BITS = DP_BITS - PG_BITS;

  // Dynamic range (Accuracy of sin table)
  private static final int DB_BITS = 8;
  private static final double DB_STEP = 48.0 / (1 << DB_BITS);
  private static final int DB_MUTE = 1 << DB_BITS;

  // Dynamic range of envelope
  private static final double EG_STEP = 0.375;
  private static final int EG_BITS = 7;

  // Dynamic range of total level
  private static final double TL_STEP = 0.75;
  private static final int TL_BITS = 6;

  // Dynamic range of sustine level
  private static final double SL_STEP = 3.0;
  private static final int[] mltable = {1, 1 * 2, 2 * 2, 3 * 2, 4 * 2, 5 * 2,
          6 * 2, 7 * 2, 8 * 2, 9 * 2, 10 * 2, 10 * 2, 12 * 2, 12 * 2, 15 * 2,
          15 * 2};
  private static final double[] kltable = {
          dB2(0.000), dB2(9.000), dB2(12.000), dB2(13.875), dB2(15.000),
          dB2(16.125), dB2(16.875), dB2(17.625),
          dB2(18.000), dB2(18.750), dB2(19.125), dB2(19.500), dB2(19.875),
          dB2(20.250), dB2(20.625), dB2(21.000)
  };
  /*************************************************************

   OPLL internal interfaces
   *************************************************************/
  private static final int SLOT_BD1 = 12;
  private static final int SLOT_BD2 = 13;
  private static final int SLOT_HH = 14;

  // Bits for liner value
  private static final int DB2LIN_AMP_BITS = 8;
  private static final int SLOT_AMP_BITS = DB2LIN_AMP_BITS;
  // Bits for envelope phase incremental counter
  private static final int EG_DP_BITS = 22;
  private static final int EG_DP_WIDTH = 1 << EG_DP_BITS;
  // Bits for Pitch and Amp modulator
  private static final int PM_PG_BITS = 8;
  private static final int PM_PG_WIDTH = 1 << PM_PG_BITS;
  private static final int PM_DP_BITS = 16;
  private static final int PM_DP_WIDTH = 1 << PM_DP_BITS;
  private static final int AM_PG_BITS = 8;
  private static final int AM_PG_WIDTH = 1 << AM_PG_BITS;
  private static final int AM_DP_BITS = 16;
  private static final int AM_DP_WIDTH = 1 << AM_DP_BITS;
  // PM table is calcurated by PM_AMP * pow(2,PM_DEPTH*sin(x)/1200)
  private static final int PM_AMP_BITS = 8;
  private static final int PM_AMP = 1 << PM_AMP_BITS;
  // PM speed(Hz) and depth(cent)
  private static final double PM_SPEED = 6.4;
  private static final double PM_DEPTH = 13.75;
  // AM speed(Hz) and depth(dB)
  private static final double AM_SPEED = 3.6413;
  private static final double AM_DEPTH = 4.875;
  private static final int SLOT_SD = 15;
  private static final int SLOT_TOM = 16;
  private static final int SLOT_CYM = 17;
  private static final int[] SL = {
          S2E(0.0), S2E(3.0), S2E(6.0), S2E(9.0), S2E(12.0), S2E(15.0), S2E(18.0),
          S2E(21.0), S2E(24.0), S2E(27.0), S2E(30.0), S2E(33.0), S2E(36.0), S2E(39.0),
          S2E(42.0), S2E(48.0)
  };
  static int INST_VOL_MULT = 8;
  static int RHYTHM_VOL_MULT = 16;
  // Input clock
  private static int fmClockHz = 3579545;

  // WaveTable for each envelope amp
  private static final int[] fullsintable = new int[PG_WIDTH];
  private static final int[] halfsintable = new int[PG_WIDTH];
  private static final int[][] waveform = {fullsintable, halfsintable};
  // LFO Table
  private static final int[] pmtable = new int[PM_PG_WIDTH];
  private static final int[] amtable = new int[AM_PG_WIDTH];
  // Sampling rate
  private static int fmRateHz = 49716;
  // Phase delta for LFO
  private static int pm_dphase
          = (int) (PM_SPEED * PM_DP_WIDTH / (fmClockHz / 72));

  // dB to Liner table
  private static final int[] DB2LIN_TABLE = new int[(DB_MUTE + DB_MUTE) * 2];
  // Liner to Log curve conversion table (for Attack rate).
  private static final int[] AR_ADJUST_TABLE = new int[1 << EG_BITS];

  // Empty voice data
//  private static final OPLL_PATCH null_patch = new OPLL_PATCH();

  // Basic voice Data
  private static final OPLL_PATCH[] default_patch
          = new OPLL_PATCH[(16 + 3) * 2];
  // Phase incr table for Attack
  private static final int[][] dphaseARTable = new int[16][16];
  // Phase incr table for Decay and Release
  private static final int[][] dphaseDRTable = new int[16][16];
  // KSL + TL Table
  private static final int[][][][] tllTable = new int[16][8][1 << TL_BITS][4];
  private static final int[][][] rksTable = new int[2][8][2];
  // Phase incr table for PG
  private static final int[][][] dphaseTable = new int[512][8][16];

  /***************************************************

   Create tables
   ****************************************************/

  // Table for AR to LogCurve.
  private static void makeAdjustTable() {
    AR_ADJUST_TABLE[0] = (1 << EG_BITS) - 1;
    for (int i = 1; i < (1 << EG_BITS); i++) {
      AR_ADJUST_TABLE[i] = (int) ((double) (1 << EG_BITS) - 1 - ((1 << EG_BITS)
              - 1) * log(i) / log(127));
    }
  }

  // Table for dB(0 -- (1<<DB_BITS)-1) to Liner(0 -- DB2LIN_AMP_WIDTH)
  private static void makeDB2LinTable() {
    for (int i = 0; i < DB_MUTE + DB_MUTE; i++) {
      DB2LIN_TABLE[i] = (int) (((1 << DB2LIN_AMP_BITS) - 1)
              * pow(10, -i * DB_STEP / 20.0));
      if (i >= DB_MUTE) {
        DB2LIN_TABLE[i] = 0;
      }
      DB2LIN_TABLE[i + DB_MUTE + DB_MUTE] = -DB2LIN_TABLE[i];
    }
  }

  // Liner(+0.0 - +1.0) to dB((1<<DB_BITS) - 1 -- 0)
  private static int lin2db(final double d) {
    if (d == 0) {
      return (DB_MUTE - 1);
    } else {
      return min(-(int) (20.0 * log10(d) / DB_STEP), DB_MUTE - 1); // 0 -- 127
    }
  }

  // Sin Table
  private static void makeSinTable() {
    for (int i = 0; i < PG_WIDTH / 4; i++) {
      fullsintable[i] = lin2db(sin(2.0 * PI * i / PG_WIDTH));
    }
    for (int i = 0; i < PG_WIDTH / 4; i++) {
      fullsintable[PG_WIDTH / 2 - 1 - i] = fullsintable[i];
    }
    for (int i = 0; i < PG_WIDTH / 2; i++) {
      fullsintable[PG_WIDTH / 2 + i] = DB_MUTE
              + DB_MUTE + fullsintable[i];
    }
    System.arraycopy(fullsintable, 0, halfsintable, 0, PG_WIDTH / 2);
    for (int i = PG_WIDTH / 2; i < PG_WIDTH; i++) {
      halfsintable[i] = fullsintable[0];
    }
  }

  private static double saw(final double phase) {
    if (phase <= PI / 2) {
      return phase * 2 / PI;
    } else if (phase <= PI * 3 / 2) {
      return 2.0 - (phase * 2 / PI);
    } else {
      return -4.0 + phase * 2 / PI;
    }
  }

  // Table for Pitch Modulator
  private static void makePmTable() {
    for (int i = 0; i < PM_PG_WIDTH; i++) {
      pmtable[i] = (int) ((double) PM_AMP * pow(2, PM_DEPTH * saw(2.0
              * PI * i / PM_PG_WIDTH) / 1200));
    }
  }

  // Table for Amp Modulator
  private static void makeAmTable() {
    for (int i = 0; i < AM_PG_WIDTH; i++) {
      amtable[i] = (int) (AM_DEPTH / 2 / DB_STEP * (1.0 + saw(2.0 * PI
              * i / PM_PG_WIDTH)));
    }
  }

  private static int am_dphase
          = (int) (AM_SPEED * AM_DP_WIDTH / (fmClockHz / 72));

  // Phase increment counter table
  private static void makeDphaseTable() {
    for (int fnum = 0; fnum < 512; fnum++) {
      for (int block = 0; block < 8; block++) {
        for (int ML = 0; ML < 16; ML++) {
          dphaseTable[fnum][block][ML] = ((fnum * mltable[ML] << block)
                  >> (20 - DP_BITS));
        }
      }
    }
  }

  private static double dB2(final double x) {
    return x * 2;
  }

  private Emu2413() {
  }

  private static void makeTllTable() {
    for (int fnum = 0; fnum < 16; fnum++) {
      for (int block = 0; block < 8; block++) {
        for (int TL = 0; TL < 64; TL++) {
          for (int KL = 0; KL < 4; KL++) {
            if (KL == 0) {
              tllTable[fnum][block][TL][KL] = TL2EG(TL);
            } else {
              int tmp = (int) (kltable[fnum] - dB2(3.000) * (7 - block));
              if (tmp <= 0) {
                tllTable[fnum][block][TL][KL] = TL2EG(TL);
              } else {
                tllTable[fnum][block][TL][KL] = (int) ((tmp >> (3 - KL))
                        / EG_STEP) + TL2EG(TL);
              }
            }
          }
        }
      }
    }
  }

  // Rate Table for Attack
  private static void makeDphaseARTable() {
    for (int AR = 0; AR < 16; AR++) {
      for (int Rks = 0; Rks < 16; Rks++) {
        int RM = AR + (Rks >> 2);
        int RL = Rks & 3;
        if (RM > 15) {
          RM = 15;
        }
        switch (AR) {
          case 0:
            dphaseARTable[AR][Rks] = 0;
            break;
          case 15:
            dphaseARTable[AR][Rks] = 0; // EG_DP_WIDTH;
            break;
          default:
            dphaseARTable[AR][Rks] = 3 * (RL + 4) << (RM + 1);
            break;
        }
      }
    }
  }

  // Rate Table for Decay and Release
  private static void makeDphaseDRTable() {
    for (int DR = 0; DR < 16; DR++) {
      for (int Rks = 0; Rks < 16; Rks++) {
        int RM = DR + (Rks >> 2);
        int RL = Rks & 3;
        if (RM > 15) {
          RM = 15;
        }
        switch (DR) {
          case 0:
            dphaseDRTable[DR][Rks] = 0;
            break;
          default:
            dphaseDRTable[DR][Rks] = (RL + 4) << (RM - 1);
            break;
        }
      }
    }
  }

  private static void makeRksTable() {
    for (int fnum8 = 0; fnum8 < 2; fnum8++) {
      for (int block = 0; block < 8; block++) {
        for (int KR = 0; KR < 2; KR++) {
          if (KR != 0) {
            rksTable[fnum8][block][KR] = (block << 1) + fnum8;
          } else {
            rksTable[fnum8][block][KR] = block >> 1;
          }
        }
      }
    }
  }

  private static void OPLL_dump2patch(final short[] dump,
                                      final OPLL_PATCH[] patch) {
    patch[0].AM = (dump[0] >> 7) & 1;
    patch[1].AM = (dump[1] >> 7) & 1;
    patch[0].PM = (dump[0] >> 6) & 1;
    patch[1].PM = (dump[1] >> 6) & 1;
    patch[0].EG = (dump[0] >> 5) & 1;
    patch[1].EG = (dump[1] >> 5) & 1;
    patch[0].KR = (dump[0] >> 4) & 1;
    patch[1].KR = (dump[1] >> 4) & 1;
    patch[0].ML = (dump[0]) & 15;
    patch[1].ML = (dump[1]) & 15;
    patch[0].KL = (dump[2] >> 6) & 3;
    patch[1].KL = (dump[3] >> 6) & 3;
    patch[0].TL = (dump[2]) & 63;
    patch[0].FB = (dump[3]) & 7;
    patch[0].WF = (dump[3] >> 3) & 1;
    patch[1].WF = (dump[3] >> 4) & 1;
    patch[0].AR = (dump[4] >> 4) & 15;
    patch[1].AR = (dump[5] >> 4) & 15;
    patch[0].DR = (dump[4]) & 15;
    patch[1].DR = (dump[5]) & 15;
    patch[0].SL = (dump[6] >> 4) & 15;
    patch[1].SL = (dump[7] >> 4) & 15;
    patch[0].RR = (dump[6]) & 15;
    patch[1].RR = (dump[7]) & 15;
  }

  private static int EG2DB(final int d) {
    return d * (int) (EG_STEP / DB_STEP);
  }

  private static void makeDefaultPatch() {
    for (int i = 0; i < PATCHES; i++) {
      OPLL_getDefaultPatch(i, new OPLL_PATCH[]{
              default_patch[i * 2] = new OPLL_PATCH(),
              default_patch[i * 2 + 1] = new OPLL_PATCH(),
      });
    }
  }

  /************************************************************

   Calc Parameters
   ************************************************************/

  private static int calc_eg_dphase(final OPLL_SLOT slot) {

    switch (slot.eg_mode) {
      case ATTACK:
        return dphaseARTable[slot.patch.AR][slot.rks];

      case DECAY:
        return dphaseDRTable[slot.patch.DR][slot.rks];

      case SUSHOLD:
        return 0;

      case SUSTINE:
        return dphaseDRTable[slot.patch.RR][slot.rks];

      case RELEASE:
        if (slot.sustine) {
          return dphaseDRTable[5][slot.rks];
        } else if (slot.patch.EG != 0) {
          return dphaseDRTable[slot.patch.RR][slot.rks];
        } else {
          return dphaseDRTable[7][slot.rks];
        }

      case SETTLE:
        return dphaseDRTable[15][0];

      case FINISH:
        return 0;

      default:
        return 0;
    }
  }

  private static int TL2EG(final int d) {
    return d * (int) (TL_STEP / EG_STEP);
  }

  private static int SL2EG(final int d) {
    return d * (int) (SL_STEP / EG_STEP);
  }

  private static int DB_POS(final double x) {
    return (int) (x / DB_STEP);
  }

  private static int DB_NEG(final double x) {
    return (int) (DB_MUTE + DB_MUTE + x / DB_STEP);
  }

  // Cut the lower b bit(s) off.
  private static int HIGHBITS(final int c, final int b) {
    return c >> b;
  }

  // Expand x which is s bits to d bits.
  private static int EXPAND_BITS(final int x, final int s, final int d) {
    return x << (d - s);
  }

  private static void UPDATE_PG(final OPLL_SLOT S) {
    S.dphase = dphaseTable[S.fnum][S.block][S.patch.ML];
  }

  private static void UPDATE_TLL(final OPLL_SLOT S) {
    if (S.type) {
      S.tll = tllTable[S.fnum >> 5][S.block][S.volume][S.patch.KL];
    } else {
      S.tll = tllTable[S.fnum >> 5][S.block][S.patch.TL][S.patch.KL];
    }
  }

  private static void UPDATE_RKS(final OPLL_SLOT S) {
    S.rks = rksTable[S.fnum >> 8][S.block][S.patch.KR];
  }

  private static void UPDATE_WF(final OPLL_SLOT S) {
    S.sintbl = waveform[S.patch.WF];
  }

  private static void UPDATE_EG(final OPLL_SLOT S) {
    S.eg_dphase = calc_eg_dphase(S);
  }

  private static void UPDATE_ALL(final OPLL_SLOT S) {
    UPDATE_PG(S);
    UPDATE_TLL(S);
    UPDATE_RKS(S);
    UPDATE_WF(S);
    UPDATE_EG(S);  // EG should be updated last.
  }

  // Slot key on
  private static void slotOn(final OPLL_SLOT slot) {
    slot.eg_mode = ATTACK;
    slot.eg_phase = 0;
    slot.phase = 0;
    UPDATE_EG(slot);
  }

  // Slot key on without reseting the phase
  private static void slotOn2(final OPLL_SLOT slot) {
    slot.eg_mode = ATTACK;
    slot.eg_phase = 0;
    UPDATE_EG(slot);
  }

  // Slot key off
  private static void slotOff(final OPLL_SLOT slot) {
    if (slot.eg_mode == ATTACK) {
      slot.eg_phase = EXPAND_BITS(AR_ADJUST_TABLE[HIGHBITS(slot.eg_phase,
              EG_DP_BITS - EG_BITS)], EG_BITS, EG_DP_BITS);
    }
    slot.eg_mode = RELEASE;
    UPDATE_EG(slot);
  }

  // Channel key on
  private static void keyOn(final OPLL opll, final int i) {
    if (!opll.slot_on_flag[i * 2]) {
      slotOn(MOD(opll, i));
    }
    if (!opll.slot_on_flag[i * 2 + 1]) {
      slotOn(CAR(opll, i));
    }
    opll.key_status[i] = true;
  }

  // Channel key off
  private static void keyOff(final OPLL opll, final int i) {
    if (opll.slot_on_flag[i * 2 + 1]) {
      slotOff(CAR(opll, i));
    }
    opll.key_status[i] = false;
  }

  private static void keyOn_BD(final OPLL opll) {
    keyOn(opll, 6);
  }

  private static void keyOn_SD(final OPLL opll) {
    if (!opll.slot_on_flag[SLOT_SD]) {
      slotOn(CAR(opll, 7));
    }
  }

  private static void keyOn_TOM(final OPLL opll) {
    if (!opll.slot_on_flag[SLOT_TOM]) {
      slotOn(MOD(opll, 8));
    }
  }

  private static void keyOn_HH(final OPLL opll) {
    if (!opll.slot_on_flag[SLOT_HH]) {
      slotOn2(MOD(opll, 7));
    }
  }

  private static void keyOn_CYM(final OPLL opll) {
    if (!opll.slot_on_flag[SLOT_CYM]) {
      slotOn2(CAR(opll, 8));
    }
  }

  // Drum key off
  private static void keyOff_BD(final OPLL opll) {
    keyOff(opll, 6);
  }

  private static void keyOff_SD(final OPLL opll) {
    if (opll.slot_on_flag[SLOT_SD]) {
      slotOff(CAR(opll, 7));
    }
  }

  private static void keyOff_TOM(final OPLL opll) {
    if (opll.slot_on_flag[SLOT_TOM]) {
      slotOff(MOD(opll, 8));
    }
  }

  private static void keyOff_HH(final OPLL opll) {
    if (opll.slot_on_flag[SLOT_HH]) {
      slotOff(MOD(opll, 7));
    }
  }

  private static void keyOff_CYM(final OPLL opll) {
    if (opll.slot_on_flag[SLOT_CYM]) {
      slotOff(CAR(opll, 8));
    }
  }

  // Change a voice
  private static void setPatch(final OPLL opll, final int i,
                               final int num) {
    opll.patch_number[i] = num;
    MOD(opll, i).patch = opll.patch[num * 2 + 0];
    CAR(opll, i).patch = opll.patch[num * 2 + 1];
  }

  // Change a rhythm voice
  private static void setSlotPatch(final OPLL_SLOT slot,
                                   final OPLL_PATCH patch) {
    slot.patch = patch;
  }

  // Set sustine parameter
  private static void setSustine(final OPLL opll, final int c,
                                 final boolean sustine) {
    CAR(opll, c).sustine = sustine;
    if (MOD(opll, c).type) {
      MOD(opll, c).sustine = sustine;
    }
  }

  // Volume : 6bit ( Volume register << 2 )
  private static void setVolume(final OPLL opll, final int c,
                                final int volume) {
    CAR(opll, c).volume = volume;
  }

  private static void setSlotVolume(final OPLL_SLOT slot,
                                    final int volume) {
    slot.volume = volume;
  }

  // Set F-Number ( fnum : 9bit )
  private static void setFnumber(final OPLL opll, final int c,
                                 final int fnum) {
    CAR(opll, c).fnum = fnum;
    MOD(opll, c).fnum = fnum;
  }

  // Set Block data (block : 3bit )
  private static void setBlock(final OPLL opll, final int c,
                               final int block) {
    CAR(opll, c).block = block;
    MOD(opll, c).block = block;
  }

  // Change Rhythm Mode
  private static void update_rhythm_mode(final OPLL opll) {
    if ((opll.patch_number[6] & 0x10) != 0) {
      if (!opll.slot_on_flag[SLOT_BD2] && (opll.reg[0x0e] & 32) == 0) {
        opll.slot[SLOT_BD1].eg_mode = FINISH;
        opll.slot[SLOT_BD2].eg_mode = FINISH;
        setPatch(opll, 6, opll.reg[0x36] >> 4);
      }
    } else if ((opll.reg[0x0e] & 32) != 0) {
      opll.patch_number[6] = 16;
      opll.slot[SLOT_BD1].eg_mode = FINISH;
      opll.slot[SLOT_BD2].eg_mode = FINISH;
      setSlotPatch(opll.slot[SLOT_BD1], opll.patch[16 * 2 + 0]);
      setSlotPatch(opll.slot[SLOT_BD2], opll.patch[16 * 2 + 1]);
    }

    if ((opll.patch_number[7] & 0x10) != 0) {
      if (!(opll.slot_on_flag[SLOT_HH] && opll.slot_on_flag[SLOT_SD])
              && ((opll.reg[0x0e] & 32) == 0)) {
        opll.slot[SLOT_HH].type = false;
        opll.slot[SLOT_HH].eg_mode = FINISH;
        opll.slot[SLOT_SD].eg_mode = FINISH;
        setPatch(opll, 7, opll.reg[0x37] >> 4);
      }
    } else if ((opll.reg[0x0e] & 32) != 0) {
      opll.patch_number[7] = 17;
      opll.slot[SLOT_HH].type = true;
      opll.slot[SLOT_HH].eg_mode = FINISH;
      opll.slot[SLOT_SD].eg_mode = FINISH;
      setSlotPatch(opll.slot[SLOT_HH], opll.patch[17 * 2 + 0]);
      setSlotPatch(opll.slot[SLOT_SD], opll.patch[17 * 2 + 1]);
    }

    if ((opll.patch_number[8] & 0x10) != 0) {
      if (!(opll.slot_on_flag[SLOT_CYM] && opll.slot_on_flag[SLOT_TOM])
              && ((opll.reg[0x0e] & 32) == 0)) {
        opll.slot[SLOT_TOM].type = false;
        opll.slot[SLOT_TOM].eg_mode = FINISH;
        opll.slot[SLOT_CYM].eg_mode = FINISH;
        setPatch(opll, 8, opll.reg[0x38] >> 4);
      }
    } else if ((opll.reg[0x0e] & 32) != 0) {
      opll.patch_number[8] = 18;
      opll.slot[SLOT_TOM].type = true;
      opll.slot[SLOT_TOM].eg_mode = FINISH;
      opll.slot[SLOT_CYM].eg_mode = FINISH;
      setSlotPatch(opll.slot[SLOT_TOM], opll.patch[18 * 2 + 0]);
      setSlotPatch(opll.slot[SLOT_CYM], opll.patch[18 * 2 + 1]);
    }
  }

  private static void update_key_status(final OPLL opll) {

    for (int ch = 0; ch < 9; ch++) {
      opll.slot_on_flag[ch * 2] = opll.slot_on_flag[ch * 2 + 1]
              = ((opll.reg[0x20 + ch]) & 0x10) != 0;
    }

    if ((opll.reg[0x0e] & 32) != 0) {
      opll.slot_on_flag[SLOT_BD1] |= (opll.reg[0x0e] & 0x10) != 0;
      opll.slot_on_flag[SLOT_BD2] |= (opll.reg[0x0e] & 0x10) != 0;
      opll.slot_on_flag[SLOT_SD] |= (opll.reg[0x0e] & 0x08) != 0;
      opll.slot_on_flag[SLOT_HH] |= (opll.reg[0x0e] & 0x01) != 0;
      opll.slot_on_flag[SLOT_TOM] |= (opll.reg[0x0e] & 0x04) != 0;
      opll.slot_on_flag[SLOT_CYM] |= (opll.reg[0x0e] & 0x02) != 0;
    }
  }

  private static void OPLL_copyPatch(final OPLL opll, final int num,
                                     final OPLL_PATCH patch) {
    OPLL_PATCH.copy(patch, opll.patch[num]);
  }

  /***********************************************************

   Initializing
   ***********************************************************/

  private static void OPLL_SLOT_reset(final OPLL_SLOT slot,
                                      final boolean type) {
    slot.type = type;
    slot.sintbl = waveform[0];
    slot.phase = 0;
    slot.dphase = 0;
    slot.output[0] = 0;
    slot.output[1] = 0;
    slot.feedback = 0;
    slot.eg_mode = FINISH;
    slot.eg_phase = EG_DP_WIDTH;
    slot.eg_dphase = 0;
    slot.rks = 0;
    slot.tll = 0;
    slot.sustine = false;
    slot.fnum = 0;
    slot.block = 0;
    slot.volume = 0;
    slot.pgout = 0;
    slot.egout = 0;
    slot.patch = new OPLL_PATCH();
  }

  private static void internal_refresh() {
    makeDphaseTable();
    makeDphaseARTable();
    makeDphaseDRTable();
  }

  private static OPLL_SLOT MOD(final OPLL o, final int x) {
    return o.slot[x << 1];
  }

  public static void OPLL_init() {
    makePmTable();
    makeAmTable();
    makeDB2LinTable();
    makeAdjustTable();
    makeTllTable();
    makeRksTable();
    makeSinTable();
    makeDefaultPatch();
    internal_refresh();
  }

  public static OPLL OPLL_new() {

      OPLL opll = new OPLL();

      for (int i = 0; i < 19 * 2; i++) {
          opll.patch[i] = new OPLL_PATCH();
      }

      OPLL_reset(opll);
      OPLL_reset_patch(opll);

    return opll;
  }

    // Reset patch datas by system default.
    public static void OPLL_reset_patch(final OPLL opll) {
        for (int i = 0; i < 19 * 2; i++) {
            OPLL_copyPatch(opll, i, default_patch[i]);
        }
    }

  private static OPLL_SLOT CAR(final OPLL o, final int x) {
    return o.slot[(x << 1) | 1];
  }

  /*********************************************************

   Generate wave data
   *********************************************************/

  // Convert Amp(0 to EG_HEIGHT) to Phase(0 to 4PI).
  private static int wave2_4pi(final int e) {
    return e << (1 + PG_BITS - SLOT_AMP_BITS);
  }

  // Convert Amp(0 to EG_HEIGHT) to Phase(0 to 8PI).
  private static int wave2_8pi(final int e) {
    return e << (2 + PG_BITS - SLOT_AMP_BITS);
  }

  // Update AM, PM unit
  private static void update_ampm(final OPLL opll) {
    opll.pm_phase = (opll.pm_phase + pm_dphase) & (PM_DP_WIDTH - 1);
    opll.am_phase = (opll.am_phase + am_dphase) & (AM_DP_WIDTH - 1);
    opll.lfo_am = amtable[HIGHBITS(opll.am_phase, AM_DP_BITS - AM_PG_BITS)];
    opll.lfo_pm = pmtable[HIGHBITS(opll.pm_phase, PM_DP_BITS - PM_PG_BITS)];
  }

  // PG
  private static void calc_phase(final OPLL_SLOT slot, final int lfo) {
    if (slot.patch.PM != 0) {
      slot.phase += (slot.dphase * lfo) >> PM_AMP_BITS;
    } else {
      slot.phase += slot.dphase;
    }

    slot.phase &= DP_WIDTH - 1;

    slot.pgout = HIGHBITS(slot.phase, DP_BASE_BITS);
  }

  // Update Noise unit
  private static void update_noise(final OPLL opll) {
    if ((opll.noise_seed & 1) != 0) {
      opll.noise_seed ^= 0x8003020;
    }
    opll.noise_seed >>= 1;
  }

  private static int S2E(final double x) {
    return SL2EG((int) (x / SL_STEP)) << (EG_DP_BITS - EG_BITS);
  }

  private static boolean BIT(final int s, final int b) {
    return ((s >> b) & 1) != 0;
  }

  // EG
  private static void calc_envelope(final OPLL_SLOT slot, final int lfo) {

    int egout;

    switch (slot.eg_mode) {
      case ATTACK:
        egout = AR_ADJUST_TABLE[HIGHBITS(slot.eg_phase, EG_DP_BITS - EG_BITS)];
        slot.eg_phase += slot.eg_dphase;
        if ((EG_DP_WIDTH & slot.eg_phase) != 0 || slot.patch.AR == 15) {
          egout = 0;
          slot.eg_phase = 0;
          slot.eg_mode = DECAY;
          UPDATE_EG(slot);
        }
        break;

      case DECAY:
        egout = HIGHBITS(slot.eg_phase, EG_DP_BITS - EG_BITS);
        slot.eg_phase += slot.eg_dphase;
        if (slot.eg_phase >= SL[slot.patch.SL]) {
          if (slot.patch.EG != 0) {
            slot.eg_phase = SL[slot.patch.SL];
            slot.eg_mode = SUSHOLD;
            UPDATE_EG(slot);
          } else {
            slot.eg_phase = SL[slot.patch.SL];
            slot.eg_mode = SUSTINE;
            UPDATE_EG(slot);
          }
        }
        break;

      case SUSHOLD:
        egout = HIGHBITS(slot.eg_phase, EG_DP_BITS - EG_BITS);
        if (slot.patch.EG == 0) {
          slot.eg_mode = SUSTINE;
          UPDATE_EG(slot);
        }
        break;

      case SUSTINE:
      case RELEASE:
        egout = HIGHBITS(slot.eg_phase, EG_DP_BITS - EG_BITS);
        slot.eg_phase += slot.eg_dphase;
        if (egout >= (1 << EG_BITS)) {
          slot.eg_mode = FINISH;
          egout = (1 << EG_BITS) - 1;
        }
        break;

      case SETTLE:
        egout = HIGHBITS(slot.eg_phase, EG_DP_BITS - EG_BITS);
        slot.eg_phase += slot.eg_dphase;
        if (egout >= (1 << EG_BITS)) {
          slot.eg_mode = ATTACK;
          egout = (1 << EG_BITS) - 1;
          UPDATE_EG(slot);
        }
        break;

      case FINISH:
        egout = (1 << EG_BITS) - 1;
        break;

      default:
        egout = (1 << EG_BITS) - 1;
        break;
    }

    if (slot.patch.AM != 0) {
      egout = EG2DB(egout + slot.tll) + lfo;
    } else {
      egout = EG2DB(egout + slot.tll);
    }

    if (egout >= DB_MUTE) {
      egout = DB_MUTE - 1;
    }

    slot.egout = egout | 3;
  }

  // CARRIOR
  private static int calc_slot_car(final OPLL_SLOT slot, final int fm) {
    if (slot.egout >= (DB_MUTE - 1)) {
      slot.output[0] = 0;
    } else {
      slot.output[0] = DB2LIN_TABLE[slot.sintbl[(slot.pgout + wave2_8pi(fm))
              & (PG_WIDTH - 1)] + slot.egout];
    }

    slot.output[1] = (slot.output[1] + slot.output[0]) >> 1;
    return slot.output[1];
  }

  // MODULATOR
  private static int calc_slot_mod(final OPLL_SLOT slot) {

    slot.output[1] = slot.output[0];

    if (slot.egout >= (DB_MUTE - 1)) {
      slot.output[0] = 0;
    } else if (slot.patch.FB != 0) {
      final int fm = wave2_4pi(slot.feedback) >> (7 - slot.patch.FB);
      slot.output[0] = DB2LIN_TABLE[slot.sintbl[(slot.pgout + fm)
              & (PG_WIDTH - 1)] + slot.egout];
    } else {
      slot.output[0] = DB2LIN_TABLE[slot.sintbl[slot.pgout] + slot.egout];
    }

    slot.feedback = (slot.output[1] + slot.output[0]) >> 1;

    return slot.feedback;
  }

  // TOM
  private static int calc_slot_tom(final OPLL_SLOT slot) {
    if (slot.egout >= (DB_MUTE - 1)) {
      return 0;
    }

    return DB2LIN_TABLE[slot.sintbl[slot.pgout] + slot.egout];
  }

  // SNARE
  private static int calc_slot_snare(final OPLL_SLOT slot,
                                     final boolean noise) {

    if (slot.egout >= DB_MUTE - 1) {
      return 0;
    }

    if (BIT(slot.pgout, 7)) {
      return DB2LIN_TABLE[(noise ? DB_POS(0.0) : DB_POS(15.0)) + slot.egout];
    } else {
      return DB2LIN_TABLE[(noise ? DB_NEG(0.0) : DB_NEG(15.0)) + slot.egout];
    }
  }

  // TOP-CYM
  private static int calc_slot_cym(final OPLL_SLOT slot, final int pgout_hh) {
    final int dbout;

    if (slot.egout >= (DB_MUTE - 1)) {
      return 0;
    } else if (((BIT(pgout_hh, PG_BITS - 8) ^ BIT(pgout_hh, PG_BITS - 1))
            | BIT(pgout_hh, PG_BITS - 7)) ^ (BIT(slot.pgout, PG_BITS - 7)
            & !BIT(slot.pgout, PG_BITS - 5))) {
      dbout = DB_NEG(3.0);
    } else {
      dbout = DB_POS(3.0);
    }

    return DB2LIN_TABLE[dbout + slot.egout];
  }

  // HI-HAT
  private static int calc_slot_hat(final OPLL_SLOT slot, final int pgout_cym,
                                   final boolean noise) {

    final int dbout;

    if (slot.egout >= (DB_MUTE - 1)) {
      return 0;
    } else if (((BIT(slot.pgout, PG_BITS - 8) ^ BIT(slot.pgout, PG_BITS - 1))
            | BIT(slot.pgout, PG_BITS - 7)) ^ (BIT(pgout_cym, PG_BITS - 7)
            & !BIT(pgout_cym, PG_BITS - 5))) {
      if (noise) {
        dbout = DB_NEG(12.0);
      } else {
        dbout = DB_NEG(24.0);
      }
    } else {
      if (noise) {
        dbout = DB_POS(12.0);
      } else {
        dbout = DB_POS(24.0);
      }
    }

    return DB2LIN_TABLE[dbout + slot.egout];
  }

  private static void OPLL_getDefaultPatch(int num,
                                           OPLL_PATCH[] patch) {
    short[] r = new short[8];
    System.arraycopy(ym2413_inst, num * 8, r, 0, r.length);
    OPLL_dump2patch(r, patch);
  }

  public static void OPLL_init(int clock, int rate) {
    fmRateHz = rate;
    if (fmClockHz != clock) {
      fmClockHz = clock;
      pm_dphase
              = (int) (PM_SPEED * PM_DP_WIDTH / (fmClockHz / 72));
      am_dphase
              = (int) (AM_SPEED * AM_DP_WIDTH / (fmClockHz / 72));
    }
    OPLL_init();
  }

    // Reset whole of OPLL except patch datas.
    public static void OPLL_reset(final OPLL opll) {

        if (opll == null) {
            return;
        }

        opll.adr = 0;
        opll.out = 0;

        opll.pm_phase = 0;
        opll.am_phase = 0;

    opll.noise_seed = 0xffff;

    for (int i = 0; i < 18; i++) {
      OPLL_SLOT_reset(opll.slot[i], (i & 1) != 0);
    }

    for (int i = 0; i < CHANNELS; i++) {
      opll.key_status[i] = false;
      setPatch(opll, i, 0);
    }

    for (int i = 0x10; i < 0x40; i++) {
      OPLL_writeReg(opll, i, 0);
    }

    opll.realstep = (int) ((1L << 31L) / fmRateHz);
    opll.opllstep = (int) ((1L << 31L) / (fmClockHz / 72));
    opll.oplltime = 0;
    for (int i = 0; i < 14; i++) {
      opll.pan[i] = 2;
    }
  }

  private static void update_output(final OPLL opll) {

    update_ampm(opll);
    update_noise(opll);

    for (int i = 0; i < 18; i++) {
      calc_phase(opll.slot[i], opll.lfo_pm);
      calc_envelope(opll.slot[i], opll.lfo_am);
    }

    //CH1-6
    for (int i = 0; i < 6; i++) {
      if (CAR(opll, i).eg_mode != FINISH) {
        opll.ch_out[i] += calc_slot_car(CAR(opll, i), calc_slot_mod(MOD(opll, i)));
      }
    }

    // CH7
    if (opll.patch_number[6] <= 15) {
      if (CAR(opll, 6).eg_mode != FINISH) {
        opll.ch_out[6] += calc_slot_car(CAR(opll, 6), calc_slot_mod(MOD(opll, 6)));
      }
    } else {
      if (CAR(opll, 6).eg_mode != FINISH) {
        opll.ch_out[9] += calc_slot_car(CAR(opll, 6), calc_slot_mod(MOD(opll, 6)));
      }
    }

    // CH8
    if (opll.patch_number[7] <= 15) {
      if (CAR(opll, 7).eg_mode != FINISH)
        opll.ch_out[7] += calc_slot_car(CAR(opll, 7), calc_slot_mod(MOD(opll, 7)));
    } else {
      if (MOD(opll, 7).eg_mode != FINISH) {
        opll.ch_out[10] += calc_slot_hat(MOD(opll, 7), CAR(opll, 8).pgout,
                (opll.noise_seed & 1) != 0);
      }
      if (CAR(opll, 7).eg_mode != FINISH) {
        opll.ch_out[11] -= calc_slot_snare(CAR(opll, 7), (opll.noise_seed & 1) != 0);
      }
    }

    // CH9
    if (opll.patch_number[8] <= 15) {
      if (CAR(opll, 8).eg_mode != FINISH) {
        opll.ch_out[8] += calc_slot_car(CAR(opll, 8), calc_slot_mod(MOD(opll, 8)));
      }
    } else {
      if (MOD(opll, 8).eg_mode != FINISH) {
        opll.ch_out[12] += calc_slot_tom(MOD(opll, 8));
      }

      if (CAR(opll, 8).eg_mode != FINISH) {
        opll.ch_out[13] -= calc_slot_cym(CAR(opll, 8), MOD(opll, 7).pgout);
      }
    }

    /* Always calc average of two samples */
    for (int i = 0; i < 14; i++) {
      opll.ch_out[i] >>= 1;
    }
  }

  static int mix_output(OPLL opll) {
    int i;
    opll.out = opll.ch_out[0];
    for (i = 1; i < 14; i++) {
      opll.out += opll.ch_out[i];
    }
    return opll.out;
  }

  /****************************************************

   I/O Ctrl
   *****************************************************/

  private static void OPLL_writeReg(final OPLL opll, int reg, int data) {

    data = data & 0xff;
    reg = reg & 0x3f;
    opll.reg[reg] = data;

    switch (reg) {
      case 0x00:
        opll.patch[0].AM = (data >> 7) & 1;
        opll.patch[0].PM = (data >> 6) & 1;
        opll.patch[0].EG = (data >> 5) & 1;
        opll.patch[0].KR = (data >> 4) & 1;
        opll.patch[0].ML = data & 15;
        for (int i = 0; i < CHANNELS; i++) {
          if (opll.patch_number[i] == 0) {
            UPDATE_PG(MOD(opll, i));
            UPDATE_RKS(MOD(opll, i));
            UPDATE_EG(MOD(opll, i));
          }
        }
        break;

      case 0x01:
        opll.patch[1].AM = (data >> 7) & 1;
        opll.patch[1].PM = (data >> 6) & 1;
        opll.patch[1].EG = (data >> 5) & 1;
        opll.patch[1].KR = (data >> 4) & 1;
        opll.patch[1].ML = data & 15;
        for (int i = 0; i < CHANNELS; i++) {
          if (opll.patch_number[i] == 0) {
            UPDATE_PG(CAR(opll, i));
            UPDATE_RKS(CAR(opll, i));
            UPDATE_EG(CAR(opll, i));
          }
        }
        break;

      case 0x02:
        opll.patch[0].KL = (data >> 6) & 3;
        opll.patch[0].TL = data & 63;
        for (int i = 0; i < CHANNELS; i++) {
          if (opll.patch_number[i] == 0) {
            UPDATE_TLL(MOD(opll, i));
          }
        }
        break;

      case 0x03:
        opll.patch[1].KL = (data >> 6) & 3;
        opll.patch[1].WF = (data >> 4) & 1;
        opll.patch[0].WF = (data >> 3) & 1;
        opll.patch[0].FB = data & 7;
        for (int i = 0; i < CHANNELS; i++) {
          if (opll.patch_number[i] == 0) {
            UPDATE_WF(MOD(opll, i));
            UPDATE_WF(CAR(opll, i));
          }
        }
        break;

      case 0x04:
        opll.patch[0].AR = (data >> 4) & 15;
        opll.patch[0].DR = data & 15;
        for (int i = 0; i < CHANNELS; i++) {
          if (opll.patch_number[i] == 0) {
            UPDATE_EG(MOD(opll, i));
          }
        }
        break;

      case 0x05:
        opll.patch[1].AR = (data >> 4) & 15;
        opll.patch[1].DR = data & 15;
        for (int i = 0; i < 9; i++) {
          if (opll.patch_number[i] == 0) {
            UPDATE_EG(CAR(opll, i));
          }
        }
        break;

      case 0x06:
        opll.patch[0].SL = (data >> 4) & 15;
        opll.patch[0].RR = data & 15;
        for (int i = 0; i < 9; i++) {
          if (opll.patch_number[i] == 0) {
            UPDATE_EG(MOD(opll, i));
          }
        }
        break;

      case 0x07:
        opll.patch[1].SL = (data >> 4) & 15;
        opll.patch[1].RR = data & 15;
        for (int i = 0; i < 9; i++) {
          if (opll.patch_number[i] == 0) {
            UPDATE_EG(CAR(opll, i));
          }
        }
        break;

      case 0x0e:
        update_rhythm_mode(opll);
        if ((data & 32) != 0) {
          if ((data & 0x10) != 0) {
            keyOn_BD(opll);
          } else {
            keyOff_BD(opll);
          }
          if ((data & 0x8) != 0) {
            keyOn_SD(opll);
          } else {
            keyOff_SD(opll);
          }
          if ((data & 0x4) != 0) {
            keyOn_TOM(opll);
          } else {
            keyOff_TOM(opll);
          }
          if ((data & 0x2) != 0) {
            keyOn_CYM(opll);
          } else {
            keyOff_CYM(opll);
          }
          if ((data & 0x1) != 0) {
            keyOn_HH(opll);
          } else {
            keyOff_HH(opll);
          }
        }
        update_key_status(opll);

        UPDATE_ALL(MOD(opll, 6));
        UPDATE_ALL(CAR(opll, 6));
        UPDATE_ALL(MOD(opll, 7));
        UPDATE_ALL(CAR(opll, 7));
        UPDATE_ALL(MOD(opll, 8));
        UPDATE_ALL(CAR(opll, 8));

        break;

      case 0x0f:
        break;

      case 0x10:
      case 0x11:
      case 0x12:
      case 0x13:
      case 0x14:
      case 0x15:
      case 0x16:
      case 0x17:
      case 0x18: {
        int ch = reg - 0x10;
        setFnumber(opll, ch, data + ((opll.reg[0x20 + ch] & 1) << 8));
        UPDATE_ALL(MOD(opll, ch));
        UPDATE_ALL(CAR(opll, ch));
        break;
      }

      case 0x20:
      case 0x21:
      case 0x22:
      case 0x23:
      case 0x24:
      case 0x25:
      case 0x26:
      case 0x27:
      case 0x28: {
        int ch = reg - 0x20;
        setFnumber(opll, ch, ((data & 1) << 8) + opll.reg[0x10 + ch]);
        setBlock(opll, ch, (data >> 1) & 7);
        setSustine(opll, ch, ((data >> 5) & 1) != 0);
        if ((data & 0x10) != 0) {
          keyOn(opll, ch);
        } else {
          keyOff(opll, ch);
        }
        UPDATE_ALL(MOD(opll, ch));
        UPDATE_ALL(CAR(opll, ch));
        update_key_status(opll);
        update_rhythm_mode(opll);
        break;
      }

      case 0x30:
      case 0x31:
      case 0x32:
      case 0x33:
      case 0x34:
      case 0x35:
      case 0x36:
      case 0x37:
      case 0x38: {
        int i = (data >> 4) & 15;
        int v = data & 15;
        if ((opll.reg[0x0e] & 32) != 0 && reg >= 0x36) {
          switch (reg) {
            case 0x37:
              setSlotVolume(MOD(opll, 7), i << 2);
              break;
            case 0x38:
              setSlotVolume(MOD(opll, 8), i << 2);
              break;
          }
        } else {
            setPatch(opll, reg - 0x30, i);
        }
          setVolume(opll, reg - 0x30, v << 2);
          UPDATE_ALL(MOD(opll, reg - 0x30));
          UPDATE_ALL(CAR(opll, reg - 0x30));
          break;
      }
    }
  }

    public static void OPLL_writeIO(final OPLL opll, final int adr,
                                    final int val) {
        if ((adr & 1) != 0) {
            OPLL_writeReg(opll, opll.adr, val);
        } else {
            opll.adr = val;
        }
    }

    public static int OPLL_calc(final OPLL opll) {

        while (opll.realstep > opll.oplltime) {
      opll.oplltime += opll.opllstep;
      update_output(opll);
    }

    opll.oplltime -= opll.realstep;
    return mix_output(opll);
  }
}
