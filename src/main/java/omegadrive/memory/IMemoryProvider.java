package omegadrive.memory;

import omegadrive.Device;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface IMemoryProvider extends IMemoryRam, IMemoryRom, Device {

    void setChecksumRomValue(long value);

    void setRomData(int[] data);
}
