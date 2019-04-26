/*
 * SmsBus
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 15/04/19 21:36
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

package omegadrive.bus.sg1k;

import afu.org.checkerframework.checker.oigj.qual.O;
import omegadrive.Device;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.vdp.Engine;
import omegadrive.vdp.Sg1000Vdp;
import omegadrive.vdp.SmsVdp;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class SmsBus extends DeviceAwareBus implements Sg1000BusProvider {

    private static Logger LOG = LogManager.getLogger(SmsBus.class);

    private static final boolean verbose = false;

    private static final int ROM_START = 0;
    private static final int ROM_END = 0xBFFF;
    private static final int RAM_START = 0xC000;
    private static final int RAM_END = 0xFFFF;

    private static final int RAM_SIZE = 0x2000;  //8Kb
    private static final int RAM_MASK = RAM_SIZE - 1;
    private static final int ROM_SIZE = ROM_END + 1; //48kb

    private static final int MAPPING_CONTROL_ADDRESS = 0xFFFC;

    public SmsVdp vdp;

    private int lastDE;
    private int[] rom;
    private int[] ram;

    /** Memory frame registers */
    private int[] frameReg = new int[FRAME_REG_DEFAULT.length];
    private int mappingControl = 0;
    private int numPages = 2; //32kb default
    private static final int[] FRAME_REG_DEFAULT = {0,1,0};

    // 0 -> 0, 1 -> 24, 2 -> 16, 3 -> 8
    private static final int[] bankShiftMap = {0, 24, 16, 8};

    /** European / Domestic System */
    private static int europe = 0x40;

    /** I/O Ports A and B * (5 ints each) */
    public int[] ioPorts;

    private final static int
            IO_TR_DIRECTION = 0,
            IO_TH_DIRECTION = 1,
            IO_TR_OUTPUT = 2,
            IO_TH_OUTPUT = 3,
            IO_TH_INPUT = 4;

    public final static int
            PORT_A = 0,
            PORT_B = 5;

    /** Horizontal Counter Latch */
    private int hCounter;

    @Override
    public void init() {
        ioPorts = new int[10];
        ioPorts[PORT_A + IO_TH_INPUT] = 1;
        ioPorts[PORT_B + IO_TH_INPUT] = 1;
        frameReg = Arrays.copyOf(FRAME_REG_DEFAULT, FRAME_REG_DEFAULT.length);
        this.rom = memoryProvider.getRomData();
        this.ram = memoryProvider.getRamData();
        this.numPages = rom.length >> 14;
    }

    @Override
    public Sg1000BusProvider attachDevice(Device device) {
        if (device instanceof SmsVdp) {
            this.vdp = (SmsVdp) device;
        }
        super.attachDevice(device);
        return this;
    }

    @Override
    public long read(long addressL, Size size) {
        int address = (int) (addressL & 0xFFFF);
        if (size != Size.BYTE) {
            LOG.error("Unexpected read, addr : {} , size: {}", address, size);
            return 0xFF;
        }
        int page = (address >> 14);
        if(page < FRAME_REG_DEFAULT.length) { //rom
            int block16k = frameReg[page] << 14;
            int newAddr = block16k + (address & 0x3FFF);
            return memoryProvider.readRomByte(newAddr);
        } else if (address >= RAM_START && address <= RAM_END) { //ram
            address &= RAM_SIZE - 1;
            return memoryProvider.readRamByte(address);
        }
        LOG.error("Unexpected Z80 memory read: " + Long.toHexString(address));
        return 0xFF;
    }

    @Override
    public void write(long addressL, long dataL, Size size) {
        int address = (int) (addressL & RAM_MASK);
        int data = (int) (dataL & 0xFF);
        memoryProvider.writeRamByte(address, data);
        if (addressL >= MAPPING_CONTROL_ADDRESS) {
            mappingRegisters(addressL, data);
        }
    }

    private void mappingRegisters(long address, int data){
        int val = (int) (address & 3);
        switch (val){
            case 0:
                mappingControl = data;
                break;
            case 1:
            case 2:
            case 3:
                int frameRegNum = val - 1;
                int bankShift = bankShiftMap[mappingControl & 3];
                data = (data + bankShift) % numPages;
                frameReg[frameRegNum] = data;
                break;
        }
        LogHelper.printLevel(LOG, Level.INFO,"writeMappingReg: {} , data: {}", address, data, verbose);
    }

    @Override
    public void writeIoPort(int port, int value) {
        port &= 0xFF;
        switch (port & 0xC1) {
            // 0x3F IO Port
            // D7 : Port B TH pin output level (1=high, 0=low)
            // D6 : Port B TR pin output level (1=high, 0=low)
            // D5 : Port A TH pin output level (1=high, 0=low)
            // D4 : Port A TR pin output level (1=high, 0=low)
            // D3 : Port B TH pin direction (1=input, 0=output)
            // D2 : Port B TR pin direction (1=input, 0=output)
            // D1 : Port A TH pin direction (1=input, 0=output)
            // D0 : Port A TR pin direction (1=input, 0=output)
            case 0x01: {
                boolean oldTH = getTH(PORT_A) != 0 || getTH(PORT_B) != 0;

                writePort(PORT_A, value);
                writePort(PORT_B, value >> 2);

                // Toggling TH latches H Counter
                if (!oldTH && (getTH(PORT_A) != 0 || getTH(PORT_B) != 0)) {
                    hCounter = getHCount();
                }
                // Rough emulation of Nationalisation bits
                else
                {
                    ioPorts[0] = (value & 0x20) << 1;
                    ioPorts[1] = (value & 0x80);

                    if (europe == 0) // not european system
                    {
                        ioPorts[0] = ~ioPorts[0];
                        ioPorts[1] = ~ioPorts[1];
                    }
                }
            }
            break;

            // 0xBE VDP Data port
            case 0x80:
                vdp.dataWrite(value);
                break;

            // 0xBD / 0xBF VDP Control port (Mirrored at two locations)
            case 0x81:
                vdp.controlWrite(value);
                break;

            // 0x7F: PSG
            case 0x40:
            case 0x41:
                soundProvider.getPsg().write(value);
                break;
            default:
                if(port == 0xDE || port == 0xDF){ //legacy SG1000/keyboard stuff
                    LOG.info("Ignored writePort: {}, data {}", Integer.toHexString(port), Integer.toHexString(value));
                    return;
                }
                LOG.warn("Unexpected writePort: {}, data {}", Integer.toHexString(port), Integer.toHexString(value));
                break;
        }
    }

    @Override
    public int readIoPort(int port)
    {
        port &= 0xFF;
        // Game Gear Serial Ports (not fully emulated)
        if (Engine.is_gg && port < 0x07)
        {
            switch (port)
            {
                // GameGear (Start Button and Nationalisation)
                case 0x00:
                    return (Engine.ggstart & 0xBF) | europe;

                // GG Serial Communication Ports  -
                // Return 0 for now as "OutRun" gets stuck in a loop by returning 0xFF
                case 0x01:
                case 0x02:
                case 0x03:
                case 0x04:
                case 0x05:
                    return 0;
                case 0x06:
                    return 0xFF;
            }
        }


        switch (port & 0xC1)
        {
            // 0x7E - Vertical Port
            case 0x40:
                return vdp.getVCount();

            // 0x7F - Horizontal Port
            case 0x41:
                return hCounter;

            // VDP Data port
            case 0x80:
                return vdp.dataRead();

            // VDP Control port
            case 0x81:
                return vdp.controlRead();

            // 0xC0 / 0xDC - I/O Port A
            // D7 : Port B DOWN pin input
            // D6 : Port B UP pin input
            // D5 : Port A TR pin input
            // D4 : Port A TL pin input
            // D3 : Port A RIGHT pin input
            // D2 : Port A LEFT pin input
            // D1 : Port A DOWN pin input
            // D0 : Port A UP pin input
            case 0xC0:
                return joypadProvider.readDataRegister1();

            // 0xC1 / 0xDD - I/O Port B and Misc
            // D7 : Port B TH pin input
            // D6 : Port A TH pin input
            // D5 : Unused
            // D4 : RESET button (1= not pressed, 0= pressed)
            // D3 : Port B TR pin input
            // D2 : Port B TL pin input
            // D1 : Port B RIGHT pin input
            // D0 : Port B LEFT pin input
            case 0xC1:
                return (joypadProvider.readDataRegister2() & 0x3F) | ioPorts[0] | ioPorts[1];
            default:
                LOG.warn("Unexpected readPort: {}", Integer.toHexString(port));
                break;
        }

        // Default Value is 0xFF
        return 0xFF;
    }

    // --------------------------------------------------------------------------------------------
    // H Counter Emulation
    //
    //  The H counter is 9 bits, and reading it returns the upper 8 bits. This is
    //  because a scanline consists of 342 pixels, which couldn't be represented
    //  with an 8-bit counter. Each scanline is divided up as follows:
    //
    //    Pixels H.Cnt   Description
    //    256 : 00-7F : Active display
    //     15 : 80-87 : Right border
    //      8 : 87-8B : Right blanking
    //     26 : 8B-ED : Horizontal sync
    //      2 : ED-EE : Left blanking
    //     14 : EE-F5 : Color burst
    //      8 : F5-F9 : Left blanking
    //     13 : F9-FF : Left border
    // --------------------------------------------------------------------------------------------

    private final int getHCount() {
        return vdp.getHCount();
    }

    // --------------------------------------------------------------------------------------------
    // Port A/B Emulation
    // --------------------------------------------------------------------------------------------

    private final void writePort(int index, int value)
    {
        ioPorts[index + IO_TR_DIRECTION] = value & 0x01;
        ioPorts[index + IO_TH_DIRECTION] = value & 0x02;
        ioPorts[index + IO_TR_OUTPUT] = value & 0x10;
        ioPorts[index + IO_TH_OUTPUT] = europe == 0 ? (~value) & 0x20 : value & 0x20;
    }

    private final int getTH(int index)
    {
        return (ioPorts[index + IO_TH_DIRECTION] == 0) ?
                ioPorts[index + IO_TH_OUTPUT] :
                ioPorts[index + IO_TH_INPUT];
    }

    private final void setTH(int index, boolean on)
    {
        ioPorts[index + IO_TH_DIRECTION] = 1;
        ioPorts[index + IO_TH_INPUT] = on ? 1 : 0;
    }



    @Override
    public void closeRom() {

    }

    @Override
    public void newFrame() {
        joypadProvider.newFrame();
    }

    boolean prev = false;

    @Override
    public void handleVdpInterruptsZ80() {
        boolean set = vdp.isVINT() || vdp.isHINT();
        z80Provider.interrupt(set);
        if(verbose && prev != set){
            LOG.info(vdp.getInterruptHandler().getStateString("INT: " + set));
            LOG.info("Vint: {}, Hint: {}", vdp.isVINT(), vdp.isHINT());
            prev = set;
        }
    }
}
