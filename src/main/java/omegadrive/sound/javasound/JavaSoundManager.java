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

import omegadrive.SystemLoader;
import omegadrive.sound.AdaptiveAudioBuffer;
import omegadrive.sound.SoundDevice.SoundDeviceType;
import omegadrive.system.perf.Telemetry;
import omegadrive.util.CircularQueue;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static omegadrive.sound.SoundDevice.SoundDeviceType.*;

public class JavaSoundManager extends AbstractSoundManager {

    private static final Logger LOG = LogHelper.getLogger(JavaSoundManager.class.getSimpleName());

    private final Map<SoundDeviceType, int[]> deviceAudioBuffers = new EnumMap<>(SoundDeviceType.class);
    private byte[] mix_buf_bytes16Stereo, playBufBytes16Stereo;
    private byte[] psg_buf_bytes;

    private AdaptiveAudioBuffer adaptiveAudioBuffer;

    //stats
    private Telemetry telemetry;
    private int samplesProducedCount;

    private int bufferLenMono16;

    public JavaSoundManager(SystemLoader.SystemType type) {
        this.type = type;

    }

    @Override
    public void init(RegionDetector.Region region) {
        super.init(region);
        deviceAudioBuffers.put(FM, new int[fmSize]);
        deviceAudioBuffers.put(PWM, new int[fmSize]);
        deviceAudioBuffers.put(PCM, new int[fmSize]);
        mix_buf_bytes16Stereo = new byte[fmSize << 1];
        playBufBytes16Stereo = new byte[fmSize << 1];
        psg_buf_bytes = new byte[psgSize];
        telemetry = Telemetry.getInstance();
        bufferLenMono16 = (int) (audioFormat.getSampleRate() / region.getFps());
        adaptiveAudioBuffer = new AdaptiveAudioBuffer(bufferLenMono16, new CircularQueue(8));
    }

    private int playOnceStereo(int fmBufferLenMono) {
        int numSamples = fmBufferLenMono;
        int fmMonoActual, pwmMonoActual = 0, pcmMonoActual = 0;
        boolean sameSamples = true;
        if (isEnabled(FM)) {
            fmMonoActual = getFm().updateStereo16(deviceAudioBuffers.get(FM), 0, fmBufferLenMono) >> 1;
            //if FM is present load a matching number of other sources samples
            fmBufferLenMono = fmMonoActual;
        }
        if (isEnabled(PWM)) {
            pwmMonoActual = getPwm().updateStereo16(deviceAudioBuffers.get(PWM), 0, fmBufferLenMono) >> 1;
            sameSamples &= fmBufferLenMono == pwmMonoActual;
            fmBufferLenMono = pwmMonoActual;
        }
        if (isEnabled(PCM)) {
            pcmMonoActual = getPcm().updateStereo16(deviceAudioBuffers.get(PCM), 0, fmBufferLenMono) >> 1;
            sameSamples &= fmBufferLenMono == pcmMonoActual;
            fmBufferLenMono = pcmMonoActual;
        }
        if (isEnabled(PSG)) {
            getPsg().fillBuffer(psg_buf_bytes, 0, fmBufferLenMono);
        }
        if (!sameSamples) {
            LogHelper.logWarnOnce(LOG, "Samples mismatch, needed: {}, fm {}, pwm {}, pcm {}", numSamples,
                    fmBufferLenMono, pwmMonoActual, pcmMonoActual);
        }
        final int fmBufferLenStereo = fmBufferLenMono << 1;
        /**
         * bufferBytesMono = fmBufferLenMono << 1;
         * bufferBytesStereo = bufferBytesMono << 1
         */
        final int bufferBytesStereo = fmBufferLenMono << 2;
        samplesProducedCount += fmBufferLenStereo;

        try {
            Arrays.fill(mix_buf_bytes16Stereo, SoundUtil.ZERO_BYTE);
            mixAudioProviders(fmBufferLenStereo);
            adaptiveAudioBuffer.addSamples(mix_buf_bytes16Stereo, bufferBytesStereo);
            if (isRecording()) {
                soundPersister.persistSound(DEFAULT_SOUND_TYPE, mix_buf_bytes16Stereo);
            }

        } catch (Exception e) {
            LOG.error("Unexpected sound error", e);
        }
        Arrays.fill(deviceAudioBuffers.get(FM), 0);
        Arrays.fill(deviceAudioBuffers.get(PWM), 0);
        Arrays.fill(deviceAudioBuffers.get(PCM), 0);
        Arrays.fill(psg_buf_bytes, SoundUtil.ZERO_BYTE);
        return fmBufferLenStereo;
    }

    //FM,PWM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
    protected void mixAudioProviders(int inputLen) {
        if (!soundEnabled) {
            return;
        }
        switch (soundDeviceSetup) {
            case 0:
                break;
            case 1: //fm only
                SoundUtil.intStereo16ToByteStereo16Mix(deviceAudioBuffers.get(FM), mix_buf_bytes16Stereo, inputLen);
                break;
            case 2: //psg only
                SoundUtil.byteMono8ToByteStereo16Mix(psg_buf_bytes, mix_buf_bytes16Stereo);
                break;
            case 3: //fm + psg
                SoundUtil.intStereo14ToByteStereo16Mix(deviceAudioBuffers.get(FM), mix_buf_bytes16Stereo, psg_buf_bytes, inputLen);
                break;
            case 4: //pwm only
                SoundUtil.intStereo16ToByteStereo16Mix(deviceAudioBuffers.get(PWM), mix_buf_bytes16Stereo, inputLen);
                break;
            case 5: //fm + pwm
                SoundUtil.intStereo14ToByteStereo16PwmMix(mix_buf_bytes16Stereo, deviceAudioBuffers.get(FM), deviceAudioBuffers.get(PWM), inputLen);
                break;
            case 6: //pwm + psg
                SoundUtil.intStereo14ToByteStereo16Mix(deviceAudioBuffers.get(PWM), mix_buf_bytes16Stereo, psg_buf_bytes, inputLen);
                break;
            case 7: //fm + psg + pwm
                SoundUtil.intStereo14ToByteStereo16PsgPwmMix(mix_buf_bytes16Stereo, deviceAudioBuffers.get(FM), deviceAudioBuffers.get(PWM), psg_buf_bytes, inputLen);
                break;
            case 11: //fm + psg + pcm
                SoundUtil.intStereo14ToByteStereo16PsgPwmMix(mix_buf_bytes16Stereo, deviceAudioBuffers.get(FM),
                        deviceAudioBuffers.get(PCM), psg_buf_bytes, inputLen);
                break;
            default:
                LOG.error("Unable to mix the sound setup: {}", soundDeviceSetup);
                break;
        }
    }

    private final AtomicInteger sync = new AtomicInteger();

    @Override
    public void onNewFrame() {
        doStats();
        getFm().onNewFrame();
        playOnceStereo(bufferLenMono16);
        final int frameId = sync.incrementAndGet();
        executorService.submit(() -> {
            int nowFrameId = sync.get();
            if (nowFrameId != frameId) {
                LogHelper.logWarnOnce(LOG, "Audio delay in frames (MAX): {}", nowFrameId - frameId);
            }
            int num = adaptiveAudioBuffer.read(playBufBytes16Stereo, playBufBytes16Stereo.length);
            SoundUtil.writeBufferInternal(dataLine, playBufBytes16Stereo, num);
        });
    }

    private void doStats() {
        if (Telemetry.enableLogToFile) {
            telemetry.addSample("audioSamplesProduced", samplesProducedCount);
        }
        samplesProducedCount = 0;
    }
}

