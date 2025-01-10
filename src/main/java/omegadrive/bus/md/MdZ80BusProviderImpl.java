/*
 * MdZ80BusProviderImpl
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 11:06
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

import omegadrive.Device;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.bus.model.MdZ80BusProvider;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.IMemoryRam;
import omegadrive.sound.fm.FmProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.Util.th;

public class MdZ80BusProviderImpl extends DeviceAwareBus implements MdZ80BusProvider {
    private static final Logger LOG = LogHelper.getLogger(MdZ80BusProviderImpl.class.getSimpleName());

    private final static boolean verbose = false;

    //    To specify which 32k section you want to access, write the upper nine
    //    bits of the complete 24-bit address into bit 0 of the bank address
    //    register, which is at 6000h (Z80) or A06000h (68000), starting with
    //    bit 15 and ending with bit 23.
    private int romBank68kSerial;

    private MdMainBusProvider mainBusProvider;
    private BusArbiter busArbiter;
    private FmProvider fmProvider;
    private byte[] ram;
    private int ramMask;


    @Override
    public BaseBusProvider attachDevice(Device device) {
        if (device instanceof MdMainBusProvider) {
            this.mainBusProvider = (MdMainBusProvider) device;
            this.mainBusProvider.getBusDeviceIfAny(BusArbiter.class).ifPresent(this::attachDevice);
        }
        if (device instanceof IMemoryRam z80Memory) {
            this.ram = z80Memory.getRamData();
            this.ramMask = ram.length - 1;
        }
        if (device instanceof BusArbiter) {
            this.busArbiter = (BusArbiter) device;
        }
        super.attachDevice(device);
        return this;
    }

    @Override
    public int read(final int address, final Size size) {
        assert size == Size.BYTE;
        int data = 0xFF;
        if (address <= END_RAM) {
            data = ram[address & ramMask];
        } else if (address >= START_YM2612 && address <= END_YM2612) {
            if (mainBusProvider.isZ80ResetState()) {
                LOG.warn("FM read while Z80 reset");
                data = 1;
            } else {
                data = getFm().read();
            }
        } else if (address >= START_ROM_BANK_ADDRESS && address <= END_UNUSED) {
            logWarnOnce(LOG, "Z80 read bank switching/unused: {}", th(address));
        } else if (address >= START_VDP && address <= END_VDP_VALID) {
            int vdpAddress = (VDP_BASE_ADDRESS + address);
            if (verbose) LOG.info("Z80 read VDP memory , address {}", th(address));
            data = mainBusProvider.read(vdpAddress, Size.BYTE);
        } else if (address >= START_68K_BANK && address <= END_68K_BANK) {
            busArbiter.addCyclePenalty(BusArbiter.CpuType.Z80, Z80_CYCLE_PENALTY);
            busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
            int addressB = romBank68kSerial | (address & M68K_BANK_MASK);
            if (addressB >= MdMainBusProvider.ADDRESS_RAM_MAP_START && addressB < MdMainBusProvider.ADDRESS_UPPER_LIMIT) {
                LOG.warn("Z80 reading from 68k RAM");
            } else {
                data = mainBusProvider.read(addressB, Size.BYTE);
            }
        } else {
            LOG.error("Illegal Z80 memory read: {}", th(address));
        }
        return data;
    }

    @Override
    public void write(final int address, final int dataInt, final Size size) {
        assert size == Size.BYTE;
        if (address <= END_RAM) {
            ram[address & ramMask] = (byte) dataInt;
        } else if (address >= START_YM2612 && address <= END_YM2612) {
            if (mainBusProvider.isZ80ResetState()) {
                LOG.warn("Illegal write to FM while Z80 reset");
                return;
            }
            getFm().write(address, dataInt);
        } else if (address >= START_ROM_BANK_ADDRESS && address <= END_ROM_BANK_ADDRESS) {
            romBanking(dataInt);
        } else if (address >= START_UNUSED && address <= END_UNUSED) {
            LOG.warn("Write to unused memory: {}", th(address));
        } else if (address >= START_VDP && address <= END_VDP_VALID) {
            int vdpAddress = VDP_BASE_ADDRESS + address;
            mainBusProvider.write(vdpAddress, dataInt, Size.BYTE);
        } else if (address > END_VDP_VALID && address <= END_VDP) {
            //Rambo III (W) (REV01) [h1C]
            LOG.error("Machine should be locked, write to address: {}", th(address));
        } else if (address >= START_68K_BANK && address <= END_68K_BANK) {
            busArbiter.addCyclePenalty(BusArbiter.CpuType.Z80, Z80_CYCLE_PENALTY);
            busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
            //NOTE: Z80 write to 68k RAM - this seems to be allowed (Mamono)
            mainBusProvider.write(romBank68kSerial | (address & M68K_BANK_MASK), dataInt, Size.BYTE);
        } else {
            LOG.error("Illegal Z80 memory write:  {}, {}", th(address), dataInt);
        }
    }

    private FmProvider getFm() {
        if (fmProvider == null) {
            fmProvider = mainBusProvider.getFm();
            assert busArbiter != BusArbiter.NO_OP;
        }
        return fmProvider;
    }

    @Override
    public void reset() {
        super.reset();
        romBank68kSerial = 0; //TODO needed?
    }

    //	 From 8000H - FFFFH is window of 68K memory.
//    Z-80 can access all of 68K memory by BANK
//    switching.   BANK select data create 68K address
//    from A15 to A23.  You must write these 9 bits
//    one at a time into 6000H serially, byte units, using the LSB.
//    To specify which 32k section you want to access, write the upper nine
//    bits of the complete 24-bit address into bit 0 of the bank address
//    register, which is at 6000h (Z80) or A06000h (68000), starting with
//    bit 15 and ending with bit 23.
    private void romBanking(int data) {
        romBank68kSerial = ((romBank68kSerial >> 1) | ((data & 1) << 23)) & 0xFF8000;
    }

    public void setRomBank68kSerial(int romBank68kSerial) {
        this.romBank68kSerial = romBank68kSerial;
    }

    public int getRomBank68kSerial() {
        return romBank68kSerial;
    }

    @Override
    public void handleInterrupts(Z80Provider.Interrupt type) {
        LOG.warn("Ignored Z80 interrupt: {} ", type);
    }
}
