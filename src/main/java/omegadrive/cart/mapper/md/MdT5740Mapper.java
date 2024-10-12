package omegadrive.cart.mapper.md;

// license:BSD-3-Clause
// copyright-holders:Fabio Priuli, MetalliC

import omegadrive.SystemLoader;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cart.mapper.BackupMemoryMapper;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.cart.mapper.md.eeprom.EepromBase;
import omegadrive.cart.mapper.md.eeprom.SpiEeprom;
import omegadrive.util.ArrayEndianUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;

import static omegadrive.bus.model.MdBusProvider.DEFAULT_ROM_END_ADDRESS;
import static omegadrive.bus.model.MdBusProvider.TIME_LINE_START;
import static omegadrive.cart.mapper.md.Ssf2Mapper.BANK_MASK;
import static omegadrive.cart.mapper.md.Ssf2Mapper.BANK_SHIFT;
import static omegadrive.util.Util.th;

/**************************************************************************
 license:BSD-3-Clause
 copyright-holders:Fabio Priuli, MetalliC

 MegaDrive / Genesis Cart + STM95 EEPROM device
 see https://github.com/mamedev/mame/blob/23b19b492e794b5da4cd3e978e1ed2fa75edef20/src/devices/bus/megadrive/stm95.cpp#L4
 **************************************************************************/

/**
 * 2023-10
 * - java translation and adaptation
 * <p>
 * Copyright (C) 2023 Federico Berti
 */
public class MdT5740Mapper extends BackupMemoryMapper implements RomMapper, RomMapper.StateAwareMapper {

    private static final Logger LOG = LogHelper.getLogger(MdT5740Mapper.class.getSimpleName());
    private static byte[] prot_15e6 = {0, 0, 0, 0x10};

    private static final int BANKBABLE_ADDR_START = 0x280000;

    private static final String fileType = "srm";

    EepromBase eeprom;
    byte[] m_bank;
    int m_rdcnt;

    RomMapper baseMapper;


    void device_reset() {
        m_rdcnt = 0;
        m_bank[0] = 0;
        m_bank[1] = 0;
        m_bank[2] = 0;
    }

    public static MdT5740Mapper createInstance(String romName, RomMapper baseMapper) {
        MdT5740Mapper m = new MdT5740Mapper(romName, baseMapper);
        return m;
    }

    private MdT5740Mapper(String romName, RomMapper baseMapper) {
        super(SystemLoader.SystemType.MD, fileType, romName, SpiEeprom.SIZE_BYTES);
        m_bank = new byte[3];
        this.baseMapper = baseMapper;
        LOG.info("MdT5740Mapper created, using folder: {}", sramFolder);
        initBackupFileIfNecessary();
        eeprom = new SpiEeprom();
        eeprom.setSram(sram);
    }

/*-------------------------------------------------
 mapper specific handlers
 -------------------------------------------------*/

    @Override
    public int readData(int address, Size size) {
        if ((address & 0xFFF_000) == TIME_LINE_START) { //0xA13_000
            assert size == Size.BYTE;
            return read_a13(address & 0xFFF);
        }
        return read(address, size);
    }

    @Override
    public void writeData(int address, int data, Size size) {
        if ((address & 0xFFF_000) == TIME_LINE_START) {
            assert size == Size.BYTE;
            write_a13(address & 0xFFF, data);
            return;
        }
        //writes to SRAM 0x20_0000-0x20_FFFF should be ignored,
        //ie. do not trigger automatic SRAM detection
        if ((address >= MdCartInfoProvider.DEFAULT_SRAM_START_ADDRESS) &&
                (address <= MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS)) {
            LOG.warn("Ignoring write to SRAM address {}, value {} {}", th(address), th(data), size);
            return;
        }
        baseMapper.writeData(address, data, size);
    }


    int read(int offset, Size size) {
        if (offset < DEFAULT_ROM_END_ADDRESS) {
            if (offset == 0x0015e6 || offset == 0x0015e8) {
                // ugly hack until we don't know much about game protection
                // first 3 reads from 15e6 return 0x00000010, then normal 0x00018010 value for crc check
                int res;
                if (m_rdcnt < 3) {
                    m_rdcnt++;
                    res = Util.readData(prot_15e6, offset - 0x0015e6, size);
                } else {
                    res = baseMapper.readData(offset, size);
                }
//                    LOG.warn("read {} {} #{}, data: {}", th(offset), size, m_rdcnt, th(res));
                return res;
            }
            if (offset >= BANKBABLE_ADDR_START) {  // last 0x180000 are bankswitched
                int bank = ((offset - BANKBABLE_ADDR_START) >> BANK_SHIFT);
                offset = (m_bank[bank] << BANK_SHIFT) | (offset & BANK_MASK);
            }
        }
        return baseMapper.readData(offset, size);
    }

    int read_a13(int offset) {
        if (offset == 0x0b) {
            //LOG.info("EEPROM reading bit: {}" ,res);
            return eeprom.readEeprom(offset, Size.BYTE) & 1;
        }
        LOG.warn("A130{} byte read\n", th(offset));
        return 0xffff;
    }

    /**
     * Writes to 0xA130F1 - 0xA130FF should be ignored
     */
    void write_a13(int offset, int data) {
        offset &= 0xFF;
        assert (offset & 1) == 1;
        if (offset == 0x01) {
//                LOG.warn("A13001 write {}\n", th(data));
        } else if (offset < 0x09) {
            m_bank[(offset >> 1) - 1] = (byte) (data & 0x0f);
        } else if (offset == 0x09) {
            eeprom.writeEeprom(offset, data, Size.BYTE);
        } else {
            LOG.error("A13 write {} byte, data: {}\n", th(offset), th(data));
        }
    }

    @Override
    public void closeRom() {
        writeFile();
    }

    @Override
    public int[] getState() {
        return ArrayEndianUtil.toUnsignedIntArray(m_bank);
    }

    /**
     * bankData is actually 8 bytes, we use only the first 3
     */
    @Override
    public void setState(int[] bankData) {
        assert bankData.length >= m_bank.length;
        for (int i = 0; i < m_bank.length; i++) {
            m_bank[i] = (byte) (bankData[i] & 0xFF);
        }
    }
}
