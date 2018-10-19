package omegadrive.util;

public enum Size {

    BYTE(0x80, 0xFF), WORD(0x8000, 0xFFFF), LONG(0x8000_0000L, 0xFFFF_FFFFL);

    long msb;
    long max;

    Size(long msb, long maxSize) {
        this.msb = msb;
        this.max = maxSize;
    }

    public long getMsb() {
        return this.msb;
    }

    public long getMax() {
        return this.max;
    }

    public static long getMaxFromByteCount(int byteCount) {
        switch (byteCount) {
            case 1:
                return BYTE.getMax();
            case 2:
                return WORD.getMax();
            case 4:
                return LONG.getMax();
        }
        return 0;
    }
}
