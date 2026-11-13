package omegadrive.sound.fm.ym2612.ymfm;

import java.io.ByteArrayOutputStream;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class YmfmSavedState {

    private final ByteArrayOutputStream m_out;

    private final byte[] m_in;
    private int m_offset;
    private final boolean m_saving;


    public static YmfmSavedState forSaving() {
        return new YmfmSavedState(true, null);
    }

    public static YmfmSavedState forRestoring(byte[] buffer) {
        return new YmfmSavedState(false, buffer);
    }


    private YmfmSavedState(boolean saving, byte[] buffer) {
        m_saving = saving;
        if (saving) {
            m_out = new ByteArrayOutputStream();
            m_in = null;
            m_offset = -1;
        } else {
            m_out = null;
            m_in = buffer != null ? buffer : new byte[0];
            m_offset = 0;
        }
    }

    public boolean saving() {
        return m_saving;
    }

    public byte[] toByteArray() {
        return m_out != null ? m_out.toByteArray() : new byte[0];
    }


    private void write(int data) {
        m_out.write(data & 0xff);
    }

    private int read() {
        return m_offset < m_in.length ? m_in[m_offset++] & 0xff : 0;
    }

    public boolean saveRestoreBool(boolean data) {
        if (m_saving) {
            write(data ? 1 : 0);
            return data;
        }
        return read() != 0;
    }

    public int saveRestore8(int data) {
        if (m_saving) {
            write(data);
            return data;
        }
        return read();
    }

    public int saveRestore16(int data) {
        if (m_saving) {
            write(data);
            write(data >> 8);
            return data;
        }
        int v = read();
        v |= read() << 8;
        return v;
    }

    public int saveRestore32(int data) {
        if (m_saving) {
            write(data);
            write(data >> 8);
            write(data >> 16);
            write(data >> 24);
            return data;
        }
        int v = read();
        v |= read() << 8;
        v |= read() << 16;
        v |= read() << 24;
        return v;
    }

    public int saveRestoreEnvelopeState(int data) {
        if (m_saving) {
            write(data);
            return data;
        }
        return read();
    }

    public void saveRestore8(int[] data) {
        for (int index = 0; index < data.length; index++) {
            data[index] = saveRestore8(data[index] & 0xff);
        }
    }


}

