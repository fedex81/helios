/*
 * MsxBus
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

package omegadrive.bus.z80;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.bus.model.Z80BusProvider;
import omegadrive.cart.CartridgeInfoProvider;
import omegadrive.cart.mapper.MapperSelector;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.cart.mapper.msx.MsxMapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.input.InputProvider;
import omegadrive.input.MsxKeyboardInput;
import omegadrive.joypad.MsxPad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.savestate.StateUtil;
import omegadrive.util.FileLoader;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.Tms9918aVdp;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static omegadrive.input.InputProvider.PlayerNumber.P1;
import static omegadrive.input.InputProvider.PlayerNumber.P2;

/**
 * see
 * https://www.msx.org/forum/semi-msx-talk/emulation/primary-slots-and-secondary-slots
 * http://msx.ebsoft.fr/roms/index.php?v=MSX1&Send=Send
 * http://fms.komkon.org/MSX/Docs/Portar.txt
 */
public class MsxBus extends DeviceAwareBus<Tms9918aVdp, MsxPad> implements Z80BusProvider, Device {

    private static final Logger LOG = LogManager.getLogger(MsxBus.class);

    static final boolean verbose = false;

    private static final int PAGE_SIZE = 0x4000; //16kb
    private static final int PAGE_MASK = PAGE_SIZE - 1;

    private static final int SLOT_SIZE = 0x10000;
    private static final int SLOTS = 4;

    private static final int[] emptySlot = new int[SLOT_SIZE];

    private final int[] bios;

    private final int[][] secondarySlot = new int[SLOTS][];
    private final boolean[] secondarySlotWritable = new boolean[SLOTS];

    private final MsxBusContext ctx;
    private InputProvider.PlayerNumber joypadSelect = P1;

    private RomMapper mapper;
    private CartridgeInfoProvider cartridgeInfoProvider;

    public MsxBusContext getCtx() {
        return ctx;
    }

    public MsxBus() {
        Path p = Paths.get(SystemLoader.biosFolder, SystemLoader.biosNameMsx1);
        bios = Util.toUnsignedIntArray(FileLoader.loadBiosFile(p));
        LOG.info("Loading Msx bios from: {}", p.toAbsolutePath().toString());
        Arrays.fill(emptySlot, 0xFF);
        ctx = new MsxBusContext();

        secondarySlot[0] = bios;
        secondarySlot[1] = emptySlot;
        secondarySlot[2] = emptySlot;
        secondarySlot[3] = emptySlot;

        mapper = RomMapper.NO_OP_MAPPER;
    }

    @Override
    public long read(long addressL, Size size) {
        int addressI = (int) (addressL & 0xFFFF);
        int page = addressI >> 14;
        int secSlotNumber = ctx.pageSlotMapper[page];
        int res = 0xFF;
        int address = (addressI & PAGE_MASK) + ctx.pageStartAddress[page];

        if (mapper != RomMapper.NO_OP_MAPPER && secSlotNumber > 0 && secSlotNumber < 3) {
            res = (int) mapper.readData(addressL, Size.BYTE);
        } else if (address < secondarySlot[secSlotNumber].length) {
            res = secondarySlot[secSlotNumber][address];
        } else {
            LOG.error("Unexpected read: {}, slot: {}", Long.toHexString(addressL), secSlotNumber);
        }
        return res;
    }

    @Override
    public Z80BusProvider attachDevice(Device device) {
        super.attachDevice(device);
        if (device instanceof IMemoryProvider) {
            secondarySlot[3] = this.memoryProvider.getRamData();
            secondarySlotWritable[3] = true;
        }
        return this;
    }

    @Override
    public void write(long addressL, long data, Size size) {
        int addressI = (int) (addressL & 0xFFFF);
        int page = addressI >> 14;
        int secSlotNumber = ctx.pageSlotMapper[page];
        if (secondarySlotWritable[secSlotNumber]) {
            int address = (addressI & PAGE_MASK) + ctx.pageStartAddress[page];
            writeSlot(secondarySlot[secSlotNumber], address, (int) data);
        } else if (mapper != RomMapper.NO_OP_MAPPER && secSlotNumber > 0 && secSlotNumber < 3) {
            mapper.writeData(addressL, data, size);
        } else {
            LOG.error("Unexpected write: {}, data: {}, slot: {}", Long.toHexString(addressL),
                    Long.toHexString(data), secSlotNumber);
        }
    }

    private void writeSlot(int[] data, int address, int value) {
        if (address < data.length) {
            data[address] = value;
        }
    }

    @Override
    public void writeIoPort(int port, int value) {
        port &= 0xFF;
        byte byteVal = (byte) (value & 0XFF);
        LogHelper.printLevel(LOG, Level.INFO, "Write IO port: {}, value: {}", port, value, verbose);
        switch (port) {
            case 0x80:
            case 0xC0: //MSX-AUDIO, RS232 - ignore
                break;
            case 0x98:
                vdpProvider.writeVRAMData(byteVal);
                break;
            case 0x99:
                vdpProvider.writeRegister(byteVal);
                break;
            case 0xA0:
                LOG.debug("Write PSG register select: {}", value);
                ctx.psgAddressLatch = value;
                break;
            case 0xA1:
                LOG.debug("Write PSG: {}", value);
                soundProvider.getPsg().write(ctx.psgAddressLatch, value);
                if (ctx.psgAddressLatch == 0xF) {
                    joypadSelect = (value & 0x40) == 0x40 ? P2 : P1;
                    LOG.debug("Write PSG register {}, data {}", ctx.psgAddressLatch, value);
                }
                break;
            case 0xA8:
                setSlotSelect(value);
                break;
            case 0xAA: // Write PPI register C (keyboard and cassette interface) (port AA)
                ctx.ppiC_Keyboard = value;
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
                res = ctx.psgAddressLatch;
                break;
            case 0xA2:
                res = soundProvider.getPsg().read(ctx.psgAddressLatch);
                if (ctx.psgAddressLatch == 0xE) {
                    res = readJoyData();
                    LOG.debug("Read PSG register {}, data {}", ctx.psgAddressLatch, res);
                }
                break;
            case 0xA8:
                res = ctx.slotSelect;
                break;
            case 0xA9: //Read PPI register B (keyboard matrix row input register)
                res = MsxKeyboardInput.getMsxKeyAdapter().getRowValue(ctx.ppiC_Keyboard & 0xF);
                res = ~((byte) res);
                break;
            case 0xAA: //Read PPI register C (keyboard and cassette interface) (port AA)
                res = ctx.ppiC_Keyboard;
                break;
            default:
                LOG.warn("inPort: {}", Integer.toHexString(port & 0xFF));
                break;

        }
        res &= 0xFF;
        LogHelper.printLevel(LOG, Level.INFO, "Read IO port: {}, value: {}", port, res, verbose);
        return res;
    }

    private void setSlotSelect(int value) {
        ctx.slotSelect = value;
        reloadPageSlotMapper();
    }

    private void reloadPageSlotMapper() {
        ctx.pageSlotMapper[0] = ctx.slotSelect & 3;
        ctx.pageSlotMapper[1] = (ctx.slotSelect & 0xC) >> 2;
        ctx.pageSlotMapper[2] = (ctx.slotSelect & 0x30) >> 4;
        ctx.pageSlotMapper[3] = (ctx.slotSelect & 0xC0) >> 6;
    }

    @Override
    public void init() {
        setupCartHw();
        int len = memoryProvider.getRomSize();
        secondarySlot[1] = memoryProvider.getRomData();
        if (len > PAGE_SIZE) {
            secondarySlot[2] = memoryProvider.getRomData();
            ctx.pageStartAddress[2] = PAGE_SIZE;
        }
    }

    private int readJoyData() {
        int res = 0xFF;
        switch (joypadSelect) {
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

    private void setupCartHw() {
        int len = memoryProvider.getRomSize();
        this.cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memoryProvider, systemProvider.getRomPath());
        MapperSelector.Entry e = MapperSelector.getMapperData(systemProvider.getSystemType(), cartridgeInfoProvider.getSha1());
        if (e != MapperSelector.MISSING_DATA) {
            LOG.info("Cart Hw match:\n{}", e);
            mapper = MsxMapper.getMapper(e.mapperName, memoryProvider);
            LOG.info("ROM size: {}, using mapper: {}", len, mapper.getClass().getSimpleName());
        } else {
            LOG.info("Unknown rom sha1: {}", cartridgeInfoProvider.getSha1());
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        buffer.put((byte) ctx.psgAddressLatch);
        buffer.put((byte) ctx.slotSelect);
        buffer.put((byte) ctx.ppiC_Keyboard);
        StateUtil.setData(buffer, ctx.pageSlotMapper);
        StateUtil.setData(buffer, ctx.pageStartAddress);
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        ctx.psgAddressLatch = buffer.get() & 0xFF;
        ctx.slotSelect = buffer.get() & 0xFF;
        ctx.ppiC_Keyboard = buffer.get() & 0xFF;
        for (int i = 0; i < ctx.pageSlotMapper.length; i++) {
            ctx.pageSlotMapper[i] = buffer.getInt();
        }
        for (int i = 0; i < ctx.pageStartAddress.length; i++) {
            ctx.pageStartAddress[i] = buffer.getInt();
        }
        setSlotSelect(ctx.slotSelect);
    }

    public static class MsxBusContext implements Serializable {
        public int psgAddressLatch = 0;
        public int slotSelect = 0;
        public int ppiC_Keyboard = 0;
        public int[] pageStartAddress = {0, 0, 0, 0};
        public int[] pageSlotMapper = {0, 0, 0, 0};
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
