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

import com.google.common.collect.ImmutableMap;
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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractSoundManager implements SoundProvider {
    private static final Logger LOG = LogHelper.getLogger(AbstractSoundManager.class.getSimpleName());

    protected static final SoundPersister.SoundType DEFAULT_SOUND_TYPE = SoundPersister.SoundType.BOTH;
    private static final int OUTPUT_SAMPLE_SIZE = 16;
    private static final int OUTPUT_CHANNELS = 2;
    public static final AudioFormat audioFormat = new AudioFormat(SoundProvider.SAMPLE_RATE_HZ, OUTPUT_SAMPLE_SIZE, OUTPUT_CHANNELS, true, false);

    protected static final Map<SoundDeviceType, SoundDevice> noSoundMap = ImmutableMap.of(
            SoundDeviceType.FM, FmProvider.NO_SOUND,
            SoundDeviceType.PSG, PsgProvider.NO_SOUND,
            SoundDeviceType.PWM, PwmProvider.NO_SOUND,
            SoundDeviceType.PCM, PcmProvider.NO_SOUND
    );
    public volatile boolean close;

    protected ExecutorService executorService;
    protected volatile Map<SoundDeviceType, SoundDevice> soundDeviceMap, activeSoundDeviceMap;
    protected SoundPersister soundPersister;
    protected int fmSize, psgSize;

    protected SourceDataLine dataLine;
    protected boolean soundEnabled = true;
    protected SystemLoader.SystemType type;
    protected RegionDetector.Region region;
    protected volatile int soundDeviceSetup = SoundDeviceType.NONE.getBit();

    public static final boolean BLIP_SOUND_MANAGER = false;

    protected List<SoundDevice.MutableDevice> mutableDeviceList = new ArrayList<>();
    protected AtomicBoolean initedOnce = new AtomicBoolean(false);

    protected static AtomicReference<Map<SoundDeviceType, SoundDevice>> sdRef = new AtomicReference<>();

    public static SoundProvider createSoundProvider(SystemLoader.SystemType systemType) {
        if (!ENABLE_SOUND) {
            LOG.warn("Sound disabled");
            return NO_SOUND;
        }
        AbstractSoundManager jsm = JAL_SOUND_MGR ? new JalSoundManager(systemType) :
                (BLIP_SOUND_MANAGER ? new JavaSoundManagerBlip(systemType) : new JavaSoundManager(systemType));
        return jsm;
    }

    @Override
    public void init(RegionDetector.Region region) {
        this.region = region;
        if (sdRef.get() == null) {
            sdRef.set(SysUtil.getSoundDevices(type, region));
        }
        activeSoundDeviceMap = new EnumMap<>(sdRef.get());
        soundDeviceMap = ImmutableMap.copyOf(activeSoundDeviceMap);
        updateSoundDeviceSetup();
        soundPersister = new FileSoundPersister();
        fmSize = SoundProvider.getFmBufferIntSize(audioFormat);
        psgSize = SoundProvider.getPsgBufferByteSize(audioFormat);
        init();
    }

    protected void updateSoundDeviceSetup() {
        soundDeviceSetup = 0;
        for (var entry : activeSoundDeviceMap.entrySet()) {
            SoundDeviceType type = entry.getKey();
            SoundDevice current = entry.getValue();
            SoundDevice noSound = noSoundMap.get(type);
            assert current != null && noSound != null;
            soundDeviceSetup |= current != noSound ? type.getBit() : 0;
        }
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
        return (PsgProvider) activeSoundDeviceMap.get(SoundDeviceType.PSG);
    }

    @Override
    public FmProvider getFm() {
        return (FmProvider) activeSoundDeviceMap.get(SoundDeviceType.FM);
    }

    @Override
    public PwmProvider getPwm() {
        return (PwmProvider) activeSoundDeviceMap.get(SoundDeviceType.PWM);
    }

    @Override
    public PcmProvider getPcm() {
        return (PcmProvider) activeSoundDeviceMap.get(SoundDeviceType.PCM);
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

    @Override
    public void setEnabled(Device device, boolean enabled) {
        if (device instanceof SoundDevice sd) {
            SoundDevice noSound = noSoundMap.get(sd.getType());
            assert noSound != null;
            boolean isEnabledNow = device != noSound;
            if (enabled != isEnabledNow) {
                SoundDevice playDevice = soundDeviceMap.get(sd.getType());
                SoundDevice currentDevice = enabled ? playDevice : noSound;
                activeSoundDeviceMap.put(sd.getType(), currentDevice);
                updateSoundDeviceSetup();
                LOG.info("{} enabled: {}", sd.getType(), isEnabledNow);
            }
        }
    }

    protected boolean isEnabled(SoundDeviceType sdt) {
        return (soundDeviceSetup & sdt.getBit()) > 0;
    }

    public void setDisablePermanent(SoundDevice.SoundDeviceType sdt) {
        SoundDevice ns = noSoundMap.get(sdt);
        activeSoundDeviceMap.put(sdt, ns);
        setSoundDeviceMap(sdt, ns);
        updateSoundDeviceSetup();
    }

    public void setSoundDeviceMap(SoundDevice.SoundDeviceType sdt, SoundDevice sd) {
        var m = new HashMap<>(soundDeviceMap);
        m.put(sdt, sd);
        soundDeviceMap = m;
        setEnabled(sd, true);
    }

    @Override
    public void updateDeviceRate(SoundDeviceType sdt, RegionDetector.Region r, int clockRateHz) {
        assert soundDeviceMap.containsKey(sdt) && activeSoundDeviceMap.containsKey(sdt);
        soundDeviceMap.get(sdt).updateRate(r, clockRateHz);
        activeSoundDeviceMap.get(sdt).updateRate(r, clockRateHz);
    }
}
