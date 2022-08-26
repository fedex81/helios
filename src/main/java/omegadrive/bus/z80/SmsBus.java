/*
 * SmsBus
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 15:22
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
import omegadrive.bus.model.Z80BusProvider;
import omegadrive.cart.CartridgeInfoProvider;
import omegadrive.cart.mapper.MapperSelector;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.cart.mapper.sms.SmsMapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.joypad.TwoButtonsJoypad;
import omegadrive.util.*;
import omegadrive.vdp.SmsVdp;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static omegadrive.sound.fm.ym2413.Ym2413Provider.FmReg.ADDR_LATCH_REG;
import static omegadrive.sound.fm.ym2413.Ym2413Provider.FmReg.DATA_REG;
import static omegadrive.util.Util.th;

/**
 * TODO: add support for port 3E
 */
public class SmsBus extends DeviceAwareBus<SmsVdp, TwoButtonsJoypad> implements Z80BusProvider, RomMapper {

    private static final Logger LOG = LogHelper.getLogger(SmsBus.class);

    private static final boolean verbose = false;

    public static boolean HW_ENABLE_FM = false;
    public static final boolean HW_ENABLE_BIOS = false;
    /**
     * Horizontal Counter Latch
     */
    protected int hCounter;
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

    protected static final int OVERSEAS = 0x40;
    protected static final int DOMESTIC = 0;

    private CartridgeInfoProvider cartridgeInfoProvider;
    private RomMapper mapper;
    private SmsMapper smsMapper;
    private int BIOS_END;
    private int[] bios;
    private int audioControl = 0;
    //https://www.smspower.org/Development/Port3E
    private int portAB, port3E;

    //0 - domestic (J)
    //0x40 - overseas (U/E)
    protected int countryValue = DOMESTIC;

    private boolean isGG = false;
    private boolean ioEnable = true, biosEnable = false, cartEnabled = true;

    @Override
    public void init() {
        handlePortAB(0xF);
        handlePort3E(HW_ENABLE_BIOS ? 0xE7 : 8);

        countryValue = RegionDetector.Region.JAPAN != systemProvider.getRegion() ? OVERSEAS : DOMESTIC;
        isGG = systemProvider.getSystemType() == SystemLoader.SystemType.GG;
        mapper = RomMapper.NO_OP_MAPPER;

        if (HW_ENABLE_BIOS) {
            Path p = Paths.get(SystemLoader.biosFolder, "bios.sms");
            bios = Util.toUnsignedIntArray(FileUtil.loadBiosFile(p));
            BIOS_END = bios.length;
            LOG.info("Loading Sms bios from: {}", p.toAbsolutePath().toString());
        }

        setupCartHw();
    }

    protected void setupCartHw() {
        this.cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memoryProvider, systemProvider.getRomPath());
        MapperSelector.Entry e = MapperSelector.getMapperData(systemProvider.getSystemType(),
                cartridgeInfoProvider.getCrc32());
        LOG.info(cartridgeInfoProvider.toString());
        String mapperName = SmsMapper.Type.SEGA.name();
        if (e != MapperSelector.MISSING_DATA) {
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
        //TODO should be in the mapper?
//        if (HW_ENABLE_BIOS && biosEnable && addressL < BIOS_END) {
//            return Util.readData(bios, size, (int) addressL);
//        }
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

    @Override
    public void writeIoPort(int port, int value) {
        port &= 0xFF;
        // Game Gear Serial Ports (do nothing for now)
        if (isGG && port < 0x07){
            return;
        }
        switch (port & 0xC1) {
            case 0:
            case 0x01:
                if (port % 2 == 0) {
                    handlePort3E(value);
                } else {
                    handlePortAB(value);
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
            //legacy SG1000/keyboard stuff, ignore
            case 0xC0:
            case 0xC1:
                //FM chip detection, defaults to false
                //Port $F2 : Bit 0 can be read and written to detect if YM2413 is available.
                boolean isAudioControl = HW_ENABLE_FM && !ioEnable && port == 0xF2;
                boolean isFmWrite = HW_ENABLE_FM && (port == 0xF0 || port == 0xF1);
                if (isAudioControl) {
                    handleAudioControl(value);
                } else if (isFmWrite) {
                    int fmPort = port == 0xF0 ? ADDR_LATCH_REG.ordinal() : DATA_REG.ordinal();
                    soundProvider.getFm().write(fmPort, value);
                }
                break;
            default:
                LOG.warn("Unexpected writePort: {}, data {}", th(port), th(value));
                break;
        }
    }

    @Override
    public int readIoPort(int port) {
        port &= 0xFF;
        // Game Gear Serial Ports (not fully emulated)
        if (isGG && port < 0x07) {
            return handleGGSerialRead(port);
        }
        switch (port & 0xC1) {
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
                if (ioEnable) {
                    return joypadProvider.readDataRegister1();
                } else if (port == 0xF2) {
                    //FM chip detection, defaults to false
                    //Port $F2 : Bit 0 can be read and written to detect if YM2413 is available.
                    return HW_ENABLE_FM ? audioControl & 3 : 0xFF;
                }
                break;
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
                return portAB & 0x80 | (portAB & 0x20) << 1 | (joypadProvider.readDataRegister2() & 0x3F);
            default:
                LOG.warn("Unexpected readPort: {}", th(port));
                break;
        }

        // Default Value is 0xFF
        return 0xFF;
    }

    /**
     * Bit	Function	            No effect on
     * 7	Expansion slot enable	SMS2, GG, Genesis
     * 6	Cartridge slot enable	GG, Genesis
     * 5	Card slot enable	    SMS2, GG, Genesis
     * 4	RAM enable
     * 3	BIOS ROM enable	        Genesis
     * 2	I/O enable	            GG, Genesis
     * 1	Unknown
     * 0	Unknown
     */
    private void handlePort3E(int value) {
        if (value != port3E) {
            boolean bEn = (value & 8) == 0;
            if (bEn && !HW_ENABLE_BIOS) {
                LOG.warn("HW_ENABLE_BIOS is {}, unable to set biosEn: {}", HW_ENABLE_BIOS, bEn);
                bEn = false;
            }
            ioEnable = (value & 4) == 0;
            biosEnable = bEn;
            cartEnabled = (value & 32) == 0 || (value & 64) == 0; //cart and card ports
            LOG.info("Setting port3E, {} -> {},  cartEn: {}, biosEn: {}, ioEn: {}", value, port3E,
                    cartEnabled, biosEnable, ioEnable);
            if (bEn && !HW_ENABLE_BIOS) {
                LOG.warn("HW_ENABLE_BIOS is {}, unable to set biosEn: {}", HW_ENABLE_BIOS, bEn);
            }
        }
    }


    // 0x3F IO Port
    // D7 : Port B TH pin output level (1=high, 0=low)
    // D6 : Port B TR pin output level (1=high, 0=low)
    // D5 : Port A TH pin output level (1=high, 0=low)
    // D4 : Port A TR pin output level (1=high, 0=low)
    // D3 : Port B TH pin direction (1=input, 0=output)
    // D2 : Port B TR pin direction (1=input, 0=output)
    // D1 : Port A TH pin direction (1=input, 0=output)
    // D0 : Port A TR pin direction (1=input, 0=output)
    private void handlePortAB(int value) {
        int thLevA = value & 0x20;
        int thDirA = value & 2;
        int thLevB = value & 0x80;
        int thDirB = value & 8;
        //DOMESTIC: set them as inputs to make them high, outputs -> low
        if (countryValue == DOMESTIC) {
            thLevA = thDirA > 0 ? 0x20 : 0;
            thLevB = thDirB > 0 ? 0x80 : 0;
            portAB = portAB & 0x5F | thLevB | thLevA;
        } else if (countryValue == OVERSEAS) {
            int prevThLevA = portAB & 0x20;
            int prevThLevB = portAB & 0x80;
            //is input and has transition
            boolean levelChange = (thDirA > 0 && prevThLevA != thLevA) ||
                    (thDirB > 0 && prevThLevB != thLevB);
            if (levelChange) {
                hCounter = getHCount();
            }
            portAB = value;
        }
    }

    private int handleGGSerialRead(int port) {
        int res = 0xFF;
        switch (port) {
            // GameGear (Start Button and Nationalisation)
            case 0x00:
                //0 -> pressed, 1 - not pressed
                int val = joypadProvider.readDataRegister3() == 2 ? 0x80 : 0;
                res = (val & 0xBF) | countryValue;
                break;
            // GG Serial Communication Ports  -
            // Return 0 for now as "OutRun" gets stuck in a loop by returning 0xFF
            case 0x01:
            case 0x02:
            case 0x03:
            case 0x04:
            case 0x05:
                res = 0;
                break;
            case 0x06:
                res = 0xFF;
                break;
        }
        return res;
    }

    private void handleAudioControl(int value) {
        audioControl = value;
        boolean psgDisable = (value & 3) == 1 || (value & 3) == 2;
        boolean fmDisable = (value & 3) == 0 || (value & 3) == 2;
        soundProvider.setEnabled(soundProvider.getPsg(), !psgDisable);
        soundProvider.setEnabled(soundProvider.getFm(), !fmDisable);
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

    protected int getHCount() {
        return vdpProvider.getHCount();
    }

    @Override
    public void closeRom() {
        mapper.closeRom();
    }

    @Override
    public void onNewFrame() {
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

    public int getMapperControl() {
        return smsMapper.getMapperControl();
    }

    public int[] getFrameReg() {
        return smsMapper.getFrameReg();
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
//        LOG.info("mapperControl: {}, frameReg: {}", control, Arrays.toString(frameReg));
        buffer.put((byte) (smsMapper.getMapperControl() & 0xFF));
        buffer.put(Util.unsignedToByteArray(smsMapper.getFrameReg()));
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        write(0xFFFC, buffer.get(), Size.BYTE);
        write(0xFFFD, buffer.get(), Size.BYTE);
        write(0xFFFE, buffer.get(), Size.BYTE);
        write(0xFFFF, buffer.get(), Size.BYTE);
    }
}
