package omegadrive.z80;

import omegadrive.bus.BaseBusProvider;
import omegadrive.util.Size;
import z80core.MemIoOps;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class Z80MemIoOps extends MemIoOps {

    private BaseBusProvider z80BusProvider;
    private long tstatesCount = 0;
    private boolean activeInterrupt;

    public static Z80MemIoOps createInstance(BaseBusProvider z80BusProvider) {
        Z80MemIoOps m = new Z80MemIoOps();
        m.z80BusProvider = z80BusProvider;
        return m;
    }

    @Override
    public int fetchOpcode(int address) {
        tstatesCount += 4;
        return (int) z80BusProvider.read(address, Size.BYTE) & 0xFF;
    }

    @Override
    public int peek8(int address) {
        tstatesCount += 3;
        return (int) z80BusProvider.read(address, Size.BYTE) & 0xFF;
    }

    @Override
    public void poke8(int address, int value) {
        tstatesCount += 3;
        z80BusProvider.write(address, value, Size.BYTE);
    }

    @Override
    public int inPort(int port) {
        tstatesCount += 4;
        return z80BusProvider.readIoPort(port) & 0xFF;
    }

    @Override
    public void outPort(int port, int value) {
        tstatesCount += 4;
        z80BusProvider.writeIoPort(port, value);
    }

    @Override
    public boolean isActiveINT() {
        return activeInterrupt;
    }

    @Override
    public void addressOnBus(int address, int tstates) {
        this.tstatesCount += tstates;
    }

    @Override
    public void interruptHandlingTime(int tstates) {
        this.tstatesCount += tstates;
    }

    @Override
    public long getTstates() {
        return tstatesCount;
    }

    @Override
    public void reset() {
        tstatesCount = 0;
    }

    public void setActiveInterrupt(boolean activeInterrupt) {
        this.activeInterrupt = activeInterrupt;
    }
}
