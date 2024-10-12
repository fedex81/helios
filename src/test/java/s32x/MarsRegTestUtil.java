package s32x;

import omegadrive.SystemLoader;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.mapper.MdMapperTest;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.*;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import org.junit.jupiter.api.Assertions;
import s32x.StaticBootstrapSupport.NextCycleResettable;
import s32x.bus.S32xBus;
import s32x.dict.S32xDict.RegSpecS32x;
import s32x.event.PollSysEventManager;
import s32x.util.BiosHolder;
import s32x.util.BiosHolder.BiosData;
import s32x.util.MarsLauncherHelper;
import s32x.util.MarsLauncherHelper.Sh2LaunchContext;

import java.nio.ByteBuffer;
import java.util.Random;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
import static s32x.dict.S32xDict.M68K_START_32X_SYSREG;
import static s32x.dict.S32xDict.RegSpecS32x.*;
import static s32x.dict.S32xDict.START_32X_SYSREG_CACHE;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class MarsRegTestUtil {

    public static final int VDP_REG_OFFSET = 0x100;
    public static final int SH2_FBCR_OFFSET = START_32X_SYSREG_CACHE + VDP_REG_OFFSET + FBCR.addr;
    public static final int SH2_BITMAP_MODE_OFFSET = START_32X_SYSREG_CACHE + VDP_REG_OFFSET + VDP_BITMAP_MODE.addr;
    public static final int SH2_SSCR_OFFSET = START_32X_SYSREG_CACHE + VDP_REG_OFFSET + SSCR.addr;
    public static final int SH2_INT_MASK = START_32X_SYSREG_CACHE + RegSpecS32x.SH2_INT_MASK.addr;
    public static final int MD_ADAPTER_CTRL_REG = M68K_START_32X_SYSREG + MD_ADAPTER_CTRL.regSpec.fullAddr;
    public static int SH2_AFLEN_OFFSET = START_32X_SYSREG_CACHE + VDP_REG_OFFSET + AFLR.addr;
    public static int SH2_AFSAR_OFFSET = START_32X_SYSREG_CACHE + VDP_REG_OFFSET + AFSAR.addr;
    private static final int MD_DMAC_CTRL = M68K_START_32X_SYSREG + RegSpecS32x.MD_DMAC_CTRL.regSpec.fullAddr;
    private static final int SH2_DREQ_CTRL = START_32X_SYSREG_CACHE + RegSpecS32x.SH2_DREQ_CTRL.regSpec.fullAddr;

    static {
        System.setProperty("32x.show.vdp.debug.viewer", "false");
    }

    public static NextCycleResettable NO_OP = new NextCycleResettable() {
        @Override
        public void setNextCycle(BufferUtil.CpuDeviceAccess cpu, int value) {
        }

        @Override
        public void onSysEvent(BufferUtil.CpuDeviceAccess cpu, PollSysEventManager.SysEvent event) {
        }
    };

    public static byte[] fillAsMdRom(byte[] rom, boolean random) {
        if (random) {
            long seed = System.currentTimeMillis();
            System.out.println("Seed: " + seed);
            Random rnd = new Random(seed);
            for (int i = 0; i < rom.length; i++) {
                rom[i] = (byte) (1 + rnd.nextInt(0xFE)); //avoid 0 and 0xFF
            }
        }
        System.arraycopy("SEGA MEGADRIVE  ".getBytes(), 0, rom, MdMapperTest.ROM_HEADER_START, 16);
        System.arraycopy("  ".getBytes(), 0, rom, MdCartInfoProvider.SVP_SV_TOKEN_ADDRESS, 2);
        return rom;
    }

    public static Sh2LaunchContext createTestInstance() {
        return createTestInstance(0x1000);
    }

    /**
     * NOTE: any array modification after this point, will be ignored by the emulated system
     */
    public static Sh2LaunchContext createTestInstance(byte[] irom) {
        MdRuntimeData.releaseInstance();
        MdRuntimeData.newInstance(SystemLoader.SystemType.S32X, SystemProvider.NO_CLOCK);
        RomHolder romHolder = new RomHolder(irom);
        Sh2LaunchContext lc = MarsLauncherHelper.setupRom(new S32xBus(), romHolder, createTestBiosHolder());
        IMemoryProvider mp = MemoryProvider.createMdInstance();
        mp.setRomData(irom);
        SystemTestUtil.setupNewMdSystem(lc.bus, mp);
        lc.bus.setRom(lc.rom);
        return lc;
    }

    public static Sh2LaunchContext createTestInstance(int romSize) {
        return createTestInstance(new byte[romSize]);
    }

    private static BiosHolder createTestBiosHolder() {
        ByteBuffer data = ByteBuffer.allocate(0x1000);
        byte[] d = data.array();
        BiosData[] bd = {new BiosData(d), new BiosData(d), new BiosData(d)};
        return new BiosHolder(bd);
    }

    public static int readBus(Sh2LaunchContext lc, CpuDeviceAccess cpu, int reg, Size size) {
        MdRuntimeData.setAccessTypeExt(cpu);
        if (cpu == M68K || cpu == Z80) {
            return (int) lc.bus.read(reg, size);
        } else {
            return lc.memory.read(reg, size);
        }
    }

    public static void writeBus(Sh2LaunchContext lc, CpuDeviceAccess cpu, int reg, int data, Size size) {
        MdRuntimeData.setAccessTypeExt(cpu);
        if (cpu == M68K || cpu == Z80) {
            lc.bus.write(reg, data, size);
        } else {
            lc.memory.write(reg, data, size);
        }
    }

    public static void assertFrameBufferDisplay(S32XMMREG s32XMMREG, int num) {
        int res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.WORD);
        Assertions.assertEquals(num, res & 1);
    }

    public static void assertPEN(S32XMMREG s32XMMREG, boolean enable) {
        int res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.BYTE);
        Assertions.assertEquals(enable ? 1 : 0, (res >> 5) & 1);
    }

    public static void assertFEN(S32XMMREG s32XMMREG, int value) {
        int res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.WORD);
        Assertions.assertEquals(value, (res >> 1) & 1);
    }

    public static void assertPRIO(S32XMMREG s32XMMREG, boolean enable) {
        int res = s32XMMREG.read(SH2_BITMAP_MODE_OFFSET, Size.WORD);
        Assertions.assertEquals(enable ? 1 : 0, (res >> 7) & 1);
    }

    public static void assertVBlank(S32XMMREG s32XMMREG, boolean on) {
        int res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.WORD) & 0xFFFF;
        Assertions.assertEquals(on ? 1 : 0, res >>> 15);
    }

    public static void assertHBlank(S32XMMREG s32XMMREG, boolean on) {
        int res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.WORD) & 0xFFFF;
        Assertions.assertEquals(on ? 1 : 0, (res >> 14) & 1);
    }

    public static void setFmAccess(Sh2LaunchContext lc, int fm) {
        int val = readBus(lc, MASTER, SH2_INT_MASK, Size.WORD) & 0x7FFF;
        writeBus(lc, MASTER, SH2_INT_MASK, (fm << 15) | val, Size.WORD);
        checkFm(lc, fm);
    }

    public static void setRv(Sh2LaunchContext lc, int rv) {
        int val = readBus(lc, M68K, MD_DMAC_CTRL, Size.WORD) & 0xFFFE;
        writeBus(lc, M68K, MD_DMAC_CTRL, (rv & 1) | val, Size.WORD);
        checkRv(lc, rv);
    }

    public static void setAdenMdSide(Sh2LaunchContext lc, boolean enable) {
        setAdenMdSide(lc, enable ? 1 : 0);
    }

    public static void setAdenMdSide(Sh2LaunchContext lc, int val) {
        val &= 1;
        int md0 = readBus(lc, M68K, MD_ADAPTER_CTRL_REG, Size.WORD);
        writeBus(lc, M68K, MD_ADAPTER_CTRL_REG, md0 | val, Size.WORD);
        checkAden(lc, val);
    }

    public static void checkFm(Sh2LaunchContext lc, int exp) {
        Assertions.assertEquals(exp, (readBus(lc, Z80, MD_ADAPTER_CTRL_REG, Size.BYTE) >> 7) & 1);
        Assertions.assertEquals(exp, (readBus(lc, M68K, MD_ADAPTER_CTRL_REG, Size.WORD) >> 15) & 1);
        Assertions.assertEquals(exp, (readBus(lc, M68K, MD_ADAPTER_CTRL_REG, Size.BYTE) >> 7) & 1);
        Assertions.assertEquals(exp, (readBus(lc, MASTER, SH2_INT_MASK, Size.WORD) >> 15) & 1);
        Assertions.assertEquals(exp, (readBus(lc, MASTER, SH2_INT_MASK, Size.BYTE) >> 7) & 1);
        Assertions.assertEquals(exp, (readBus(lc, SLAVE, SH2_INT_MASK, Size.WORD) >> 15) & 1);
        Assertions.assertEquals(exp, (readBus(lc, SLAVE, SH2_INT_MASK, Size.BYTE) >> 7) & 1);
    }

    public static void checkRv(Sh2LaunchContext lc, int exp) {
        Assertions.assertEquals(exp, readBus(lc, Z80, MD_DMAC_CTRL + 1, Size.BYTE) & 1);
        Assertions.assertEquals(exp, readBus(lc, M68K, MD_DMAC_CTRL, Size.WORD) & 1);
        Assertions.assertEquals(exp, readBus(lc, M68K, MD_DMAC_CTRL + 1, Size.BYTE) & 1);
        Assertions.assertEquals(exp, readBus(lc, MASTER, SH2_DREQ_CTRL, Size.WORD) & 1);
        Assertions.assertEquals(exp, readBus(lc, MASTER, SH2_DREQ_CTRL + 1, Size.BYTE) & 1);
        Assertions.assertEquals(exp, readBus(lc, SLAVE, SH2_DREQ_CTRL, Size.WORD) & 1);
        Assertions.assertEquals(exp, readBus(lc, SLAVE, SH2_DREQ_CTRL + 1, Size.BYTE) & 1);
    }

    public static void checkAden(Sh2LaunchContext lc, int expAden) {
        Assertions.assertEquals(expAden, readBus(lc, Z80, MD_ADAPTER_CTRL_REG + 1, Size.BYTE) & 1);
        Assertions.assertEquals(expAden, readBus(lc, M68K, MD_ADAPTER_CTRL_REG, Size.WORD) & 1);
        Assertions.assertEquals(expAden, readBus(lc, M68K, MD_ADAPTER_CTRL_REG + 1, Size.BYTE) & 1);
        Assertions.assertEquals(expAden, (readBus(lc, MASTER, SH2_INT_MASK, Size.WORD) >> 9) & 1);
        Assertions.assertEquals(expAden, (readBus(lc, MASTER, SH2_INT_MASK, Size.BYTE) >> 1) & 1);
        Assertions.assertEquals(expAden, (readBus(lc, SLAVE, SH2_INT_MASK, Size.WORD) >> 9) & 1);
        Assertions.assertEquals(expAden, (readBus(lc, SLAVE, SH2_INT_MASK, Size.BYTE) >> 1) & 1);
        Assertions.assertEquals(expAden, lc.s32XMMREG.aden);
    }

    public static void checkCart(Sh2LaunchContext lc, int exp) {
        Assertions.assertEquals(exp, (readBus(lc, MASTER, SH2_INT_MASK, Size.WORD) >> 8) & 1);
        Assertions.assertEquals(exp, (readBus(lc, SLAVE, SH2_INT_MASK, Size.WORD) >> 8) & 1);
        Assertions.assertEquals(exp, readBus(lc, MASTER, SH2_INT_MASK, Size.BYTE) & 1);
        Assertions.assertEquals(exp, readBus(lc, SLAVE, SH2_INT_MASK, Size.BYTE) & 1);
    }

    public static void checkHen(Sh2LaunchContext lc, int exp) {
        Assertions.assertEquals(exp, readBus(lc, MASTER, SH2_INT_MASK, Size.WORD));
        Assertions.assertEquals(exp, readBus(lc, SLAVE, SH2_INT_MASK, Size.WORD));
        Assertions.assertEquals(exp, readBus(lc, MASTER, SH2_INT_MASK + 1, Size.BYTE));
        Assertions.assertEquals(exp, readBus(lc, SLAVE, SH2_INT_MASK + 1, Size.BYTE));
    }

    public static void checkRenBit(Sh2LaunchContext lc, boolean val) {
        int exp = val ? 1 : 0;
        Assertions.assertEquals(exp, (readBus(lc, M68K, MD_ADAPTER_CTRL_REG, Size.WORD) >> 7) & 1);
        Assertions.assertEquals(exp, (readBus(lc, Z80, MD_ADAPTER_CTRL_REG, Size.WORD) >> 7) & 1);
        Assertions.assertEquals(exp, readBus(lc, M68K, MD_ADAPTER_CTRL_REG + 1, Size.BYTE) >> 7);
        Assertions.assertEquals(exp, readBus(lc, Z80, MD_ADAPTER_CTRL_REG + 1, Size.BYTE) >> 7);
    }
}
