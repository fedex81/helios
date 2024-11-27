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

import omegadrive.sound.SoundDevice.SampleBufferContext;
import omegadrive.system.perf.Telemetry;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class JavaSoundManager2 extends AbstractSoundManager {

    private static final Logger LOG = LogHelper.getLogger(JavaSoundManager2.class.getSimpleName());
    private byte[] mix_buf_bytes16Stereo;
    private final AtomicInteger sync = new AtomicInteger();

    //stats
    private Telemetry telemetry;
    private volatile int samplesProducedCount, samplesConsumedCount;

    @Override
    public void init(RegionDetector.Region region) {
        super.init(region);
        mix_buf_bytes16Stereo = new byte[fmSize << 1];
        telemetry = Telemetry.getInstance();
    }

    @Override
    public void onNewFrame() {
        doStats();
        psg.onNewFrame();
        fm.onNewFrame();
        int len = mixAudioProviders();
        playSound(len);
    }

    //FM,PWM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
    protected int mixAudioProviders() {
        if (!soundEnabled) {
            return 0;
        }
        int len = 0;
        switch (soundDeviceSetup) {
            case 1: //fm only
                System.arraycopy(fm.getFrameData().lineBuffer, 0, mix_buf_bytes16Stereo, 0, fm.getFrameData().stereoBytesLen);
                len = fm.getFrameData().stereoBytesLen;
                break;
            case 2: //psg only
                System.arraycopy(psg.getFrameData().lineBuffer, 0, mix_buf_bytes16Stereo, 0, psg.getFrameData().stereoBytesLen);
                len = psg.getFrameData().stereoBytesLen;
                break;
            case 3: //fm + psg
                SoundUtil.mixTwoSources(psg.getFrameData().lineBuffer, fm.getFrameData().lineBuffer, mix_buf_bytes16Stereo,
                        psg.getFrameData().stereoBytesLen, fm.getFrameData().stereoBytesLen);
                //TODO ugly hack
                len = Math.min(psg.getFrameData().stereoBytesLen, fm.getFrameData().stereoBytesLen);
                break;
            case 6: //pwm + psg
//                SoundUtil.intStereo14ToByteStereo16Mix(pwm_buf_ints, mix_buf_bytes16Stereo, psg_buf_bytes, inputLen);
                throw new RuntimeException("" + soundDeviceSetup);
//                break;
            case 7: //fm + psg + pwm
//                SoundUtil.intStereo14ToByteStereo16PwmMix(mix_buf_bytes16Stereo, fm_buf_ints, pwm_buf_ints, psg_buf_bytes, inputLen);
//                break;
                throw new RuntimeException("" + soundDeviceSetup);
            case 11: //fm + psg + pcm
//                SoundUtil.intStereo14ToByteStereo16PwmMix(mix_buf_bytes16Stereo, fm_buf_ints, pcm_buf_ints, psg_buf_bytes, inputLen);
//                break;
                throw new RuntimeException("" + soundDeviceSetup);
            default:
                LOG.error("Unable to mix the sound setup: {}", soundDeviceSetup);
                break;
        }
        return len;
    }

    private void playSound(SampleBufferContext context) {
        if (context.stereoBytesLen > 0) {
            System.arraycopy(context.lineBuffer, 0, mix_buf_bytes16Stereo, 0, context.stereoBytesLen);
            playSound(context.stereoBytesLen);
        }
    }

    private long lastFrame = -1;

    private void playSound(int inputLen) {
        long frame = Telemetry.getInstance().getFrameCounter();
        if (frame == lastFrame) {
            LogHelper.logWarnOnce(LOG, "Duplicate playSound call!!");
            return;
        }
        lastFrame = frame;
        if (inputLen > 0) {
            final long current = sync.incrementAndGet();
            executorService.submit(() -> {
                SoundUtil.writeBufferInternal(dataLine, mix_buf_bytes16Stereo, 0, inputLen);
                if (BufferUtil.assertionsEnabled) {
                    if (current != sync.get()) {
                        LOG.info("Audio thread too slow: {} vs {}", current, sync.get());
                    }
                }
            });
        } else {
            LOG.warn("Empty sound buffer!!");
        }
    }



    private void doStats() {
        if (Telemetry.enable) {
            telemetry.addSample("audioSamplesConsumed", samplesConsumedCount);
            telemetry.addSample("audioSamplesProduced", samplesProducedCount);
        }
        samplesConsumedCount = samplesProducedCount = 0;
    }
}

