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
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface SoundProvider extends Device {
    Logger LOG = LogManager.getLogger(SoundProvider.class.getSimpleName());

    long PAL_PSG_CLOCK = Util.GEN_PAL_MCLOCK_MHZ / 15; // 3546893
    long NTSC_PSG_CLOCK = Util.GEN_NTSC_MCLOCK_MHZ / 15; //3579545;
    long NTSC_FM_CLOCK = Util.GEN_NTSC_MCLOCK_MHZ / 7; //7670442;
    long PAL_FM_CLOCK = Util.GEN_PAL_MCLOCK_MHZ / 7; //7600485;

    int SAMPLE_RATE_HZ = Integer.valueOf(System.getProperty("audio.sample.rate.hz", "44100"));

    int OVERRIDE_AUDIO_BUFFER_LEN_MS = Integer.valueOf(System.getProperty("audio.buffer.length.ms", "0"));

    boolean ENABLE_SOUND = Boolean.valueOf(System.getProperty("helios.enable.sound", "true"));

    boolean MD_NUKE_AUDIO = Boolean.valueOf(System.getProperty("md.nuke.audio", "false"));

    int OVERRIDE_AUDIO_BUFFER_SIZE = OVERRIDE_AUDIO_BUFFER_LEN_MS > 0 ?
            (int) (SAMPLE_RATE_HZ / 1000d * OVERRIDE_AUDIO_BUFFER_LEN_MS)
            : 0;

    PsgProvider getPsg();

    FmProvider getFm();

    static int getPsgBufferByteSize(int fps) {
        return getFmBufferIntSize(fps) / 2;
    }

    static int getFmBufferIntSize(int fps) {
        if (OVERRIDE_AUDIO_BUFFER_SIZE > 0) {
            int size = OVERRIDE_AUDIO_BUFFER_SIZE;
            size += size % 2 == 1 ? 1 : 0;
            return size;
        }
        int res = 2 * SAMPLE_RATE_HZ / fps;
        return res % 2 == 0 ? res : res + 1;
    }

    static double getPsgSoundClockScaled(RegionDetector.Region r) {
        return (RegionDetector.Region.EUROPE != r ? NTSC_PSG_CLOCK : PAL_PSG_CLOCK) / 32d;
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
        public void output(long fps) {

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

    void output(long nanos);

    default boolean isSoundWorking() {
        return false;
    }
}
