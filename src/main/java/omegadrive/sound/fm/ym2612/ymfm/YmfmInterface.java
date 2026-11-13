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
    private int m_busyCount = 0;

    public void setEngine(YmfmEngineCallbacks engine) {
        m_engine = engine;
    }

    public void tickTimers(int clocks) {
        if (m_busyCount > 0) {
            m_busyCount = Math.max(0, m_busyCount - clocks);
        }
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
        if (m_busyCount <= 0)
            m_busyCount = clocks;
    }

    public boolean ymfmIsBusy() {
        return m_busyCount > 0;
    }

    public void ymfmUpdateIrq(boolean asserted) {
        //unused
    }

    public int ymfmExternalRead(int type, int address) {
        //unused
        return 0;
    }

    public void ymfmExternalWrite(int type, int address, int data) {
        //unused
    }
}
