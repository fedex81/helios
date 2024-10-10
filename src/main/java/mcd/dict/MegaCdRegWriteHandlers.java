package mcd.dict;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.util.function.BiConsumer;

import static mcd.dict.MegaCdDict.BitRegDef.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdDict.SharedBitDef.*;
import static mcd.util.McdRegBitUtil.*;
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

    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteLSBReg2_S = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(SUB_M68K);
        int now = readBuffer(buff, MCD_MEM_MODE.addr + 1, Size.BYTE);
        int prevRet = now & RET.getBitMask();
        int prevMode = now & MODE.getBitMask();
        int prevDmna = now & DMNA.getBitMask();
        //Terminator_E
        if (assertionsEnabled) {
            int newDmna = d & DMNA.getBitMask();
            boolean dmnaWriteOk = newDmna == 0 || (newDmna > 0 && prevDmna > 0); //DMNA write only 0
            if (!dmnaWriteOk) {
                LogHelper.logWarnOnceForce(LOG, "{} illegal DMNA write: {}->{}", SUB_M68K, prevDmna, newDmna);
            }
        }

        if (prevMode == 0 && prevRet == 1 && prevDmna == 0) {
            d |= RET.getBitMask();
        } else {
            setSharedBitInternal(ctx, SUB_M68K, RET, d);
        }
        setSharedBitInternal(ctx, SUB_M68K, MODE, d);
        setBitDefInternal(ctx, SUB_M68K, PM0, d);
        setBitDefInternal(ctx, SUB_M68K, PM1, d);
    };
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteMSBReg2_S = (ctx, d) -> {
        //Write protected are writable according to mcd-verificator
        MegaCdDict.writeReg(ctx, SUB_M68K, MCD_MEM_MODE, MCD_MEM_MODE.addr, d, Size.BYTE); //WP0-7 write protected bits
        MegaCdDict.writeReg(ctx, M68K, MCD_MEM_MODE, MCD_MEM_MODE.addr, d, Size.BYTE); //main
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
            int now = readBuffer(buff, MCD_RESET.addr, Size.BYTE);
            //Flux sets IEN2 = 1
            boolean ien2ok = (d & IEN2.getBitMask()) > 0 ? (now & IEN2.getBitMask()) > 0 : true; //IEN2 write only 0
            if (!ien2ok) {
                LogHelper.logWarnOnceForce(LOG, "{} illegal IEN2 write: {}->{}", M68K, now & IEN2.getBitMask(),
                        d & IEN2.getBitMask());
                d &= ~IEN2.getBitMask();
            }
        }
        //mcd-ver main sets IFL2 to 0
        setBitDefInternal(ctx, M68K, IFL2, d);
    };

    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteLSBReg2_M = (ctx, d) -> {
        var buff = ctx.getGateSysRegs(M68K);
        int now = readBuffer(buff, MCD_MEM_MODE.addr + 1, Size.BYTE);
        int prevRet = now & RET.getBitMask();
        int prevMode = now & MODE.getBitMask();
        int prevDmna = now & DMNA.getBitMask();
        {
            int newRet = d & RET.getBitMask();
            int newMode = d & MODE.getBitMask();
            boolean retWriteOk = newRet == 0 || (newRet > 0 && prevRet > 0); //RET write only 0
            //star wars_E_Demo sets mode = 1
            boolean modeWriteOk = newMode == 0 || (newMode > 0 && prevMode > 0); //MODE write only 0
            if (!retWriteOk) {
                LogHelper.logWarnOnceForce(LOG, "{} illegal RET write: {}->{}", M68K, prevRet, newRet);
                d &= ~RET.getBitMask();
            }
            if (!modeWriteOk) {
                LogHelper.logWarnOnceForce(LOG, "{} illegal MODE write: {}->{}", M68K, prevMode, newMode);
                d &= ~MODE.getBitMask();
            }
        }
        //2M_SUB, RET == 0, DMNA > 0 -> main cannot modify DMNA, SUB needs to release WRAM first
        if (prevMode != 0 || prevRet != 0 || prevDmna <= 0) {
            setSharedBitInternal(ctx, M68K, DMNA, d);
        }
        setSharedBitInternal(ctx, M68K, RET, d);
        setSharedBitInternal(ctx, M68K, MODE, d);
        setBitDefInternal(ctx, M68K, BK0, d);
        setBitDefInternal(ctx, M68K, BK1, d);
    };
    private final static BiConsumer<MegaCdMemoryContext, Integer> setByteMSBReg2_M = (ctx, d) -> {
        writeBufferRaw(ctx.getGateSysRegs(M68K), MCD_MEM_MODE.addr, d, Size.BYTE); //WP0-7 write protected bits
        writeBufferRaw(ctx.getGateSysRegs(SUB_M68K), MCD_MEM_MODE.addr, d, Size.BYTE); //sub too
    };

    static {
        setByteHandlersMain[MCD_RESET.addr] = new BiConsumer[]{setByteMSBReg0_M, setByteLSBReg0_M};
        setByteHandlersMain[MCD_MEM_MODE.addr] = new BiConsumer[]{setByteMSBReg2_M, setByteLSBReg2_M};
        setByteHandlersSub[MCD_RESET.addr] = new BiConsumer[]{setByteMSBReg0_S, setByteLSBReg0_S};
        setByteHandlersSub[MCD_MEM_MODE.addr] = new BiConsumer[]{setByteMSBReg2_S, setByteLSBReg2_S};
        setByteHandlersSub[MCD_CDC_MODE.addr] = new BiConsumer[]{setByteMSBReg4_S, setByteLSBReg4_S};
    }
}