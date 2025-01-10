package omegadrive.mapper;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cart.mapper.md.MdBackupMemoryMapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.nio.ByteBuffer;

import static omegadrive.bus.model.MdMainBusProvider.SRAM_LOCK;
import static omegadrive.cart.MdCartInfoProvider.MdRomHeaderField.SYSTEM_TYPE;
import static omegadrive.util.UtilTest.RUNNING_IN_GITHUB;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class MdMapperTest {

    IMemoryProvider mem;
    ByteBuffer buffer;

    public static final int ROM_HEADER_START = SYSTEM_TYPE.startOffset;

    private void prepareRomData(int size, String systemType) {
        buffer = ByteBuffer.allocate(size);
        buffer.position(ROM_HEADER_START);
        buffer.put(systemType.getBytes());
        mem = MemoryProvider.createMdInstance();
    }

    @Test
    public void testNoMapper() {
        prepareRomData(0x20_0000, "SEGA GENESIS"); //16 Mbit

        int address = 0x2F_0000;
        int address1 = 0x1F_0000;
        int val1 = 0x55_66_77_88;

        buffer.putInt(address1, val1);
        MdMainBusProvider bus = loadRomData();

        testBusRead(bus, address, 0xFFFF_FFFF); //read to unmapped address space
        testBusRead(bus, address1, val1);
    }

    @Test
    public void testNoMapperSram() {
        Assume.assumeFalse(RUNNING_IN_GITHUB);
        prepareRomData(0x10_0000, "SEGA GENESIS"); //8 Mbit
        prepareSramHeader();
        testSramInternal();
    }

    //see VR 32x japan
    @Test
    public void testMapper() {
        Assume.assumeFalse(RUNNING_IN_GITHUB);
        prepareRomData(0x50_0000, "SEGA GENESIS"); //40 Mbit

        int address = 0x20_00FF;
        int address1 = 0x40_00FF;
        int val = 0x11_22_33_44;
        buffer.putInt(address, val);
        buffer.putInt(address1, val);
        MdMainBusProvider bus = loadRomData();
        bus.write(SRAM_LOCK, 0, Size.BYTE); //enable ssfMapper, disable SRAM
        testBusRead(bus, address, val);
        testBusRead(bus, address1, val);

        bus.write(address, 0x55, Size.BYTE); //write to cart ignored
        testBusRead(bus, address, val);

        bus.write(SRAM_LOCK, 3, Size.BYTE); //enable SRAM (RW mode), disable ssfMapper
        testBusRead(bus, address, -1); //read SRAM = -1 (defaults to 0xFF)
        testBusRead(bus, address1, val);

        int sramData = 0x1234_5678;
        bus.write(address, sramData, Size.BYTE); //write BYTE to SRAM
        testBusRead(bus, address, ((sramData & 0xFF) << 24) | 0xFF_FFFF);

        sramData = 0x1324_5768;
        bus.write(address, sramData, Size.WORD); //write WORD to SRAM
        testBusRead(bus, address, ((sramData & 0xFFFF) << 16) | 0xFFFF);

        sramData = 0x8326_1748;
        bus.write(address, sramData, Size.LONG); //write LONG to SRAM
        testBusRead(bus, address, sramData);

        bus.write(SRAM_LOCK, 0, Size.BYTE); //enable ssfMapper, disable SRAM
        testBusRead(bus, address, val);
        testBusRead(bus, address1, val);

        bus.write(SRAM_LOCK, 1, Size.BYTE); //enable SRAM READ-ONLY, disable ssfMapper
        testBusRead(bus, address, sramData); //read SRAM
        testBusRead(bus, address1, val);
        bus.write(address, 0x99, Size.BYTE); //write BYTE to cart, ignored

        boolean ignore = !MdBackupMemoryMapper.allowSramWritesWhenReadOnly;
        System.out.println("Ignoring sramWrite: " + ignore);
        int exp = ignore ? sramData : 0x9926_1748;
        testBusRead(bus, address, exp); //read SRAM
    }

    //tries to use sram without declaring it in the header
    //NOTE: there is no overlap between rom vs sram address space
    //Buck Rogers
    @Test
    //NOTE: fails in github actions
    public void testNoMapperSramDodgy() {
        Assume.assumeFalse(RUNNING_IN_GITHUB);
        prepareRomData(0x20_0000, "SEGA GENESIS"); //16 Mbit
        testSramInternal();
    }

    //BadApple, UMK3
    @Test
    public void testFlatMapper() {
        prepareRomData(0x50_0000, "SEGA GENESIS"); //40 Mbit

        int address = 0x44_0000;
        int val = 0x11_22_33_44;
        int address1 = 0x1F_0000;
        int val1 = 0x55_66_77_88;
        buffer.putInt(address, val);
        buffer.putInt(address1, val1);
        MdMainBusProvider bus = loadRomData();

        testBusRead(bus, address, val);
        testBusRead(bus, address1, val1);
    }

    /**
     * Astebros demo, switches on sram but in read-only mode, it then expects to be able to write to it.
     */
    @Test
    public void testSramReadOnlyFlag() {
        prepareRomData(0x50_0000, "SEGA GENESIS"); //40 Mbit
        MdMainBusProvider bus = loadRomData();
        int address = 0x20_00FF;
        int sramData = 0x1234_5678;
        int val = 0x99_34_56_78;

        bus.write(SRAM_LOCK, 3, Size.BYTE); //enable SRAM, disable ssfMapper
        bus.write(address, sramData, Size.LONG); //write LONG to SRAM
        testBusRead(bus, address, sramData); //read SRAM

        bus.write(SRAM_LOCK, 1, Size.BYTE); //enable SRAM READ-ONLY, disable ssfMapper
        bus.write(address, 0x99, Size.BYTE); //write BYTE to cart

        boolean ignore = !MdBackupMemoryMapper.allowSramWritesWhenReadOnly;
        System.out.println("Ignoring sramWrite: " + ignore);
        int exp = ignore ? sramData : val;
        testBusRead(bus, address, exp); //read SRAM
    }

    private void testSramInternal() {
        int address = 0x2F_0000;
        int address1 = 0x0F_0000;
        int address2 = 0x20_0001;
        int val1 = 0x55_66_77_88;

        buffer.putInt(address1, val1);
        MdMainBusProvider bus = loadRomData();

        //unmapped read
        testBusRead(bus, address, 0xFFFF_FFFF);
        testBusRead(bus, address1, val1);
        testBusRead(bus, address2, 0xFFFF_FFFF); //sram contains 0xFFs

        //test write
        int val2 = 0x22_44_66_88;
        bus.write(address2, val2, Size.LONG);
        testBusRead(bus, address2, val2);
    }

    private void testBusRead(MdMainBusProvider bus, int address, final int expectedLong) {
        for (Size size : Size.values()) {
            switch (size) {
                case BYTE:
                    byte exp = (byte) ((expectedLong >> 24) & 0xFF);
                    byte res = (byte) (bus.read(address, size));
                    Assert.assertEquals(address + "," + size, exp, res);
                    break;
                case WORD:
                    short iexp = (short) ((expectedLong >> 16) & 0xFFFF);
                    short ires = (short) bus.read(address, size);
                    Assert.assertEquals(address + "," + size, iexp, ires);
                    break;
                default:
                    Assert.assertEquals(address + "," + size, expectedLong, bus.read(address, size));
                    break;
            }
        }
    }

    private void prepareSramHeader() {
        buffer.position(MdCartInfoProvider.SRAM_FLAG_ADDRESS);
        buffer.put(MdCartInfoProvider.EXTERNAL_RAM_FLAG_VALUE.getBytes());
        buffer.put((byte) 0x80); //sram
        buffer.put((byte) 0x20); //backup
        buffer.putInt(0x20_0001); //sram start
        buffer.putInt(0x20_FFFF); //sram end
    }

    private MdMainBusProvider loadRomData() {
        mem.setRomData(buffer.array());
        MdMainBusProvider bus = SystemTestUtil.setupNewMdSystem(mem);
        bus.init();
        return bus;
    }
}
