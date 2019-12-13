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

import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.stream.IntStream;

public class JavaSoundManager2 extends AbstractSoundManager {
    private static Logger LOG = LogManager.getLogger(JavaSoundManager2.class.getSimpleName());

    private final Object blocker = new Object();
    private volatile boolean hasOutput;

    public final static int BUFFER_SIZE_SHIFT = 2; //1 -> double the size
    boolean useThread = true;
    private byte[] psgBuffer = new byte[0];
    private int[] fmBuffer = new int[0];
    private byte[] mixBuffer = new byte[0];
    boolean verbose = false;

    long lastWrite = 0;
    int start = 0;
    private int samplesPerFrame8Mono;
    private int samplesPerFrame16Mono;
    private byte[] oneMsBuffer = new byte[44];

    @Override
    protected Runnable getRunnable(SourceDataLine dataLine, RegionDetector.Region region) {
        return new Runnable() {
            @Override
            public void run() {
                int limit = dataLine.getBufferSize() - oneMsBuffer.length;
                int avail = 0;
                do {
                    do {
                        dataLine.available();
                        avail = dataLine.available();
                        if (avail >= limit) {
                            LogHelper.printLevel(LOG, Level.INFO, "Buffer size: {}", avail, verbose);
                            SoundUtil.writeBufferInternal(dataLine, oneMsBuffer, 0, oneMsBuffer.length);
                        }
                        Util.waitOnObject(blocker, 1);
                    } while (!hasOutput);
                    hasOutput = false;
                    mixAndPlay();
                } while (!close);
            }
        };
    }

    @Override
    protected void init(RegionDetector.Region region) {
        super.init(region);
        samplesPerFrame8Mono = (int) (region.getFrameIntervalMs() * (SAMPLE_RATE_HZ / 1000d));
        samplesPerFrame16Mono = samplesPerFrame8Mono << 1;
        psgBuffer = new byte[samplesPerFrame8Mono << BUFFER_SIZE_SHIFT];  //8 bit mono as byte
        fmBuffer = new int[samplesPerFrame16Mono << BUFFER_SIZE_SHIFT];  //16 bit stereo as Integer
        mixBuffer = new byte[samplesPerFrame16Mono << BUFFER_SIZE_SHIFT];  //16 bit mono as bytes
    }

    @Override
    public void output(long oneFrame) {
        LogHelper.printLevel(LOG, Level.INFO, "Start Output", verbose);
        int availSamples = fm.update(fmBuffer, start, samplesPerFrame8Mono);
        availSamples = fixBuffer(fmBuffer, availSamples);
        psg.output(psgBuffer, start, start + availSamples);
        SoundUtil.intStereo14ToByteMono16Mix(fmBuffer, mixBuffer, psgBuffer);
        start += availSamples;
        if (start >= psgBuffer.length) {
            start = 0;
            if (useThread) {
                synchronized (blocker) {
                    hasOutput = true;
                    blocker.notifyAll();
                }
            } else {
                mixAndPlay();
            }
        }
        LogHelper.printLevel(LOG, Level.INFO, "Done Output", verbose);
    }

    protected void mixAndPlay() {
        long now = System.nanoTime();
        long beforeWrite = 0;
        LogHelper.printLevel(LOG, Level.INFO, "Start play", verbose);
        int bufStart = 0;
        while (bufStart < mixBuffer.length) {
            int lineAvailable = waitLineAvailable();
            LogHelper.printLevel(LOG, Level.INFO, "Available before {}, bufStart {}, bufLen {}", lineAvailable, bufStart,
                    mixBuffer.length, verbose);
            int len = Math.min(mixBuffer.length - bufStart, lineAvailable);
            LogHelper.printLevel(LOG, Level.INFO, "latestRun: {}", (now - lastWrite) / 1_000_000d, verbose);
            beforeWrite = System.nanoTime();
            int written = SoundUtil.writeBufferInternal(dataLine, mixBuffer, bufStart, len);
            lineAvailable = dataLine.available();
            lineAvailable = dataLine.available();
            LogHelper.printLevel(LOG, Level.INFO, "Available after {}, bufStart {}, bufLen {}", lineAvailable, bufStart,
                    mixBuffer.length, verbose);
            bufStart += written;
        }
        now = System.nanoTime();
        LogHelper.printLevel(LOG, Level.INFO, "writeFrame interval: {}, timeSpentWriting: {}", (now - lastWrite) / 1_000_000d,
                (now - beforeWrite) / 1_000_000d, verbose);
//        soundPersister.startRecording(SoundPersister.SoundType.BOTH);
        if (isRecording()) {
            soundPersister.persistSound(DEFAULT_SOUND_TYPE, mixBuffer);
        }
        IntStream.range(0, oneMsBuffer.length).forEach(i -> {
            oneMsBuffer[i] = mixBuffer[(i % 2 == 0) ? mixBuffer.length - 2 : mixBuffer.length - 1];
        });
        lastWrite = System.nanoTime();
        LogHelper.printLevel(LOG, Level.INFO, "Done play", verbose);
    }

    private int waitLineAvailable() {
        int lineAvailable = dataLine.available();
        lineAvailable = dataLine.available(); //need to do it twice!
        if (lineAvailable <= 0) {
            do {
                Util.sleep(1);
                dataLine.available();
                lineAvailable = dataLine.available();
                LogHelper.printLevel(LOG, Level.INFO, "Sleep", verbose);
            } while (lineAvailable <= 0);
        }
        return lineAvailable;
    }

    private int fixBuffer(int[] fmBuffer, int availSamples) {
        if (availSamples != samplesPerFrame8Mono) {
            if (Math.abs(availSamples - samplesPerFrame8Mono) > 1) {
                LOG.info((availSamples < samplesPerFrame8Mono ? "U" : "O") + ": {}, {}",
                        availSamples, samplesPerFrame8Mono);
            }

            int lastIdx = (availSamples << 1) - 1;
            int lastVal = (fmBuffer[lastIdx] + fmBuffer[lastIdx - 1]) >> 1; //stereo -> take avg
            Arrays.fill(fmBuffer, lastIdx, fmBuffer.length, lastVal);
            availSamples = samplesPerFrame8Mono;
        }
        return availSamples;
    }
}