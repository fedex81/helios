package s32x;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.util.MarsLauncherHelper;
import s32x.util.Md32xRuntimeData;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.SLAVE;
import static s32x.MarsRegTestUtil.createTestInstance;
import static s32x.dict.S32xDict.RegSpecS32x.COMM0;
import static s32x.dict.S32xDict.START_32X_SYSREG_CACHE;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CommTest {

    static final int SH2_COMM0_OFFSET = START_32X_SYSREG_CACHE + COMM0.addr;

    private MarsLauncherHelper.Sh2LaunchContext lc;

    @BeforeEach
    public void before() {
        lc = createTestInstance();
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
        Md32xRuntimeData.setAccessTypeExt(SLAVE);
        //at least
        lc.s32XMMREG.write(SH2_COMM0_OFFSET, 0, Size.LONG);
        Md32xRuntimeData.resetCpuDelayExt();
        Assertions.assertEquals(0, lc.s32XMMREG.read(SH2_COMM0_OFFSET, Size.LONG));
        lc.s32XMMREG.write(SH2_COMM0_OFFSET, 0xe1, Size.LONG);

        int cycleDelay = Md32xRuntimeData.getCpuDelayExt();
        Assertions.assertTrue(cycleDelay >= 12);
    }

    /**
     * Ok sequence
     * M68K COMM0 Read 101 WORD, frame 2276, cycle 95720
     * M68K COMM0 Read 1 BYTE, frame 2276, cycle 95736
     * M68K COMM0 Read 101 WORD, frame 2276, cycle 95874
     * M68K COMM0 Read 1 BYTE, frame 2276, cycle 95890
     * ---> this doesn't run when KO, why??? TODO
     * MASTER COMM0 Read 1 BYTE, frame 2276, cycle 95996
     * MASTER COMM2 Write 65 WORD, frame 2276, cycle 96002
     * MASTER COMM0 Write 80 BYTE, frame 2276, cycle 96014
     * --->
     * M68K COMM0 Read 8001 WORD, frame 2276, cycle 96028
     * M68K COMM0 Read 80 BYTE, frame 2276, cycle 96044
     * M68K COMM1 Read 5 WORD, frame 2276, cycle 96072
     * M68K COMM2 Read 65 WORD, frame 2276, cycle 96167
     * M68K COMM0 Write 1 BYTE, frame 2276, cycle 96211
     * M68K COMM0 Read 101 WORD, frame 2276, cycle 96339
     */
    @Test
    public void testFifa96Comm() {
    }

}
