package omegadrive.vdp;

import omegadrive.vdp.model.IVdpFifo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.IntStream;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * TODO
 * - when fifo size = 3 and a move.l to vdp happens, I need to write two words -> fifo size = 5
 */
public class VdpFifo implements IVdpFifo {

    private static Logger LOG = LogManager.getLogger(VdpFifo.class.getSimpleName());

    public static final int FIFO_SIZE = 4;
    public static final int FIFO_REAL_SIZE = 6;

    private VdpFifoEntry[] fifo = new VdpFifoEntry[FIFO_REAL_SIZE];
    private int popPointer;
    private int pushPointer;
    private int fifoSize;

    public VdpFifo() {
        IntStream.range(0, FIFO_REAL_SIZE).forEach(i -> fifo[i] = new VdpFifoEntry());
    }

    boolean logEnable = false;

    @Override
    public void push(GenesisVdp.VdpRamType vdpRamType, int addressReg, int data) {
        if (isFullInternal()) {
            LOG.info("FIFO full");
            return;
        }
        VdpFifoEntry entry = fifo[pushPointer];
        entry.data = data;
        entry.addressRegister = addressReg;
        entry.vdpRamType = vdpRamType;
        pushPointer = (pushPointer + 1) % FIFO_REAL_SIZE;
        fifoSize++;
        if (logEnable) {
            LOG.info("Fifo push: " + vdpRamType + ", " + Integer.toHexString(addressReg) + ", " + Integer.toHexString(data));
            logEnable = true;
        }
    }

    @Override
    public VdpFifoEntry pop() {
        if (isEmpty()) {
            LOG.info("FIFO empty");
            return null;
        }
        VdpFifoEntry entry = fifo[popPointer];
        popPointer = (popPointer + 1) % FIFO_REAL_SIZE;
        fifoSize--;
        if (logEnable) {
            LOG.info("Fifo pop: " + entry.vdpRamType + ", " + Integer.toHexString(entry.addressRegister) + ", " + Integer.toHexString(entry.data));
        }
        return entry;
    }

    @Override
    public boolean isEmpty() {
        return fifoSize == 0;
    }

    private boolean isFullInternal() {
        return fifoSize == FIFO_REAL_SIZE - 1;
    }

    @Override
    public boolean isFull() {
        return fifoSize >= FIFO_SIZE;
    }
}
