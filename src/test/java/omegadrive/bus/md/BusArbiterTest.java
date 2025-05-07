/*
 * Copyright (c) 2018-2019 Federico Berti
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

package omegadrive.bus.md;

import omegadrive.SystemLoader;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.bus.model.Z80BusProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.cpu.z80.Z80CoreWrapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.model.MdVdpProvider;
import omegadrive.vdp.model.VdpCounterMode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static omegadrive.util.SystemTestUtil.createTestJoypadProvider;

public class BusArbiterTest {

    private boolean verbose = false;
    private MdVdpProvider vdp;
    private MdMainBusProvider bus;
    private BusArbiter busArbiter;
    int hCounterIntAccepted = -1;

    int hCounterRaise = -1;
    int vCounterRaise = -1;
    int hCounterPending = -1;
    int vCounterPending = -1;
    int vCounterIntAccepted = -1;
    private MC68000Wrapper cpu;

    private Z80BusProvider z80bus;

    @Before
    public void setup() {
        SystemProvider emu = MdVdpTestUtil.createTestMdProvider();
        bus = new MdBus();
        vdp = MdVdpProvider.createVdp(bus);
        Z80Provider z80 = Z80CoreWrapper.createInstance(SystemLoader.SystemType.MD, bus);
        z80bus = z80.getZ80BusProvider();

        cpu = new MC68000Wrapper(BufferUtil.CpuDeviceAccess.M68K, bus) {
            @Override
            public boolean raiseInterrupt(int level) {
                hCounterRaise = vdp.getHCounter();
                vCounterRaise = vdp.getVCounter();
                boolean accept = level > getM68k().getInterruptLevel();
                if (accept) {
                    hCounterIntAccepted = hCounterRaise;
                    vCounterIntAccepted = vCounterRaise;
                }
                return accept;
            }
        };
        busArbiter = BusArbiter.createInstance(vdp, cpu, z80);

        bus.attachDevice(vdp).attachDevice(cpu).attachDevice(busArbiter).
                attachDevice(createTestJoypadProvider()).
                attachDevice(emu);
    }

    private void setupZ80() {
        Z80Provider z80 = new Z80CoreWrapper() {
            {
                this.z80BusProvider = z80bus;
            }
            @Override
            public boolean interrupt(boolean value) {
                hCounterRaise = vdp.getHCounter();
                vCounterRaise = vdp.getVCounter();
                return true;
            }
        };
        busArbiter = BusArbiter.createInstance(vdp, cpu, z80);
        bus.attachDevice(busArbiter);
    }

    //    You can execute at least one instruction after enabling VINT in the VDP with a pending VINT.
//    Emulating this is required to make Sesame Street Counting Cafe to work.
    @Test
    public void testSesameStreet() {
        vdp.writeControlPort(0x8C00);
        vdp.writeControlPort(0x8174);
        vdp.resetVideoMode(true);
        do {
            MdVdpTestUtil.runVdpSlot(vdp);
            if (MdVdpTestUtil.isVdpVInt(vdp) && hCounterPending < 0) {
                hCounterPending = vdp.getHCounter();
                vCounterPending = vdp.getVCounter();
            }
            bus.handleVdpInterrupts68k();

        } while (hCounterRaise < 0);
        Assert.assertEquals(vCounterRaise, vCounterPending);
        //this should be at least 1
        Assert.assertTrue(hCounterRaise > hCounterPending);
    }

    /**
     * Lotus 2, hip shouldnt trigger on line 0
     * <p>
     * Ack VDP VINT - , hce=6(c), vce=e0(e0), hBlankSet=false,vBlankSet=true, vIntPending=false, hIntPending=false, hLinePassed=243
     * IntMask from: 6 to: 7
     * HCOUNTER_VALUE changed from: ff, to: 0 -- , hce=26(4c), vce=e0(e0), hBlankSet=false,vBlankSet=true, vIntPending=false, hIntPending=false, hLinePassed=243
     * MODE_2 changed from: 24, to: 64 -- , hce=28(50), vce=e0(e0), hBlankSet=false,vBlankSet=true, vIntPending=false, hIntPending=false, hLinePassed=243
     * IntMask from: 7 to: 3
     * IntMask from: 3 to: 7
     * IntMask from: 7 to: 3
     * Set HIP: true, hLinePassed: -1, hce=85(10a), vce=0(0), hBlankSet=false,vBlankSet=false, vIntPending=false, hIntPending=true, hLinePassed=-1
     */
    @Test
    @Ignore("TODO fix")
    public void testLotus2_hint() {
        vdp.writeControlPort(0x8C00);
        //disable hint
        vdp.writeControlPort(0x8AFF);
        vdp.writeControlPort(0x8004);
        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdp);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);
        vdp.resetVideoMode(true);
        do {
            boolean wasVblank = MdVdpTestUtil.isVBlank(vdp);
            MdVdpTestUtil.runVdpSlot(vdp);
            boolean vBlankTrigger = !wasVblank && MdVdpTestUtil.isVBlank(vdp);
            if (vBlankTrigger) {
                vdp.setHip(false);
                vdp.setVip(false);
                //enable hint after vblank period
                vdp.writeControlPort(0x8A00);
                vdp.writeControlPort(0x8014);
                MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);
                Assert.assertFalse("HINT should not be pending", vdp.getHip());
            }
            bus.handleVdpInterrupts68k();

        } while (hCounterRaise < 0);
        //this should be at least 1, ie. no HINT triggered on line 0
        Assert.assertEquals(1, vCounterRaise);
    }

    @Test
    public void testZ80Interrupt() {
        setupZ80();
        //disable hint
        vdp.writeControlPort(0x8AFF);
        vdp.writeControlPort(0x8004);
        //enable VINT
        vdp.writeControlPort(0x8164);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);
        vdp.resetVideoMode(true);
        VdpCounterMode mode = VdpCounterMode.getCounterMode(vdp.getVideoMode());
        do {
            MdVdpTestUtil.runVdpSlot(vdp);
            bus.handleVdpInterrupts68k();
        } while (vCounterRaise < 0);
        Assert.assertEquals(mode.vBlankSet, vCounterRaise);
        vCounterRaise = -1;
        do {
            MdVdpTestUtil.runVdpSlot(vdp);
            bus.handleVdpInterrupts68k();
        } while (vCounterRaise < 0);
        Assert.assertEquals(mode.vBlankSet, vCounterRaise);
    }

    @Test
    public void testVBlankFlagWhenDisplayDisabled() {
        vdp.writeControlPort(0x8AFF);
        vdp.writeControlPort(0x8004);
        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdp);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);
        vdp.resetVideoMode(true);
        do {
            MdVdpTestUtil.runVdpSlot(vdp);
        } while (MdVdpTestUtil.isVBlank(vdp));

        //disable display -> vblank on
        MdVdpTestUtil.vdpDisplayEnable(vdp, false);
        MdVdpTestUtil.runVdpSlot(vdp);
        Assert.assertTrue(MdVdpTestUtil.isVBlank(vdp));

        //enable display
        MdVdpTestUtil.vdpDisplayEnable(vdp, true);
        MdVdpTestUtil.runVdpSlot(vdp);
        Assert.assertFalse(MdVdpTestUtil.isVBlank(vdp));
    }

    @Test
    public void testHBlankFlagWhenDisplayDisabled() {
        vdp.writeControlPort(0x8AFF);
        vdp.writeControlPort(0x8004);
        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdp);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);
        vdp.resetVideoMode(true);
        do {
            MdVdpTestUtil.runVdpSlot(vdp);
        } while (MdVdpTestUtil.isHBlank(vdp));

        //disable display -> hblank doesnt change
        MdVdpTestUtil.vdpDisplayEnable(vdp, false);
        MdVdpTestUtil.runVdpSlot(vdp);
        Assert.assertFalse(MdVdpTestUtil.isHBlank(vdp));
    }

    /**
     * Interrupts are know acknowledged based on what the
     * VDP thinks its asserting rather than what the 68K actually is ack-ing - Fixes Fatal Rewind
     * <p>
     * VBLANK_INT was vdp_enabled6 and vdp_pending6 at some point
     * the arbiter sets it as arb_pending6, but 68k is masking level6 interrupts.
     * Later !vdp_enabled6, but still arb_pending6.
     * When the busArb gets an ack back for HBLANK_INT, it mistakenly
     * sets !vdp_pending6 intstead of !vdp_pending4
     */
    @Test
    public void testFatalRewind() {
        hCounterIntAccepted = -1;
        vCounterIntAccepted = -1;

        cpu.getM68k().setSR(0x2000);
        set68kIntMask(7); //mask all ints
        //disable hint
        vdp.writeControlPort(0x8AFF);
        vdp.writeControlPort(0x8004);
        //enable VINT
        vdp.writeControlPort(0x8164);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);
        vdp.resetVideoMode(true);

        checkIntAccepted(false);

        //disable VINT
        MdVdpTestUtil.vdpDisplayEnable(vdp, true);
        //enable HINT
        vdp.writeControlPort(0x8A01); //hint every line
        vdp.writeControlPort(0x8014);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);

        checkIntAccepted(false);

        //Now the busArbiter still has VINT has pending, when it gets an ack from 68k
        //it should mistakenly set vip=false (instead of hip=false)
        set68kIntMask(0);

        checkIntAccepted(true);
        bus.ackInterrupt68k(4); //simulate the CPU acking level 4

        //check that vint was accepted instead of hint
        Assert.assertFalse(MdVdpTestUtil.getVip(vdp));
        Assert.assertTrue(vdp.getHip());
    }

    /**
     * Similar to FatalRewind but 68k acks for lev6, but the vdp thinks it's a lev4.
     * In this case make sure we are acking lev6.
     */
    @Test
    public void testLotus2() {
        hCounterIntAccepted = -1;
        vCounterIntAccepted = -1;

        cpu.getM68k().setSR(0x2000);
        set68kIntMask(7); //mask all ints
        //enable hint
        vdp.writeControlPort(0x8A01); //hint every line
        vdp.writeControlPort(0x8014);
        //disable VINT
        MdVdpTestUtil.vdpDisplayEnable(vdp, true);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);
        vdp.resetVideoMode(true);

        checkIntAccepted(false);

        //enable VINT
        vdp.writeControlPort(0x8164);
        //disable HINT
        vdp.writeControlPort(0x8A01); //hint every line
        vdp.writeControlPort(0x8014);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdp);

        checkIntAccepted(false);

        //Now the busArbiter still has HINT has pending, when it gets an ack from 68k
        //it should correctly set vip=false
        set68kIntMask(0);

        checkIntAccepted(true);
        bus.ackInterrupt68k(6); //simulate the CPU acking level 6

        //check that vint was accepted
        Assert.assertFalse(MdVdpTestUtil.getVip(vdp));
        Assert.assertTrue(vdp.getHip());
    }

    private void checkIntAccepted(boolean accepted) {
        int cnt = 0;
        MdVdpTestUtil.runToStartFrame(vdp);
        do {
            MdVdpTestUtil.runVdpSlot(vdp);
            bus.handleVdpInterrupts68k();
            cnt++;
        } while (vCounterIntAccepted < 0 && cnt < 50000);

        //vint was not processed
        Assert.assertEquals(accepted, hCounterIntAccepted >= 0);
        Assert.assertEquals(accepted, vCounterIntAccepted >= 0);
    }

    private void set68kIntMask(int level) {
        final int INTERRUPT_FLAGS_MASK = 0x0700;
        int sr = cpu.getM68k().getSR();
        sr &= ~(INTERRUPT_FLAGS_MASK);
        sr |= (level & 0x07) << 8;
        cpu.getM68k().setSR(sr);
    }
}
