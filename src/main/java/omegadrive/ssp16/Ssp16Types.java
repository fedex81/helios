package omegadrive.ssp16;

import java.util.stream.IntStream;

import static omegadrive.ssp16.Ssp16.*;

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
public interface Ssp16Types {

    Svp_t NO_SVP_CONTEXT = new Svp_t(new Ssp1601_t());

    /* register names */
    enum Ssp16Reg {
        SSP_GR0, SSP_X, SSP_Y, SSP_A,
        SSP_ST, SSP_STACK, SSP_PC, SSP_P,
        SSP_PM0, SSP_PM1, SSP_PM2, SSP_XST,
        SSP_PM4, SSP_gr13, SSP_PMC, SSP_AL
    }

    class Ssp_reg_t {
        public int v; //unsigned 32 bit
        public short l; //unsigned 16 bit
        public short h; //unsigned 16 bit

        public void setV(long v) {
            this.v = (int) v;
            this.h = (short) (this.v >> 16);
            this.l = (short) (this.v & 0xFFFF);
        }

        public final void setH(int h) {
            this.h = (short) h;
            this.v = (h << 16) | (v & 0xFFFF);
        }

        public void setL(int l) {
            this.l = (short) l;
            this.v = (v & 0xFFFF_0000) | (l & 0xFFFF);
        }
    }

    class Ssp1601_t {
        public mem mem = new mem();
        public ptr ptr = new ptr();
        public Ssp_reg_t[] gr = new Ssp_reg_t[16];  /* general registers */
        public short[] stack = new short[6];
        public int emu_status;
        public int[][] pmac = new int[2][6];  /* read/write modes/addrs for PM0-PM5 */

        {
            IntStream.range(0, gr.length).forEach(i -> gr[i] = new Ssp_reg_t());
        }

        public byte[] getR() {
            return null;
        }

        public int[] getR(boolean zero) {
            return zero ? ptr.bank.r0 : ptr.bank.r1;
        }

        public int getEmu_status() {
            return emu_status;
        }

        public void setEmu_status(int emu_status) {
            this.emu_status = emu_status;
        }

        public void setRegisterValue(Ssp16Reg reg, int value) {
            gr[reg.ordinal()].h = (short) value;
        }

        public int getRegisterValue(Ssp16Reg reg) {
            return gr[reg.ordinal()].h;
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

        public class ptr {
            final static int REGS_PER_BANK = 4;
            public bank bank = new bank();

            public int getPointerVal(int pos) {
                return pos < REGS_PER_BANK ? bank.r0[pos] : bank.r1[pos - REGS_PER_BANK];
            }

            public void setPointerVal(int pos, int val) {
                int pos1 = pos % REGS_PER_BANK;
                if (pos1 == 3) { //r3 and r7 cannot be modified
                    return;
                }
                int[] rg = pos < REGS_PER_BANK ? bank.r0 : bank.r1;
                rg[pos1] = val & SSP_POINTER_REGS_MASK;
            }

            /* BANK pointers */ //8 bit unsigned
            public class bank {
                public int[] r0 = new int[REGS_PER_BANK]; //8 bit unsigned
                public int[] r1 = new int[REGS_PER_BANK]; //8 bit unsigned
            }
        }
    }

    class Svp_t {
        public Ssp1601_t ssp1601;
        public int[] iram_rom = new int[IRAM_ROM_SIZE_WORDS]; /* IRAM (0-0x7ff) and program ROM (0x800-0x1ffff) */
        public int[] dram = new int[DRAM_SIZE_WORDS];

        protected Svp_t(Ssp1601_t ssp1601) {
            this.ssp1601 = ssp1601;
        }

        public void writeRamWord(int addressWord, int value) {
            dram[addressWord & MASK_16BIT] = value & MASK_16BIT;
        }

        public int readRamWord(int addressWord) {
            return dram[addressWord & MASK_16BIT] & MASK_16BIT;
        }
    }

    class Cart {
        public int[] rom; //store words here
    }
}
