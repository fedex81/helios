/*
 * SoundProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 15:49
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

package omegadrive.sound;

import omegadrive.Device;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;

public interface SoundProvider extends Device, BaseVdpProvider.VdpEventListener {
    Logger LOG = LogManager.getLogger(SoundProvider.class.getSimpleName());

    long PAL_PSG_CLOCK = Util.GEN_PAL_MCLOCK_MHZ / 15; // 3546893
    long NTSC_PSG_CLOCK = Util.GEN_NTSC_MCLOCK_MHZ / 15; //3579545;
    long NTSC_FM_CLOCK = Util.GEN_NTSC_MCLOCK_MHZ / 7; //7670442;
    long PAL_FM_CLOCK = Util.GEN_PAL_MCLOCK_MHZ / 7; //7600485;

    int SAMPLE_RATE_HZ = Integer.parseInt(System.getProperty("audio.sample.rate.hz", "44100"));

    int DEFAULT_BUFFER_SIZE_MS = 50;
    int AUDIO_BUFFER_LEN_MS = Integer.parseInt(System.getProperty("audio.buffer.length.ms",
            String.valueOf(DEFAULT_BUFFER_SIZE_MS)));

    boolean ENABLE_SOUND = Boolean.parseBoolean(System.getProperty("helios.enable.sound", "true"));

    boolean JAL_SOUND_MGR = Boolean.parseBoolean(System.getProperty("helios.jal.sound.mgr", "false"));

    int[] EMPTY_FM = new int[0];
    byte[] EMPTY_PSG = new byte[0];

    PsgProvider getPsg();

    FmProvider getFm();

    static int getPsgBufferByteSize(AudioFormat audioFormat) {
        return getFmBufferIntSize(audioFormat) >> 1;
    }

    static int getFmBufferIntSize(AudioFormat audioFormat) {
        return SoundUtil.getStereoSamplesBufferSize(audioFormat);
    }

    static double getPsgSoundClock(RegionDetector.Region r) {
        return (RegionDetector.Region.EUROPE != r ? NTSC_PSG_CLOCK : PAL_PSG_CLOCK);
    }

    static double getFmSoundClock(RegionDetector.Region r) {
        return (RegionDetector.Region.EUROPE != r ? NTSC_FM_CLOCK : PAL_FM_CLOCK);
    }

    SoundProvider NO_SOUND = new SoundProvider() {
        @Override
        public PsgProvider getPsg() {
            return PsgProvider.NO_SOUND;
        }

        @Override
        public FmProvider getFm() {
            return FmProvider.NO_SOUND;
        }

        @Override
        public void onNewFrame() {
        }

        @Override
        public void reset() {

        }

        @Override
        public void close() {

        }

        @Override
        public boolean isMute() {
            return false;
        }

        @Override
        public void setEnabled(boolean mute) {
        }

        @Override
        public void setEnabled(Device device, boolean mute) {

        }
    };

    void close();

    default boolean isRecording() {
        return false;
    }

    default void setRecording(boolean recording) {
        //NO OP
    }

    boolean isMute();

    void setEnabled(boolean mute);

    void setEnabled(Device device, boolean enabled);
}
