/*
 * JavaSoundManager2
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 27/10/19 13:04
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

import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.SourceDataLine;
import java.time.Duration;
import java.util.Arrays;

public class JavaSoundManager2 extends AbstractSoundManager {
    private static Logger LOG = LogManager.getLogger(JavaSoundManager2.class.getSimpleName());

    private final Object blocker = new Object();
    private volatile boolean hasOutput;

    private int samplesPerFrameMono;
    private int samplesPerFrameStereo;
    private byte[] psgBuffer = new byte[0];
    private int[] fmBuffer = new int[0];
    private byte[] mixBuffer = new byte[0];

    long lastWrite = 0;

    @Override
    protected Runnable getRunnable(SourceDataLine dataLine, RegionDetector.Region region) {
        return new Runnable() {
            @Override
            public void run() {
                do {
                    do {
                        Util.waitOnObject(blocker);
                    } while (!hasOutput);
                    hasOutput = false;
                    Arrays.fill(mixBuffer, (byte) 0);
                    //FM: stereo 16 bit, PSG: mono 8 bit, OUT: mono 16 bit
                    SoundUtil.intStereo14ToByteMono16Mix(fmBuffer, mixBuffer, psgBuffer);
                    long now = System.nanoTime();
                    LOG.info("latestRun: " + Duration.ofNanos(now - lastWrite).toMillis());
                    lastWrite = now;
                    SoundUtil.writeBufferInternal(dataLine, mixBuffer, mixBuffer.length);
                    now = System.nanoTime();
                    LOG.info("writeSound: " + Duration.ofNanos(now - lastWrite).toMillis());
                    lastWrite = now;
                } while (!close);
            }
        };
    }

    @Override
    protected void init(RegionDetector.Region region) {
        super.init(region);
        samplesPerFrameMono = (int) (region.getFrameIntervalMs() * (SAMPLE_RATE_HZ / 1000d));
        samplesPerFrameStereo = samplesPerFrameMono << 1;
        psgBuffer = new byte[samplesPerFrameMono];  //8 bit mono
        fmBuffer = new int[samplesPerFrameStereo];  //16 bit stereo
        mixBuffer = new byte[samplesPerFrameStereo];  //16 bit mono
    }

    @Override
    public void output(long oneFrame) {
        int availSamples = fm.update(fmBuffer, 0, samplesPerFrameMono);
        psg.output(psgBuffer, 0, samplesPerFrameMono);
        if (availSamples != samplesPerFrameMono) {
            LOG.info((availSamples < samplesPerFrameMono ? "U" : "O") + ": {}, {}",
                    availSamples, samplesPerFrameMono);
            int lastIdx = (availSamples << 1) - 1;
            int lastVal = (fmBuffer[lastIdx] + fmBuffer[lastIdx - 1]) >> 1;
            Arrays.fill(fmBuffer, lastIdx, fmBuffer.length, lastVal);
        }
        synchronized (blocker) {
            hasOutput = true;
            blocker.notifyAll();
        }
    }
}
