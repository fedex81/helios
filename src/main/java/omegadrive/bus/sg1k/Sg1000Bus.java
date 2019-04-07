/*
 * Sg1000Bus
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

import omegadrive.Device;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.Size;
import omegadrive.vdp.Sg1000Vdp;

public class Sg1000Bus extends DeviceAwareBus implements Sg1000BusProvider {

    private static int ROM_START = 0;
    private static int ROM_END = 0xBFFF;
    private static int RAM_START = 0xC000;
    private static int RAM_END = 0xFFFF;

    private static int RAM_SIZE = 0x400;  //1Kb
    private static int ROM_SIZE = ROM_END + 1; //48kb

    public Sg1000Vdp vdp;

    private int lastDE;
    private int[] rom;
    private int[] ram;

    @Override
    public Sg1000BusProvider attachDevice(Device device) {
        if (device instanceof IMemoryProvider) {
            IMemoryProvider memory = (IMemoryProvider) device;
            this.rom = memory.getRomData();
            this.ram = memory.getRamData();
        }
        if (device instanceof Sg1000Vdp) {
            this.vdp = (Sg1000Vdp) device;
        }
        super.attachDevice(device);
        return this;
    }

    @Override
    public long read(long addressL, Size size) {
        int address = (int) addressL;
        if (size != Size.BYTE) {
            LOG.error("Unexpected read, addr : {} , size: {}", address, size);
            return 0xFF;
        }
        if (address <= ROM_END) {
            return memoryProvider.readRomByte(address);
        } else if (address >= RAM_START && address <= RAM_END) {
            address &= RAM_SIZE - 1;
            return memoryProvider.readRamByte(address);
        }
        LOG.error("Unexpected Z80 memory read: " + Long.toHexString(address));
        return 0xFF;
    }

    @Override
    public void write(long address, long data, Size size) {
        address &= RAM_SIZE - 1;
        memoryProvider.writeRamByte((int) address, (int) (data & 0xFF));
    }

    /**
     * https://www.geeksforgeeks.org/programmable-peripheral-interface-8255/
     * <p>
     * $7E = V counter (read) / SN76489 data (write)
     * $7F = H counter (read) / SN76489 data (write, mirror)
     * $BE = Data port (r/w)
     * $BF = Control port (r/w)
     * <p>
     * The address decoding for the I/O ports is done with A7, A6, and A0 of
     * the Z80 address bus, so the VDP locations are mirrored:
     * <p>
     * $40-7F = Even locations are V counter/PSG, odd locations are H counter/PSG
     * $80-BF = Even locations are data port, odd locations are control port.
     * <p>
     * A7 A6 A0
     * 0  0  0 NONE ?
     * 0  0  1 NONE ?
     * 0  1  0 -> V counter (read) / SN76489 data (write)
     * 0  1  1 -> H counter (read) / SN76489 data (write, mirror)
     * 1  0  0 -> Data port (r/w)
     * 1  0  1 -> Control port (r/w)
     * 1  1  0 -> joy 1 (read)
     * 1  1  1 -> joy 2 (read)
     */
    @Override
    public void writeIoPort(int port, int value) {
        port &= 0xFF;
        byte byteVal = (byte) (value & 0XFF);
//        LOG.info("Write port: {}, value: {}", Integer.toHexString(port), Integer.toHexString(value));
        switch (port & 0xC1) {
            case 0x40:
            case 0x41:
                soundProvider.getPsg().write(byteVal);
                break;
            case 0x80:
                //                LOG.warn("write vdp vram: {}", Integer.toHexString(value));
                vdp.writeVRAMData(byteVal);
                break;
            case 0x81:
                //                LOG.warn("write: vdp address: {}", Integer.toHexString(value));
                vdp.writeRegister(byteVal);
                break;
            case 0xC0: //aka $DE
                if (port == 0xDE) {
                    LOG.debug("write 0xDE: {}", Integer.toHexString(value & 0xFF));
                    lastDE = value;
                }
                break;
            case 0xC1: //aka $DF
                if (port == 0xDF) {
                    LOG.warn("write 0xDF: {}", Integer.toHexString(value & 0xFF));
                }
                break;
            default:
                LOG.warn("outPort: {} ,data {}", Integer.toHexString(port), Integer.toHexString(value));
                break;
        }
    }

    @Override
    public int readIoPort(int port) {
        port &= 0xFF;
//        LOG.info("Read port: {}", Integer.toHexString(port));
        switch (port & 0xC1) {
            case 0x40:
                LOG.warn("VCounter read");
                break;
            case 0x41:
                LOG.warn("HCounter read");
                break;
            case 0x80:
                //                LOG.warn("read: vdp vram");
                return vdp.readVRAMData();
            case 0x81:
                //                LOG.warn("read: vdp status reg");
                return vdp.readStatus();
            case 0xC0:
                if (port == 0xDE) {
                    LOG.warn("read 0xDE: {}", Integer.toHexString(lastDE));
//                    return lastDE;
                }
                LOG.debug("read: joy1");
                return joypadProvider.readDataRegister1();
            case 0xC1:
                // The PPI control register cannot be read, and always
                // returns $FF.
                if (port == 0xDF) {
                    LOG.warn("read 0xDF");
//                    return 0xFF;
                }
                LOG.debug("read: joy2");
                return joypadProvider.readDataRegister2();
            default:
                LOG.warn("inPort: {}", Integer.toHexString(port & 0xFF));
                break;

        }
        return 0xFF;
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
        boolean set = vdp.getStatusINT() && vdp.getGINT();
        z80Provider.interrupt(set);
//        if(prev != set){
//            LOG.info(vdp.getInterruptHandler().getStateString("Vint: " + set));
//            prev = set;
//        }
    }
}
