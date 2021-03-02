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
import omegadrive.bus.z80.MsxBus;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.FileLoader;
import omegadrive.vdp.Tms9918aVdp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Z80StateBaseHandler implements BaseStateHandler {

    protected static final Z80SavestateVersion CURRENT_SAVE_VERSION = Z80SavestateVersion.VER_1;
    private static final Logger LOG = LogManager.getLogger(Z80StateBaseHandler.class.getSimpleName());
    private static final String MAGIC_WORD_STR = "HELIOS-Z80";
    private static final byte[] MAGIC_WORD = MAGIC_WORD_STR.getBytes();
    private static final int SYS_NAME_LEN = 16;
    private static final Set<Class<? extends Device>> deviceClassSet = ImmutableSet.of(Z80Provider.class,
            Tms9918aVdp.class, IMemoryProvider.class, MsxBus.class);
    protected String fileExtension = "error";
    protected ByteBuffer buffer;
    protected int version;
    protected String fileName;
    protected Type type;
    private Z80SavestateVersion pmVersion;
    protected int FIXED_SIZE_LIMIT = 0x5000;
    private List<Device> deviceList = Collections.emptyList();

    protected Z80StateBaseHandler() {
    }

    public static BaseStateHandler createInstance(String fileName, SystemLoader.SystemType systemType,
                                                  BaseStateHandler.Type loadSaveType, Set<Device> devices) {
        return createInstance(fileName, systemType, loadSaveType, devices, null);
    }

    private static BaseStateHandler createInstance(String fileName, SystemLoader.SystemType systemType,
                                                   BaseStateHandler.Type loadSaveType, Set<Device> devices, byte[] data) {
        Z80StateBaseHandler h;
        switch (systemType) {
            case SG_1000:
            case COLECO:
                h = new Z80StateBaseHandler();
                break;
            case MSX:
                h = new Z80StateBaseHandler();
                h.FIXED_SIZE_LIMIT = 0x9000;
                break;
            default:
                LOG.error("Error");
                return BaseStateHandler.EMPTY_STATE;
        }
        if (loadSaveType == Type.LOAD) {
            boolean res = h.initLoadType(fileName, systemType, data);
            if (!res) {
                return BaseStateHandler.EMPTY_STATE;
            }
        } else {
            h.initSaveType(fileName, systemType);
        }
        h.setDevicesWithContext(devices);
        return h;
    }

    public static BaseStateHandler createLoadInstance(String fileName, SystemLoader.SystemType systemType, byte[] data,
                                                      Set<Device> devices) {
        return createInstance(fileName, systemType, Type.LOAD, devices, data);
    }

    protected String initCommon(String fileName, SystemLoader.SystemType systemType) {
        fileExtension = StateUtil.fileExtensionMap.getOrDefault(systemType, "error");
        this.fileName = handleFileExtension(fileName);
        return this.fileName;
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

    protected boolean initLoadType(String fileName, SystemLoader.SystemType systemType, byte[] data) {
        fileName = initCommon(fileName, systemType);
        data = data == null ? FileLoader.readFileSafe(Paths.get(fileName)) : data;
        buffer = ByteBuffer.wrap(data);
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
        StateUtil.processState(this, deviceList);
    }

    private void setDevicesWithContext(Set<Device> devs) {
        if (!deviceList.isEmpty()) {
            LOG.warn("Overwriting device list: {}", Arrays.toString(deviceList.toArray()));
        }
        deviceList = StateUtil.getDeviceOrderList(deviceClassSet, devs);

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