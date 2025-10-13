/*
 * JavaSoundManager
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 17:40
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

import omegadrive.sound.SoundDevice;
import omegadrive.sound.SoundDevice.SampleBufferContext;
import omegadrive.system.perf.Telemetry;
import omegadrive.util.*;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static omegadrive.util.SoundUtil.mixTwoSources;

public class JavaSoundManagerBlip extends AbstractSoundManager {

    private static final Logger LOG = LogHelper.getLogger(JavaSoundManagerBlip.class.getSimpleName());

    static class AudioMixContext {
        public volatile int soundDeviceSetup;
        public final Map<SoundDevice.SoundDeviceType, SoundDevice> map;
        public final byte[] mix_buf_bytes16Stereo;

        public AudioMixContext(Map<SoundDevice.SoundDeviceType, SoundDevice> m, byte[] b) {
            map = m;
            mix_buf_bytes16Stereo = b;
        }
    }
    private final AtomicInteger sync = new AtomicInteger();
    private volatile AudioMixContext audioMixContext;

    //stats
    private Telemetry telemetry;
    private volatile int samplesProducedCount, samplesConsumedCount;


    @Override
    public void init(RegionDetector.Region region) {
        super.init(region);
        audioMixContext = new AudioMixContext(activeSoundDeviceMap, new byte[fmSize << 1]);
        telemetry = Telemetry.getInstance();
    }

    @Override
    public void onNewFrame() {
        doStats();
        activeSoundDeviceMap.values().forEach(SoundDevice::onNewFrame);
        int len = activeSoundDeviceMap.values().stream().
                mapToInt(d -> d.getFrameData().stereoBytesLen).max().orElse(0);
        playSound(len);
    }

    //FM,PWM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
    protected static int mixAudioProviders(AudioMixContext amc) {
        int len = 0;
        SoundDevice device = SoundDevice.NO_SOUND;
        SoundDevice fm = amc.map.get(SoundDevice.SoundDeviceType.FM);
        SoundDevice psg = amc.map.get(SoundDevice.SoundDeviceType.PSG);
        switch (amc.soundDeviceSetup) {
            case 1: //fm only
                device = fm;
            case 2: //psg only
                device = device == SoundDevice.NO_SOUND ? psg : device;
                SampleBufferContext sbc = device.getFrameData();
                System.arraycopy(sbc.lineBuffer, 0, amc.mix_buf_bytes16Stereo, 0, sbc.stereoBytesLen);
                len = sbc.stereoBytesLen;
                break;
            case 3: //fm + psg
                len = mixTwoSources(psg.getFrameData().lineBuffer, fm.getFrameData().lineBuffer, amc.mix_buf_bytes16Stereo,
                        psg.getFrameData().stereoBytesLen, fm.getFrameData().stereoBytesLen);
                break;
        }
        return len;
    }

    private void playSound(int inputLen) {
        if (!soundEnabled) {
            return;
        }
        if (inputLen > 0) {
            final long current = sync.incrementAndGet();
            audioMixContext.soundDeviceSetup = soundDeviceSetup;
            final AudioMixContext amc = audioMixContext;
            executorService.submit(Util.wrapRunnableEx(() -> {
                mixAudioProviders(amc);
                SoundUtil.writeBufferInternal(dataLine, amc.mix_buf_bytes16Stereo, 0, inputLen);
                if (BufferUtil.assertionsEnabled) {
                    if (current != sync.get()) {
                        LOG.info("Audio thread too slow: {} vs {}", current, sync.get());
                    }
                }
            }));
        } else {
            LOG.warn("Empty sound buffer!!");
        }
    }

    private void doStats() {
        if (Telemetry.enableLogToFile) {
            telemetry.addSample("audioSamplesConsumed", samplesConsumedCount);
            telemetry.addSample("audioSamplesProduced", samplesProducedCount);
        }
        samplesConsumedCount = samplesProducedCount = 0;
    }
}

