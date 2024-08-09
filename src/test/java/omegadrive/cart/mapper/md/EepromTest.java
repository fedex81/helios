package omegadrive.cart.mapper.md;

import omegadrive.cart.loader.MdLoader;
import omegadrive.cart.loader.MdRomDbModel;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static omegadrive.cart.MdCartInfoProvider.DEFAULT_SRAM_START_ADDRESS;
import static omegadrive.cart.MdCartInfoProvider.NO_PROVIDER;
import static omegadrive.cart.loader.MdRomDbModel.NO_ENTRY;


public class EepromTest {

    private static int romByte = 0xAA;

    private static final RomMapper rom = new RomMapper() {
        @Override
        public int readData(int address, Size size) {
            return romByte;
        }

        @Override
        public void writeData(int address, int data, Size size) {
        }
    };

    private RomMapper mapper;
    private String nflQc32x_serial = "___T-8102B___";

    private void setup() {
        MdRomDbModel.RomDbEntry romDbEntry = MdLoader.getEntry(nflQc32x_serial);
        Assertions.assertNotEquals(NO_ENTRY, romDbEntry);
        mapper = MdBackupMemoryMapper.createInstance(rom, NO_PROVIDER, RomMapper.SramMode.READ_WRITE, romDbEntry);
    }

    /**
     * NFL Qc 32x reads from 0x20_0000 LONG with SRAM enabled and expects to read from cart
     */
    @Test
    public void testLongReadSramEnabled() {
        setup();
        int res = mapper.readData((int) DEFAULT_SRAM_START_ADDRESS, Size.LONG);
        Assertions.assertEquals(romByte, res);
    }
}