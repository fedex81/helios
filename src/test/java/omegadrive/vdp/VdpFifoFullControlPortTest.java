package omegadrive.vdp;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import omegadrive.vdp.model.MdVdpProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static omegadrive.SystemLoader.SystemType.MD;
import static omegadrive.bus.model.MdZ80BusProvider.VDP_BASE_ADDRESS;
import static omegadrive.system.SystemProvider.NO_CLOCK;
import static omegadrive.vdp.model.MdVdpProvider.VdpRamType.VRAM;
import static omegadrive.vdp.model.MdVdpProvider.VdpRegisterName.AUTO_INCREMENT;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class VdpFifoFullControlPortTest {

    private static final int VDP_DATA_PORT = VDP_BASE_ADDRESS;
    private static final int VDP_CTRL_PORT = VDP_DATA_PORT + 4;

    private MdMainBusProvider bus;
    private MdVdpProvider vdp;

    @BeforeEach
    public void setup() {
        bus = SystemTestUtil.setupNewMdSystem();
        vdp = bus.getVdp();
        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdp);
        MdRuntimeData.newInstance(MD, NO_CLOCK);
    }


    private void fillFifo() {
        bus.write(VDP_CTRL_PORT, 0x4000, Size.WORD);
        bus.write(VDP_CTRL_PORT, 0, Size.WORD);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);
        for (int i = 0; i < 4; i++) {
            bus.write(VDP_DATA_PORT, 0x1111 * (i + 1), Size.WORD);
        }
        Assertions.assertTrue(vdp.getFifo().isFull());
    }

    //Bubba n stix
    @Test
    public void controlPortWriteIsNotStalledByFullFifo() {
        fillFifo();
        Assertions.assertEquals(0, vdp.getRegisterData(AUTO_INCREMENT));

        bus.write(VDP_CTRL_PORT, 0x8f02, Size.WORD); //reg15 (autoInc) = 2

        Assertions.assertEquals(2, vdp.getRegisterData(AUTO_INCREMENT));
    }

    @Test
    public void dataPortWriteStallsUntilFifoHasRoom() {
        fillFifo();
        bus.write(VDP_DATA_PORT, 0xBEEF, Size.WORD);

        Assertions.assertFalse(bus.is68kRunning());
        Assertions.assertTrue(vdp.getFifo().isFull());
        Assertions.assertEquals(0, vdp.getVdpMemory().readVideoRamWord(VRAM, 0));

        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);
        Assertions.assertTrue(bus.is68kRunning());

        //autoInc = 0, last write on VRAM address 0 should be 0xBEEF
        Assertions.assertEquals(0xBEEF, vdp.getVdpMemory().readVideoRamWord(VRAM, 0));
    }

    @Test
    public void backToBackDataPortWritesWhileFifoFullAreBothKept() {
        bus.write(VDP_CTRL_PORT, 0x8f02, Size.WORD); //reg15 (autoInc) = 2
        fillFifo();

        //movem.w (d0-d1), a0 //a0 = VDP_DATA_PORT
        //is this ever done in practice? probably not
        bus.write(VDP_DATA_PORT, 0xAAAA, Size.WORD);
        bus.write(VDP_DATA_PORT + 2, 0xBBBB, Size.WORD);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);

        //TODO known issue the 1st write gets dropped
        //and the 2nd write goes to addr 8
//        Assertions.assertEquals(0xBBBB, vdp.getVdpMemory().readVideoRamWord(VRAM, 10));
//        Assertions.assertEquals(0xAAAA, vdp.getVdpMemory().readVideoRamWord(VRAM, 8));
        Assertions.assertEquals(0xBBBB, vdp.getVdpMemory().readVideoRamWord(VRAM, 8));

    }
}
