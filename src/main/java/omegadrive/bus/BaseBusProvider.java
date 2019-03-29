package omegadrive.bus;

import omegadrive.Device;
import omegadrive.util.Size;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public interface BaseBusProvider extends Device {

    long read(long address, Size size);

    void write(long address, long data, Size size);

    void writeIoPort(int port, int value);

    int readIoPort(int port);

    void closeRom();

    void newFrame();

    BaseBusProvider attachDevice(Device device);
}
