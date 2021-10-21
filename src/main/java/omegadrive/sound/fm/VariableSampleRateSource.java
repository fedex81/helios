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
    private final double outputSampleRate;
    private final Queue<Integer> sampleQueue =
            new SpscAtomicArrayQueue<>(SoundProvider.SAMPLE_RATE_HZ);
    protected AtomicInteger queueLen = new AtomicInteger();
    private final AudioRateControl audioRateControl;
    private int sampleRatePerFrame = 0;
    private final int audioScaleBits;
    private final Integer[] stereoSamples = new Integer[2]; //[0] left, [1] right

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

    public void setMicrosPerInputSample(double microsPerInputSample) {
        this.microsPerInputSample = microsPerInputSample;
    }

    protected void addStereoSamples(int sampleL, int sampleR) {
        sampleQueue.offer(Util.getFromIntegerCache(sampleL | 1)); //sampleL is always odd
        sampleQueue.offer(Util.getFromIntegerCache(sampleR & ~1)); //sampleR is always even
        queueLen.addAndGet(2);
        sampleRatePerFrame += 2;
    }

    protected void addMonoSample(int sample) {
        addStereoSamples(sample, sample);
    }

    @Override
    public int update(int[] buf_lr, int offset, int count) {
        offset <<= 1;
        int end = (count << 1) + offset;
        final int initialQueueSize = queueLen.get();
        int queueIndicativeLen = initialQueueSize;
        int i = offset;
        for (; i < end && queueIndicativeLen > 0; i += 2) {
            //when using mono we process two samples
            if (!fillStereoSamples(stereoSamples)) {
                LOG.warn("Null left sample QL{} P{}", queueIndicativeLen, i);
                break;
            }
            queueIndicativeLen = queueLen.addAndGet(-2);
            //Integer -> short -> int
            buf_lr[i] = ((short) (stereoSamples[0] & 0xFFFF)) << audioScaleBits;
            buf_lr[i + 1] = ((short) (stereoSamples[1] & 0xFFFF)) << audioScaleBits;
        }
        return i >> 1;
    }

    private boolean fillStereoSamples(Integer[] stereoSamples) {
        stereoSamples[0] = sampleQueue.peek();
        if (stereoSamples[0] == null) {
            return false;
        }
        sampleQueue.poll();
        stereoSamples[1] = sampleQueue.poll();
        if (stereoSamples[1] == null) {
            stereoSamples[1] = stereoSamples[0];
            LOG.warn("Null right sample, left: {}", stereoSamples[0]);
        }
        //sanity check
//        if((stereoSamples[0] & 1) == 0 || (stereoSamples[1] & 1) == 1){
//            LOG.warn("Unexpected sample ordering");
//        }
        return true;
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
