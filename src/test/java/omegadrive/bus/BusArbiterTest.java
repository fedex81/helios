package omegadrive.bus;

import omegadrive.GenesisProvider;
import omegadrive.m68k.M68kProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.util.RegionDetector;
import omegadrive.vdp.GenesisVdpNew;
import omegadrive.vdp.VdpProvider;
import omegadrive.vdp.VdpTestUtil;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class BusArbiterTest {


//    You can execute at least one instruction after enabling VINT in the VDP with a pending VINT.
//    Emulating this is required to make Sesame Street Counting Cafe to work.

    //    MODE_2 changed from: 14, to: 74 -- , hce=f1(1e2), vce=4f(4f), hBlankSet=true,vBlankSet=false, vIntPending=true,
//                                         hIntPending=false, hLinePassed=176
//    vdpHV: f6, 4f Raise: Arbiter IPL: vint: true, hint: true
//    vdpHV: f6, 4f Raise: Vdp State: vint: true, hint: false
//    vdpHV: f6, 4f raise 68k intLevel: 6
//    vdpHV: f6, 4f Z80 raise interrupt
//    vdpHV: fb, 4f PreAck: Arbiter IPL: vint: true, hint: true
//    vdpHV: fb, 4f PreAck: Vdp State: vint: true, hint: false
//    vdpHV: fb, 4f Ack VDP VINT
//    vdpHV: fb, 4f PostAck: Arbiter IPL: vint: false, hint: false
//    vdpHV: fb, 4f PostAck: Vdp State: vint: false, hint: false

    int hCounterRaise = -1;
    int vCounterRaise = -1;
    int hCounterPending = -1;
    int vCounterPending = -1;

    @Test
    public void testSesameStreet() {
        GenesisProvider emu = createGenesisProvider();
        BusProvider bus = BusProvider.createBus();
        VdpProvider vdp = VdpProvider.createVdp(bus);
        Z80Provider z80 = Z80CoreWrapper.createInstance(bus);


        M68kProvider cpu = new MC68000Wrapper(bus) {
            @Override
            public boolean raiseInterrupt(int level) {
                hCounterRaise = vdp.getHCounter();
                vCounterRaise = vdp.getVCounter();
                return true;
            }
        };
        BusArbiter busArbiter = BusArbiter.createInstance(vdp, cpu, z80);

        bus.attachDevice(vdp).attachDevice(cpu).attachDevice(busArbiter).attachDevice(emu);
        vdp.writeControlPort(0x8C00);
        vdp.writeControlPort(0x8174);
        ((GenesisVdpNew) vdp).resetVideoMode(true);
        do {
            VdpTestUtil.runVdpSlot(vdp);
            if (busArbiter.isVdpVInt() && hCounterPending < 0) {
                hCounterPending = vdp.getHCounter();
                vCounterPending = vdp.getVCounter();
            }
            bus.handleVdpInterrupts();

        } while (hCounterRaise < 0);
        Assert.assertEquals(vCounterRaise, vCounterPending);
        //this should be at least 1
        Assert.assertTrue(hCounterRaise > hCounterPending);
    }

    /**
     * TODO
     * Interrupts are know acknowledged based on what the
     * VDP thinks its asserting rather than what the 68K actually is ack-ing - Fixes Fatal Rewind
     */
    public void testFatalRewind() {

    }


    private static GenesisProvider createGenesisProvider() {
        return new GenesisProvider() {
            @Override
            public RegionDetector.Region getRegion() {
                return null;
            }

            @Override
            public void renderScreen(int[][] screenData) {

            }

            @Override
            public void handleNewGame(Path file) {

            }

            @Override
            public void handleCloseGame() {

            }

            @Override
            public void handleCloseApp() {

            }

            @Override
            public void handleLoadState(Path file) {

            }

            @Override
            public boolean isGameRunning() {
                return false;
            }

            @Override
            public boolean isSoundWorking() {
                return false;
            }

            @Override
            public void toggleMute() {

            }

            @Override
            public void toggleSoundRecord() {

            }

            @Override
            public void setFullScreen(boolean value) {

            }

            @Override
            public void init() {

            }

            @Override
            public void setPlayers(int i) {

            }

            @Override
            public void setDebug(boolean value) {

            }

            @Override
            public String getRomName() {
                return null;
            }

            @Override
            public void handleSaveState() {

            }
        };
    }
}
