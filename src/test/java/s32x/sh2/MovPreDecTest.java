package s32x.sh2;

import omegadrive.SystemLoader;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static omegadrive.util.Util.th;
import static s32x.dict.S32xDict.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 * <p>
 * MOVBM MOV.B Rm,@–Rn
 * MOVWM MOV.W Rm,@–Rn
 * MOVLM MOV.L Rm,@–Rn
 * <p>
 * Test when n = m
 */
public class MovPreDecTest extends Sh2BaseTest {

    int baseCodeB = 0x2004; //MOV.B R0,@–R0
    int baseCodeW = 0x2005; //MOV.W R0,@–R0
    int baseCodeL = 0x2006; //MOV.L R0,@–R0

    int sdramAddr = (SH2_START_SDRAM_CACHE + 0x124) | SH2_CACHE_THROUGH_OFFSET;
    int memStart = (sdramAddr - 0x10) & SH2_SDRAM_MASK;
    int memLen = 0x20;

    int regNum = 0;

    @BeforeEach
    public void before() {
        super.before();
        MdRuntimeData.newInstance(SystemLoader.SystemType.S32X, SystemProvider.NO_CLOCK);
        MdRuntimeData.setAccessTypeExt(BufferUtil.CpuDeviceAccess.MASTER);
    }

    @Test
    public void testMOVLM() {
        testInternal(Size.LONG, baseCodeL, 0xfc52a85e);
    }

    @Test
    public void testMOVWM() {
        testInternal(Size.WORD, baseCodeW, 0x9a4cd644);
    }

    @Test
    public void testMOVBM() {
        testInternal(Size.BYTE, baseCodeB, 0xd5baf425);
    }

    private void testInternal(Size size, int code, int expHc) {
        byte[] pre = getMemoryChunk(memStart, memLen);
        Assertions.assertEquals(2111290369, Arrays.hashCode(pre)); //all zeros
        ctx.registers[regNum] = sdramAddr;
        switch (size) {
            case BYTE -> sh2.MOVBM(code);
            case WORD -> sh2.MOVWM(code);
            case LONG -> sh2.MOVLM(code);
        }
        byte[] post = getMemoryChunk(memStart, memLen);
        String s = String.format("\nPre : %s\nPost: %s\npostHc: %X, expPostHc: %X",
                Arrays.toString(pre), Arrays.toString(post), Arrays.hashCode(post), expHc);
        Assertions.assertEquals(th(expHc), th(Arrays.hashCode(post)), s);
        Assertions.assertEquals(sdramAddr - size.getByteSize(), ctx.registers[regNum]);
    }

    private byte[] getMemoryChunk(int start, int len) {
        byte[] pre = new byte[len];
        final ByteBuffer sdram = memory.getMemoryDataCtx().sdram;
        int pos = sdram.position();
        sdram.position(start);
        sdram.get(pre, 0, pre.length);
        sdram.position(pos);
        return pre;
    }
}
