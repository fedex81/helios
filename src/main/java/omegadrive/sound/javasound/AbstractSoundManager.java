/*
 * AbstractSoundManager
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 27/10/19 13:12
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

package omegadrive.sound.javasound;

import mcd.pcm.BlipPcmProvider;
import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.sound.PcmProvider;
import omegadrive.sound.PwmProvider;
import omegadrive.sound.SoundDevice;
import omegadrive.sound.SoundDevice.SoundDeviceType;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.persist.FileSoundPersister;
import omegadrive.sound.persist.SoundPersister;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SysUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.PriorityThreadFactory;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import org.slf4j.Logger;
import s32x.pwm.BlipPwmProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractSoundManager implements SoundProvider {
    private static final Logger LOG = LogHelper.getLogger(AbstractSoundManager.class.getSimpleName());

    protected static final SoundPersister.SoundType DEFAULT_SOUND_TYPE = SoundPersister.SoundType.BOTH;
    private static final int OUTPUT_SAMPLE_SIZE = 16;
    private static final int OUTPUT_CHANNELS = 2;
    public static final AudioFormat audioFormat = new AudioFormat(SoundProvider.SAMPLE_RATE_HZ, OUTPUT_SAMPLE_SIZE, OUTPUT_CHANNELS, true, false);
    public volatile boolean close;

    protected ExecutorService executorService;
    protected volatile Map<SoundDeviceType, SoundDevice> soundDeviceMap;
    protected volatile PsgProvider psg;
    protected volatile FmProvider fm;
    protected volatile PwmProvider pwm;
    protected volatile PcmProvider pcm;
    protected SoundPersister soundPersister;
    protected int fmSize, psgSize;

    protected SourceDataLine dataLine;
    protected boolean soundEnabled = true;
    private SystemLoader.SystemType type;
    protected RegionDetector.Region region;
    protected volatile int soundDeviceSetup = SoundDeviceType.NONE.getBit();

    protected List<SoundDevice.MutableDevice> mutableDeviceList = new ArrayList<>();
    protected AtomicBoolean initedOnce = new AtomicBoolean(false);

    public static SoundProvider createSoundProvider(SystemLoader.SystemType systemType) {
        if (!ENABLE_SOUND) {
            LOG.warn("Sound disabled");
            return NO_SOUND;
        }
        AbstractSoundManager jsm = JAL_SOUND_MGR ? new JalSoundManager() : new JavaSoundManager();
        jsm.type = systemType;
        return jsm;
    }

    @Override
    public void init(RegionDetector.Region region) {
        this.region = region;
        soundDeviceMap = SysUtil.getSoundDevices(type, region);
        psg = (PsgProvider) soundDeviceMap.get(SoundDeviceType.PSG);
        fm = (FmProvider) soundDeviceMap.get(SoundDeviceType.FM);
        pwm = (PwmProvider) soundDeviceMap.get(SoundDeviceType.PWM);
        pcm = (PcmProvider) soundDeviceMap.get(SoundDeviceType.PCM);
        updateSoundDeviceSetup();
        soundPersister = new FileSoundPersister();
        fmSize = SoundProvider.getFmBufferIntSize(audioFormat);
        psgSize = SoundProvider.getPsgBufferByteSize(audioFormat);
        init();
    }

    protected void updateSoundDeviceSetup() {
        soundDeviceSetup = 0;
        soundDeviceSetup |= (fm != FmProvider.NO_SOUND) ? SoundDeviceType.FM.getBit() : 0;
        soundDeviceSetup |= (psg != PsgProvider.NO_SOUND) ? SoundDeviceType.PSG.getBit() : 0;
        soundDeviceSetup |= (pwm != PwmProvider.NO_SOUND) ? SoundDeviceType.PWM.getBit() : 0;
        soundDeviceSetup |= (pcm != PcmProvider.NO_SOUND) ? SoundDeviceType.PCM.getBit() : 0;
    }

    @Override
    public void init() {
        assert initedOnce.compareAndSet(false, true);
        assert dataLine == null && executorService == null;

        dataLine = SoundUtil.createDataLine(audioFormat);
        executorService = Executors.newSingleThreadExecutor
                (new PriorityThreadFactory(Thread.MAX_PRIORITY, AbstractSoundManager.class.getSimpleName()));
        LOG.info("Output audioFormat: {}, bufferSize: {}, region: {}", audioFormat, fmSize, region);
    }

    @Override
    public PsgProvider getPsg() {
        return psg;
    }

    @Override
    public FmProvider getFm() {
        return fm;
    }

    @Override
    public PwmProvider getPwm() {
        return pwm;
    }

    @Override
    public PcmProvider getPcm() {
        return pcm;
    }

    @Override
    public void reset() {
        LOG.info("Resetting sound");
        close = true;
        if (initedOnce.get()) {
            List<Runnable> list = executorService.shutdownNow();
            LOG.info("Closing sound, stopping background tasks: #{}", list.size());
            SoundUtil.close(dataLine);
            setRecording(false);
        }
    }

    @Override
    public void close() {
        reset();
    }

    @Override
    public boolean isRecording() {
        return soundPersister.isRecording();
    }

    @Override
    public void setRecording(boolean recording) {
        if (isRecording() && !recording) {
            soundPersister.stopRecording();
        } else if (!isRecording() && recording) {
            soundPersister.startRecording(DEFAULT_SOUND_TYPE);
        }
    }

    @Override
    public void addExternalSoundSource(SoundDevice.MutableDevice mutableDevice) {
        mutableDeviceList.add(mutableDevice);
    }

    @Override
    public boolean isMute() {
        return !soundEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        LOG.info("Set sound enabled: {}", enabled);
        //TODO hack
        BlipPcmProvider.mute = !enabled;
        BlipPwmProvider.mute = !enabled;
//        mutableDeviceList.forEach(d -> d.setEnabled(enabled));
    }

    //SMS only
    @Override
    public void setEnabled(Device device, boolean enabled) {
        if (fm == device) {
            boolean isEnabled = fm != FmProvider.NO_SOUND;
            if (isEnabled != enabled) {
                this.fm = enabled ? (FmProvider) soundDeviceMap.get(SoundDeviceType.FM) : FmProvider.NO_SOUND;
                updateSoundDeviceSetup();
                LOG.info("FM enabled: {}", enabled);
            }
        } else if (psg == device) {
            boolean isEnabled = psg != PsgProvider.NO_SOUND;
            if (isEnabled != enabled) {
                this.psg = (PsgProvider) (enabled ? soundDeviceMap.get(SoundDeviceType.PSG) : PsgProvider.NO_SOUND);
                updateSoundDeviceSetup();
                LOG.info("PSG enabled: {}", enabled);
            }
        } else if (pwm == device) {
            boolean isEnabled = pwm != PwmProvider.NO_SOUND;
            if (isEnabled != enabled) {
                this.pwm = (PwmProvider) (enabled ? soundDeviceMap.get(SoundDeviceType.PWM) : PwmProvider.NO_SOUND);
                updateSoundDeviceSetup();
                LOG.info("PWM enabled: {}", enabled);
            }
        } else if (pcm == device) {
            boolean isEnabled = pcm != PcmProvider.NO_SOUND;
            if (isEnabled != enabled) {
                this.pcm = (PcmProvider) (enabled ? soundDeviceMap.get(SoundDeviceType.PCM) : PcmProvider.NO_SOUND);
                updateSoundDeviceSetup();
                LOG.info("PCM enabled: {}", enabled);
            }
        }
    }
}
