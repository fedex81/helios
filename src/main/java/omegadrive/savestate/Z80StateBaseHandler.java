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
import omegadrive.bus.z80.Z80BusProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.FileLoader;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.Z80State;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.IntStream;

import static omegadrive.savestate.StateUtil.loadZ80State;
import static omegadrive.savestate.StateUtil.saveZ80State;

public abstract class Z80StateBaseHandler<T extends Z80BusProvider, V extends BaseVdpProvider> implements Z80StateHandler<T, V> {

    protected static final Z80SavestateVersion CURRENT_SAVE_VERSION = Z80SavestateVersion.VER_1;
    private static final Logger LOG = LogManager.getLogger(Z80StateBaseHandler.class.getSimpleName());
    private static final String MAGIC_WORD_STR = "HELIOS-Z80";
    private static final byte[] MAGIC_WORD = MAGIC_WORD_STR.getBytes();
    private static final int SYS_NAME_LEN = 16;
    protected static int FIXED_SIZE_LIMIT = 0x5000;
    protected String fileExtension = "error";
    protected ByteBuffer buffer;
    protected int version;
    protected String fileName;
    protected Type type;
    private Z80SavestateVersion pmVersion;

    protected Z80StateBaseHandler() {
    }

    public static Z80StateHandler createInstance(String fileName, SystemLoader.SystemType systemType,
                                                 BaseStateHandler.Type loadSaveType) {
        Z80StateBaseHandler h;
        switch (systemType) {
            case SG_1000:
            case COLECO:
                h = new TmsZ80StateHandler();
                break;
            case MSX:
                h = new MsxStateHandler();
                break;
            default:
                LOG.error("Error");
                return Z80StateHandler.EMPTY_STATE;
        }
        if (loadSaveType == Type.LOAD) {
            boolean res = h.initLoadType(fileName, systemType);
            if (!res) {
                return Z80StateHandler.EMPTY_STATE;
            }
        } else {
            h.initSaveType(fileName, systemType);
        }
        return h;
    }

    protected void initCommon(String fileName, SystemLoader.SystemType systemType) {
        fileExtension = StateUtil.fileExtensionMap.getOrDefault(systemType, "error");
        this.fileName = handleFileExtension(fileName);
    }

    protected void initSaveType(String fileName, SystemLoader.SystemType systemType) {
        initCommon(fileName, systemType);
        buffer = ByteBuffer.allocate(FIXED_SIZE_LIMIT);
        //file type
        buffer.put(MAGIC_WORD);
        buffer.put((byte) CURRENT_SAVE_VERSION.getVersion());
        buffer.put(Arrays.copyOf(systemType.name().getBytes(), SYS_NAME_LEN));

        buffer.put(FIXED_SIZE_LIMIT - 3, (byte) 'E');
        buffer.put(FIXED_SIZE_LIMIT - 2, (byte) 'O');
        buffer.put(FIXED_SIZE_LIMIT - 1, (byte) 'F');

        version = CURRENT_SAVE_VERSION.getVersion();
        type = Type.SAVE;
    }

    protected boolean initLoadType(String fileName, SystemLoader.SystemType systemType) {
        initCommon(fileName, systemType);
        buffer = ByteBuffer.wrap(FileLoader.readFileSafe(Paths.get(this.fileName)));
        type = Type.LOAD;
        return detectStateFileType(systemType);
    }

    private String handleFileExtension(String fileName) {
        return fileName + (!fileName.toLowerCase().contains("." + fileExtension) ? "." + fileExtension : "");
    }

    protected boolean detectStateFileType(SystemLoader.SystemType systemType) {
        byte[] b = new byte[MAGIC_WORD.length];
        buffer.get(b);
        String fileType = new String(b);
        if (!MAGIC_WORD_STR.equalsIgnoreCase(fileType)) {
            LOG.error("Unable to load savestate of type: {}, size: {}", fileType, buffer.capacity());
            return false;
        }
        version = buffer.get();
        pmVersion = Z80SavestateVersion.parseVersion(version);
        if (pmVersion != CURRENT_SAVE_VERSION) {
            LOG.error("Unable to handle savestate version: {}", CURRENT_SAVE_VERSION);
            return false;
        }
        byte[] sys = new byte[SYS_NAME_LEN];
        buffer.get(sys);
        String sysName = new String(sys).trim();
        try {
            SystemLoader.SystemType type = SystemLoader.SystemType.valueOf(sysName);
            if (systemType != type) {
                LOG.error("Unable to handle savestate {}, actual systemType {}, expected: {}", fileName, type, systemType);
                return false;
            }
        } catch (Exception e) {
            LOG.error("Unable to handle savestate {}, unknown systemType {}, expected: {}", fileName, sysName, systemType);
            return false;
        }

        return true;
    }

    @Override
    public void loadZ80Context(Z80Provider z80) {
        Z80State z80State = loadZ80State(buffer);
        z80.loadZ80State(z80State);
    }

    @Override
    public void saveZ80Context(Z80Provider z80) {
        saveZ80State(buffer, z80.getZ80State());
    }

    @Override
    public void loadMemory(IMemoryProvider mem) {
        IntStream.range(0, mem.getRamSize()).forEach(i -> mem.writeRamByte(i, buffer.get() & 0xFF));
    }

    @Override
    public void saveMemory(IMemoryProvider mem) {
        int[] ram = mem.getRamData();
        IntStream.range(0, ram.length).forEach(i -> buffer.put((byte) (ram[i] & 0xFF)));
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

    enum Z80SavestateVersion {
        UNKNOWN(0),
        VER_1(1);

        private int version;

        Z80SavestateVersion(int version) {
            this.version = version;
        }

        public static Z80SavestateVersion parseVersion(int v) {
            for (Z80SavestateVersion ver : Z80SavestateVersion.values()) {
                if (v == ver.version) {
                    return ver;
                }
            }
            return UNKNOWN;
        }

        public int getVersion() {
            return version;
        }
    }
}