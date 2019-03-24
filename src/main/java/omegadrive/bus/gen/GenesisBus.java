package omegadrive.bus.gen;

import omegadrive.Genesis;
import omegadrive.SystemProvider;
import omegadrive.bus.mapper.BackupMemoryMapper;
import omegadrive.bus.mapper.GenesisMapper;
import omegadrive.bus.mapper.Ssf2Mapper;
import omegadrive.joypad.JoypadProvider;
import omegadrive.m68k.M68kProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.util.CartridgeInfoProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

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
 * TODO
 * Interrupts are know acknowleged based on what the VDP thinks its asserting rather than what the 68K actually is acking - Fixes Fatal Rewind
 *
 */
public class GenesisBus implements GenesisBusProvider, GenesisMapper {

    private static Logger LOG = LogManager.getLogger(GenesisBus.class.getSimpleName());

    public static boolean verbose = false || Genesis.verbose;

    private SystemProvider emu;
    private IMemoryProvider memory;
    private JoypadProvider joypad;

    private GenesisVdpProvider vdp;
    private Z80Provider z80;
    private M68kProvider cpu;
    private SoundProvider sound;

    private CartridgeInfoProvider cartridgeInfoProvider;
    private GenesisMapper mapper;

    private BusArbiter busArbiter;

    public static long ROM_START_ADDRESS;
    public static long ROM_END_ADDRESS;
    public static long RAM_START_ADDRESS;
    public static long RAM_END_ADDRESS;

    enum BusState {READY, NOT_READY}

    private BusState busState = BusState.NOT_READY;

    //TEST ONLY
    protected GenesisBus() {
        this.mapper = this;
        this.busArbiter = BusArbiter.createInstance(vdp, cpu, z80);
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
        this.cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memory, getEmulator().getRomName());
        initializeRomData();
        LOG.info(cartridgeInfoProvider.toString());
        this.busArbiter = BusArbiter.createInstance(vdp, cpu, z80);
        detectState();
        LOG.info("Bus state: " + busState);
    }

    @Override
    public void resetFrom68k() {
        this.getFm().reset();
        this.z80.reset();
    }

    @Override
    public boolean shouldStop68k() {
        return busArbiter.shouldStop68k();
    }

    @Override
    public void setStop68k(int mask) {
        busArbiter.setStop68k(mask);
    }

    @Override
    public GenesisBusProvider attachDevice(Object device) {
        if (device instanceof IMemoryProvider) {
            this.memory = (IMemoryProvider) device;
        }
        if (device instanceof SystemProvider) {
            this.emu = (SystemProvider) device;
        }
        if (device instanceof JoypadProvider) {
            this.joypad = (JoypadProvider) device;
        }
        if (device instanceof GenesisVdpProvider) {
            this.vdp = (GenesisVdpProvider) device;
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
        if (device instanceof BusArbiter) {
            this.busArbiter = (BusArbiter) device;
        }
        return this;
    }

    private void detectState() {
        boolean ok = Objects.nonNull(emu) && Objects.nonNull(cpu) && Objects.nonNull(joypad) && Objects.nonNull(vdp) &&
                Objects.nonNull(memory) && Objects.nonNull(z80) && Objects.nonNull(sound) && Objects.nonNull(cartridgeInfoProvider)
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
    public long readData(long address, Size size) {
        address = address & 0xFF_FFFF;
        if (address <= CartridgeInfoProvider.DEFAULT_ROM_END_ADDRESS) {  //ROM
//            if(address <= cartridgeInfoProvider.getRomEnd()){ TODO Umk trilogy
            if (isSramUsedWithBrokenHeader(address)) { // Buck Rogers
                checkBackupMemoryMapper(SramMode.READ_WRITE);
                return mapper.readData(address, size);
            }
            return Util.readRom(memory, size, (int) address);
        } else if (address > CartridgeInfoProvider.DEFAULT_ROM_END_ADDRESS && address < Z80_ADDRESS_SPACE_START) {  //Reserved
            LOG.warn("Read on reserved address: " + Integer.toHexString((int) address));
            return size.getMax();
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
            LOG.warn("BUS READ NOT MAPPED: address: {}, PC: {}", Util.pad4(address), Util.pad4(cpu.getPC()));
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
                boolean adjust = adjustSramLimits(addressL);
                checkBackupMemoryMapper(SramMode.READ_WRITE, adjust);
                mapper.writeData(addressL, data, size);
                return;
            }
            //Batman&Robin writes to address 0 - tries to enable debug mode?
            String msg = "Unexpected write to ROM: " + Long.toHexString(addressL) + ", value : " + data;
            LOG.warn(msg);
            //TODO Micro Machines, Fantastic Dizzy write to ROM
//            if (addressL > 0) {
//                throw new IllegalArgumentException(msg);
//            }
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
            Util.writeRam(memory, size, addressZ, (int) data);
        } else {
            LOG.warn("WRITE NOT SUPPORTED ! " + Integer.toHexString((int) addressL) + " - PC: " + Integer.toHexString((int) cpu.getPC()));
        }
    }

    private boolean adjustSramLimits(long address) {
        //FIFA 96
        boolean adjust = cartridgeInfoProvider.getSramEnd() < CartridgeInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        adjust &= address > cartridgeInfoProvider.getSramEnd() && address < CartridgeInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        if (adjust) {
            LOG.warn("Adjusting SRAM limit from: {} to: {}", Long.toHexString(cartridgeInfoProvider.getSramEnd()),
                    Long.toHexString(CartridgeInfoProvider.DEFAULT_SRAM_END_ADDRESS));
            cartridgeInfoProvider.setSramEnd(CartridgeInfoProvider.DEFAULT_SRAM_END_ADDRESS);
        }
        return adjust;
    }

    private void logVdpCounter(int v, int h) {
        if (Genesis.verbose) {
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
    private long internalRegRead(long address, Size size) {
        int value = 0;
        if (address == 0xA11100 || address == 0xA11101
            //TODO this shouldn't be here???
//               || address == 0xA11200 || address == 0xA11201
                ) {    //	Z80 bus request
            value = z80.isBusRequested() ? 0 : 1;
            value = z80.isReset() ? 1 : value;
//            Bit 0 of A11100h (byte access) or bit 8 of A11100h (word access) controls
//            the Z80's /BUSREQ line.
            if (size == Size.WORD) {
                value = value << 8;
            }
            LOG.debug("Read Z80 busReq: {}", value);
            return value;
        } else if (address == 0xA11200 || address == 0xA11201) {
            LOG.warn("Unexpected Z80 read at: " + Long.toHexString(address));
        } else if (address >= 0xA13000 && address <= 0xA130FF) {
            //NOTE genTest does: cmpi.l #'MARS',$A130EC  ;32X
            LOG.warn("Unexpected /TIME or mapper read at: " + Long.toHexString(address));
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
            if (size == Size.WORD) {
                // Street Fighter 2 sends 0xFFFF, Monster World 0xFEFF
                data = data & 0x0100;
            }
            if (data == 0x0100 || data == 0x1) {
                boolean isReset = z80.isReset();
                if (!z80.isBusRequested()) {
                    LOG.debug("busRequested, reset: {}", isReset);
                    z80.requestBus();
                } else {
                    LOG.debug("busRequested, ignored, reset: {}", isReset);
                }
                //	 #$0000 needs to be written to $A11100 to return the bus back to the Z80
            } else if (data == 0x0000) {
                if (z80.isBusRequested()) {
                    z80.unrequestBus();
                    LOG.debug("busUnrequested, reset : {}", z80.isReset());
                } else {
                    LOG.debug("busUnrequested ignored");
                }
            } else {
                LOG.warn("Unexpected data on busRequest, address: {} , {}",
                        Long.toHexString(addressL), Integer.toBinaryString((int) data));
            }
        } else if (addressL == 0xA11200 || addressL == 0xA11201) {    //	Z80 bus reset
            //	if the Z80 is required to be reset (for example, to load a new program to it's memory)
            //	this may be done by writing #$0000 to $A11200, but only when the Z80 bus is requested
            if (data == 0x0000) {
                if (z80.isBusRequested()) {
                    z80.reset();
                    getFm().reset();
                    LOG.debug("Reset while busRequested");
                } else {
                    LOG.debug("Reset while busUnrequested, ignoring");
                }

                //	After returning the bus after loading the new program to it's memory,
                //	the Z80 may be let go from reset by writing #$0100 to $A11200.
            } else if (data == 0x0100 || data == 0x1) {
                z80.disableReset();
                getFm().reset();
                LOG.debug("Disable reset, busReq : {}", z80.isBusRequested());
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
        if ((address & 0xFFF) <= 1) {    //	Version register (read-only word-long)
            data = emu.getRegionCode();
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
                data = joypad.readDataRegister1();
                break;
            case 4:
                data = joypad.readDataRegister2();
                break;
            case 6:
                data = joypad.readDataRegister3();
                break;
            case 8:
                data = joypad.readControlRegister1();
                break;
            case 0xA:
                data = joypad.readControlRegister2();
                break;
            case 0xC:
                data = joypad.readControlRegister3();
                break;
            case 0x12:
            case 0x18:
            case 0x1E:
                int scNumber = address == 0x12 ? 1 : ((address == 0x18) ? 2 : 3);
                LOG.info("Reading serial control{}, {}", scNumber, Util.pad4(address));
                break;
            default:
                LOG.warn("Unexpected ioRead: {}", Long.toHexString(addressL));
                break;
        }
        return data;
    }

    private void ioWrite(long addressL, Size size, long data) {
        if (size != Size.BYTE && data > 0xFF) {
            LOG.error("Unexpected sized write: {}, {}, {}", size, Long.toHexString(addressL), Long.toHexString(data));
        }
        //both even and odd addresses
        int address = (int) (addressL & 0xFFE);
        switch (address) {
            case 2:
                joypad.writeDataRegister1(data);
                break;
            case 4:
                joypad.writeDataRegister2(data);
                break;
            case 6:
                LOG.warn("Write to expansion port: " + Long.toHexString(address) +
                        ", data: " + Long.toHexString(data) + ", size: " + size);
                break;
            case 8:
                joypad.writeControlRegister1(data & 0xFF);
                break;
            case 0xA:
                joypad.writeControlRegister2(data & 0xFF);
                break;
            case 0xC:
                joypad.writeControlRegister3(data & 0xFF);
                break;
            case 0x12:
            case 0x18:
            case 0x1E:
                int scNumber = address == 0x12 ? 1 : ((address == 0x18) ? 2 : 3);
                LOG.warn("Write to controller {} serial: {}, data: {}",
                        scNumber, Long.toHexString(addressL), Long.toHexString(data));
                break;
            default:
                LOG.warn("Unexpected ioWrite: " + Long.toHexString(address) + ", " + data);
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
        long data;
        if (!z80.isBusRequested() || z80.isReset()) {
            LOG.warn("Reading Z80 memory without busreq");
            return 0;
        }
        int addressZ = (int) (address & GenesisBusProvider.M68K_TO_Z80_MEMORY_MASK);
        data = z80.readMemory(addressZ);
        if (size == Size.BYTE) {
            return data;
        } else {
            //    A word-wide read from Z80 RAM has the LSB of the data duplicated in the MSB
            LOG.info("Word-wide read from Z80 ram");
            return data << 8 | data;
        }
    }

    private void z80MemoryWrite(long address, Size size, long dataL) {
        if (!z80.isBusRequested() || z80.isReset()) {
            LOG.warn("Writing Z80 memory when bus not requested or Z80 reset");
            return;
        }
        //	https://emu-docs.org/Genesis/gen-hw.txt
        //	When doing word-wide writes to Z80 RAM, only the MSB is written, and the LSB is ignored
        int data = (int) dataL;
        if (size == Size.WORD) {
            data = data >> 8;
        }
        if (size == Size.LONG) {
            LOG.error("Unexpected long write, addr: {}, data: {}", address, dataL);
        }
        int addressZ = (int) (address & GenesisBusProvider.M68K_TO_Z80_MEMORY_MASK);
        z80.writeMemory(addressZ, data);
    }

    //    Byte-wide reads
//
//    Reading from even VDP addresses returns the MSB of the 16-bit data,
//    and reading from odd address returns the LSB:
    private long vdpRead(long addressL, Size size) {
        long data = 0;
        long address = addressL & 0x1F; //low 5 bits
        if (address <= 0x07) {
            boolean even = address % 2 == 0;
            boolean isVdpData = address <= 0x03;
            //read word by default
            data = isVdpData ? vdp.readDataPort() : vdp.readControl();
            if (size == Size.BYTE) {
                data = even ? data >> 8 : data & 0xFF;
            } else if (size == Size.LONG) {
                data = data << 16;
                long data2 = isVdpData ? vdp.readDataPort() : vdp.readControl();
                data |= data2;
            }
        } else if (address >= 0x08 && address <= 0x0E) { //	VDP HV counter
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
        } else if (address == 0x1C) {
            LOG.warn("Ignoring VDP debug register write, address : {}", Util.pad4(addressL));
        } else if (address > 0x17) {
            LOG.info("vdpRead on unused address: " + Long.toHexString(addressL));
//            return 0xFF;
        } else {
            LOG.warn("Unexpected vdpRead, address: " + Long.toHexString(addressL));
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
    public SystemProvider getEmulator() {
        return emu;
    }

    private void checkSsf2Mapper() {
        this.mapper = Ssf2Mapper.getOrCreateInstance(this, mapper, memory);
    }

    private void checkBackupMemoryMapper(SramMode sramMode) {
        checkBackupMemoryMapper(sramMode, false);
    }

    private void checkBackupMemoryMapper(SramMode sramMode, boolean forceCreate) {
        this.mapper = forceCreate ? BackupMemoryMapper.createInstance(this, cartridgeInfoProvider) :
                BackupMemoryMapper.getOrCreateInstance(this, mapper, cartridgeInfoProvider, sramMode);
    }

    @Override
    public IMemoryProvider getMemory() {
        return memory;
    }

    @Override
    public GenesisVdpProvider getVdp() {
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

    @Override
    public void handleVdpInterrupts68k() {
        busArbiter.handleInterrupts68k();
    }

    @Override
    public void handleVdpInterruptsZ80() {
        busArbiter.handleInterruptZ80();
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
    }

    @Override
    public void newFrame() {
        busArbiter.newFrame();
        joypad.newFrame();
    }
}
