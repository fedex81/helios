package omegadrive.ssp16;

import omegadrive.memory.IMemoryProvider;

import java.util.stream.IntStream;

/*
 * basic, incomplete SSP160x (SSP1601?) interpreter
 *
 * Copyright (c) Gražvydas "notaz" Ignotas, 2008
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the organization nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Java translation by Federico Berti
 */
public interface Ssp16 {

    int SSP_PMC_HAVE_ADDR = 0x0001; /* address written to PMAC, waiting for mode */
    int SSP_PMC_SET = 0x0002; /* PMAC is set */
    int SSP_HANG = 0x1000; /* 68000 hangs SVP */
    int SSP_WAIT_PM0 = 0x2000; /* bit1 in PM0 */
    int SSP_WAIT_30FE06 = 0x4000; /* ssp tight loops on 30FE08 to become non-zero */
    int SSP_WAIT_30FE08 = 0x8000; /* same for 30FE06 */
    int SSP_WAIT_MASK = 0xf000;

    int SSP_RAM_SIZE_WORDS = 256;
    int SSP_RAM_MASK_WORDS = SSP_RAM_SIZE_WORDS - 1;
    int SSP_POINTER_REGS_MASK = 0xFF;
    int MASK_16BIT = 0xFFFF;
    int PC_MASK = MASK_16BIT;

    int IRAM_ROM_SIZE_WORDS = 0x10000; //128 kbytes -> 64k words
    int IRAM_SIZE_WORDS = 0x400; //2kbytes -> 1k words
    int ROM_SIZE_WORDS = IRAM_ROM_SIZE_WORDS - IRAM_SIZE_WORDS; //63k words
    int DRAM_SIZE_WORDS = 0x10000; //128Kbytes -> 64k words

    int SVP_ROM_START_ADDRESS_BYTE = 0x800;
    int SVP_ROM_START_ADDRESS_WORD = SVP_ROM_START_ADDRESS_BYTE >> 1;

    Ssp16 NO_SVP = new Ssp16() {
        @Override
        public void ssp1601_reset(ssp1601_t ssp) {

        }

        @Override
        public void ssp1601_run(int cycles) {

        }
    };

    svp_t NO_SVP_CONTEXT = new svp_t(new ssp1601_t());

    static Ssp16 createSvp(IMemoryProvider memoryProvider) {
        cart svpCart = new cart();
        ssp1601_t sspCtx = new ssp1601_t();
        svp_t svpCtx = new svp_t(sspCtx);
        loadSvpMemory(svpCtx, svpCart, SVP_ROM_START_ADDRESS_BYTE, memoryProvider.getRomData());

        Ssp16Impl ssp16 = Ssp16Impl.createInstance(sspCtx, svpCtx, svpCart);
        ssp16.ssp1601_reset(sspCtx);
        return ssp16;
    }

    static void loadSvpMemory(svp_t svpCtx, cart cart, int startAddrRomByte, int[] romBytes) {
        cart.rom = new int[romBytes.length >> 1]; //words
        int k = 0;
        for (int i = 0; i < romBytes.length; i += 2) {
            cart.rom[k] = ((romBytes[i] << 8) | romBytes[i + 1]) & 0xFFFF;
            if (i >= startAddrRomByte && k < svpCtx.iram_rom.length) {
                svpCtx.iram_rom[k] = cart.rom[k];
            }
            k++;
        }
    }

    void ssp1601_reset(ssp1601_t ssp);

    void ssp1601_run(int cycles);

    default svp_t getSvpContext() {
        return NO_SVP_CONTEXT;
    }

    /* register names */
    enum Ssp16Reg {
        SSP_GR0, SSP_X, SSP_Y, SSP_A,
        SSP_ST, SSP_STACK, SSP_PC, SSP_P,
        SSP_PM0, SSP_PM1, SSP_PM2, SSP_XST,
        SSP_PM4, SSP_gr13, SSP_PMC, SSP_AL
    }

    class ssp_reg_t {
        public int v; //unsigned 32 bit
        public short l; //unsigned 16 bit
        public short h; //unsigned 16 bit

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
        public ssp_reg_t[] gr = new ssp_reg_t[16];  /* general registers */
        short[] stack = new short[6];
        public int emu_status;
        int[][] pmac = new int[2][6];  /* read/write modes/addrs for PM0-PM5 */

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
        public int[] iram_rom = new int[IRAM_ROM_SIZE_WORDS]; /* IRAM (0-0x7ff) and program ROM (0x800-0x1ffff) */
        public int[] dram = new int[DRAM_SIZE_WORDS];

        protected svp_t(ssp1601_t ssp1601) {
            this.ssp1601 = ssp1601;
        }
    }

    class cart {
        public int[] rom; //store words here
    }
}
