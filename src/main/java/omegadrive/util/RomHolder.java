package omegadrive.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class RomHolder {

    private final static Logger LOG = LogManager.getLogger(RomHolder.class.getSimpleName());

    public static final RomHolder EMPTY_ROM = new RomHolder(new int[1]);

    public int baseSize, size, romMask;
    public int[] data;

    public RomHolder(int[] rom) {
        this.data = Util.getPaddedRom(rom);
        this.size = data.length;
        this.baseSize = rom.length;
        this.romMask = Util.getRomMask(size);
        assert romMask == size - 1;
        if (baseSize != size) {
            LOG.info(this::toString);
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
