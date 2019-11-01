package omegadrive.vdp.model;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public interface VdpMemory {

    int[] getVram();

    default int[] getCram() {
        throw new RuntimeException("Cram not available");
    }

    default int[] getVsram() {
        throw new RuntimeException("Vsram not available");
    }
}
