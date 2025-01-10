package omegadrive.cpu.z80;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.bus.model.MdZ80BusProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Z80BusTest {

    MdZ80BusProvider z80bus;
    MdMainBusProvider mainBus;
    IMemoryProvider memoryProvider;

    @Before
    public void setup() {
        mainBus = SystemTestUtil.setupNewMdSystem();
        Optional<MdZ80BusProvider> optBus = mainBus.getBusDeviceIfAny(MdZ80BusProvider.class);
        Assert.assertTrue(optBus.isPresent());
        z80bus = optBus.get();
        Optional<IMemoryProvider> opt = mainBus.getBusDeviceIfAny(IMemoryProvider.class);
        Assert.assertTrue(opt.isPresent());
        memoryProvider = opt.get();
    }

    //NOTE: Z80 write to 68k RAM - this seems to be allowed (Mamono)
    @Test
    public void testZ80Write68kRam() {
        z80bus.setRomBank68kSerial(0xE0_0000);
        for (int i = MdZ80BusProvider.START_68K_BANK; i <= MdZ80BusProvider.END_68K_BANK; i++) {
            z80bus.write(i, i & 0xFF, Size.BYTE);
        }
        for (int i = 0; i <= MdZ80BusProvider.M68K_BANK_MASK; i++) {
            int val = memoryProvider.readRamByte(i);
            Assert.assertEquals(th(i), i & 0xFF, val & 0xFF);
        }
    }

    /**
     * this seems to be not allowed, but lethal wedding (LW_build_0446.bin) homebrew does it,
     * trying to play music
     */
    @Test
    public void testZ80Read68kRam() {
        z80bus.setRomBank68kSerial(0xE0_0000);
        for (int i = MdZ80BusProvider.START_68K_BANK; i < MdZ80BusProvider.END_68K_BANK; i++) {
            int val = (int) z80bus.read(i, Size.BYTE);
            Assert.assertEquals(0xff, val);
        }

        //write to 68k RAM
        testZ80Write68kRam();

        //still unable to read
        z80bus.setRomBank68kSerial(0xE0_0000);
        for (int i = MdZ80BusProvider.START_68K_BANK; i < MdZ80BusProvider.END_68K_BANK; i++) {
            int val = (int) z80bus.read(i, Size.BYTE);
            Assert.assertEquals(0xff, val);
        }
    }
}
