/*
 * GstStateHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 20:41
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
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemory;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.Z80;
import z80core.Z80State;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;

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
    public static int FILE_SIZE = 0x22478;

    protected ByteBuffer buffer;
    protected int version;
    protected int softwareId;
    protected String fileName;
    protected Type type;
    protected boolean runAhead;

    byte[] arr4 = new byte[4], arr2 = new byte[2];

    private Function<Integer, Integer> getInt4Fn = pos -> {
        buffer.position(pos);
        buffer.get(arr4);
        return Util.getUInt32LE(arr4);
    };

    private Function<Integer, Integer> getInt2Fn = pos -> {
        buffer.position(pos);
        buffer.get(arr2);
        return Util.getUInt32LE(arr2);
    };

    private BiConsumer<Integer, Integer> setInt4LEFn = (pos, val) -> {
        Util.setUInt32LE(val, arr4, 0);
        buffer.position(pos);
        buffer.put(arr4);
    };

    private BiConsumer<Integer, Integer> setInt2LEFn = (pos, val) -> {
        buffer.put(pos, (byte) (val & 0xFF));
        buffer.put(pos + 1, (byte) ((val >> 8) & 0xFF));
    };

    protected GstStateHandler() {
    }

    public void setData(byte[] data) {
        this.buffer = ByteBuffer.wrap(data);
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
    public byte[] getData() {
        return buffer.array();
    }

    @Override
    public void loadFmState(FmProvider fm) {
        int reg;
        int limit = FM_DATA_SIZE / 2;
        buffer.position(FM_REG_OFFSET);
        for (reg = 0; reg < limit; reg++) {
            fm.write(MdFmProvider.FM_ADDRESS_PORT0, reg & 0xFF);
            fm.write(MdFmProvider.FM_DATA_PORT0, buffer.get(FM_REG_OFFSET + reg) & 0xFF);
            fm.write(MdFmProvider.FM_ADDRESS_PORT1, reg & 0xFF);
            fm.write(MdFmProvider.FM_DATA_PORT1, buffer.get(FM_REG_OFFSET + limit + reg) & 0xFF);
        }
    }

    @Override
    public void loadVdpState(BaseVdpProvider vdp) {
        loadVdpMemory(vdp.getVdpMemory());
        IntStream.range(0, GenesisVdpProvider.VDP_REGISTERS_SIZE).forEach(
                i -> vdp.updateRegisterData(i, buffer.get(i + VDP_REG_OFFSET) & 0xFF));
        vdp.reload();
    }

    private void loadVdpMemory(VdpMemory vdpMemoryInterface) {
        int[] vram = vdpMemoryInterface.getVram();
        for (int i = 0; i < GenesisVdpProvider.VDP_VRAM_SIZE; i += 2) {
            vram[i] = buffer.get(i + VRAM_DATA_OFFSET) & 0xFF;
            vram[i + 1] = buffer.get(i + VRAM_DATA_OFFSET + 1) & 0xFF;
        }
        //cram is swapped
        int[] cram = vdpMemoryInterface.getCram();
        for (int i = 0; i < GenesisVdpProvider.VDP_CRAM_SIZE; i += 2) {
            cram[i] = buffer.get(i + CRAM_DATA_OFFSET + 1) & 0xFF;
            cram[i + 1] = buffer.get(i + CRAM_DATA_OFFSET) & 0xFF;
        }

        int[] vsram = vdpMemoryInterface.getVsram();
        for (int i = 0; i < GenesisVdpProvider.VDP_VSRAM_SIZE; i += 2) {
            vsram[i] = buffer.get(i + VSRAM_DATA_OFFSET) & 0xFF;
            vsram[i + 1] = buffer.get(i + VSRAM_DATA_OFFSET + 1) & 0xFF;
        }
    }

    private Z80State loadZ80State() {
        Z80State z80State = new Z80State();
        z80State.setRegAF(getInt2Fn.apply(0x404));
        z80State.setRegBC(getInt2Fn.apply(0x408));
        z80State.setRegDE(getInt2Fn.apply(0x40C));
        z80State.setRegHL(getInt2Fn.apply(0x410));
        z80State.setRegIX(getInt2Fn.apply(0x414));
        z80State.setRegIY(getInt2Fn.apply(0x418));
        z80State.setRegPC(getInt2Fn.apply(0x41C));
        z80State.setRegSP(getInt2Fn.apply(0x420));
        z80State.setRegAFx(getInt2Fn.apply(0x424));
        z80State.setRegBCx(getInt2Fn.apply(0x428));
        z80State.setRegDEx(getInt2Fn.apply(0x42C));
        z80State.setRegHLx(getInt2Fn.apply(0x430));
        z80State.setIM(Z80.IntMode.IM1);
        boolean iffN = (buffer.get(0x436) & 0xFF) > 0;
        z80State.setIFF1(iffN);
        z80State.setIFF2(iffN);
        return z80State;
    }

    @Override
    public void loadZ80(Z80Provider z80, GenesisBusProvider bus) {
        int z80BankInt = getInt4Fn.apply(0x43C);
        GenesisZ80BusProvider.setRomBank68kSerial(z80, z80BankInt);

        bus.setZ80BusRequested((buffer.get(0x439) & 0xFF) > 0);
        bus.setZ80ResetState(false);

        boolean isReset = (buffer.get(0x438) & 0xFF) > 0;
        if (isReset && runAhead) {
            LOG.warn("Z80 should be reset, not doing it!");
            bus.setZ80ResetState(true);
            //TODO dont think this is needed?
            z80.reset();
        }

        IntStream.range(0, GenesisZ80BusProvider.Z80_RAM_MEMORY_SIZE).forEach(
                i -> z80.writeMemory(i, buffer.get(i + Z80_RAM_DATA_OFFSET) & 0xFF));

        Z80State z80State = loadZ80State();
        z80.loadZ80State(z80State);
    }


    //TODO should use M68kProvider
    @Override
    public void load68k(MC68000Wrapper m68kProvider, IMemoryProvider memoryProvider) {
        for (int i = 0; i < MemoryProvider.M68K_RAM_SIZE; i += 2) {
            memoryProvider.writeRamByte(i, buffer.get(i + M68K_RAM_DATA_OFFSET) & 0xFF);
            memoryProvider.writeRamByte(i + 1, buffer.get(i + M68K_RAM_DATA_OFFSET + 1) & 0xFF);
        }

        MC68000 m68k = m68kProvider.getM68k();
        m68k.setSR(getInt2Fn.apply(0xD0));
        IntStream.range(0, 8).forEach(i ->
                m68k.setDataRegisterLong(i, getInt4Fn.apply(M68K_REGD_OFFSET + i * 4))
        );

        IntStream.range(0, 8).forEach(i ->
                m68k.setAddrRegisterLong(i, getInt4Fn.apply(M68K_REGA_OFFSET + i * 4))
        );
        m68k.setPC(getInt4Fn.apply(0xC8));
        int ssp = getInt4Fn.apply(0xD2);
        int usp = getInt4Fn.apply(0xD6);
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
            buffer.put(FM_REG_OFFSET + i, (byte) fm.readRegister(0, i));
            buffer.put(FM_REG_OFFSET + i + limit, (byte) fm.readRegister(1, i));
        }
    }

    @Override
    public void saveVdp(BaseVdpProvider vdp) {
        VdpMemory vdpMemoryInterface = vdp.getVdpMemory();
        int[] vram = vdpMemoryInterface.getVram();
        for (int i = 0; i < GenesisVdpProvider.VDP_VRAM_SIZE; i += 2) {
            buffer.put(i + VRAM_DATA_OFFSET, (byte) vram[i]);
            buffer.put(i + VRAM_DATA_OFFSET + 1, (byte) vram[i + 1]);
        }
        int[] cram = vdpMemoryInterface.getCram();
        //cram is swapped
        for (int i = 0; i < GenesisVdpProvider.VDP_CRAM_SIZE; i += 2) {
            buffer.put(i + CRAM_DATA_OFFSET + 1, (byte) cram[i]);
            buffer.put(i + CRAM_DATA_OFFSET, (byte) cram[i + 1]);

        }

        int[] vsram = vdpMemoryInterface.getVsram();
        for (int i = 0; i < GenesisVdpProvider.VDP_VSRAM_SIZE; i += 2) {
            buffer.put(i + VSRAM_DATA_OFFSET, (byte) vsram[i]);
            buffer.put(i + VSRAM_DATA_OFFSET + 1, (byte) vsram[i + 1]);
        }
        IntStream.range(0, 24).forEach(i -> buffer.put(i + VDP_REG_OFFSET, (byte) vdp.getRegisterData(i)));
    }

    @Override
    public void saveZ80(Z80Provider z80, GenesisBusProvider bus) {
        IntStream.range(0, GenesisZ80BusProvider.Z80_RAM_MEMORY_SIZE).forEach(
                i -> buffer.put(Z80_RAM_DATA_OFFSET + i, (byte) z80.readMemory(i)));
        buffer.put(0x438, (byte) (bus.isZ80ResetState() ? 1 : 0));
        buffer.put(0x439, (byte) (bus.isZ80BusRequested() ? 1 : 0));

        int romBankSerial = GenesisZ80BusProvider.getRomBank68kSerial(z80);
        if (romBankSerial >= 0) {
            setInt4LEFn.accept(0x43C, romBankSerial);
        }
        saveZ80State(z80.getZ80State());
    }

    private void saveZ80State(Z80State z80State) {
        setInt4LEFn.accept(0x404, z80State.getRegAF());
        setInt4LEFn.accept(0x408, z80State.getRegBC());
        setInt4LEFn.accept(0x40C, z80State.getRegDE());
        setInt4LEFn.accept(0x410, z80State.getRegHL());
        setInt4LEFn.accept(0x414, z80State.getRegIX());
        setInt4LEFn.accept(0x418, z80State.getRegIY());
        setInt4LEFn.accept(0x41C, z80State.getRegPC());
        setInt4LEFn.accept(0x420, z80State.getRegSP());
        setInt4LEFn.accept(0x424, z80State.getRegAFx());
        setInt4LEFn.accept(0x428, z80State.getRegBCx());
        setInt4LEFn.accept(0x42C, z80State.getRegDEx());
        setInt4LEFn.accept(0x430, z80State.getRegHLx());
        buffer.put(0x436, (byte) (z80State.isIFF1() ? 1 : 0));
    }

    @Override
    public void save68k(MC68000Wrapper mc68000Wrapper, IMemoryProvider memoryProvider) {
        for (int i = 0; i < MemoryProvider.M68K_RAM_SIZE; i += 2) {
            buffer.put(i + M68K_RAM_DATA_OFFSET, (byte) memoryProvider.readRamByte(i));
            buffer.put(i + 1 + M68K_RAM_DATA_OFFSET, (byte) memoryProvider.readRamByte(i + 1));
        }

        MC68000 m68k = mc68000Wrapper.getM68k();
        setInt4LEFn.accept(0xC8, m68k.getPC());
        setInt2LEFn.accept(0xD0, m68k.getSR());
        setInt4LEFn.accept(0xD2, m68k.getSSP());
        setInt4LEFn.accept(0xD6, m68k.getUSP());

        IntStream.range(0, 8).forEach(i ->
                setInt4LEFn.accept(M68K_REGD_OFFSET + i * 4, m68k.getDataRegisterLong(i)));
        IntStream.range(0, 8).forEach(i ->
                setInt4LEFn.accept(M68K_REGA_OFFSET + i * 4, m68k.getAddrRegisterLong(i)));
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
