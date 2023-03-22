package s32x.sh2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.J2CoreTest;
import s32x.bus.Sh2Bus;
import s32x.util.Md32xRuntimeData;
import s32x.util.S32xUtil;

import java.nio.ByteBuffer;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * NOTE: havent seen any code doing it with mova
 */
public class MovDelayBranchTest {

    private static boolean sh2Debug = false;

    static ByteBuffer rom = ByteBuffer.allocate(0x1000);
    private Sh2 sh2;
    private Sh2Context ctx;

    @BeforeEach
    public void before() {
        Sh2.Sh2Config.reset(Sh2.Sh2Config.DEFAULT_CONFIG);
        Sh2Bus memory = J2CoreTest.getMemory(rom);
        sh2 = J2CoreTest.getSh2Interpreter(memory, sh2Debug);
        ctx = J2CoreTest.createContext(S32xUtil.CpuDeviceAccess.MASTER, memory);
        rom.putInt(0, 0x10); //PC
        rom.putInt(4, 0xF0); //SP
        Md32xRuntimeData.newInstance();
        Sh2Context.burstCycles = 1;
        sh2.reset(ctx);
        System.out.println("Reset, PC: " + ctx.PC + ", SP: " + ctx.registers[15]);
        Assertions.assertFalse(Sh2.Sh2Config.get().drcEn);
    }

    /**
     * After Burner
     * M 0600a634	a35a	bra H'0600acec
     * M 0600a636	960f	mov.w @(H'0600a658), R6  // r6 = 0xFF9
     * M 0600acec	0102	stc SR, R1
     */
    @Test
    public void testMOVWI_delayBranch() {
        int stopHere = 0x80;
        int expR6 = 0x1981;
        rom.putShort(0x9E, (short) expR6);

        rom.putShort(0x10, (short) 0xa035); //bra 0x7E
        rom.putShort(0x12, (short) 0x960f); //mov.w  @(H'9e), R6 (9e = 7e + 2 + (f << 1))
        rom.putShort(0x7E, (short) 0x0102); //stc SR, R1

        int expR1 = 0xF0;
        int cnt = 0;
        do {
            ctx.cycles = 1;
            sh2.run(ctx);
            cnt++;
        } while (ctx.PC < stopHere);
        Assertions.assertEquals(expR1, ctx.registers[1]);
        Assertions.assertEquals(expR6, ctx.registers[6]);
    }

    /**
     * soul start (not actually the real disasm)
     * M 06000902	b007	bsr H'06000914
     * M 06000904	d108	mov.l @(H'06000928), R1
     * M 06000914	0102	stc SR, R1
     */
    @Test
    public void testMOVLI_delayBranch() {
        int stopHere = 0x80;
        int expR6 = 0x1981_ABCD;
        rom.putInt(0x90, expR6);

        rom.putShort(0x10, (short) 0xa035); //bra 0x7E
        rom.putShort(0x12, (short) 0xD604); //mov.l  @(H'90), R6 (90 = 7e + 2 + (4 << 2))
        rom.putShort(0x7E, (short) 0x0102); //stc SR, R1

        int expR1 = 0xF0;
        int cnt = 0;
        do {
            ctx.cycles = 1;
            sh2.run(ctx);
            cnt++;
        } while (ctx.PC < stopHere);
        Assertions.assertEquals(expR1, ctx.registers[1]);
        Assertions.assertEquals(expR6, ctx.registers[6]);
    }
}
