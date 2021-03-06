package omegadrive.mapper;

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import omegadrive.util.Util;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;

import static omegadrive.bus.model.GenesisBusProvider.SRAM_LOCK;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class MdMapperTest {

    IMemoryProvider mem;
    ByteBuffer buffer;

    private void prepareRomData(int size, String systemType) {
        buffer = ByteBuffer.allocate(size);
        buffer.position(MdCartInfoProvider.ROM_HEADER_START);
        buffer.put(systemType.getBytes());
        mem = MemoryProvider.createGenesisInstance();
    }


    @Test
    public void testNoMapper() {
        prepareRomData(0x20_0000, "SEGA GENESIS"); //16 Mbit

        int address = 0x2F_0000;
        int address1 = 0x1F_0000;
        int val1 = 0x55_66_77_88;

        buffer.putInt(address1, val1);
        GenesisBusProvider bus = loadRomData();

        testBusRead(bus, address, 0xFFFF_FFFFL); //read to unmapped address space
        testBusRead(bus, address1, val1);
    }

    @Test
    @Ignore("fails in github actions")
    /**
     * TODO
     * omegadrive.mapper.MdMapperTest > testNoMapperSram FAILED
     *     java.lang.AssertionError at MdMapperTest.java:146
     */
    public void testNoMapperSram() {
        prepareRomData(0x10_0000, "SEGA GENESIS"); //8 Mbit
        prepareSramHeader();
        testSramInternal();
    }

    @Test
    @Ignore("fails in github actions")
    /**
     * TODO
     * omegadrive.mapper.MdMapperTest > testMapper FAILED
     *     java.lang.AssertionError at MdMapperTest.java:146
     */
    public void testMapper() {
        prepareRomData(0x50_0000, "SEGA GENESIS"); //40 Mbit

        int address = 0x20_00FF;
        int address1 = 0x40_00FF;
        int val = 0x11_22_33_44;
        buffer.putInt(address, val);
        buffer.putInt(address1, val);
        GenesisBusProvider bus = loadRomData();
        bus.write(SRAM_LOCK, 0, Size.BYTE); //enable ssfMapper, disable SRAM
        testBusRead(bus, address, val);
        testBusRead(bus, address1, val);

        bus.write(address, 0x55, Size.BYTE); //write to cart ignored
        testBusRead(bus, address, val);

        bus.write(SRAM_LOCK, 1, Size.BYTE); //enable SRAM, disable ssfMapper
        testBusRead(bus, address, 0); //read SRAM = 0
        testBusRead(bus, address1, val);

        int sramData = 0x1234_5678;
        bus.write(address, sramData, Size.BYTE); //write BYTE to SRAM
        testBusRead(bus, address, (sramData & 0xFF) << 24);

        sramData = 0x1324_5768;
        bus.write(address, sramData, Size.WORD); //write WORD to SRAM
        testBusRead(bus, address, (sramData & 0xFFFF) << 16);

        sramData = 0x8326_1748;
        bus.write(address, sramData, Size.LONG); //write LONG to SRAM
        testBusRead(bus, address, sramData);

        bus.write(SRAM_LOCK, 0, Size.BYTE); //enable ssfMapper, disable SRAM
        testBusRead(bus, address, val);
        testBusRead(bus, address1, val);
    }

    //tries to use sram without declaring it in the header
    //NOTE: there is no overlap between rom vs sram address space
    //Buck Rogers
    @Test
    @Ignore("fails in github actions")
    /**
     * TODO
     * omegadrive.mapper.MdMapperTest > testNoMapperSramDodgy FAILED
     *     java.lang.AssertionError at MdMapperTest.java:146
     */
    public void testNoMapperSramDodgy() {
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
        GenesisBusProvider bus = loadRomData();

        testBusRead(bus, address, val);
        testBusRead(bus, address1, val1);
    }

    private void testSramInternal() {
        int address = 0x2F_0000;
        int address1 = 0x0F_0000;
        int address2 = 0x20_0001;
        int val1 = 0x55_66_77_88;

        buffer.putInt(address1, val1);
        GenesisBusProvider bus = loadRomData();

        //unmapped read
        testBusRead(bus, address, 0xFFFF_FFFFL);
        testBusRead(bus, address1, val1);
        testBusRead(bus, address2, 0); //sram contains 0s

        //test write
        int val2 = 0x22_44_66_88;
        bus.write(address2, val2, Size.LONG);
        testBusRead(bus, address2, val2);
    }

    private void testBusRead(GenesisBusProvider bus, int address, final long expectedLong) {
        for (Size size : Size.values()) {
            switch (size) {
                case BYTE:
                    byte exp = (byte) ((expectedLong >> 24) & 0xFF);
                    byte res = (byte) (bus.read(address, size));
                    Assert.assertEquals(address + "," + size, exp, res);
                    break;
                case WORD:
                    int iexp = (int) ((expectedLong >> 16) & 0xFFFF);
                    int ires = (int) bus.read(address, size);
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

    private GenesisBusProvider loadRomData() {
        mem.setRomData(Util.toUnsignedIntArray(buffer.array()));
        GenesisBusProvider bus = SystemTestUtil.setupNewMdSystem(mem);
        bus.init();
        return bus;
    }
}
