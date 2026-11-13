package omegadrive.sound.fm.ym2612.ymfm;

import java.util.Arrays;

import static omegadrive.sound.fm.ym2612.ymfm.Ymfm.*;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class OpnaRegisters {

    public static final int OUTPUTS = 2;
    public static final int CHANNELS = 6;
    public static final int ALL_CHANNELS = (1 << CHANNELS) - 1; //0x3f
    public static final int OPERATORS = CHANNELS * 4; //24
    public static final int WAVEFORMS = 1;
    public static final int REGISTERS = 0x200;
    public static final int REG_MODE = 0x27;
    public static final int DEFAULT_PRESCALE = 6;
    public static final int EG_CLOCK_DIVIDER = 3;

    public static final boolean EG_HAS_SSG = true;
    public static final boolean MODULATOR_DELAY = false;

    public static final int CSM_TRIGGER_MASK = 1 << 2; //4
    public static final int STATUS_TIMERA = 0x01;
    public static final int STATUS_TIMERB = 0x02;
    public static final int STATUS_BUSY = 0x80;
    public static final int STATUS_IRQ = 0;

    public static final int RHYTHM_CHANNEL = 0xff;
    public static final int WAVEFORM_LENGTH = 0x400;
    public static final boolean DYNAMIC_OPS = false;
    public static final boolean EG_HAS_DEPRESS = false;
    public static final boolean EG_HAS_REVERB = false;

    private int m_lfoCounter;
    private int m_lfoAm;
    private final int[] m_regdata = new int[REGISTERS];
    //single waveform for OPNA
    private final int[] m_waveform = new int[WAVEFORM_LENGTH];


    public static final class OperatorMapping {
        public final int[] chan = new int[CHANNELS];
    }

    private static int operatorList(int o1, int o2, int o3, int o4) {
        return (o1 & 0xff) | ((o2 & 0xff) << 8) | ((o3 & 0xff) << 16) | ((o4 & 0xff) << 24);
    }

    private static int effectiveRate(int rawrate, int ksr) {
        return (rawrate == 0) ? 0 : Math.min(rawrate + ksr, 63);
    }

    public OpnaRegisters() {
        m_lfoCounter = 0;
        m_lfoAm = 0;

        for (int index = 0; index < WAVEFORM_LENGTH; index++) {
            m_waveform[index] = (Tables.absSinAttenuation(index)) | (bitfield(index, 9) << 15) & 0xffff;
        }
    }

    public void reset() {
        Arrays.fill(m_regdata, 0);
        //enable output on both channels by default (IsOpnA true)
        m_regdata[0xb4] = m_regdata[0xb5] = m_regdata[0xb6] = 0xc0;
        m_regdata[0x1b4] = m_regdata[0x1b5] = m_regdata[0x1b6] = 0xc0;
    }

    public void saveRestore(YmfmSavedState state) {
        m_lfoCounter = state.saveRestore32(m_lfoCounter);
        m_lfoAm = state.saveRestore8(m_lfoAm);
        state.saveRestore8(m_regdata);
    }

    public static int channelOffset(int chnum) {
        return (chnum % 3) + 0x100 * (chnum / 3);
    }

    public static int operatorOffset(int opnum) {
        return (opnum % 12) + ((opnum % 12) / 3) + 0x100 * (opnum / 12);
    }


    public void operatorMap(OperatorMapping dest) {
        dest.chan[0] = operatorList(0, 6, 3, 9);
        dest.chan[1] = operatorList(1, 7, 4, 10);
        dest.chan[2] = operatorList(2, 8, 5, 11);
        dest.chan[3] = operatorList(12, 18, 15, 21);
        dest.chan[4] = operatorList(13, 19, 16, 22);
        dest.chan[5] = operatorList(14, 20, 17, 23);
    }

    public int read(int index) {
        return m_regdata[index];
    }

    public boolean write(int index, int data, int[] result) {
        data &= 0xff;
        if ((index & 0xf0) == 0xa0) {
            if (bitfield(index, 0, 2) == 3) {
                return false;
            }

            int latchindex = 0xb8 | bitfield(index, 3);

            if (bitfield(index, 2) != 0) {
                m_regdata[latchindex] = data & 0x3f;
            } else {
                m_regdata[index] = data;
                m_regdata[index | 4] = m_regdata[latchindex];
            }
            return false;

        } else if ((index & 0xf8) == 0xb8) {
            return false;
        }

        m_regdata[index] = data;
        if (index == 0x28) {
            int channel = bitfield(data, 0, 2);
            if (channel == 3)
                return false;
            channel += bitfield(data, 2, 1) * 3;
            result[0] = channel;
            result[1] = bitfield(data, 4, 4);
            return true;
        }
        return false;
    }

    private static final int[] LFO_MAX_COUNT = {109, 78, 72, 68, 63, 45, 9, 6};


    public int clockNoiseAndLfo() {
        if (lfoEnable() == 0) {
            m_lfoCounter = 0;
            m_lfoAm = 0x3f;
            return 0;
        }
        int subcount = m_lfoCounter++ & 0xff;
        if (subcount >= LFO_MAX_COUNT[lfoRate()])
            m_lfoCounter += 0x101 - subcount;
        m_lfoAm = bitfield(m_lfoCounter, 8, 6);

        if (bitfield(m_lfoCounter, 8 + 6) == 0)
            m_lfoAm ^= 0x3f;
        int pm = bitfield(m_lfoCounter, 10, 3);

        if (bitfield(m_lfoCounter, 10 + 3) != 0)
            pm ^= 7;
        return bitfield(m_lfoCounter, 10 + 4) != 0 ? -pm : pm;
    }

    public void resetLfo() {
        m_lfoCounter = 0;
    }

    public int lfoAmOffset(int choffs) {
        int amShift = (1 << (chLfoAmSens(choffs) ^ 3)) - 1;
        return (m_lfoAm << 1) >>> amShift;
    }

    public int noiseState() {
        return 0;
    }

    public void cacheOperatorData(int choffs, int opoffs, OpdataCache cache) {
        cache.waveform = m_waveform;
        cache.waveformOffset = 0;

        int blockFreq = cache.blockFreq = chBlockFreq(choffs);
        if (multiFreq() != 0 && choffs == 2) {
            if (opoffs == 2)
                blockFreq = cache.blockFreq = multiBlockFreq(1);
            else if (opoffs == 10)
                blockFreq = cache.blockFreq = multiBlockFreq(2);
            else if (opoffs == 6)
                blockFreq = cache.blockFreq = multiBlockFreq(0);
        }

        int keyCode = bitfield(blockFreq, 10, 4) << 1;
        keyCode |= bitfield(0xfe80, bitfield(blockFreq, 7, 4));

        cache.detune = Tables.detuneAdjustment(opDetune(opoffs), keyCode);


        cache.multiple = opMultiple(opoffs) * 2;
        if (cache.multiple == 0) {
            cache.multiple = 1;
        }

        if (lfoEnable() == 0 || chLfoPmSens(choffs) == 0)
            cache.phaseStep = computePhaseStep(choffs, opoffs, cache, 0);
        else
            cache.phaseStep = OpdataCache.PHASE_STEP_DYNAMIC;

        cache.totalLevel = opTotalLevel(opoffs) << 3;

        cache.egSustain = opSustainLevel(opoffs);
        cache.egSustain |= (cache.egSustain + 1) & 0x10;
        cache.egSustain <<= 5;

        int ksrval = keyCode >> (opKsr(opoffs) ^ 3);
        cache.egRate[EG_ATTACK] = effectiveRate(opAttackRate(opoffs) * 2, ksrval);
        cache.egRate[EG_DECAY] = effectiveRate(opDecayRate(opoffs) * 2, ksrval);
        cache.egRate[EG_SUSTAIN] = effectiveRate(opSustainRate(opoffs) * 2, ksrval);
        cache.egRate[EG_RELEASE] = effectiveRate(opReleaseRate(opoffs) * 4 + 2, ksrval);
    }

    int computePhaseStep(int choffs, int opoffs, OpdataCache cache, int lfoRawPm) {
        int fnum = bitfield(cache.blockFreq, 0, 11) << 1;
        int pmSensitivity = chLfoPmSens(choffs);
        if (pmSensitivity != 0) {
            fnum += Tables.opnLfoPmPhaseAdjustment(bitfield(cache.blockFreq, 4, 7), pmSensitivity, lfoRawPm);
            fnum &= 0xfff;
        }
        int block = bitfield(cache.blockFreq, 11, 3);
        int phaseStep = (fnum << block) >>> 2;
        phaseStep += cache.detune;
        phaseStep &= 0x1ffff;
        return (phaseStep * cache.multiple) >>> 1;
    }

    public String logKeyon(int choffs, int opoffs) {
        return "";
    }


    private int byteOf(int offset, int start, int count) {
        return byteOf(offset, start, count, 0);
    }

    private int byteOf(int offset, int start, int count, int extraOffset) {
        return bitfield(m_regdata[offset + extraOffset], start, count);
    }

    private int word(int offset1, int start1, int count1, int offset2, int start2, int count2) {
        return word(offset1, start1, count1, offset2, start2, count2, 0);
    }

    private int word(int offset1, int start1, int count1, int offset2, int start2, int count2, int extraOffset) {
        return (byteOf(offset1, start1, count1, extraOffset) << count2) | byteOf(offset2, start2, count2, extraOffset);
    }


    public int multiFreq() {
        return byteOf(0x27, 6, 2) != 0 ? 1 : 0;
    }

    public int lfoEnable() {
        return byteOf(0x22, 3, 1);
    }

    public int lfoRate() {
        return byteOf(0x22, 0, 3);
    }

    public int multiBlockFreq(int num) {
        return word(0xac, 0, 6, 0xa8, 0, 8, num);
    }


    public int chLfoAmSens(int choffs) {
        return byteOf(0xb4, 4, 2, choffs);
    }

    public int chLfoPmSens(int choffs) {
        return byteOf(0xb4, 0, 3, choffs);
    }

    public int chBlockFreq(int choffs) {
        return word(0xa4, 0, 6, 0xa0, 0, 8, choffs);
    }

    public int opDetune(int opoffs) {
        return byteOf(0x30, 4, 3, opoffs);
    }

    public int opMultiple(int opoffs) {
        return byteOf(0x30, 0, 4, opoffs);
    }

    public int opTotalLevel(int opoffs) {
        return byteOf(0x40, 0, 7, opoffs);
    }

    public int opKsr(int opoffs) {
        return byteOf(0x50, 6, 2, opoffs);
    }

    public int opSustainLevel(int opoffs) {
        return byteOf(0x80, 4, 4, opoffs);
    }

    public int opAttackRate(int opoffs) {
        return byteOf(0x50, 0, 5, opoffs);
    }

    public int opDecayRate(int opoffs) {
        return byteOf(0x60, 0, 5, opoffs);
    }

    public int opSustainRate(int opoffs) {
        return byteOf(0x70, 0, 5, opoffs);
    }

    public int opReleaseRate(int opoffs) {
        return byteOf(0x80, 0, 4, opoffs);
    }


    public int chFeedback(int choffs) {
        return byteOf(0xb0, 3, 3, choffs);
    }

    public int chOutputAny(int choffs) {
        return byteOf(0xb4, 6, 2, choffs);
    }

    public int chAlgorithm(int choffs) {
        return byteOf(0xb0, 0, 3, choffs);
    }

    public int chOutput0(int choffs) {
        return byteOf(0xb4, 7, 1, choffs);
    }

    public int chOutput1(int choffs) {
        return byteOf(0xb4, 6, 1, choffs);
    }


    public int opSsgEgEnable(int opoffs) {
        return byteOf(0x90, 3, 1, opoffs);
    }

    public int opSsgEgMode(int opoffs) {
        return byteOf(0x90, 0, 3, opoffs);
    }

    public int opLfoAmEnable(int opoffs) {
        return byteOf(0x60, 7, 1, opoffs);
    }

    public int statusMask() {
        return 0;
    }

    public int noiseEnable() {
        return 0;
    }

    public int irqReset() {
        return 0;
    }

    public int enableTimerA() {
        return byteOf(0x27, 2, 1);
    }

    public int enableTimerB() {
        return byteOf(0x27, 3, 1);
    }

    public int csm() {
        return (byteOf(0x27, 6, 2) == 2) ? 1 : 0;
    }


    public int resetTimerB() {
        return byteOf(0x27, 5, 1);
    }

    public int resetTimerA() {
        return byteOf(0x27, 4, 1);
    }

    public int loadTimerB() {
        return byteOf(0x27, 1, 1);
    }

    public int loadTimerA() {
        return byteOf(0x27, 0, 1);
    }

    public int timerAValue() {
        return word(0x24, 0, 8, 0x25, 0, 2);
    }

    public int timerBValue() {
        return byteOf(0x26, 0, 8);
    }

}


