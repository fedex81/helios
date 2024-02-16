package s32x.sh2;

import com.google.common.collect.ImmutableMap;
import omegadrive.cpu.CpuFastDebug;
import omegadrive.cpu.CpuFastDebug.DebugMode;
import omegadrive.cpu.CpuFastDebug.PcInfoWrapper;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import s32x.bus.Sh2Bus;
import s32x.dict.S32xDict;
import s32x.util.Md32xRuntimeData;

import java.util.Map;
import java.util.function.Predicate;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class Sh2Debug extends Sh2Impl implements CpuFastDebug.CpuDebugInfoProvider {

    private static final Logger LOG = LogHelper.getLogger(Sh2Debug.class.getSimpleName());

    //00_00_0000 - 00_00_4000 BOOT ROM
    //02_00_0000 - 02_40_0000 ROM
    //04_00_0000 - 04_04_0000 FRAME BUFFER + OVERWRITE
    //06_00_0000 - 06_04_0000 RAM
    //C0_00_0000 - C0_01_0000 CACHE AREA
    public static final Map<Integer, Integer> areaMaskMap = ImmutableMap.<Integer, Integer>builder().
            //boot rom + regs, rom, frame buffer + overwrite, sdram
                    put(0, 0x8000 - 1).put(2, 0x40_0000 - 1).put(4, 0x4_0000 - 1).put(6, 0x4_0000 - 1).
            //cache through
                    put(0x20, 0x8000 - 1).put(0x22, 0x40_0000 - 1).put(0x24, 0x4_0000 - 1).put(0x26, 0x4_0000 - 1).
            //cache access
                    put(0xC0, 0x10000 - 1).build();

    public static final int[] pcAreaMaskMap = new int[S32xDict.SH2_PC_AREAS];

    static {
        for (var e : areaMaskMap.entrySet()) {
            pcAreaMaskMap[e.getKey()] = e.getValue();
        }
    }

    //TODO check, ignoring BRAF (branch far), BSR, BSRF, JMP, JSR
    public static final Predicate<Integer> isBranchNearOpcode = op ->
            (op & 0xFF00) == 0x8900 //bt
                    || (op & 0xFF00) == 0x8B00 //bf
                    || (op & 0xFF00) == 0x8F00 //bf/s
                    || (op & 0xFF00) == 0x8D00 //bt/s
                    || (op & 0xF000) == 0xA000 //bra
            ;

    public static final Predicate<Integer> isCmpOpcode = op ->
            (op & 0xF00F) == 0x3000 //cmp/eq
                    || (op & 0xF00F) == 0x3000 //CMP/EQ Rm,Rn
                    || (op & 0xF00F) == 0x3002 //CMP/HS Rm,Rn
                    || (op & 0xF00F) == 0x3003 //CMP/GE Rm,Rn
                    || (op & 0xF00F) == 0x3006 //CMP/HI Rm,Rn
                    || (op & 0xF00F) == 0x3007 //CMP/GT Rm,Rn
                    || (op & 0xFF00) == 0x8800 //cmp/eq #imm
                    || (op & 0xF0FF) == 0x4015 //CMP/PL Rn
                    || (op & 0xF0FF) == 0x4011 //CMP/PZ Rn
                    || (op & 0xF00F) == 0x200C //CMP/STR Rm,Rn
            ;

    public static final Predicate<Integer> isTstOpcode = op ->
            (op & 0xF00F) == 0x2008 //tst Rm,Rn
                    || (op & 0xFF00) == 0xC800 //TST#imm,R0
                    || (op & 0xFF00) == 0xCC00 //TST.B#imm,@(R0,GBR)
            ;

    public static final Predicate<Integer> isMovOpcode = op ->
            (op & 0xF00F) == 0x6002 //mov.l @Rm, Rn
                    || (op & 0xF00F) == 0x6001 //mov.w @Rm, Rn
                    || (op & 0xF00F) == 0x6000 //mov.b @Rm, Rn
                    || (op & 0xFF00) == 0xC600 //mov.l @(disp,GBR),R0
                    || (op & 0xFF00) == 0xC500 //MOV.W@(disp,GBR),R0
                    || (op & 0xFF00) == 0xC400 //MOV.B@(disp,GBR),R0
                    || (op & 0xFF00) == 0x8400 //MOV.B@(disp,Rm),R0
                    || (op & 0xFF00) == 0x8500 //MOV.W@(disp,Rm),R0
                    || (op & 0xF000) == 0x5000 //MOV.L@(disp,Rm),R0
                    || (op & 0xF000) == 0x9000 //MOV.W@(disp,PC),R0
                    || (op & 0xF000) == 0xD000 //MOV.L@(disp,PC),R0
            ;

    private static final Predicate<Integer> isNopOpcode = op -> op == 9;
    public static final Predicate<Integer> isLoopOpcode = isNopOpcode.or(isBranchNearOpcode).
            or(isCmpOpcode).or(isTstOpcode).or(isMovOpcode);
    public static final Predicate<Integer> isIgnoreOpcode =
            op -> (op & 0xF0FF) == 0x4010 //dt
            ;

    private final CpuFastDebug[] fastDebug = new CpuFastDebug[2];

    private static PcInfoWrapper[][] piw;

    public Sh2Debug(Sh2Bus memory) {
        super(memory);
        LOG.warn("Sh2 cpu: creating debug instance");
        init();
    }

    @Override
    public void init() {
        fastDebug[0] = new CpuFastDebug(this, createContext());
        fastDebug[1] = new CpuFastDebug(this, createContext());
        fastDebug[0].debugMode = DebugMode.NEW_INST_ONLY;
        fastDebug[1].debugMode = DebugMode.NEW_INST_ONLY;
        piw = fastDebug[0].pcInfoWrapper;
    }

//    @Override
//    public void run(Sh2Context ctx) {
//        try {
//            super.run(ctx);
//        } catch (Exception e) {
//            e.printStackTrace();
//            Util.waitForever();
//        }
//    }

    @Override
    public final void printDebugMaybe(int opcode) {
        ctx.opcode = opcode;
        final int n = ctx.cpuAccess.ordinal();
//        fastDebug[n].isBusyLoop(ctx.PC & 0x0FFF_FFFF, ctx.opcode);
        fastDebug[n].printDebugMaybe();
        if ((ctx.PC & 1) > 0) {
            LOG.error("Odd PC: {}", th(ctx.PC));
            throw new RuntimeException("Odd PC");
        }
    }

    public static CpuFastDebug.CpuDebugContext createContext() {
        CpuFastDebug.CpuDebugContext ctx = new CpuFastDebug.CpuDebugContext(areaMaskMap);
        ctx.pcAreaShift = S32xDict.SH2_PC_AREA_SHIFT;
        ctx.isLoopOpcode = isLoopOpcode;
        ctx.isIgnoreOpcode = isIgnoreOpcode;
        return ctx;
    }

    @Override
    public String getInstructionOnly(int pc, int opcode) {
        return Sh2Helper.getInstString(ctx.sh2TypeCode, pc, opcode);
    }

    /**
     * Slow, use with care
     */
    @Override
    public String getInstructionOnly(int pc) {
        assert pc != ctx.PC;
        int delay = Md32xRuntimeData.getCpuDelayExt();
        String res = Sh2Helper.getInstString(ctx.sh2TypeCode, pc, memory.read16(pc));
        Md32xRuntimeData.resetCpuDelayExt(delay);
        return res;
    }

    @Override
    public String getCpuState(String head) {
        return Sh2Helper.toDebuggingString(ctx);
    }

    @Override
    public int getPc() {
        return ctx.PC;
    }

    @Override
    public int getOpcode() {
        return ctx.opcode;
    }
}