/*
 * JavaSoundManager2
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 15:44
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
import java.util.Arrays;

public class JavaSoundManager2 extends AbstractSoundManager {
    private static Logger LOG = LogManager.getLogger(JavaSoundManager2.class.getSimpleName());

    private final Object blocker = new Object();
    private volatile boolean hasOutput;

    int samplesPerFrame = 0;
    private byte[] psgBuffer = new byte[0];
    private int[] fmBuffer = new int[0];
    private byte[] mixBuffer = new byte[0];

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
                    //FM: stereo 16 bit, PSG: mono 8 bit, OUT: mono 16 bit
                    SoundUtil.intStereo14ToByteMono16Mix(fmBuffer, mixBuffer, psgBuffer);
                    Arrays.fill(fmBuffer, 0);
                    SoundUtil.writeBufferInternal(dataLine, mixBuffer, mixBuffer.length);
                } while (!close);
            }
        };
    }

    @Override
    public void output(long oneFrame) {
        int availSamples = fm.update(fmBuffer, 0, samplesPerFrame);
        psg.output(psgBuffer, 0, samplesPerFrame);
        if (availSamples != samplesPerFrame) {
            LOG.info((availSamples < samplesPerFrame ? "U" : "O") + ": {}, {}", availSamples, samplesPerFrame);
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
