package omegadrive.bus;

import omegadrive.SystemProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.util.Size;
import omegadrive.vdp.model.BaseVdpProvider;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public interface BaseBusProvider {

    BaseBusProvider attachDevice(Object device);

    IMemoryProvider getMemory();

    JoypadProvider getJoypad();

    SoundProvider getSound();

    SystemProvider getEmulator();

    BaseVdpProvider getVdp();

    long read(long address, Size size);

    void write(long address, long data, Size size);

    void writeIoPort(int port, int value);

    int readIoPort(int port);

    void reset();

    void closeRom();

    void newFrame();
}
