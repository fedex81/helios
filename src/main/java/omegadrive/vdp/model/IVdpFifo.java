package omegadrive.vdp.model;

import omegadrive.vdp.model.GenesisVdpProvider.VramMode;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface IVdpFifo {

    class VdpFifoEntry {
        public GenesisVdpProvider.VdpPortType portType;
        public VramMode vdpRamMode;
        public int addressRegister;
        public int data;
        public boolean firstByteWritten;

        @Override
        public String toString() {
            return "VdpFifoEntry{" +
                    "portType=" + portType +
                    ", vdpRamMode=" + vdpRamMode +
                    ", addressRegister=" + addressRegister +
                    ", data=" + data +
                    ", firstByteWritten=" + firstByteWritten +
                    '}';
        }
    }

    void push(VramMode vdpRamMode, int addressReg, int data);

    VdpFifoEntry pop();

    VdpFifoEntry peek();

    boolean isEmpty();

    boolean isFull();

    static IVdpFifo createNoFifo(VdpMemoryInterface memoryInterface) {
        return new IVdpFifo() {

            private VdpFifoEntry latest = new VdpFifoEntry();

            @Override
            public void push(VramMode vdpRamMode, int addressReg, int data) {
                latest.data = data;
                latest.vdpRamMode = vdpRamMode;
                latest.addressRegister = addressReg;
                memoryInterface.writeVideoRamWord(vdpRamMode, data, addressReg);
            }

            @Override
            public VdpFifoEntry pop() {
                throw new IllegalStateException("FIFO is always empty!");
            }

            @Override
            public VdpFifoEntry peek() {
                return latest;
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
