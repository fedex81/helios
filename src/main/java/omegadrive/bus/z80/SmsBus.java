/*
 * SmsBus
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 05/10/19 14:22
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

package omegadrive.bus.z80;

import omegadrive.SystemLoader;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.cart.CartridgeInfoProvider;
import omegadrive.cart.mapper.MapperSelector;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.cart.mapper.SmsMapper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Size;
import omegadrive.vdp.SmsVdp;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static omegadrive.sound.fm.ym2413.Ym2413Provider.FmReg.ADDR_LATCH_REG;
import static omegadrive.sound.fm.ym2413.Ym2413Provider.FmReg.DATA_REG;

public class SmsBus extends DeviceAwareBus<SmsVdp> implements Z80BusProvider, RomMapper {

    private static Logger LOG = LogManager.getLogger(SmsBus.class);

    private static final boolean verbose = false;

    public static boolean HW_ENABLE_FM = false;

    private static final int ROM_START = 0;
    private static final int ROM_END = 0xBFFF;
    public static final int RAM_START = 0xC000;
    public static final int RAM_END = 0xFFFF;

    public static final int RAM_SIZE = 0x2000;  //8Kb
    public static final int RAM_MASK = RAM_SIZE - 1;
    private static final int ROM_SIZE = ROM_END + 1; //48kb

    public static final int SEGA_MAPPING_CONTROL_ADDRESS = 0xFFFC;
    public static final int CODEM_MAPPING_BASE_ADDRESS = 0x4000;
    public static final int KOREA_MAPPING_CONTROL_ADDRESS = 0xA000;

    private static final int OVERSEAS = 0x40;
    private static final int DOMESTIC = 0;

    private CartridgeInfoProvider cartridgeInfoProvider;
    private RomMapper mapper;
    private SmsMapper smsMapper;

    /** I/O Ports A and B * (5 ints each) */
    private int[] ioPorts;

    //0 - domestic (J)
    //0x40 - overseas (U/E)
    private int countryValue = DOMESTIC;

    private boolean isGG = false;

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

        countryValue = RegionDetector.Region.JAPAN != systemProvider.getRegion() ? OVERSEAS : DOMESTIC;
        isGG = systemProvider.getSystemType() == SystemLoader.SystemType.GG;
        mapper = RomMapper.NO_OP_MAPPER;

        setupCartHw();
    }

    private void setupCartHw(){
        this.cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memoryProvider, systemProvider.getRomName());
        MapperSelector.Entry e = MapperSelector.getMapperData(systemProvider.getSystemType(),
                cartridgeInfoProvider.getCrc32());
        LOG.info(cartridgeInfoProvider.toString());
        String mapperName = SmsMapper.Type.SEGA.name();
        if(e != MapperSelector.MISSING_DATA){
            LOG.info("Cart db match:\n{}", e);
            mapperName = e.mapperName;
        } else {
            LOG.info("Unknown rom, assuming {} mapper, crc32: {}", mapperName, cartridgeInfoProvider.getCrc32());
        }
        smsMapper = SmsMapper.createInstance(cartridgeInfoProvider.getRomName(), memoryProvider);
        mapper = smsMapper.setupRomMapper(mapperName, mapper);
    }

    @Override
    public long read(long addressL, Size size) {
        return mapper.readData(addressL, size);
    }

    @Override
    public void write(long addressL, long dataL, Size size) {
        mapper.writeData(addressL, dataL, size);
    }

    @Override
    public long readData(long addressL, Size size) {
        return smsMapper.readDataMapper(addressL, size);
    }

    @Override
    public void writeData(long address, long data, Size size) {
        memoryProvider.writeRamByte((int)(address & RAM_MASK), (int)(data & 0xFF));
    }

    private int audioControl = 0;
    private boolean ioEnable = true;

    @Override
    public void writeIoPort(int port, int value) {
        port &= 0xFF;
        // Game Gear Serial Ports (do nothing for now)
        if (isGG && port < 0x07){
            return;
        }
        //FM chip detection, defaults to false
        //Port $F2 : Bit 0 can be read and written to detect if YM2413 is available.
        if (HW_ENABLE_FM && !ioEnable && port == 0xF2) {
            audioControl = value;
            boolean psgMute = (value & 3) == 1 || (value & 3) == 2;
            boolean fmMute = (value & 3) == 0 || (value & 3) == 2;
            LOG.info("PSG mute : {}, FM mute: {}", psgMute, fmMute);
            return;
        }

        if (HW_ENABLE_FM && (port == 0xF0 || port == 0xF1)) {
            int fmPort = port == 0xF0 ? ADDR_LATCH_REG.ordinal() : DATA_REG.ordinal();
            soundProvider.getFm().write(fmPort, value);
            return;
        }
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

                    if (countryValue == DOMESTIC) // not european system
                    {
                        ioPorts[0] = ~ioPorts[0];
                        ioPorts[1] = ~ioPorts[1];
                    }
                }
            }
            break;

            // 0xBE VDP Data port
            case 0x80:
                vdpProvider.dataWrite(value);
                break;

            // 0xBD / 0xBF VDP Control port (Mirrored at two locations)
            case 0x81:
                vdpProvider.controlWrite(value);
                break;

            // 0x7F: PSG
            case 0x40:
            case 0x41:
                soundProvider.getPsg().write(value);
                break;
            default:
                if (port == 0xDE || port == 0xDF ||  //legacy SG1000/keyboard stuff
                        port == 0xF0 || port == 0xF1 || port == 0xF2) {
//                    LOG.debug("Ignored writePort: {}, data {}", Integer.toHexString(port), Integer.toHexString(value));
                    return;
                }
                if (port == 0x3E) {
                    ioEnable = (value & 4) == 0;
//                    LOG.debug("writePort: {}, data {}", Integer.toHexString(port), Integer.toHexString(value));
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
        if (isGG && port < 0x07)
        {
            switch (port)
            {
                // GameGear (Start Button and Nationalisation)
                case 0x00:
                    //0 -> pressed, 1 - not pressed
                    int val = joypadProvider.readDataRegister3() == 2 ? 0x80 : 0;
                    return (val & 0xBF) | countryValue;

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
        //FM chip detection, defaults to false
        //Port $F2 : Bit 0 can be read and written to detect if YM2413 is available.
        if (port == 0xF2) {
            if (ioEnable) {
                LOG.warn("FM chip detection read when io enabled");
            }
            return HW_ENABLE_FM ? audioControl & 3 : 0xFF;
        }

        switch (port & 0xC1)
        {
            // 0x7E - Vertical Port
            case 0x40:
                return vdpProvider.getVCount();

            // 0x7F - Horizontal Port
            case 0x41:
                return hCounter;

            // VDP Data port
            case 0x80:
                return vdpProvider.dataRead();

            // VDP Control port
            case 0x81:
                return vdpProvider.controlRead();

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
        return vdpProvider.getHCount();
    }

    // --------------------------------------------------------------------------------------------
    // Port A/B Emulation
    // --------------------------------------------------------------------------------------------

    private final void writePort(int index, int value)
    {
        ioPorts[index + IO_TR_DIRECTION] = value & 0x01;
        ioPorts[index + IO_TH_DIRECTION] = value & 0x02;
        ioPorts[index + IO_TR_OUTPUT] = value & 0x10;
        ioPorts[index + IO_TH_OUTPUT] = countryValue == DOMESTIC ? (~value) & 0x20 : value & 0x20;
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

    public void reloadBanking() {
        smsMapper.reloadBanking();
    }

    @Override
    public void closeRom() {
        mapper.closeRom();
    }

    @Override
    public void newFrame() {
        joypadProvider.newFrame();
    }

    private boolean prev = false;
    private boolean isNmiSet = false;

    @Override
    public void handleInterrupts(Z80Provider.Interrupt type) {
        if(type == Z80Provider.Interrupt.NMI){
            handleNmi();
            return;
        }
        handleIM();
    }

    private void handleIM(){
        boolean set = vdpProvider.isVINT() || vdpProvider.isHINT();
        z80Provider.interrupt(set);
        if(verbose && prev != set){
            LOG.info(vdpProvider.getInterruptHandler().getStateString("INT: " + set));
            LOG.info("Vint: {}, Hint: {}", vdpProvider.isVINT(), vdpProvider.isHINT());
            prev = set;
        }
    }

    private void handleNmi() {
        boolean set = joypadProvider.readDataRegister3() < 2;
        if(set && !isNmiSet){
            z80Provider.triggerNMI();
        }
        isNmiSet = set;
    }
}
