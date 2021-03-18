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

import com.google.common.collect.ImmutableSet;
import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.z80.SmsBus;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.FileLoader;
import omegadrive.util.Util;
import omegadrive.vdp.SmsVdp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import static omegadrive.savestate.StateUtil.skip;

public class MekaStateHandler implements BaseStateHandler {

    private static final String MAGIC_WORD_STR = "MEKA";
    private static final byte[] MAGIC_WORD = MAGIC_WORD_STR.getBytes();
    private static final int Z80_REG_lEN = 26;
    private static final int Z80_MISC_LEN = 27;
    private static final int VDP_MISC_LEN = 20;
    private static final MekaSavestateVersion DEFAULT_SAVE_VERSION = MekaSavestateVersion.VER_D;
    private static final Function<Integer, String> toCrcStringFn = v -> (v < 16 ? "0" : "") +
            Integer.toHexString(v);
    private final static String fileExtension = "s00";
    private static final Logger LOG = LogManager.getLogger(MekaStateHandler.class.getSimpleName());
    private static final Set<Class<? extends Device>> deviceClassSet = ImmutableSet.of(Z80Provider.class,
            SmsVdp.class, IMemoryProvider.class, SmsBus.class);

    int[] vdpState = new int[3];
    private ByteBuffer buffer;
    private int version;
    private int softwareId;
    private String fileName;
    private Type type;
    private SystemLoader.SystemType systemType;
    private MekaSavestateVersion mekaVersion;
    private List<Device> deviceList = Collections.emptyList();

    private MekaStateHandler() {
    }

    public static BaseStateHandler createInstance(SystemLoader.SystemType systemType,
                                                  String fileName, Type type, Set<Device> deviceSet) {
        BaseStateHandler h = type == Type.LOAD ? createLoadInstance(fileName, deviceSet) :
                createSaveInstance(fileName, systemType, deviceSet);
        return h;
    }

    private static BaseStateHandler createLoadInstance(String fileName, Set<Device> deviceSet) {
        MekaStateHandler h = new MekaStateHandler();
        h.fileName = handleFileExtension(fileName);
        h.buffer = ByteBuffer.wrap(FileLoader.readBinaryFile(Paths.get(h.fileName)));
        h.type = Type.LOAD;
        h.setDevicesWithContext(deviceSet);
        return h.detectStateFileType();
    }

    private static MekaStateHandler createSaveInstance(String fileName, SystemLoader.SystemType systemType, Set<Device> deviceSet) {
        MekaStateHandler h = createSaveInstance(fileName, systemType, "0"); //TODO crc32
        h.setDevicesWithContext(deviceSet);
        return h;
    }

    public static BaseStateHandler createLoadInstance(String fileName, byte[] data, Set<Device> deviceSet) {
        MekaStateHandler h = new MekaStateHandler();
        h.fileName = handleFileExtension(fileName);
        h.buffer = ByteBuffer.wrap(data);
        h.type = Type.LOAD;
        h.setDevicesWithContext(deviceSet);
        return h.detectStateFileType();
    }

    private static MekaStateHandler createSaveInstance(String fileName, SystemLoader.SystemType systemType,
                                                       String romCrc32) {
        MekaStateHandler h = new MekaStateHandler();
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



    private static String handleFileExtension(String fileName) {
        return fileName + (!fileName.toLowerCase().contains(".s0") ? "." + fileExtension : "");
    }

    private static String decodeCrc32(MekaSavestateVersion version, ByteBuffer data) {
        int index = data.position();
        data.position(index + 4);
        return toCrcStringFn.apply(data.get(index + 3) & 0xFF) + toCrcStringFn.apply(data.get(index + 2) & 0xFF) +
                toCrcStringFn.apply(data.get(index + 1) & 0xFF) + toCrcStringFn.apply(data.get(index) & 0xFF);
    }

    @Override
    public void processState() {
        SmsBus bus = StateUtil.getInstanceOrThrow(deviceList, SmsBus.class);
        SmsVdp vdp = StateUtil.getInstanceOrThrow(deviceList, SmsVdp.class);
        Z80Provider z80 = StateUtil.getInstanceOrThrow(deviceList, Z80Provider.class);
        IMemoryProvider mem = StateUtil.getInstanceOrThrow(deviceList, IMemoryProvider.class);

        if (type == Type.LOAD) {
            //order is important
            z80.loadContext(buffer);
            skip(buffer, Z80_MISC_LEN);

            loadVdp(vdp, bus);

            mem.loadContext(buffer);
            loadVdpMemory(vdp);
        } else {
            //order is important
            z80.saveContext(buffer);
            skip(buffer, Z80_MISC_LEN);

            saveVdp(vdp, bus);

            mem.saveContext(buffer);
            saveVdpMemory(vdp);
        }
    }

    private void setDevicesWithContext(Set<Device> devs) {
        if (!deviceList.isEmpty()) {
            LOG.warn("Overwriting device list: {}", Arrays.toString(deviceList.toArray()));
        }
        deviceList = StateUtil.getDeviceOrderList(deviceClassSet, devs);
    }

    private BaseStateHandler detectStateFileType() {
        String fileType = Util.toStringValue(buffer.get(), buffer.get(), buffer.get(), buffer.get());
        if (!MAGIC_WORD_STR.equalsIgnoreCase(fileType)) {
            LOG.error("Unable to load savestate of type: {}, size: {}", fileType, buffer.capacity());
            return BaseStateHandler.EMPTY_STATE;
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
            String crc32 = decodeCrc32(mekaVersion, buffer);
            LOG.info("ROM crc32: {}", crc32);
        }
    }

    private void loadVdp(SmsVdp vdp, SmsBus bus) {
        int pos = buffer.position();
        vdp.loadContext(buffer);
        //HEL string + additional data
        boolean isHeliosFormat = buffer.position() > pos + SmsVdp.VDP_REGISTERS_SIZE + 3;
        int toSkip = isHeliosFormat ? VDP_MISC_LEN - (vdpState.length * 4 + 3) : VDP_MISC_LEN - 3;
        skip(buffer, toSkip);
        bus.loadContext(buffer); //loadMappers
        if (version >= 0xD) {
            int vdpLine = Util.getUInt32LE(buffer.get(), buffer.get());
            LOG.info("vdpLine: {}", vdpLine);
        }
    }

    private void saveVdp(SmsVdp vdp, SmsBus bus) {
        vdp.saveContext(buffer);
        skip(buffer, VDP_MISC_LEN - (vdpState.length * 4 + 3));
        bus.saveContext(buffer); //saveMappers
        buffer.put((byte) 0); //vdpLine
        buffer.put((byte) 0); //vdpLine
    }

    private void loadVdpMemory(SmsVdp vdp) {
        int[] vram = vdp.getVdpMemory().getVram();
        int[] cram = vdp.getVdpMemory().getCram();
        IntStream.range(0, SmsVdp.VDP_VRAM_SIZE).forEach(i -> vram[i] = buffer.get() & 0xFF);
        //SMS CRAM = 0x20, GG = 0x40
        IntStream.range(0, SmsVdp.VDP_CRAM_SIZE).forEach(i -> {
            int smsCol = buffer.get();
            int r = smsCol & 0x03;
            int g = (smsCol >> 2) & 0x03;
            int b = (smsCol >> 4) & 0x03;
            cram[i] = ((r * 85) << 16) | ((g * 85) << 8) | (b * 85);
        });
        vdp.forceFullRedraw();
    }

    private void saveVdpMemory(SmsVdp vdp) {
        int[] vram = vdp.getVdpMemory().getVram();
        int[] cram = vdp.getVdpMemory().getCram();
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
    public ByteBuffer getDataBuffer() {
        return buffer;
    }
}
