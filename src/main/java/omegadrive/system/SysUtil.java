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
import omegadrive.system.gb.GbSoundWrapper;
import omegadrive.system.nes.NesSoundWrapper;
import omegadrive.util.Size;
import omegadrive.vdp.model.BaseVdpAdapter;
import omegadrive.vdp.model.BaseVdpProvider;

import java.util.*;

import static omegadrive.sound.SoundDevice.SoundDeviceType.*;
import static omegadrive.sound.SoundProvider.SAMPLE_RATE_HZ;
import static omegadrive.util.RegionDetector.Region;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class SysUtil {


    public static Map<SoundDevice.SoundDeviceType, SoundDevice> getSoundDevices(SystemType systemType, Region region) {
        Map<SoundDevice.SoundDeviceType, SoundDevice> m = new HashMap<>();
        m.put(PSG, getPsgProvider(systemType, region));
        m.put(FM, getFmProvider(systemType, region));
        m.put(PWM, getPwmProvider(systemType, region));
        return m;
    }

    public static SoundDevice getPwmProvider(SystemType systemType, Region region) {
        return PwmProvider.NO_SOUND;
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
                fmProvider = new NesSoundWrapper(region, AbstractSoundManager.audioFormat);
                break;
            case GB:
                fmProvider = new GbSoundWrapper(region, AbstractSoundManager.audioFormat);
                break;
            default:
                break;
        }
        return fmProvider;
    }

    public static final BaseBusProvider NO_OP_BUS = new BaseBusProvider() {
        @Override
        public long read(long address, Size size) {
            return 0;
        }

        @Override
        public void write(long address, long data, Size size) {

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
