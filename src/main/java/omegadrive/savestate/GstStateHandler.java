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

import com.google.common.collect.ImmutableSet;
import m68k.cpu.MC68000;
import omegadrive.Device;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.bus.model.GenesisZ80BusProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemory;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import z80core.Z80;
import z80core.Z80State;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static omegadrive.savestate.StateUtil.*;

public class GstStateHandler implements BaseStateHandler {

    private static final Logger LOG = LogManager.getLogger(GstStateHandler.class.getSimpleName());

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

    protected static final String extension = ".gs";

    private static final Set<Class<? extends Device>> deviceClassSet = ImmutableSet.of(Z80Provider.class,
            GenesisVdpProvider.class, IMemoryProvider.class, GenesisBusProvider.class, SoundProvider.class, MC68000Wrapper.class);

    protected ByteBuffer buffer;
    protected int version;
    protected int softwareId;
    protected String fileName;
    protected Type type;
    protected boolean runAhead;

    private List<Device> deviceList = Collections.emptyList();

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
    public ByteBuffer getDataBuffer() {
        return buffer;
    }

    @Override
    public void processState() {
        Level prev = LogManager.getRootLogger().getLevel();
        GenesisBusProvider bus = StateUtil.getInstanceOrThrow(deviceList, GenesisBusProvider.class);
        BaseVdpProvider vdp = StateUtil.getInstanceOrThrow(deviceList, BaseVdpProvider.class);
        Z80Provider z80 = StateUtil.getInstanceOrThrow(deviceList, Z80Provider.class);
        IMemoryProvider mem = StateUtil.getInstanceOrThrow(deviceList, IMemoryProvider.class);
        MC68000Wrapper cpu = StateUtil.getInstanceOrThrow(deviceList, MC68000Wrapper.class);
        SoundProvider sound = StateUtil.getInstanceOrThrow(deviceList, SoundProvider.class);
        try {
            Configurator.setRootLevel(Level.ERROR);
            if (getType() == Type.LOAD) {
                loadFmState(sound.getFm());
                loadVdpState(vdp);
                loadZ80(z80, bus);
                load68k(cpu, mem);
            } else {
                saveFm(sound.getFm());
                saveZ80(z80, bus);
                save68k(cpu, mem);
                saveVdp(vdp);
            }
        } finally {
            Configurator.setRootLevel(prev);
        }
        if (getType() == Type.LOAD) {
            LOG.info("Savestate loaded from: {}", getFileName());
        }
    }

    protected void setDevicesWithContext(Set<Device> devs) {
        if (!deviceList.isEmpty()) {
            LOG.warn("Overwriting device list: {}", Arrays.toString(deviceList.toArray()));
        }
        deviceList = StateUtil.getDeviceOrderList(deviceClassSet, devs);
    }

    public void loadFmState(FmProvider fm) {
        int reg;
        int limit = FM_DATA_SIZE / 2;
        buffer.position(FM_REG_OFFSET);
        for (reg = 0; reg < limit; reg++) {
            fm.write(MdFmProvider.FM_ADDRESS_PORT0, reg & 0xFF);
            fm.write(MdFmProvider.FM_DATA_PORT0, buffer.get(FM_REG_OFFSET + reg) & 0xFF);
            do {
                fm.tick();
            } while ((fm.read() & 0x40) > 0); //while busy
            fm.write(MdFmProvider.FM_ADDRESS_PORT1, reg & 0xFF);
            fm.write(MdFmProvider.FM_DATA_PORT1, buffer.get(FM_REG_OFFSET + limit + reg) & 0xFF);
            do {
                fm.tick();
            } while ((fm.read() & 0x40) > 0); //while busy
        }
    }

    protected void loadVdpState(BaseVdpProvider vdp) {
        IntStream.range(0, GenesisVdpProvider.VDP_REGISTERS_SIZE).forEach(
                i -> vdp.updateRegisterData(i, buffer.get(i + VDP_REG_OFFSET) & 0xFF));
        loadVdpMemory((VdpMemoryInterface) vdp.getVdpMemory());
        vdp.reload();
    }

    protected void loadVdpMemory(VdpMemoryInterface vdpMemoryInterface) {
        for (int i = 0; i < GenesisVdpProvider.VDP_VRAM_SIZE; i += 2) {
            vdpMemoryInterface.writeVramByte(i, buffer.get(i + VRAM_DATA_OFFSET) & 0xFF);
            vdpMemoryInterface.writeVramByte(i + 1, buffer.get(i + VRAM_DATA_OFFSET + 1) & 0xFF);
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

    protected void loadZ80(Z80Provider z80, GenesisBusProvider bus) {
        int z80BankInt = getInt4Fn.apply(buffer, 0x43C);
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

        Z80State z80State = loadZ80StateGst(buffer);
        z80State.setIM(Z80.IntMode.IM1);
        z80.loadZ80State(z80State);
    }


    //TODO should use M68kProvider
    protected void load68k(MC68000Wrapper m68kProvider, IMemoryProvider memoryProvider) {
        for (int i = 0; i < MemoryProvider.M68K_RAM_SIZE; i += 2) {
            memoryProvider.writeRamByte(i, buffer.get(i + M68K_RAM_DATA_OFFSET) & 0xFF);
            memoryProvider.writeRamByte(i + 1, buffer.get(i + M68K_RAM_DATA_OFFSET + 1) & 0xFF);
        }

        MC68000 m68k = m68kProvider.getM68k();
        m68k.setSR(getInt2Fn.apply(buffer, 0xD0));
        IntStream.range(0, 8).forEach(i ->
                m68k.setDataRegisterLong(i, getInt4Fn.apply(buffer, M68K_REGD_OFFSET + i * 4))
        );

        IntStream.range(0, 8).forEach(i ->
                m68k.setAddrRegisterLong(i, getInt4Fn.apply(buffer, M68K_REGA_OFFSET + i * 4))
        );
        m68k.setPC(getInt4Fn.apply(buffer, 0xC8));
        int ssp = getInt4Fn.apply(buffer, 0xD2);
        int usp = getInt4Fn.apply(buffer, 0xD6);
        if (usp > 0) {
            LOG.warn("USP is not 0: {}", usp);
        }
        if (ssp > 0) {
            LOG.warn("SSP is not 0: {}", ssp);
        }
    }

    protected void saveFm(FmProvider fm) {
        int limit = FM_DATA_SIZE / 2;
        for (int i = 0; i < limit; i++) {
            buffer.put(FM_REG_OFFSET + i, (byte) fm.readRegister(0, i));
            buffer.put(FM_REG_OFFSET + i + limit, (byte) fm.readRegister(1, i));
        }
    }

    protected void saveVdp(BaseVdpProvider vdp) {
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

    protected void saveZ80(Z80Provider z80, GenesisBusProvider bus) {
        IntStream.range(0, GenesisZ80BusProvider.Z80_RAM_MEMORY_SIZE).forEach(
                i -> buffer.put(Z80_RAM_DATA_OFFSET + i, (byte) z80.readMemory(i)));
        buffer.put(0x438, (byte) (bus.isZ80ResetState() ? 1 : 0));
        buffer.put(0x439, (byte) (bus.isZ80BusRequested() ? 1 : 0));

        int romBankSerial = GenesisZ80BusProvider.getRomBank68kSerial(z80);
        if (romBankSerial >= 0) {
            setInt4LEFn(buffer, 0x43C, romBankSerial);
        }
        saveZ80StateGst(buffer, z80.getZ80State());
    }

    protected void save68k(MC68000Wrapper mc68000Wrapper, IMemoryProvider memoryProvider) {
        for (int i = 0; i < MemoryProvider.M68K_RAM_SIZE; i += 2) {
            buffer.put(i + M68K_RAM_DATA_OFFSET, (byte) memoryProvider.readRamByte(i));
            buffer.put(i + 1 + M68K_RAM_DATA_OFFSET, (byte) memoryProvider.readRamByte(i + 1));
        }

        MC68000 m68k = mc68000Wrapper.getM68k();
        setInt4LEFn(buffer, 0xC8, m68k.getPC());
        setInt2LEFn(buffer, 0xD0, m68k.getSR());
        setInt4LEFn(buffer, 0xD2, m68k.getSSP());
        setInt4LEFn(buffer, 0xD6, m68k.getUSP());

        IntStream.range(0, 8).forEach(i ->
                setInt4LEFn(buffer, M68K_REGD_OFFSET + i * 4, m68k.getDataRegisterLong(i)));
        IntStream.range(0, 8).forEach(i ->
                setInt4LEFn(buffer, M68K_REGA_OFFSET + i * 4, m68k.getAddrRegisterLong(i)));
    }
}