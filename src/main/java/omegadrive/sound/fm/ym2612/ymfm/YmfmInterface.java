package omegadrive.sound.fm.ym2612.ymfm;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class YmfmInterface {

    protected YmfmEngineCallbacks m_engine;

    private final long[] m_timerCount = {-1, -1};

    public void setEngine(YmfmEngineCallbacks engine) {
        m_engine = engine;
    }

    public void tickTimers(int clocks) {
        for (int tnum = 0; tnum < 2; tnum++) {
            if (m_timerCount[tnum] < 0) {
                continue;
            }
            m_timerCount[tnum] -= clocks;
            if (m_timerCount[tnum] <= 0) {
                m_timerCount[tnum] = -1;
                if (m_engine != null) {
                    m_engine.engineTimerExpired(tnum);
                }
            }
        }
    }


    public void ymfmSyncModeWrite(int data) {
        m_engine.engineModeWrite(data);
    }

    public void ymfmSyncCheckInterrupts() {
        m_engine.engineCheckInterrupts();
    }

    public void ymfmSetTimer(int tnum, int durationInClocks) {
        if (tnum < 0 || tnum > 1)
            return;
        m_timerCount[tnum] = (durationInClocks < 0) ? -1 : durationInClocks;
    }

    public void ymfmSetBusyEnd(int clocks) {
    }

    public boolean ymfmIsBusy() {
        return false;
    }

    public void ymfmUpdateIrq(boolean asserted) {
    }

    public int ymfmExternalRead(int type, int address) {
        return 0;
    }

    public void ymfmExternalWrite(int type, int address, int data) {
    }
}
