/*
 * MekaStateHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 18:42
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

import omegadrive.SystemLoader;
import omegadrive.bus.z80.SmsBus;
import omegadrive.bus.z80.Z80BusProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.FileLoader;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.SmsVdp;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.Z80;
import z80core.Z80State;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.IntStream;

public class MekaStateHandler2 implements SmsStateHandler {

    private static final byte[] MAGIC_WORD = {'M', 'E', 'K', 'A'};
    private static final String MAGIC_WORD_STR = "MEKA";
    private static final int Z80_REG_lEN = 25;
    private static final int Z80_MISC_LEN = 27;
    private static final int VDP_MISC_LEN = 20;
    private static final MekaSavestateVersion DEFAULT_SAVE_VERSION = MekaSavestateVersion.VER_D;
    private static final Function<Integer, String> toCrcStringFn = v -> (v < 16 ? "0" : "") +
            Integer.toHexString(v);
    private final static String fileExtension = "s00";
    private static Logger LOG = LogManager.getLogger(MekaStateHandler2.class.getSimpleName());
    int[] vdpState = new int[3];
    private ByteBuffer buffer;
    private int version;
    private int softwareId;
    private String fileName;
    private Type type;
    private SystemLoader.SystemType systemType;
    private MekaSavestateVersion mekaVersion;

    private MekaStateHandler2() {
    }

    public static SmsStateHandler createLoadInstance(String fileName) {
        MekaStateHandler2 h = new MekaStateHandler2();
        h.fileName = handleFileExtension(fileName);
        h.buffer = ByteBuffer.wrap(FileLoader.readBinaryFileByte(Paths.get(h.fileName)));
        h.type = Type.LOAD;
        SmsStateHandler s = h.detectStateFileType();
        return s;
    }

    public static SmsStateHandler createLoadInstance(String fileName, int[] data) {
        MekaStateHandler2 h = new MekaStateHandler2();
        h.fileName = handleFileExtension(fileName);
        h.buffer = ByteBuffer.wrap(Util.toByteArray(data));
        h.type = Type.LOAD;
        SmsStateHandler s = h.detectStateFileType();
        return s;
    }

    public static SmsStateHandler createSaveInstance(String fileName, SystemLoader.SystemType systemType,
                                                     String romCrc32) {
        MekaStateHandler2 h = new MekaStateHandler2();
        int machineDriverId = systemType == SystemLoader.SystemType.SMS ? 0 :
                (systemType == SystemLoader.SystemType.GG ? 1 : -1);
        if (machineDriverId < 0) {
            throw new IllegalArgumentException("Invalid systemType: " + systemType);
        }
        long crc32 = Long.parseLong(romCrc32, 16);
        int len = (DEFAULT_SAVE_VERSION.getMemoryEndPos() + 3) << 1;
        h.buffer = ByteBuffer.allocate(len);
        //file type
        h.buffer.put(MAGIC_WORD);
        h.buffer.put((byte) 0x1A); //unknown
        h.buffer.put((byte) DEFAULT_SAVE_VERSION.getVersion());
        h.buffer.put((byte) machineDriverId);

        h.buffer.put((byte) (crc32 & 0xFF));
        h.buffer.put((byte) ((crc32 >> 8) & 0xFF));
        h.buffer.put((byte) ((crc32 >> 16) & 0xFF));
        h.buffer.put((byte) ((crc32 >> 24) & 0xFF));

        h.buffer.put(len - 3, (byte) 'E');
        h.buffer.put(len - 2, (byte) 'O');
        h.buffer.put(len - 1, (byte) 'F');

        h.mekaVersion = DEFAULT_SAVE_VERSION;
        h.systemType = systemType;

        h.fileName = handleFileExtension(fileName);
        h.type = Type.SAVE;
        return h;
    }

    public static SmsStateHandler createSaveInstance(String fileName, SystemLoader.SystemType systemType) {
        return createSaveInstance(fileName, systemType, "0");
    }

    private static String handleFileExtension(String fileName) {
        return fileName + (!fileName.toLowerCase().contains(".s0") ? "." + fileExtension : "");
    }

    private static String decodeCrc32(MekaSavestateVersion version, ByteBuffer data) {
        int index = data.position();
        data.position(index + 4);
        return toCrcStringFn.apply((int) data.get(index + 3)) + toCrcStringFn.apply((int) data.get(index + 2)) +
                toCrcStringFn.apply((int) data.get(index + 1)) + toCrcStringFn.apply((int) data.get(index));
    }

    private static void skip(ByteBuffer buf, int len) {
        buf.position(buf.position() + len);
    }

    private static void loadMappers(ByteBuffer buffer, Z80BusProvider bus) {
        bus.write(0xFFFC, buffer.get(), Size.BYTE);
        bus.write(0xFFFD, buffer.get(), Size.BYTE);
        bus.write(0xFFFE, buffer.get(), Size.BYTE);
        bus.write(0xFFFF, buffer.get(), Size.BYTE);
    }

    private static void saveMappers(ByteBuffer buffer, Z80BusProvider bus) {
        SmsBus smsbus = (SmsBus) bus;
        int[] frameReg = smsbus.getFrameReg();
        int control = smsbus.getMapperControl();
        LOG.info("mapperControl: {}, frameReg: {}", control, Arrays.toString(frameReg));
        buffer.put((byte) (control & 0xFF));
        buffer.put(Util.toByteArray(frameReg));
    }

    //2 bytes for a 16 bit int
    private static void setData(ByteBuffer buf, int... data) {
        Arrays.stream(data).forEach(val -> buf.put((byte) (val & 0xFF)));
    }

    private SmsStateHandler detectStateFileType() {
        String fileType = Util.toStringValue(buffer.get(), buffer.get(), buffer.get(), buffer.get());
        if (!MAGIC_WORD_STR.equalsIgnoreCase(fileType)) {
            LOG.error("Unable to load savestate of type: {}, size: {}", fileType, buffer.capacity());
            return SmsStateHandler.EMPTY_STATE;
        }
        buffer.get(); //skip 1
        version = buffer.get();
        mekaVersion = MekaSavestateVersion.getMekaVersion(version);
        int machineDriverId = buffer.get();
        systemType = machineDriverId == 0 ? SystemLoader.SystemType.SMS :
                (machineDriverId == 1 ? SystemLoader.SystemType.GG : null);
        if (systemType == null) {
            throw new IllegalArgumentException("Unknown machineDriverId: " + machineDriverId);
        }
        crcCheck();
        return this;
    }

    private void crcCheck() {
        if (version >= 0xC) {
            //TODO
            String crc32 = decodeCrc32(mekaVersion, buffer);
            LOG.info("ROM crc32: {}", crc32);
        }
    }

    private Z80State loadZ80State(ByteBuffer data) {
        Z80State z80State = new Z80State();
        z80State.setRegAF(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegBC(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegDE(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegHL(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegIX(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegIY(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegPC(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegSP(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegAFx(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegBCx(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegDEx(Util.getUInt32LE(data.get(), data.get()));
        z80State.setRegHLx(Util.getUInt32LE(data.get(), data.get()));

        int val = data.get();
        Z80.IntMode im = ((val & 2) > 0) ? Z80.IntMode.IM1 : Z80.IntMode.IM0;
        z80State.setIM(im);
        z80State.setIFF1((val & 1) > 0);
        z80State.setIFF2((val & 8) > 0);
        z80State.setHalted((val & 0x80) > 0);

        skip(data, Z80_MISC_LEN);
        return z80State;
    }

    @Override
    public void loadVdp(BaseVdpProvider vdp, IMemoryProvider memory, SmsBus bus) {
        SmsVdp smsVdp = (SmsVdp) vdp;
        IntStream.range(0, SmsVdp.VDP_REGISTERS_SIZE).forEach(i -> smsVdp.registerWrite(i, buffer.get() & 0xFF));
        int toSkip = VDP_MISC_LEN - 3;
        String helString = Util.toStringValue(buffer.get(), buffer.get(), buffer.get());
        if ("HEL".equals(helString)) {
            vdpState[0] = buffer.getInt();
            vdpState[1] = buffer.getInt();
            vdpState[2] = buffer.getInt();
            smsVdp.setStateSimple(vdpState);
            toSkip = VDP_MISC_LEN - (vdpState.length * 4 + 3);
        }
        skip(buffer, toSkip);
        loadMappers(buffer, bus);
        if (version >= 0xD) {
            int vdpLine = Util.getUInt32LE(buffer.get(), buffer.get());
            LOG.info("vdpLine: {}", vdpLine);
        }
    }


    @Override
    public void saveVdp(BaseVdpProvider vdp, IMemoryProvider memory, Z80BusProvider bus) {
        IntStream.range(0, SmsVdp.VDP_REGISTERS_SIZE).forEach(i -> buffer.put((byte) vdp.getRegisterData(i)));
        SmsVdp smsVdp = (SmsVdp) vdp;
        smsVdp.getStateSimple(vdpState);
        buffer.put((byte) 'H').put((byte) 'E').put((byte) 'L');
        buffer.putInt(vdpState[0]).putInt(vdpState[1]).putInt(vdpState[2]);
        skip(buffer, VDP_MISC_LEN - (vdpState.length * 4 + 3));
        saveMappers(buffer, bus);
        buffer.put((byte) 0); //vdpLine
        buffer.put((byte) 0); //vdpLine
    }


    @Override
    public void loadZ80(Z80Provider z80, Z80BusProvider bus) {
        Z80State z80State = loadZ80State(buffer);
        z80.loadZ80State(z80State);
    }

    @Override
    public void saveZ80(Z80Provider z80, Z80BusProvider bus) {
        Z80State s = z80.getZ80State();
        setData(buffer, s.getRegF(), s.getRegA(), s.getRegC(), s.getRegB(),
                s.getRegE(), s.getRegD(), s.getRegL(), s.getRegH());
        setData(buffer, s.getRegIX() & 0xFF, s.getRegIX() >> 8, s.getRegIY() & 0xFF,
                s.getRegIX() >> 8, s.getRegPC() & 0xFF, s.getRegPC() >> 8, s.getRegSP() & 0xFF,
                s.getRegSP() >> 8);
        setData(buffer, s.getRegFx(), s.getRegAx(), s.getRegCx(), s.getRegBx(), s.getRegEx(),
                s.getRegDx(), s.getRegLx(), s.getRegHx());

        //TODO check
        int val = (s.isHalted() ? 1 : 0) << 8 | (s.isIFF2() ? 1 : 0) << 3 | s.getIM().ordinal() << 1 |
                (s.isIFF1() ? 1 : 0);
        setData(buffer, val);
        skip(buffer, Z80_MISC_LEN);
    }

    @Override
    public void loadMemory(IMemoryProvider mem, SmsVdp vdp) {
        int[] vram = vdp.getVdpMemory().getVram();
        int[] cram = vdp.getVdpMemory().getCram();
        IntStream.range(0, MemoryProvider.SMS_Z80_RAM_SIZE).forEach(i -> mem.writeRamByte(i, buffer.get() & 0xFF));
        IntStream.range(0, SmsVdp.VDP_VRAM_SIZE).forEach(i -> vram[i] = buffer.get() & 0xFF);
        //TODO check SMS CRAM = 0x20, GG = 0x40
        IntStream.range(0, SmsVdp.VDP_CRAM_SIZE).forEach(i -> {
            int smsCol = buffer.get();
            int r = smsCol & 0x03;
            int g = (smsCol >> 2) & 0x03;
            int b = (smsCol >> 4) & 0x03;
            cram[i] = ((r * 85) << 16) | ((g * 85) << 8) | (b * 85);
        });
        vdp.forceFullRedraw();
    }

    @Override
    public void saveMemory(IMemoryProvider mem, SmsVdp vdp) {
        int[] ram = mem.getRamData();
        int[] vram = vdp.getVdpMemory().getVram();
        int[] cram = vdp.getVdpMemory().getCram();
        IntStream.range(0, MemoryProvider.SMS_Z80_RAM_SIZE).forEach(i -> buffer.put((byte) (ram[i] & 0xFF)));
        IntStream.range(0, SmsVdp.VDP_VRAM_SIZE).forEach(i -> buffer.put((byte) (vram[i] & 0xFF)));
        IntStream.range(0, SmsVdp.VDP_CRAM_SIZE).forEach(i -> {
            //0xAARRGGBB (4 bytes) Java colour
            //SMS : 00BBGGRR   (1 byte)
            int javaColor = cram[i];
            int b = (javaColor & 0xFF) / 85;
            int g = ((javaColor >> 8) & 0xFF) / 85;
            int r = ((javaColor >> 16) & 0xFF) / 85;
            int smsCol = b << 4 | g << 2 | r;
            buffer.put((byte) (smsCol & 0xFF));
        });
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
        return Util.toIntArray(buffer.array());
    }
}
