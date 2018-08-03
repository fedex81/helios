package omegadrive.vdp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class VdpDmaHandler {

    private static Logger LOG = LogManager.getLogger(VdpDmaHandler.class.getSimpleName());

    public static GenesisVdp.DmaMode getDmaMode(int reg17) {
        int dmaBits = reg17 >> 6;
        GenesisVdp.DmaMode mode = (dmaBits & 0b10) < 2 ? GenesisVdp.DmaMode.MEM_TO_VRAM : GenesisVdp.DmaMode.VRAM_FILL;
        mode = (dmaBits & 0b11) == 3 ? GenesisVdp.DmaMode.VRAM_COPY : mode;
        return mode;
    }
}
