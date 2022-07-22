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

import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

public abstract class VariableSampleRateSource extends GenericAudioProvider {

    private static final Logger LOG = LogHelper.getLogger(VariableSampleRateSource.class.getSimpleName());
    final static int DEFAULT_AUDIO_SCALE_BITS = 5;
    protected double microsPerOutputSample;
    protected double microsPerInputSample;
    protected double sourceSampleRate;
    protected volatile double fmCalcsPerMicros;
    private final double outputSampleRate;
    private final AudioRateControl audioRateControl;
    private int sampleRatePerFrame = 0;

    protected VariableSampleRateSource(double sourceSampleRate, AudioFormat audioFormat, String sourceName) {
        this(sourceSampleRate, audioFormat, sourceName, DEFAULT_AUDIO_SCALE_BITS);
    }

    protected VariableSampleRateSource(double sourceSampleRate, AudioFormat audioFormat,
                                       String sourceName, int audioScaleBits) {
        super(audioFormat, audioScaleBits, (int) audioFormat.getSampleRate()); //500ms maxQueueLen
        assert audioFormat.getChannels() == 2;
        this.outputSampleRate = audioFormat.getSampleRate();
        this.sourceSampleRate = sourceSampleRate;
        this.microsPerOutputSample = (1_000_000.0 / outputSampleRate);
        this.microsPerInputSample = (1_000_000.0 / sourceSampleRate);
        this.fmCalcsPerMicros = microsPerOutputSample;
        this.audioRateControl = new AudioRateControl(sourceName, audioFormat);
        start();
    }

    protected abstract void spinOnce();

    public void setMicrosPerInputSample(double microsPerInputSample) {
        this.microsPerInputSample = microsPerInputSample;
    }

    @Override
    protected void addStereoSample(int left, int right) {
        super.addStereoSample(left, right);
        sampleRatePerFrame += 2;
    }

    @Override
    public void reset() {
        super.reset();
        sampleRatePerFrame = 0;
    }

    @Override
    public void onNewFrame() {
        fmCalcsPerMicros = audioRateControl.adaptiveRateControl(stereoQueueLen.get(), fmCalcsPerMicros, sampleRatePerFrame);
        sampleRatePerFrame = 0;
    }
}
