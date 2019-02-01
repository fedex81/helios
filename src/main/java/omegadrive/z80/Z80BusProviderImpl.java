package omegadrive.z80;

import omegadrive.bus.BusProvider;
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
public class Z80BusProviderImpl implements Z80BusProvider {
    private static Logger LOG = LogManager.getLogger(Z80BusProviderImpl.class.getSimpleName());

//    public static int MEMORY_SIZE = 0x2000;

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

    private BusProvider busProvider;
    private Z80Provider z80Provider;
    private IMemory z80Memory;

    public Z80BusProviderImpl(BusProvider busProvider, Z80Provider z80Provider, IMemory z80Memory) {
        this.busProvider = busProvider;
        this.z80Provider = z80Provider;
        this.z80Memory = z80Memory;
    }

    @Override
    public int readMemory(int address) {
        if (address < IMemory.MEMORY_SIZE) { //RAM
            return z80Memory.readByte(address);
        } else if (address >= IMemory.MEMORY_SIZE && address < IMemory.MEMORY_SIZE * 2) { //RAM Mirror
            return z80Memory.readByte(address - IMemory.MEMORY_SIZE);
        } else if (address >= START_YM2612 && address <= END_YM2612) { //YM2612
            if (z80Provider.isReset()) {
                LOG.warn("FM read while Z80 reset");
                return 1;
            }
            return busProvider.getFm().read();
        } else if (address >= ROM_BANK_ADDRESS && address <= 0x7EFF) {        //	BankSwitching and Reserved
            LOG.error("Z80 read bank switching: " + Integer.toHexString(address));
            return 0xFF;
        } else if (address >= START_VDP && address <= END_VDP) { //read VDP
            int vdpAddress = VDP_BASE_ADDRESS + address;
            LOG.warn("Z80 read VDP memory , address : " + address);
            return (int) busProvider.read(vdpAddress, Size.BYTE);
        } else if (address >= START_68K_BANK && address <= END_68K_BANK) { //M68k memory bank
            if (romBankPointer % ROM_BANK_POINTER_SIZE != 0) {
                LOG.info("Reading 68k memory, but pointer: " + romBankPointer);
            }
            address = address - START_68K_BANK + (romBank68kSerial << 15);
            //this seems to be not allowed
            if (address >= BusProvider.ADDRESS_RAM_MAP_START && address < BusProvider.ADDRESS_UPPER_LIMIT) {
                LOG.warn("Z80 reading from 68k RAM");
                return 0xFF;
            }
            return (int) busProvider.read(address, Size.BYTE);
        } else {
            LOG.error("Illegal Z80 memory read: " + Integer.toHexString(address));
        }
        return 0;
    }

    @Override
    public void writeMemory(int address, int dataInt) {
        if (address < IMemory.MEMORY_SIZE) {  //RAM
            z80Memory.writeByte(address, dataInt);
        } else if (address >= IMemory.MEMORY_SIZE && address < IMemory.MEMORY_SIZE * 2) { //RAM MIRROR
            z80Memory.writeByte(address - IMemory.MEMORY_SIZE, dataInt);
        } else if (address >= START_YM2612 && address < END_YM2612) { //YM2612
            //LOG.info("Writing " + Integer.toHexString(address) + " data: " + data);
            if (z80Provider.isReset()) {
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
            busProvider.write(vdpAddress, dataInt, Size.BYTE);
        } else if (address >= 0x7F20 && address <= END_VDP) {        //	VDP Illegal
            LOG.warn("Illegal Write to VDP: " + Integer.toHexString(address));
        } else if (address >= START_68K_BANK && address <= END_68K_BANK) {
            address = address - START_68K_BANK + (romBank68kSerial << 15);
            //Z80 write to 68k RAM - this seems to be allowed
            if (address >= BusProvider.ADDRESS_RAM_MAP_START && address < BusProvider.ADDRESS_UPPER_LIMIT) {
                LOG.info("Z80 writing to 68k RAM");
            }
            busProvider.write(address, dataInt, Size.BYTE);
        } else {
            LOG.error("Illegal Z80 memory write:  " + Integer.toHexString(address) + ", " + dataInt);
        }
    }

    private void writeFm(int address, int data) {
        busProvider.getFm().write(address, data);
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
