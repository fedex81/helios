package omegadrive.sound.fm.ym2612.ymfm;

import static omegadrive.sound.fm.ym2612.ymfm.OpnaRegisters.OPERATORS;
import static omegadrive.sound.fm.ym2612.ymfm.OpnaRegisters.STATUS_BUSY;
import static omegadrive.sound.fm.ym2612.ymfm.Ymfm.bitfield;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class Ym2612 {

    protected int m_address;
    protected int m_dacData;
    protected int m_dacEnable;
    protected final FmEngine m_fm;
    private final YmfmOutput m_temp = new YmfmOutput();
    private final YmfmOutput[] m_single = new YmfmOutput[1];

    public Ym2612(YmfmInterface intf) {
        m_address = 0;
        m_dacData = 0;
        m_dacEnable = 0;
        m_fm = new FmEngine(intf);
    }


    public void reset() {
        m_fm.reset();
    }

    public void saveRestore(YmfmSavedState state) {
        m_address = state.saveRestore16(m_address);
        m_dacData = state.saveRestore16(m_dacData);
        m_dacEnable = state.saveRestore8(m_dacEnable);
        m_fm.saveRestore(state);
    }

    public int sampleRate(int inputClock) {
        return m_fm.sampleRate(inputClock);
    }

    public void invalidateCaches() {
        m_fm.invalidateCaches();
    }

    public int readStatus() {
        int result = m_fm.status();
        if (m_fm.intf().ymfmIsBusy()) {
            result |= STATUS_BUSY;
        }
        return result & 0xff;
    }

    public int read(int offset) {
        int result = 0;
        switch (offset & 3) {
            case 0:
                result = readStatus();
                break;
            case 1:
            case 2:
            case 3:
                break;
        }
        return result & 0xff;
    }

    public void writeAddress(int data) {
        m_address = data & 0xff;
    }

    public void writeData(int data) {
        data &= 0xff;
        if (bitfield(m_address, 8) != 0)
            return;
        if (m_address == 0x2a) {
            m_dacData = (m_dacData & ~0x1fe) | ((data ^ 0x80) << 1);
        } else if (m_address == 0x2b) {
            m_dacEnable = bitfield(data, 7);
        } else if (m_address == 0x2c) {
            m_dacData = (m_dacData & ~1) | bitfield(data, 3);
        } else {
            m_fm.write(m_address, data);
        }
        m_fm.intf().ymfmSetBusyEnd(32 * m_fm.clockPrescale());
    }

    public void writeAddressHi(int data) {
        m_address = 0x100 | (data & 0xff);
    }

    public void writeDataHi(int data) {
        if (bitfield(m_address, 8) == 0)
            return;
        m_fm.write(m_address, data & 0xff);
        m_fm.intf().ymfmSetBusyEnd(32 * m_fm.clockPrescale());
    }

    public void write(int offset, int data) {
        switch (offset & 3) {
            case 0:
                writeAddress(data);
                break;
            case 1:
                writeData(data);
                break;
            case 2:
                writeAddressHi(data);
                break;
            case 3:
                writeDataHi(data);
                break;
        }
    }

    protected int dacDiscontinuity(int value) {
        return value < 0 ? value - 3 : value + 4;
    }

    public void generate(YmfmOutput[] output, int outOffset, int numsamples) {
        YmfmOutput temp = m_temp;
        for (int samp = 0; samp < numsamples; samp++) {
            YmfmOutput out = output[outOffset + samp];
            m_fm.clock(FmEngine.ALL_CHANNELS);
            m_fm.intf().tickTimers(OPERATORS * m_fm.clockPrescale());
            out.clear();

            int lastFmChannel = (m_dacEnable != 0) ? 5 : 6;
            for (int chan = 0; chan < lastFmChannel; chan++) {
                m_fm.output(temp.clear(), 5, 256, 1 << chan);
                out.data[0] += dacDiscontinuity(temp.data[0]);
                out.data[1] += dacDiscontinuity(temp.data[1]);
            }

            if (m_dacEnable != 0) {
                int dacval = dacDiscontinuity((short) (m_dacData << 7) >> 7);
                out.data[0] += (m_fm.regs().chOutput0(0x102) != 0) ? dacval : dacDiscontinuity(0);
                out.data[1] += (m_fm.regs().chOutput1(0x102) != 0) ? dacval : dacDiscontinuity(0);
            }

            out.data[0] = (out.data[0] * 128) * 64 / (6 * 65);
            out.data[1] = (out.data[1] * 128) * 64 / (6 * 65);
        }
    }


    public void generate(YmfmOutput output) {
        m_single[0] = output;
        generate(m_single, 0, 1);
    }
}






















