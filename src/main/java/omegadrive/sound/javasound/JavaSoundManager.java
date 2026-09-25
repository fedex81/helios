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
        deviceAudioBuffers.put(CDDA, new int[fmSize]);
        mix_buf_bytes16Stereo = new byte[fmSize << 1];
        playBufBytes16Stereo = new byte[fmSize << 1];
        psg_buf_bytes = new byte[psgSize];
        telemetry = Telemetry.getInstance();
        bufferLenMono16 = (int) (audioFormat.getSampleRate() / region.getFps());
        adaptiveAudioBuffer = new AdaptiveAudioBuffer(bufferLenMono16, new CircularQueue(8));
    }

    private final StringBuilder sb = new StringBuilder();

    private int playOnceStereo(int bufferLenMono) {
        int numSamples = bufferLenMono;
        sb.setLength(0);
        if (isEnabled(FM)) {
            int actual = getFm().updateStereo16(deviceAudioBuffers.get(FM), 0, bufferLenMono) >> 1;
            checkSamples(sb, FM, bufferLenMono, actual);
            //TODO FM should be stretched as well
//            bufferLenMono = fmMonoActual;
        }
        if (isEnabled(PWM)) {
            int actual = getPwm().updateStereo16(deviceAudioBuffers.get(PWM), 0, bufferLenMono) >> 1;
            checkSamples(sb, PWM, bufferLenMono, actual);
        }
        if (isEnabled(PCM)) {
            int actual = getPcm().updateStereo16(deviceAudioBuffers.get(PCM), 0, bufferLenMono) >> 1;
            checkSamples(sb, PCM, bufferLenMono, actual);
        }
        if (isEnabled(CDDA)) {
            int actual = getCdda().updateStereo16(deviceAudioBuffers.get(CDDA), 0, bufferLenMono) >> 1;
            checkSamples(sb, CDDA, bufferLenMono, actual);
        }
        if (isEnabled(PSG)) {
            getPsg().fillBuffer(psg_buf_bytes, 0, bufferLenMono);
        }
        if (sb.length() > 0) {
//            LOG.warn("Audio samples mismatch, ref: {}, " + sb, numSamples);
            LogHelper.logWarnOnce(LOG, "Audio samples mismatch, ref: {}" + sb, numSamples); //TODO fix PCM
        }
        final int bufferLenStereo = bufferLenMono << 1;
        /**
         * bufferBytesMono = bufferLenMono << 1;
         * bufferBytesStereo = bufferBytesMono << 1
         */
        final int bufferBytesStereo = bufferLenMono << 2;
        samplesProducedCount += bufferLenStereo;

        try {
            Arrays.fill(mix_buf_bytes16Stereo, SoundUtil.ZERO_BYTE);
            if (!isMute()) {
                SoundUtil.mix(soundDeviceSetup, deviceAudioBuffers, psg_buf_bytes, mix_buf_bytes16Stereo, bufferLenStereo);
            }
            adaptiveAudioBuffer.addSamples(mix_buf_bytes16Stereo, bufferBytesStereo);
            if (isRecording()) {
                soundPersister.persistSound("MIX", mix_buf_bytes16Stereo);
            }

        } catch (Exception e) {
            LOG.error("Unexpected sound error", e);
        }
        clearData();
        return bufferLenStereo;
    }

    private void checkSamples(StringBuilder s, SoundDeviceType sdt, int ref, int act) {
        if (ref != act) {
            s.append(", " + sdt + ": " + act);
        }
    }

    private void clearData() {
        if (isEnabled(FM)) Arrays.fill(deviceAudioBuffers.get(FM), 0);
        if (isEnabled(PWM)) Arrays.fill(deviceAudioBuffers.get(PWM), 0);
        if (isEnabled(PCM)) Arrays.fill(deviceAudioBuffers.get(PCM), 0);
        if (isEnabled(CDDA)) Arrays.fill(deviceAudioBuffers.get(CDDA), 0);
        if (isEnabled(PSG)) Arrays.fill(psg_buf_bytes, SoundUtil.ZERO_BYTE);
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

