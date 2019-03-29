package omegadrive.bus.gen;

import omegadrive.Device;
import omegadrive.bus.BaseBusProvider;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.memory.IMemoryRam;
import omegadrive.util.Size;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Z80BusProviderImpl
 * <p>
 * 0000-1FFFh : RAM
 * 2000-3FFFh : RAM (mirror)
 * 4000-5FFFh : YM2612 (1)
 * 6000-60FFh : Bank address register (2)
 * 6100-7EFFh : Unused (3)
 * 7F00-7FFFh : VDP (4)
 * 8000-FFFFh : Bank area
 *
 * @author Federico Berti
 */
public class GenesisZ80BusProviderImpl extends DeviceAwareBus implements GenesisZ80BusProvider {
    private static Logger LOG = LogManager.getLogger(GenesisZ80BusProviderImpl.class.getSimpleName());

//    public static int MEMORY_SIZE = 0x2000;

    public static final int END_RAM = 0x3FFF;
    public static final int START_YM2612 = 0x4000;
    public static final int END_YM2612 = 0x5FFF;
    public static final int ROM_BANK_ADDRESS = 0x6000;
    public static final int START_VDP = 0x7F00;
    public static final int END_VDP = 0x7FFF;
    public static final int START_68K_BANK = 0x8000;
    public static final int END_68K_BANK = 0xFFFF;

    public static final int VDP_BASE_ADDRESS = 0xC00000;

    private static final int ROM_BANK_POINTER_SIZE = 9;

    //    To specify which 32k section you want to access, write the upper nine
    //    bits of the complete 24-bit address into bit 0 of the bank address
    //    register, which is at 6000h (Z80) or A06000h (68000), starting with
    //    bit 15 and ending with bit 23.
    private int romBank68kSerial;
    private int romBankPointer;

    private GenesisBusProvider mainBusProvider;
    private IMemoryRam z80Memory;
    private int[] ram;
    private int ramSize;

    @Override
    public BaseBusProvider attachDevice(Device device) {
        if (device instanceof GenesisBusProvider) {
            this.mainBusProvider = (GenesisBusProvider) device;
        }
        if (device instanceof IMemoryRam) {
            this.z80Memory = (IMemoryRam) device;
            this.ram = z80Memory.getRamData();
            this.ramSize = ram.length;
        }
        super.attachDevice(device);
        return this;
    }

    @Override
    public long read(long addressL, Size size) {
        int address = (int) addressL;
        if (address < END_RAM) { //RAM
            address &= (ram.length - 1);
            return ram[address];
        } else if (address >= START_YM2612 && address <= END_YM2612) { //YM2612
            if (mainBusProvider.isZ80ResetState()) {
                LOG.warn("FM read while Z80 reset");
                return 1;
            }
            return mainBusProvider.getFm().read();
        } else if (address >= ROM_BANK_ADDRESS && address <= 0x7EFF) {        //	BankSwitching and Reserved
            LOG.error("Z80 read bank switching: " + Integer.toHexString(address));
            return 0xFF;
        } else if (address >= START_VDP && address <= END_VDP) { //read VDP
            int vdpAddress = (int) (VDP_BASE_ADDRESS + address);
            LOG.warn("Z80 read VDP memory , address : " + address);
            return (int) mainBusProvider.read(vdpAddress, Size.BYTE);
        } else if (address >= START_68K_BANK && address <= END_68K_BANK) { //M68k memory bank
            if (romBankPointer % ROM_BANK_POINTER_SIZE != 0) {
                LOG.info("Reading 68k memory, but pointer: " + romBankPointer);
            }
            address = address - START_68K_BANK + (romBank68kSerial << 15);
            //this seems to be not allowed
            if (address >= GenesisBusProvider.ADDRESS_RAM_MAP_START && address < GenesisBusProvider.ADDRESS_UPPER_LIMIT) {
                LOG.warn("Z80 reading from 68k RAM");
                return 0xFF;
            }
            return (int) mainBusProvider.read(address, Size.BYTE);
        } else {
            LOG.error("Illegal Z80 memory read: " + Integer.toHexString(address));
        }
        return 0;
    }

    @Override
    public void write(long addressL, long data, Size size) {
        int dataInt = (int) data;
        int address = (int) addressL;
        if (address < END_RAM) {  //RAM
            address &= (ram.length - 1);
            ram[address] = dataInt;
        } else if (address >= START_YM2612 && address < END_YM2612) { //YM2612
            //LOG.info("Writing " + Integer.toHexString(address) + " data: " + data);
            if (mainBusProvider.isZ80ResetState()) {
                LOG.warn("Illegal write to FM while Z80 reset");
                return;
            }
            writeFm(address, dataInt);
        } else if (address >= ROM_BANK_ADDRESS && address <= 0x60FF) {        //	rom banking
            if (address == ROM_BANK_ADDRESS) {
                romBanking(dataInt);
            } else {
                LOG.warn("Unexpected bank write: " + Integer.toHexString(address) + ", data: " + dataInt);
            }
        } else if (address >= 0x6100 && address <= 0x7EFF) {        //	unused
            LOG.warn("Write to unused memory: " + Integer.toHexString(address));
        } else if (address >= START_VDP && address <= 0x7F1F) {        //	VDP (SN76489 PSG)
            int vdpAddress = VDP_BASE_ADDRESS + address;
            mainBusProvider.write(vdpAddress, dataInt, Size.BYTE);
        } else if (address >= 0x7F20 && address <= END_VDP) {        //	VDP Illegal
            LOG.warn("Illegal Write to VDP: " + Integer.toHexString(address));
        } else if (address >= START_68K_BANK && address <= END_68K_BANK) {
            address = address - START_68K_BANK + (romBank68kSerial << 15);
            //Z80 write to 68k RAM - this seems to be allowed
            if (address >= GenesisBusProvider.ADDRESS_RAM_MAP_START && address < GenesisBusProvider.ADDRESS_UPPER_LIMIT) {
                LOG.info("Z80 writing to 68k RAM");
            }
            mainBusProvider.write(address, dataInt, Size.BYTE);
        } else {
            LOG.error("Illegal Z80 memory write:  " + Integer.toHexString(address) + ", " + dataInt);
        }
    }

    @Override
    public void reset() {
        super.reset();
        romBank68kSerial = 0;
        romBankPointer = 0;
    }

    @Override
    public void closeRom() {

    }

    @Override
    public void newFrame() {

    }

    private void writeFm(int address, int data) {
        mainBusProvider.getFm().write(address, data);
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
        if (romBankPointer == ROM_BANK_POINTER_SIZE) {
            romBank68kSerial = 0;
            romBankPointer = 0;
        }
        romBank68kSerial = ((data & 1) << romBankPointer) | romBank68kSerial;
        romBankPointer++;
    }

    public void setRomBank68kSerial(int romBank68kSerial) {
        this.romBank68kSerial = romBank68kSerial;
    }

    public int getRomBank68kSerial() {
        return romBank68kSerial;
    }
}
