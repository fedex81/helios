package mcd.pcm;

import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdDict.RegSpecMcd;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static mcd.bus.MegaCdSubCpuBus.logAccessReg;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdPcm implements BufferUtil.StepDevice {

    private static final Logger LOG = LogHelper.getLogger(McdPcm.class.getSimpleName());

    public static final int PCM_REG_MASK = 0x2F;

    public static final int PCM_WAVE_DATA = 0x2001;

    public static final int PCM_AREA_SIZE = MegaCdDict.END_MCD_SUB_PCM_AREA - MegaCdDict.START_MCD_SUB_PCM_AREA;
    public static final int PCM_AREA_MASK = PCM_AREA_SIZE - 1;

    private ByteBuffer waveData;

    public McdPcm() {
        waveData = ByteBuffer.allocate(PCM_AREA_SIZE);
    }

    public int read(int address, Size size) {
        address &= PCM_AREA_MASK;
        if (address >= PCM_WAVE_DATA) {
            LogHelper.logWarnOnce(LOG, "Reading from PCM wave data");
            BufferUtil.readBuffer(waveData, address, size);
        } else if (address < PCM_REG_MASK) {
            RegSpecMcd regSpec = getPcmReg(address);
            logAccessReg(regSpec, SUB_M68K, address, 0, size, true);
        }
        return size.getMask();
    }

    public void write(int address, int value, Size size) {
        address &= PCM_AREA_MASK;
        if (address >= PCM_WAVE_DATA) {
            LogHelper.logWarnOnce(LOG, "Writing to PCM wave data");
            BufferUtil.writeBufferRaw(waveData, address, value, size);
        } else if (address < PCM_REG_MASK) {
            RegSpecMcd regSpec = getPcmReg(address);
            logAccessReg(regSpec, SUB_M68K, address, value, size, false);
        }
    }


    private RegSpecMcd getPcmReg(int address) {
        return MegaCdDict.getRegSpec(SUB_M68K, 0x100 + (address & 0xFF));
    }
}
