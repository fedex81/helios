package omegadrive.bus.sg1k;

import omegadrive.SystemProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.util.FileLoader;
import omegadrive.util.Size;
import omegadrive.vdp.Sg1000Vdp;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.z80.Z80Provider;

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
public class ColecoBus implements Sg1000BusProvider {

    private static int BIOS_START = 0;
    private static int BIOS_END = 0x1FFF;
    private static int RAM_START = 0x6000;
    private static int RAM_END = 0x7FFF;
    private static int ROM_START = 0x8000;
    private static int ROM_END = 0xFFFF;

    private static int RAM_SIZE = 0x400;  //1Kb
    private static int ROM_SIZE = ROM_END + 1; //48kb

    private SoundProvider sound;
    private JoypadProvider joypadProvider;
    private Z80Provider z80;
    private IMemoryProvider memory;
    private SystemProvider sg1000;
    public Sg1000Vdp vdp;

    private boolean readPad;
    private int[] rom;
    private int[] ram;
    private int[] bios;

    private String biosPath = ".";
    private String biosName = "bios_coleco.col";
    private boolean isNmiSet = false;


    public ColecoBus() {
        Path p = Paths.get(biosPath, biosName);
        bios = FileLoader.readFileSafe(p);
        LOG.info("Loading Coleco bios from: " + p.toAbsolutePath().toString());
    }

    @Override
    public ColecoBus attachDevice(Object device) {
        if (device instanceof IMemoryProvider) {
            this.memory = (IMemoryProvider) device;
        }
        if (device instanceof SystemProvider) {
            this.sg1000 = (SystemProvider) device;
        }
        if (device instanceof JoypadProvider) {
            this.joypadProvider = (JoypadProvider) device;
        }
        if (device instanceof Z80Provider) {
            this.z80 = (Z80Provider) device;
        }
        if (device instanceof SoundProvider) {
            this.sound = (SoundProvider) device;
        }
        if (device instanceof Sg1000Vdp) {
            this.vdp = (Sg1000Vdp) device;
        }
        return this;
    }

    @Override
    public IMemoryProvider getMemory() {
        return memory;
    }

    @Override
    public JoypadProvider getJoypad() {
        return joypadProvider;
    }

    @Override
    public SoundProvider getSound() {
        return sound;
    }

    @Override
    public SystemProvider getEmulator() {
        return sg1000;
    }

    @Override
    public GenesisVdpProvider getVdp() {
        return null;
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
            return memory.readRamByte(address);
        } else if (address <= ROM_END) {
//            address &= rom.length - 1;
            address = (address - ROM_START);// & (rom.length - 1);
            return memory.readRomByte(address);
        }
        LOG.error("Unexpected Z80 memory read: " + Long.toHexString(address));
        return 0xFF;
    }

    @Override
    public void write(long address, long data, Size size) {
        address &= RAM_SIZE - 1;
        memory.writeRamByte((int) address, (int) (data & 0xFF));
    }

    @Override
    public void writeIoPort(int port, int value) {
        port &= 0xFF;
        byte byteVal = (byte) (value & 0XFF);
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
                sound.getPsg().write(byteVal);
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
//        LOG.info("Read port: {}", Integer.toHexString(port));
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
        this.rom = memory.getRomData();
        this.ram = memory.getRamData();
    }

    @Override
    public void closeRom() {

    }

    @Override
    public void newFrame() {
        joypadProvider.newFrame();
        frameTrigger = false;
    }


    private boolean frameTrigger = false;

    @Override
    public void handleVdpInterruptsZ80() {
        boolean set = vdp.getStatusINT() && vdp.getGINT();
        //do not re-trigger
        if (set && !isNmiSet && !frameTrigger) {
            z80.triggerNMI();
            frameTrigger = true;
        }
        isNmiSet = set;
    }
}
