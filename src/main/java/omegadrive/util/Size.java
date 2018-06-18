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

    public static void main(String[] args) {
        int data = 0x87;
        data = data << 8 | data;
        System.out.println(Integer.toHexString(data));
    }

}
