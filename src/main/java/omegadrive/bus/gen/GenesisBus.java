/*
 * GenesisBus
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 13:52
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

package omegadrive.bus.gen;

import omegadrive.Device;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cart.loader.MdLoader;
import omegadrive.cart.loader.MdRomDbModel;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.cart.mapper.md.MdBackupMemoryMapper;
import omegadrive.cart.mapper.md.Ssf2Mapper;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider.VdpBusyState;
import omegadrive.vdp.model.GenesisVdpProvider.VdpPortType;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.Objects;

public class GenesisBus extends DeviceAwareBus<GenesisVdpProvider> implements GenesisBusProvider, RomMapper {


    private static final Logger LOG = LogManager.getLogger(GenesisBus.class.getSimpleName());

    public static boolean verbose = false;
    public static final int M68K_CYCLE_PENALTY = 3;

    private MdCartInfoProvider cartridgeInfoProvider;
    private RomMapper mapper;
    private RomMapper ssf2Mapper = RomMapper.NO_OP_MAPPER;
    private RomMapper backupMemMapper = RomMapper.NO_OP_MAPPER;
    private SvpBus svpMapper = SvpBus.NO_OP;
    private MsuMdHandler msuMdHandler;
    private MdRomDbModel.Entry entry;

    private BusArbiter busArbiter = BusArbiter.NO_OP;

    GenesisBus.VdpRunnable vdpRunnable = new GenesisBus.VdpRunnable() {
        @Override
        public void run() {
            write(vdpAddress, vpdData, vdpSize);
        }
    };

    private static long ROM_END_ADDRESS;

    enum BusState {READY, NOT_READY}

    private BusState busState = BusState.NOT_READY;

    private boolean z80BusRequested;
    private boolean z80ResetState;
    private boolean enableTmss;

    public GenesisBus() {
        this.mapper = this;
        this.enableTmss = Boolean.parseBoolean(System.getProperty("md.enable.tmss", "false"));
    }

    void initializeRomData() {
        ROM_END_ADDRESS = Math.min(cartridgeInfoProvider.getRomSize(), Z80_ADDRESS_SPACE_START);
        entry = MdLoader.getEntry(cartridgeInfoProvider.getSerial());
        if (cartridgeInfoProvider.isSramEnabled() || entry.hasEeprom()) {
            mapper = MdBackupMemoryMapper.createInstance(this, cartridgeInfoProvider, entry);
        }
        //some homebrews use a flat ROM mapper, in theory up to Z80_ADDRESS_SPACE_START
        if (ROM_END_ADDRESS > DEFAULT_ROM_END_ADDRESS) {
            LOG.warn("Assuming flat ROM mapper up to address: {}", ROM_END_ADDRESS);
        }
        msuMdHandler = MsuMdHandlerImpl.createInstance(systemProvider.getRomPath());
    }

    @Override
    public void init() {
        this.cartridgeInfoProvider = MdCartInfoProvider.createInstance(memoryProvider,
                systemProvider.getRomPath().getFileName().toString());
        initializeRomData();
        LOG.info(cartridgeInfoProvider.toString());
        attachDevice(BusArbiter.createInstance(vdpProvider, m68kProvider, z80Provider));
        this.z80BusRequested = false;
        this.z80ResetState = true;
        detectState();
        LOG.info("Bus state: " + busState);
    }

    @Override
    public void resetFrom68k() {
        this.getFm().reset();
        this.z80Provider.reset();
        z80ResetState = true;
    }

    @Override
    public boolean is68kRunning() {
        return busArbiter.is68kRunning();
    }

    @Override
    public void setVdpBusyState(VdpBusyState state) {
        busArbiter.setVdpBusyState(state);
    }

    @Override
    public GenesisBusProvider attachDevice(Device device) {
        if (device instanceof BusArbiter) {
            this.busArbiter = (BusArbiter) device;
            getDeviceIfAny(Z80Provider.class).ifPresent(zp -> zp.getZ80BusProvider().attachDevice(device));
        }
        super.attachDevice(device);
        return this;
    }

    private void detectState() {
        boolean ok = Objects.nonNull(systemProvider) && Objects.nonNull(m68kProvider) && Objects.nonNull(joypadProvider) && Objects.nonNull(vdpProvider) &&
                Objects.nonNull(memoryProvider) && Objects.nonNull(z80Provider) && Objects.nonNull(soundProvider) && Objects.nonNull(cartridgeInfoProvider)
                && Objects.nonNull(busArbiter);
        busState = ok ? BusState.READY : BusState.NOT_READY;
    }

    @Override
    public long read(long address, Size size) {
        if (verbose) {
            long res = mapper.readData(address, size);
            logInfo("Read address: {}, size: {}, result: {}",
                    Long.toHexString(address), size, Long.toHexString(res));
            return res;
        }
        return mapper.readData(address, size);
    }

    @Override
    public void write(long address, long data, Size size) {
        if (verbose) {
            logInfo("Write address: {}, data: {}, size: {}", Long.toHexString(address),
                    Long.toHexString(data), size);
        }
        mapper.writeData(address, data, size);
    }

    @Override
    public long readData(long addressL, Size size) {
        int address = (int) (addressL & 0xFF_FFFF);
        if (address <= ROM_END_ADDRESS) {  //ROM
            if (cartridgeInfoProvider.isSramUsedWithBrokenHeader(address)) { // Buck Rogers
                checkBackupMemoryMapper(SramMode.READ_WRITE);
                return mapper.readData(address, size);
            }
            return Util.readRom(memoryProvider, size, address);
        } else if (address >= ADDRESS_RAM_MAP_START && address <= ADDRESS_UPPER_LIMIT) {  //RAM (64K mirrored)
            return Util.readRam(memoryProvider, size, address & M68K_RAM_MASK);
        } else if (address > DEFAULT_ROM_END_ADDRESS && address < Z80_ADDRESS_SPACE_START) {  //Reserved
            LOG.warn("Read on reserved address: {}, {}", Integer.toHexString(address), size);
            if (address == 0x400100) { //TODO
                return Util.readRom(memoryProvider, size, address & 0xFFFF);
            }
            return size.getMax();
        } else if (address >= Z80_ADDRESS_SPACE_START && address <= Z80_ADDRESS_SPACE_END) {    //	Z80 addressing space
            return z80MemoryRead(address, size);
        } else if (address >= IO_ADDRESS_SPACE_START && address <= IO_ADDRESS_SPACE_END) {    //IO Addressing space
            return ioRead(address, size);
        } else if (address >= INTERNAL_REG_ADDRESS_SPACE_START && address <= INTERNAL_REG_ADDRESS_SPACE_END) {
            return internalRegRead(address, size);
        } else if (address >= VDP_ADDRESS_SPACE_START && address <= VDP_ADDRESS_SPACE_END) { // VDP
            return vdpRead(address, size);
        } else {
            LOG.error("Unexpected bus read: {}, 68k PC: {}",
                    Long.toHexString(address), Long.toHexString(m68kProvider.getPC()));
        }
        return 0xFF;
    }

    @Override
    public void writeData(long addressL, long data, Size size) {
        int address = (int) (addressL & 0xFF_FFFF);
        data &= size.getMask();
        if (address >= ADDRESS_RAM_MAP_START && address <= ADDRESS_UPPER_LIMIT) {  //RAM (64K mirrored)
            Util.writeRam(memoryProvider, size, address & M68K_RAM_MASK, data);
        } else if (address >= Z80_ADDRESS_SPACE_START && address <= Z80_ADDRESS_SPACE_END) {    //	Z80 addressing space
            z80MemoryWrite(address, size, data);
        } else if (address >= IO_ADDRESS_SPACE_START && address <= IO_ADDRESS_SPACE_END) {    //	IO addressing space
            ioWrite(address, size, data);
        } else if (address >= INTERNAL_REG_ADDRESS_SPACE_START && address <= INTERNAL_REG_ADDRESS_SPACE_END) {
            internalRegWrite(address, size, data);
        } else if (address >= VDP_ADDRESS_SPACE_START && address < VDP_ADDRESS_SPACE_END) {  //VDP
            vdpWrite(address, size, data);
        } else if (address <= ROM_END_ADDRESS) {
            cartWrite(address, data, size);
        } else {
            LOG.error("Unexpected bus write: {}, 68k PC: {}",
                    Long.toHexString(addressL), Long.toHexString(m68kProvider.getPC()));
        }
    }

    private void cartWrite(long addressL, long data, Size size) {
        if (cartridgeInfoProvider.isSramUsedWithBrokenHeader(addressL)) { // Buck Rogers
            LOG.info("Unexpected Sram write: " + Long.toHexString(addressL) + ", value : " + data);
            boolean adjust = cartridgeInfoProvider.adjustSramLimits(addressL);
            checkBackupMemoryMapper(SramMode.READ_WRITE, adjust);
            mapper.writeData(addressL, data, size);
            return;
        }
        //Batman&Robin writes to address 0 - tries to enable debug mode?
        LOG.warn("Unexpected write to ROM address {}, value {}", Long.toHexString(addressL), data);
    }

    private void logVdpCounter(int v, int h) {
        if (verbose) {
            LOG.info("Read HV counter, hce={}, vce={}", Long.toHexString(h), Long.toHexString(v));
        }
    }

    //If the 68k wishes to access anything in the Z80 address space, the Z80 must be stopped.
    // This can be accomplished through the register at $A11100.
    // To stop the Z80 and send a bus request, #$0100 must be written to $A11100.
    // To see if the Z80 has stopped, bit 0 of $A11100 must be checked - if it's clear,
    // the 68k may access the bus, but wait if it is set.
    // Once access to the Z80 area is complete,
    // #$0000 needs to be written to $A11100 to return the bus back to the Z80.
    // No waiting to see if the bus is returned is required here ? it is handled automatically.
    // However, if the Z80 is required to be reset (for example, to load a new program to it's memory)
    // this may be done by writing #$0000 to $A11200, but only when the Z80 bus is requested.
    // After returning the bus after loading the new program to it's memory,
    // the Z80 may be let go from reset by writing #$0100 to $A11200.

//            Reading this bit will return 0 if the bus can be accessed by the 68000,
//            or 1 if the Z80 is still busy.

    //            If the Z80 is reset, or if it is running (meaning the 68000 does not
//            have the bus) it will also return 1. The only time it will switch from
//            1 to 0 is right after the bus is requested, and if the Z80 is still
//            busy accessing memory or not.
    private long internalRegRead(int address, Size size) {
        if (address >= Z80_BUS_REQ_CONTROL_START && address <= Z80_BUS_REQ_CONTROL_END) {
            return z80BusReqRead(size);
        } else if (address >= Z80_RESET_CONTROL_START && address <= Z80_RESET_CONTROL_END) {
            //NOTE few roms read a11200
            LOG.warn("Unexpected Z80 read at: {}, {}", Long.toHexString(address), size);
        } else if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
            return msuMdHandler.handleMsuMdRead(address, size);
        } else if (address >= TIME_LINE_START && address <= TIME_LINE_END) {
            //NOTE genTest does: cmpi.l #'MARS',$A130EC  ;32X
            LOG.warn("Unexpected /TIME or mapper read at: " + Long.toHexString(address));
        } else if (address >= TMSS_AREA1_START && address <= TMSS_AREA1_END) {
            LOG.warn("TMSS read enable cart");
        } else if (address >= TMSS_AREA2_START && address <= TMSS_AREA2_END) {
            LOG.warn("TMSS read enable cart");
        } else if (address >= MEMORY_MODE_START && address <= MEMORY_MODE_END) {
            LOG.warn("Memory mode reg read");
        } else if (address >= SVP_REG_AREA_START && address <= SVP_REG_AREA_END) {
            checkSvpMapper();
            return svpMapper.m68kSvpRegRead(address, size);
        } else {
            LOG.error("Unexpected internalRegRead: {} , {}", Long.toHexString(address), size);
        }
        return 0xFF;
    }

    private void internalRegWrite(int address, Size size, long data) {
        if (address >= MEMORY_MODE_START && address <= MEMORY_MODE_END) {
//            Only D8 of address $A11OO0 is effective and for WRITE ONLY.
//            $A11OO0 D8 ( W)
//            O: ROM MODE
//            1: D-RAM MODE
            LOG.info("Setting memory mode to: {}", data);
        } else if (address >= Z80_BUS_REQ_CONTROL_START && address <= Z80_BUS_REQ_CONTROL_END) {
            z80BusReqWrite(address, data, size);
        } else if (address >= Z80_RESET_CONTROL_START && address <= Z80_RESET_CONTROL_END) {
            z80ResetControlWrite(data);
        } else if (address >= TIME_LINE_START && address <= TIME_LINE_END) {
            timeLineControlWrite(address, data);
        } else if (address >= TMSS_AREA1_START && address <= TMSS_AREA1_END) {
            // used to lock/unlock the VDP by writing either "SEGA" to unlock it or anything else to lock it.
            LOG.warn("TMSS write, vdp lock: " + Integer.toHexString((int) data));
        } else if (address == TMSS_AREA2_START || address == TMSS_AREA2_END) {
//            control the bankswitching between the cart and the TMSS rom.
//            Setting the first bit to 1 enable the cart, and setting it to 0 enable the TMSS.
            LOG.warn("TMSS write enable cart: " + (data == 1));
        } else if (address >= SVP_REG_AREA_START && address <= SVP_REG_AREA_END) {
            checkSvpMapper();
            svpMapper.m68kSvpRegWrite(address, data, size);
        } else if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
            msuMdHandler.handleMsuMdWrite(address, (int) data, size);
        } else {
            LOG.warn("Unexpected internalRegWrite: {}, {}, {}", Integer.toHexString(address),
                    Long.toHexString(data), size);
        }
    }

    private int z80BusReqRead(Size size) {
        int value = z80BusRequested ? 0 : 1;
        value = z80ResetState ? 1 : value;
//            Bit 0 of A11100h (byte access) or bit 8 of A11100h (word access) controls
//            the Z80's /BUSREQ line.
        if (size == Size.WORD) {
            //NOTE: Time Killers is buggy and needs bit0 !=0
            value = (value << 8) | (m68kProvider.getPrefetchWord() & 0xFF);
        }
        LOG.debug("Read Z80 busReq: {} {}", size, value);
        return value;
    }

    /**
     * 0xA130F1
     * * <p>
     * * 7  6  5  4  3  2  1  0
     * * +-----------------------+
     * * |??|??|??|??|??|??|WP|MD|
     * * +-----------------------+
     * * <p>
     * * MD:     Mode -- 0 = ROM, 1 = RAM
     * * WP:     Write protect -- 0 = writable, 1 = not writable
     * * <p>
     */
    private void timeLineControlWrite(int addressL, long data) {
        if (addressL >= Ssf2Mapper.BANK_SET_START_ADDRESS && addressL <= Ssf2Mapper.BANK_SET_END_ADDRESS) {
            LOG.info("Mapper bank set, address: {} , data: {}", Long.toHexString(addressL),
                    Integer.toHexString((int) data));
            checkSsf2Mapper();
            ROM_END_ADDRESS = DEFAULT_ROM_END_ADDRESS;
            mapper.writeBankData(addressL, data);
        } else if (addressL == 0xA130F1) {
            boolean rom = (data & 1) == 0;
            if (rom) {
                checkSsf2Mapper();
            } else {
                //NOTE: seems like 1 allows sram writes
//                SramMode sramMode = (data & 2) > 0 ? SramMode.READ_WRITE : SramMode.READ_ONLY;
                checkBackupMemoryMapper(SramMode.READ_WRITE);
            }
            LOG.info("Mapper register set: {}, {}", data, mapper.getClass().getSimpleName());
        } else {
            LOG.warn("Unexpected mapper set, address: {}, data: {}", Long.toHexString(addressL),
                    Integer.toHexString((int) data));
        }
    }

    private void z80ResetControlWrite(long data) {
        //	if the Z80 is required to be reset (for example, to load a new program to it's memory)
        //	this may be done by writing #$0000 to $A11200, but only when the Z80 bus is requested
        if (data == 0x0000) {
            if (z80BusRequested) {
                z80Provider.reset();
                z80ResetState = true;
                getFm().reset();
                LOG.debug("Reset while busRequested");
            } else {
                LOG.debug("Reset while busUnrequested, ignoring");
            }

            //	After returning the bus after loading the new program to it's memory,
            //	the Z80 may be let go from reset by writing #$0100 to $A11200.
        } else if (data == 0x0100 || data == 0x1) {
            z80ResetState = false;
            LOG.debug("Disable reset, busReq : {}", z80BusRequested);
        } else {
            LOG.warn("Unexpected data on busReset: " + Integer.toBinaryString((int) data));
        }
    }

    private void z80BusReqWrite(int addressL, long data, Size size) {
        LOG.debug("Write Z80 busReq: {} {}", size, data);
        //	To stop the Z80 and send a bus request, #$0100 must be written to $A11100.
        if (size == Size.WORD) {
            // Street Fighter 2 sends 0xFFFF, Monster World 0xFEFF, Slap Fight 0xFF
            data >>= 8;
        }
        data &= 1;
        if (data > 0) {
            boolean isReset = z80ResetState;
            if (!z80BusRequested) {
                LOG.debug("busRequested, reset: {}", isReset);
                z80BusRequested = true;
            } else {
                LOG.debug("busRequested, ignored, reset: {}", isReset);
            }
            //	 #$0000 needs to be written to $A11100 to return the bus back to the Z80
        } else {
            if (z80BusRequested) {
                z80BusRequested = false;
                LOG.debug("busUnrequested, reset : {}", z80ResetState);
            } else {
                LOG.debug("busUnrequested ignored");
            }
        }
    }

    /**
     * $A10001	REG_VERSION
     * Bit	7
     * 0 : Domestic (Japanese model)
     * 1 : Oversea (US or European model)
     * Bit	6
     * 0 : NTSC Clock (7.67Mhz)
     * 1 : PAL Clock (7.60Mhz)
     * Bit	5
     * 0 : Expansion unit connected
     * 1 : Expansion unit not connected
     * Bit 0-4
     * ?	Version number
     * <p>
     * TMSS = REGION_CODE + 1
     */
    private long ioRead(long address, Size size) {
        long data = 0;
        if ((address & 0xFFF) <= 1) {    //	Version register (read-only word-long)
            data = systemProvider.getRegionCode() | (enableTmss ? 1 : 0);
            data = size == Size.WORD ? (data << 8) | data : data;
            return data;
        }
        switch (size) {
            case BYTE:
            case WORD:
                data = ioReadInternal(address);
                break;
            case LONG:
                //Codemasters
                data = ioReadInternal(address);
                data = data << 16 | ioReadInternal(address + 2);
                break;
        }
        return data;
    }

    private long ioReadInternal(long addressL) {
        long data = 0;
        //both even and odd addresses
        int address = (int) (addressL & 0xFFE);
        switch (address) {
            case 2:
                data = joypadProvider.readDataRegister1();
                break;
            case 4:
                data = joypadProvider.readDataRegister2();
                break;
            case 6:
                data = joypadProvider.readDataRegister3();
                break;
            case 8:
                data = joypadProvider.readControlRegister1();
                break;
            case 0xA:
                data = joypadProvider.readControlRegister2();
                break;
            case 0xC:
                data = joypadProvider.readControlRegister3();
                break;
            case 0x12:
            case 0x18:
            case 0x1E:
                int scNumber = address == 0x12 ? 1 : ((address == 0x18) ? 2 : 3);
                LOG.info("Reading serial control{}, {}", scNumber, Long.toHexString(address));
                break;
            default:
                LOG.warn("Unexpected ioRead: {}", Long.toHexString(addressL));
                break;
        }
        return data;
    }

    private void ioWrite(int address, Size size, long data) {
        switch (size) {
            case BYTE:
            case WORD:
                ioWriteInternal(address, data);
                break;
            case LONG:
                //Flink
                ioWriteInternal(address, data >> 16);
                ioWriteInternal(address + 2, data & 0xFFFF);
                break;
        }
    }

    private void ioWriteInternal(int addressL, long data) {
        //both even and odd addresses
        int address = addressL & 0xFFE;
        switch (address) {
            case 2:
                joypadProvider.writeDataRegister1(data);
                break;
            case 4:
                joypadProvider.writeDataRegister2(data);
                break;
            case 6:
                LOG.debug("Write to expansion port: {}, data: {}", Long.toHexString(address),
                        Long.toHexString(data));
                break;
            case 8:
                joypadProvider.writeControlRegister1(data & 0xFF);
                break;
            case 0xA:
                joypadProvider.writeControlRegister2(data & 0xFF);
                break;
            case 0xC:
                joypadProvider.writeControlRegister3(data & 0xFF);
                break;
            case 0x12:
            case 0x18:
            case 0x1E:
                int scNumber = address == 0x12 ? 1 : ((address == 0x18) ? 2 : 3);
                LOG.debug("Write to controller {} serial: {}, data: {}",
                        scNumber, Long.toHexString(addressL), Long.toHexString(data));
                break;
            default:
                if (address >= 0x20) { //Reserved
                    LOG.warn("Unexpected ioWrite {}, data {}",
                            Long.toHexString(address), Long.toHexString(data));
                }
                break;
        }
    }

    //            The Z80 bus can only be accessed by the 68000 when the Z80 is running
//            and the 68000 has the bus. (as opposed to the Z80 being reset, and/or
//            having the bus itself)
//
//            Otherwise, reading $A00000-A0FFFF will return the MSB of the next
//            instruction to be fetched, and the LSB will be set to zero. Writes
//            are ignored.
//
//            Addresses A08000-A0FFFFh mirror A00000-A07FFFh, so the 68000 cannot
//            access it's own banked memory.
    private long z80MemoryRead(long address, Size size) {
        busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
        if (!z80BusRequested || z80ResetState) {
            LOG.warn("Reading Z80 memory without busreq");
            return 0;
        }
        int addressZ = (int) (address & GenesisBusProvider.M68K_TO_Z80_MEMORY_MASK);
        if (size == Size.BYTE) {
            return z80Provider.readMemory(addressZ);
        } else if (size == Size.WORD) {
            return z80MemoryReadWord(addressZ); //Mario Lemieux Hockey
        } else {
            //Mahjong Cop Ryuu - Shiro Ookami no Yabou (J) [h1C]
            busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
            long dataHigh = z80MemoryReadWord(addressZ);
            long dataLow = z80MemoryReadWord(addressZ + 2);
            return dataHigh << 16 | dataLow;
        }
    }

    private void z80MemoryWrite(int address, Size size, long dataL) {
        busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
        if (!z80BusRequested || z80ResetState) {
            LOG.warn("Writing Z80 memory when bus not requested or Z80 reset");
            return;
        }
        address &= GenesisBusProvider.M68K_TO_Z80_MEMORY_MASK;
        int data = (int) dataL;
        if (size == Size.BYTE) {
            z80Provider.writeMemory(address, data);
        } else if (size == Size.WORD) {
            z80MemoryWriteWord(address, data);
        } else {
            //longword access to Z80 like "Stuck Somewhere In Time" does
            //(where every other byte goes nowhere, it was done because it made bulk transfers faster)
            LOG.debug("Unexpected long write, addr: {}, data: {}", address, dataL);
            busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
            z80MemoryWriteWord(address, data >> 16);
            z80MemoryWriteWord(address + 2, data & 0xFFFF);
        }
    }

    //	https://emu-docs.org/Genesis/gen-hw.txt
    //	When doing word-wide writes to Z80 RAM, only the MSB is written, and the LSB is ignored
    private void z80MemoryWriteWord(int address, int data) {
        z80Provider.writeMemory(address, (data & 0xFFFF) >> 8);
    }

    //    A word-wide read from Z80 RAM has the LSB of the data duplicated in the MSB
    private int z80MemoryReadWord(int address) {
        int data = z80Provider.readMemory(address);
        return data << 8 | data;
    }

    //    Byte-wide reads
//
//    Reading from even VDP addresses returns the MSB of the 16-bit data,
//    and reading from odd address returns the LSB:
    private long vdpRead(int address, Size size) {
        switch (size) {
            case WORD:
            case BYTE:
                return vdpReadInternal(address, size);
            case LONG:
                long res = vdpReadInternal(address, Size.WORD) << 16;
                return res | vdpReadInternal(address + 2, Size.WORD);
        }
        return 0xFF;
    }


    private void vdpWrite(int address, Size size, long data) {
        switch (size) {
            case WORD:
            case BYTE:
                vdpWriteWord(address, size, data);
                break;
            case LONG:
                int address1 = address & 0x1F; //low 5 bits
                if (address1 < 0x8 && checkVdpBusy(address, size, data)) {
                    return;
                }
                vdpWriteWord(address, Size.WORD, data >> 16);
                vdpWriteWord(address + 2, Size.WORD, data & 0xFFFF);
                break;
        }
    }

    private int vdpReadInternal(int addressL, Size size) {
        boolean valid = (addressL & VDP_VALID_ADDRESS_MASK) == VDP_ADDRESS_SPACE_START;
        if (!valid) {
            LOG.error("Illegal VDP read, address {}, size {}", Long.toHexString(addressL), size);
            return 0xFF;
        }
        int address = addressL & 0x1F; //low 5 bits
        if (address <= 0x07) {
            int vdpData = readVdpPortWord(address);
            switch (size) {
                case WORD:
                    return vdpData;
                case BYTE:
                    boolean even = address % 2 == 0;
                    return even ? vdpData >> 8 : vdpData & 0xFF;
            }
            LOG.error("Unexpected {} vdp port read: {}", size, Integer.toHexString(addressL));
            return 0xFF;
        } else if (address <= 0x0E) { //	VDP HV counter
//            Reading the HV counter will return the following data:
//
//            VC7 VC6 VC5 VC4 VC3 VC2 VC1 VC0     (D15-D08)
//            HC8 HC7 HC6 HC5 HC4 HC3 HC2 HC1     (D07-D00)
//
//            VCx = Vertical position in lines.
//            HCx = Horizontal position in pixels.
//
//            According to the manual, VC0 is replaced with VC8 when in interlace mode 2.
//
//            For 8-bit reads, the even byte (e.g. C00008h) returns the V counter, and
//            the odd byte (e.g. C00009h) returns the H counter.
            int v = vdpProvider.getVCounter();
            int h = vdpProvider.getHCounter();
            logVdpCounter(v, h);
            if (size == Size.WORD) {
                return (v << 8) | h;
            } else if (size == Size.BYTE) {
                boolean even = address % 2 == 0;
                return even ? v : h;
            }
        } else if (address == 0x1C) {
            LOG.warn("Ignoring VDP debug register read, address : {}", Long.toHexString(addressL));
        } else if (address > 0x17) {
            LOG.info("vdpRead on unused address: " + Long.toHexString(addressL));
//            return 0xFF;
        } else {
            LOG.warn("Unexpected vdpRead, address: " + Long.toHexString(addressL));
        }
        return 0xFF;
    }

    private int readVdpPortWord(int address) {
        VdpPortType portType = address < 4 ? VdpPortType.DATA : VdpPortType.CONTROL;
        //read word by default
        return vdpProvider.readVdpPortWord(portType);
    }

    //      Doing an 8-bit write to the control or data port is interpreted by
//                the VDP as a 16-bit word, with the data written used for both halfs
//                of the word.
    private void vdpWriteWord(int addressLong, Size size, long data) {
        boolean valid = (addressLong & VDP_VALID_ADDRESS_MASK) == VDP_ADDRESS_SPACE_START;
        if (!valid) {
            LOG.error("Illegal VDP write, address {}, data {}, size {}",
                    Long.toHexString(addressLong), Long.toHexString(data), size);
            throw new RuntimeException("Illegal VDP write");
        }

        int address = addressLong & 0x1F; //low 5 bits
        if (address < 0x8) { //DATA or CONTROL PORT
            VdpPortType portType = address < 0x4 ? VdpPortType.DATA :
                    VdpPortType.CONTROL;
            if (checkVdpBusy(addressLong, size, data)) {
                return;
            }
            if (size == Size.BYTE) {
                data = data << 8 | data;
            }
            vdpProvider.writeVdpPortWord(portType, (int) data);
        } else if (address < 0x0F) {   //HV Counter
            LOG.warn("HV counter write");
        }
        //            Doing byte-wide writes to even PSG addresses has no effect.
//
//            If you want to write to the PSG via word-wide writes, the data
//            must be in the LSB. For instance:
//
//            move.b      (a4)+, d0       ; PSG data in LSB
//            move.w      d0, $C00010     ; Write to PSG
        else if (address >= 0x10 && address < 0x18) {
            int psgData = (int) (data & 0xFF);
            if (size == Size.WORD) {
                LOG.warn("PSG word write, address: " + Long.toHexString(address) + ", data: " + data);
            }
            soundProvider.getPsg().write(psgData);
        } else {
            LOG.warn("Unexpected vdpWrite, address: " + Long.toHexString(address) + ", data: " + data);
        }
    }

    //NOTE: this assumes that only 68k writes on the vpdDataPort
    private boolean checkVdpBusy(int address, Size size, long data) {
        if (busArbiter.getVdpBusyState() == VdpBusyState.FIFO_FULL ||
                busArbiter.getVdpBusyState() == VdpBusyState.MEM_TO_VRAM) {
            vdpRunnable.vdpAddress = address;
            vdpRunnable.vdpSize = size;
            vdpRunnable.vpdData = data;
            busArbiter.runLater(vdpRunnable);
            return true;
        }
        return false;
    }

    private void checkSsf2Mapper() {
        if (ssf2Mapper == RomMapper.NO_OP_MAPPER) {
            this.ssf2Mapper = Ssf2Mapper.createInstance(this, memoryProvider);
        }
        mapper = ssf2Mapper;
    }

    private void checkSvpMapper() {
        if (svpMapper == SvpBus.NO_OP) {
            this.svpMapper = SvpMapper.createInstance(this, memoryProvider);
            mapper = svpMapper;
            LOG.info("Enabling mapper: {}", mapper.getClass().getSimpleName());
        }
    }

    private void checkBackupMemoryMapper(SramMode sramMode) {
        checkBackupMemoryMapper(sramMode, false);
    }

    private void checkBackupMemoryMapper(SramMode sramMode, boolean forceCreate) {
        if (backupMemMapper == RomMapper.NO_OP_MAPPER) {
            MdRomDbModel.Entry entry = forceCreate ? this.entry : MdLoader.NO_ENTRY;
            this.backupMemMapper = MdBackupMemoryMapper.createInstance(this, cartridgeInfoProvider, sramMode, entry);
        }
        backupMemMapper.setSramMode(sramMode);
        this.mapper = backupMemMapper;
    }

    @Override
    public boolean isSvp() {
        return svpMapper != SvpBus.NO_OP;
    }

    @Override
    public FmProvider getFm() {
        return soundProvider.getFm();
    }

    @Override
    public PsgProvider getPsg() {
        return soundProvider.getPsg();
    }

    @Override
    public SystemProvider getSystem() {
        return systemProvider;
    }

    @Override
    public GenesisVdpProvider getVdp() {
        return vdpProvider;
    }

    @Override
    public boolean isZ80Running() {
        return !z80ResetState && !z80BusRequested;
    }

    @Override
    public void setZ80BusRequested(boolean z80BusRequested) {
        this.z80BusRequested = z80BusRequested;
    }

    @Override
    public void setZ80ResetState(boolean z80ResetState) {
        this.z80ResetState = z80ResetState;
    }

    @Override
    public boolean isZ80BusRequested() {
        return z80BusRequested;
    }

    @Override
    public boolean isZ80ResetState() {
        return z80ResetState;
    }

    @Override
    public void handleVdpInterrupts68k() {
        busArbiter.handleInterrupts68k();
    }

    @Override
    public void handleVdpInterruptsZ80() {
        busArbiter.handleInterruptZ80();
    }

    @Override
    public int[] getMapperData() {
        if (ssf2Mapper instanceof Ssf2Mapper) {
            return ((Ssf2Mapper) ssf2Mapper).getState();
        }
        return new int[0];
    }

    @Override
    public void setMapperData(int[] data) {
        if (ssf2Mapper instanceof Ssf2Mapper) {
            ((Ssf2Mapper) ssf2Mapper).setState(data);
        }
    }

    private static void logInfo(String str, Object... args) {
        if (verbose) {
            LOG.info(new ParameterizedMessage(str, args));
        }
    }

    @Override
    public void closeRom() {
        if (mapper != this) {
            mapper.closeRom();
        }
        ssf2Mapper.closeRom();
        backupMemMapper.closeRom();
        msuMdHandler.close();
    }

    @Override
    public void onNewFrame() {
        joypadProvider.newFrame();
    }
}
