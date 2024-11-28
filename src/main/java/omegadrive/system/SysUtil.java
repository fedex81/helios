package omegadrive.system;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import mcd.MegaCd2;
import omegadrive.Device;
import omegadrive.SystemLoader.SystemType;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.sound.PcmProvider;
import omegadrive.sound.PwmProvider;
import omegadrive.sound.SoundDevice;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.ym2413.BlipYm2413Provider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.gb.Gb;
import omegadrive.system.gb.GbSoundWrapper;
import omegadrive.system.nes.Nes;
import omegadrive.system.nes.NesSoundWrapper;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.vdp.model.BaseVdpAdapter;
import omegadrive.vdp.model.BaseVdpProvider;
import org.slf4j.Logger;
import s32x.Md32x;
import s32x.pwm.BlipPwmProvider;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static omegadrive.SystemLoader.SystemType.*;
import static omegadrive.SystemLoader.handleCompressedFiles;
import static omegadrive.sound.SoundDevice.SoundDeviceType.*;
import static omegadrive.sound.SoundProvider.SAMPLE_RATE_HZ;
import static omegadrive.util.RegionDetector.Region;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class SysUtil {

    private final static Logger LOG = LogHelper.getLogger(SysUtil.class.getSimpleName());

    public static final String SMD_INTERLEAVED_EXT = ".smd";

    public static final Map<SystemType, String[]> sysFileExtensionsMap = ImmutableMap.of(
            MD, new String[]{".md", ".bin", SMD_INTERLEAVED_EXT},
            S32X, new String[]{".32x", ".bin", ".md"},
            MEGACD, new String[]{".cue", ".bin", ".iso"},
            SG_1000, new String[]{".sg", ".sc"},
            COLECO, new String[]{".col"},
            MSX, new String[]{".rom"},
            SMS, new String[]{".sms"},
            GG, new String[]{".gg"},
            NES, new String[]{".nes"},
            GB, new String[]{".gb"}
    );
    public static final String[] compressedBinaryTypes = {".gz", ".zip"};

    public static final Set<String> binaryTypesSet;
    public static final String[] binaryTypes;

    static {
        ImmutableSet.Builder<String> b = ImmutableSet.builder();
        sysFileExtensionsMap.values().stream().flatMap(v -> Arrays.stream(v)).forEach(b::add);
        Arrays.stream(compressedBinaryTypes).forEach(b::add);
        binaryTypesSet = b.build();
        binaryTypes = binaryTypesSet.toArray(String[]::new);
    }

    public enum RomFileType {
        UNKNOWN, BIN_CUE, ISO, CART_ROM;

        public boolean isDiscImage() {
            return this == BIN_CUE || this == ISO;
        }
    }

    public static class RomSpec {

        public static final RomSpec NO_ROM = RomSpec.of(new File("./NO_FILE"));

        public Path file;
        public SystemType systemType;

        public static RomSpec of(Path path) {
            return of(path, SystemType.NONE);
        }

        public static RomSpec of(String filePath) {
            return of(Path.of(filePath), SystemType.NONE);
        }

        public static RomSpec of(File file) {
            return of(Path.of(file.getAbsolutePath()), SystemType.NONE);
        }

        public static RomSpec of(File file, SystemType systemType) {
            return of(Path.of(file.getAbsolutePath()), systemType);
        }

        public static RomSpec of(Path file, SystemType systemType) {
            RomSpec r = new RomSpec();
            r.systemType = systemType;
            r.file = file;
            return r;
        }

        @Override
        public String toString() {
            return systemType + "," + file.toAbsolutePath();
        }
    }

    public static SystemProvider createSystemProvider(RomSpec romSpec, DisplayWindow display) {
        String lowerCaseName = handleCompressedFiles(romSpec.file, romSpec.file.toString().toLowerCase());

        if (lowerCaseName == null) {
            LOG.error("Unable to load file: {}", romSpec);
            return null;
        }
        SystemType type = romSpec.systemType;

        if (type == SystemType.NONE) {
            for (var entry : sysFileExtensionsMap.entrySet()) {
                boolean isMatch = Arrays.stream(entry.getValue()).anyMatch(lowerCaseName::endsWith);
                if (isMatch) {
                    type = entry.getKey();
                    break;
                }
            }
        }
        SystemProvider systemProvider = switch (type) {
//            case MD -> Megadrive.createNewInstance(display);
            case MD -> MegaCd2.createNewInstance(display);
            case MEGACD -> MegaCd2.createNewInstance(display);
            case S32X -> Md32x.createNewInstance32x(display);
            case SG_1000 -> Z80BaseSystem.createNewInstance(SG_1000, display);
            case COLECO -> Z80BaseSystem.createNewInstance(COLECO, display);
            case MSX -> Z80BaseSystem.createNewInstance(MSX, display);
            case SMS -> Sms.createNewInstance(SMS, display);
            case GG -> Sms.createNewInstance(GG, display);
            case NES -> Nes.createNewInstance(NES, display);
            case GB -> Gb.createNewInstance(GB, display);
            case NONE -> {
                LOG.error("Unable to find a system to load: {}", romSpec);
                yield null;
            }
        };
        return systemProvider;
    }


    public static Map<SoundDevice.SoundDeviceType, SoundDevice> getSoundDevices(SystemType systemType, Region region) {
        Map<SoundDevice.SoundDeviceType, SoundDevice> m = new EnumMap<>(SoundDevice.SoundDeviceType.class);
        m.put(PSG, getPsgProvider(systemType, region));
        m.put(FM, getFmProvider(systemType, region));
        m.put(PWM, getPwmProvider(systemType, region));
        m.put(PCM, getPcmProvider(systemType, region));
        return m;
    }

    public static SoundDevice getPwmProvider(SystemType systemType, Region region) {
        return switch (systemType) {
            case S32X -> new BlipPwmProvider(region, AbstractSoundManager.audioFormat);
            default -> PwmProvider.NO_SOUND;
        };
    }

    public static SoundDevice getPcmProvider(SystemType systemType, Region region) {
        return switch (systemType) {
            default -> PcmProvider.NO_SOUND;
        };
    }

    public static SoundDevice getPsgProvider(SystemType systemType, Region region) {
        SoundDevice psgProvider = PsgProvider.NO_SOUND;
        switch (systemType) {
            case MSX:
                psgProvider = PsgProvider.createAyInstance(region, SAMPLE_RATE_HZ);
                break;
            case NES:
            case GB:
                //no PSG, external audio set as FM
                break;
            default:
                psgProvider = PsgProvider.createSnInstance(region, AbstractSoundManager.audioFormat);
                break;
        }
        return psgProvider;
    }

    public static SoundDevice getFmProvider(SystemType systemType, Region region) {
        SoundDevice fmProvider = FmProvider.NO_SOUND;
        switch (systemType) {
            case MD:
            case S32X:
            case MEGACD:
                fmProvider = MdFmProvider.createInstance(region, AbstractSoundManager.audioFormat);
                break;
            case SMS:
                if (Sms.ENABLE_FM) {
                    fmProvider = BlipYm2413Provider.createInstance(AbstractSoundManager.audioFormat);
                }
                break;
            case NES:
                fmProvider = new NesSoundWrapper(region);
                break;
            case GB:
                fmProvider = new GbSoundWrapper(region);
                break;
            default:
                break;
        }
        return fmProvider;
    }

    public static final BaseBusProvider NO_OP_BUS = new BaseBusProvider() {
        @Override
        public int read(int address, Size size) {
            return 0;
        }

        @Override
        public void write(int address, int data, Size size) {

        }

        @Override
        public void writeIoPort(int port, int value) {

        }

        @Override
        public int readIoPort(int port) {
            return 0;
        }

        @Override
        public BaseBusProvider attachDevice(Device device) {
            return null;
        }

        @Override
        public <T extends Device> Optional<T> getBusDeviceIfAny(Class<T> clazz) {
            return Optional.empty();
        }

        @Override
        public <T extends Device> Set<T> getAllDevices(Class<T> clazz) {
            return Collections.emptySet();
        }
    };
    public static final BaseVdpProvider NO_OP_VDP_PROVIDER = new BaseVdpAdapter();
}
