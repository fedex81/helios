package omegadrive.system;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import mcd.MegaCd;
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
import omegadrive.system.gb.BlipGbSoundWrapper;
import omegadrive.system.gb.Gb;
import omegadrive.system.nes.BlipNesSoundWrapper;
import omegadrive.system.nes.Nes;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.vdp.model.BaseVdpAdapter;
import omegadrive.vdp.model.BaseVdpProvider;
import org.slf4j.Logger;
import s32x.Md32x;
import s32x.MegaCd32x;
import s32x.pwm.BlipPwmProvider;
import s32x.pwm.Pwm;
import s32x.pwm.S32xPwmProvider;
import s32x.sh2.Sh2Helper;

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

    public static final Map<SystemType, String[]> sysFileExtensionsMap = ImmutableMap.<SystemType, String[]>builder()
            .put(MD, new String[]{".md", ".bin", SMD_INTERLEAVED_EXT})
            .put(S32X, new String[]{".32x", ".bin", ".md"})
            .put(MEGACD, new String[]{".cue", ".bin", ".iso"})
            .put(MEGACD_S32X, new String[]{".cue", ".bin", ".iso", ".32x"})
            .put(SG_1000, new String[]{".sg", ".sc"})
            .put(COLECO, new String[]{".col"})
            .put(MSX, new String[]{".rom"})
            .put(SMS, new String[]{".sms"})
            .put(GG, new String[]{".gg"})
            .put(NES, new String[]{".nes"})
            .put(GB, new String[]{".gb"}).build();
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

    public static SystemProvider createSystemProvider(MediaSpecHolder romSpec, DisplayWindow display) {
        MediaSpecHolder.MediaSpec mediaSpec = romSpec.getBootableMedia();
        String lcName = mediaSpec.romFile.getFileName().toString().toLowerCase();
        String lowerCaseName = handleCompressedFiles(mediaSpec.romFile, lcName);

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
        romSpec.systemType = type;
        romSpec.reload();
        SystemProvider systemProvider = switch (type) {
            case MD -> Megadrive.createNewInstance(display);
            case MEGACD -> MegaCd.createNewInstance(display);
            case S32X -> Md32x.createNewInstance32x(display);
            case MEGACD_S32X -> MegaCd32x.createNewInstance(display);
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
            case S32X -> Pwm.PWM_USE_BLIP ? new BlipPwmProvider(region) : new S32xPwmProvider(region);
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
                psgProvider = PsgProvider.createAyInstance(systemType, region, SAMPLE_RATE_HZ);
                break;
            case NES:
            case GB:
                //no PSG, external audio set as FM
                break;
            default:
                psgProvider = PsgProvider.createSnInstance(systemType, region, SAMPLE_RATE_HZ);
                break;
        }
        return psgProvider;
    }

    public static SoundDevice getFmProvider(SystemType systemType, Region region) {
        SoundDevice fmProvider = FmProvider.NO_SOUND;
        switch (systemType) {
            case MEGACD_S32X:
                fmProvider = MdFmProvider.createFastInstance(region, AbstractSoundManager.audioFormat);
                break;
            case MD:
            case MEGACD:
            case S32X:
                boolean fast = systemType == S32X && !Sh2Helper.Sh2Config.get().fastFm ? false : true;
                fmProvider = MdFmProvider.createInstance(region, AbstractSoundManager.audioFormat, fast);
                break;
            case SMS:
                if (Sms.ENABLE_FM) {
                    fmProvider = BlipYm2413Provider.createInstance();
                }
                break;
            case NES:
                fmProvider = new BlipNesSoundWrapper(region);
                break;
            case GB:
                fmProvider = new BlipGbSoundWrapper(region);
                break;
            default:
                break;
        }
        return fmProvider;
    }

    //TODO
    public static boolean isBlipSound(SystemType systemType) {
        return true;
//        systemType == SystemType.SMS || systemType == SystemType.GG ||
//                systemType == SystemType.COLECO || systemType == SG_1000 || systemType == MSX || systemType == NES
//                || systemType == GB;
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
