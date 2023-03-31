package omegadrive.system;

import omegadrive.Device;
import omegadrive.SystemLoader.SystemType;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.sound.PwmProvider;
import omegadrive.sound.SoundDevice;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.ym2413.Ym2413Provider;
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
import s32x.pwm.Pwm;
import s32x.pwm.S32xPwmProvider;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

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

    public static final String[] mdBinaryTypes = {".md", ".bin", SMD_INTERLEAVED_EXT};
    public static final String[] sgBinaryTypes = {".sg", ".sc"};
    public static final String[] cvBinaryTypes = {".col"};
    public static final String[] msxBinaryTypes = {".rom"};
    public static final String[] smsBinaryTypes = {".sms"};
    public static final String[] ggBinaryTypes = {".gg"};
    public static final String[] nesBinaryTypes = {".nes"};
    public static final String[] gbBinaryTypes = {".gb"};
    public static final String[] s32xBinaryTypes = {".32x", ".bin", ".md"};
    public static final String[] compressedBinaryTypes = {".gz", ".zip"};

    public static final String[] binaryTypes = Stream.of(
            mdBinaryTypes, sgBinaryTypes, cvBinaryTypes, msxBinaryTypes, smsBinaryTypes, ggBinaryTypes, nesBinaryTypes,
            gbBinaryTypes, s32xBinaryTypes,
            compressedBinaryTypes
    ).flatMap(Stream::of).toArray(String[]::new);

    public static SystemProvider createSystemProvider(Path file, DisplayWindow display, boolean debugPerf) {
        String lowerCaseName = handleCompressedFiles(file, file.toString().toLowerCase());
        if (lowerCaseName == null) {
            LOG.error("Unable to load file: {}", file != null ? file.toAbsolutePath() : "null");
            return null;
        }
        SystemProvider systemProvider = null;
        boolean isGen = Arrays.stream(mdBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean is32x = Arrays.stream(s32xBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isSg = Arrays.stream(sgBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isCv = Arrays.stream(cvBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isMsx = Arrays.stream(msxBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isSms = Arrays.stream(smsBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isGg = Arrays.stream(ggBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isNes = Arrays.stream(nesBinaryTypes).anyMatch(lowerCaseName::endsWith);
        boolean isGb = Arrays.stream(gbBinaryTypes).anyMatch(lowerCaseName::endsWith);
        if (isGen) {
            systemProvider = Megadrive.createNewInstance(display);
        } else if (isSg) {
            systemProvider = Z80BaseSystem.createNewInstance(SystemType.SG_1000, display);
        } else if (isCv) {
            systemProvider = Z80BaseSystem.createNewInstance(SystemType.COLECO, display);
        } else if (isMsx) {
            systemProvider = Z80BaseSystem.createNewInstance(SystemType.MSX, display);
        } else if (isSms) {
            systemProvider = Sms.createNewInstance(SystemType.SMS, display);
        } else if (isGg) {
            systemProvider = Sms.createNewInstance(SystemType.GG, display);
        } else if (isNes) {
            systemProvider = Nes.createNewInstance(SystemType.NES, display);
        } else if (isGb) {
            systemProvider = Gb.createNewInstance(SystemType.GB, display);
        } else if (is32x) {
            systemProvider = Md32x.createNewInstance32x(display, debugPerf);
        }
        if (systemProvider == null) {
            LOG.error("Unable to find a system to load: {}", file.toAbsolutePath());
        }
        return systemProvider;
    }


    public static Map<SoundDevice.SoundDeviceType, SoundDevice> getSoundDevices(SystemType systemType, Region region) {
        Map<SoundDevice.SoundDeviceType, SoundDevice> m = new EnumMap<>(SoundDevice.SoundDeviceType.class);
        m.put(PSG, getPsgProvider(systemType, region));
        m.put(FM, getFmProvider(systemType, region));
        m.put(PWM, getPwmProvider(systemType, region));
        return m;
    }

    public static SoundDevice getPwmProvider(SystemType systemType, Region region) {
        return switch (systemType) {
            case S32X -> Pwm.PWM_USE_BLIP ? new BlipPwmProvider(region) : new S32xPwmProvider(region);
            default -> PwmProvider.NO_SOUND;
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
                psgProvider = PsgProvider.createSnInstance(region, SAMPLE_RATE_HZ);
                break;
        }
        return psgProvider;
    }

    public static SoundDevice getFmProvider(SystemType systemType, Region region) {
        SoundDevice fmProvider = FmProvider.NO_SOUND;
        switch (systemType) {
            case GENESIS:
            case S32X:
                fmProvider = MdFmProvider.createInstance(region, AbstractSoundManager.audioFormat);
                break;
            case SMS:
                if (Sms.ENABLE_FM) {
                    fmProvider = Ym2413Provider.createInstance(AbstractSoundManager.audioFormat);
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
