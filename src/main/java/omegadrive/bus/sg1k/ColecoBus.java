package omegadrive.bus.sg1k;

import omegadrive.Device;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.util.FileLoader;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.vdp.Sg1000Vdp;
import org.apache.logging.log4j.Level;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 * <p>
 * https://atarihq.com/danb/files/CV-Tech.txt
 * http://www.smspower.org/forums/9920-ColecoNMIEmulationWasMekaBugAndFix
 */
public class ColecoBus extends DeviceAwareBus implements Sg1000BusProvider {

    static final boolean verbose = false;

    private static int BIOS_START = 0;
    private static int BIOS_END = 0x1FFF;
    private static int RAM_START = 0x6000;
    private static int RAM_END = 0x7FFF;
    private static int ROM_START = 0x8000;
    private static int ROM_END = 0xFFFF;

    private static int RAM_SIZE = 0x400;  //1Kb
    private static int ROM_SIZE = ROM_END + 1; //48kb

    public Sg1000Vdp vdp;
    private int[] bios;

    private String biosPath = "./bios";
    private String biosName = "bios_coleco.col";

    private boolean isNmiSet = false;


    public ColecoBus() {
        Path p = Paths.get(biosPath, biosName);
        bios = FileLoader.readFileSafe(p);
        LOG.info("Loading Coleco bios from: " + p.toAbsolutePath().toString());
    }

    @Override
    public Sg1000BusProvider attachDevice(Device device) {
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
        if (address <= BIOS_END) {
            return bios[address];
        } else if (address >= RAM_START && address <= RAM_END) {
            address &= RAM_SIZE - 1;
            return memoryProvider.readRamByte(address);
        } else if (address >= ROM_START && address <= ROM_END) {
            address = (address - ROM_START);// & (rom.length - 1);
            return memoryProvider.readRomByte(address);
        }
        LOG.error("Unexpected Z80 memory read: " + Long.toHexString(address));
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
                vdp.writeVRAMData(byteVal);
                break;
            case 0xA1:
                //                LOG.warn("write: vdp address: {}", Integer.toHexString(value));
                vdp.writeRegister(byteVal);
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
                return vdp.readVRAMData();
            case 0xA1:
                //                LOG.warn("read: vdp status reg");
                return vdp.readStatus();
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
    public void closeRom() {

    }

    @Override
    public void newFrame() {
        joypadProvider.newFrame();
    }

    @Override
    public void handleVdpInterruptsZ80() {
        boolean set = vdp.getStatusINT() && vdp.getGINT();
        //do not re-trigger
        if (set && !isNmiSet) {
            z80Provider.triggerNMI();
        }
        isNmiSet = set;
    }
}
