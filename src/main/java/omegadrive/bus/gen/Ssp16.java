package omegadrive.bus.gen;

import java.util.stream.IntStream;

/**
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public interface Ssp16 {

    int SSP_PMC_HAVE_ADDR = 0x0001; /* address written to PMAC, waiting for mode */
    int SSP_PMC_SET = 0x0002; /* PMAC is set */
    int SSP_HANG = 0x1000; /* 68000 hangs SVP */
    int SSP_WAIT_PM0 = 0x2000; /* bit1 in PM0 */
    int SSP_WAIT_30FE06 = 0x4000; /* ssp tight loops on 30FE08 to become non-zero */
    int SSP_WAIT_30FE08 = 0x8000; /* same for 30FE06 */
    int SSP_WAIT_MASK = 0xf000;

    //logging
    int EL_SVP = 0x00004000; /* SVP stuff  */
    int EL_ANOMALY = 0x80000000; /* some unexpected conditions (during emulation) */

    int SSP_RAM_SIZE_WORDS = 256;
    int SSP_RAM_MASK_WORDS = SSP_RAM_SIZE_WORDS - 1;
    int SSP_POINTER_REGS_MASK = 0xFF;
    int MASK_16BIT = 0xFFFF;
    int PC_MASK = MASK_16BIT;

    int IRAM_ROM_SIZE_WORDS = 0x10000; //128 kbytes -> 64k words
    int IRAM_SIZE_WORDS = 0x400; //2kbytes -> 1k words
    int ROM_SIZE_WORDS = IRAM_ROM_SIZE_WORDS - IRAM_SIZE_WORDS; //63k words
    int DRAM_SIZE_WORDS = 0x10000; //128Kbytes -> 64k words

    public static void main(String[] args) {
        int l = 0xFFFF_8800;
        short l1 = (short) l;
        long v = 0x0809_41F4;
        v = (v & 0xFFFF_0000) | (l & 0xFFFF);
        System.out.println(Long.toHexString(v));

        int h = 0xFFFF_F800;
        short h1 = (short) h;
        v = 0x0809_41F4;
        v = (h & 0xFFFF) << 16 | (v & 0xFFFF);
        v = h << 16 | (v & 0xFFFF);
        System.out.println(Long.toHexString(v));
    }

    ;

    void ssp1601_reset(ssp1601_t ssp);

    void ssp1601_run(int cycles);

    /* register names */
    enum Ssp16Reg {
        SSP_GR0, SSP_X, SSP_Y, SSP_A,
        SSP_ST, SSP_STACK, SSP_PC, SSP_P,
        SSP_PM0, SSP_PM1, SSP_PM2, SSP_XST,
        SSP_PM4, SSP_gr13, SSP_PMC, SSP_AL
    }

    class ssp_reg_t {
        int v; //unsigned 32 bit
        short l; //unsigned 16 bit
        short h; //unsigned 16 bit

        public void setV(long v) {
            this.v = (int) v;
            this.h = (short) (this.v >> 16);
            this.l = (short) (this.v & 0xFFFF);
        }

        public void setH(int h) {
            this.h = (short) h;
            this.v = (h << 16) | (v & 0xFFFF);
        }

        public void setL(int l) {
            this.l = (short) l;
            this.v = (v & 0xFFFF_0000) | (l & 0xFFFF);
        }
    }

    class ssp1601_t {
        mem mem = new mem();
        ptr ptr = new ptr();
        ssp_reg_t[] gr = new ssp_reg_t[16];  /* general registers */
        short[] stack = new short[6];
        long[][] pmac = new long[2][6];  /* read/write modes/addrs for PM0-PM5 */
        int emu_status;
        int[] pad = new int[30];

        {
            IntStream.range(0, gr.length).forEach(i -> gr[i] = new ssp_reg_t());
        }

        class mem {

            bank bank = new bank();

            public void setRAM(int addr, int val) {
                if (addr < SSP_RAM_SIZE_WORDS) {
                    bank.RAM0[addr] = val;
                } else {
                    bank.RAM1[addr & 0xFF] = val;
                }
            }

            public int readRAM(int addr) {
                if (addr < SSP_RAM_SIZE_WORDS) {
                    return bank.RAM0[addr];
                } else {
                    return bank.RAM1[addr & 0xFF];
                }
            }

            /* 2 internal RAM banks */ //16 bit unsigned
            class bank {
                int[] RAM0 = new int[SSP_RAM_SIZE_WORDS]; //16 bit unsigned
                int[] RAM1 = new int[SSP_RAM_SIZE_WORDS]; //16 bit unsigned
            }
        }

        class ptr {
            bank bank = new bank();

            public int getPointerVal(int pos) {
                return pos < 4 ? bank.r0[pos] : bank.r1[pos - 4];
            }

            public void setPointerVal(int pos, int val) {
                int pos1 = pos % 4;
                if (pos1 == 3) { //r3 and r7 cannot be modified
                    return;
                }
                int[] rg = pos < 4 ? bank.r0 : bank.r1;
                rg[pos1] = val & SSP_POINTER_REGS_MASK;
            }

            /* BANK pointers */ //8 bit unsigned
            class bank {
                int[] r0 = new int[4]; //8 bit unsigned
                int[] r1 = new int[4]; //8 bit unsigned
            }
        }
    }

    class svp_t {
        public ssp1601_t ssp1601;
        int[] iram_rom = new int[IRAM_ROM_SIZE_WORDS]; /* IRAM (0-0x7ff) and program ROM (0x800-0x1ffff) */
        int[] dram = new int[DRAM_SIZE_WORDS];
    }

    class cart {
        int[] rom; //store words here
    }
}
