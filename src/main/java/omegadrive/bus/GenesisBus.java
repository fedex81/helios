package omegadrive.bus;

import omegadrive.Genesis;
import omegadrive.GenesisProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.m68k.M68kProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.util.CartridgeInfoProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.VdpProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * GenesisBus
 *
 * @author Federico Berti
 * <p>
 * Based on genefusto GenVdp
 * https://github.com/DarkMoe/genefusto
 * @author DarkMoe
 *
 * https://wiki.megadrive.org/index.php?title=IO_Registers
 * https://www.gamefaqs.com/genesis/916377-genesis/faqs/9755
 * http://darkdust.net/writings/megadrive/initializing
 *
 */
public class GenesisBus implements BusProvider, GenesisMapper {

    private static Logger LOG = LogManager.getLogger(GenesisBus.class.getSimpleName());

    private static boolean verbose = false || Genesis.verbose;

    private GenesisProvider emu;
    private MemoryProvider memory;
    private JoypadProvider joypad;

    public VdpProvider vdp;
    public Z80Provider z80;
    public M68kProvider cpu;
    public SoundProvider sound;

    public CartridgeInfoProvider cartridgeInfoProvider;
    private GenesisMapper mapper;

    public static long ROM_START_ADDRESS;
    public static long ROM_END_ADDRESS;
    public static long RAM_START_ADDRESS;
    public static long RAM_END_ADDRESS;

    enum BusState {READY, NOT_READY}

    private BusState busState = BusState.NOT_READY;
    private BusProvider.VdpIntState vdpIntState = BusProvider.VdpIntState.NONE;

    private boolean stop68k = false;

    protected GenesisBus() {
        this.mapper = this;
    }

    void initializeRomData() {
        ROM_START_ADDRESS = cartridgeInfoProvider.getRomStart();
        ROM_END_ADDRESS = cartridgeInfoProvider.getRomEnd();
        RAM_START_ADDRESS = cartridgeInfoProvider.getRamStart();
        RAM_END_ADDRESS = cartridgeInfoProvider.getRamEnd();
        if (cartridgeInfoProvider.isSramEnabled()) {
            mapper = BackupMemoryMapper.createInstance(this, cartridgeInfoProvider);
        }
    }

    @Override
    public void reset() {
        this.cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memory);
        initializeRomData();
        LOG.info(cartridgeInfoProvider.toString());
        detectState();
        LOG.info("Bus state: " + busState);
    }

    @Override
    public boolean shouldStop68k() {
        return stop68k;
    }

    @Override
    public void setStop68k(boolean value) {
        if (stop68k != value) {
            LOG.debug("VDP stop 68k: {}", value);
        }
        stop68k = value;
    }

    @Override
    public BusProvider attachDevice(Object device) {
        if (device instanceof MemoryProvider) {
            this.memory = (MemoryProvider) device;
        }
        if (device instanceof GenesisProvider) {
            this.emu = (GenesisProvider) device;
        }
        if (device instanceof JoypadProvider) {
            this.joypad = (JoypadProvider) device;
        }
        if (device instanceof VdpProvider) {
            this.vdp = (VdpProvider) device;
        }
        if (device instanceof Z80Provider) {
            this.z80 = (Z80Provider) device;
        }
        if (device instanceof M68kProvider) {
            this.cpu = (M68kProvider) device;
        }
        if (device instanceof SoundProvider) {
            this.sound = (SoundProvider) device;
        }
        return this;
    }

    private void detectState() {
        boolean ok = Objects.nonNull(emu) && Objects.nonNull(cpu) && Objects.nonNull(joypad) && Objects.nonNull(vdp) &&
                Objects.nonNull(memory) && Objects.nonNull(z80) && Objects.nonNull(sound) && Objects.nonNull(cartridgeInfoProvider);
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
    public long readData(long address, Size size) {
        address = address & 0xFF_FFFF;
        if (address <= CartridgeInfoProvider.DEFAULT_ROM_END_ADDRESS) {  //ROM
            if (isSramUsedWithBrokenHeader(address)) { // Buck Rogers
                checkBackupMemoryMapper(SramMode.READ_WRITE);
                return mapper.readData(address, size);
            }
            return Util.readRom(memory, size, address);
        } else if (address > CartridgeInfoProvider.DEFAULT_ROM_END_ADDRESS && address < Z80_ADDRESS_SPACE_START) {  //Reserved
            LOG.warn("Read on reserved address: " + Integer.toHexString((int) address));
            return 0;
        } else if (address >= Z80_ADDRESS_SPACE_START && address <= Z80_ADDRESS_SPACE_END) {    //	Z80 addressing space
            return z80MemoryRead(address, size);
        } else if (address >= IO_ADDRESS_SPACE_START && address <= IO_ADDRESS_SPACE_END) {    //IO Addressing space
            return ioRead(address, size);
        } else if (address >= INTERNAL_REG_ADDRESS_SPACE_START && address <= INTERNAL_REG_ADDRESS_SPACE_END) {
            return internalRegRead(address, size);
        } else if (address >= VDP_ADDRESS_SPACE_START && address <= VDP_ADDRESS_SPACE_END) { // VDP
            return vdpRead(address, size);
        } else if (address >= ADDRESS_RAM_MAP_START && address <= ADDRESS_UPPER_LIMIT) {  //RAM (64K mirrored)
            return Util.readRam(memory, size, address);
        } else {
            LOG.warn("BUS READ NOT MAPPED: " + pad4(address) + " - " + pad4(cpu.getPC()));
        }
        return 0;
    }

    @Override
    public void writeData(long address, long data, Size size) {
        long addressL = (address & 0xFF_FFFF);
        if (size == Size.BYTE) {
            data = data & 0xFF;
        } else if (size == Size.WORD) {
            data = data & 0xFFFF;
        } else {
            data = data & 0xFFFF_FFFFL;
        }

        if (addressL <= CartridgeInfoProvider.DEFAULT_ROM_END_ADDRESS) {    //	Cartridge ROM/RAM
            if (isSramUsedWithBrokenHeader(addressL)) { // Buck Rogers
                LOG.info("Unexpected Sram write: " + Long.toHexString(addressL) + ", value : " + data);
                checkBackupMemoryMapper(SramMode.READ_WRITE);
                mapper.writeData(addressL, data, size);
                return;
            }
            LOG.error("Unexpected write to ROM: " + Long.toHexString(addressL) + ", value : " + data);
        } else if (addressL >= Z80_ADDRESS_SPACE_START && addressL <= Z80_ADDRESS_SPACE_END) {    //	Z80 addressing space
            z80MemoryWrite(address, size, data);
        } else if (addressL >= IO_ADDRESS_SPACE_START && addressL <= IO_ADDRESS_SPACE_END) {    //	IO addressing space
            ioWrite(addressL, size, data);
        } else if (addressL >= INTERNAL_REG_ADDRESS_SPACE_START && addressL <= INTERNAL_REG_ADDRESS_SPACE_END) {
            internalRegWrite(addressL, size, data);
        } else if (addressL >= VDP_ADDRESS_SPACE_START && addressL < VDP_ADDRESS_SPACE_END) {  //VDP
            vdpWrite(addressL, size, data);
        } else if (addressL >= ADDRESS_RAM_MAP_START && addressL <= ADDRESS_UPPER_LIMIT) {  //RAM (64K mirrored)
            long addressZ = addressL & 0xFFFF;
            Util.writeRam(memory, size, addressZ, data);
        } else {
            LOG.warn("WRITE NOT SUPPORTED ! " + Integer.toHexString((int) addressL) + " - PC: " + Integer.toHexString((int) cpu.getPC()));
        }
    }

    private void logVdpCounter(int v, int h) {
        if (Genesis.verbose) {
            LOG.info("Read HV counter, v=" + v + ", h=" + h);
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
    private long internalRegRead(long address, Size size) {
        int value = 0;
        if (address == 0xA11100 || address == 0xA11101 || address == 0xA11200 || address == 0xA11201) {    //	Z80 bus request
            value = z80.isBusRequested() ? 0 : 1;
            value = z80.isReset() ? 1 : value;
//            Bit 0 of A11100h (byte access) or bit 8 of A11100h (word access) controls
//            the Z80's /BUSREQ line.
            if (size == Size.WORD) {
                value = value << 8;
            }
            return value;
        } else if (address >= 0xA13000 && address <= 0xA130FF) {
            LOG.warn("Unexpected mapper read at: " + Long.toHexString(address));
        } else if (address == 0xA14100 || address == 0xA14101) {
            LOG.warn("TMSS read enable cart");
        } else {
            LOG.warn("Unexpected internalRegRead: " + address);
        }
        return value;
    }

    private void internalRegWrite(long addressL, Size size, long data) {
        if (addressL == 0xA11000 || addressL == 0xA11001) {    //	Memory mode register
//            Only D8 of address $A11OO0 is effective and for WRITE ONLY.
//            $A11OO0 D8 ( W)
//            O: ROM MODE
//            1: D-RAM MODE
            LOG.info("Setting memory mode to: " + data);
        } else if (addressL == 0xA11100 || addressL == 0xA11101) {    //	Z80 bus request
            //	To stop the Z80 and send a bus request, #$0100 must be written to $A11100.
            // Street Fighter 2 sends 0xFFFF
            if (data == 0x0100 || data == 0x1 || data == 0xFFFF) {
                boolean isReset = z80.isReset();
                if (!z80.isBusRequested()) {
                    LOG.debug("busRequested, reset: " + isReset);
                    z80.requestBus();
                } else {
                    LOG.debug("busRequested, ignored, reset: " + isReset);
                }
                //	 #$0000 needs to be written to $A11100 to return the bus back to the Z80
            } else if (data == 0x0000) {
                if (z80.isBusRequested()) {
                    z80.unrequestBus();
                    LOG.debug("busUnrequested, reset : " + z80.isReset());
                } else {
                    LOG.debug("busUnrequested ignored");
                }
            } else {
                LOG.warn("Unexpected data on busRequest: " + Integer.toBinaryString((int) data));
            }
        } else if (addressL == 0xA11200 || addressL == 0xA11201) {    //	Z80 bus reset
            //	if the Z80 is required to be reset (for example, to load a new program to it's memory)
            //	this may be done by writing #$0000 to $A11200, but only when the Z80 bus is requested
            if (data == 0x0000) {
                if (z80.isBusRequested()) {
                    z80.reset();
                    LOG.debug("Reset while busRequested");
                } else {
                    LOG.debug("Reset while busUnrequested, ignoring");
                }

                //	After returning the bus after loading the new program to it's memory,
                //	the Z80 may be let go from reset by writing #$0100 to $A11200.
            } else if (data == 0x0100 || data == 0x1) {
                z80.disableReset();
                LOG.debug("Disable reset, busReq : " + z80.isBusRequested());
            } else {
                LOG.warn("Unexpected data on busReset: " + Integer.toBinaryString((int) data));
            }

        } else if (addressL >= 0xA13000 && addressL <= 0xA130FF) {
            //	Sonic 3 will write to this register to enable and disable writing to its save game memory
            //$A130F1 (what you'd officially write to if you had a full blown mapper) has this format: ??????WE,
            //where W = allow writing and E = enable SRAM (it needs to be 00 to make SRAM hidden
            // and 11 to make SRAM writeable).
            //These games generally just look at the /TIME signal (the entire $A130xx range)
            // unless they feature a full-blown mapper).
            if (addressL >= Ssf2Mapper.BANK_SET_START_ADDRESS && addressL <= Ssf2Mapper.BANK_SET_END_ADDRESS) {
                LOG.info("Mapper bank set, address: " + Long.toHexString(addressL) + ", data: " + Integer.toHexString((int) data));
                checkSsf2Mapper();
                mapper.writeBankData(addressL, data);
            } else if (addressL == 0xA130F1) {
                boolean rom = (data & 3) % 2 == 0;
                //NOTE we dont support Ssf2Mapper and SRAM at the same time
                if (rom) { //enable ROM
                    checkSsf2Mapper();
                } else {
                    checkBackupMemoryMapper(SramMode.READ_WRITE);
                }
                LOG.info("Mapper register set: " + data);
            } else {
                LOG.warn("Unexpected mapper set, address: " + Long.toHexString(addressL) + ", data: " + Integer.toHexString((int) data));
            }
        } else if (addressL >= 0xA14000 || addressL <= 0xA14003) {
            //          used to lock/unlock the VDP by writing either "SEGA" to unlock it or anything else to lock it.
            LOG.warn("TMSS write, vdp lock: " + Integer.toHexString((int) data));
        } else if (addressL == 0xA14100 || addressL == 0xA14101) {    //	VDP TMSS
//            control the bankswitching between the cart and the TMSS rom.
//            Setting the first bit to 1 enable the cart, and setting it to 0 enable the TMSS.
            LOG.warn("TMSS write enable cart: " + (data == 1));
        } else {
            LOG.warn("Unexpected internalRegWrite: " + addressL + ", " + data);
        }
    }

    private long ioRead(long address, Size size) {
        long data = 0;
        if (address == 0xA10000 || address == 0xA10001) {    //	Version register (read-only word-long)
            data = emu.getRegionCode();
            if (size == Size.BYTE) {
                return data;
            } else {
                return data << 8 | data;
            }

        } else if (address == 0xA10002 || address == 0xA10003) {    //	Controller 1 data
            data = joypad.readDataRegister1();

        } else if (address == 0xA10004 || address == 0xA10005) {    //	Controller 2 data
            data = joypad.readDataRegister2();

        } else if (address == 0xA10006 || address == 0xA10007) {    //	Expansion data
            data = joypad.readDataRegister3();
        } else if (address == 0xA10008 || address == 0xA10009) {    //	Controller 1 control
            if (size == Size.BYTE) {
                data = joypad.readControlRegister1() & 0xFF;
            } else if (size == Size.WORD) {
                data = joypad.readControlRegister1();
            } else if (size == Size.LONG) { //Codemasters
                data = joypad.readControlRegister1();
                data = data << 16 | joypad.readControlRegister2();
            }
        } else if (address == 0xA1000A || address == 0xA1000B) {    //	Controller 2 control
            if (size == Size.BYTE) {
                data = joypad.readControlRegister2() & 0xFF;
            } else {
                data = joypad.readControlRegister2();
            }
        } else if (address == 0xA1000C || address == 0xA1000D) {    //	Expansion Port Control
            if (size == Size.BYTE) {
                data = joypad.readControlRegister3() & 0xFF;
            } else {
                data = joypad.readControlRegister3();
            }
        } else if (address == 0xA10013 || address == 0xA10019 || address == 0xA1001F) {
            LOG.info("Reading serial control, " + pad4(address));
            data = 0;
        } else {
            LOG.warn("Unexpected ioRead: " + address);
        }
        return data;
    }

    private void ioWrite(long addressL, Size size, long data) {
        if (size != Size.BYTE) {
            LOG.warn("Word wide write: " + addressL + ", " + data);
        }
        if (addressL == 0xA10002 || addressL == 0xA10003) {    //	Controller 1 data
            joypad.writeDataRegister1(data);
        } else if (addressL == 0xA10004 || addressL == 0xA10005) {    //	Controller 2 data
            joypad.writeDataRegister2(data);
        } else if (addressL == 0xA10006 || addressL == 0xA10007) {    //	Expansion port data
            LOG.warn("Expansion port data");
        } else if (addressL == 0xA10008 || addressL == 0xA10009) {    //	Controller 1 control
            joypad.writeControlRegister1(data & 0xFF);
        } else if (addressL == 0xA1000A || addressL == 0xA1000B) {    //	Controller 2 control
            joypad.writeControlRegister2(data & 0xFF);
        } else if (addressL == 0xA1000C || addressL == 0xA1000D) {    //	Controller 2 control
            joypad.writeControlRegister3(data & 0xFF);
        } else if (addressL == 0xA10012 || addressL == 0xA10013) {    //	Controller 1 serial control
            LOG.warn("IMPL CONTR 1 !!");
        } else if (addressL == 0xA10018 || addressL == 0xA10019) {    //	Controller 2 serial control
            LOG.warn("IMPL CONTR 2 !!");
        } else if (addressL == 0xA1001E || addressL == 0xA1001F) {    //	Expansion port serial control
            LOG.warn("expansion port serial control !!");
        } else {
            LOG.warn("Unexpected ioWrite: " + addressL + ", " + data);
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
        long data;
        if (!z80.isBusRequested() || z80.isReset()) {
            LOG.warn("Reading Z80 memory without busreq");
            return 0;
        }
        int addressZ = (int) (address & 0xA07FFF) - Z80_ADDRESS_SPACE_START;
        data = z80.readMemory(addressZ);
        if (size == Size.BYTE) {
            return data;
        } else {
            //    A word-wide read from Z80 RAM has the LSB of the data duplicated in the MSB
            LOG.info("Word-wide read from Z80 ram");
            return data << 8 | data;
        }
    }

    private void z80MemoryWrite(long address, Size size, long data) {
        if (!z80.isBusRequested() || z80.isReset()) {
            LOG.warn("Writing Z80 memory when bus not requested or Z80 reset");
            return;
        }
        int addressZ = (int) (address & 0xA07FFF) - Z80_ADDRESS_SPACE_START;
        Util.writeZ80(z80, size, addressZ, data);
    }

    //    Byte-wide reads
//
//    Reading from even VDP addresses returns the MSB of the 16-bit data,
//    and reading from odd address returns the LSB:
    private long vdpRead(long address, Size size) {
        long data = 0;
        if (address >= 0xC00000 && address <= 0xC00007) {
            boolean even = address % 2 == 0;
            boolean isVdpData = address <= 0xC00003;
            //read word by default
            data = isVdpData ? vdp.readDataPort() : vdp.readControl();
            if (size == Size.BYTE) {
                data = even ? data >> 8 : data & 0xFF;
            } else if (size == Size.LONG) {
                data = data << 16;
                long data2 = isVdpData ? vdp.readDataPort() : vdp.readControl();
                data |= data2;
            }
        } else if (address >= 0xC00008 && address <= 0xC0000E) { //	VDP HV counter
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
            int v = vdp.getVCounter();
            int h = vdp.getHCounter();
            logVdpCounter(v, h);
            if (size == Size.WORD) {
                return (v << 8) | h;
            } else if (size == Size.BYTE) {
                boolean even = address % 2 == 0;
                return even ? v : h;
            }
        } else if (address == 0xC0001C) {
            LOG.warn("Ignoring VDP debug register write, address : " + pad4(address));
        } else {
            LOG.warn("Unexpected vdpRead, address: " + Long.toHexString(address));
        }
        return data;
    }

    //      Doing an 8-bit write to the control or data port is interpreted by
//                the VDP as a 16-bit word, with the data written used for both halfs
//                of the word.
    private void vdpWrite(long addressL, Size size, long data) {
        addressL = addressL & 0x1F; //low 5 bits
        if (addressL < 0x4) {    //DATA PORT
            if (size == Size.BYTE) {
                data = data << 8 | data;
                vdp.writeDataPort(data);
            } else if (size == Size.WORD) {
                vdp.writeDataPort(data);
            } else {
                vdp.writeDataPort(data >> 16);
                vdp.writeDataPort(data & 0xFFFF);
            }
        } else if (addressL >= 0x4 && addressL < 0x8) {    //CONTROL PORT
            if (size == Size.BYTE) {
                data = data << 8 | data;
                vdp.writeControlPort(data);
            } else if (size == Size.WORD) {
                vdp.writeControlPort(data);
            } else {
                vdp.writeControlPort(data >> 16);
                vdp.writeControlPort(data & 0xFFFF);
            }
        } else if (addressL >= 0x8 && addressL < 0x0F) {   //HV Counter
            LOG.warn("HV counter write");
        }
        //            Doing byte-wide writes to even PSG addresses has no effect.
//
//            If you want to write to the PSG via word-wide writes, the data
//            must be in the LSB. For instance:
//
//            move.b      (a4)+, d0       ; PSG data in LSB
//            move.w      d0, $C00010     ; Write to PSG
        else if (addressL >= 0x10 && addressL < 0x18) {
            int psgData = (int) (data & 0xFF);
            if (size == Size.WORD) {
                LOG.warn("PSG word write, address: " + Long.toHexString(addressL) + ", data: " + data);
            }
            sound.getPsg().write(psgData);
        } else {
            LOG.warn("Unexpected vdpWrite, address: " + Long.toHexString(addressL) + ", data: " + data);
        }
    }

    private static boolean isSramUsedWithBrokenHeader(long address) {
        boolean noOverlapBetweenRomAndSram =
                CartridgeInfoProvider.DEFAULT_SRAM_START_ADDRESS > GenesisBus.ROM_END_ADDRESS;
        return noOverlapBetweenRomAndSram &&
                (address >= CartridgeInfoProvider.DEFAULT_SRAM_START_ADDRESS &&
                        address <= CartridgeInfoProvider.DEFAULT_SRAM_END_ADDRESS);
    }

    @Override
    public GenesisProvider getEmulator() {
        return emu;
    }

    private void checkSsf2Mapper() {
        this.mapper = Ssf2Mapper.getOrCreateInstance(this, mapper, memory);
    }

    private void checkBackupMemoryMapper(SramMode sramMode) {
        this.mapper = BackupMemoryMapper.getOrCreateInstance(this, mapper, cartridgeInfoProvider, sramMode);
    }

    @Override
    public MemoryProvider getMemory() {
        return memory;
    }

    @Override
    public VdpProvider getVdp() {
        return vdp;
    }

    @Override
    public JoypadProvider getJoypad() {
        return joypad;
    }

    @Override
    public SoundProvider getSound() {
        return sound;
    }

    public final String pad4(long reg) {
        String s = Long.toHexString(reg).toUpperCase();
        while (s.length() < 4) {
            s = "0" + s;
        }
        return s;
    }

    private void raiseInterrupts() {
        int level = isVdpHInt() ? M68kProvider.HBLANK_INTERRUPT_LEVEL : 0;
        //VINT has priority
        level = isVdpVInt() ? M68kProvider.VBLANK_INTERRUPT_LEVEL : level;
        if (level > 0) {
            logInfo("68k raise interrupt: {}", level);
            cpu.raiseInterrupt(level);
        }
    }

    private void raiseInterruptsZ80() {
        logInfo("Z80 raise interrupt");
        z80.interrupt();
    }

    private void ackInterrupts() {
        if (isVdpVInt()) {
            vdp.setVip(false);
            logInfo("Ack VDP VINT");
        } else if (isVdpHInt()) {
            vdp.setHip(false);
            logInfo("Ack VDP HINT");
        }
    }

    private boolean isVdpVInt() {
        return vdp.getVip() && vdp.isIe0();
    }

    private boolean isVdpHInt() {
        return vdp.getHip() && vdp.isIe1();
    }

    private boolean checkInterrupts() {
        return isVdpVInt() || isVdpHInt();
    }

    private boolean shouldRaiseZ80 = false;

    /* Cycle-accurate VINT flag (Ex-Mutants, Tyrant / Mega-Lo-Mania, Marvel Land) */
    /* this allows VINT flag to be read just before vertical interrupt is being triggered */
    @Override
    public boolean handleVdpInterrupts() {
        boolean vdpInt = checkInterrupts();
        if (!vdpInt) {
            vdpIntState = BusProvider.VdpIntState.NONE;
            return true;
        }
//        verbose = true;
        switch (vdpIntState) {
            case NONE:
                vdpIntState = BusProvider.VdpIntState.PROCESS_INT;
                shouldRaiseZ80 = isVdpVInt();
                logInfo("Z80 interrupt detected: {}", shouldRaiseZ80);
                break;
            case PROCESS_INT:
                raiseInterrupts();
                vdpIntState = VdpIntState.ACK_INT;
                break;
            case ACK_INT:
                ackInterrupts();
                vdpIntState = BusProvider.VdpIntState.INT_DONE;
                break;
            case INT_DONE:
                //Lotus II
                vdpIntState = BusProvider.VdpIntState.NONE;
                break;
            default:
                LOG.error("Unexpected state while handling vdp interrupts");
                break;
        }
//        verbose = false;
        logInfo("VDP interrupt state: {}", vdpIntState);
        return true;

    }

    @Override
    public boolean handleVdpInterruptsZ80() {
        //NOTE: if z80 is not running, the interrupt will be lost
        if (shouldRaiseZ80) {
            raiseInterruptsZ80();
            shouldRaiseZ80 = false;
        }
        return true;
    }

    private static void logInfo(String str, Object... args) {
        if (verbose) {
            Util.printLevel(LOG, Level.INFO, str, args);
        }
    }

    public static void main(String[] args) {
        long address = -2;
        address = address & 0xFF_FFFF;
        System.out.println(Long.toHexString(address));
    }
}
