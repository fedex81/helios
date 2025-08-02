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
import omegadrive.system.perf.Telemetry;
import omegadrive.util.*;
import org.slf4j.Logger;

import java.util.Arrays;

import static omegadrive.sound.SoundDevice.SoundDeviceType.*;

public class JavaSoundManager extends AbstractSoundManager {

    private static final Logger LOG = LogHelper.getLogger(JavaSoundManager.class.getSimpleName());

    public static int sleepTotal = 0;
    public static final long EMPTY_QUEUE_SLEEP_NS = 500_000;

    volatile int[] fm_buf_ints, pwm_buf_ints, pcm_buf_ints;
    volatile byte[] mix_buf_bytes16Stereo;
    volatile byte[] psg_buf_bytes;
    volatile int fmSizeMono;

    //stats
    private Telemetry telemetry;
    private volatile int samplesProducedCount, samplesConsumedCount, audioThreadLoops, audioThreadEmptyLoops;

    @Override
    public void init(RegionDetector.Region region) {
        super.init(region);
        fm_buf_ints = new int[fmSize];
        pwm_buf_ints = new int[fmSize];
        pcm_buf_ints = new int[fmSize];
        mix_buf_bytes16Stereo = new byte[fm_buf_ints.length << 1];
        psg_buf_bytes = new byte[psgSize];
        fmSizeMono = (int) Math.round(fmSize / 2d);
        telemetry = Telemetry.getInstance();
        executorService.submit(getRunnable());
    }

    private int playOnceStereo(int fmBufferLenMono) {
        int fmMonoActual = getFm().updateStereo16(fm_buf_ints, 0, fmBufferLenMono) >> 1;
        //if FM is present load a matching number of psg samples
        fmBufferLenMono = (soundDeviceSetup & FM.getBit()) > 0 ? fmMonoActual : fmBufferLenMono;
        int pwmMonoActual = getPwm().updateStereo16(pwm_buf_ints, 0, fmBufferLenMono) >> 1;
        int pcmMonoActual = getPcm().updateStereo16(pcm_buf_ints, 0, fmBufferLenMono) >> 1;
        fmBufferLenMono = (soundDeviceSetup & PWM.getBit()) > 0 ? pwmMonoActual : fmBufferLenMono;
        fmBufferLenMono = (soundDeviceSetup & PCM.getBit()) > 0 ? pcmMonoActual : fmBufferLenMono;
        getPsg().updateMono8(psg_buf_bytes, 0, fmBufferLenMono);

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
            SoundUtil.writeBufferInternal(dataLine, mix_buf_bytes16Stereo, bufferBytesStereo);

            if (isRecording()) {
                soundPersister.persistSound(DEFAULT_SOUND_TYPE, mix_buf_bytes16Stereo);
            }

        } catch (Exception e) {
            LOG.error("Unexpected sound error", e);
        }
        Arrays.fill(fm_buf_ints, 0);
        Arrays.fill(psg_buf_bytes, SoundUtil.ZERO_BYTE);
        return fmBufferLenStereo;
    }

    private Runnable getRunnable() {
        return Util.wrapRunnableEx(() -> {
            final int monoSize = Math.min(fmSizeMono, SoundUtil.getMonoSamplesBufferSize(audioFormat, 25));
            try {
                do {
                    int actualStereo = playOnceStereo(monoSize);
                    samplesConsumedCount += actualStereo;
                    if (actualStereo <= 10) {
                        audioThreadEmptyLoops++;
                        Sleeper.parkExactly(EMPTY_QUEUE_SLEEP_NS);
                    }
                    audioThreadLoops++;
                } while (!close);
            } catch (Exception | Error e) {
                LOG.error("Unexpected sound error, stopping", e);
            }
            LOG.info("Stopping sound thread");
            soundDeviceMap.values().forEach(SoundDevice::reset);
        });
    }

    //FM,PWM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
    protected void mixAudioProviders(int inputLen) {
        if (!soundEnabled) {
            return;
        }
        switch (soundDeviceSetup) {
            case 1: //fm only
                SoundUtil.intStereo16ToByteStereo16Mix(fm_buf_ints, mix_buf_bytes16Stereo, inputLen);
                break;
            case 2: //psg only
                SoundUtil.byteMono8ToByteStereo16Mix(psg_buf_bytes, mix_buf_bytes16Stereo);
                break;
            case 3: //fm + psg
                SoundUtil.intStereo14ToByteStereo16Mix(fm_buf_ints, mix_buf_bytes16Stereo, psg_buf_bytes, inputLen);
                break;
            case 6: //pwm + psg
                SoundUtil.intStereo14ToByteStereo16Mix(pwm_buf_ints, mix_buf_bytes16Stereo, psg_buf_bytes, inputLen);
                break;
            case 7: //fm + psg + pwm
                SoundUtil.intStereo14ToByteStereo16PwmMix(mix_buf_bytes16Stereo, fm_buf_ints, pwm_buf_ints, psg_buf_bytes, inputLen);
                break;
            case 11: //fm + psg + pcm
                SoundUtil.intStereo14ToByteStereo16PwmMix(mix_buf_bytes16Stereo, fm_buf_ints, pcm_buf_ints, psg_buf_bytes, inputLen);
                break;
            default:
                LOG.error("Unable to mix the sound setup: {}", soundDeviceSetup);
                break;
        }
    }

    @Override
    public void onNewFrame() {
        doStats();
        getFm().onNewFrame();
    }

    private void doStats() {
        if (Telemetry.enableLogToFile) {
            telemetry.addSample("audioThreadLoops", audioThreadLoops);
            telemetry.addSample("audioThreadEmptyLoops", audioThreadEmptyLoops);
            telemetry.addSample("audioSamplesConsumed", samplesConsumedCount);
            telemetry.addSample("audioSamplesProduced", samplesProducedCount);
        }
        audioThreadLoops = audioThreadEmptyLoops = samplesConsumedCount = samplesProducedCount = 0;
    }
}

