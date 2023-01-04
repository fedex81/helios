package omegadrive.util;

import org.slf4j.Logger;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class RomHolder {

    private final static Logger LOG = LogHelper.getLogger(RomHolder.class.getSimpleName());

    public static final RomHolder EMPTY_ROM = new RomHolder(new int[1]);

    public final int baseSize;
    public final int size;
    public final int romMask;
    public final int[] data;

    public RomHolder(int[] rom) {
        this.data = Util.getPaddedRom(rom);
        this.size = data.length;
        this.baseSize = rom.length;
        this.romMask = Util.getRomMask(size);
        assert romMask == size - 1;
        if (baseSize != size) {
            LOG.info(toString());
        }
    }

    @Override
    public String toString() {
        return "RomHolder{" +
                "romSize=" + th(baseSize) +
                ", paddedSize=" + th(size) +
                ", romMask=" + th(romMask) +
                '}';
    }
}
