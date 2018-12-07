package omegadrive.savestate;

import m68k.cpu.MC68000;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.VdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.z80.Z80Memory;
import omegadrive.z80.Z80Provider;
import omegadrive.z80.jsanchezv.Z80;
import omegadrive.z80.jsanchezv.Z80State;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.stream.IntStream;

import static omegadrive.util.Util.getUInt32;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class GstStateHandler implements GenesisStateHandler {

    private static Logger LOG = LogManager.getLogger(GstStateHandler.class.getSimpleName());

    private static int FM_REG_OFFSET = 0x1E4;
    private static int FM_DATA_SIZE = 0x200;
    private static int VDP_REG_OFFSET = 0xFA;
    private static int CRAM_DATA_OFFSET = 0x112;
    private static int VRAM_DATA_OFFSET = 0x12478;
    private static int VSRAM_DATA_OFFSET = 0x192;
    private static int Z80_RAM_DATA_OFFSET = 0x474;
    private static int M68K_RAM_DATA_OFFSET = 0x2478;
    private static int M68K_REGD_OFFSET = 0x80;
    private static int M68K_REGA_OFFSET = 0xA0;

    private static String MAGIC_WORD = "GST";

    private int[] data;
    private int version;
    private int softwareId;
    private String fileName;

    private GstStateHandler() {
    }

    public static GenesisStateHandler createInstance(String fileName, int[] stateData) {
        GstStateHandler h = new GstStateHandler();
        h.data = stateData;
        h.fileName = fileName;
        GenesisStateHandler res = h.detectStateFileType();
        return res;
    }

    private GenesisStateHandler detectStateFileType() {
        String fileType = Util.toStringValue(data[0], data[1], data[2]);
        if (!MAGIC_WORD.equalsIgnoreCase(fileType)) {
            LOG.error("Unable to load save state of type: " + MAGIC_WORD);
            return GenesisStateHandler.EMPTY_STATE;
        }
        version = data[0x50];
        softwareId = data[0x51];
        LOG.info("Savestate version: {}, softwareId: {}", version, softwareId);
        return this;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public void loadFmState(FmProvider fm) {
        int i;
        fm.reset();
        int limit = FM_DATA_SIZE / 2;

        for (i = 0; i < limit; i++) {
            int address = i;
            fm.write0(address, data[FM_REG_OFFSET + i]);
            fm.write1(address, data[FM_REG_OFFSET + limit + i]);
        }
    }

    @Override
    public void loadVdpState(VdpProvider vdp) {
        loadVdpMemory(vdp.getVdpMemory());
        IntStream.range(0, 24).forEach(i -> vdp.updateRegisterData(i, data[i + VDP_REG_OFFSET] & 0xFF));
    }

    private void loadVdpMemory(VdpMemoryInterface vdpMemoryInterface) {
        for (int i = 0; i < VdpProvider.VDP_VRAM_SIZE; i += 2) {
            vdpMemoryInterface.writeVramByte(i, data[i + VRAM_DATA_OFFSET]);
            vdpMemoryInterface.writeVramByte(i + 1, data[i + VRAM_DATA_OFFSET + 1]);
        }
        for (int i = 0; i < VdpProvider.VDP_CRAM_SIZE; i += 2) {
            vdpMemoryInterface.writeCramByte(i, data[i + CRAM_DATA_OFFSET + 1]);
            vdpMemoryInterface.writeCramByte(i + 1, data[i + CRAM_DATA_OFFSET]);
        }

        for (int i = 0; i < VdpProvider.VDP_VSRAM_SIZE; i += 2) {
            vdpMemoryInterface.writeVsramByte(i, data[i + VSRAM_DATA_OFFSET]);
            vdpMemoryInterface.writeVsramByte(i + 1, data[i + VSRAM_DATA_OFFSET + 1]);
        }
    }

    @Override
    public void loadZ80(Z80Provider z80) {
        Z80State z80State = loadZ80State(data);

        IntStream.range(0, Z80Memory.MEMORY_SIZE).forEach(
                i -> z80.writeMemory(i, data[i + Z80_RAM_DATA_OFFSET], Size.BYTE));
        z80.unrequestBus();
        z80.disableReset();

        boolean isReset = data[0x438] > 0;
        boolean isBusReq = data[0x439] > 0;
        if (isBusReq) {
            z80.requestBus();
        }
        if (isReset) {
            LOG.warn("Z80 should be reset, not doing it!");
//            z80.reset();
        }
        int z80BankInt = getUInt32(Arrays.copyOfRange(data, 0x43C, 0x43C + 4));
        z80.getZ80Memory().setRomBank68kSerial(z80BankInt);
        z80.loadZ80State(z80State);

    }

    private static Z80State loadZ80State(int[] data) {
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
        z80State.setIM(Z80.IntMode.IM1);
        boolean iffN = data[0x436] > 0;
        z80State.setIFF1(iffN);
        z80State.setIFF2(iffN);
        return z80State;
    }

    //TODO should use M68kProvider
    @Override
    public void load68k(MC68000Wrapper m68kProvider, MemoryProvider memoryProvider) {
        for (int i = 0; i < MemoryProvider.M68K_RAM_SIZE; i += 2) {
            memoryProvider.writeRamByte(i, data[i + M68K_RAM_DATA_OFFSET]);
            memoryProvider.writeRamByte(i + 1, data[i + M68K_RAM_DATA_OFFSET + 1]);
        }

        MC68000 m68k = m68kProvider.getM68k();
        m68k.setSR(getUInt32(Arrays.copyOfRange(data, 0xD0, 0xD0 + 2)));
        IntStream.range(0, 8).forEach(i -> m68k.setDataRegisterLong(i,
                getUInt32(Arrays.copyOfRange(data, M68K_REGD_OFFSET + i * 4, M68K_REGD_OFFSET + (1 + i) * 4))));
        IntStream.range(0, 8).forEach(i -> m68k.setAddrRegisterLong(i,
                getUInt32(Arrays.copyOfRange(data, M68K_REGA_OFFSET + i * 4, M68K_REGA_OFFSET + (1 + i) * 4))));
        m68k.setPC(getUInt32(Arrays.copyOfRange(data, 0xC8, 0xC8 + 4)));

        int ssp = getUInt32(Arrays.copyOfRange(data, 0xD2, 0xD2 + 2));
        int usp = getUInt32(Arrays.copyOfRange(data, 0xD6, 0xD6 + 2));
        if (usp > 0) {
            LOG.warn("USP is not 0: " + usp);
        }
        if (ssp > 0) {
            LOG.warn("SSP is not 0: " + ssp);
        }
    }

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
}
