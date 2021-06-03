package omegadrive.system;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.ym2413.Ym2413Provider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.gb.GbSoundWrapper;
import omegadrive.system.nes.NesSoundWrapper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Size;
import omegadrive.vdp.model.BaseVdpAdapter;
import omegadrive.vdp.model.BaseVdpProvider;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static omegadrive.sound.SoundProvider.SAMPLE_RATE_HZ;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class SysUtil {

    public static PsgProvider getPsgProvider(SystemLoader.SystemType systemType, RegionDetector.Region region) {
        PsgProvider psgProvider = PsgProvider.NO_SOUND;
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

    public static FmProvider getFmProvider(SystemLoader.SystemType systemType, RegionDetector.Region region) {
        FmProvider fmProvider = FmProvider.NO_SOUND;
        switch (systemType) {
            case GENESIS:
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

    static class OptionalKeyListener implements KeyListener {
        KeyListener target;

        @Override
        public void keyTyped(KeyEvent e) {
            if (target != null) target.keyTyped(e);
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (target != null) target.keyPressed(e);
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (target != null) target.keyReleased(e);
        }

        public void setTarget(KeyListener target) {
            this.target = target;
        }
    }


}
