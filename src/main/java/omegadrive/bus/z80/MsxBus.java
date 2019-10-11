/*
 * MsxBus
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 11:06
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

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.cart.CartridgeInfoProvider;
import omegadrive.cart.mapper.MapperSelector;
import omegadrive.cart.mapper.MsxAsciiMapper;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.input.MsxKeyboardInput;
import omegadrive.joypad.JoypadProvider.JoypadNumber;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.FileLoader;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.vdp.Tms9918aVdp;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static omegadrive.joypad.JoypadProvider.JoypadNumber.P1;
import static omegadrive.joypad.JoypadProvider.JoypadNumber.P2;

/**
 * see
 * https://www.msx.org/forum/semi-msx-talk/emulation/primary-slots-and-secondary-slots
 * http://msx.ebsoft.fr/roms/index.php?v=MSX1&Send=Send
 * http://fms.komkon.org/MSX/Docs/Portar.txt
 */
public class MsxBus extends DeviceAwareBus<Tms9918aVdp> implements Z80BusProvider {

    private static Logger LOG = LogManager.getLogger(MsxBus.class);

    static final boolean verbose = false;

    private static int PAGE_SIZE = 0x4000; //16kb
    private static int PAGE_MASK = PAGE_SIZE -1;

    private static int SLOT_SIZE = 0x10000;
    private static int SLOTS = 4;

    private int[] bios;

    private int slotSelect = 0;
    private int ppiC_Keyboard = 0;
    private int[][] secondarySlot = new int[SLOTS][];
    private boolean[] secondarySlotWritable = new boolean[SLOTS];
    private int[] pageStartAddress = {0, 0, 0, 0};
    private int[] pageSlotMapper = {0, 0, 0, 0};

    private int[] emptySlot = new int[SLOT_SIZE];

    private int psgAddressLatch = 0;
    private JoypadNumber joypadSelect = P1;

    private RomMapper mapper;
    private CartridgeInfoProvider cartridgeInfoProvider;

    public MsxBus() {
        Path p = Paths.get(SystemLoader.biosFolder, SystemLoader.biosNameMsx1);
        bios = FileLoader.loadBiosFile(p);
        LOG.info("Loading Msx bios from: " + p.toAbsolutePath().toString());
        Arrays.fill(emptySlot, 0xFF);

        secondarySlot[0] = bios;
        secondarySlot[1] = emptySlot;
        secondarySlot[2] = emptySlot;
        secondarySlot[3] = emptySlot;

        mapper = RomMapper.NO_OP_MAPPER;
    }

    @Override
    public Z80BusProvider attachDevice(Device device) {
        super.attachDevice(device);
        if(device instanceof IMemoryProvider){
            secondarySlot[3] = this.memoryProvider.getRamData();
            secondarySlotWritable[3] = true;
        }
        return this;
    }

    @Override
    public long read(long addressL, Size size) {
        int addressI = (int) (addressL & 0xFFFF);
        int page = addressI >> 14;
        int secSlotNumber = pageSlotMapper[page];
        int res = 0xFF;
        int address = (addressI & PAGE_MASK) + pageStartAddress[page];

        if(mapper != RomMapper.NO_OP_MAPPER && secSlotNumber > 0 && secSlotNumber < 3){
            res = (int) mapper.readData(addressL, Size.BYTE);
        } else if(address < secondarySlot[secSlotNumber].length) {
            res = secondarySlot[secSlotNumber][address];
        } else {
            LOG.error("Unexpected read: {}, slot: {}", Long.toHexString(addressL), secSlotNumber);
        }
        return res;
    }

    private void writeSlot(int[] data, int address, int value){
        if(address < data.length) {
            data[address] = value;
        }
    }

    @Override
    public void write(long addressL, long data, Size size) {
        int addressI = (int) (addressL & 0xFFFF);
        int page = addressI >> 14;
        int res = 0xFF;
        int secSlotNumber = pageSlotMapper[page];
        if(secondarySlotWritable[secSlotNumber]){
            int address = (addressI & PAGE_MASK) + pageStartAddress[page];
            writeSlot(secondarySlot[secSlotNumber], address, (int) data);
        } else if(mapper != RomMapper.NO_OP_MAPPER && secSlotNumber > 0 && secSlotNumber < 3) {
            mapper.writeData(addressL, data, size);
        } else {
            LOG.error("Unexpected write: {}, data: {}, slot: {}", Long.toHexString(addressL),
                    Long.toHexString(data), secSlotNumber);
        }
    }

    @Override
    public void writeIoPort(int port, int value) {
        port &= 0xFF;
        byte byteVal = (byte) (value & 0XFF);
        LogHelper.printLevel(LOG, Level.INFO, "Write IO port: {}, value: {}", port, value, verbose);
        switch (port) {
            case 0x80:
            case 0xC0:
                joypadProvider.writeDataRegister1(port);
                break;
            case 0x98:
                vdpProvider.writeVRAMData(byteVal);
                break;
            case 0x99:
                vdpProvider.writeRegister(byteVal);
                break;
            case 0xA0:
                LOG.debug("Write PSG register select: {}",  value);
                psgAddressLatch = value;
                break;
            case 0xA1:
                LOG.debug("Write PSG: {}", value);
                soundProvider.getPsg().write(psgAddressLatch , value);
                if(psgAddressLatch == 0xF){
                    joypadSelect = (value & 0x40) == 0x40 ? P2 : P1;
                    LOG.debug("Write PSG register {}, data {}", psgAddressLatch, value);
                }
                break;
            case 0xA8:
                setSlotSelect(value);
                break;
            case 0xAA: // Write PPI register C (keyboard and cassette interface) (port AA)
                ppiC_Keyboard = value;
                break;
            case 0xAB: //Write PPI command register (used for setting bit 4-7 of ppi_C) (port AB)
                break;
            case 0xFC:
            case 0xFD:
            case 0xFE:
            case 0xFF:
                LOG.warn("Ignoring MSX2 memMapper write: {}, data {}",
                        Integer.toHexString(port), Integer.toHexString(value));
                break;
            default:
                LOG.warn("outPort: {}, data {}", Integer.toHexString(port), Integer.toHexString(value));
                break;
        }
    }

    @Override
    public int readIoPort(int port) {
        port &= 0xFF;
        int res = 0xFF;

        switch (port) {
            case 0x98:
                res = vdpProvider.readVRAMData();
                break;
            case 0x99:
                res = vdpProvider.readStatus();
                break;
            case 0xA0:
                LOG.debug("Read PSG register select");
                res = psgAddressLatch;
                break;
            case 0xA2:
                res = soundProvider.getPsg().read(psgAddressLatch);
                if(psgAddressLatch == 0xE){
                    res = readJoyData();
                    LOG.debug("Read PSG register {}, data {}", psgAddressLatch, res);
                }
                break;
            case 0xA8:
                res = slotSelect;
                break;
            case 0xA9: //Read PPI register B (keyboard matrix row input register)
                res = MsxKeyboardInput.getMsxKeyAdapter().getRowValue(ppiC_Keyboard & 0xF);
                res = ~((byte)res);
                break;
            case 0xAA: //Read PPI register C (keyboard and cassette interface) (port AA)
                res = ppiC_Keyboard;
                break;
            default:
                LOG.warn("inPort: {}", Integer.toHexString(port & 0xFF));
                break;

        }
        res &= 0xFF;
        LogHelper.printLevel(LOG, Level.INFO, "Read IO port: {}, value: {}", port, res, verbose);
        return res;
    }

    private void setSlotSelect(int value){
        slotSelect = value;
        reloadPageSlotMapper();
    }

    private void reloadPageSlotMapper(){
        pageSlotMapper[0] = slotSelect & 3;
        pageSlotMapper[1] = (slotSelect & 0xC) >> 2;
        pageSlotMapper[2] = (slotSelect & 0x30) >> 4;
        pageSlotMapper[3] = (slotSelect & 0xC0) >> 6;
    }

    private int readJoyData(){
        int res = 0xFF;
        switch (joypadSelect){
            case P1:
                res = joypadProvider.readDataRegister1();
                break;
            case P2:
                res = joypadProvider.readDataRegister2();
                break;
        }
        LOG.debug("Read joy {}, data: {}", joypadSelect, res);
        return res;
    }

    @Override
    public void init() {
        setupCartHw();
        int len = memoryProvider.getRomSize();
        secondarySlot[1] = memoryProvider.getRomData();
        if(len > PAGE_SIZE){
            secondarySlot[2] = memoryProvider.getRomData();
            pageStartAddress[2] = PAGE_SIZE;
        }
    }

    private void setupCartHw(){
        int len = memoryProvider.getRomSize();
        this.cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memoryProvider, systemProvider.getRomName());
        MapperSelector.Entry e = MapperSelector.getMapperData(systemProvider.getSystemType(), cartridgeInfoProvider.getSha1());
        if(e != MapperSelector.MISSING_DATA){
            LOG.info("Cart Hw match:\n{}", e);
            if(!RomMapper.NO_MAPPER_NAME.equalsIgnoreCase(e.mapperName)) {
                mapper = MsxAsciiMapper.createMapper(memoryProvider.getRomData(), e.mapperName);
                LOG.info("ROM size: {}, using mapper: {}", len, mapper.getClass().getSimpleName());
            }
        } else {
            LOG.info("Unknown rom sha1: {}", cartridgeInfoProvider.getSha1());
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void onNewFrame() {
        joypadProvider.newFrame();
    }

    @Override
    public void handleInterrupts(Z80Provider.Interrupt type) {
        boolean set = vdpProvider.getStatusINT() && vdpProvider.getGINT();
        z80Provider.interrupt(set);
    }
}
