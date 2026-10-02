package mcd.dict;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.util.function.BiConsumer;

import static mcd.dict.MegaCdDict.BitRegDef.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_CDC_MODE;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_RESET;
import static mcd.dict.MegaCdDict.SharedBitDef.*;
import static mcd.util.McdRegBitUtil.setBitDefInternal;
import static mcd.util.McdRegBitUtil.setSharedBitsOtherCpu;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.*;

public class MegaCdRegWriteHandlers {

    private final static Logger LOG = LogHelper.getLogger(MegaCdRegWriteHandlers.class.getSimpleName());

    public static final BiConsumer<MegaCdMemoryContext, Integer>[][] setByteHandlersMain = new BiConsumer[8][2];
    public static final BiConsumer<MegaCdMemoryContext, Integer>[][] setByteHandlersSub = new BiConsumer[8][2];

    /**
     * SUB
     **/
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteLSBReg0_S = (ctx, d) -> {
        assert d < 2; //Version bits only write 0
        setBitDefInternal(ctx, SUB_M68K, RES0, d);
    };
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteMSBReg0_S = (ctx, d) -> {
        setBitDefInternal(ctx, SUB_M68K, LEDR, d);
        setBitDefInternal(ctx, SUB_M68K, LEDG, d);
    };

    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteMSBReg4_S = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(SUB_M68K);
        //mcd-ver: DSR, EDT can only be set to 0 by SUB
        writeBufferRaw(buff, MCD_CDC_MODE.addr, d & 7, Size.BYTE);
        setSharedBitsOtherCpu(ctx, SUB_M68K, d & 7, DD0, DD1, DD2, DSR, EDT);
    };

    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteLSBReg4_S = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(SUB_M68K);
        writeBufferRaw(buff, MCD_CDC_MODE.addr + 1, d & 0x1f, Size.BYTE); //mcd-verificator
        writeBufferRaw(ctx.getGateSysRegs(M68K), MCD_CDC_MODE.addr + 1, 0, Size.BYTE);
    };

    /**
     * MAIN
     **/
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteLSBReg0_M = (ctx, d) -> {
        setBitDefInternal(ctx, M68K, SRES, d);
        setBitDefInternal(ctx, M68K, SBRQ, d);
    };
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteMSBReg0_M = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(M68K);
        {
            if (assertionsEnabled) {
                int now = readBuffer(buff, MCD_RESET.addr, Size.BYTE);
                //Flux sets IEN2 = 1
                //TODO check: IEN2 write only 0 ??
                int mask = IEN2.getBitMask();
                boolean stateChanged = ((d ^ now) & mask) != 0;
                if (stateChanged) {
                    LogHelper.logWarnOnce(LOG, "{} illegal IEN2 write state changed: {}->{}",
                            M68K, now & IEN2.getBitMask(),
                            d & IEN2.getBitMask());
                    //NOTE just info, we are not writing IEN2 anyway
                }
                mask = IFL2.getBitMask();
                boolean stateChanged1to0 = (~d & now & mask) != 0;
                if (stateChanged1to0) {
                    LogHelper.logWarnOnce(LOG, "{} IFL2 1->0 transition, should be ignored?"); //TODO
                }
            }
        }
        //mcd-ver main sets IFL2 to 0
        setBitDefInternal(ctx, M68K, IFL2, d);
    };

    static {
        setByteHandlersMain[MCD_RESET.addr] = new BiConsumer[]{setByteMSBReg0_M, setByteLSBReg0_M};
        setByteHandlersSub[MCD_RESET.addr] = new BiConsumer[]{setByteMSBReg0_S, setByteLSBReg0_S};
        setByteHandlersSub[MCD_CDC_MODE.addr] = new BiConsumer[]{setByteMSBReg4_S, setByteLSBReg4_S};
    }
}