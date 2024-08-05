package mcd.dict;

import omegadrive.util.BufferUtil;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.RegSpec;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

import static mcd.dict.MegaCdDict.McdRegCpuType.*;
import static mcd.dict.MegaCdDict.McdRegType.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdMemoryContext.*;
import static omegadrive.bus.model.GenesisBusProvider.MEGA_CD_EXP_START;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class MegaCdDict {

    private static final Logger LOG = LogHelper.getLogger(MegaCdDict.class.getSimpleName());

    private static final boolean verbose = false;

    public static final int MDC_SUB_GATE_REGS_SIZE = 0x200;
    public static final int MDC_SUB_GATE_REGS_MASK = MDC_SUB_GATE_REGS_SIZE - 1;

    public enum McdRegCpuType {
        REG_MAIN(M68K), REG_SUB(SUB_M68K), REG_BOTH(M68K, SUB_M68K);

        //2 -> Main only
        //4 -> sub only
        //6 -> both
        public final int bitSet;

        McdRegCpuType(CpuDeviceAccess... cpus) {
            int bs = 0;
            for (var cpu : cpus) {
                bs |= cpu.ordinal();
            }
            bitSet = bs;
        }
    }

    public enum McdRegType {NONE, SYS, COMM, CDC, CDD, ASIC, PCM}

    public static RegSpecMcd[][] mcdRegMapping = new RegSpecMcd[McdRegCpuType.values().length][MDC_SUB_GATE_REGS_MASK];

    public enum RegSpecMcd {
        MCD_RESET(SYS, 0),   //Reset
        MCD_MEM_MODE(SYS, 2), //Memory Mode, write protect
        MCD_CDC_MODE(CDC, 4), //CDC Mode, MAIN read-only

        MCD_HINT_VECTOR(SYS, REG_MAIN, 6), //HINT vector (level 4)
        MCD_CDC_REG_DATA(CDC, REG_SUB, 6), //CDC Register data

        MCD_CDC_HOST(CDC, 8), //CDC host data

        MCD_CDC_DMA_ADDRESS(CDC, 0xA), //CDC DMA address

        MCD_STOPWATCH(CDC, 0xC), //Stopwatch

        MCD_COMM_FLAGS(SYS, 0xE), //Communication flags

        MCD_COMM0(COMM, 0x10), //Communication
        MCD_COMM1(COMM, 0x12), //Communication
        MCD_COMM2(COMM, 0x14), //Communication
        MCD_COMM3(COMM, 0x16), //Communication
        MCD_COMM4(COMM, 0x18), //Communication
        MCD_COMM5(COMM, 0x1A), //Communication
        MCD_COMM6(COMM, 0x1C), //Communication
        MCD_COMM7(COMM, 0x1E), //Communication

        MCD_COMM8(COMM, 0x20), //Communication
        MCD_COMM9(COMM, 0x22), //Communication
        MCD_COMMA(COMM, 0x24), //Communication
        MCD_COMMB(COMM, 0x26), //Communication
        MCD_COMMC(COMM, 0x28), //Communication
        MCD_COMMD(COMM, 0x2A), //Communication
        MCD_COMME(COMM, 0x2C), //Communication
        MCD_COMMF(COMM, 0x2E), //Communication

        MCD_TIMER_INT3(SYS, 0x30), //General Use Timer W/INT3
        MCD_INT_MASK(SYS, 0x32), //Interrupt Mask control

        MCD_CD_FADER(CDD, 0x34), //CD Fader
        MCD_CDD_CONTROL(CDD, 0x36), //CDD Control

        MCD_CDD_COMM0(CDD, 0x38), //CDD Comm, status[0],[1]
        MCD_CDD_COMM1(CDD, 0x3A), //CDD Comm, status[2],[3]
        MCD_CDD_COMM2(CDD, 0x3C), //CDD Comm, status[4],[5]
        MCD_CDD_COMM3(CDD, 0x3E), //CDD Comm, status[6],[7]

        MCD_CDD_COMM4(CDD, 0x40), //CDD Comm, status[8],[9] //9 is checksum
        MCD_CDD_COMM5(CDD, 0x42), //CDD Comm, command[0],[1]
        MCD_CDD_COMM6(CDD, 0x44), //CDD Comm, command[2],[3]
        MCD_CDD_COMM7(CDD, 0x46), //CDD Comm, command[4],[5]
        MCD_CDD_COMM8(CDD, 0x48), //CDD Comm, command[6],[7]
        MCD_CDD_COMM9(CDD, 0x4A), //CDD Comm, command[8],[9] //9 is checksum

        MCD_FONT_COLOR(SYS, 0x4C), //Font color
        MCD_FONT_BIT(SYS, 0x4E), //Font bit
        MCD_FONT_DATA0(SYS, 0x50), //Font data
        MCD_FONT_DATA1(SYS, 0x52), //Font data
        MCD_FONT_DATA2(SYS, 0x54), //Font data
        MCD_FONT_DATA3(SYS, 0x56), //Font data

        MCD_IMG_STAMP_SIZE(ASIC, 0x58), //Image Stamp Size

        //TODO valid bits depend on setup
        MCD_IMG_STAMP_MAP_ADDR(ASIC, 0x5A), //Image Stamp map base address
        MCD_IMG_VCELL(ASIC, 0x5C), //Image buffer V cell size (0-32 cells)
        MCD_IMG_START_ADDR(ASIC, 0x5E), //Image buffer start address

        MCD_IMG_OFFSET(ASIC, 0x60), //Image buffer offset
        MCD_IMG_HDOT(ASIC, 0x62), //Image buffer H dot size (horizontal dot size overwritten in the buffer)

        MCD_IMG_VDOT(ASIC, 0x64), //Image buffer V dot size (vertical dot size overwritten in the buffer)

        MCD_IMG_TRACE_VECTOR_ADDR(ASIC, 0x66),
        //vector base address(Xstart, Ystart, (Dtita)X, <Del ta), table base address)

        MCD_PCM_ENV(PCM, 0x101),

        MCD_PCM_PAN(PCM, 0x103),

        MCD_PCM_FDL(PCM, 0x105),

        MCD_PCM_FDH(PCM, 0x107),

        MCD_PCM_LSL(PCM, 0x109),
        MCD_PCM_LSH(PCM, 0x10B),

        MCD_PCM_START(PCM, 0x10D),

        MCD_PCM_CTRL(PCM, 0x10F),

        MCD_PCM_ON_OFF(PCM, 0x111),

        INVALID(NONE, -1);

        public final RegSpec regSpec;
        public final McdRegCpuType regCpuType;
        public final McdRegType deviceType;
        public final int addr;
        public final int deviceAccessTypeDelay;

        //defaults to 16 bit wide register
        RegSpecMcd(McdRegType deviceType, int addr) {
            this(deviceType, REG_BOTH, addr);
        }

        RegSpecMcd(McdRegType deviceType, McdRegCpuType cpuType, int addr) {
            this.deviceType = deviceType;
            this.deviceAccessTypeDelay = 1;
            this.regCpuType = getCpuTypeFromDevice(deviceType, cpuType);
            this.regSpec = createRegSpec(addr);
            this.addr = regSpec.bufferAddr;
            init();
        }

        private RegSpec createRegSpec(int addr) {
            return deviceType == NONE ? RegSpec.INVALID_REG :
                    new RegSpec(name(), addr, MDC_SUB_GATE_REGS_MASK, Size.WORD);
        }

        private void init() {
            if (deviceType == NONE) {
                return;
            }
            int addrLen = regSpec.regSize.getByteSize();
            for (int i = regSpec.fullAddr; i < regSpec.fullAddr + addrLen; i++) {
                if (regCpuType == REG_BOTH) {
                    mcdRegMapping[REG_BOTH.ordinal()][i] = this;
                } else {
                    mcdRegMapping[regCpuType.ordinal()][i] = this;
                }
            }
        }

        public String getName() {
            return regSpec.name;
        }
    }

    public interface BitRegisterDef {
        int getBitPos();

        int getRegBytePos();

        int getBitMask();

        default CpuDeviceAccess getCpu() {
            return null;
        }
    }

    public enum SharedBitDef implements BitRegisterDef {
        RET(0, MCD_MEM_MODE.addr + 1), DMNA(1, MCD_MEM_MODE.addr + 1),
        MODE(2, MCD_MEM_MODE.addr + 1),
        DD0(0, MCD_CDC_MODE.addr), DD1(1, MCD_CDC_MODE.addr), DD2(2, MCD_CDC_MODE.addr),
        EDT(7, MCD_CDC_MODE.addr), DSR(6, MCD_CDC_MODE.addr);


        private final int pos, regBytePos, bitMask;

        SharedBitDef(int p, int rbp) {
            this.pos = p;
            this.regBytePos = rbp;
            this.bitMask = 1 << pos;
        }

        @Override
        public int getBitPos() {
            return pos;
        }

        @Override
        public int getRegBytePos() {
            return regBytePos;
        }

        @Override
        public int getBitMask() {
            return bitMask;
        }
    }

    public enum BitRegDef implements BitRegisterDef {

        RES0(SUB_M68K, 0, MCD_RESET.addr + 1),
        LEDR(SUB_M68K, 0, MCD_RESET.addr),
        LEDG(SUB_M68K, 1, MCD_RESET.addr),
        PM0(SUB_M68K, 3, MCD_MEM_MODE.addr + 1),
        PM1(SUB_M68K, 4, MCD_MEM_MODE.addr + 1),

        IFL2(M68K, 0, MCD_RESET.addr),
        IEN2(M68K, 7, MCD_RESET.addr),
        SRES(M68K, 0, MCD_RESET.addr + 1),
        SBRQ(M68K, 1, MCD_RESET.addr + 1),
        BK0(M68K, 6, MCD_MEM_MODE.addr + 1),
        BK1(M68K, 7, MCD_MEM_MODE.addr + 1);

        private final int pos, regBytePos, bitMask;
        public final CpuDeviceAccess cpu;

        BitRegDef(CpuDeviceAccess cpu, int p, int rbp) {
            this.pos = p;
            this.regBytePos = rbp;
            this.bitMask = 1 << pos;
            this.cpu = cpu;
        }

        @Override
        public int getBitPos() {
            return pos;
        }

        @Override
        public int getRegBytePos() {
            return regBytePos;
        }

        @Override
        public int getBitMask() {
            return bitMask;
        }

        @Override
        public CpuDeviceAccess getCpu() {
            return cpu;
        }
    }

    private static McdRegCpuType getCpuTypeFromDevice(McdRegType deviceType, McdRegCpuType baseType) {
        //override ASIC as subOnly
        return deviceType == ASIC ? REG_SUB : baseType;
    }

    public static void logAccess(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        if (verbose) {
            LOG.info("{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                    size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
        }
    }

    public static Set<Integer> z80RegAccess = new HashSet<>();

    public static RegSpecMcd getRegSpec(CpuDeviceAccess cpu, int address) {
        assert cpu != Z80; //TODO
        RegSpecMcd r = mcdRegMapping[REG_BOTH.ordinal()][address & MDC_SUB_GATE_REGS_MASK];
        if (r == null) {
            int idx = cpu == M68K ? REG_MAIN.ordinal() : REG_SUB.ordinal();
            r = mcdRegMapping[idx][address & MDC_SUB_GATE_REGS_MASK];
            if (r == null) {
                LogHelper.logWarnOnce(LOG, "{} unknown register at address: {}", cpu, th(address));
                r = RegSpecMcd.INVALID;
            }
        }
        return r;
    }

    public static void checkRegLongAccess(RegSpecMcd regSpec, Size size) {
        boolean checkLong = regSpec.deviceType != McdRegType.COMM && (regSpec.deviceType == McdRegType.SYS ||
                (regSpec.addr < RegSpecMcd.MCD_CDD_COMM0.addr || regSpec.addr >= MCD_FONT_COLOR.addr));
        if (checkLong) {
            assert size != Size.LONG;
        }
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

    public static void writeCommonRegWord(MegaCdMemoryContext mc, CpuDeviceAccess cpu, RegSpecMcd reg, int value) {
        assert reg.deviceType != SYS;
        assert reg.addr > NUM_SYS_REG_NON_SHARED;
        writeReg(mc, cpu, reg, reg.addr, value, Size.WORD);
    }

    public static void writeSysRegWord(MegaCdMemoryContext mc, CpuDeviceAccess cpu, RegSpecMcd reg, int value) {
        assert reg.addr < NUM_SYS_REG_NON_SHARED;
        writeReg(mc, cpu, reg, reg.addr, value, Size.WORD);
    }

    public static void writeReg(MegaCdMemoryContext mc, CpuDeviceAccess cpu, RegSpecMcd reg, int value, Size size) {
        writeReg(mc, cpu, reg, reg.addr, value, size);
    }

    public static void writeReg(MegaCdMemoryContext mc, CpuDeviceAccess cpu, RegSpecMcd reg, int addr, int value, Size size) {
        assert (reg.regCpuType.bitSet & cpu.ordinal()) > 0;
        BufferUtil.writeBufferRaw(mc.getRegBuffer(cpu, reg), addr, value, size);
    }


    public static final int MCD_MAIN_MODE1_MASK = 0x400_000;

    public static final int M68K_START_HINT_VECTOR_WRITEABLE = 0x70;
    public static final int M68K_END_HINT_VECTOR_WRITEABLE = 0x74;

    public static final int M68K_START_HINT_VECTOR_WRITEABLE_M1 = M68K_START_HINT_VECTOR_WRITEABLE | MCD_MAIN_MODE1_MASK;
    public static final int M68K_END_HINT_VECTOR_WRITEABLE_M1 = M68K_END_HINT_VECTOR_WRITEABLE | MCD_MAIN_MODE1_MASK;

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

    public static final int END_MCD_WORD_RAM_1M_BANK0 = START_MCD_WORD_RAM + MCD_WORD_RAM_1M_SIZE;

    public static final int START_MCD_WORD_RAM_MODE1 = 0x600_000;
    public static final int END_MCD_WORD_RAM_MODE1 = START_MCD_WORD_RAM_MODE1 + MCD_WORD_RAM_2M_SIZE;
    public static final int END_MCD_WORD_RAM_1M_MODE1 = START_MCD_WORD_RAM_MODE1 + MCD_WORD_RAM_1M_SIZE;

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


    public static final int MCD_SUB_BRAM_SIZE = 0x2000;
    /**
     * only odd addresses map to BRAM, internal BRAM size is actually half
     */
    public static final int MCD_SUB_BRAM_MEMORY_WINDOW = MCD_SUB_BRAM_SIZE << 1;

    public static final int MCD_SUB_BRAM_MEM_WINDOW_MASK = MCD_SUB_BRAM_MEMORY_WINDOW - 1;
    public static final int START_MCD_SUB_BRAM_AREA = 0xFE_0000;

    //bram size is 0x4000 but is mirrored within a 0x10_000 area
    public static final int END_MCD_SUB_BRAM_AREA = 0xFF_0000;
    public static final int START_MCD_SUB_PCM_AREA = 0xFF_0000;

    public static final int END_MCD_SUB_PCM_AREA = 0xFF_8000;
    public static final int START_MCD_SUB_GATE_ARRAY_REGS = END_MCD_SUB_PCM_AREA;
    public static final int END_MCD_SUB_GATE_ARRAY_REGS = 0xFF_8200;

    public static final int START_MCD_SUB_GA_COMM_W = START_MCD_SUB_GATE_ARRAY_REGS + 0x20;
    public static final int END_MCD_SUB_GA_COMM_W = START_MCD_SUB_GA_COMM_W + 0x10;
    public static final int START_MCD_SUB_GA_COMM_R = START_MCD_SUB_GATE_ARRAY_REGS + 0x10;
    public static final int END_MCD_SUB_GA_COMM_R = END_MCD_SUB_GA_COMM_W;

    public static final int START_MCD_SUB_WORD_RAM_2M = 0x80_000;
    public static final int END_MCD_SUB_WORD_RAM_2M = START_MCD_SUB_WORD_RAM_2M + MCD_WORD_RAM_2M_SIZE;

    public static final int START_MCD_SUB_WORD_RAM_1M = 0xC0_000;

    public static final int END_MCD_SUB_WORD_RAM_1M = START_MCD_SUB_WORD_RAM_1M + MCD_WORD_RAM_1M_SIZE;
}
