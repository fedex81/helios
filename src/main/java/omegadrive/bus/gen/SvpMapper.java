/*
 * GenesisBus
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 13:52
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.bus.gen;

import omegadrive.cart.mapper.RomMapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.Size;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static omegadrive.bus.gen.Ssp16.*;
import static omegadrive.bus.gen.Ssp16.Ssp16Reg.SSP_PM0;
import static omegadrive.bus.gen.Ssp16.Ssp16Reg.SSP_XST;

public class SvpMapper implements RomMapper, SvpBus {


    private static final Logger LOG = LogManager.getLogger(SvpMapper.class.getSimpleName());

    public static boolean verbose = false;
    public static boolean VR_TEST_MODE = false;
    public static Ssp16Impl svp;
    public static SvpMapper instance;
    /**
     * $30FE02 - Command finished flag
     * $30FE04 - Tile buffer address
     * $30FE06 - Command sent flag
     * $30FE08 - Command ID
     * $30FE10 - Command parameter (SEGA logo sequence frame ID, title screen end transition sequence frame ID)
     */
    String[] str = {
            "", "", "Command finished flag", "",
            "Tile buffer address", "", "Command sent flag", "", "Command ID", "",};
    private RomMapper baseMapper;
    private IMemoryProvider memoryProvider;

    public SvpMapper(RomMapper baseMapper, IMemoryProvider memoryProvider) {
        this.baseMapper = baseMapper;
        this.memoryProvider = memoryProvider;
        svp = createSvp();
        instance = this;
    }

    private static void loadSvpIram(svp_t svpCtx, cart cart, int startAddrRomByte) {
        int k = startAddrRomByte >> 1;
        int limit = Math.min(cart.rom.length, svpCtx.iram_rom.length);
        for (int i = startAddrRomByte; i < limit; i += 2) {
            svpCtx.iram_rom[k++] = (cart.rom[i] << 8 | cart.rom[i + 1]) & 0xFFFF;
        }
    }

    @Override
    public long readData(long addressL, Size size) {
        return m68kSvpReadData(addressL, size);
    }

    //68k writing data
    @Override
    public void writeData(long addressL, long data, Size size) {
        m68kSvpWriteData(addressL, data, size);
    }

    @Override
    public long m68kSvpReadData(long addressL, Size size) {
        int address = (int) (addressL & 0xFF_FFFF);
        if (address >= SVP_MAP_DRAM_START_ADDR_BYTE && address < SVP_MAP_DRAM_END_ADDR_BYTE) {
            LOG.debug("svp DRAM read: {}", Integer.toHexString(address));
//            if(address == 0x30FE02){ //fake the command finished flag
//                return 1;
//            }
            int data = svp.svp.dram[(address >> 1) & 0xFFFE];
            if (address >= 0x30FE00 && address <= 0x30FE10) { //fake the command finished flag
                if ((address & 0x1F) < 10) {
                    LOG.info("Read {} : {}", str[address & 0x1F], data);
                }
                if ((address & 0x1F) == 0x30FE10) {
                    LOG.info("Read command parameter : {}", data);
                }
            }
            return data;
        } else if (address >= 0x39_0000 && address < 0x3A_0000) { // "cell arrange" 1: 390000-39ffff
            LOG.warn("svp svpca1 read: {} {}", Integer.toHexString(address), size);
            // this is 68k code rewritten
            long a1 = addressL >> 1;
            a1 = (a1 & 0x7001) | ((a1 & 0x3e) << 6) | ((a1 & 0xfc0) >> 5);
            return svp.svp.dram[(int) a1];
        } else if (address >= 0x3a_0000 && address < 0x3B_0000) { // "cell arrange" 2: 3a0000-3affff
            LOG.warn("svp svpca2 read: {} {}", Integer.toHexString(address), size);
            long a1 = addressL >> 1;
            a1 = (a1 & 0x7801) | ((a1 & 0x1e) << 6) | ((a1 & 0x7e0) >> 4);
            return svp.svp.dram[(int) a1];
        }
        //VR test mode
        if (VR_TEST_MODE && address == 0x201E0) {
            return 0x4e71;
        }
        return baseMapper.readData(addressL, size);
    }

    @Override
    public long m68kSvpRegRead(int address, Size size) {
        switch (size) {
            case BYTE:
                long val = svpRegReadWord(address & ~1);
//                if (!(a & 1)) d >>= 8;
                return (address & 1) > 0 ? (val & 0xFF) : (val >> 8);
            case WORD:
                return svpRegReadWord(address);
            case LONG:
                return svpRegReadWord(address) << 16 | svpRegReadWord(address + 2);
        }
        return 0xFFFF;
    }

    @Override
    public void m68kSvpRegWrite(int address, long data, Size size) {
        switch (size) {
            case BYTE:
                LOG.error("Unexpected write {}, {}, {}", address, data, size);
                break;
            case WORD:
                svpRegWriteWord(address, data);
                break;
            case LONG:
                svpRegWriteWord(address, data >> 16);
                svpRegWriteWord(address + 2, data & 0xFFFF);
//                svpRegWriteWord(address, data & 0xFFFF);
                break;
        }
    }

    private long svpRegReadWord(int address) {
        int res;
        switch (address & 0xF) {
            case 0:
            case 2:
                res = svp.ssp.gr[SSP_XST.ordinal()].h;
                LOG.info("Svp read command register: {}", res);
                return res;
            case 4:
                int pm0 = svp.ssp.gr[SSP_PM0.ordinal()].h;
                svp.ssp.gr[SSP_PM0.ordinal()].setH(pm0 & ~1);
                LOG.info("Svp read status register: {}", pm0);
                return pm0;
            case 6:
                LOG.info("Svp read halt register");
                break;
            case 8:
                LOG.info("Svp read interrupt register");
                break;
            default:
                LOG.warn("Svp unexpected register read {}", Integer.toHexString(address));
        }
        return 0xFFFF;
    }

    private void svpRegWriteWord(int address, long data) {
        switch (address & 0xF) {
            case 0:
            case 2:
                LOG.info("Svp write command register {}, {}", Integer.toHexString(address), Long.toHexString(data));
                svp.ssp.gr[SSP_XST.ordinal()].setH((int) (data & 0xFFFF));
                int val = svp.ssp.gr[SSP_PM0.ordinal()].h;
                svp.ssp.gr[SSP_PM0.ordinal()].setH(val | 2);
                svp.ssp.emu_status &= ~SSP_WAIT_PM0;
                break;
            case 4:
                LOG.info("Svp write status register {}, {}", Integer.toHexString(address), Long.toHexString(data));
                break;
            case 6:
                LOG.info("Svp write halt register {}, {}", Integer.toHexString(address), Long.toHexString(data));
                break;
            case 8:
                LOG.info("Svp write interrupt register {}, {}", Integer.toHexString(address), Long.toHexString(data));
                break;
            default:
                LOG.info("Svp unexpected register write {}, {}", Integer.toHexString(address), Long.toHexString(data));
        }
    }

    public Ssp16Impl createSvp() {
        cart svpCart = new cart();
        svpCart.rom = memoryProvider.getRomData();
        ssp1601_t sspCtx = new ssp1601_t();
        svp_t svpCtx = new svp_t();
        loadSvpIram(svpCtx, svpCart, SVP_ROM_START_ADDRESS_BYTE);

        Ssp16Impl ssp16 = Ssp16Impl.createInstance(this, sspCtx, svpCtx, svpCart);
        svpCtx.ssp1601 = sspCtx;
        ssp16.ssp1601_reset(sspCtx);
        return ssp16;
    }

    private void svpMemoryWriteWord(int address, int data) {
        if (data > 0) {
            if (address == SVP_CMD_SENT_FLAG_BYTE) svp.ssp.emu_status &= ~SSP_WAIT_30FE06;
            else if (address == SVP_CMD_ID_FLAG_BYTE) svp.ssp.emu_status &= ~SSP_WAIT_30FE08;
        }
        if (address >= 0x30FE00 && address <= 0x30FE10) { //fake the command finished flag
            if ((address & 0x1F) < 10) {
                LOG.info("Write {} : {}", str[address & 0x1F], data);
            }
            if ((address & 0x1F) == 0x30FE10) {
                LOG.info("Write Command parameter : {}", data);
            }
        }

        svp.svp.dram[(address >> 1) & 0xFFFE] = data & 0xFFFF;
        LOG.debug("svp write {}, value {}", Integer.toHexString(address), Long.toHexString(data));
    }

    @Override
    public void svpWriteDataWord(long addressWord, int data) {
        int address = (int) (addressWord & 0xFF_FFFF);
        if (address >= SVP_MAP_DRAM_START_ADDR_WORD && address < SVP_MAP_DRAM_END_ADDR_WORD) {
            svpMemoryWriteWord(address << 1, data);
        } else if (address >= SVP_MAP_IRAM_START_ADDR_WORD && address < SVP_MAP_IRAM_END_ADDR_WORD) {
            svp.svp.iram_rom[address & 0x3FF] = data;
        } else {
            LOG.warn("Unexpected svp write {} ,{}", Long.toHexString(addressWord), Integer.toHexString(data));
//            baseMapper.writeData(addressWord << 1, data, Size.WORD);
        }
    }

    @Override
    public int svpReadDataWord(long addressWord) {
        int address = (int) (addressWord & 0xFF_FFFF);
        if (address >= SVP_MAP_DRAM_START_ADDR_WORD && address < SVP_MAP_DRAM_END_ADDR_WORD) {
            LOG.debug("svp DRAM read: {}", Integer.toHexString(address));
//            if(address == 0x30FE02){ //fake the command finished flag
//                return 1;
//            }
            int data = svp.svp.dram[address & 0xFFFE];
            if (address >= (0x30FE00 >> 1) && address <= (0x30FE10 >> 1)) {
                if ((address & 0x1F) < 10) {
                    LOG.debug("Read {} : {}", str[address & 0x1F], data);
                }
                if ((address & 0x1F) == (0x30FE10 >> 1)) {
                    LOG.info("Read command parameter : {}", data);
                }
            }
            return data;
        } else if (address >= SVP_MAP_IRAM_START_ADDR_WORD && address < SVP_MAP_IRAM_END_ADDR_WORD) {
            int res = svp.svp.iram_rom[address & 0x3FF];
            LOG.info("svp IRAM read: {}, {}", Integer.toHexString(address), Integer.toHexString(res));
            return res;
//        } else if(address >= 0 && address <= 0x1F_FFFF) { //ROM
//            int addr = address; // >> 1;
//            return svp.cart.rom[addr] << 8 | svp.cart.rom[addr+1];
        }
//        LOG.warn("Unexpected svp read {}", Long.toHexString(addressWord));
        return (int) baseMapper.readData(addressWord << 1, Size.WORD);
//        return 0xFF;
    }

    //68k writing data
    @Override
    public void m68kSvpWriteData(long addressL, long data, Size size) {
        int address = (int) (addressL & 0xFF_FFFF);
        data &= size.getMask();
        if (address >= SVP_MAP_DRAM_START_ADDR_BYTE && address < SVP_MAP_DRAM_END_ADDR_BYTE) {
            switch (size) {
                case WORD:
                    svpMemoryWriteWord(address, (int) data);
                    break;
                case LONG:
                    svpMemoryWriteWord(address, (int) (data >> 16));
                    svpMemoryWriteWord(address + 2, (int) (data & 0xFFFF));
                    break;
                case BYTE:
                    LOG.error("oops");
                    break;
            }
            return;
        }
        baseMapper.writeData(addressL, data, size);
    }
}
