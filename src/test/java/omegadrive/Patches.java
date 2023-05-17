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
