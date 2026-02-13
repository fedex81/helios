package mcd.cdd;

import omegadrive.util.FileUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.StringJoiner;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class TrackContentHelper implements Closeable {

    enum TrackContentType {FILE, BYTE_ARRAY}
    private RandomAccessFile raf;
    private ByteBuffer bb;

    private TrackContentType tct;

    private String fName;

    public static TrackContentHelper ofFile(File f) throws FileNotFoundException {
        var t = new TrackContentHelper();
        t.raf = new RandomAccessFile(f, "r");
        t.fName = f.getName();
        t.tct = TrackContentType.FILE;
        return t;
    }

    public static TrackContentHelper ofDataArray(byte[] b) {
        var t = new TrackContentHelper();
        t.bb = ByteBuffer.wrap(b);
        t.tct = TrackContentType.BYTE_ARRAY;
        return t;
    }

    public long length() throws IOException {
        if (isFileBased()) {
            return raf.length();
        }
        return bb.capacity();
    }

    public void read(byte[] sec) throws IOException {
        read(sec, 0, sec.length);
    }

    public int read(byte[] sec, int offset, int length) throws IOException {
        if (isFileBased()) {
            return raf.read(sec, offset, length);
        } else {
            bb.get(sec, offset, length);
            return length;
        }
    }

    public int readShortLE() throws IOException {
        if (isFileBased()) {
            return FileUtil.readShortLE(raf);
        }
        return FileUtil.readShortLE(bb);
    }

    public void seek(int pos) throws IOException {
        if (isFileBased()) raf.seek(pos);
        else bb.position(pos);
    }

    @Override
    public void close() throws IOException {
        if (isFileBased()) raf.close();
        else bb.clear();
    }

    public boolean checkOpenIfFileBased(boolean openOrClosed) {
        if (isFileBased()) {
            return raf.getChannel().isOpen() == openOrClosed;
        }
        return openOrClosed;
    }

    public boolean isFileBased() {
        return tct == TrackContentType.FILE;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", TrackContentHelper.class.getSimpleName() + "[", "]")
                .add("tct=" + tct)
                .add("fName='" + fName + "'")
                .toString();
    }
}
