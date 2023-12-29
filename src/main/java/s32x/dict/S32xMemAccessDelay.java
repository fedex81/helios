package s32x.dict;

import omegadrive.util.BufferUtil;
import s32x.util.Md32xRuntimeData;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 * <p>
 */
public class S32xMemAccessDelay {

    public static final int ROM = 0;
    public static final int FRAME_BUFFER = 1;
    public static final int PALETTE = 2;
    public static final int VDP_REG = 3;
    public static final int SYS_REG = 4;
    public static final int BOOT_ROM = 5;
    public static final int SDRAM = 6;
    public static final int NO_DELAY = 7;

    public static final int[][] readDelays, writeDelays;

    static {
        readDelays = new int[BufferUtil.CpuDeviceAccess.cdaValues.length][8];
        writeDelays = new int[BufferUtil.CpuDeviceAccess.cdaValues.length][8];

        readDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][ROM] = 3; //[0,5]
        readDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][FRAME_BUFFER] = 3; //[2,4]
        readDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][PALETTE] = 2; //min
        readDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][VDP_REG] = 2; //const
        readDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][SYS_REG] = 0; //const
        readDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][BOOT_ROM] = 0; //n/a
        readDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][SDRAM] = 0; //n/a

        writeDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][ROM] = 3; //[0,5]
        writeDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][FRAME_BUFFER] = 0; //const
        writeDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][PALETTE] = 3; //min
        writeDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][VDP_REG] = 0; //const
        writeDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][SYS_REG] = 0; //const
        writeDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][BOOT_ROM] = 0; //n/a
        writeDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][SDRAM] = 0; //n/a

        readDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][ROM] = 11; //[6,15]
        readDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][FRAME_BUFFER] = 9; //[5,12]
        readDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][PALETTE] = 5; //min
        readDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][VDP_REG] = 5; //const
        readDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][SYS_REG] = 1; //const
        readDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][BOOT_ROM] = 1; //const
        readDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][SDRAM] = 6; //12 clock for 8 words burst

        writeDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][ROM] = 11; //[6,15]
        writeDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][FRAME_BUFFER] = 2; //[1,3]
        writeDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][PALETTE] = 5; //min
        writeDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][VDP_REG] = 5; //const
        writeDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][SYS_REG] = 1; //const
        writeDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][BOOT_ROM] = 1; //const
        writeDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()][SDRAM] = 2; //2 clock for 1 word

        System.arraycopy(readDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()], 0, readDelays[BufferUtil.CpuDeviceAccess.SLAVE.ordinal()], 0,
                readDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()].length);
        System.arraycopy(writeDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()], 0, writeDelays[BufferUtil.CpuDeviceAccess.SLAVE.ordinal()], 0,
                writeDelays[BufferUtil.CpuDeviceAccess.MASTER.ordinal()].length);
        //Z80 uses M68k delays/2, for lack of a better idea
        //Blackthorne z80 writes to s32x sysRegs
        for (int i = 0; i < readDelays[0].length; i++) {
            readDelays[BufferUtil.CpuDeviceAccess.Z80.ordinal()][i] = readDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][i] >> 1;
            writeDelays[BufferUtil.CpuDeviceAccess.Z80.ordinal()][i] = writeDelays[BufferUtil.CpuDeviceAccess.M68K.ordinal()][i] >> 1;
        }
    }

    public static void addReadCpuDelay(int deviceType) {
        Md32xRuntimeData.addCpuDelayExt(readDelays, deviceType);
    }

    public static void addWriteCpuDelay(int deviceType) {
        Md32xRuntimeData.addCpuDelayExt(writeDelays, deviceType);
    }
}
