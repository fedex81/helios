/*
 * GstStateHandler
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

package omegadrive.savestate;

import m68k.cpu.MC68000;
import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.bus.gen.GenesisZ80BusProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.z80.Z80Memory;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.Z80;
import z80core.Z80State;

import java.util.Arrays;
import java.util.stream.IntStream;

import static omegadrive.util.Util.getUInt32;

public class GstStateHandler implements GenesisStateHandler {

    private static Logger LOG = LogManager.getLogger(GstStateHandler.class.getSimpleName());

    private static int FM_REG_OFFSET = 0x1E4;
    public static int FM_DATA_SIZE = 0x200;
    private static int VDP_REG_OFFSET = 0xFA;
    private static int CRAM_DATA_OFFSET = 0x112;
    private static int VRAM_DATA_OFFSET = 0x12478;
    private static int VSRAM_DATA_OFFSET = 0x192;
    private static int Z80_RAM_DATA_OFFSET = 0x474;
    private static int M68K_RAM_DATA_OFFSET = 0x2478;
    private static int M68K_REGD_OFFSET = 0x80;
    private static int M68K_REGA_OFFSET = 0xA0;
    private static int FILE_SIZE = 0x22478;

    private static String MAGIC_WORD = "GST";

    private int[] data;
    private int version;
    private int softwareId;
    private String fileName;
    private Type type;


    private GstStateHandler() {
    }

    public static GenesisStateHandler createLoadInstance(String fileName, int[] stateData) {
        GstStateHandler h = new GstStateHandler();
        h.data = stateData;
        h.fileName = fileName;
        h.type = Type.LOAD;
        GenesisStateHandler res = h.detectStateFileType();
        return res;
    }

    public static GenesisStateHandler createSaveInstance(String fileName) {
        GstStateHandler h = new GstStateHandler();
        h.data = new int[FILE_SIZE];
        //file type
        h.data[0] = 'G';
        h.data[1] = 'S';
        h.data[2] = 'T';
        //special Genecyst stuff
        h.data[6] = 0xE0;
        h.data[7] = 0x40;
        h.fileName = fileName;
        h.type = Type.SAVE;
        return h;
    }

    private GenesisStateHandler detectStateFileType() {
        String fileType = Util.toStringValue(data[0], data[1], data[2]);
        if (!MAGIC_WORD.equalsIgnoreCase(fileType) || data.length < FILE_SIZE) {
            LOG.error("Unable to load save state of type: {}, size: {}", MAGIC_WORD, data.length);
            return GenesisStateHandler.EMPTY_STATE;
        }
        version = data[0x50];
        softwareId = data[0x51];
        LOG.info("Savestate version: {}, softwareId: {}", version, softwareId);
        return this;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public int[] getData() {
        return data;
    }

    @Override
    public void loadFmState(FmProvider fm) {
        int i;
        fm.reset();
        int limit = FM_DATA_SIZE / 2;

        for (i = 0; i < limit; i++) {
            fm.write(FmProvider.FM_ADDRESS_PORT0, i);
            fm.write(FmProvider.FM_DATA_PORT0, data[FM_REG_OFFSET + i]);
            fm.write(FmProvider.FM_ADDRESS_PORT1, i);
            fm.write(FmProvider.FM_DATA_PORT1, data[FM_REG_OFFSET + limit + i]);
        }
    }

    @Override
    public void loadVdpState(BaseVdpProvider vdp) {
        loadVdpMemory(vdp.getVdpMemory());
        IntStream.range(0, BaseVdpProvider.VDP_REGISTERS_SIZE).forEach(i -> vdp.updateRegisterData(i, data[i + VDP_REG_OFFSET] & 0xFF));
        vdp.reload();
    }

    private void loadVdpMemory(VdpMemoryInterface vdpMemoryInterface) {
        for (int i = 0; i < BaseVdpProvider.VDP_VRAM_SIZE; i += 2) {
            vdpMemoryInterface.writeVramByte(i, data[i + VRAM_DATA_OFFSET]);
            vdpMemoryInterface.writeVramByte(i + 1, data[i + VRAM_DATA_OFFSET + 1]);
        }
        for (int i = 0; i < BaseVdpProvider.VDP_CRAM_SIZE; i += 2) {
            vdpMemoryInterface.writeCramByte(i, data[i + CRAM_DATA_OFFSET + 1]);
            vdpMemoryInterface.writeCramByte(i + 1, data[i + CRAM_DATA_OFFSET]);
        }

        for (int i = 0; i < BaseVdpProvider.VDP_VSRAM_SIZE; i += 2) {
            vdpMemoryInterface.writeVsramByte(i, data[i + VSRAM_DATA_OFFSET]);
            vdpMemoryInterface.writeVsramByte(i + 1, data[i + VSRAM_DATA_OFFSET + 1]);
        }
    }

    @Override
    public void loadZ80(Z80Provider z80, GenesisBusProvider bus) {
        Z80State z80State = loadZ80State(data);

        IntStream.range(0, Z80Memory.Z80_RAM_MEMORY_SIZE).forEach(
                i -> z80.writeMemory(i, data[i + Z80_RAM_DATA_OFFSET]));

        bus.setZ80BusRequested(false);
        bus.setZ80ResetState(false);

        boolean isReset = data[0x438] > 0;
        boolean isBusReq = data[0x439] > 0;
        if (isBusReq) {
            bus.setZ80BusRequested(true);
        }
        if (isReset) {
            //TODO seems to break a lot of savestates
            LOG.warn("Z80 should be reset, not doing it!");
//            bus.setZ80ResetState(true);
        }
        int z80BankInt = getUInt32(Arrays.copyOfRange(data, 0x43C, 0x43C + 4));
        GenesisZ80BusProvider.setRomBank68kSerial(z80, z80BankInt);
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
    public void load68k(MC68000Wrapper m68kProvider, IMemoryProvider memoryProvider) {
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

    @Override
    public void saveFm(FmProvider fm) {
        int limit = FM_DATA_SIZE / 2;
        for (int i = 0; i < limit; i++) {
            data[FM_REG_OFFSET + i] = fm.readRegister(0, i);
            data[FM_REG_OFFSET + i + limit] = fm.readRegister(1, i);
        }
    }

    @Override
    public void saveVdp(BaseVdpProvider vdp) {
        VdpMemoryInterface vdpMemoryInterface = vdp.getVdpMemory();
        for (int i = 0; i < BaseVdpProvider.VDP_VRAM_SIZE; i += 2) {
            data[i + VRAM_DATA_OFFSET] = vdpMemoryInterface.readVramByte(i);
            data[i + VRAM_DATA_OFFSET + 1] = vdpMemoryInterface.readVramByte(i + 1);
        }
        for (int i = 0; i < BaseVdpProvider.VDP_CRAM_SIZE; i += 2) {
            data[i + CRAM_DATA_OFFSET + 1] = vdpMemoryInterface.readCramByte(i);
            data[i + CRAM_DATA_OFFSET] = vdpMemoryInterface.readCramByte(i + 1);
        }

        for (int i = 0; i < BaseVdpProvider.VDP_VSRAM_SIZE; i += 2) {
            data[i + VSRAM_DATA_OFFSET] = vdpMemoryInterface.readVsramByte(i);
            data[i + VSRAM_DATA_OFFSET + 1] = vdpMemoryInterface.readVsramByte(i + 1);
        }
        IntStream.range(0, 24).forEach(i -> data[i + VDP_REG_OFFSET] = vdp.getRegisterData(i));
    }

    @Override
    public void saveZ80(Z80Provider z80, GenesisBusProvider bus) {
        IntStream.range(0, Z80Memory.Z80_RAM_MEMORY_SIZE).forEach(
                i -> data[Z80_RAM_DATA_OFFSET + i] = z80.readMemory(i));
        data[0x438] = bus.isZ80ResetState() ? 1 : 0;
        data[0x439] = bus.isZ80BusRequested() ? 1 : 0;

        int romBankSerial = GenesisZ80BusProvider.getRomBank68kSerial(z80);
        if (romBankSerial >= 0) {
            Util.setUInt32(romBankSerial, data, 0x43C);
        }
        saveZ80State(z80.getZ80State());
    }

    private void saveZ80State(Z80State z80State) {
        Util.setUInt32(z80State.getRegAF(), data, 0x404);
        Util.setUInt32(z80State.getRegBC(), data, 0x408);
        Util.setUInt32(z80State.getRegDE(), data, 0x40C);
        Util.setUInt32(z80State.getRegHL(), data, 0x410);
        Util.setUInt32(z80State.getRegIX(), data, 0x414);
        Util.setUInt32(z80State.getRegIY(), data, 0x418);
        Util.setUInt32(z80State.getRegPC(), data, 0x41C);
        Util.setUInt32(z80State.getRegSP(), data, 0x420);
        Util.setUInt32(z80State.getRegAFx(), data, 0x424);
        Util.setUInt32(z80State.getRegBCx(), data, 0x428);
        Util.setUInt32(z80State.getRegDEx(), data, 0x42C);
        Util.setUInt32(z80State.getRegHLx(), data, 0x430);

        //TODO
        Util.setUInt32(z80State.isIFF1() ? 1 : 0, data, 0x436);
    }

    @Override
    public void save68k(MC68000Wrapper mc68000Wrapper, IMemoryProvider memoryProvider) {
        for (int i = 0; i < MemoryProvider.M68K_RAM_SIZE; i += 2) {
            data[i + M68K_RAM_DATA_OFFSET] = memoryProvider.readRamByte(i);
            data[i + M68K_RAM_DATA_OFFSET + 1] = memoryProvider.readRamByte(i + 1);
        }

        MC68000 m68k = mc68000Wrapper.getM68k();
        Util.setUInt32(m68k.getSR(), data, 0xD0);
        Util.setUInt32(m68k.getPC(), data, 0xC8);
        Util.setUInt32(m68k.getSSP(), data, 0xD2);
        Util.setUInt32(m68k.getUSP(), data, 0xD6);
        IntStream.range(0, 8).forEach(i -> Util.setUInt32(m68k.getDataRegisterLong(i), data, M68K_REGD_OFFSET + i * 4));
        IntStream.range(0, 8).forEach(i -> Util.setUInt32(m68k.getAddrRegisterLong(i), data, M68K_REGA_OFFSET + i * 4));
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
