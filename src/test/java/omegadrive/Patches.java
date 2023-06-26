package omegadrive;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class Patches {

    /**
     * It came from the desert
     * Initial part of the intro doesn't show any graphics.
     * We're skipping the code that setups CRAM, ie the palette is all blacks
     *
     * 378e -> 3792 -> 37a6 -> 43c
     *
     *
     * CPU  003786  0cb8  cmpi.l  #$00000001,($ff8784) tS3CvzNx
     * CPU  00378e  6600  bne     $0037bc tS3cvZnx
     *
     * //write ff8784
     * CPU  00319c  21fc  move.l  #$00000000,($ff8784)
     * CPU  003e84  52b8  addq.l  #1,($ff8784)
     *
     *
     * ff8784 goes 0->1->2, then we reach 003786 -> never executes code @ 3792
     * addq is run during vblank (intLevel = 6)
     * cmpi is run during normal operation (intLevel = 3)
     *
     * Code running between vblanks is too slow, if you reduce the delays for the 68k the problem is solved.
     * hblank is a noop (rte)
     *
     * BaseSystem: Frame: 534
     * 2023-05-19 11:33:30.303 INFO [Proto).zip] Megadrive: DDD, 56, 00005be6   48e7 e0e0               movem.l  d0-d2/a0-a2,-(a7)
     * 2023-05-19 11:33:30.315 INFO [Proto).zip] BaseSystem: Frame: 535
     * 2023-05-19 11:33:30.321 INFO [Proto).zip] Megadrive: DDD, 60, 00005c58   4cdf 0707               movem.l  (a7)+,d0-d2/a0-a2
     * 2023-05-19 11:33:30.321 INFO [Proto).zip] Megadrive: DDD, 64, 0006b492   0c79 0003 00ff0000      cmpi.w   #$0003,$00ff0000
     * 2023-05-19 11:33:30.321 INFO [Proto).zip] Megadrive: DDD, 128, 00000366   48e7 fffe               movem.l  d0-d7/a0-a6,-(a7)
     * 2023-05-19 11:33:30.321 INFO [Proto).zip] MC68000WrapperFastDebug: 00003e84   52b8 8784               addq.l   #1,$8784, mem 0xffff8784: 1, intLevel: 6
     * 2023-05-19 11:33:30.321 INFO [Proto).zip] Megadrive: DDD, 132, 00000370   4cdf 7fff               movem.l  (a7)+,d0-d7/a0-a6
     * 2023-05-19 11:33:30.332 INFO [Proto).zip] BaseSystem: Frame: 536
     * 2023-05-19 11:33:30.332 INFO [Proto).zip] Megadrive: DDD, 64, 0006b492   0c79 0003 00ff0000      cmpi.w   #$0003,$00ff0000
     * 2023-05-19 11:33:30.332 INFO [Proto).zip] Megadrive: DDD, 128, 00000366   48e7 fffe               movem.l  d0-d7/a0-a6,-(a7)
     * 2023-05-19 11:33:30.332 INFO [Proto).zip] MC68000WrapperFastDebug: 00003e84   52b8 8784               addq.l   #1,$8784, mem 0xffff8784: 2, intLevel: 6
     * 2023-05-19 11:33:30.332 INFO [Proto).zip] Megadrive: DDD, 132, 00000370   4cdf 7fff               movem.l  (a7)+,d0-d7/a0-a6
     * 2023-05-19 11:33:30.333 INFO [Proto).zip] Megadrive: DDD, 54, 000035ba   c1c2                    muls     d2,d0
     * 2023-05-19 11:33:30.333 INFO [Proto).zip] Megadrive: DDD, 54, 000035ba   c1c2                    muls     d2,d0
     * 2023-05-19 11:33:30.333 INFO [Proto).zip] Megadrive: DDD, 54, 000035ba   c1c2                    muls     d2,d0
     * 2023-05-19 11:33:30.333 INFO [Proto).zip] Megadrive: DDD, 54, 000035ba   c1c2                    muls     d2,d0
     * 2023-05-19 11:33:30.333 INFO [Proto).zip] Megadrive: DDD, 54, 000035ba   c1c2                    muls     d2,d0
     * 2023-05-19 11:33:30.333 INFO [Proto).zip] Megadrive: DDD, 54, 000035ba   c1c2                    muls     d2,d0
     * 2023-05-19 11:33:30.333 INFO [Proto).zip] Megadrive: DDD, 54, 000035ba   c1c2                    muls     d2,d0
     * 2023-05-19 11:33:30.333 INFO [Proto).zip] Megadrive: DDD, 54, 000035ba   c1c2                    muls     d2,d0
     * 2023-05-19 11:33:30.335 INFO [Proto).zip] MC68000WrapperFastDebug: 00003786   0cb8 00000001 8784      cmpi.l   #$00000001,$8784, mem 0xffff8784: 2, intLevel: 3
     *
     */
//    @Override
//    public int runInstruction() {
//        currentPC = m68k.getPC() & MD_PC_MASK; //needs to be set
//        opcode = m68k.getPrefetchWord();
//        fastDebug.printDebugMaybe();
//        boolean print = (currentPC == 0x319c) || (currentPC == 0x3e84) || (currentPC == 0x3786);
//        if (!busyLoopDetection) {
//            if(!hack && currentPC == 0x3786) {
//                int memb = m68k.readMemoryLong(0xffff8784);
//                LOG.info("{}, mem 0xffff8784: {}, intLevel: {}", getInfo(), th(memb), m68k.getInterruptLevel());
//                if(memb == 2){
//                    m68k.writeMemoryLong(0xffff8784, 1);
//                    hack = true;
//                }
//            }
//            int r = super.runInstruction();
//            if(print){
//                int memb = m68k.readMemoryLong(0xffff8784);
//                LOG.info("{}, mem 0xffff8784: {}, intLevel: {}", getInfo(), th(memb), m68k.getInterruptLevel());
//            }
//            return r;
//        }
//
//        return fastDebug.isBusyLoop(currentPC, opcode) + super.runInstruction();
//    }
}
