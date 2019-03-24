package omegadrive.bus;

import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.joypad.GenesisJoypad;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.util.Size;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.z80.Z80CoreWrapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * GenesisBusTest
 *
 * @author Federico Berti
 */
public class GenesisBusTest {

    private GenesisBusProvider bus;

    @Before
    public void init() {
        bus = GenesisBusProvider.createBus();
        IMemoryProvider memory = MemoryProvider.createGenesisInstance();
        GenesisJoypad joypad = new GenesisJoypad();

        GenesisVdpProvider vdp = GenesisVdpProvider.createVdp(bus);
        MC68000Wrapper cpu = new MC68000Wrapper(bus);
        Z80CoreWrapper z80 = Z80CoreWrapper.createGenesisInstance(bus);
        //sound attached later
        SoundProvider sound = SoundProvider.NO_SOUND;
        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(cpu).attachDevice(z80).attachDevice(sound);
    }

    /**
     * When 68k writes to z80 address space 0x8000 - 0xFFFF mirrors 0x0000 - 0x7FFF
     */
    @Test
    public void test68kWriteToZ80() {
        int value = 1;
        bus.write(0xA11100, value, Size.BYTE);
        bus.write(GenesisBusProvider.Z80_ADDRESS_SPACE_START, value, Size.BYTE);
        long res = bus.read(GenesisBusProvider.Z80_ADDRESS_SPACE_START, Size.BYTE);
        Assert.assertEquals(value, res);

        value = 2;
        bus.write(0xA08000, value, Size.BYTE);
        res = bus.read(GenesisBusProvider.Z80_ADDRESS_SPACE_START, Size.BYTE);
        Assert.assertEquals(value, res);

        value = 3;
        bus.write(0xA08500, value, Size.BYTE);
        res = bus.read(0xA00500, Size.BYTE);
        Assert.assertEquals(value, res);

        value = 4;
        bus.write(0xA00A00, value, Size.BYTE);
        res = bus.read(0xA08A00, Size.BYTE);
        Assert.assertEquals(value, res);
    }
}
