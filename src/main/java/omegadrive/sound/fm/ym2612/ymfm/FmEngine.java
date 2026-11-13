package omegadrive.sound.fm.ym2612.ymfm;

import static omegadrive.sound.fm.ym2612.ymfm.OpnaRegisters.*;
import static omegadrive.sound.fm.ym2612.ymfm.Ymfm.*;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class FmEngine implements YmfmEngineCallbacks {


    public static final int ALL_CHANNELS = OpnaRegisters.ALL_CHANNELS;

    private final YmfmInterface m_intf;
    private int m_envCounter;
    private int m_status;
    private int m_clockPrescale;
    private int m_irqMask;
    private int m_irqState;
    private final int[] m_timerRunning = new int[2];
    private int m_totalClocks;
    private int m_activeChannels;
    private int m_modifiedChannels;
    private int m_prepareCount;
    private final OpnaRegisters m_regs = new OpnaRegisters();
    private final FmChannel[] m_channel = new FmChannel[CHANNELS];
    private final FmOperator[] m_operator = new FmOperator[OPERATORS];

    private final OpnaRegisters.OperatorMapping m_map = new OpnaRegisters.OperatorMapping();
    private final int[] m_writeResult = new int[2];


    public FmEngine(YmfmInterface intf) {
        m_intf = intf;
        m_envCounter = 0;
        m_status = 0;
        m_clockPrescale = DEFAULT_PRESCALE;
        m_irqMask = STATUS_TIMERA | STATUS_TIMERB;
        m_irqState = 0;
        m_timerRunning[0] = m_timerRunning[1] = 0;
        m_totalClocks = 0;
        m_activeChannels = ALL_CHANNELS;
        m_modifiedChannels = ALL_CHANNELS;
        m_prepareCount = 0;

        m_intf.setEngine(this);

        for (int chnum = 0; chnum < CHANNELS; chnum++) {
            m_channel[chnum] = new FmChannel(this, OpnaRegisters.channelOffset(chnum));
        }

        for (int opnum = 0; opnum < OPERATORS; opnum++) {
            m_operator[opnum] = new FmOperator(this, OpnaRegisters.operatorOffset(opnum));
        }
        assignOperators();
    }

    public void reset() {
        setResetStatus(0, 0xff);
        m_regs.reset();
        write(REG_MODE, 0);
        for (FmChannel chan : m_channel)
            chan.reset();
        for (FmOperator op : m_operator)
            op.reset();
    }

    public void saveRestore(YmfmSavedState state) {
        m_envCounter = state.saveRestore32(m_envCounter);
        m_status = state.saveRestore8(m_status);
        m_clockPrescale = state.saveRestore8(m_clockPrescale);
        m_irqMask = state.saveRestore8(m_irqMask);
        m_irqState = state.saveRestore8(m_irqState);
        m_timerRunning[0] = state.saveRestore8(m_timerRunning[0]);
        m_timerRunning[1] = state.saveRestore8(m_timerRunning[1]);
        m_totalClocks = state.saveRestore8(m_totalClocks);

        m_regs.saveRestore(state);

        for (int chnum = 0; chnum < CHANNELS; chnum++) {
            m_channel[chnum].saveRestore(state);
        }

        for (int opnum = 0; opnum < OPERATORS; opnum++) {
            m_operator[opnum].saveRestore(state);
        }
        invalidateCaches();
    }

    public int clock(int chanmask) {
        m_totalClocks = (m_totalClocks + 1) & 0xff;
        if (m_modifiedChannels != 0 || m_prepareCount++ >= 4096) {
            m_activeChannels = 0;
            for (int chnum = 0; chnum < CHANNELS; chnum++) {
                if (bitfield(chanmask, chnum) != 0)
                    if (m_channel[chnum].prepare())
                        m_activeChannels |= 1 << chnum;
            }
            m_modifiedChannels = m_prepareCount = 0;
        }

        if (bitfield(++m_envCounter, 0, 2) == EG_CLOCK_DIVIDER)
            m_envCounter += 4 - EG_CLOCK_DIVIDER;
        int lfoRawPm = m_regs.clockNoiseAndLfo();
        for (int chnum = 0; chnum < CHANNELS; chnum++) {
            if (bitfield(chanmask, chnum) != 0)
                m_channel[chnum].clock(m_envCounter, lfoRawPm);
        }
        return m_envCounter;
    }

    public void output(YmfmOutput output, int rshift, int clipmax, int chanmask) {
        chanmask &= m_activeChannels;
        for (int chnum = 0; chnum < CHANNELS; chnum++) {
            if (bitfield(chanmask, chnum) != 0) {
                if (m_channel[chnum].is4op())
                    m_channel[chnum].output4op(output, rshift, clipmax);
                else
                    m_channel[chnum].output2op(output, rshift, clipmax);
            }
        }
    }

    protected void write(int regNum, int data) {
        if (regNum == REG_MODE) {
            m_intf.ymfmSyncModeWrite(data);
            return;
        }
        m_modifiedChannels = ALL_CHANNELS;
        if (m_regs.write(regNum, data, m_writeResult)) {
            int keyonChannel = m_writeResult[0];
            int keyonOpmask = m_writeResult[1];
            if (keyonChannel < CHANNELS) {
                m_channel[keyonChannel].keyonoff(keyonOpmask, KEY_ON_NORMAL, keyonChannel);
                m_channel[keyonChannel].prepare();
            }
        }
    }

    public int status() {
        return m_status & ~STATUS_BUSY & ~m_regs.statusMask() & 0xFF;
    }

    public int setResetStatus(int set, int reset) {
        m_status = (m_status | set) & ~(reset | STATUS_BUSY) & 0xff;
        m_intf.ymfmSyncCheckInterrupts();
        return m_status & ~m_regs.statusMask() & 0xff;
    }

    public void setIrqMask(int mask) {
        m_irqMask = mask & 0xff;
        m_intf.ymfmSyncCheckInterrupts();
    }

    public int clockPrescale() {
        return m_clockPrescale;
    }

    public void setClockPrescale(int prescale) {
        m_clockPrescale = prescale & 0xff;
    }

    public int sampleRate(int baseClock) {
        return baseClock / (m_clockPrescale * OPERATORS);
    }

    public YmfmInterface intf() {
        return m_intf;
    }

    protected void invalidateCaches() {
        m_modifiedChannels = ALL_CHANNELS;
    }

    public OpnaRegisters regs() {
        return m_regs;
    }

    @Override
    public void engineTimerExpired(int tnum) {
        if (tnum == 0 && m_regs.enableTimerA() != 0)
            setResetStatus(STATUS_TIMERA, 0);
        else if (tnum == 1 && m_regs.enableTimerB() != 0)
            setResetStatus(STATUS_TIMERB, 0);

        if (tnum == 0 && m_regs.csm() != 0)
            for (int chnum = 0; chnum < CHANNELS; chnum++) {
                if (bitfield(CSM_TRIGGER_MASK, chnum) != 0) {
                    m_channel[chnum].keyonoff(0xf, KEY_ON_CSM, chnum);
                    m_modifiedChannels |= 1 << chnum;
                }
            }

        m_timerRunning[tnum] = 0;
        updateTimer(tnum, 1, 0);
    }


    @Override
    public void engineCheckInterrupts() {
        int oldState = m_irqState;
        m_irqState = ((m_status & m_irqMask & ~m_regs.statusMask()) != 0) ? 1 : 0;
        if (m_irqState != 0)
            m_status |= STATUS_IRQ;
        else
            m_status &= ~STATUS_IRQ;
        m_status &= 0xff;
        if (oldState != m_irqState)
            m_intf.ymfmUpdateIrq(m_irqState != 0);

    }

    @Override
    public void engineModeWrite(int data) {
        m_modifiedChannels = ALL_CHANNELS;
        m_regs.write(REG_MODE, data, m_writeResult);
        if (m_regs.irqReset() != 0)
            setResetStatus(0, 0x78);
        else {
            int resetMask = 0;
            if (m_regs.resetTimerB() != 0)
                resetMask |= STATUS_TIMERB;
            if (m_regs.resetTimerA() != 0)
                resetMask |= STATUS_TIMERA;
            setResetStatus(0, resetMask);

            updateTimer(1, m_regs.loadTimerB(), -(m_totalClocks & 15));
            updateTimer(0, m_regs.loadTimerA(), 0);
        }
    }

    private void assignOperators() {
        m_regs.operatorMap(m_map);
        for (int chnum = 0; chnum < CHANNELS; chnum++) {
            for (int index = 0; index < 4; index++) {
                int opnum = bitfield(m_map.chan[chnum], 8 * index, 8);
                m_channel[chnum].assign(index, (opnum == 0xff) ? null : m_operator[opnum]);
            }
        }
    }

    private void updateTimer(int tnum, int enable, int deltaClocks) {
        if (enable != 0 && m_timerRunning[tnum] == 0) {
            int period = (tnum == 0) ? (1024 - m_regs.timerAValue()) : 16 * (256 - m_regs.timerBValue());
            period += deltaClocks;

            m_intf.ymfmSetTimer(tnum, period * OPERATORS * m_clockPrescale);
            m_timerRunning[tnum] = 1;
        } else if (enable == 0) {
            m_intf.ymfmSetTimer(tnum, -1);
            m_timerRunning[tnum] = 0;
        }
    }
}
