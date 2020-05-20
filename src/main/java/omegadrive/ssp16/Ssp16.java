package omegadrive.ssp16;

import omegadrive.memory.IMemoryProvider;
import omegadrive.ssp16.Ssp16Types.Ssp1601_t;

import static omegadrive.ssp16.Ssp16Types.Cart;
import static omegadrive.ssp16.Ssp16Types.Svp_t;

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
        public void ssp1601_reset(Ssp1601_t ssp) {

        }

        @Override
        public void ssp1601_run(int cycles) {

        }
    };

    static Ssp16 createSvp(IMemoryProvider memoryProvider) {
        Cart svpCart = new Cart();
        Ssp1601_t sspCtx = new Ssp1601_t();
        Svp_t svpCtx = new Svp_t(sspCtx);
        loadSvpMemory(svpCtx, svpCart, SVP_ROM_START_ADDRESS_BYTE, memoryProvider.getRomData());

        Ssp16Impl ssp16 = Ssp16Impl.createInstance(sspCtx, svpCtx, svpCart);
        ssp16.ssp1601_reset(sspCtx);
        return ssp16;
    }

    static void loadSvpMemory(Svp_t svpCtx, Cart cart, int startAddrRomByte, int[] romBytes) {
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

    void ssp1601_reset(Ssp1601_t ssp);

    void ssp1601_run(int cycles);

    default Svp_t getSvpContext() {
        return Ssp16Types.NO_SVP_CONTEXT;
    }
}
