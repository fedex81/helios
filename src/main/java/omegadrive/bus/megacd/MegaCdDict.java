package omegadrive.bus.megacd;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.dict.S32xMemAccessDelay;
import s32x.util.RegSpec;
import s32x.util.S32xUtil.CpuDeviceAccess;

import java.util.HashSet;
import java.util.Set;

import static omegadrive.bus.megacd.MegaCdDict.McdRegCpuType.*;
import static omegadrive.bus.megacd.MegaCdDict.McdRegType.*;
import static omegadrive.bus.megacd.MegaCdDict.RegSpecMcd.MCD_RESET;
import static omegadrive.bus.megacd.MegaCdMemoryContext.MCD_PRG_RAM_SIZE;
import static omegadrive.bus.megacd.MegaCdMemoryContext.MCD_WORD_RAM_2M_SIZE;
import static omegadrive.bus.model.GenesisBusProvider.MEGA_CD_EXP_START;
import static omegadrive.util.Util.th;
import static s32x.util.S32xUtil.CpuDeviceAccess.Z80;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class MegaCdDict {

    private static final Logger LOG = LogHelper.getLogger(MegaCdDict.class.getSimpleName());

    public static final int MDC_SUB_GATE_REGS_SIZE = 0x200;
    public static final int MDC_SUB_GATE_REGS_MASK = MDC_SUB_GATE_REGS_SIZE - 1;

    public enum McdRegCpuType {REG_MAIN, REG_SUB, REG_BOTH}

    public enum McdRegType {NONE, SYS, COMM, CD, ASIC}

    public static RegSpecMcd[][] mcdRegMapping = new RegSpecMcd[McdRegCpuType.values().length][MDC_SUB_GATE_REGS_MASK];

    //
    //TODO force init
    static {
        System.out.println(MCD_RESET);
    }

    public enum RegSpecMcd {
        MCD_RESET(SYS, 0, 0x103, 0xFFFF),   //Reset
        MCD_MEM_MODE(SYS, 2, 0xFFC2, 0xFFFF), //Memory Mode, write protect
        MCD_CDC_MODE(CD, 4, 0, 0xFFFF), //CDC Mode, MAIN read-only

        MCD_HINT_VECTOR(SYS, 6, 0xFFFF, 0xFFFF), //CDC Mode, MAIN read-only

        MCD_CDC_HOST(SYS, 8, 0, 0xFFFF), //CDC host data

        MCD_STOPWATCH(SYS, 0xC, 0, 0xFFFF), //Stopwatch

        MCD_COMM_FLAGS(SYS, 0xE, 0xFF00, 0xFFFF), //Communication flags

        MCD_COMM0(COMM, 0x10, 0xFFFF, 0), //Communication
        MCD_COMM1(COMM, 0x12, 0xFFFF, 0), //Communication
        MCD_COMM2(COMM, 0x14, 0xFFFF, 0), //Communication
        MCD_COMM3(COMM, 0x16, 0xFFFF, 0), //Communication
        MCD_COMM4(COMM, 0x18, 0xFFFF, 0), //Communication
        MCD_COMM5(COMM, 0x1A, 0xFFFF, 0), //Communication
        MCD_COMM6(COMM, 0x1C, 0xFFFF, 0), //Communication
        MCD_COMM7(COMM, 0x1E, 0xFFFF, 0), //Communication

        MCD_COMM8(COMM, 0x20, 0, 0xFFFF), //Communication
        MCD_COMM9(COMM, 0x22, 0, 0xFFFF), //Communication
        MCD_COMMA(COMM, 0x24, 0, 0xFFFF), //Communication
        MCD_COMMB(COMM, 0x26, 0, 0xFFFF), //Communication
        MCD_COMMC(COMM, 0x28, 0, 0xFFFF), //Communication
        MCD_COMMD(COMM, 0x2A, 0, 0xFFFF), //Communication
        MCD_COMME(COMM, 0x2C, 0, 0xFFFF), //Communication
        MCD_COMMF(COMM, 0x2E, 0, 0xFFFF), //Communication

        MCD_TIMER_INT3(SYS, 0x30, 0, 0xFF), //General Use Timer W/INT3
        MCD_INT_MASK(SYS, 0x32, 0, 0x7E), //Interrupt Mask control

        MCD_CD_FADER(CD, 0x34, 0, 0x7FFE), //CD Fader
        MCD_CDD_CONTROL(CD, 0x36, 0, 0x7), //CDD Control

        MCD_FONT_COLOR(SYS, 0x4C, 0, 0xFF), //Font color
        MCD_FONT_BIT(SYS, 0x4E, 0, 0xFFFF), //Font bit
        MCD_FONT_DATA0(SYS, 0x50, 0, 0), //Font data
        MCD_FONT_DATA1(SYS, 0x52, 0, 0), //Font data
        MCD_FONT_DATA2(SYS, 0x54, 0, 0), //Font data
        MCD_FONT_DATA3(SYS, 0x56, 0, 0), //Font data

        MCD_IMG_STAMP_SIZE(ASIC, 0x58, 0, 0x7), //Image Stamp Size

        //TODO valid bits depend on setup
        MCD_IMG_STAMP_MAP_ADDR(ASIC, 0x5A, 0, 0xFFE0), //Image Stamp map base address
        MCD_IMG_VCELL(ASIC, 0x5C, 0, 0x1F), //Image buffer V cell size (0-32 cells)
        MCD_IMG_START_ADDR(ASIC, 0x5E, 0, 0xFFF8), //Image buffer start address

        MCD_IMG_OFFSET(ASIC, 0x60, 0, 0x3F), //Image buffer offset
        MCD_IMG_HDOT(ASIC, 0x62, 0, 0x1FF), //Image buffer H dot size (horizontal dot size overwritten in the buffer)

        MCD_IMG_VDOT(ASIC, 0x64, 0, 0xFF), //Image buffer V dot size (vertical dot size overwritten in the buffer)

        MCD_IMG_TRACE_VECTOR_ADDR(ASIC, 0x66, 0, 0xFFFE),
        //vector base address(Xstart, Ystart, (Dtita)X, <Del ta), table base address)
        INVALID(NONE, -1);

        public final RegSpec regSpec;
        public final McdRegCpuType regCpuType;
        public final McdRegType deviceType;
        public final int addr;
        public final int deviceAccessTypeDelay;

        //defaults to 16 bit wide register
        RegSpecMcd(McdRegType deviceType, int addr, int writeAndMaskMain, int writeAndMaskSub) {
            this.deviceType = deviceType;
            this.deviceAccessTypeDelay = S32xMemAccessDelay.SYS_REG;
            this.regCpuType = getCpuTypeFromDevice(deviceType, name());
            this.regSpec = createRegSpec(addr, writeAndMaskMain, writeAndMaskSub);
            this.addr = regSpec.bufferAddr;
            init();
        }

        RegSpecMcd(McdRegType deviceType, int addr) {
            this(deviceType, addr, 0xFFFF, 0);
        }

        RegSpecMcd(McdRegType deviceType, int addr, int writeAndMask) {
            this(deviceType, addr, writeAndMask, 0);
        }

        private RegSpec createRegSpec(int addr, int writeAndMask, int writeOrMask) {
            return deviceType == NONE ? RegSpec.INVALID_REG :
                    new RegSpec(name(), addr, MDC_SUB_GATE_REGS_MASK,
                            writeAndMask, writeOrMask, Size.WORD);
        }

        private void init() {
            if (deviceType == NONE) {
                return;
            }
            int addrLen = regSpec.regSize.getByteSize();
            for (int i = regSpec.fullAddr; i < regSpec.fullAddr + addrLen; i++) {
                mcdRegMapping[regCpuType.ordinal()][i] = this;
                if (regCpuType == REG_BOTH) {
                    mcdRegMapping[REG_MAIN.ordinal()][i] = this;
                    mcdRegMapping[REG_SUB.ordinal()][i] = this;
                }
            }
        }

        public String getName() {
            return regSpec.name;
        }
    }

    private static McdRegCpuType getCpuTypeFromDevice(McdRegType deviceType, String name) {
        return deviceType == NONE || deviceType == COMM || deviceType == SYS ? McdRegCpuType.REG_BOTH : REG_SUB;
    }

    public static void logAccess(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        LOG.info("{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }

    public static Set<Integer> z80RegAccess = new HashSet<>();

    public static RegSpecMcd getRegSpec(CpuDeviceAccess cpu, int address) {
        assert cpu != Z80; //TODO
        RegSpecMcd r = mcdRegMapping[REG_BOTH.ordinal()][address & MDC_SUB_GATE_REGS_MASK];
        if (r == null) {
            r = mcdRegMapping[REG_SUB.ordinal()][address & MDC_SUB_GATE_REGS_MASK];
            if (r == null) {
                LOG.error("{} unknown register at address: {}", cpu, th(address));
                r = RegSpecMcd.INVALID;
                assert false;
            }
        }
        return r;
    }

    public static void logZ80Access(CpuDeviceAccess cpu, RegSpecMcd r, int address, Size size, boolean read) {
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

    public static final int MCD_MAIN_PRG_RAM_WINDOW_SIZE = 0x20_000;
    public static final int MCD_MAIN_PRG_RAM_WINDOW_MASK = MCD_MAIN_PRG_RAM_WINDOW_SIZE - 1;
    public static final int START_MCD_MAIN_PRG_RAM = 0x20_000;
    public static final int END_MCD_MAIN_PRG_RAM = START_MCD_MAIN_PRG_RAM +
            MCD_MAIN_PRG_RAM_WINDOW_SIZE;

    public static final int START_MCD_MAIN_PRG_RAM_MODE1 = 0x400_000 | START_MCD_MAIN_PRG_RAM;
    public static final int END_MCD_MAIN_PRG_RAM_MODE1 = START_MCD_MAIN_PRG_RAM_MODE1 +
            MCD_MAIN_PRG_RAM_WINDOW_SIZE;
    public static final int START_MCD_WORD_RAM = 0x200_000;
    public static final int END_MCD_WORD_RAM = START_MCD_WORD_RAM + MCD_WORD_RAM_2M_SIZE;

    public static final int START_MCD_WORD_RAM_MODE1 = 0x600_000;
    public static final int END_MCD_WORD_RAM_MODE1 = START_MCD_WORD_RAM_MODE1 + MCD_WORD_RAM_2M_SIZE;

    public static final int MCD_BOOT_ROM_SIZE = 0x20_000;
    public static final int MCD_BOOT_ROM_MASK = MCD_BOOT_ROM_SIZE - 1;
    public static final int START_MCD_BOOT_ROM = 0;
    public static final int END_MCD_BOOT_ROM = MCD_BOOT_ROM_SIZE;
    public static final int START_MCD_BOOT_ROM_MODE1 = START_MCD_BOOT_ROM + 0x400_000;
    public static final int END_MCD_BOOT_ROM_MODE1 = START_MCD_BOOT_ROM_MODE1 + MCD_BOOT_ROM_SIZE;

    public static final int START_MCD_MAIN_GA_COMM_R = MEGA_CD_EXP_START + 0x10;
    public static final int END_MCD_MAIN_GA_COMM_R = START_MCD_MAIN_GA_COMM_R + 0x20;
    public static final int START_MCD_MAIN_GA_COMM_W = START_MCD_MAIN_GA_COMM_R;
    public static final int END_MCD_MAIN_GA_COMM_W = START_MCD_MAIN_GA_COMM_W + 0x10;

    /**
     * SUB
     */
    public static final int START_MCD_SUB_PRG_RAM = 0;
    public static final int END_MCD_SUB_PRG_RAM = START_MCD_SUB_PRG_RAM + MCD_PRG_RAM_SIZE;

    public static final int START_MCD_SUB_GATE_ARRAY_REGS = 0xFF_8000;
    public static final int END_MCD_SUB_GATE_ARRAY_REGS = 0xFF_81FF;

    public static final int START_MCD_SUB_GA_COMM_W = START_MCD_SUB_GATE_ARRAY_REGS + 0x20;
    public static final int END_MCD_SUB_GA_COMM_W = START_MCD_SUB_GA_COMM_W + 0x10;
    public static final int START_MCD_SUB_GA_COMM_R = START_MCD_SUB_GATE_ARRAY_REGS + 0x10;
    public static final int END_MCD_SUB_GA_COMM_R = END_MCD_SUB_GA_COMM_W;
    public static final int SUB_CPU_REGS_MASK = MDC_SUB_GATE_REGS_SIZE - 1;

    public static final int START_MCD_SUB_WORD_RAM = 0x80_000;
    public static final int END_MCD_SUB_WORD_RAM = START_MCD_SUB_WORD_RAM + MCD_WORD_RAM_2M_SIZE;
}
