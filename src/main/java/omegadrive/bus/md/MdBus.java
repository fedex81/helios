/*
 * MdBus
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

package omegadrive.bus.md;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.bus.model.SvpBus;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cart.loader.MdRomDbModel;
import omegadrive.cart.loader.MdRomDbModel.RomDbEntry;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.cart.mapper.md.ExSsfMapper;
import omegadrive.cart.mapper.md.MdBackupMemoryMapper;
import omegadrive.cart.mapper.md.Ssf2Mapper;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.MdJoypad;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.msumd.MsuMdHandler;
import omegadrive.sound.msumd.MsuMdHandlerImpl;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.MdVdpProvider;
import omegadrive.vdp.model.MdVdpProvider.VdpBusyState;
import omegadrive.vdp.model.MdVdpProvider.VdpPortType;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;

import static omegadrive.cart.mapper.md.Ssf2Mapper.BANK_SET_END_ADDRESS;
import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;
import static omegadrive.joypad.JoypadProvider.JoypadType.BUTTON_3;
import static omegadrive.system.SystemProvider.SystemEvent.FORCE_PAD_TYPE;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.Z80;
import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.LogHelper.logWarnOnceWhenEn;
import static omegadrive.util.Util.th;

public class MdBus extends DeviceAwareBus<MdVdpProvider, MdJoypad> implements MdMainBusProvider, RomMapper {

    private static final Logger LOG = LogHelper.getLogger(MdBus.class.getSimpleName());

    public final static boolean verbose = false;
    public static final int M68K_CYCLE_PENALTY = 3;
    protected MdCartInfoProvider cartridgeInfoProvider;
    private RomMapper mapper;
    private RomMapper exSsfMapper = RomMapper.NO_OP_MAPPER;
    private RomMapper backupMemMapper = RomMapper.NO_OP_MAPPER;
    private SvpBus svpMapper = SvpBus.NO_OP;
    protected MsuMdHandler msuMdHandler = MsuMdHandler.NO_OP_HANDLER;
    private BusArbiter busArbiter = BusArbiter.NO_OP;

    final BusWriteRunnable vdpRunnable = new BusWriteRunnable() {
        @Override
        public void run() {
            CpuDeviceAccess prev = MdRuntimeData.setAccessTypeExt(cpu);
//            LOG.info("{}, {} {}", th(vdpAddress), vpdData, vdpSize);
            write(address, data, size);
            MdRuntimeData.setAccessTypeExt(prev);
        }
    };

    private static long ROM_END_ADDRESS;

    enum BusState {READY, NOT_READY}

    private BusState busState = BusState.NOT_READY;

    protected byte[] rom, ram;
    protected int romMask;
    private boolean z80BusRequested;
    private boolean z80ResetState;
    private int sramLockValue;
    private final boolean enableTmss;

    //NOTE only a stub for serial ports, not supported
    private byte[] serialPortData = new byte[20];

    public MdBus() {
        this.mapper = this;
        this.enableTmss = Boolean.parseBoolean(System.getProperty("md.enable.tmss", "false"));
    }

    void initializeRomData() {
//        assert systemProvider.getMediaSpec().cartFile.bootable;
        SystemLoader.SystemType st = systemProvider.getSystemType();
        cartridgeInfoProvider = (MdCartInfoProvider) systemProvider.getMediaSpec().getBootableMedia().mediaInfoProvider;
        assert cartridgeInfoProvider != null;
        ROM_END_ADDRESS = Math.min(cartridgeInfoProvider.getRomSize(), Z80_ADDRESS_SPACE_START);
        assert ROM_END_ADDRESS > 0;
        if (cartridgeInfoProvider.getEntry().hasEeprom()) {
            checkBackupMemoryMapper(SramMode.READ_WRITE, cartridgeInfoProvider.getEntry());
        } else if (cartridgeInfoProvider.isSramEnabled()) {
            //default disable, see Vr 32x
            checkBackupMemoryMapper(SramMode.DISABLE);
        }
        if (cartridgeInfoProvider.isSsfMapper()) {
            checkExSsfMapper();
        }
        msuMdHandler = MsuMdHandlerImpl.createInstance(st, systemProvider.getRomPath());
        //some homebrews use a flat ROM mapper, in theory up to Z80_ADDRESS_SPACE_START
        if (st == SystemLoader.SystemType.MD && !cartridgeInfoProvider.isSsfMapper() && ROM_END_ADDRESS > DEFAULT_ROM_END_ADDRESS) {
            LOG.warn("Assuming flat ROM mapper up to address: {}", ROM_END_ADDRESS);
        }
        if (cartridgeInfoProvider.isSvp()) {
            checkSvpMapper();
        }
        if (cartridgeInfoProvider.getEntry().hasForce3Btn()) {
            systemProvider.handleSystemEvent(FORCE_PAD_TYPE, BUTTON_3.name());
        }
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
    public MdMainBusProvider attachDevice(Device device) {
        if (device instanceof BusArbiter) {
            this.busArbiter = (BusArbiter) device;
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
    public int read(int address, Size size) {
        if (verbose) {
            int res = mapper.readData(address, size);
            logInfo("Read address: {}, size: {}, result: {}",
                    th(address), size, th(res));
            return res;
        }
        return mapper.readData(address, size);
    }

    @Override
    public void write(int address, int data, Size size) {
        if (verbose) {
            logInfo("Write address: {}, data: {}, size: {}", th(address),
                    th(data), size);
        }
        mapper.writeData(address, data, size);
    }
    @Override
    public void init() {
        initializeRomData();
        if (busArbiter == null || busArbiter == BusArbiter.NO_OP) {
            attachDevice(BusArbiter.createInstance(vdpProvider, m68kProvider, z80Provider));
        } else {
            LOG.warn("BusArbiter already created");
        }
        this.z80BusRequested = false;
        this.z80ResetState = true;
        detectState();
        LOG.info("Bus state: {}", busState);
        ram = memoryProvider.getRamData();
        rom = memoryProvider.getRomData();
        romMask = memoryProvider.getRomMask();
    }

    @Override
    public int readData(int address, final Size size) {
        address &= MD_PC_MASK;
        int data;
        if (address < ROM_END_ADDRESS) {  //ROM
            data = Util.readDataMask(rom, address, romMask, size);
        } else if (address >= ADDRESS_RAM_MAP_START && address <= ADDRESS_UPPER_LIMIT) {  //RAM (64K mirrored)
            data = Util.readDataMask(ram, address, M68K_RAM_MASK, size);
        } else if (address > DEFAULT_ROM_END_ADDRESS && address < Z80_ADDRESS_SPACE_START) {  //Reserved
            data = reservedRead(address, size);
        } else if (address >= Z80_ADDRESS_SPACE_START && address <= Z80_ADDRESS_SPACE_END) {    //	Z80 addressing space
            data = z80MemoryRead(address, size);
        } else if (address >= IO_ADDRESS_SPACE_START && address <= IO_ADDRESS_SPACE_END) {    //IO Addressing space
            data = ioRead(address, size);
        } else if (address >= INTERNAL_REG_ADDRESS_SPACE_START && address <= INTERNAL_REG_ADDRESS_SPACE_END) {
            data = internalRegRead(address, size);
        } else if (address >= VDP_ADDRESS_SPACE_START && address <= VDP_ADDRESS_SPACE_END) { // VDP
            data = vdpRead(address, size);
        } else if (cartridgeInfoProvider.isSramUsedWithBrokenHeader(address)) { // Buck Rogers
            checkBackupMemoryMapper(SramMode.READ_WRITE);
            data = mapper.readData(address, size);
        } else {
            logWarnOnce(LOG, "Unexpected bus read: {}, 68k PC: {}",
                    th(address), th(m68kProvider.getPC()));
            data = size.getMask();
        }
        return data & size.getMask();
    }

    @Override
    public void writeData(int address, int data, final Size size) {
        //RegAccessLogger.regAccess("M68K",  addressL,  data, size, false);
        address &= MD_PC_MASK;
        data &= size.getMask();
        if (address >= ADDRESS_RAM_MAP_START && address <= ADDRESS_UPPER_LIMIT) {  //RAM (64K mirrored)
            Util.writeDataMask(ram, address, data, M68K_RAM_MASK, size);
        } else if (address >= Z80_ADDRESS_SPACE_START && address <= Z80_ADDRESS_SPACE_END) {    //	Z80 addressing space
            z80MemoryWrite(address, size, data);
        } else if (address >= IO_ADDRESS_SPACE_START && address <= IO_ADDRESS_SPACE_END) {    //	IO addressing space
            ioWrite(address, size, data);
        } else if (address >= INTERNAL_REG_ADDRESS_SPACE_START && address <= INTERNAL_REG_ADDRESS_SPACE_END) {
            internalRegWrite(address, size, data);
        } else if (address >= VDP_ADDRESS_SPACE_START && address < VDP_ADDRESS_SPACE_END) {  //VDP
            vdpWrite(address, size, data);
        } else if (address < ROM_END_ADDRESS) {
            cartWrite(address, data, size);
        } else if (cartridgeInfoProvider.isSramUsedWithBrokenHeader(address)) { // Buck Rogers
            checkBackupMemoryMapper(SramMode.READ_WRITE);
            mapper.writeData(address, data, size);
        } else {
            reservedWrite(address, data, size);
        }
    }

    private void cartWrite(int addressL, int data, Size size) {
        if (cartridgeInfoProvider.isSramUsedWithBrokenHeader(addressL)) { // Buck Rogers
            LOG.info("Unexpected Sram write: {}, value : {}", th(addressL), data);
            boolean adjust = cartridgeInfoProvider.adjustSramLimits(addressL);
            checkBackupMemoryMapper(SramMode.READ_WRITE, adjust);
            mapper.writeData(addressL, data, size);
            return;
        }
        //Batman&Robin writes to address 0 - tries to enable debug mode?
        LogHelper.logWarnOnce(LOG, "Unexpected write to ROM address {} {}", th(addressL), size);
    }

    private void reservedWrite(int addressL, int data, Size size) {
        if (msuMdHandler == MsuMdHandler.NO_OP_HANDLER) {
            logWarnOnce(LOG, "Unexpected bus write: {}, 68k PC: {}", th(addressL),
                    th(m68kProvider.getPC()));
        } else {
            msuMdHandler.handleMsuMdWrite(addressL, data, size);
        }
    }

    private int reservedRead(int address, Size size) {
        if (msuMdHandler == MsuMdHandler.NO_OP_HANDLER) {
            logWarnOnce(LOG, "Read on reserved address: {}, {}", th(address), size);
            return size.getMax();
        } else {
            //reads rom at 0x40_0000 MegaCD mirror
            assert false; //TODO check this
            return Util.readDataMask(rom, address, DEFAULT_ROM_END_ADDRESS, size);
        }
    }

    private void logVdpCounter(int v, int h) {
        if (verbose) {
            LOG.info("Read HV counter, hce={}, vce={}", th(h), th(v));
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
    private int internalRegRead(int address, Size size) {
        if (address >= Z80_BUS_REQ_CONTROL_START && address <= Z80_BUS_REQ_CONTROL_END) {
            return z80BusReqRead(size);
        } else if (address >= Z80_RESET_CONTROL_START && address <= Z80_RESET_CONTROL_END) {
            //NOTE few roms read a11200
            LogHelper.logWarnOnce(LOG, "Unexpected Z80 read at: {}, {}", th(address), size);
        } else if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
            return msuMdHandler.handleMsuMdRead(address, size);
        } else if (address >= TIME_LINE_START && address <= TIME_LINE_END) {
            //NOTE genTest does: cmpi.l #'MARS',$A130EC  ;32X
            return timeLineControlRead(address, size);
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
            LOG.error("Unexpected internalRegRead: {} , {}", th(address), size);
        }
        return 0xFF;
    }

    private void internalRegWrite(int address, Size size, int data) {
        if (address >= MEMORY_MODE_START && address <= MEMORY_MODE_END) {
//            Only D8 of address $A11OO0 is effective and for WRITE ONLY.
//            $A11OO0 D8 ( W)
//            O: ROM MODE
//            1: D-RAM MODE
            LOG.info("Setting memory mode to: {}", data);
        } else if (address >= Z80_BUS_REQ_CONTROL_START && address <= Z80_BUS_REQ_CONTROL_END) {
            z80BusReqWrite(data, size);
        } else if (address >= Z80_RESET_CONTROL_START && address <= Z80_RESET_CONTROL_END) {
            z80ResetControlWrite(data, size);
        } else if (address >= TIME_LINE_START && address <= TIME_LINE_END) {
            assert size != Size.LONG;
            timeLineControlWrite(address, data, size);
        } else if (address >= TMSS_AREA1_START && address <= TMSS_AREA1_END) {
            // used to lock/unlock the VDP by writing either "SEGA" to unlock it or anything else to lock it.
            LOG.warn("TMSS write, vdp lock: {}", th(data));
        } else if (address == TMSS_AREA2_START || address == TMSS_AREA2_END) {
//            control the bankswitching between the cart and the TMSS rom.
//            Setting the first bit to 1 enable the cart, and setting it to 0 enable the TMSS.
            LOG.warn("TMSS write enable cart: {}", data == 1);
        } else if (address >= SVP_REG_AREA_START && address <= SVP_REG_AREA_END) {
            checkSvpMapper();
            svpMapper.m68kSvpRegWrite(address, data, size);
        } else if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
            msuMdHandler.handleMsuMdWrite(address, data, size);
        } else {
            LOG.warn("Unexpected internalRegWrite: {}, {}, {}", th(address),
                    th(data), size);
        }
    }

    // Bit 0 of A11100h (byte access) or bit 8 of A11100h (word access) controls
    // the Z80's /BUSREQ line.
    //0 means the Z80 bus can be accessed by 68k (/BUSACK asserted and/ZRESET released).
    private int z80BusReqRead(Size size) {
        int prefetch = m68kProvider.getPrefetchWord(); //shadow beast[U] needs the prefetch
        int value = (prefetch & 0xFE) | ((z80BusRequested && !z80ResetState) ? 0 : 1);
        if (size == Size.WORD) {
            //NOTE: Time Killers is buggy and needs bit0 !=0
            value = (value << 8) | (prefetch & 0xFEFF);
        }
//        LOG.debug("Read Z80 busReq: {} {}", th(value), size);
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
    private void timeLineControlWrite(int addressL, int data, Size size) {
        if (size == Size.WORD) {
            LogHelper.logWarnOnce(LOG, "timeLineControlWrite should be byte-wide {} {}", th(addressL), size);
            addressL |= 1;
        }
        if (addressL >= Ssf2Mapper.BANK_SET_START_ADDRESS && addressL <= BANK_SET_END_ADDRESS) {
            LOG.info("Mapper bank set, address: {} , data: {}", th(addressL),
                    th(data));
            checkExSsfMapper();
            mapper.writeBankData(addressL, data);
        } else if (addressL == SRAM_LOCK) {
            sramLockValue = data;
            boolean rom = (data & 1) == 0;
            boolean writable = (data & 2) > 0;
            if (rom) {
                checkExSsfMapper();
            } else {
                checkBackupMemoryMapper(writable ? SramMode.READ_WRITE : SramMode.READ_ONLY);
            }
            if (backupMemMapper != NO_OP_MAPPER) {
                backupMemMapper.setSramMode(writable ? SramMode.READ_WRITE : SramMode.READ_ONLY);
            }
            if (verbose) LOG.debug("Mapper register set: {}, {}", data, mapper.getClass().getSimpleName());
        } else {
            LOG.warn("Unexpected mapper set, address: {}, data: {} {}", th(addressL), th(data), size);
        }
    }

    private int timeLineControlRead(int addressL, Size size) {
        if (addressL == SRAM_LOCK && size == Size.BYTE) { //chaotix
            return sramLockValue;
        }
        LOG.warn("Unexpected /TIME or mapper read at: {} {}", th(addressL), size);
        return size.getMask();
    }

    //	if the Z80 is required to be reset (for example, to load a new program to it's memory)
    //	this may be done by writing #$0000 to $A11200, but only when the Z80 bus is requested
    //	After returning the bus after loading the new program to it's memory,
    //	the Z80 may be let go from reset by writing #$0100 to $A11200.
    private void z80ResetControlWrite(int data, Size size) {
        if (verbose) LOG.info("Write Z80 resetControl: {} {}", th(data), size);
        if (size == Size.WORD) {
            data >>>= 8;
        }
        //only data bit0 is connected to the bus arbiter
        boolean reset = (data & 1) == 0;
        if (reset) {
            if (z80BusRequested) {
                z80ResetState = true;
                if (verbose) LOG.info("Reset while busRequested: {}", z80BusRequested);
            } else {
                if (verbose) LOG.warn("Reset while busUnrequested, ignoring");
            }
        } else {
            if (z80ResetState) {
                doZ80Reset();
                z80ResetState = false;
            }
            if (verbose) LOG.info("Disable reset, busReq : {}", z80BusRequested);
        }
    }

    private void doZ80Reset() {
        z80Provider.reset();
        getFm().reset();
    }

    //	To stop the Z80 and send a bus request, #$0100 must be written to $A11100.
    //	 #$0000 needs to be written to $A11100 to return the bus back to the Z80
    //NOTE: Street Fighter 2 sends 0xFFFF, Monster World 0xFEFF, Slap Fight 0xFF
    private void z80BusReqWrite(int data, Size size) {
        if (verbose) LOG.info("Write Z80 busReq: {} {}", th(data), size);
        if (size == Size.WORD) {
            data >>>= 8;
        }
        //only data bit0 is connected to the bus arbiter
        boolean busReq = (data & 1) > 0;
        if (busReq) {
            if (!z80BusRequested) {
                if (verbose) LOG.info("busRequested, reset: {}", z80ResetState);
                z80BusRequested = true;
            } else {
                if (verbose) LOG.info("busRequested, ignored, reset: {}", z80ResetState);
            }
        } else {
            if (z80BusRequested) {
                z80BusRequested = false;
                if (verbose) LOG.info("busUnrequested, reset : {}", z80ResetState);
            } else {
                if (verbose) LOG.info("busUnrequested ignored");
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
    private int ioRead(int address, Size size) {
        int data = 0;
        //	Version register (read-only word-long)
        if ((address & 0xFFF) <= 1) {
            //expansion unit not connected bit#5 set (0x20), otherwise 0
            int expUnit = systemProvider.getSystemType().isMegaCdAttached() ? 0 : 0x20;
            data = expUnit | systemProvider.getRegionCode() | (enableTmss ? 1 : 0);
            data = size == Size.WORD ? (data << 8) | data : data;
            return data;
        }
        switch (size) {
            case BYTE:
            case WORD:
                data = ioReadInternal(address, size);
                break;
            case LONG:
                //Codemasters
                data = ioReadInternal(address, size);
                data = data << 16 | ioReadInternal(address + 2, size);
                break;
        }
        return data;
    }

    private int ioReadInternal(int addressL, Size size) {
        int data = 0xFF;
        //both even and odd addresses
        int address = (addressL & 0xFFE);
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
            default:
                if (address >= 0xE && address < 0x20) {
                    data = Util.readData(serialPortData, address - 0xE, size);
                    LogHelper.logWarnOnce(LOG, "Reading serial control: {} {}, data: {}", th(address), size, th(data));
                } else {
                    LogHelper.logWarnOnce(LOG, "Unexpected ioRead: {}", th(addressL));
                }
                break;
        }
        if (size == Size.WORD) {
            data |= (data << 8) & 0xFF00;
        }
        return data;
    }

    //IO chip only got /LWR so it only reacts to byte-wide writes to low (odd) addresses or word-wide writes.
    // In case of word-wide writes, LSB (D0-D7) is being used when accessing I/O registers.
    private void ioWrite(int address, Size size, int data) {
        switch (size) {
            case BYTE:
                if ((address & 1) == 0) { //Turrican (USA, Europe) (Unl)
                    LogHelper.logWarnOnce(LOG, "Ignore byte-wide writes on even addr: {} {}", th(address), size);
                    return;
                }
            case WORD:
                ioWriteInternal(address, data, size);
                break;
            case LONG:
                //Flink
                ioWriteInternal(address, data >>> 16, size);
                ioWriteInternal(address + 2, data & 0xFFFF, size);
                break;
        }
    }

    private void ioWriteInternal(int addressL, int dataL, Size size) {
        //both even and odd addresses
        int address = addressL & 0xFFE;
        int data = (dataL & 0xFF);
        switch (address) {
            case 2:
                joypadProvider.writeDataRegister1(data);
                break;
            case 4:
                joypadProvider.writeDataRegister2(data);
                break;
            case 6:
                if (verbose) LOG.debug("Write to expansion port: {}, data: {}", th(address), th(data));
                break;
            case 8:
                joypadProvider.writeControlRegister1(data);
                break;
            case 0xA:
                joypadProvider.writeControlRegister2(data);
                break;
            case 0xC:
                joypadProvider.writeControlRegister3(data);
                break;
            default:
                if (address >= 0xE && address < 0x20) {
                    logWarnOnceWhenEn(LOG, "Write serial control: {}", th(addressL));
                    Util.writeData(serialPortData, address - 0xE, data, size);
                } else { //Reserved
                    LOG.error("Unexpected ioWrite {}, data {}", th(addressL), th(data));
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
    private int z80MemoryRead(int address, Size size) {
        busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
        if (!z80BusRequested || z80ResetState) {
            //uwol
            LOG.warn("68k read access to Z80 bus with busreq: {}, z80reset: {}", z80BusRequested, z80ResetState);
            return 0; //TODO this should return z80 open bus (ie. prefetch?)
        }
        int addressZ = (address & MdMainBusProvider.M68K_TO_Z80_MEMORY_MASK);
        if (size == Size.BYTE) {
            return z80Provider.readMemory(addressZ);
        } else if (size == Size.WORD) {
            return z80MemoryReadWord(addressZ); //Mario Lemieux Hockey
        } else {
            //Mahjong Cop Ryuu - Shiro Ookami no Yabou (J) [h1C]
            busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
            int dataHigh = z80MemoryReadWord(addressZ);
            int dataLow = z80MemoryReadWord(addressZ + 2);
            return dataHigh << 16 | dataLow;
        }
    }

    private void z80MemoryWrite(int address, Size size, int dataL) {
        busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
        if (!z80BusRequested || z80ResetState) {
            logWarnOnce(LOG, "68k write access to Z80 bus with busreq: {}, z80reset: {}", z80BusRequested, z80ResetState);
            return;
        }
        address &= MdMainBusProvider.M68K_TO_Z80_MEMORY_MASK;
        if (size == Size.BYTE) {
            z80Provider.writeMemory(address, dataL);
        } else if (size == Size.WORD) {
            z80MemoryWriteWord(address, dataL);
        } else {
            //longword access to Z80 like "Stuck Somewhere In Time" does
            //(where every other byte goes nowhere, it was done because it made bulk transfers faster)
            if (verbose) LOG.debug("Unexpected long write, addr: {}, data: {}", address, dataL);
            busArbiter.addCyclePenalty(BusArbiter.CpuType.M68K, M68K_CYCLE_PENALTY);
            z80MemoryWriteWord(address, dataL >>> 16);
            z80MemoryWriteWord(address + 2, dataL & 0xFFFF);
        }
    }

    //	https://emu-docs.org/Genesis/gen-hw.txt
    //	When doing word-wide writes to Z80 RAM, only the MSB is written, and the LSB is ignored
    private void z80MemoryWriteWord(int address, int data) {
        z80Provider.writeMemory(address, (data >> 8) & 0xFF);
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
    private int vdpRead(int address, Size size) {
        switch (size) {
            case WORD:
            case BYTE:
                return vdpReadInternal(address, size);
            case LONG:
                int res = vdpReadInternal(address, Size.WORD) << 16;
                return res | vdpReadInternal(address + 2, Size.WORD);
        }
        return 0xFF;
    }


    private void vdpWrite(int address, Size size, int data) {
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
                vdpWriteWord(address, Size.WORD, data >>> 16);
                vdpWriteWord(address + 2, Size.WORD, data & 0xFFFF);
                //TODO fixes Bubba but breaks Fatal Rewind
//                m68kProvider.addCyclePenalty(22);
                break;
        }
    }

    private int vdpReadInternal(int addressL, Size size) {
        boolean valid = (addressL & VDP_VALID_ADDRESS_MASK) == VDP_ADDRESS_SPACE_START;
        if (!valid) {
            LogHelper.logWarnOnce(LOG, "Illegal VDP read at {} {}", th(addressL), size);
            return size.getMask();
        }
        int address = addressL & 0x1F; //low 5 bits
        if (address <= 0x07) {
            int vdpData = readVdpPortWord(address);
            switch (size) {
                case WORD:
                    return vdpData;
                case BYTE:
                    return (address & 1) == 0 ? vdpData >>> 8 : vdpData & 0xFF; //even
            }
            LOG.error("Unexpected {} vdp port read: {}", size, th(addressL));
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
                return (address & 1) == 0 ? v : h; //even
            }
        } else if (address == 0x1C) {
            LOG.warn("Ignoring VDP debug register read, address : {}", th(addressL));
        } else if (address > 0x17) {
            LOG.info("vdpRead on unused address: {}", th(addressL));
//            return 0xFF;
        } else {
            LOG.warn("Unexpected vdpRead, address: {}", th(addressL));
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
    private void vdpWriteWord(int addressLong, Size size, int data) {
        boolean valid = (addressLong & VDP_VALID_ADDRESS_MASK) == VDP_ADDRESS_SPACE_START;
        if (!valid) {
            LOG.error("Illegal VDP write, address {}, data {}, size {}",
                    th(addressLong), th(data), size);
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
            vdpProvider.writeVdpPortWord(portType, data);
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
            int psgData = (data & 0xFF);
            if (size == Size.WORD) {
                LOG.warn("PSG word write, address: {}, data: {}", th(address), data);
            }
            soundProvider.getPsg().write(psgData);
        } else {
            LOG.warn("Unexpected vdpWrite, address: {}, data: {}", th(address), data);
        }
    }

    //NOTE: this assumes that only 68k writes on the vpdDataPort
    private boolean checkVdpBusy(int address, Size size, int data) {
        if (busArbiter.getVdpBusyState() == VdpBusyState.FIFO_FULL ||
                busArbiter.getVdpBusyState() == VdpBusyState.MEM_TO_VRAM) {
            vdpRunnable.cpu = MdRuntimeData.getAccessTypeExt();
            vdpRunnable.address = address;
            vdpRunnable.size = size;
            vdpRunnable.data = data;
            busArbiter.runLater68k(vdpRunnable);
            return true;
        }
        return false;
    }

    private void checkExSsfMapper() {
        if (exSsfMapper == RomMapper.NO_OP_MAPPER) {
            this.exSsfMapper = ExSsfMapper.createInstance(this, memoryProvider);
        }
        mapper = exSsfMapper;
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

    private void checkBackupMemoryMapper(SramMode sramMode, boolean forceCreateSram) {
        checkBackupMemoryMapper(sramMode, forceCreateSram ? cartridgeInfoProvider.getEntry() : MdRomDbModel.NO_ENTRY);
    }

    private void checkBackupMemoryMapper(SramMode sramMode, RomDbEntry entry) {
        if (backupMemMapper == RomMapper.NO_OP_MAPPER) {
            this.backupMemMapper = MdBackupMemoryMapper.createInstance(this,
                    cartridgeInfoProvider, sramMode, entry);
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
    public MdVdpProvider getVdp() {
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
        busArbiter.handleInterrupts(M68K);
    }

    @Override
    public void handleVdpInterruptsZ80() {
        busArbiter.handleInterrupts(Z80);
    }

    @Override
    public void ackInterrupt68k(int level) {
        busArbiter.ackInterrupt68k(level);
    }

    @Override
    public int[] getMapperData() {
        return getStateAwareMapper().getState();
    }

    @Override
    public void setMapperData(int[] data) {
        getStateAwareMapper().setState(data);
    }

    private StateAwareMapper getStateAwareMapper() {
        StateAwareMapper m = NO_STATE;
        if (exSsfMapper instanceof StateAwareMapper) {
            m = (StateAwareMapper) exSsfMapper;
        } else if (mapper instanceof StateAwareMapper) { //MdT5740Mapper
            m = (StateAwareMapper) mapper;
        }
        return m;
    }

    public MdCartInfoProvider getCartridgeInfoProvider() {
        return cartridgeInfoProvider;
    }

    private static void logInfo(String str, Object... args) {
        if (verbose) {
            LOG.info(LogHelper.formatMessage(str, args));
        }
    }

    @Override
    public void closeRom() {
        if (mapper != this) {
            mapper.closeRom();
        }
        exSsfMapper.closeRom();
        if (mapper != backupMemMapper) {
            backupMemMapper.closeRom();
        }
        msuMdHandler.close();
    }

    @Override
    public void onNewFrame() {
        Optional.ofNullable(joypadProvider).ifPresent(JoypadProvider::newFrame);
    }
}
