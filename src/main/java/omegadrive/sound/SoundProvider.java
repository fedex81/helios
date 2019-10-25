/*
 * SoundProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 25/10/19 14:47
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
import omegadrive.SystemLoader;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.ym2413.Ym2413Provider;
import omegadrive.sound.javasound.JavaSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.Sms;
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

    int OVERRIDE_AUDIO_BUFFER_SIZE = Integer.valueOf(System.getProperty("audio.buffer.size", "0"));


    PsgProvider getPsg();

    FmProvider getFm();

    static SoundProvider createSoundProvider(SystemLoader.SystemType systemType, RegionDetector.Region region) {
        PsgProvider psgProvider;
        FmProvider fmProvider = FmProvider.NO_SOUND;
        switch (systemType) {
            case MSX:
                psgProvider = PsgProvider.createAyInstance(region, SAMPLE_RATE_HZ);
                break;
            case GENESIS:
                psgProvider = PsgProvider.createSnInstance(region, SAMPLE_RATE_HZ);
                fmProvider = MdFmProvider.createInstance(region, SAMPLE_RATE_HZ);
                break;
            case SMS:
                if (Sms.ENABLE_FM) {
                    fmProvider = Ym2413Provider.createInstance(region, SAMPLE_RATE_HZ);
                }
                psgProvider = PsgProvider.createSnInstance(region, SAMPLE_RATE_HZ);

                break;
            default:
                psgProvider = PsgProvider.createSnInstance(region, SAMPLE_RATE_HZ);
                break;

        }
        JavaSoundManager jsm = new JavaSoundManager();
        jsm.setFm(fmProvider);
        jsm.setPsg(psgProvider);
        jsm.init(region);
        return jsm;
    }

    static int getPsgBufferByteSize(int fps) {
        return getFmBufferIntSize(fps) / 2;
    }

    static int getFmBufferIntSize(int fps) {
        if (OVERRIDE_AUDIO_BUFFER_SIZE > 0) {
            return OVERRIDE_AUDIO_BUFFER_SIZE;
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
        public void setMute(boolean mute) {
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

    void setMute(boolean mute);

    void output(long nanos);

    default boolean isSoundWorking() {
        return false;
    }
}
