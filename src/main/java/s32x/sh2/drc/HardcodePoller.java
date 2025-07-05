package s32x.sh2.drc;

import omegadrive.util.BufferUtil;
import omegadrive.util.Size;

import java.util.HashMap;
import java.util.Map;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class HardcodePoller {

    public static Map<Integer, Sh2DrcBlockOptimizer.BlockPollData> hardcodePollers = new HashMap<>();

    static {
        /**
         * Doom Fusion V2
         * 2025-04-03 16:33:07.320 ERROR [XF_V2.32x)] Sh2DrcBlockOptimizer: SLAVE Poll ignored at PC 600260a: 603ffa8 NONE
         *  0600260a	471b	tas.b @R7
         *  0000260c	0829	movt R8
         *  0000260e	2888	tst R8, R8
         *  00002610	8dfb	bt/s H'0000260a
         *  00002612	51f2	mov.l @(2, R15), R1
         */
        int[] w = new int[]{0x471b, 0x0829, 0x2888, 0x8dfb, 0x51f2};
        Sh2DrcBlockOptimizer.BlockPollData bpd = new Sh2DrcBlockOptimizer.BlockPollData(null, null, -1, w);
        bpd.memLoadPos = 0;
        bpd.memLoadTargetSize = Size.BYTE;
        bpd.memLoadOpcode = bpd.words[bpd.memLoadPos];
        bpd.cmpPos = 2;
        bpd.cmpOpcode = bpd.words[bpd.cmpPos];
        bpd.branchPos = 3;
        bpd.branchOpcode = bpd.words[bpd.branchPos];
        hardcodePollers.put(BufferUtil.hashCode(bpd.words, bpd.words.length), bpd);

        /**
         * Doom Fusion V2
         *  2025-02-06 22:58:33.445 ERROR [XF_V2.32x)] Sh2DrcBlockOptimizer: MASTER Poll ignored at PC 201215a: 603f190 NONE
         *   0201215a	6b81	mov.w @R8, R11
         *   0001215c	2bb8	tst R11, R11
         *   0001215e	8ffc	bf/s H'0001215a
         *   00012160	5cfb	mov.l @(11, R15), R12
         */
        w = new int[]{0x6b81, 0x2bb8, 0x8ffc, 0x5cfb};
        bpd = new Sh2DrcBlockOptimizer.BlockPollData(null, null, -1, w);
        bpd.memLoadPos = 0;
        bpd.memLoadTargetSize = Size.WORD;
        bpd.memLoadOpcode = bpd.words[bpd.memLoadPos];
        bpd.cmpPos = 1;
        bpd.cmpOpcode = bpd.words[bpd.cmpPos];
        bpd.branchPos = 2;
        bpd.branchOpcode = bpd.words[bpd.branchPos];
        hardcodePollers.put(BufferUtil.hashCode(bpd.words, bpd.words.length), bpd);

        /**
         * SRB2 32XN
         * 2024-12-24 23:36:34.012 ERROR [X_v0.1.32x] Sh2DrcBlockOptimizer: MASTER Poll ignored at PC 20190e8: 603bf3a NONE
         * 020190e8	6031	mov.w @R3, R0
         * 000190ea	6121	mov.w @R2, R1
         * 000190ec	611d	extu.w R1, R1
         * 000190ee	c901	and H'01, R0
         * 000190f0	3010	cmp/eq R1, R0
         * 000190f2	8bf9	bf H'000190e8
         * <p>
         * 2024-12-24 23:39:33.693 ERROR [X_v0.1.32x] Sh2DrcBlockOptimizer: MASTER Poll ignored at PC 2016e40: 2603bf2c NONE
         * 02016e40	6642	mov.l @R4, R6
         * 00016e42	6163	mov R6, R1
         * 00016e44	3178	sub R7, R1
         * 00016e46	31a3	cmp/ge R10, R1
         * 00016e48	8ffa	bf/s H'00016e40
         * 00016e4a	e363	mov H'63, R3
         */
        w = new int[]{0x6642, 0x6163, 0x3178, 0x31a3, 0x8ffa, 0xe363};
        bpd = new Sh2DrcBlockOptimizer.BlockPollData(null, null, -1, w);
        bpd.memLoadPos = 0;
        bpd.memLoadTargetSize = Size.LONG;
        bpd.memLoadOpcode = bpd.words[bpd.memLoadPos];
        bpd.cmpPos = 3;
        bpd.cmpOpcode = bpd.words[bpd.cmpPos];
        bpd.branchPos = 4;
        bpd.branchOpcode = bpd.words[bpd.branchPos];
        hardcodePollers.put(BufferUtil.hashCode(bpd.words, bpd.words.length), bpd);
    }

    public static void copyData(Sh2DrcBlockOptimizer.BlockPollData src, Sh2DrcBlockOptimizer.BlockPollData dest) {
        dest.memLoadPos = src.memLoadPos;
        dest.memLoadTargetSize = src.memLoadTargetSize;
        dest.memLoadOpcode = src.memLoadOpcode;
        dest.cmpPos = src.cmpPos;
        dest.cmpOpcode = src.cmpOpcode;
        dest.branchPos = src.branchPos;
        dest.branchOpcode = src.branchOpcode;
        dest.branchPc = dest.pc + (dest.branchPos << 1);
        dest.isPoller = true;
    }
}
