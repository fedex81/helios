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
    final static String[] str = {
            "", "", "Command finished flag", "", //3
            "Tile buffer address", "", "Command sent flag", "", //7
            "Command ID", "", "", "", "Command parameter", "", "", "", ""};
    boolean once = false;
    boolean is68k = false;


    public SvpMapper(RomMapper baseMapper, IMemoryProvider memoryProvider) {
        this.baseMapper = baseMapper;
        this.memoryProvider = memoryProvider;
        svp = createSvp();
        instance = this;
    }

    private RomMapper baseMapper;

    @Override
    public long readData(long addressL, Size size) {
        return m68kSvpReadData(addressL, size);
    }

    //68k writing data
    @Override
    public void writeData(long addressL, long data, Size size) {
        m68kSvpWriteData(addressL, data, size);
    }

    private IMemoryProvider memoryProvider;

    private void loadSvpMemory(svp_t svpCtx, cart cart, int startAddrRomByte) {
        int[] romBytes = memoryProvider.getRomData();
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

    @Override
    public long m68kSvpRegRead(int address, Size size) {
        switch (size) {
            case BYTE:
                long val = svpRegReadWord(address & ~1);
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
                break;
        }
    }

    @Override
    public long m68kSvpReadData(long addressL, Size size) {
        int address = (int) (addressL & 0xFF_FFFF);

        is68k = true;
        if (address >= SVP_MAP_DRAM_START_ADDR_BYTE && address < SVP_MAP_DRAM_END_ADDR_BYTE) {
            switch (size) {
                case WORD:
                    return svpMemoryReadWord(address >> 1);
                case LONG:
                    return svpMemoryReadWord(address >> 1) << 16 |
                            svpMemoryReadWord((address >> 1) + 1);
                case BYTE:
                    LOG.error("Unexpected byte-wide read: {}", Integer.toHexString(address));
                    return 0xFF;
            }
        } else if (address >= SVP_MAP_DRAM_CELL_1_START_BYTE && address < SVP_MAP_DRAM_CELL_1_END_BYTE) {
            if (size != Size.WORD) {
                LOG.error("oops");
            }
            LOG.debug("svp svpca1 read: {} {}", Integer.toHexString(address), size);
            // this is 68k code rewritten
            long a1 = addressL >> 1;
            a1 = (a1 & 0x7001) | ((a1 & 0x3e) << 6) | ((a1 & 0xfc0) >> 5);
            return svp.svp.dram[(int) a1];
        } else if (address >= SVP_MAP_DRAM_CELL_2_START_BYTE && address < SVP_MAP_DRAM_CELL_2_END_BYTE) {
            if (size != Size.WORD) {
                LOG.error("oops");
            }
            LOG.debug("svp svpca2 read: {} {}", Integer.toHexString(address), size);
            long a1 = addressL >> 1;
            a1 = (a1 & 0x7801) | ((a1 & 0x1e) << 6) | ((a1 & 0x7e0) >> 4);
            return svp.svp.dram[(int) a1];
        }
        //VR test mode - trigger once otherwise we break checksum
        if (VR_TEST_MODE && !once && address == 0x201E0) {
            once = true;
            return 0x4e71;
        }
        is68k = false;
        return baseMapper.readData(addressL, size);
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

    private long svpRegReadWord(int address) {
        int res;
        switch (address & 0xF) {
            case 0:
            case 2:
                res = svp.ssp.gr[SSP_XST.ordinal()].h & 0xFFFF;
                LOG.info("Svp read command register: {}", res);
                return res;
            case 4:
                int pm0 = svp.ssp.gr[SSP_PM0.ordinal()].h & 0xFFFF;
                svp.ssp.gr[SSP_PM0.ordinal()].setH(pm0 & ~1);
                LOG.debug("Svp read status register: {}", pm0);
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

    public Ssp16Impl createSvp() {
        cart svpCart = new cart();
        ssp1601_t sspCtx = new ssp1601_t();
        svp_t svpCtx = new svp_t();
        loadSvpMemory(svpCtx, svpCart, SVP_ROM_START_ADDRESS_BYTE);

        Ssp16Impl ssp16 = Ssp16Impl.createInstance(this, sspCtx, svpCtx, svpCart);
        svpCtx.ssp1601 = sspCtx;
        ssp16.ssp1601_reset(sspCtx);
        return ssp16;
    }

    private void svpMemoryWriteWord(int addressByte, int data) {
        if (data > 0) {
            if (addressByte == SVP_CMD_SENT_FLAG_BYTE) svp.ssp.emu_status &= ~SSP_WAIT_30FE06;
            else if (addressByte == SVP_CMD_ID_FLAG_BYTE) svp.ssp.emu_status &= ~SSP_WAIT_30FE08;
        }
        checkCommandAccess(addressByte >> 1, data, true);
        svp.svp.dram[(addressByte >> 1) & 0xFFFF] = data & 0xFFFF;
        LOG.debug("svp write {}, value {}", Integer.toHexString(addressByte), Long.toHexString(data));
    }

    private int svpMemoryReadWord(int addressWord) {
        LOG.debug("svp DRAM read: {}", Integer.toHexString(addressWord));
        int data = svp.svp.dram[addressWord & 0xFFFF];
        checkCommandAccess(addressWord, data, false);
        return data;
    }

    private void checkCommandAccess(int addressWord, int data, boolean write) {
        int address = addressWord << 1;
        if (address >= 0x30FE00 && address < 0x30FE10) {
            if (write) {
                LOG.info((is68k ? "68k" : "Svp") + " Write {} : {}", str[address & 0x1F], data);
            } else {
                LOG.debug((is68k ? "68k" : "Svp") + " Read {} : {}", str[address & 0x1F], data);
            }
        }
    }

    @Override
    public void svpWriteDataWord(long addressWord, int data) {
        int address = (int) (addressWord & 0xFF_FFFF);
        if (address >= SVP_MAP_DRAM_START_ADDR_WORD && address < SVP_MAP_DRAM_END_ADDR_WORD) {
            svpMemoryWriteWord(address << 1, data);
        } else if (address >= SVP_MAP_IRAM_START_ADDR_WORD && address < SVP_MAP_IRAM_END_ADDR_WORD) {
            svp.svp.iram_rom[address & 0x3FF] = data;
        } else {
            LOG.warn("Unexpected svp write {}_w ,{}", Long.toHexString(addressWord),
                    Integer.toHexString(data));
        }
    }

    @Override
    public int svpReadDataWord(long addressWord) {
        int address = (int) (addressWord & 0xFF_FFFF);
        if (address >= SVP_MAP_DRAM_START_ADDR_WORD && address < SVP_MAP_DRAM_END_ADDR_WORD) {
            return svpMemoryReadWord(address);
        } else if (address >= SVP_MAP_IRAM_START_ADDR_WORD && address < SVP_MAP_IRAM_END_ADDR_WORD) {
            int res = svp.svp.iram_rom[address & 0x3FF];
            LOG.debug("svp IRAM read: {}, {}", Integer.toHexString(address), Integer.toHexString(res));
            return res;
        } else if (address >= 0 && address <= 0xF_FFFF) { //ROM
            return (int) baseMapper.readData(addressWord << 1, Size.WORD);
        }
        LOG.warn("Unexpected svp read: {}_w", Long.toHexString(addressWord));
        return 0xFF;
    }

    //68k writing data
    @Override
    public void m68kSvpWriteData(long addressL, long data, Size size) {
        int address = (int) (addressL & 0xFF_FFFF);
        data &= size.getMask();
        if (address >= SVP_MAP_DRAM_START_ADDR_BYTE && address < SVP_MAP_DRAM_END_ADDR_BYTE) {
            is68k = true;
            switch (size) {
                case WORD:
                    svpMemoryWriteWord(address, (int) data);
                    break;
                case LONG:
                    svpMemoryWriteWord(address, (int) (data >> 16));
                    svpMemoryWriteWord(address + 2, (int) (data & 0xFFFF));
                    break;
                case BYTE:
                    LOG.error("Unexpected byte-wide write: {}, {}", Integer.toHexString(address), Long.toHexString(data));
                    break;
            }
            is68k = false;
            return;
        }
        baseMapper.writeData(addressL, data, size);
    }
}
