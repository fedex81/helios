package mcd.dict;

import omegadrive.util.Size;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_MEM_MODE;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_RESET;
import static mcd.dict.MegaCdMemoryContext.SharedBit.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.*;

public class MegaCdRegWriteHandlers {

    public static final BiConsumer<MegaCdMemoryContext, Integer>[][] setByteHandlersMain = new BiConsumer[8][2];
    public static final BiConsumer<MegaCdMemoryContext, Integer>[][] setByteHandlersSub = new BiConsumer[8][2];

    /**
     * SUB
     **/
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteLSBReg0_S = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(SUB_M68K);
        assert d < 2; //Version bits only write 0
        setBitInternal(buff, MCD_RESET.addr + 1, 0, d); //RES0
    };
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteMSBReg0_S = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(SUB_M68K);
        setBitInternal(buff, MCD_RESET.addr, 0, d); //LEDR
        setBitInternal(buff, MCD_RESET.addr, 1, d); //LEDG
    };

    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteLSBReg2_S = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(SUB_M68K);
        if (assertionsEnabled) {
            int now = readBuffer(buff, MCD_MEM_MODE.addr + 1, Size.BYTE);
            assert (d & 2) == 0 || ((d & 2) > 0 && (now & 2) > 0); //DMNA write only 0
        }
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 0, d); //RET
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 2, d); //MODE
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 6, d); //BK0
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 7, d); //BK1

        ctx.setSharedBit(SUB_M68K, RET, d & 1);
        ctx.setSharedBit(SUB_M68K, MODE, (d >> 2) & 1);
    };
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteMSBReg2_S = (ctx, d) -> {
        assert d == 0; //Write protected bits only write 0
        var buff = ctx.getGateSysRegs(SUB_M68K);
        writeBufferRaw(buff, MCD_MEM_MODE.addr, d, Size.BYTE); //WP0-7 write protected bits
    };
    /**
     * MAIN
     **/
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteLSBReg0_M = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(M68K);
        setBitInternal(buff, MCD_RESET.addr + 1, 0, d); //SRES
        setBitInternal(buff, MCD_RESET.addr + 1, 1, d); //SBRQ
    };
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteMSBReg0_M = (ctx, d) -> {
        assert (d & 128) == 0; //IEN2 write only 0
        var buff = ctx.getGateSysRegs(M68K);
        setBitInternal(buff, MCD_RESET.addr, 0, d); //IFL2
    };

    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteLSBReg2_M = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(M68K);
        if (assertionsEnabled) {
            int now = readBuffer(buff, MCD_MEM_MODE.addr + 1, Size.BYTE);
            assert (d & 1) == 0 || ((d & 1) > 0 && (now & 1) > 0); //RET write only 0
            assert (d & 4) == 0 || ((d & 4) > 0 && (now & 4) > 0); //MODE write only 0
        }
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 0, d); //RET
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 1, d); //DMNA
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 2, d); //MODE
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 3, d); //PM0
        setBitInternal(buff, MCD_MEM_MODE.addr + 1, 4, d); //PM1
        ctx.setSharedBit(M68K, RET, d & 1);
        ctx.setSharedBit(M68K, DMNA, (d >> 1) & 1);
        ctx.setSharedBit(M68K, MODE, (d >> 2) & 1);
    };
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteMSBReg2_M = (ctx, d) -> {
        writeBufferRaw(ctx.getGateSysRegs(M68K), MCD_MEM_MODE.addr, d, Size.BYTE); //WP0-7 write protected bits
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