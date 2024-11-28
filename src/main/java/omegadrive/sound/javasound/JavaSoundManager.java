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
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class JavaSoundManager extends AbstractSoundManager {

    private static final Logger LOG = LogHelper.getLogger(JavaSoundManager.class.getSimpleName());
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
        pwm.newFrame();
        int len = mixAudioProviders();
        playSound(len);
    }

    //FM,PWM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
    protected int mixAudioProviders() {
        if (!soundEnabled) {
            return 0;
        }
        Arrays.fill(mix_buf_bytes16Stereo, (byte) 0);
        int len = switch (soundDeviceSetup) {
            //fm only
            case 1 -> mix(fm);
            //psg only
            case 2 -> mix(psg);
            //fm + psg
            case 3 -> mix(psg, fm);
            //pwm + psg
            case 6 -> mix(psg, pwm);
            //fm + psg + pwm
            case 7 -> mix(psg, fm, pwm);
            //fm + psg + pcm
            case 11 -> mix(psg, fm, pcm);
            default -> {
                LOG.error("Unable to mix the sound setup: {}", soundDeviceSetup);
                yield 0;
            }
        };
        return len;
    }

    private int mix(SoundDevice src) {
        System.arraycopy(src.getFrameData().lineBuffer, 0, mix_buf_bytes16Stereo, 0, src.getFrameData().stereoBytesLen);
        return src.getFrameData().stereoBytesLen;
    }

    private int mix(SoundDevice src1, SoundDevice src2) {
        return SoundUtil.mixTwoSources(src1.getFrameData(), src2.getFrameData(), mix_buf_bytes16Stereo);
    }

    private int mix(SoundDevice src1, SoundDevice src2, SoundDevice src3) {
        int len = mix(src1, src2);
        if (src3.getFrameData().stereoBytesLen > 0) {
            len = SoundUtil.mixTwoSources(src3.getFrameData().lineBuffer, mix_buf_bytes16Stereo, mix_buf_bytes16Stereo,
                    src3.getFrameData().stereoBytesLen, len);
        }
        return len;
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

