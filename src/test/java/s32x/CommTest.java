package s32x;

import omegadrive.SystemLoader;
import omegadrive.system.SystemProvider;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Helper;
import s32x.util.MarsLauncherHelper;

import java.nio.ByteBuffer;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
import static s32x.MarsRegTestUtil.createTestInstance;
import static s32x.dict.S32xDict.RegSpecS32x.COMM0;
import static s32x.dict.S32xDict.RegSpecS32x.COMM6;
import static s32x.dict.S32xDict.*;
import static s32x.sh2.Sh2Disassembler.NOP;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CommTest {

    static final int SH2_COMM0_OFFSET = START_32X_SYSREG_CACHE + COMM0.addr;
    private static final int BASE_M68K_SYS_REG = 0xA15100;

    private MarsLauncherHelper.Sh2LaunchContext lc;

    private int cycleCounter = 0, frameCounter = 0;

    @BeforeEach
    public void before() {
        Sh2Helper.Sh2Config.reset(configDrcEn);
        lc = createTestInstance();
        MdRuntimeData.releaseInstance();
        MdRuntimeData.newInstance(SystemLoader.SystemType.S32X, new SystemProvider.SystemClock() {
            @Override
            public int getCycleCounter() {
                return cycleCounter;
            }

            @Override
            public long getFrameCounter() {
                return frameCounter;
            }
        });
    }



    /**
     * SLAVE COMM0 Write, frame 2434, 0 LONG, cycle: 76
     * SLAVE COMM0 Read, frame 2434, 0 LONG, cycle: 76
     * SLAVE COMM2 Read, frame 2434, 8 LONG, cycle: 76
     * SLAVE COMM0 Write, frame 2434, e1 LONG, cycle: 80
     * SLAVE COMM2 Read, frame 2434, 0 WORD, cycle: 76
     * MASTER COMM0 Read, frame 2434, e1 LONG, cycle: 77 <- should be reading 0 instead of 0xE1
     * <p>
     * //SLAVE block #1
     * S 06003b2e	c608	mov.l @(8, GBR), R0 [NEW]
     * S 06003b30	6303	mov R0, R3 [NEW]
     * S 06003b32	2019	and R1, R0 [NEW]
     * S 06003b34	cb20	or H'20, R0 [NEW]
     * S 06003b36	3020	cmp/eq R2, R0 [NEW]
     * S 06003b38	8bf9	bf H'06003b2e [NEW]
     * S 06003b3a	0009	nop [NEW]
     * S 06003b3c	e000	mov H'00, R0 [NEW]
     * S 06003b3e	c208	mov.l R0, @(8, GBR) [NEW]  //SLAVE COMM0 Write, 0 LONG, 2 cycles
     * S 06003b40	6117	not R1, R1 [NEW]           //1 cycle
     * S 06003b42	2319	and R1, R3 [NEW]	   //1 cycle
     * S 06003b44	333c	add R3, R3 [NEW]	   //1 cycle
     * S 06003b46	333c	add R3, R3 [NEW]           //1 cycle
     * S 06003b48	c609	mov.l @(9, GBR), R0 [NEW]  //2 cycles
     * S 06003b4a	d108	mov.l @(H'06003b6c), R1 [NEW] //2 cycles
     * S 06003b4c	313c	add R3, R1 [NEW]           //1 cycle
     * S 06003b4e	6112	mov.l @R1, R1 [NEW]        //2 cycles
     * S 06003b50	dd05	mov.l @(H'06003b68), R13 [NEW] //2 cycles
     * S 06003b52	412b	jmp @R1 [NEW]              //1 cycle
     * <p>
     * //SLAVE block #2
     * S 06003b54	0009	nop [NEW] //1 cycle
     * S 06003b7c	d01b	mov.l @(H'06003bec), R0 [NEW] //2 cycles
     * S 06003b7e	c208	mov.l R0, @(8, GBR) [NEW] //SLAVE COMM0 Write, 0xE1 LONG
     * <p>
     * //MASTER poll block, needs to run S 06003b3e < master_block < S 06003b7e (20 cycles window)
     * M 0600090c	c608	mov.l @(8, GBR), R0 [NEW] //MASTER COMM0 Read, 0 LONG,
     * M 0600090e	3010	cmp/eq R1, R0 [NEW]  //R1 = 0
     * M 06000910	8bfc	bf H'0600090c [NEW]
     * <p>
     * Fix: successive COMM writes can only happen > 12 cycles apart, this seems to be enough to fix this case
     */
    @Test
    public void testBrutalComm() {
        MdRuntimeData.setAccessTypeExt(SLAVE);
        //at least
        lc.s32XMMREG.write(SH2_COMM0_OFFSET, 0, Size.LONG);
        MdRuntimeData.resetCpuDelayExt();
        Assertions.assertEquals(0, lc.s32XMMREG.read(SH2_COMM0_OFFSET, Size.LONG));
        lc.s32XMMREG.write(SH2_COMM0_OFFSET, 0xe1, Size.LONG);

        int cycleDelay = MdRuntimeData.getCpuDelayExt();
        Assertions.assertTrue(cycleDelay >= 12);
    }

    protected static Sh2Helper.Sh2Config configDrcEn = new Sh2Helper.Sh2Config(true, true, true, true);

    /**
     * Interleaving should be
     * M68K writes 0xBEEFFACE
     * MASTER reads 0xBEEFFACE
     * SLAVE reads 0xBEEFFACE
     * MASTER writes 0
     *
     * Below the observed failure mode
     *
     * //68k writes 0xBEEFFACE
     * 68M 00ff0044   33fc face 00a1512e      move.w   #$face,$00a1512e [NEW]
     * 68M 00ff004c   33fc beef 00a1512c      move.w   #$beef,$00a1512c [NEW]
     *
     *
     * //MASTER reads 0xBEEFFACE
     *  M 06000b22	d02b	mov.l @(H'06000bd0), R0
     *  M 06000b24	6002	mov.l @R0, R0  //COMM6 read
     *  M 06000b26	d12b	mov.l @(H'06000bd4), R1
     *  M 06000b28	3100	cmp/eq R0, R1
     *  M 06000b2a	8ffa	bf/s H'06000b22
     *
     * //MASTER writes 0
     *  M 06000b2c	0009	nop
     *  M 06000b2e	d028	mov.l @(H'06000bd0), R0
     *  M 06000b30	e100	mov H'00, R1
     *  M 06000b32	2012	mov.l R1, @R0 //COMM6 write
     *
     * //SLAVE reads 0 (instead of 0xBEEFFACE)
     *  S 06001240	d005	mov.l @(H'06001258), R0
     *  S 06001242	6002	mov.l @R0, R0
     *  S 06001244	d105	mov.l @(H'0600125c), R1
     *  S 06001246	3100	cmp/eq R0, R1
     *  S 06001248	8ffa	bf/s H'06001240
     *  S 0600124a	0009	nop
     */
    @Test
    public void testFifa96Comm() {
        int beef_face = 0xBEEF_FACE;
        int mstartPc = 0xb22;
        int sstartPc = 0x1240;
        int mloopPc = 0xb34;
        int sh2Cycles = 32; //4 makes it pass
        int[] msh2Code = {
                0xd02b, 0x6002, 0xd12b, 0x3100, 0x8ffa, NOP, 0xd028, 0xe100, 0x2012,
                NOP, 0xaffd, NOP  //infinite loop @0xB34
        };
        int[] ssh2Code = {
                0xd005, 0x6002, 0xd105, 0x3100, 0x8ffa, NOP,
                NOP, 0xaffd, NOP  //infinite loop
        };
        //setup memory
        ByteBuffer mem = lc.memory.getMemoryDataCtx().sdram;
        mem.putInt(0xbd0, START_32X_SYSREG | COMM6.addr);
        mem.putInt(0x1258, START_32X_SYSREG | COMM6.addr);
        mem.putInt(0xbd4, beef_face);
        mem.putInt(0x125c, beef_face);

        MdRuntimeData.setAccessTypeExt(MASTER);
        setupMemSh2(mstartPc, msh2Code, lc.masterCtx, sh2Cycles);
        setupMemSh2(sstartPc, ssh2Code, lc.slaveCtx, sh2Cycles);

        Runnable m68kWriteComm = () -> {
            MdRuntimeData.setAccessTypeExt(M68K);
            lc.bus.write(BASE_M68K_SYS_REG | COMM6.addr, beef_face, Size.LONG);
        };

        MdRuntimeData.resetCpuDelayExt();

        cycleCounter = 0;
        //M68k writes 0xBEEF to COMM6 at cycle 0
        //MASTER writes 0 to COMM6 at cycle 7
        //SLAVE reads COMM6 at cycle 13
        m68kWriteComm.run();
        int mCycles = 1, sCycles = 1;
        boolean slaveBeeffaceOnce = false;
        boolean masterWritesZero = false;
        do {
            if (mCycles == cycleCounter) {
                MdRuntimeData.setAccessTypeExt(MASTER);
                lc.sh2.run(lc.masterCtx);
                mCycles += lc.masterCtx.cycles_ran + MdRuntimeData.resetCpuDelayExt();
                int temp = cycleCounter;
                cycleCounter = 100;
                masterWritesZero |= 0 == lc.bus.read(BASE_M68K_SYS_REG | COMM6.addr, Size.LONG);
                cycleCounter = temp;
            }
//            m68kWriteComm.run();
            if (sCycles == cycleCounter) {
                if (masterWritesZero) {
                    MdRuntimeData.setAccessTypeExt(SLAVE);
                    lc.sh2.run(lc.slaveCtx);
                    sCycles += lc.slaveCtx.cycles_ran + MdRuntimeData.resetCpuDelayExt();
                    slaveBeeffaceOnce |= beef_face == lc.slaveCtx.registers[0];
                }
            }
            cycleCounter++;
        } while (cycleCounter < 300);
        //COMM6/7 is now 0
        Assertions.assertEquals(0, lc.bus.read(BASE_M68K_SYS_REG | COMM6.addr, Size.LONG));
        //SLAVE was able to read beef_face
        Assertions.assertTrue(slaveBeeffaceOnce);
        //MASTER in an infinite loop
        Assertions.assertEquals(mloopPc, lc.masterCtx.PC & 0xFFF);
        //SLAVE in an infinite loop
        Assertions.assertNotEquals(sstartPc, lc.slaveCtx.PC & 0xFFFF);
    }

    private void setupMemSh2(int startPc, int[] opcodes, Sh2Context sh2Context, int cycles) {
        int startSh2 = SH2_START_SDRAM | startPc;
        ByteBuffer mem = lc.memory.getMemoryDataCtx().sdram;
        for (int i = 0; i < opcodes.length; i++) {
            mem.putShort(startPc + (i << 1), (short) opcodes[i]);
        }
        sh2Context.PC = startSh2;
        sh2Context.cycles = cycles;
    }
}
