package omegadrive.vdp.model;

import omegadrive.vdp.GenesisVdp;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface IVdpFifo {

    class VdpFifoEntry {
        public GenesisVdp.VdpRamType vdpRamType;
        public int addressRegister;
        public int data;
    }

    void push(GenesisVdp.VdpRamType vdpRamType, int addressReg, int data);

    VdpFifoEntry pop();

    boolean isEmpty();

    boolean isFull();

    static IVdpFifo createNoFifo(VdpMemoryInterface memoryInterface) {
        return new IVdpFifo() {
            @Override
            public void push(GenesisVdp.VdpRamType vdpRamType, int addressReg, int data) {
                memoryInterface.writeVideoRamWord(vdpRamType, data, addressReg);
            }

            @Override
            public VdpFifoEntry pop() {
                throw new IllegalStateException("FIFO is always empty!");
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public boolean isFull() {
                return false;
            }
        };
    }

}
