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
        fm.onNewFrame();
        SampleBufferContext psgContext = psg.getFrameData();
        playSound(psgContext);
    }

    private void playSound(SampleBufferContext context) {
        if (context.stereoBytesLen > 0) {
            System.arraycopy(context.lineBuffer, 0, mix_buf_bytes16Stereo, 0, context.stereoBytesLen);
            final int len = context.stereoBytesLen;
            final long current = sync.incrementAndGet();
            executorService.submit(() -> {
                SoundUtil.writeBufferInternal(dataLine, mix_buf_bytes16Stereo, 0, len);
                if (BufferUtil.assertionsEnabled) {
                    if (current != sync.get()) {
                        LOG.info("{} Audio thread too slow: {} vs {}", current, sync.get());
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

