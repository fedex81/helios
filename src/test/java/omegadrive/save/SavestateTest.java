package omegadrive.save;

import m68k.cpu.MC68000;
import omegadrive.Genesis;
import omegadrive.bus.BusProvider;
import omegadrive.joypad.GenesisJoypad;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.GenesisMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.vdp.GenesisVdpMemoryInterface;
import omegadrive.vdp.GenesisVdpNew;
import omegadrive.vdp.VdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Memory;
import omegadrive.z80.jsanchezv.Z80;
import omegadrive.z80.jsanchezv.Z80State;
import org.junit.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class SavestateTest {
    /*
GST genecyst save file
Range        Size   Description
-----------  -----  -----------
00000-00002  3      "GST"
00006-00007  2      "\xE0\x40"
000FA-00112  24     VDP registers
00112-00191  128    Color RAM
00192-001E1  80     Vertical scroll RAM
001E4-003E3  512    YM2612 registers
00474-02473  8192   Z80 RAM
02478-12477  65536  68K RAM
12478-22477  65536  Video RAM

main 68000 registers
--------------------
00080-0009F : D0-D7
000A0-000BF : A0-A7
000C8 : PC
000D0 : SR
000D2 : USP
000D6 : SSP

Z80 registers
-------------
00404 : AF
00408 : BC
0040C : DE
00410 : HL
00414 : IX
00418 : IY
0041C : PC
00420 : SP
00424 : AF'
00428 : BC'
0042C : DE'
00430 : HL'
00434 : I
00435 : Unknow
00436 : IFF1 = IFF2
00437 : Unknow
The 'R' register is not supported.
Z80 State
---------
00438 : Z80 RESET
00439 : Z80 BUSREQ
0043A : Unknow0
0043B : Unknow
0043C : Z80 BANK (DWORD)*/

    public static void main(String[] args) throws Exception {
        Path p = Paths.get(".", "test01.gs0");
        System.out.println(p.toAbsolutePath().toString());
        byte[] data = Files.readAllBytes(p);
        load(data);
    }

    private static void load(byte[] data) throws Exception {
        String fileType = toStringValue(Arrays.copyOfRange(data, 0, 3));
        Assert.assertEquals("GST", fileType);
        System.out.println("File type: " + fileType);
        System.out.println("Version: " + data[0x50]);
        System.out.println("SwId: " + data[0x51]);
        BusProvider busProvider = BusProvider.createBus();
        MemoryProvider memoryProvider = new GenesisMemoryProvider();
        busProvider.attachDevice(memoryProvider);

        VdpProvider vdp1 = loadVdp(busProvider, data);
        Z80CoreWrapper z80w = loadZ80(busProvider, data);
        MC68000Wrapper m68kw = load68k(busProvider, memoryProvider, data);

        String rom = "/home/fede/roms/Sonic The Hedgehog (W) (REV00) [!].bin";
        Genesis genesis = new Genesis(false) {

            @Override
            public void init() {
                joypad = new GenesisJoypad();
//                inputProvider = InputProvider.createInstance(joypad);

                memory = memoryProvider;
                bus = busProvider;
                vdp = vdp1;
                cpu = m68kw;
                z80 = z80w;
                //sound attached later
                sound = SoundProvider.NO_SOUND;
                bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                        attachDevice(cpu).attachDevice(z80).attachDevice(sound);
            }

            @Override
            protected void resetAfterGameLoad() {
                bus.reset();
                ((GenesisVdpNew) vdp).initMode();
            }
        };
        genesis.handleNewGame(Paths.get(rom));
    }


//    Range    Size   Description
//-----------  -----  -----------
//000FA-00112  24     VDP registers

    private static VdpProvider loadVdp(BusProvider busProvider, byte[] data) {
        VdpMemoryInterface memoryInterface = loadVdpMemory(data);
        GenesisVdpNew vdp = GenesisVdpNew.createInstance(busProvider, memoryInterface);
        int vdpRegOffset = 0xFA;
        IntStream.range(0, 24).forEach(i -> vdp.updateRegisterData(i, data[i + vdpRegOffset] & 0xFF));
        return vdp;
    }


    /**
     * main 68000 registers
     * --------------------
     * 00080-0009F : D0-D7
     * 000A0-000BF : A0-A7
     * 000C8 : PC
     * 000D0 : SR
     * 000D2 : USP
     * 000D6 : SSP
     */
    private static MC68000Wrapper load68k(BusProvider provider, MemoryProvider memoryProvider, byte[] data) {
        MC68000Wrapper mc68000Wrapper = new MC68000Wrapper(provider);
        int m68kRamOffset = 0x2478;
        for (int i = 0; i < MemoryProvider.M68K_RAM_SIZE; i++) {
            memoryProvider.writeRamByte(i, data[i + m68kRamOffset + 1]);
            memoryProvider.writeRamByte(i + 1, data[i + m68kRamOffset]);
        }

        MC68000 m68k = mc68000Wrapper.getM68k();
        int regDOffset = 0x80;
        int regAOffset = 0xA0;
        IntStream.range(0, 8).forEach(i -> m68k.setDataRegisterLong(i,
                getUInt32(Arrays.copyOfRange(data, regDOffset + i * 4, regDOffset + (1 + i) * 4))));
        IntStream.range(0, 8).forEach(i -> m68k.setAddrRegisterLong(i,
                getUInt32(Arrays.copyOfRange(data, regAOffset + i * 4, regAOffset + (1 + i) * 4))));
        m68k.setPC(getUInt32(Arrays.copyOfRange(data, 0xC8, 0xC8 + 4)));
        m68k.setSR(getUInt32(Arrays.copyOfRange(data, 0xD0, 0xD0 + 2)));
        m68k.setUSP(getUInt32(Arrays.copyOfRange(data, 0xD2, 0xD2 + 2)));
        m68k.setSSP(getUInt32(Arrays.copyOfRange(data, 0xD6, 0xD6 + 2)));

        return mc68000Wrapper;
    }

    private static int getUInt32(byte... bytes) {
        int value = (bytes[0] & 0xFF) << 0;
        value = bytes.length > 1 ? value | ((bytes[1] & 0xFF) << 8) : value;
        value = bytes.length > 2 ? value | ((bytes[2] & 0xFF) << 16) : value;
        value = bytes.length > 3 ? value | ((bytes[3] & 0xFF) << 24) : value;
        return value;
    }

    private static String toStringValue(byte[] data) {
        String value = "";
        for (int i = 0; i < data.length; i++) {
            value += (char) (data[i] & 0xFF);
        }
        return value;
    }

    private static Z80CoreWrapper loadZ80(BusProvider provider, byte[] data) {
        Z80State z80State = loadZ80State(data);
        Z80Memory memory = new Z80Memory(provider);
        int z80RamOffset = 0x474;
        IntStream.range(0, Z80Memory.MEMORY_SIZE).forEach(i -> memory.writeByte(i, data[i + z80RamOffset]));
        Z80CoreWrapper coreWrapper = Z80CoreWrapper.createInstance(memory, z80State);
        coreWrapper.initialize();
        boolean isReset = data[0x438] > 0;
        boolean isBusReq = data[0x439] > 0;

        if (isBusReq) {
            coreWrapper.requestBus();
        }
        if (!isReset) {
            coreWrapper.disableReset();
        }
        int z80BankInt = getUInt32(Arrays.copyOfRange(data, 0x43C, 0x43C + 4));
        memory.setRomBank68kSerial(z80BankInt);
        return coreWrapper;
    }

    private static Z80State loadZ80State(byte[] data) {
        Z80State z80State = new Z80State();
        z80State.setRegAF(getUInt32(data[0x404], data[0x405]));
        z80State.setRegBC(getUInt32(data[0x408], data[0x409]));
        z80State.setRegDE(getUInt32(data[0x40C], data[0x40D]));
        z80State.setRegHL(getUInt32(data[0x410], data[0x411]));
        z80State.setRegIX(getUInt32(data[0x414], data[0x415]));
        z80State.setRegIY(getUInt32(data[0x418], data[0x419]));
        z80State.setRegPC(getUInt32(data[0x41C], data[0x41D]));
        z80State.setRegSP(getUInt32(data[0x420], data[0x421]));
        z80State.setRegAFx(getUInt32(data[0x424], data[0x424]));
        z80State.setRegBCx(getUInt32(data[0x428], data[0x428]));
        z80State.setRegDEx(getUInt32(data[0x42C], data[0x42D]));
        z80State.setRegHLx(getUInt32(data[0x430], data[0x431]));
        z80State.setIM(data[0x434] == 0 ? Z80.IntMode.IM0 : Z80.IntMode.IM1);
        z80State.setIFF1(data[0x436] > 0);
        z80State.setIFF2(data[0x436] > 0);
        return z80State;
    }

    private static GenesisVdpMemoryInterface loadVdpMemory(byte[] data) {
        int[] vram = new int[VdpProvider.VDP_VRAM_SIZE];
        int[] cram = new int[VdpProvider.VDP_CRAM_SIZE];
        int[] vsram = new int[VdpProvider.VDP_VSRAM_SIZE];

        int cramOffset = 0x112;
        int vramOffset = 0x12478;
        int vsramOffset = 0x192;

        IntStream.range(0, cram.length).forEach(i -> cram[i] = data[i + cramOffset]);
        IntStream.range(0, vsram.length).forEach(i -> vsram[i] = data[i + vsramOffset]);
        for (int i = 0; i < vram.length; i += 2) {
            vram[i] = data[i + vramOffset + 1];
            vram[i + 1] = data[i + vramOffset];
        }
        return GenesisVdpMemoryInterface.createInstance(vram, cram, vsram);
    }
}
