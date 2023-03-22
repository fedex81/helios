package s32x.util;

import omegadrive.util.FileUtil;
import omegadrive.util.Size;
import omegadrive.util.Util;
import s32x.util.S32xUtil.CpuDeviceAccess;

import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class BiosHolder {

    private static final byte[] EMPTY = new byte[1];

    public final static BiosHolder NO_BIOS =
            new BiosHolder(new BiosData[]{new BiosData(EMPTY), new BiosData(EMPTY), new BiosData(EMPTY)});

    public static class BiosData {
        public ByteBuffer buffer;
        public int rawSize, padMask;

        public BiosData(byte[] b) {
            rawSize = b.length;
            padMask = Util.getRomMask(rawSize);
            buffer = ByteBuffer.wrap(b);
        }

        public int readBuffer(int address, Size size) {
            return S32xUtil.readBuffer(buffer, address & padMask, size);
        }
    }

    private BiosData[] biosData = new BiosData[CpuDeviceAccess.values().length];
    private Path sh2m, sh2s, m68k;


    public BiosHolder(Path sh2m, Path sh2s, Path m68k) {
        this.sh2m = sh2m;
        this.sh2s = sh2s;
        this.m68k = m68k;
        init();
    }

    public BiosHolder(BiosData[] biosData) {
        this.biosData = biosData;
    }

    private void init() {
        assert sh2m.toFile().exists();
        assert sh2s.toFile().exists();
        assert m68k.toFile().exists();

        biosData[CpuDeviceAccess.MASTER.ordinal()] = new BiosData(FileUtil.readFileSafe(sh2m));
        biosData[CpuDeviceAccess.SLAVE.ordinal()] = new BiosData(FileUtil.readFileSafe(sh2s));
        biosData[CpuDeviceAccess.M68K.ordinal()] = new BiosData(FileUtil.readFileSafe(m68k));

        assert biosData[CpuDeviceAccess.MASTER.ordinal()].buffer.capacity() > 0;
        assert biosData[CpuDeviceAccess.SLAVE.ordinal()].buffer.capacity() > 0;
        assert biosData[CpuDeviceAccess.M68K.ordinal()].buffer.capacity() > 0;
    }

    public BiosData getBiosData(CpuDeviceAccess type) {
        return biosData[type.ordinal()];
    }
}
