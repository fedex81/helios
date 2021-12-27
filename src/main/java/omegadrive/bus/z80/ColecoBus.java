/*
 * ColecoBus
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

import omegadrive.SystemLoader;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.bus.model.Z80BusProvider;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.joypad.ColecoPad;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.Tms9918aVdp;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * see
 * https://atarihq.com/danb/files/CV-Tech.txt
 * http://www.smspower.org/forums/9920-ColecoNMIEmulationWasMekaBugAndFix
 */
public class ColecoBus extends DeviceAwareBus<Tms9918aVdp, ColecoPad> implements Z80BusProvider {

    private static final Logger LOG = LogManager.getLogger(ColecoBus.class);

    static final boolean verbose = false;

    private static final int BIOS_START = 0;
    private static final int BIOS_END = 0x1FFF;
    private static final int RAM_START = 0x6000;
    private static final int RAM_END = 0x7FFF;
    private static final int ROM_START = 0x8000;
    private static final int ROM_END = 0xFFFF;

    private static final int RAM_SIZE = 0x400;  //1Kb
    private static final int ROM_SIZE = ROM_END + 1; //48kb

    private final int[] bios;

    private boolean isNmiSet = false;

    public ColecoBus() {
        Path p = Paths.get(SystemLoader.biosFolder, SystemLoader.biosNameColeco);
        bios = Util.toUnsignedIntArray(FileUtil.loadBiosFile(p));
        LOG.info("Loading Coleco bios from: {}", p.toAbsolutePath().toString());
    }

    @Override
    public long read(long addressL, Size size) {
        int address = (int) addressL;
        if (size != Size.BYTE) {
            LOG.error("Unexpected read, addr : {} , size: {}", address, size);
            return 0xFF;
        }
        if (address <= BIOS_END) {
            return bios[address];
        } else if (address >= RAM_START && address <= RAM_END) {
            address &= RAM_SIZE - 1;
            return memoryProvider.readRamByte(address);
        } else if (address >= ROM_START && address <= ROM_END) {
            address = (address - ROM_START);// & (rom.length - 1);
            return memoryProvider.readRomByte(address);
        }
        LOG.error("Unexpected Z80 memory read: {}", Long.toHexString(address));
        return 0xFF;
    }

    @Override
    public void write(long address, long data, Size size) {
        address &= RAM_SIZE - 1;
        memoryProvider.writeRamByte((int) address, (int) (data & 0xFF));
    }

    @Override
    public void writeIoPort(int port, int value) {
        port &= 0xFF;
        byte byteVal = (byte) (value & 0XFF);
        LogHelper.printLevel(LOG, Level.INFO, "Write port: {}, value: {}", port, value, verbose);
        switch (port & 0xE1) {
            case 0x80:
            case 0xC0:
                joypadProvider.writeDataRegister1(port);
                break;
            case 0xA0:
                //                LOG.warn("write vdp vram: {}", Integer.toHexString(value));
                vdpProvider.writeVRAMData(byteVal);
                break;
            case 0xA1:
                //                LOG.warn("write: vdp address: {}", Integer.toHexString(value));
                vdpProvider.writeRegister(byteVal);
                break;
            case 0xE1:
                soundProvider.getPsg().write(byteVal);
                break;
            default:
                LOG.warn("outPort: {} ,data {}", Integer.toHexString(port), Integer.toHexString(value));
                break;
        }
    }

    //see meka/coleco.cpp
    @Override
    public int readIoPort(int port) {
        port &= 0xFF;
        LogHelper.printLevel(LOG, Level.INFO, "Read port: {}", port, verbose);
        switch (port & 0xE1) {
            case 0xA0:
                //                LOG.warn("read: vdp vram");
                return vdpProvider.readVRAMData();
            case 0xA1:
                //                LOG.warn("read: vdp status reg");
                return vdpProvider.readStatus();
            case 0xE0:
                return joypadProvider.readDataRegister1();
            case 0xE1:
                return joypadProvider.readDataRegister2();
            default:
                LOG.warn("inPort: {}", Integer.toHexString(port & 0xFF));
                break;

        }
        return 0xFF;
    }

    @Override
    public void reset() {
        isNmiSet = false;
    }

    @Override
    public void onNewFrame() {
        joypadProvider.newFrame();
    }

    @Override
    public void handleInterrupts(Z80Provider.Interrupt type) {
        boolean set = vdpProvider.getStatusINT() && vdpProvider.getGINT();
        //do not re-trigger
        if (set && !isNmiSet) {
            z80Provider.triggerNMI();
        }
        isNmiSet = set;
    }
}
