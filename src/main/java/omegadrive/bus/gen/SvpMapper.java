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
import omegadrive.ssp16.Ssp16;
import omegadrive.util.Size;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static omegadrive.ssp16.Ssp16.*;
import static omegadrive.ssp16.Ssp16Types.Ssp1601_t;
import static omegadrive.ssp16.Ssp16Types.Ssp16Reg.SSP_PM0;
import static omegadrive.ssp16.Ssp16Types.Ssp16Reg.SSP_XST;
import static omegadrive.ssp16.Ssp16Types.Svp_t;

public class SvpMapper implements RomMapper, SvpBus {

    private static final Logger LOG = LogManager.getLogger(SvpMapper.class.getSimpleName());

    private static final boolean verbose = false;
    private static final boolean VR_TEST_MODE = false;

    //TODO fix
    public static Ssp16 ssp16 = NO_SVP;
    private static SvpMapper instance = null;

    protected Svp_t svpCtx;
    protected Ssp1601_t sspCtx;

    protected RomMapper baseMapper;

    protected SvpMapper(RomMapper baseMapper, Ssp16 ssp16p) {
        this.baseMapper = baseMapper;
        ssp16 = ssp16p;
        this.svpCtx = ssp16p.getSvpContext();
        this.sspCtx = this.svpCtx.ssp1601;
        instance = this;
    }

    public static SvpMapper createInstance(RomMapper baseMapper, Ssp16 ssp16) {
        return new SvpMapper(baseMapper, ssp16);
    }

    public static SvpMapper createInstance(RomMapper baseMapper, IMemoryProvider memoryProvider) {
        return createInstance(baseMapper, Ssp16.createSvp(memoryProvider));
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
    public long m68kSvpRegRead(int address, Size size) {
        return m68kSvpRegRead(sspCtx, address, size);
    }

    @Override
    public void m68kSvpRegWrite(int address, long data, Size size) {
        m68kSvpRegWrite(sspCtx, address, data, size);
    }

    //68k writing data
    @Override
    public void m68kSvpWriteData(long addressL, long data, Size size) {
        m68kSvpWriteData(svpCtx, addressL, data, size);
    }

    @Override
    public long m68kSvpReadData(long addressL, Size size) {
        return m68kSvpReadData(svpCtx, addressL, size);
    }

    protected final long m68kSvpRegRead(Ssp1601_t sspCtx, int address, Size size) {
        switch (size) {
            case BYTE:
                long val = svpRegReadWord(sspCtx, address & ~1);
                return (address & 1) > 0 ? (val & 0xFF) : (val >> 8);
            case WORD:
                return svpRegReadWord(sspCtx, address);
            case LONG:
                return svpRegReadWord(sspCtx, address) << 16 | svpRegReadWord(sspCtx, address + 2);
        }
        return 0xFFFF_FFFF;
    }

    protected final void m68kSvpRegWrite(Ssp1601_t sspCtx, int address, long data, Size size) {
        switch (size) {
            case BYTE:
                LOG.error("Unexpected write {}, {}, {}", address, data, size);
                break;
            case WORD:
                svpRegWriteWord(sspCtx, address, data);
                break;
            case LONG:
                svpRegWriteWord(sspCtx, address, data >> 16);
                svpRegWriteWord(sspCtx, address + 2, data & 0xFFFF);
                break;
        }
    }

    protected final long m68kSvpReadData(Svp_t svpCtx, long addressL, Size size) {
        int address = (int) (addressL & 0xFF_FFFF);
        if (address >= SVP_MAP_DRAM_START_ADDR_BYTE && address < SVP_MAP_DRAM_END_ADDR_BYTE) {
            //        LOG.debug("svp DRAM read: {}", Integer.toHexString(addressWord));
            switch (size) {
                case WORD:
                    return svpCtx.readRamWord(address >> 1);
                case LONG:
                    return (long) svpCtx.readRamWord(address >> 1) << 16 |
                            svpCtx.readRamWord((address >> 1) + 1);
                case BYTE:
                    LOG.error("Unexpected byte-wide read: {}", Integer.toHexString(address));
                    return 0xFF;
            }
        } else if (address >= SVP_MAP_DRAM_CELL_1_START_BYTE && address < SVP_MAP_DRAM_CELL_1_END_BYTE) {
//            LOG.debug("svp svpca1 read: {} {}", Integer.toHexString(address), size);
            // this is 68k code rewritten
            long a1 = addressL >> 1;
            a1 = (a1 & 0x7001) | ((a1 & 0x3e) << 6) | ((a1 & 0xfc0) >> 5);
            return svpCtx.readRamWord((int) a1);
        } else if (address >= SVP_MAP_DRAM_CELL_2_START_BYTE && address < SVP_MAP_DRAM_CELL_2_END_BYTE) {
//            LOG.debug("svp svpca2 read: {} {}", Integer.toHexString(address), size);
            long a1 = addressL >> 1;
            a1 = (a1 & 0x7801) | ((a1 & 0x1e) << 6) | ((a1 & 0x7e0) >> 4);
            return svpCtx.readRamWord((int) a1);
        }
        //VR test mode - trigger once otherwise we break checksum
//        if (VR_TEST_MODE && address == 0x201E0) {
//            return 0x4e71;
//        }
        return baseMapper.readData(addressL, size);
    }

    protected final void svpRegWriteWord(Ssp1601_t sspCtx, int address, long data) {
        switch (address & 0xF) {
            case 0:
            case 2:
                if (verbose) {
                    LOG.info("Svp write command register {}, {}", Integer.toHexString(address), Long.toHexString(data));
                }
                int pm0 = sspCtx.getRegisterValue(SSP_PM0);
                sspCtx.setRegisterValue(SSP_PM0, pm0 | 2);
                sspCtx.setRegisterValue(SSP_XST, (int) (data & 0xFFFF));
                int es = sspCtx.getEmu_status();
                sspCtx.setEmu_status(es & ~SSP_WAIT_PM0);
                break;
            default:
                LOG.debug("Svp unexpected register write {}, {}", Integer.toHexString(address), Long.toHexString(data));
        }
    }

    private final long svpRegReadWord(Ssp1601_t sspCtx, int address) {
        int res;
        switch (address & 0xF) {
            case 0:
            case 2:
                res = sspCtx.getRegisterValue(SSP_XST);
//                LOG.debug("Svp read command register: {}", res);
                return res;
            case 4:
                int pm0 = sspCtx.getRegisterValue(SSP_PM0);
                sspCtx.setRegisterValue(SSP_PM0, pm0 & ~1);
//                LOG.debug("Svp read status register: {}", pm0);
                return pm0;
            default:
                LOG.warn("Svp unexpected register read {}", Integer.toHexString(address));
        }
        return 0xFFFF;
    }

    protected final void svpMemoryWriteWord(Svp_t svpCtx, int addressByte, int data) {
        if (data > 0) {
            if (addressByte == SVP_CMD_SENT_FLAG_BYTE) sspCtx.setEmu_status(sspCtx.getEmu_status() & ~SSP_WAIT_30FE06);
            else if (addressByte == SVP_CMD_ID_FLAG_BYTE)
                sspCtx.setEmu_status(sspCtx.getEmu_status() & ~SSP_WAIT_30FE08);
        }
        svpCtx.writeRamWord((addressByte >> 1), data);
//        LOG.info("svp write word {}_w, value {}", Integer.toHexString(addressByte >> 1), Long.toHexString(data));
    }

    protected final void m68kSvpWriteData(Svp_t svpCtx, long addressL, long data, Size size) {
        int address = (int) (addressL & 0xFF_FFFF);
        data &= size.getMask();
        if (address >= SVP_MAP_DRAM_START_ADDR_BYTE && address < SVP_MAP_DRAM_END_ADDR_BYTE) {
            switch (size) {
                case WORD:
                    svpMemoryWriteWord(svpCtx, address, (int) data);
                    break;
                case LONG:
                    svpMemoryWriteWord(svpCtx, address, (int) (data >> 16));
                    svpMemoryWriteWord(svpCtx, address + 2, (int) (data & 0xFFFF));
                    break;
                case BYTE:
                    LOG.error("Unexpected byte-wide write: {}, {}", Integer.toHexString(address), Long.toHexString(data));
                    break;
            }
            return;
        }
        baseMapper.writeData(addressL, data, size);
    }

    public static void setSvpContext(Svp_t svpCtx) {
        if (svpCtx == null) {
            return;
        }
        instance.svpCtx = svpCtx;
        instance.sspCtx = svpCtx.ssp1601;
        ssp16.loadSvpContext(svpCtx);
    }
}
