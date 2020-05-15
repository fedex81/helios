package omegadrive.bus.gen;

/*
   basic, incomplete SSP160x (SSP1601?) interpreter
   with SVP memory controller emu

   (c) Copyright 2008, Grazvydas "notaz" Ignotas
   Free for non-commercial use.

   For commercial use, separate licencing terms must be obtained.

   Modified for Genesis Plus GX (Eke-Eke), added big endian support, fixed mode & addr
*/

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static omegadrive.bus.gen.Ssp16.Ssp16Reg.*;


/*
 * Register info
 *
 * 0. "-"
 *   size: 16
 *   desc: Constant register with all bits set (0xffff).
 *
 * 1. "X"
 *   size: 16
 *   desc: Generic register. When set, updates P (P = X * Y * 2)
 *
 * 2. "Y"
 *   size: 16
 *   desc: Generic register. When set, updates P (P = X * Y * 2)
 *
 * 3. "A"
 *   size: 32
 *   desc: Accumulator.
 *
 * 4. "ST"
 *   size: 16
 *   desc: Status register. From MAME: bits 0-9 are CONTROL, other FLAG
 *     fedc ba98 7654 3210
 *       210 - RPL (?)       "Loop size". If non-zero, makes (rX+) and (rX-) respectively
 *                           modulo-increment and modulo-decrement. The value shows which
 *                           power of 2 to use, i.e. 4 means modulo by 16.
 *                           (e: fir16_32.sc, IIR_4B.SC, DECIM.SC)
 *       43  - RB (?)
 *       5   - GP0_0 (ST5?)  Changed before acessing PM0 (affects banking?).
 *       6   - GP0_1 (ST6?)  Cleared before acessing PM0 (affects banking?). Set after.
 *                           datasheet says these (5,6) bits correspond to hardware pins.
 *       7   - IE (?)        Not directly used by SVP code (never set, but preserved)?
 *       8   - OP (?)        Not used by SVP code (only cleared)? (MAME: saturated value
 *                           (probably means clamping? i.e. 0x7ffc + 9 -> 0x7fff))
 *       9   - MACS (?)      Not used by SVP code (only cleared)? (e: "mac shift")
 *       a   - GPI_0         Interrupt 0 enable/status?
 *       b   - GPI_1         Interrupt 1 enable/status?
 *       c   - L             L flag. Carry?
 *       d   - Z             Zero flag.
 *       e   - OV            Overflow flag.
 *       f   - N             Negative flag.
 *     seen directly changing code sequences:
 *       ldi ST, 0      ld  A, ST     ld  A, ST     ld  A, ST     ldi st, 20h
 *       ldi ST, 60h    ori A, 60h    and A, E8h    and A, E8h
 *                      ld  ST, A     ld  ST, A     ori 3
 *                                                  ld  ST, A
 *
 * 5. "STACK"
 *   size: 16
 *   desc: hw stack of 6 levels (according to datasheet)
 *
 * 6. "PC"
 *   size: 16
 *   desc: Program counter.
 *
 * 7. "P"
 *   size: 32
 *   desc: multiply result register. P = X * Y * 2
 *         probably affected by MACS bit in ST.
 *
 * 8. "PM0" (PM from PMAR name from Tasco's docs)
 *   size: 16?
 *   desc: Programmable Memory access register.
 *         On reset, or when one (both?) GP0 bits are clear,
 *         acts as status for XST, mapped at 015004 at 68k side:
 *         bit0: ssp has written something to XST (cleared when 015004 is read)
 *         bit1: 68k has written something through a1500{0|2} (cleared on PM0 read)
 *
 * 9. "PM1"
 *   size: 16?
 *   desc: Programmable Memory access register.
 *         This reg. is only used as PMAR.
 *
 * 10. "PM2"
 *   size: 16?
 *   desc: Programmable Memory access register.
 *         This reg. is only used as PMAR.
 *
 * 11. "XST"
 *   size: 16?
 *   desc: eXternal STate. Mapped to a15000 and a15002 at 68k side.
 *         Can be programmed as PMAR? (only seen in test mode code)
 *         Affects PM0 when written to?
 *
 * 12. "PM4"
 *   size: 16?
 *   desc: Programmable Memory access register.
 *         This reg. is only used as PMAR. The most used PMAR by VR.
 *
 * 13. (unused by VR)
 *
 * 14. "PMC" (PMC from PMAC name from Tasco's docs)
 *   size: 32?
 *   desc: Programmable Memory access Control. Set using 2 16bit writes,
 *         first address, then mode word. After setting PMAC, PMAR sould
 *         be blind accessed (ld -, PMx  or  ld PMx, -) to program it for
 *         reading and writing respectively.
 *         Reading the register also shifts it's state (from "waiting for
 *         address" to "waiting for mode" and back). Reads always return
 *         address related to last PMx register accressed.
 *         (note: addresses do not wrap).
 *
 * 15. "AL"
 *   size: 16
 *   desc: Accumulator Low. 16 least significant bits of accumulator.
 *         (normally reading acc (ld X, A) you get 16 most significant bits).
 *
 *
 * There are 8 8-bit pointer registers rX. r0-r3 (ri) point to RAM0, r4-r7 (rj) point to RAM1.
 * They can be accessed directly, or 2 indirection levels can be used [ (rX), ((rX)) ],
 * which work similar to * and ** operators in C, only they use different memory banks and
 * ((rX)) also does post-increment. First indirection level (rX) accesses RAMx, second accesses
 * program memory at address read from (rX), and increments value in (rX).
 *
 * r0,r1,r2,r4,r5,r6 can be modified [ex: ldi r0, 5].
 * 3 modifiers can be applied (optional):
 *  + : post-increment [ex: ld a, (r0+) ]. Can be made modulo-increment by setting RPL bits in ST.
 *  - : post-decrement. Can be made modulo-decrement by setting RPL bits in ST (not sure).
 *  +!: post-increment, unaffected by RPL (probably).
 * These are only used on 1st indirection level, so things like [ld a, ((r0+))] and [ld X, r6-]
 * ar probably invalid.
 *
 * r3 and r7 are special and can not be changed (at least Samsung samples and SVP code never do).
 * They are fixed to the start of their RAM banks. (They are probably changeable for ssp1605+,
 * Samsung's old DSP page claims that).
 * 1 of these 4 modifiers must be used (short form direct addressing?):
 *  |00: RAMx[0] [ex: (r3|00), 0] (based on sample code)
 *  |01: RAMx[1]
 *  |10: RAMx[2] ? maybe 10h? accortding to Div_c_dp.sc, 2
 *  |11: RAMx[3]
 *
 *
 * Instruction notes
 *
 * ld a, * doesn't affect flags! (e: A_LAW.SC, Div_c_dp.sc)
 *
 * mld (rj), (ri) [, b]
 *   operation: A = 0; P = (rj) * (ri)
 *   notes: based on IIR_4B.SC sample. flags? what is b???
 *
 * mpya (rj), (ri) [, b]
 *   name: multiply and add?
 *   operation: A += P; P = (rj) * (ri)
 *
 * mpys (rj), (ri), b
 *   name: multiply and subtract?
 *   notes: not used by VR code.
 *
 * mod cond, op
 *   mod cond, shr  does arithmetic shift
 *
 * 'ld -, AL' and probably 'ld AL, -' are for dummy assigns
 *
 * memory map:
 * 000000 - 1fffff   ROM, accessable by both
 * 200000 - 2fffff   unused?
 * 300000 - 31ffff   DRAM, both
 * 320000 - 38ffff   unused?
 * 390000 - 3907ff   IRAM. can only be accessed by ssp?
 * 390000 - 39ffff   similar mapping to "cell arrange" in Sega CD, 68k only?
 * 3a0000 - 3affff   similar mapping to "cell arrange" in Sega CD, a bit different
 *
 * 30fe02 - 0 if SVP busy, 1 if done (set by SVP, checked and cleared by 68k)
 * 30fe06 - also sync related.
 * 30fe08 - job number [1-12] for SVP. 0 means no job. Set by 68k, read-cleared by SVP.
 *
 * + figure out if 'op A, P' is 32bit (nearly sure it is)
 * * does mld, mpya load their operands into X and Y?
 * * OP simm
 *
 * Assumptions in this code
 *   P is not directly writeable
 *   flags correspond to full 32bit accumulator
 *   only Z and N status flags are emulated (others unused by SVP)
 *   modifiers for 'OP a, ri' are ignored (invalid?/not used by SVP)
 *   'ld d, (a)' loads from program ROM
 */
public class Ssp16Impl implements Ssp16 {
    private final static Logger LOG = LogManager.getLogger(Ssp16Impl.class.getSimpleName());
    /*#define USE_DEBUGGER*/
    public static final boolean LOG_SVP = false;
    /* flags */
    static final int SSP_FLAG_L = (1 << 0xc);
    static final int SSP_FLAG_Z = (1 << 0xd);
    static final int SSP_FLAG_V = (1 << 0xe);
    static final int SSP_FLAG_N = (1 << 0xf);

    static int PC;
    static int g_cycles;
    static int running = 0;
    static int last_iram = 0;
    static int[] bpts = new int[10]; // = { 0, };
    static char[] buffo = new char[64]; //{0,};
    public ssp1601_t ssp = null;
    public svp_t svp = null;
    public cart cart = null;
    /* 0 */
    ssp_reg_t rX; //.h;
    ssp_reg_t rY; //.h;
    ssp_reg_t rA; //.h;
    ssp_reg_t rST; //.h;  /* 4 */
    ssp_reg_t rSTACK; //.h;
    ssp_reg_t rPC; //.h;
    ssp_reg_t rP;
    ssp_reg_t rPM0; //.h;  /* 8 */

    //    int[] rIJ ; //= ssp.ptr.r;
    ssp_reg_t rPM1; //.h;
    ssp_reg_t rPM2; //.h;
    ssp_reg_t rXST; //.h;
    ssp_reg_t rPM4; //.h;  /* 12 */
    /* 13 */
    ssp_reg_t rPMC; /* will keep addr in .h, mode in .l */
    ssp_reg_t rAL; //.l;
    ssp_reg_t rA32; //.v;
    Set<Integer> pcSet = new HashSet<>();
    private SvpBus svpBus;

    public static Ssp16Impl createInstance(SvpBus svpBus, ssp1601_t ssp, svp_t svp, cart cart) {
        Ssp16Impl s = new Ssp16Impl();
        s.ssp = ssp;
        s.cart = cart;
        s.svp = svp;
        s.svpBus = svpBus;
        s.init();
        return s;
    }

    static int get_inc(int mode) {
        int inc = (mode >> 11) & 7;
        if (inc != 0) {
            if (inc != 7) inc--;
            inc = 1 << inc; // 0 1 2 4 8 16 32 128
            if ((mode & 0x8000) > 0) inc = -inc; // decrement mode
        }
        return inc;
    }

    static void debug_dump2file(String fname, int[] mem, int len) {
        //TODO
    }

    private void init() {
        /* 0 */
        rX = ssp.gr[SSP_X.ordinal()];
        rY = ssp.gr[SSP_Y.ordinal()];
        rA = ssp.gr[SSP_A.ordinal()];
        rST = ssp.gr[SSP_ST.ordinal()];  /* 4 */
        rSTACK = ssp.gr[SSP_STACK.ordinal()];
        rPC = ssp.gr[SSP_PC.ordinal()];
        rP = ssp.gr[SSP_P.ordinal()];
        rPM0 = ssp.gr[SSP_PM0.ordinal()];  /* 8 */
        rPM1 = ssp.gr[SSP_PM1.ordinal()];
        rPM2 = ssp.gr[SSP_PM2.ordinal()];
        rXST = ssp.gr[SSP_XST.ordinal()];
        rPM4 = ssp.gr[SSP_PM4.ordinal()];  /* 12 */
        /* 13 */
        rPMC = ssp.gr[SSP_PMC.ordinal()];   /* will keep addr in , mode in .l */
        rAL = ssp.gr[SSP_A.ordinal()]; //.l
        rA32 = ssp.gr[SSP_A.ordinal()]; //.v
    }

    int IJind(int op) {
        return (((op >> 6) & 4) | (op & 3));
    }

    int GET_PC() {
        return PC;
    }

    int GET_PPC_OFFS() {
        return PC - 1;
    }

    int SET_PC(int d) {
        PC = d & 0xFFFF; //??
        return PC;
    }

    //#define REG_READ(r) (((r) <= 4) ? ssp->gr[r].byte.h : read_handlers[r]())
    int REG_READ(int r) {
        if (r <= 4) {
            return ssp.gr[r].h;
        }
        return invokeReadHandler(r);
    }

    void REG_WRITE(int r, int d) {
        int r1 = r;
        if (r1 >= 4) {
            invokeWriteHandler(r1, d);
//                write_handlers[r1] (d);
        } else if (r1 > 0) {
            ssp.gr[r1].setH(d);
        }
    }

    /* update ZN according to 32bit ACC. */
    void UPD_ACC_ZN() {
        rST.setH(rST.h & ~(SSP_FLAG_Z | SSP_FLAG_N));
        if (rA32.v == 0) rST.setH(rST.h | SSP_FLAG_Z);
        else rST.setH(rST.h | ((rA32.v >> 16) & SSP_FLAG_N));
    }

    /* it seems SVP code never checks for L and OV, so we leave them out. */
    /* rST |= (t>>4)&SSP_FLAG_L; */
    void UPD_LZVN() {
        rST.setH(rST.h & ~(SSP_FLAG_L | SSP_FLAG_Z | SSP_FLAG_V | SSP_FLAG_N));
        if (rA32.v == 0) rST.setH(rST.h | SSP_FLAG_Z);
        else rST.setH(rST.h | ((rA32.v >> 16) & SSP_FLAG_N));
    }

    /* standard cond processing. */
    /* again, only Z and N is checked, as SVP doesn't seem to use any other conds. */
    int COND_CHECK(int op) {
        int cond = 0;
        long opl = op;
        switch (op & 0xf0) {
            case 0x00:
                cond = 1;
                break; /* always true */
            case 0x50:
//                return (Z == f) ? true : false; // check Z flag
                cond = ((rST.h ^ (opl << 5)) & SSP_FLAG_Z) == 0 ? 1 : 0;
                break; /* Z matches f(?) bit */
            case 0x70:
//                return (N == f) ? true : false; // check N flag
                cond = ((rST.h ^ (opl << 7)) & SSP_FLAG_N) == 0 ? 1 : 0;
                break; /* N matches f(?) bit */
            default:
                break;
        }
        return cond;
    }

    /* ops with accumulator. */
    /* how is low word really affected by these? */
    /* nearly sure 'ld A' doesn't affect flags */
    void OP_LDA(long x) {
        rA.setH((int) x);
    }

    void OP_LDA32(long x) {
        rA32.setV(x);
    }

    void OP_SUBA(long x) {
        rA32.setV(rA32.v - (x << 16));
        UPD_LZVN();
    }

    void OP_SUBA32(long x) {
        rA32.setV(rA32.v - x);
        UPD_LZVN();
    }

    void OP_CMPA(long x) {
        long t = rA32.v - ((x) << 16);
        rST.setH(rST.h & ~(SSP_FLAG_L | SSP_FLAG_Z | SSP_FLAG_V | SSP_FLAG_N));
//        rST.h &= ~(SSP_FLAG_L | SSP_FLAG_Z | SSP_FLAG_V | SSP_FLAG_N);
        if (t == 0) {
//            rST.h |= SSP_FLAG_Z;
            rST.setH(rST.h | SSP_FLAG_Z);
        } else {
//            rST.h |= (t >> 16) & SSP_FLAG_N;
            rST.setH((int) (rST.h | ((t >> 16) & SSP_FLAG_N)));
        }
    }

    void OP_CMPA32(long x) {
        long t = (int) rA32.v - (x);
        rST.setH(rST.h & ~(SSP_FLAG_L | SSP_FLAG_Z | SSP_FLAG_V | SSP_FLAG_N));
        if (t == 0) rST.setH(rST.h | SSP_FLAG_Z);
        else rST.setH((int) (rST.h | ((t >> 16) & SSP_FLAG_N)));
    }

    void OP_ADDA(long x) {
        rA32.setV(rA32.v + (x << 16));
//        rA32.v += (x) << 16;
        UPD_LZVN();
    }

    void OP_ADDA32(long x) {
        rA32.setV(rA32.v + x);
//        rA32.v += (x);
        UPD_LZVN();
    }

    void OP_ANDA(long x) {
//        rA32.v &= x << 16;
        rA32.setV(rA32.v & (x << 16));
        UPD_ACC_ZN();
    }

    void OP_ANDA32(long x) {
        rA32.setV(rA32.v & x);
//        rA32.v &= (x);
        UPD_ACC_ZN();
    }

    /* ----------------------------------------------------- */
    /* register i/o handlers */

    void OP_ORA(long x) {
        rA32.setV(rA32.v | (x << 16));
//        rA32.v |= (x) << 16;
        UPD_ACC_ZN();
    }

    void OP_ORA32(long x) {
        rA32.setV(rA32.v | x);
//        rA32.v |= (x);
        UPD_ACC_ZN();
    }

    void OP_EORA(long x) {
        rA32.setV(rA32.v ^ (x << 16));
//        rA32.v ^= (x) << 16;
        UPD_ACC_ZN();
    }

    void OP_EORA32(long x) {
//        rA32.v ^= (x);
        rA32.setV(rA32.v ^ x);
        UPD_ACC_ZN();
    }

    boolean OP_CHECK32(int op, Consumer<Integer> OP) {
        if ((op & 0x0f) == SSP_P.ordinal()) { /* A <- P */
            read_P(); /* update P */
            OP.accept(rP.v);
            return true;
        } else if ((op & 0x0f) == SSP_A.ordinal()) { /* A <- A */
            OP.accept(rA32.v);
            return true;
        }
        return false;
    }

    /* 0-4, 13 */
    public int read_unknown() {
        if (LOG_SVP) {
            LOG.info("ssp FIXME: unknown read @ %04x", GET_PPC_OFFS());
        }
        LOG.error("ssp FIXME: unknown read @ %04x", GET_PPC_OFFS());
        return 0;
    }

    void write_unknown(int d) {
        if (LOG_SVP) {
            LOG.info("ssp FIXME: unknown write @ %04x", GET_PPC_OFFS());
        }
        LOG.error("ssp FIXME: unknown write @ %04x", GET_PPC_OFFS());
    }

    /* 4 */
    void write_ST(int d) {
        /* if ((rST ^ d) & 0x0007) LOG.info(EL_SVP, "ssp RPL %d -> %d @ %04x", rST&7, d&7, GET_PPC_OFFS()); */
        if (LOG_SVP) {
            if (((rST.h ^ d) & 0x0f98) > 0)
                LOG.info("ssp FIXME ST {} -> {} @ {}", rST, d, GET_PPC_OFFS());
        }
        rST.setH(d);
    }

    /* ----------------------------------------------------- */

    /* 5 */
    int read_STACK() {
        rSTACK.setH(rSTACK.h - 1);
        if ((short) rSTACK.h < 0) {
            rSTACK.setH(5);
            if (LOG_SVP) {
                LOG.info("ssp FIXME: stack underflow! ({}) @ {}", rSTACK.h, GET_PPC_OFFS());
            }
        }
        return ssp.stack[rSTACK.h];
    }

    void write_STACK(int d) {
        if (rSTACK.h >= 6) {
            if (LOG_SVP) {
                LOG.info("ssp FIXME: stack overflow! ({}) @ {}", rSTACK.h, GET_PPC_OFFS());
            }
            //push is ignored?, this was set 0 originally
//            rSTACK.setH(0);
            return;
        }
        ssp.stack[rSTACK.h] = (short) d;
        rSTACK.setH(rSTACK.h + 1);
    }

    /* 6 */
    int read_PC() {
        /* g_cycles--; */
        return GET_PC();
    }

    void write_PC(int d) {
        SET_PC(d);
        g_cycles--;
    }

    /* 7 */
    int read_P() {
        rP.setV(rX.h * rY.h * 2);
        return rP.h;
    }

    int overwrite_write(int currentVal, int newVal) {
        if ((newVal & 0xf000) > 0) {
            currentVal &= ~0xf000;
            currentVal |= newVal & 0xf000;
        }
        if ((newVal & 0x0f00) > 0) {
            currentVal &= ~0x0f00;
            currentVal |= newVal & 0x0f00;
        }
        if ((newVal & 0x00f0) > 0) {
            currentVal &= ~0x00f0;
            currentVal |= newVal & 0x00f0;
        }
        if ((newVal & 0x000f) > 0) {
            currentVal &= ~0x000f;
            currentVal |= newVal & 0x000f;
        }
        return currentVal;
    }

    int pm_io(int reg, int write, int d) {
        if ((ssp.emu_status & SSP_PMC_SET) > 0) {
            int opcode = svp.iram_rom[PC - 1];
            // this MUST be blind r or w
            if ((opcode & 0xff0f) > 0 && (opcode & 0xfff0) > 0) {
                ssp.emu_status &= ~SSP_PMC_SET;
                return 0;
            }
            ssp.pmac[write][reg] = rPMC.v;
            ssp.emu_status &= ~SSP_PMC_SET;

            return 0;
        }

        // just in case
        if ((ssp.emu_status & SSP_PMC_HAVE_ADDR) > 0) {
            ssp.emu_status &= ~SSP_PMC_HAVE_ADDR;
        }

        if (reg == 4 || (rST.h & 0x60) > 0) {
            if (write > 0) {
                int mode = (int) (ssp.pmac[write][reg] >> 16);
                int addr = (int) (ssp.pmac[write][reg] & 0xffff);
                if ((mode & 0x43ff) == 0x0018) // DRAM
                {
                    int inc = get_inc(mode);
                    if ((mode & 0x0400) > 0) {
                        int currentVal = svp.dram[addr];
                        d = overwrite_write(currentVal, d);
                    }
                    svp.dram[addr] = d & 0xFFFF;
                    ssp.pmac[write][reg] += inc;
//                    LOG.info("svp dram write {}, {}", Integer.toHexString(addr),
//                            Integer.toHexString(d));
                } else if ((mode & 0xfbff) == 0x4018) // DRAM, cell inc
                {
                    if ((mode & 0x0400) > 0) {
                        int currentVal = svp.dram[addr];
                        d = overwrite_write(currentVal, d);
                    }
                    svp.dram[addr] = d & 0xFFFF;
                    ssp.pmac[write][reg] += (addr & 1) > 0 ? 31 : 1;
                } else if ((mode & 0x47ff) == 0x001c) // IRAM
                {
                    int inc = get_inc(mode);
                    svp.iram_rom[addr & 0x3FF] = d & 0xFFFF;
                    ssp.pmac[write][reg] += inc;
//                    LOG.debug("svp iram write {}, {}", Integer.toHexString(addr & 0x3FF),
//                            Integer.toHexString(svp.iram_rom[addr & 0x3FF]));
                } else {
                    LOG.info(String.format("ssp FIXME: PM%x unhandled write mode %04x, [%06x] %04x @ %04x",
                            reg, mode, 0, d, GET_PPC_OFFS()));
                }
            } else {
                int mode = (int) (ssp.pmac[0][reg] >> 16);
                int addr = (int) (ssp.pmac[0][reg] & 0xffff);
                if ((mode & 0xfff0) == 0x0800) // ROM, inc 1, verified to be correct
                {
                    ssp.pmac[0][reg] += 1;
                    int romAddr = (addr | ((mode & 0xf) << 16));
                    d = cart.rom[romAddr] & 0xFFFF;
//                    LOG.info("svp rom read {}, {}", Integer.toHexString(romAddr),
//                            Integer.toHexString(d));
                } else if ((mode & 0x47ff) == 0x0018) // DRAM
                {
                    int inc = get_inc(mode);
                    d = svp.dram[addr] & 0xFFFF;
                    ssp.pmac[0][reg] += inc;
//                    LOG.info("svp dram read {}, {}", Integer.toHexString(addr),
//                            Integer.toHexString(d));
                } else {
                    System.out.printf("ssp FIXME: PM%i unhandled read  mode %04x, [%06x] @ %04x",
                            reg, mode, 0, GET_PPC_OFFS());
                    d = 0;
                }
            }

            // PMC value corresponds to last PMR accessed (not sure).
            rPMC.setV(ssp.pmac[write][reg]);

            return d;
        }

        return -1;
    }

    /* 8 */
    int read_PM0() {
        int d = pm_io(0, 0, 0);
        if (d != -1) {
            return d;
        }
        if (LOG_SVP) {
            LOG.info("PM0 raw r {} @ {}", rPM0.h, GET_PPC_OFFS());
        }
        d = rPM0.h;
        if ((d & 2) == 0 && (GET_PPC_OFFS() == 0x400 || GET_PPC_OFFS() == (0x1851E >> 1))) {
            ssp.emu_status |= SSP_WAIT_PM0;
            if (LOG_SVP) {
                LOG.info("det TIGHT loop: PM0");
            }
        }
//        rPM0.h &= ~2; /* ? */
        rPM0.setH(rPM0.h & ~2);
        return d;
    }

    void write_PM0(int d) {
        int r = pm_io(0, 1, d);
        if (r != (int) -1) return;
        if (LOG_SVP) {
            LOG.info("PM0 raw w %04x @ %04x", d, GET_PPC_OFFS());
        }
        rPM0.setH(d);
    }

    /* 9 */
    int read_PM1() {
        int d = pm_io(1, 0, 0);
        if (d != (int) -1) return d;
        /* can be removed? */
        if (LOG_SVP) {
            LOG.info("PM1 raw r %04x @ %04x", rPM1.h, GET_PPC_OFFS());
        }
        return rPM1.h;
    }

    void write_PM1(int d) {
        int r = pm_io(1, 1, d);
        if (r != (int) -1) return;
        /* can be removed? */
        if (LOG_SVP) {
            LOG.info("PM1 raw w %04x @ %04x", d, GET_PPC_OFFS());
        }
        rPM1.setH(d);
    }

    /* 10 */
    int read_PM2() {
        int d = pm_io(2, 0, 0);
        if (d != (int) -1) return d;
        /* can be removed? */
        if (LOG_SVP) {
            LOG.info("PM2 raw r %04x @ %04x", rPM2.h, GET_PPC_OFFS());
        }
        return rPM2.h;
    }

    void write_PM2(int d) {
        int r = pm_io(2, 1, d);
        if (r != (int) -1) return;
        /* can be removed? */
        if (LOG_SVP) {
            LOG.info("PM2 raw w %04x @ %04x", d, GET_PPC_OFFS());
        }
        rPM2.setH(d);
    }

    /* 11 */
    int read_XST() {
        /* can be removed? */
        int d = pm_io(3, 0, 0);
        if (d != (int) -1) return d;
        if (LOG_SVP) {
            LOG.info("XST raw r %04x @ %04x", rXST.h, GET_PPC_OFFS());
        }
        return rXST.h;
    }

    void write_XST(int d) {
        /* can be removed? */
        int r = pm_io(3, 1, d);
        if (r != (int) -1) return;
        if (LOG_SVP) {
            LOG.info("XST raw w %04x @ %04x", d, GET_PPC_OFFS());
        }
//        rPM0.h |= 1;
        rPM0.setH(rPM0.h | 1);
        rXST.setH(d);
    }

    /* 12 */
    int read_PM4() {
        int d = pm_io(4, 0, 0);
        if (d == 0) {
            switch (GET_PPC_OFFS()) {
                case 0x0854 >> 1:
                    ssp.emu_status |= SSP_WAIT_30FE08;
//                    LOG.info("det TIGHT loop: [30fe08]");
                    if (LOG_SVP) {
                        LOG.info("det TIGHT loop: [30fe08]");
                    }
                    break;
                case 0x4f12 >> 1:
                    ssp.emu_status |= SSP_WAIT_30FE06;
//                    LOG.info("det TIGHT loop: [30fe06]");
                    if (LOG_SVP) {
                        LOG.info("det TIGHT loop: [30fe06]");
                    }
                    break;
            }
        }
        if (d != (int) -1) return d;
        /* can be removed? */
        if (LOG_SVP) {
            LOG.info("PM4 raw r %04x @ %04x", rPM4.h, GET_PPC_OFFS());
        }
        return rPM4.h;
    }

    void write_PM4(int d) {
        int r = pm_io(4, 1, d);
        if (r != (int) -1) return;
        /* can be removed? */
        if (LOG_SVP) {
            LOG.info("PM4 raw w %04x @ %04x", d, GET_PPC_OFFS());
        }
        rPM4.setH(d);
    }

    /* 14 */
    int read_PMC() {
        if (LOG_SVP) {
            LOG.info("PMC r a %04x (st %c) @ %04x", rPMC.h,
                    (ssp.emu_status & SSP_PMC_HAVE_ADDR) > 0 ? 'm' : 'a', GET_PPC_OFFS());
        }
        if ((ssp.emu_status & SSP_PMC_HAVE_ADDR) > 0) {
            /* if (ssp.emu_status & SSP_PMC_SET) */
            /*  LOG.info(EL_ANOMALY|EL_SVP, "prev PMC not used @ %04x", GET_PPC_OFFS()); */
            ssp.emu_status |= SSP_PMC_SET;
            ssp.emu_status &= ~SSP_PMC_HAVE_ADDR;
            /* return ((rPMC.h << 4) & 0xfff0) | ((rPMC.h >> 4) & 0xf); */
            long val = rPMC.l;
            return (int) (((val << 4) & 0xfff0) | ((val >> 4) & 0xf));
        } else {
            ssp.emu_status |= SSP_PMC_HAVE_ADDR;
            /* return rPMC.h; */
            return rPMC.l;
        }
    }

    void write_PMC(int d) {
        if ((ssp.emu_status & SSP_PMC_HAVE_ADDR) > 0) {
            /* if (ssp.emu_status & SSP_PMC_SET) */
            /*  LOG.info(EL_ANOMALY|EL_SVP, "prev PMC not used @ %04x", GET_PPC_OFFS()); */
            ssp.emu_status |= SSP_PMC_SET;
            ssp.emu_status &= ~SSP_PMC_HAVE_ADDR;
            /* rPMC.l = d; */
            rPMC.setH(d);
            if (LOG_SVP) {
                LOG.info("PMC w m %04x @ %04x", rPMC.l, GET_PPC_OFFS());
            }
        } else {
            ssp.emu_status |= SSP_PMC_HAVE_ADDR;
            /* rPMC.h = d; */
            rPMC.setL(d);
            if (LOG_SVP) {
                LOG.info("PMC w a {} @ {}", rPMC.h, GET_PPC_OFFS());
            }
        }
    }



    /* ----------------------------------------------------- */
    /* pointer register handlers */

    /* 15 */
    int read_AL() {
        if (svp.iram_rom[PC - 1] == 0x000f) {
            if (LOG_SVP) {
                LOG.info("ssp dummy PM assign %08x @ %04x", rPMC.v, GET_PPC_OFFS());
            }
            ssp.emu_status &= ~(SSP_PMC_SET | SSP_PMC_HAVE_ADDR); /* ? */
        }
        return rAL.l;
    }

    void write_AL(int d) {
        rAL.setL(d);
    }

    //static read_func_t read_handlers[16] =
//        {
//        read_unknown, read_unknown, read_unknown, read_unknown, /* -, X, Y, A */
//        read_unknown,  /* 4 ST */
//        read_STACK,
//        read_PC,
//        read_P,
//        read_PM0,  /* 8 */
//        read_PM1,
//        read_PM2,
//        read_XST,
//        read_PM4,  /* 12 */
//        read_unknown,  /* 13 gr13 */
//        read_PMC,
//        read_AL
//        };
    int invokeReadHandler(int ordinal) {
        switch (ordinal) {
            case 0:
            case 1:
            case 2:
            case 3: /* -, X, Y, A */
            case 4:                         /* 4 ST */
            case 13:                        /* 13 gr13 */
                return read_unknown();
            case 5:
                return read_STACK();
            case 6:
                return read_PC();
            case 7:
                return read_P();
            case 8:
                return read_PM0(); /* 8 */
            case 9:
                return read_PM1();
            case 10:
                return read_PM2();
            case 11:
                return read_XST();
            case 12:
                return read_PM4(); /* 12 */
            case 14:
                return read_PMC();
            case 15:
                return read_AL();
            default:
                LOG.info("invokeReadHandler error: " + ordinal);
        }
        return 0xFF;
    }

    //static write_func_t write_handlers[16] =
//        {
//        write_unknown, write_unknown, write_unknown, write_unknown, /* -, X, Y, A */
//        /*  write_unknown, */ /* 4 ST */
//        write_ST,  /* 4 ST (debug hook) */
//        write_STACK,
//        write_PC,
//        write_unknown,  /* 7 P */
//        write_PM0,  /* 8 */
//        write_PM1,
//        write_PM2,
//        write_XST,
//        write_PM4,  /* 12 */
//        write_unknown,  /* 13 gr13 */
//        write_PMC,
//        write_AL
//        };
    int invokeWriteHandler(int ordinal, int value) {
        switch (ordinal) {
            case 0:
            case 1:
            case 2:
            case 3: /* -, X, Y, A */
            case 7:                         /* 7 P */
            case 13:                        /* 13 gr13 */
                write_unknown(value);
                break;
            case 4:/* 4 ST */
                //VR needs this
                write_ST(value);
                break;
            case 5:
                write_STACK(value);
                break;
            case 6:
                write_PC(value);
                break;
            case 8:
                write_PM0(value); /* 8 */
                break;
            case 9:
                write_PM1(value);
                break;
            case 10:
                write_PM2(value);
                break;
            case 11:
                write_XST(value);
                break;
            case 12:
                write_PM4(value); /* 12 */
                break;
            case 14:
                write_PMC(value);
                break;
            case 15:
                write_AL(value);
                break;
            default:
                LOG.info("invokeWriteHandler error: " + ordinal);
        }
        return 0xFF;
    }


    /* ----------------------------------------------------- */

    int ptr1_read(int op) {
        return ptr1_read_(op & 3, (op >> 6) & 4, (op << 1) & 0x18);
    }

    int ptr1_read_(int ri, int isj2, int modi3) {
        /* int t = (op&3) | ((op>>6)&4) | ((op<<1)&0x18); */
        int mask, add = 0;
        final int t = ri | isj2 | modi3;
        int rpPtr = 0, res;
        switch (t) {
            /* mod=0 (00) */
            case 0x00:
            case 0x01:
            case 0x02:
                return ssp.mem.bank.RAM0[ssp.ptr.bank.r0[t & 3]];
            case 0x03:
                return ssp.mem.bank.RAM0[0];
            case 0x04:
            case 0x05:
            case 0x06:
                return ssp.mem.bank.RAM1[ssp.ptr.bank.r1[t & 3]];
            case 0x07:
                return ssp.mem.bank.RAM1[0];
            /* mod=1 (01), "+!" */
            case 0x08:
            case 0x09:
            case 0x0a:
                int val = ssp.ptr.bank.r0[t & 3];
                ssp.ptr.bank.r0[t & 3] = (val + 1) & SSP_POINTER_REGS_MASK;
                return ssp.mem.bank.RAM0[val];
            case 0x0b:
                return ssp.mem.bank.RAM0[1];
            case 0x0c:
            case 0x0d:
            case 0x0e:
                val = ssp.ptr.bank.r1[t & 3];
                ssp.ptr.bank.r1[t & 3] = (val + 1) & SSP_POINTER_REGS_MASK;
                return ssp.mem.bank.RAM1[val];
            case 0x0f:
                return ssp.mem.bank.RAM1[1];
            /* mod=2 (10), "-" */
            case 0x10:
            case 0x11:
            case 0x12:
                rpPtr = ssp.ptr.bank.r0[t & 3];
                res = ssp.mem.bank.RAM0[rpPtr];
                if ((rST.h & 7) == 0) {
                    ssp.ptr.bank.r0[t & 3] = (rpPtr - 1) & SSP_POINTER_REGS_MASK;
                    return res;
                }
                add = -1;
                //goto modulo;
                mask = (1 << (rST.h & 7)) - 1;
                //*rp = (*rp & ~mask) | ((*rp + add) & mask);
                rpPtr = (rpPtr & ~mask) | ((rpPtr + add) & mask);
                ssp.ptr.bank.r0[t & 3] = rpPtr & SSP_POINTER_REGS_MASK;
                return res;
            case 0x13:
                return ssp.mem.bank.RAM0[2];
            case 0x14:
            case 0x15:
            case 0x16:
                rpPtr = ssp.ptr.bank.r1[t & 3];
                res = ssp.mem.bank.RAM1[rpPtr];
                if ((rST.h & 7) == 0) {
                    ssp.ptr.bank.r1[t & 3] = (rpPtr - 1) & SSP_POINTER_REGS_MASK;
                    return res;
                }
                add = -1;
                //goto modulo;
                mask = (1 << (rST.h & 7)) - 1;
                rpPtr = (rpPtr & ~mask) | ((rpPtr + add) & mask);
                ssp.ptr.bank.r1[t & 3] = rpPtr & SSP_POINTER_REGS_MASK;
                return res;
            case 0x17:
                return ssp.mem.bank.RAM1[2];
            /* mod=3 (11), "+" */
            case 0x18:
            case 0x19:
            case 0x1a:
                rpPtr = ssp.ptr.bank.r0[t & 3];
                res = ssp.mem.bank.RAM0[rpPtr];
                if ((rST.h & 7) == 0) {
                    ssp.ptr.bank.r0[t & 3] = (rpPtr + 1) & SSP_POINTER_REGS_MASK;
                    return res;
                }
                add = 1;
                //goto modulo;
                mask = (1 << (rST.h & 7)) - 1;
                rpPtr = (rpPtr & ~mask) | ((rpPtr + add) & mask);
                ssp.ptr.bank.r0[t & 3] = rpPtr & SSP_POINTER_REGS_MASK;
                return res;
            case 0x1b:
                return ssp.mem.bank.RAM0[3];
            case 0x1c:
            case 0x1d:
            case 0x1e:
                rpPtr = ssp.ptr.bank.r1[t & 3];
                res = ssp.mem.bank.RAM1[rpPtr];
                if ((rST.h & 7) == 0) {
                    ssp.ptr.bank.r1[t & 3] = (rpPtr + 1) & SSP_POINTER_REGS_MASK;
                    return res;
                }
                add = 1;
                //goto modulo;
                mask = (1 << (rST.h & 7)) - 1;
                rpPtr = (rpPtr & ~mask) | ((rpPtr + add) & mask);
                ssp.ptr.bank.r1[t & 3] = rpPtr & SSP_POINTER_REGS_MASK;
                return res;
            case 0x1f:
                return ssp.mem.bank.RAM1[3];
        }
        return 0;
    }

    void ptr1_write(int op, int d) {
        final int t = (op & 3) | ((op >> 6) & 4) | ((op << 1) & 0x18);
        d = d & MASK_16BIT;
        int val;
        switch (t) {
            /* mod=0 (00) */
            case 0x00:
            case 0x01:
            case 0x02:
                ssp.mem.bank.RAM0[ssp.ptr.bank.r0[t & 3]] = d;
                return;
            case 0x03:
                ssp.mem.bank.RAM0[0] = d;
                return;
            case 0x04:
            case 0x05:
            case 0x06:
                ssp.mem.bank.RAM1[ssp.ptr.bank.r1[t & 3]] = d;
                return;
            case 0x07:
                ssp.mem.bank.RAM1[0] = d;
                return;
            /* mod=1 (01), "+!" */
            /* mod=3,      "+" */
            case 0x08:
            case 0x18:
            case 0x09:
            case 0x19:
            case 0x0a:
            case 0x1a:
                val = ssp.ptr.bank.r0[t & 3];
                ssp.ptr.bank.r0[t & 3] = (val + 1) & SSP_POINTER_REGS_MASK;
                ssp.mem.bank.RAM0[val] = d;
                return;
            case 0x0b:
                ssp.mem.bank.RAM0[1] = d;
                return;
            case 0x0c:
            case 0x1c:
            case 0x0d:
            case 0x1d:
            case 0x0e:
            case 0x1e:
                val = ssp.ptr.bank.r1[t & 3];
                ssp.ptr.bank.r1[t & 3] = (val + 1) & SSP_POINTER_REGS_MASK;
                ssp.mem.bank.RAM1[val] = d;
                return;
            case 0x0f:
                ssp.mem.bank.RAM1[1] = d;
                return;
            /* mod=2 (10), "-" */
            case 0x10:
            case 0x11:
            case 0x12:
                val = ssp.ptr.bank.r0[t & 3];
                ssp.ptr.bank.r0[t & 3] = (val - 1) & SSP_POINTER_REGS_MASK;
                ssp.mem.bank.RAM0[val] = d;
                return;
            case 0x13:
                ssp.mem.bank.RAM0[2] = d;
                return;
            case 0x14:
            case 0x15:
            case 0x16:
                val = ssp.ptr.bank.r1[t & 3];
                ssp.ptr.bank.r1[t & 3] = (val - 1) & SSP_POINTER_REGS_MASK;
                ssp.mem.bank.RAM1[val] = d;
                return;
            case 0x17:
                ssp.mem.bank.RAM1[2] = d;
                return;
            /* mod=3 (11) */
            case 0x1b:
                ssp.mem.bank.RAM0[3] = d;
                return;
            case 0x1f:
                ssp.mem.bank.RAM1[3] = d;
                return;
        }
    }

    int ptr2_read(int op) {
        int mv = 0;
        final int t = (op & 3) | ((op >> 6) & 4) | ((op << 1) & 0x18);
        switch (t) {
            /* mod=0 (00) */
            case 0x00:
            case 0x01:
            case 0x02:
                mv = ssp.mem.bank.RAM0[ssp.ptr.bank.r0[t & 3]];
                ssp.mem.bank.RAM0[ssp.ptr.bank.r0[t & 3]] = (mv + 1) & MASK_16BIT;
                break;
            case 0x03:
                mv = ssp.mem.bank.RAM0[0];
                ssp.mem.bank.RAM0[0] = (mv + 1) & MASK_16BIT;
                break;
            case 0x04:
            case 0x05:
            case 0x06:
                mv = ssp.mem.bank.RAM1[ssp.ptr.bank.r1[t & 3]];
                ssp.mem.bank.RAM1[ssp.ptr.bank.r1[t & 3]] = (mv + 1) & MASK_16BIT;
                break;
            case 0x07:
                mv = ssp.mem.bank.RAM1[0];
                ssp.mem.bank.RAM1[0] = (mv + 1) & MASK_16BIT;
                break;
            /* mod=1 (01) */
            case 0x0b:
                mv = ssp.mem.bank.RAM0[1];
                ssp.mem.bank.RAM0[1] = (mv + 1) & MASK_16BIT;
                break;
            case 0x0f:
                mv = ssp.mem.bank.RAM1[1];
                ssp.mem.bank.RAM1[1] = (mv + 1) & MASK_16BIT;
                break;
            /* mod=2 (10) */
            case 0x13:
                mv = ssp.mem.bank.RAM0[2];
                ssp.mem.bank.RAM0[2] = (mv + 1) & MASK_16BIT;
                break;
            case 0x17:
                mv = ssp.mem.bank.RAM1[2];
                ssp.mem.bank.RAM1[2] = (mv + 1) & MASK_16BIT;
                break;
            /* mod=3 (11) */
            case 0x1b:
                mv = ssp.mem.bank.RAM0[3];
                ssp.mem.bank.RAM0[3] = (mv + 1) & MASK_16BIT;
                break;
            case 0x1f:
                mv = ssp.mem.bank.RAM1[3];
                ssp.mem.bank.RAM1[3] = (mv + 1) & MASK_16BIT;
                break;
            default:
                if (LOG_SVP) {
                    LOG.info("ssp FIXME: invalid mod in ((rX))? @ %04x", GET_PPC_OFFS());
                }
                return 0;
        }
//        return ((unsigned short *)svp->iram_rom)[mv];
        return svp.iram_rom[mv];
    }

//static void debug_dump2file(const char *fname, void *mem, int len)
//        {
//        FILE *f = fopen(fname, "wb");
//        unsigned short *p = mem;
//        int i;
//        if (f) {
//        for (i = 0; i < len/2; i++) p[i] = (p[i]<<8) | (p[i]>>8);
//        fwrite(mem, 1, len, f);
//        fclose(f);
//        for (i = 0; i < len/2; i++) p[i] = (p[i]<<8) | (p[i]>>8);
//        printf("dumped to %s\n", fname);
//        }
//        else
//        printf("dump failed\n");
//        }

    @Override
    public void ssp1601_reset(ssp1601_t l_ssp) {
        ssp = l_ssp;
        ssp.emu_status = 0;
        ssp.gr[SSP_GR0.ordinal()].setV(0xffff0000L);
        rPC.setH(0x400);
        rSTACK.setH(0); /* ? using ascending stack */
        rST.setH(0);
    }

    void debug_dump(boolean force) {
        if (!LOG_SVP && !force) {
            return;
        }
        StringBuilder sb = new StringBuilder("\n\n");
        sb.append(String.format("GR0:   %04x    X: %04x    Y: %04x  A: %08x\n", ssp.gr[SSP_GR0.ordinal()].h, rX.h, rY.h,
                ssp.gr[SSP_A.ordinal()].v));
        sb.append(String.format("PC:    %04x  (%04x)                P: %08x\n", GET_PC(), GET_PC() << 1,
                ssp.gr[SSP_P.ordinal()].v));
        sb.append(String.format("PM0:   %04x  PM1: %04x  PM2: %04x\n", rPM0.h, rPM1.h, rPM2.h));
        sb.append(String.format("XST:   %04x  PM4: %04x  PMC: %08x\n", rXST.h, rPM4.h, ssp.gr[SSP_PMC.ordinal()].v));
        sb.append(String.format(" ST:   %04x  %c%c%c%c,  GP0_0 %x,  GP0_1 %x\n", rST.h,
                (rST.h & SSP_FLAG_N) > 0 ? 'N' : 'n', (rST.h & SSP_FLAG_V) > 0 ? 'V' : 'v',
                (rST.h & SSP_FLAG_Z) > 0 ? 'Z' : 'z', (rST.h & SSP_FLAG_L) > 0 ? 'L' : 'l',
                (rST.h >> 5) & 1, (rST.h >> 6) & 1));
        sb.append(String.format("STACK: %d %04x %04x %04x %04x %04x %04x\n", rSTACK.h, ssp.stack[0], ssp.stack[1],
                ssp.stack[2], ssp.stack[3], ssp.stack[4], ssp.stack[5]));
        sb.append(String.format("r0-r2: %02x %02x %02x  r4-r6: %02x %02x %02x\n",
                ssp.ptr.getPointerVal(0), ssp.ptr.getPointerVal(1), ssp.ptr.getPointerVal(2),
                ssp.ptr.getPointerVal(4), ssp.ptr.getPointerVal(5), ssp.ptr.getPointerVal(6)));
        sb.append(String.format("cycles: %d, emu_status: %x\n\n", g_cycles, ssp.emu_status));
        LOG.info(sb.toString());
        System.out.println(sb.toString());
    }

    void debug_dump_mem() {
        int h, i;
        LOG.info("RAM0\n");
        for (h = 0; h < 32; h++) {
            if (h == 16) LOG.info("RAM1\n");
            LOG.info("%03x:", h * 16);
            for (i = 0; i < 16; i++)
                LOG.info(" %04x", ssp.mem.readRAM(h * 16 + i));
            LOG.info("\n");
        }
    }

    void debug(int pc, int op) {

        char[] buff = new char[64]; // = {0,};
        int i;

        if (running > 0) {
            for (i = 0; i < 10; i++)
                if (pc != 0 && bpts[i] == pc) {
                    LOG.info("breakpoint %d\n", i);
                    running = 0;
                    break;
                }
        }
        if (running > 0) return;

        LOG.info("%04x (%02x) @ %04x\n", op, op >> 9, pc << 1);
        int baddr = 0;
        while (true) {
            LOG.info("dbg> ");
//        fflush(stdout);
//        fgets(buff, sizeof(buff), stdin);
//        if (buff[0] == '\n') strcpy(buff, buffo);
//        else strcpy(buffo, buff);

            switch (buff[baddr]) {
                case 0:
                    System.exit(0);
                case 'c':
                case 'r':
                    running = 1;
                    return;
                case 's':
                case 'n':
                    return;
                case 'x':
                    debug_dump(true);
                    break;
                case 'm':
                    debug_dump_mem();
                    break;
                case 'b':
                    baddr += 2;
                    i = 0;
                    if (buff[baddr + 3] == ' ') {
                        i = buff[baddr + 2] - '0';
                        baddr += 4;
                    }
//                bpts[i] = strtol(baddr, NULL, 16) >> 1; //TODO
                    LOG.info("breakpoint %d set @ %04x\n", i, bpts[i] << 1);
                    break;
                case 'd':
//            LOG.info(buff, "iramrom_%04x.bin", last_iram);
                    debug_dump2file(String.copyValueOf(buff), svp.iram_rom, svp.iram_rom.length);
                    debug_dump2file("dram.bin", svp.dram, svp.dram.length);
                    break;
                default:
                    LOG.info("unknown command\n");
                    break;
            }
        }
    } /* USE_DEBUGGER */

    private void logNewPc() {
        if (pcSet.add(PC)) {
            StringBuilder sb = new StringBuilder();
            sb.setLength(0);
            sb.append(Integer.toHexString(PC) + ": ");
            Ssp16Disasm.dasm_ssp1601(sb, PC, svp.iram_rom);
            LOG.info(sb.toString());
//            if (PC == 0x1bc8c) {
//                debug_dump(true);
//            }
        }
    }

    @Override
    public void ssp1601_run(int cycles) {
        SET_PC(rPC.h);
        g_cycles = cycles;
        do {
            int op, tmpv, cond;
//            debug_dump(true);
//            logNewPc();
            op = svp.iram_rom[PC] & 0xFFFF;
            PC = (PC + 1) & PC_MASK;
//        #ifdef USE_DEBUGGER
//        debug(GET_PC()-1, op);
//        }
            switch (op >> 9) {
                /* ld d, s */
                case 0x00:
                    if (op == 0) break; /* nop */
                    if (op == ((SSP_A.ordinal() << 4) | SSP_P.ordinal())) { /* A <- P */
                        /* not sure. MAME claims that only hi word is transferred. */
                        read_P(); /* update P */
                        rA32.setV(rP.v);
                    } else {
                        tmpv = REG_READ(op & 0x0f);
                        REG_WRITE((op & 0xf0) >> 4, tmpv);
                    }
                    break;

                /* ld d, (ri) */
                case 0x01:
                    tmpv = ptr1_read(op);
                    REG_WRITE((op & 0xf0) >> 4, tmpv);
                    break;

                /* ld (ri), s */
                case 0x02:
                    tmpv = REG_READ((op & 0xf0) >> 4);
                    ptr1_write(op, tmpv);
                    break;

                /* ldi d, imm */
                case 0x04:
                    tmpv = svp.iram_rom[PC];
                    PC = (PC + 1) & PC_MASK;
                    REG_WRITE((op & 0xf0) >> 4, tmpv);
                    break;

                /* ld d, ((ri)) */
                case 0x05:
                    tmpv = ptr2_read(op);
                    REG_WRITE((op & 0xf0) >> 4, tmpv);
                    break;

                /* ldi (ri), imm */
                case 0x06:
                    tmpv = svp.iram_rom[PC];
                    PC = (PC + 1) & PC_MASK;
                    ptr1_write(op, tmpv);
                    break;

                /* ld adr, a */
                case 0x07:
                    ssp.mem.setRAM(op & 0x1ff, rA.h);
                    break;

                /* ld d, ri */
                case 0x09:
                    tmpv = ssp.ptr.getPointerVal((op & 3) | ((op >> 6) & 4));
//                            rIJ[(op & 3) | ((op >> 6) & 4)];
                    REG_WRITE((op & 0xf0) >> 4, tmpv);
                    break;

                /* ld ri, s */
                case 0x0a:
                    ssp.ptr.setPointerVal((op & 3) | ((op >> 6) & 4), REG_READ((op & 0xf0) >> 4));
                    break;

                /* ldi ri, simm */
                case 0x0c:
                case 0x0d:
                case 0x0e:
                case 0x0f:
                    ssp.ptr.setPointerVal((op >> 8) & 7, op);
                    break;

                /* call cond, addr */
                case 0x24:
                    cond = COND_CHECK(op);
                    if (cond > 0) {
                        int new_PC = svp.iram_rom[PC];
                        ;
                        PC = (PC + 1) & PC_MASK;
                        write_STACK(GET_PC());
                        write_PC(new_PC);
                    } else {
                        PC = (PC + 1) & PC_MASK;
                    }
                    break;
                /* ld d, (a) */
                case 0x25:
//            tmpv = ((unsigned short *)svp->iram_rom)[rA];
                    tmpv = svp.iram_rom[rA.h & MASK_16BIT];
                    REG_WRITE((op & 0xf0) >> 4, tmpv);
                    break;

                /* bra cond, addr */
                case 0x26:
                    cond = COND_CHECK(op);
                    if (cond > 0) {
                        int new_PC = svp.iram_rom[PC];
                        PC = (PC + 1) & PC_MASK;
                        write_PC(new_PC);
                    } else {
                        PC = (PC + 1) & PC_MASK;
                    }
                    break;
                /* mod cond, op */
                case 0x48:
                    cond = COND_CHECK(op);
                    if (cond > 0) {
                        int val = rA32.v; //signed 32 bit
                        switch (op & 7) {
                            case 2:
                                /* shr (arithmetic) */

                                rA32.setV(val >> 1); //to signed 32 bit
                                break;
                            case 3:
                                rA32.setV(val << 1); //to signed 32 bit

                                break; /* shl */
                            case 6:
                                rA32.setV(-val); //to signed 32 bit
                                break; /* neg */
                            case 7:
                                if (val < 0) {
                                    rA32.setV(-val); //to signed 32 bit
                                }
                                break; /* abs */
                            default:
                                if (LOG_SVP) {
                                    LOG.info("ssp FIXME: unhandled mod %d @ %04x",
                                            op & 7, GET_PPC_OFFS());
                                }
                                break;
                        }
                        UPD_ACC_ZN(); /* ? */
                    }
                    break;
                /* mpys? */
                case 0x1b:
                    if (LOG_SVP) {
                        if ((op & 0x100) == 0) LOG.info("ssp FIXME: no b bit @ %04x", GET_PPC_OFFS());
                    }
                    read_P(); /* update P */
//                    rA32.v -= rP.v;
                    rA32.setV(rA32.v - rP.v); /* maybe only upper word? */
                    UPD_ACC_ZN();      /* there checking flags after this */
                    rX.setH(ptr1_read_(op & 3, 0, (op << 1) & 0x18)); /* ri (maybe rj?) */
                    rY.setH(ptr1_read_((op >> 4) & 3, 4, (op >> 3) & 0x18)); /* rj */
                    break;

                /* mpya (rj), (ri), b */
                case 0x4b:
                    if (LOG_SVP) {
                        if ((op & 0x100) == 0) LOG.info("ssp FIXME: no b bit @ %04x", GET_PPC_OFFS());
                    }
                    read_P(); /* update P */
//                    rA32.v += rP.v;
                    rA32.setV(rA32.v + rP.v); /* confirmed to be 32bit */
                    UPD_ACC_ZN(); /* ? */
                    rX.setH(ptr1_read_(op & 3, 0, (op << 1) & 0x18)); /* ri (maybe rj?) */
                    rY.setH(ptr1_read_((op >> 4) & 3, 4, (op >> 3) & 0x18)); /* rj */
                    break;

                /* mld (rj), (ri), b */
                case 0x5b:
                    if (LOG_SVP) {
                        if ((op & 0x100) == 0) LOG.info("ssp FIXME: no b bit @ %04x", GET_PPC_OFFS());
                    }
                    rA32.setV(0);
                    rST.setH(rST.h & 0x0fff); /* ? */
                    rX.setH(ptr1_read_(op & 3, 0, (op << 1) & 0x18)); /* ri (maybe rj?) */
                    rY.setH(ptr1_read_((op >> 4) & 3, 4, (op >> 3) & 0x18)); /* rj */
                    break;

                /* OP a, s */
                case 0x10:
                    if (OP_CHECK32(op, this::OP_SUBA32)) break;
                    tmpv = REG_READ(op & 0x0f);
                    OP_SUBA(tmpv);
                    break;
                case 0x30:
                    if (OP_CHECK32(op, this::OP_CMPA32)) break;
                    tmpv = REG_READ(op & 0x0f);
                    OP_CMPA(tmpv);
                    break;
                case 0x40:
                    if (OP_CHECK32(op, this::OP_ADDA32)) break;
                    tmpv = REG_READ(op & 0x0f);
                    OP_ADDA(tmpv);
                    break;
                case 0x50:
                    if (OP_CHECK32(op, this::OP_ANDA32)) break;
                    tmpv = REG_READ(op & 0x0f);
                    OP_ANDA(tmpv);
                    break;
                case 0x60:
                    if (OP_CHECK32(op, this::OP_ORA32)) break;
                    tmpv = REG_READ(op & 0x0f);
                    OP_ORA(tmpv);
                    break;
                case 0x70:
                    if (OP_CHECK32(op, this::OP_EORA32)) break;
                    tmpv = REG_READ(op & 0x0f);
                    OP_EORA(tmpv);
                    break;

                /* OP a, (ri) */
                case 0x11:
                    tmpv = ptr1_read(op);
                    OP_SUBA(tmpv);
                    break;
                case 0x31:
                    tmpv = ptr1_read(op);
                    OP_CMPA(tmpv);
                    break;
                case 0x41:
                    tmpv = ptr1_read(op);
                    OP_ADDA(tmpv);
                    break;
                case 0x51:
                    tmpv = ptr1_read(op);
                    OP_ANDA(tmpv);
                    break;
                case 0x61:
                    tmpv = ptr1_read(op);
                    OP_ORA(tmpv);
                    break;
                case 0x71:
                    tmpv = ptr1_read(op);
                    OP_EORA(tmpv);
                    break;

                /* OP a, adr */
                case 0x03:
                    tmpv = ssp.mem.readRAM(op & 0x1ff);
                    OP_LDA(tmpv);
                    break;
                case 0x13:
                    tmpv = ssp.mem.readRAM(op & 0x1ff);
                    OP_SUBA(tmpv);
                    break;
                case 0x33:
                    tmpv = ssp.mem.readRAM(op & 0x1ff);
                    OP_CMPA(tmpv);
                    break;
                case 0x43:
                    tmpv = ssp.mem.readRAM(op & 0x1ff);
                    OP_ADDA(tmpv);
                    break;
                case 0x53:
                    tmpv = ssp.mem.readRAM(op & 0x1ff);
                    OP_ANDA(tmpv);
                    break;
                case 0x63:
                    tmpv = ssp.mem.readRAM(op & 0x1ff);
                    OP_ORA(tmpv);
                    break;
                case 0x73:
                    tmpv = ssp.mem.readRAM(op & 0x1ff);
                    OP_EORA(tmpv);
                    break;

                /* OP a, imm */
                case 0x14:
                    tmpv = svp.iram_rom[PC];
                    PC = (PC + 1) & PC_MASK;
                    OP_SUBA(tmpv);
                    break;
                case 0x34:
                    tmpv = svp.iram_rom[PC];
                    PC = (PC + 1) & PC_MASK;
                    OP_CMPA(tmpv);
                    break;
                case 0x44:
                    tmpv = svp.iram_rom[PC];
                    PC = (PC + 1) & PC_MASK;
                    OP_ADDA(tmpv);
                    break;
                case 0x54:
                    tmpv = svp.iram_rom[PC];
                    PC = (PC + 1) & PC_MASK;
                    OP_ANDA(tmpv);
                    break;
                case 0x64:
                    tmpv = svp.iram_rom[PC];
                    PC = (PC + 1) & PC_MASK;
                    OP_ORA(tmpv);
                    break;
                case 0x74:
                    tmpv = svp.iram_rom[PC];
                    PC = (PC + 1) & PC_MASK;
                    OP_EORA(tmpv);
                    break;

                /* OP a, ((ri)) */
                case 0x15:
                    tmpv = ptr2_read(op);
                    OP_SUBA(tmpv);
                    break;
                case 0x35:
                    tmpv = ptr2_read(op);
                    OP_CMPA(tmpv);
                    break;
                case 0x45:
                    tmpv = ptr2_read(op);
                    OP_ADDA(tmpv);
                    break;
                case 0x55:
                    tmpv = ptr2_read(op);
                    OP_ANDA(tmpv);
                    break;
                case 0x65:
                    tmpv = ptr2_read(op);
                    OP_ORA(tmpv);
                    break;
                case 0x75:
                    tmpv = ptr2_read(op);
                    OP_EORA(tmpv);
                    break;

                /* OP a, ri */
                case 0x19:
                    tmpv = ssp.ptr.getPointerVal(IJind(op));
                    OP_SUBA(tmpv);
                    break;
                case 0x39:
                    tmpv = ssp.ptr.getPointerVal(IJind(op));
                    OP_CMPA(tmpv);
                    break;
                case 0x49:
                    tmpv = ssp.ptr.getPointerVal(IJind(op));
                    OP_ADDA(tmpv);
                    break;
                case 0x59:
                    tmpv = ssp.ptr.getPointerVal(IJind(op));
                    OP_ANDA(tmpv);
                    break;
                case 0x69:
                    tmpv = ssp.ptr.getPointerVal(IJind(op));
                    OP_ORA(tmpv);
                    break;
                case 0x79:
                    tmpv = ssp.ptr.getPointerVal(IJind(op));
                    OP_EORA(tmpv);
                    break;

                /* OP simm */
                case 0x1c:
                    OP_SUBA(op & 0xff);
                    if (LOG_SVP) {
                        if ((op & 0x100) > 0) LOG.info("FIXME: simm with upper bit set");
                    }
                    break;
                case 0x3c:
                    OP_CMPA(op & 0xff);
                    if (LOG_SVP) {
                        if ((op & 0x100) > 0) LOG.info("FIXME: simm with upper bit set");
                    }
                    break;
                case 0x4c:
                    OP_ADDA(op & 0xff);
                    if (LOG_SVP) {
                        if ((op & 0x100) > 0) LOG.info("FIXME: simm with upper bit set");
                    }
                    break;
                /* MAME code only does LSB of top word, but this looks wrong to me. */
                case 0x5c:
                    OP_ANDA(op & 0xff);
                    if (LOG_SVP) {
                        if ((op & 0x100) > 0) LOG.info("FIXME: simm with upper bit set");
                    }
                    break;
                case 0x6c:
                    OP_ORA(op & 0xff);
                    if (LOG_SVP) {
                        if ((op & 0x100) > 0) LOG.info("FIXME: simm with upper bit set");
                    }
                    break;
                case 0x7c:
                    OP_EORA(op & 0xff);
                    if (LOG_SVP) {
                        if ((op & 0x100) > 0) LOG.info("FIXME: simm with upper bit set");
                    }
                    break;

                default:
                    LOG.error("ssp FIXME unhandled op {} @ {}", op, GET_PPC_OFFS());
                    break;
            }
        }
//        while (--g_cycles > 0 && !(ssp.emu_status & SSP_WAIT_MASK));
        while (--g_cycles > 0 && (ssp.emu_status & SSP_WAIT_MASK) == 0);

        read_P(); /* update P */
        rPC.setH(GET_PC());

        if (LOG_SVP) {
            if (ssp.gr[SSP_GR0.ordinal()].v != 0xffff0000)
                LOG.info("ssp FIXME: REG 0 corruption! {}", ssp.gr[SSP_GR0.ordinal()].v);
        }
    }
}
