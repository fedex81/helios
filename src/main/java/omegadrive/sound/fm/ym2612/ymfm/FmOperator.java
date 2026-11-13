package omegadrive.sound.fm.ym2612.ymfm;

import static omegadrive.sound.fm.ym2612.ymfm.Ymfm.*;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class FmOperator {

    private final static int EG_QUIET = 0x380;

    private int m_choffs;
    private final int m_opoffs;
    private int m_phase;
    private int m_envAttenuation;
    private int m_envState;
    private int m_ssgInverted;
    private int m_keyState;
    private int m_keyonLive;
    private final OpdataCache m_cache = new OpdataCache();
    private final OpnaRegisters m_regs;
    private final FmEngine m_owner;

    public FmOperator(FmEngine owner, int opoffs) {
        m_choffs = 0;
        m_opoffs = opoffs;
        m_phase = 0;
        m_envAttenuation = 0x3ff;
        m_envState = Ymfm.EG_RELEASE;
        m_ssgInverted = 0;
        m_keyState = 0;
        m_keyonLive = 0;
        m_regs = owner.regs();
        m_owner = owner;
    }

    public void saveRestore(YmfmSavedState state) {
        m_phase = state.saveRestore32(m_phase);
        m_envAttenuation = state.saveRestore16(m_envAttenuation);
        m_envState = state.saveRestoreEnvelopeState(m_envState);
        m_ssgInverted = state.saveRestore8(m_ssgInverted);
        m_keyState = state.saveRestore8(m_keyState);
        m_keyonLive = state.saveRestore8(m_keyonLive);
    }

    public void reset() {
        m_phase = 0;
        m_envAttenuation = 0x3ff;
        m_envState = Ymfm.EG_RELEASE;
        m_ssgInverted = 0;
        m_keyState = 0;
        m_keyonLive = 0;
    }

    public int opoffs() {
        return m_opoffs;
    }

    public int choffs() {
        return m_choffs;
    }

    public void setChoffs(int choffs) {
        m_choffs = choffs;
    }


    public boolean prepare() {
        m_regs.cacheOperatorData(m_choffs, m_opoffs, m_cache);

        clockKeyState(m_keyonLive != 0 ? 1 : 0);
        m_keyonLive &= ~(1 << Ymfm.KEY_ON_CSM);
        return (m_envState != Ymfm.EG_RELEASE || m_envAttenuation < EG_QUIET);
    }

    public void clock(int envCounter, int lfoRawPm) {
        if (m_regs.opSsgEgEnable(m_opoffs) != 0)
            clockSSgEgState();
        else m_ssgInverted = 0;

        if (bitfield(envCounter, 0, 2) == 0)
            clockEnvelope(envCounter >>> 2);

        clockPhase(lfoRawPm);
    }

    public int computeVolume(int phase, int amOffset) {
        if (m_envAttenuation > EG_QUIET)
            return 0;
        int sinAttenuation = m_cache.waveform[m_cache.waveformOffset + (phase & (OpnaRegisters.WAVEFORM_LENGTH - 1))];
        int envAttenuation = envelopeAttenuation(amOffset) << 2;
        int result = Tables.attenuationToVolume((sinAttenuation & 0x7fff) + envAttenuation);
        return bitfield(sinAttenuation, 15) != 0 ? -result : result;
    }


    public void keyonoff(int on, int type) {
        m_keyonLive = (m_keyonLive & ~(1 << type)) | (bitfield(on, 0) << type);
    }

    public OpnaRegisters regs() {
        return m_regs;
    }

    private void startAttack(boolean isRestart) {
        if (m_envState == Ymfm.EG_ATTACK)
            return;
        m_envState = EG_ATTACK;
        if (!isRestart)
            m_ssgInverted = m_regs.opSsgEgEnable(m_opoffs) & bitfield(m_regs.opSsgEgMode(m_opoffs), 2);

        if (!isRestart)
            m_phase = 0;
        if (m_cache.egRate[EG_ATTACK] >= 62)
            m_envAttenuation = 0;
    }

    private void startAttack() {
        startAttack(false);
    }


    private void startRelease() {
        if (m_envState >= EG_RELEASE) {
            return;
        }
        m_envState = EG_RELEASE;
        if (m_ssgInverted != 0) {
            m_envAttenuation = (0x200 - m_envAttenuation) & 0x3ff;
            m_ssgInverted = 0;
        }
    }

    private void clockKeyState(int keystate) {
        if ((keystate ^ m_keyState) != 0) {
            m_keyState = keystate;
            if (keystate != 0)
                startAttack();
            else
                startRelease();
        }
    }

    private void clockSSgEgState() {
        if (bitfield(m_envAttenuation, 9) == 0)
            return;
        int mode = m_regs.opSsgEgMode(m_opoffs);
        if (bitfield(mode, 0) != 0) {
            m_ssgInverted = bitfield(mode, 2) ^ bitfield(mode, 1);
            if (m_envState != EG_ATTACK)
                m_envAttenuation = (m_ssgInverted != 0) ? 0x200 : 0x3ff;
        } else {
            m_ssgInverted ^= bitfield(mode, 1);
            if (m_envState == EG_DECAY || m_envState == EG_SUSTAIN)
                startAttack(true);
            if (bitfield(mode, 1) == 0)
                m_phase = 0;
        }
        if (m_envState == EG_RELEASE)
            m_envAttenuation = 0x3ff;
    }

    private void clockEnvelope(int envCounter) {
        if (m_envState == EG_ATTACK && m_envAttenuation == 0)
            m_envState = EG_DECAY;

        if (m_envState == EG_DECAY && m_envAttenuation >= m_cache.egSustain)
            m_envState = EG_SUSTAIN;
        int rate = m_cache.egRate[m_envState];
        int rateShift = rate >>> 2;
        envCounter <<= rateShift;

        if (bitfield(envCounter, 0, 11) != 0)
            return;

        int relevantBits = bitfield(envCounter, Math.max(rateShift, 11), 3);
        int increment = Tables.attenuationIncrement(rate, relevantBits);
        if (m_envState == EG_ATTACK) {
            if (rate < 62)
                m_envAttenuation += (~m_envAttenuation * increment) >> 4;
        } else {
            if (m_regs.opSsgEgEnable(m_opoffs) == 0)
                m_envAttenuation += increment;
            else if (m_envAttenuation < 0x200)
                m_envAttenuation += 4 * increment;
            if (m_envAttenuation >= 0x400)
                m_envAttenuation = 0x3ff;
        }
        m_envAttenuation &= 0xffff;
    }

    private void clockPhase(int lfoRawPm) {
        int phaseStep = m_cache.phaseStep;
        if (phaseStep == OpdataCache.PHASE_STEP_DYNAMIC)
            phaseStep = m_regs.computePhaseStep(m_choffs, m_opoffs, m_cache, lfoRawPm);
        m_phase += phaseStep;
    }

    private int envelopeAttenuation(int amOffset) {
        int result = m_envAttenuation >>> m_cache.egShift;
        if (m_ssgInverted != 0)
            result = (0x200 - result) & 0x3ff;
        if (m_regs.opLfoAmEnable(m_opoffs) != 0)
            result += amOffset;
        result += m_cache.totalLevel;
        return Math.min(result, 0x3ff);
    }


    public int phase() {
        return m_phase >>> 10;
    }

    public int computeNoiseVolume(int amOffset) {
        throw new RuntimeException();
    }
}
