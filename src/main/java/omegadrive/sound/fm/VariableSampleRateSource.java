/*
 * Ym2612Nuke
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 25/10/19 16:39
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

package omegadrive.sound.fm;

import omegadrive.sound.SoundProvider;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.atomic.SpscAtomicArrayQueue;

import javax.sound.sampled.AudioFormat;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class VariableSampleRateSource implements FmProvider {

    private static final Logger LOG = LogManager.getLogger(VariableSampleRateSource.class.getSimpleName());
    final static int DEFAULT_AUDIO_SCALE_BITS = 5;
    protected double microsPerOutputSample;
    protected double microsPerInputSample;
    protected double sourceSampleRate;
    protected volatile double fmCalcsPerMicros;
    private double outputSampleRate;
    private Queue<Integer> sampleQueue =
            new SpscAtomicArrayQueue<>(SoundProvider.SAMPLE_RATE_HZ);
    private AtomicInteger queueLen = new AtomicInteger();
    private AudioRateControl audioRateControl;
    private int sampleRatePerFrame = 0;
    private final int audioScaleBits;

    protected VariableSampleRateSource(double sourceSampleRate, AudioFormat audioFormat, String sourceName) {
        this(sourceSampleRate, audioFormat, sourceName, DEFAULT_AUDIO_SCALE_BITS);
    }

    protected VariableSampleRateSource(double sourceSampleRate, AudioFormat audioFormat,
                                       String sourceName, int audioScaleBits) {
        this.outputSampleRate = audioFormat.getSampleRate();
        this.sourceSampleRate = sourceSampleRate;
        this.microsPerOutputSample = (1_000_000.0 / outputSampleRate);
        this.microsPerInputSample = (1_000_000.0 / sourceSampleRate);
        this.fmCalcsPerMicros = microsPerOutputSample;
        this.audioRateControl = new AudioRateControl(sourceName, SoundUtil.getMonoSamplesBufferSize(audioFormat));
        this.audioScaleBits = audioScaleBits;
    }

    protected abstract void spinOnce();

    protected void addSample(int sample) {
        sampleQueue.offer(Util.getFromIntegerCache(sample));
        queueLen.addAndGet(1);
        sampleRatePerFrame++;
    }

    @Override
    public int update(int[] buf_lr, int offset, int count) {
        offset <<= 1;
        int end = (count << 1) + offset;
        int rsample, lsample;
        int sampleNum;
        Integer isample;
        final int initialQueueSize = queueLen.get();
        int queueIndicativeLen = initialQueueSize;
        for (int i = offset; i < end && queueIndicativeLen > 0; i += 2) {
            isample = sampleQueue.peek();
            if (isample == null) {
                LOG.debug("Null sample QL{} P{}", queueIndicativeLen, i);
                break;
            }
            sampleQueue.remove();
            lsample = (short) (isample & 0xFFFF);
            rsample = (short) (lsample - (short) ((isample >> 16) & 0xFFFF)); // diff = l - r, r = l - diff
            queueIndicativeLen = queueLen.decrementAndGet();
            buf_lr[i] = lsample << audioScaleBits;
            buf_lr[i + 1] = rsample << audioScaleBits;
        }
        sampleNum = initialQueueSize - queueIndicativeLen;
        return sampleNum;
    }

    @Override
    public void reset() {
        sampleQueue.clear();
        queueLen.set(0);
        sampleRatePerFrame = 0;
    }

    @Override
    public void onNewFrame() {
        fmCalcsPerMicros = audioRateControl.adaptiveRateControl(queueLen.get(), fmCalcsPerMicros, sampleRatePerFrame);
        sampleRatePerFrame = 0;
    }
}
