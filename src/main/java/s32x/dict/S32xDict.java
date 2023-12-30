package s32x.dict;

import omegadrive.util.BufferUtil;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.RegSpec;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.vdp.MarsVdp;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.Util.th;
import static s32x.dict.S32xDict.S32xRegCpuType.*;
import static s32x.dict.S32xDict.S32xRegType.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class S32xDict {

    private static final Logger LOG = LogHelper.getLogger(S32xDict.class.getSimpleName());

    public static final int S32X_REG_SIZE = 0x200;
    public static final int S32X_REG_MASK = S32X_REG_SIZE - 1;
    public static final int S32X_VDP_REG_MASK = 0xFF;

    public enum S32xRegCpuType {REG_MD, REG_SH2, REG_BOTH}

    public enum S32xRegType {NONE, VDP, PWM, SYS, COMM, DMA}

    public static S32xRegType[] s32xRegTypeMapping = new S32xRegType[S32X_REG_SIZE];
    public static RegSpecS32x[][] s32xRegMapping = new RegSpecS32x[S32xRegCpuType.values().length][S32X_REG_SIZE];

    private static final S32xRegCpuType[] cpuToRegTypeMapper =
            new S32xRegCpuType[CpuDeviceAccess.values().length];

    static {
        cpuToRegTypeMapper[MASTER.ordinal()] = REG_SH2;
        cpuToRegTypeMapper[SLAVE.ordinal()] = REG_SH2;
        cpuToRegTypeMapper[M68K.ordinal()] = REG_MD;
        cpuToRegTypeMapper[Z80.ordinal()] = REG_MD;
    }

    public enum RegSpecS32x {
        SH2_INT_MASK(SYS, 0, 0x808F),   //Interrupt Mask
        SH2_STBY_CHANGE(SYS, 2),   //StandBy Changer Register
        SH2_HCOUNT_REG(SYS, 4, 0xFF), //H Count Register
        SH2_DREQ_CTRL(DMA, 6, 0), //DREQ Control Reg. (read-only)
        SH2_DREQ_SRC_ADDR_H(DMA, 8, 0xFF),
        SH2_DREQ_SRC_ADDR_L(DMA, 0xA, 0xFFFE),
        SH2_DREQ_DEST_ADDR_H(DMA, 0xC, 0xFF),
        SH2_DREQ_DEST_ADDR_L(DMA, 0xE, 0xFFE),
        SH2_DREQ_LEN(DMA, 0x10),
        SH2_FIFO_REG(DMA, 0x12),
        SH2_VRES_INT_CLEAR(SYS, 0x14),//VRES Interrupt Clear Register
        SH2_VINT_CLEAR(SYS, 0x16),
        SH2_HINT_CLEAR(SYS, 0x18),
        SH2_CMD_INT_CLEAR(SYS, 0x1A),
        SH2_PWM_INT_CLEAR(SYS, 0x1C),

        MD_ADAPTER_CTRL(SYS, 0, 0x8003, 0x80),
        MD_INT_CTRL(SYS, 2, 0x3),  //Interrupt Control Register
        MD_BANK_SET(SYS, 4, 0x3),  //Bank Set Register
        MD_DMAC_CTRL(DMA, 6, 0x7), //Transfers Data to SH2 DMAC
        MD_DREQ_SRC_ADDR_H(DMA, 8),
        MD_DREQ_SRC_ADDR_L(DMA, 0xA),
        MD_DREQ_DEST_ADDR_H(DMA, 0xC),
        MD_DREQ_DEST_ADDR_L(DMA, 0xE),
        MD_DREQ_LEN(DMA, 0x10),
        MD_FIFO_REG(DMA, 0x12),
        MD_SEGA_TV(SYS, 0x1A, 0x1),

        COMM0(COMM, 0x20),
        COMM1(COMM, 0x22),
        COMM2(COMM, 0x24),
        COMM3(COMM, 0x26),
        COMM4(COMM, 0x28),
        COMM5(COMM, 0x2A),
        COMM6(COMM, 0x2C),
        COMM7(COMM, 0x2E),

        PWM_CTRL(PWM, 0x30),
        PWM_CYCLE(PWM, 0x32, 0xFFF), //PWM Cycle Register
        PWM_LCH_PW(PWM, 0x34, 0xFFF), //PWM Left channel Pulse Width Reg
        PWM_RCH_PW(PWM, 0x36, 0xFFF), //PWM Right channel Pulse Width Reg
        PWM_MONO(PWM, 0x38, 0xFFF), //PWM Mono Pulse Width Reg

        VDP_BITMAP_MODE(VDP, 0x100),
        SSCR(VDP, 0x102, 1), //Screen Shift Control Register
        AFLR(VDP, 0x104, 0xFF), //Auto Fill Length Register
        AFSAR(VDP, 0x106), //Auto Fill Start Address Register
        AFDR(VDP, 0x108), //Auto Fill Data Register
        FBCR(VDP, 0x10A, 0xFFFC), //Frame Buffer Control Register

        INVALID(NONE, -1);

        public final RegSpec regSpec;
        public final S32xRegCpuType regCpuType;
        public final S32xRegType deviceType;
        public final int addr;
        public final int deviceAccessTypeDelay;

        //defaults to 16 bit wide register
        RegSpecS32x(S32xRegType deviceType, int addr, int writeAndMask, int writeOrMask) {
            this.deviceType = deviceType;
            this.deviceAccessTypeDelay = deviceType == VDP ? S32xMemAccessDelay.VDP_REG : S32xMemAccessDelay.SYS_REG;
            this.regCpuType = getCpuTypeFromDevice(deviceType, name());
            this.regSpec = createRegSpec(addr, writeAndMask, writeOrMask);
            this.addr = regSpec.bufferAddr;
            init();
        }

        RegSpecS32x(S32xRegType deviceType, int addr) {
            this(deviceType, addr, 0xFFFF, 0);
        }

        RegSpecS32x(S32xRegType deviceType, int addr, int writeAndMask) {
            this(deviceType, addr, writeAndMask, 0);
        }

        private RegSpec createRegSpec(int addr, int writeAndMask, int writeOrMask) {
            return deviceType == NONE ? RegSpec.INVALID_REG :
                    new RegSpec(name(), addr, (deviceType != VDP ? S32X_REG_MASK : S32X_VDP_REG_MASK),
                            writeAndMask, writeOrMask, Size.WORD);
        }

        private void init() {
            if (deviceType == NONE) {
                return;
            }
            int addrLen = regSpec.regSize.getByteSize();
            for (int i = regSpec.fullAddr; i < regSpec.fullAddr + addrLen; i++) {
                s32xRegMapping[regCpuType.ordinal()][i] = this;
                s32xRegTypeMapping[i] = deviceType;
                if (regCpuType == S32xRegCpuType.REG_BOTH) {
                    s32xRegMapping[REG_MD.ordinal()][i] = this;
                    s32xRegMapping[REG_SH2.ordinal()][i] = this;
                }
            }
        }

        public int getAddrMask() {
            return regSpec.addrMask;
        }

        public String getName() {
            return regSpec.name;
        }
    }

    private static S32xRegCpuType getCpuTypeFromDevice(S32xRegType deviceType, String name) {
        return deviceType == NONE || deviceType == COMM || deviceType == PWM || deviceType == VDP ? S32xRegCpuType.REG_BOTH :
                S32xRegCpuType.valueOf("REG_" + name.split("_")[0]);
    }

    public static final int P32XS_FM = (1 << 15);
    public static final int SH2_nCART_WORD = (1 << 8);
    public static final int SH2_nCART_BYTE = (1 << 0);

    public static final int P32XS_REN_POS = 7;
    public static final int P32XS_REN = (1 << P32XS_REN_POS);
    public static final int P32XS_nRES = (1 << 1);

    public static final int MD_ADEN_BIT = 1 << 0;
    public static final int MD_ADEN_BIT_POS = 0;
    public static final int SH2_ADEN_BYTE = (1 << 1);
    public static final int SH2_ADEN_WORD = (1 << 9);
    public static final int P32XS_FULL = (1 << 7); // DREQ FIFO full
    public static final int P32XS_68S = (1 << 2);
    public static final int P32XS_DMA = (1 << 1);
    public static final int P32XS_RV = (1 << 0);

    public static final int INTMASK_HEN_BIT_POS = 7;
    public static final int FBCR_VBLK_BIT_POS = 15;
    public static final int FBCR_HBLK_BIT_POS = 14;
    public static final int FBCR_FRAMESEL_BIT_POS = 0;
    public static final int FBCR_nFEN_BIT_POS = 1;
    public static final int P32XV_VBLK = (1 << FBCR_VBLK_BIT_POS);
    public static final int P32XV_PAL = (1 << 15);

    public static final int P32XV_PEN = (1 << 13);
    public static final int P32XV_PRIO = (1 << 7);
    public static final int P32XV_240 = (1 << 6);

    public static final int SH2_PC_AREAS = 0x100;
    public static final int SH2_PC_AREA_SHIFT = 24;

    /**
     * SH2 memory map
     **/
    private static final int SH2_BOOT_ROM_SIZE = 0x4000; // 16kb
    public static final int SH2_SDRAM_SIZE = 0x4_0000; // 256kb
    private static final int SH2_MAX_ROM_SIZE = 0x40_0000; // 256kb
    public static final int SH2_SDRAM_MASK = SH2_SDRAM_SIZE - 1;
    private static final int SH2_ROM_MASK = SH2_MAX_ROM_SIZE - 1;

    public static final int SH2_CACHE_THROUGH_OFFSET = 0x2000_0000;

    public static final int SH2_CACHE_THROUGH_MASK = 0xFFF_FFFF;

    public static final int SH2_START_BOOT_ROM = SH2_CACHE_THROUGH_OFFSET;
    public static final int SH2_END_BOOT_ROM = SH2_START_BOOT_ROM + SH2_BOOT_ROM_SIZE;

    public static final int SH2_START_SDRAM_CACHE = 0x600_0000;
    public static final int SH2_START_SDRAM = SH2_CACHE_THROUGH_OFFSET + SH2_START_SDRAM_CACHE;
    public static final int SH2_END_SDRAM_CACHE = SH2_START_SDRAM_CACHE + SH2_SDRAM_SIZE;
    public static final int SH2_END_SDRAM = SH2_START_SDRAM + SH2_SDRAM_SIZE;

    public static final int SH2_START_ROM_CACHE = 0x200_0000;
    public static final int SH2_START_ROM = SH2_CACHE_THROUGH_OFFSET + SH2_START_ROM_CACHE;
    public static final int SH2_END_ROM_CACHE = SH2_START_ROM_CACHE + 0x40_0000; //4 Mbit window;
    public static final int SH2_END_ROM = SH2_START_ROM + 0x40_0000; //4 Mbit window;

    public static final int SH2_START_CACHE_FLUSH = 0x6000_0000;
    public static final int SH2_END_CACHE_FLUSH = 0x8000_0000;
    public static final int SH2_START_DATA_ARRAY = 0xC000_0000;
    public static final int SH2_ONCHIP_REG_MASK = 0xE000_4000;
    public static final int SH2_START_DRAM_MODE = 0xFFFF_8000;
    public static final int SH2_END_DRAM_MODE = 0xFFFF_C000;

    public static final int SIZE_32X_SYSREG = 0x100;
    public static final int SIZE_32X_VDPREG = 0x100;
    public static final int SIZE_32X_COLPAL = 0x200; // 512 bytes, 256 words
    public static final int DRAM_SIZE = 0x20000; //128 kb window, 2 DRAM banks 128kb each
    public static final int DRAM_MASK = DRAM_SIZE - 1;
    public static final int S32X_MMREG_MASK = 0xFF;
    public static final int S32X_COLPAL_MASK = SIZE_32X_COLPAL - 1;

    public static final int START_32X_SYSREG_CACHE = 0x4000;
    public static final int END_32X_SYSREG_CACHE = START_32X_SYSREG_CACHE + SIZE_32X_SYSREG;
    public static final int START_32X_VDPREG_CACHE = END_32X_SYSREG_CACHE;
    public static final int END_32X_VDPREG_CACHE = START_32X_VDPREG_CACHE + SIZE_32X_VDPREG;
    public static final int START_32X_COLPAL_CACHE = END_32X_VDPREG_CACHE;
    public static final int END_32X_COLPAL_CACHE = START_32X_COLPAL_CACHE + SIZE_32X_COLPAL;

    public static final int START_32X_SYSREG = START_32X_SYSREG_CACHE + SH2_CACHE_THROUGH_OFFSET;
    public static final int START_32X_VDPREG = START_32X_VDPREG_CACHE + SH2_CACHE_THROUGH_OFFSET;
    public static final int START_32X_COLPAL = START_32X_COLPAL_CACHE + SH2_CACHE_THROUGH_OFFSET;
    public static final int END_32X_SYSREG = START_32X_SYSREG + SIZE_32X_SYSREG;
    public static final int END_32X_VDPREG = START_32X_VDPREG + SIZE_32X_VDPREG;
    public static final int END_32X_COLPAL = START_32X_COLPAL + SIZE_32X_COLPAL;

    public static final int START_DRAM_CACHE = 0x400_0000;
    public static final int END_DRAM_CACHE = START_DRAM_CACHE + DRAM_SIZE;
    public static final int START_DRAM = START_DRAM_CACHE + SH2_CACHE_THROUGH_OFFSET;
    public static final int END_DRAM = END_DRAM_CACHE + SH2_CACHE_THROUGH_OFFSET;

    public static final int START_OVER_IMAGE_CACHE = 0x402_0000;
    public static final int END_OVER_IMAGE_CACHE = START_OVER_IMAGE_CACHE + DRAM_SIZE;
    public static final int START_OVER_IMAGE = START_OVER_IMAGE_CACHE + SH2_CACHE_THROUGH_OFFSET;
    public static final int END_OVER_IMAGE = END_OVER_IMAGE_CACHE + SH2_CACHE_THROUGH_OFFSET;

    /**
     * Undocumented mirroring (see soulstar)
     * 24000000 - 24020000 DRAM
     * 24020000 - 24040000 OVERIMAGE
     * 24040000 - 24060000 DRAM MIRROR
     */
    public static final int END_DRAM_OVER_MIRROR = END_OVER_IMAGE_CACHE + SH2_CACHE_THROUGH_OFFSET + DRAM_SIZE;
    public static final int DRAM_OVER_MIRROR_MASK = 0xFFF3_FFFF;

    /**
     * M68K memory map
     **/
    public static final int M68K_START_HINT_VECTOR_WRITEABLE = 0x70;
    public static final int M68K_END_HINT_VECTOR_WRITEABLE = 0x74;
    public static final int M68K_END_VECTOR_ROM = 0x100;
    public static final int M68K_START_FRAME_BUFFER = 0x84_0000;
    public static final int M68K_END_FRAME_BUFFER = M68K_START_FRAME_BUFFER + DRAM_SIZE;
    public static final int M68K_START_OVERWRITE_IMAGE = 0x86_0000;
    public static final int M68K_END_OVERWRITE_IMAGE = M68K_START_OVERWRITE_IMAGE + DRAM_SIZE;
    public static final int M68K_START_ROM_MIRROR = 0x88_0000;
    public static final int M68K_END_ROM_MIRROR = 0x90_0000;
    public static final int M68K_START_ROM_MIRROR_BANK = M68K_END_ROM_MIRROR;
    public static final int M68K_END_ROM_MIRROR_BANK = 0xA0_0000;
    public static final int M68K_ROM_WINDOW_MASK = 0x7_FFFF; //according to docs, *NOT* 0xF_FFFF
    public static final int M68K_ROM_MIRROR_MASK = 0xF_FFFF;
    public static final int M68K_START_MARS_ID = 0xA130EC;
    public static final int M68K_END_MARS_ID = 0xA130F0;
    public static final int M68K_START_32X_SYSREG = 0xA1_5100;
    public static final int M68K_END_32X_SYSREG = M68K_START_32X_SYSREG + 0x80;
    public static final int M68K_MASK_32X_SYSREG = M68K_END_32X_SYSREG - M68K_START_32X_SYSREG - 1;
    public static final int M68K_START_32X_VDPREG = M68K_END_32X_SYSREG;
    public static final int M68K_END_32X_VDPREG = M68K_START_32X_VDPREG + 0x80;
    public static final int M68K_MASK_32X_VDPREG = M68K_MASK_32X_SYSREG;
    public static final int M68K_START_32X_COLPAL = M68K_END_32X_VDPREG;
    public static final int M68K_END_32X_COLPAL = M68K_START_32X_COLPAL + 0x200;
    public static final int M68K_MASK_32X_COLPAL = M68K_END_32X_COLPAL - M68K_START_32X_COLPAL - 1;

    public static final int SH2_SYSREG_32X_OFFSET = S32xDict.START_32X_SYSREG,
            SH2_VDPREG_32X_OFFSET = START_32X_VDPREG, SH2_COLPAL_32X_OFFSET = START_32X_COLPAL;

    public static class S32xDictLogContext {
        public CpuDeviceAccess cpu;
        public ByteBuffer regArea;
        public RegSpecS32x regSpec;
        public int fbD, fbW;
        public boolean read;
    }

    public static void checkName(CpuDeviceAccess cpu, RegSpecS32x regSpec, int address, Size size) {
        if (regSpec == null) {
            LOG.warn("{} 32X mmreg unknown reg: {} {}", cpu, th(address), size);
        }
    }

    public static void logAccess(S32xDictLogContext logCtx, int address, int value, Size size) {
        LOG.info("{} 32x reg {} {} ({}) {} {}", logCtx.cpu, logCtx.read ? "read" : "write",
                size, logCtx.regSpec.getName(), th(address), !logCtx.read ? ": " + th(value) : "");
    }

    public static void detectRegAccess(S32xDictLogContext logCtx, int address, int value, Size size) {
        String sformat = "%s %s %s, %s(%X), %4X %s %s";
        final String evenOdd = (address & 1) == 0 ? "EVEN" : "ODD";
        String type = logCtx.read ? "R" : "W";
        RegSpecS32x regSpec = logCtx.regSpec;
        String s = null;
        int currentWord = BufferUtil.readBuffer(logCtx.regArea, regSpec.addr, Size.WORD);
        value = logCtx.read ? currentWord : value;
        switch (regSpec) {
            case VDP_BITMAP_MODE:
                s = String.format(sformat, logCtx.cpu.toString(), type, regSpec.getName(),
                        MarsVdp.BitmapMode.vals[value & 3].name(), value & 3, value, size.name(), evenOdd);
                break;
            case FBCR:
                String s1 = "D" + logCtx.fbD + "W" + logCtx.fbW +
                        "|H" + ((value >> 14) & 1) + "V" + ((value >> 15) & 1);
                s = String.format(sformat, logCtx.cpu.toString(), type, regSpec.getName(),
                        s1, value & 3, value, size.name(), evenOdd);
                break;
            case SH2_INT_MASK:
                if (logCtx.cpu == CpuDeviceAccess.M68K) {
                    s = String.format(sformat, logCtx.cpu, type, regSpec.getName(),
                            "[RESET: " + ((value & 3) >> 1) + ", ADEN: " + (value & 1) + "]", value & 3,
                            value, size.name(), evenOdd);
                } else {
                    s = String.format(sformat, logCtx.cpu.toString(), type, regSpec.getName(), "", value,
                            value, size.name(), evenOdd);
                }
                break;
            case MD_BANK_SET:
                s = String.format(sformat, logCtx.cpu.toString(), type, regSpec.getName(),
                        "", value & 3, value, size.name(), evenOdd);
                break;
            case COMM0:
            case COMM1:
            case COMM2:
            case COMM3:
            case COMM4:
            case COMM5:
            case COMM6:
            case COMM7:
                if (logCtx.read) {
                    return;
                }
                int valueMem = BufferUtil.readBuffer(logCtx.regArea, (address & S32X_REG_MASK) & ~1, Size.LONG);
                String s2 = decodeComm(valueMem);
                s = String.format(sformat, logCtx.cpu.toString(), type, regSpec.getName(),
                        s2, value, valueMem, size.name(), evenOdd);
                break;
            default:
                s = String.format(sformat, logCtx.cpu.toString(), type, regSpec.getName(),
                        "", value, value, size.name(), evenOdd);
                break;
        }
        if (s != null) {
            LOG.info(s);
        }
    }

    public static RegSpecS32x getRegSpec(S32xRegCpuType regCpuType, int address) {
        RegSpecS32x r = s32xRegMapping[regCpuType.ordinal()][address & S32X_REG_MASK];
        if (r == null) {
            logWarnOnce(LOG, "{} unknown register at address: {}", regCpuType, th(address));
        }
        return r;
    }

    public static Set<Integer> z80RegAccess = new HashSet<>();

    public static RegSpecS32x getRegSpec(CpuDeviceAccess cpu, int address) {
        RegSpecS32x r = s32xRegMapping[REG_BOTH.ordinal()][address & S32X_REG_MASK];
        if (r != null) {
            return r;
        }
        r = s32xRegMapping[cpuToRegTypeMapper[cpu.ordinal()].ordinal()][address & S32X_REG_MASK];
        if (r == null) {
            logWarnOnce(LOG, "{} unknown register at address: {}", cpu, th(address));
            r = RegSpecS32x.INVALID;
        }
        assert cpu == Z80 ? r != RegSpecS32x.AFSAR && r != RegSpecS32x.AFDR && r != RegSpecS32x.AFLR : true;
        return r;
    }

    public static void logZ80Access(CpuDeviceAccess cpu, RegSpecS32x r, int address, Size size, boolean read) {
        if (cpu == Z80 && z80RegAccess.add(address)) {
            LOG.warn("{} {} access register {} at address: {} {}", cpu, read ? "read" : "write", r, th(address), size);
        }
    }

    public static String decodeComm(int valueMem) {
        String s1 = "";
        if (valueMem > 0x10_00_00_00) { //LONG might be ASCII
            s1 = "'" + (char) ((valueMem & 0xFF000000) >> 24) +
                    (char) ((valueMem & 0x00FF0000) >> 16) +
                    (char) ((valueMem & 0x0000FF00) >> 8) +
                    (char) ((valueMem & 0x000000FF) >> 0) + "'";
        } else if ((valueMem & 0xFFFF) > 0x10_00) { //WORD might be ASCII
            s1 = "'" + (char) ((valueMem & 0x0000FF00) >> 8) +
                    (char) ((valueMem & 0x000000FF) >> 0) + "'";
        }
        return s1;
    }
}
