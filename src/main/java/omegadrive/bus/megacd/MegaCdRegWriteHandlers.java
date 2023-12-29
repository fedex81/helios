package omegadrive.bus.megacd;

import omegadrive.util.Size;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import static omegadrive.bus.megacd.MegaCdDict.RegSpecMcd.MCD_MEM_MODE;
import static omegadrive.bus.megacd.MegaCdDict.RegSpecMcd.MCD_RESET;
import static omegadrive.util.S32xUtil.setBitVal;
import static omegadrive.util.S32xUtil.writeBuffer;

public class MegaCdRegWriteHandlers {

    public static final BiConsumer<ByteBuffer, Integer>[][] setByteHandlersMain = new BiConsumer[8][2];
    public static final BiConsumer<ByteBuffer, Integer>[][] setByteHandlersSub = new BiConsumer[8][2];

    /**
     * SUB
     **/
    private final static BiConsumer<ByteBuffer, Integer> setByteLSBReg0_S = (buff, d) -> {
        setBitInternal(buff, MCD_RESET.addr + 1, 0, d); //RES0
    };
    private final static BiConsumer<ByteBuffer, Integer> setByteMSBReg0_S = (buff, d) -> {
        setBitInternal(buff, MCD_RESET.addr, 0, d); //LEDR
        setBitInternal(buff, MCD_RESET.addr, 1, d); //LEDG
    };

    private final static BiConsumer<ByteBuffer, Integer> setByteLSBReg2_S = (buff, d) -> {
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 0, d); //DMNA
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 6, d); //BK0
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 7, d); //BK1
    };
    private final static BiConsumer<ByteBuffer, Integer> setByteMSBReg2_S = (buff, d) -> {
        writeBuffer(buff, MCD_MEM_MODE.addr, d, Size.BYTE); //WP0-7 write protected bits
    };
    /**
     * MAIN
     **/
    private final static BiConsumer<ByteBuffer, Integer> setByteLSBReg0_M = (buff, d) -> {
        setBitInternal(buff, MCD_RESET.addr + 1, 0, d); //SRES
        setBitInternal(buff, MCD_RESET.addr + 1, 1, d); //SBRQ
    };
    private final static BiConsumer<ByteBuffer, Integer> setByteMSBReg0_M = (buff, d) -> {
        setBitInternal(buff, MCD_RESET.addr, 0, d); //IFL2
    };

    private final static BiConsumer<ByteBuffer, Integer> setByteLSBReg2_M = (buff, d) -> {
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 0, d); //RET
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 2, d); //MODE
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 3, d); //PM0
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 4, d); //PM1
    };
    private final static BiConsumer<ByteBuffer, Integer> setByteMSBReg2_M = (buff, d) -> {
        writeBuffer(buff, MCD_MEM_MODE.addr, d, Size.BYTE); //WP0-7 write protected bits
    };

    private static void setBitInternal(ByteBuffer buff, int regAddr, int bitPos, int data) {
        assert bitPos < 8;
        setBitVal(buff, regAddr, bitPos, (data >> bitPos) & 1, Size.BYTE);
    }


    static {
        setByteHandlersMain[MCD_RESET.addr] = new BiConsumer[]{setByteMSBReg0_M, setByteLSBReg0_M};
        setByteHandlersMain[MCD_MEM_MODE.addr] = new BiConsumer[]{setByteMSBReg2_M, setByteLSBReg2_M};
        setByteHandlersSub[MCD_RESET.addr] = new BiConsumer[]{setByteMSBReg0_S, setByteLSBReg0_S};
        setByteHandlersSub[MCD_MEM_MODE.addr] = new BiConsumer[]{setByteMSBReg2_S, setByteLSBReg2_S};
    }
}
