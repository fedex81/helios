package omegadrive.z80;

import omegadrive.bus.BusProvider;
import omegadrive.util.Size;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;

/**
 * Z80Memory
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
public class Z80Memory implements IMemory {
    private static Logger LOG = LogManager.getLogger(Z80Memory.class.getSimpleName());

    public static final int MEMORY_SIZE = 0x2000;

    public static final int START_YM2612 = 0x4000;
    public static final int END_YM2612 = 0x5FFF;
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

    private int YMA0;
    private int YMD0;
    private int YMA1;
    private int YMD1;

    private final int[] memory = new int[MEMORY_SIZE];
    private BusProvider busProvider;
    private Z80Provider z80Provider;

    public Z80Memory(BusProvider busProvider, Z80Provider z80Provider) {
        this.busProvider = busProvider;
        this.z80Provider = z80Provider;
        // Set all to HALT - stops runaway code
        for (int a = 0; a < memory.length; a++) {
            memory[a] = 0x76;
        }
    }


    private int readMemory(int address) {
        if (address < MEMORY_SIZE) { //RAM
            return memory[address];
        } else if (address >= MEMORY_SIZE && address < MEMORY_SIZE * 2) { //RAM Mirror
            address = address - MEMORY_SIZE;
            return memory[address];
        } else if (address >= START_YM2612 && address <= END_YM2612) { //YM2612
            if (z80Provider.isReset()) {
                LOG.warn("FM read while Z80 reset");
                return 1;
            }
            return busProvider.getFm().read();
        } else if (address >= 0x6000 && address <= 0x7EFF) {        //	BankSwitching and Reserved
            LOG.error("Z80 read bank switching: " + Integer.toHexString(address));
            return 0xFF;
        } else if (address >= 0x7F00 && address <= 0x7FFF) { //read VDP
            int vdpAddress = VDP_BASE_ADDRESS + address;
            LOG.warn("Z80 read VDP memory , address : " + address);
            return (int) busProvider.read(vdpAddress, Size.BYTE);
        } else if (address >= START_68K_BANK && address <= END_68K_BANK) { //M68k memory bank
            if (romBankPointer % ROM_BANK_POINTER_SIZE != 0) {
                LOG.info("Reading 68k memory, but pointer: " + romBankPointer);
            }
            address = address - START_68K_BANK + (romBank68kSerial << 15);
            return (int) busProvider.read(address, Size.BYTE);
        } else {
            LOG.error("Illegal Z80 memory read: " + Integer.toHexString(address));
        }
        return 0;
    }


    private void writeMemory(int address, int data) {
        if (address < MEMORY_SIZE) {  //RAM
            memory[address] = data;
        } else if (address >= MEMORY_SIZE && address < MEMORY_SIZE * 2) { //RAM MIRROR
            memory[address - MEMORY_SIZE] = data;
        } else if (address >= START_YM2612 && address < END_YM2612) { //YM2612
            //LOG.info("Writing " + Integer.toHexString(address) + " data: " + data);
            if (z80Provider.isReset()) {
                LOG.warn("Illegal write to FM while Z80 reset");
                return;
            }
            if (address % 4 == 0) {        //	YM2612 A0
                YMA0 = data;
            } else if (address % 4 == 1) {        //	YM2612 D0
                YMD0 = data;
                busProvider.getFm().write0(YMA0, YMD0);
            } else if (address % 4 == 2) {        //	YM2612 A1
                YMA1 = data;
            } else if (address % 4 == 3) {        //	YM2612 D1
                YMD1 = data;
                busProvider.getFm().write1(YMA1, YMD1);
            }
        } else if (address >= 0x6000 && address <= 0x60FF) {        //	rom banking
            if (address == 0x6000) {
                romBanking(data);
            } else {
                LOG.warn("Unexpected bank write: " + Integer.toHexString(address) + ", data: " + data);
            }
        } else if (address >= 0x6100 && address <= 0x7EFF) {        //	unused
            LOG.warn("Write to unused memory: " + Integer.toHexString(address));
        } else if (address >= 0x7F00 && address <= 0x7F1F) {        //	VDP (SN76489 PSG)
            int vdpAddress = VDP_BASE_ADDRESS + address;
            busProvider.write(vdpAddress, data, Size.BYTE);
        } else if (address >= 0x7F20 && address <= 0x7FFF) {        //	VDP Illegal
            LOG.warn("Illegal Write to VDP: " + Integer.toHexString(address));
        } else if (address >= START_68K_BANK && address <= END_68K_BANK) {
            address = address - START_68K_BANK + (romBank68kSerial << 15);
//            LOG.warn("Illegal write to 68k: " + Integer.toHexString(address));
            busProvider.write(address, data, Size.BYTE); //TODO Alex Kidd writes to PSG via 68k??
        } else {
            LOG.error("Illegal Z80 memory write:  " + Integer.toHexString(address) + ", " + data);
        }
    }


    @Override
    // Read a byte from memory
    public int readByte(int address) {
        return readMemory(address);
    }

    @Override
    // Read a word from memory
    public int readWord(int address) {
        return readByte(address) + readByte(address + 1) * 0xFF;
    }

    @Override
    public void writeByte(int address, int data) {
        writeMemory(address, data);
    }

    @Override
    public void writeWord(int address, int data) {
        writeByte(address, (data & 0x00FF));
        address = (address + 1) & 65535;
        data = (data >>> 8);
        writeByte(address, data);
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

    public int[] getMemory() {
        return memory;
    }

    /**
     * Read a standard tape dump file into an array and return
     *
     * @param fileName The file to read
     * @throws IOException Thrown if a failure occurs while reading the file
     */
    private void readHexDumpFile(String fileName) throws IOException {
        String line;
        int address, base;
        LineNumberReader source = new LineNumberReader(new InputStreamReader(new FileInputStream(fileName), Charset.forName("UTF-8")));
        //
        boolean firstTime = true;
        while (true) { // read a line

            String inputLine = source.readLine();
            if ((null == inputLine) || (inputLine.charAt(0) == '.')) {
                break;
            }
            line = inputLine.trim();
            // System.out.println("<" + line + ">");

            // convert and place into memory

            address = Integer.parseInt(line.substring(0, 4), 16);
            // System.out.println("Address : " + address + " : " + line.substring(0, 4));
            if (firstTime) {
                firstTime = false;
            }
            base = 5;
            for (int i = 0; i < 8; i++) {
                int value = Integer.parseInt(line.substring(base, base + 2), 16);
                memory[address] = value;
                base = base + 3;
                address++;
            }
        }
        source.close();
    }

    public static void main(String[] args) {
        int val = 0x1C0A;
        System.out.println(val & 0x1F);
        val = 0x1FFC;
        System.out.println(val & 0x1F);
    }

}
