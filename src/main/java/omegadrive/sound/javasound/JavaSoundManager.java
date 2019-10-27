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

import omegadrive.sound.fm.FmProvider;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;

public class JavaSoundManager extends AbstractSoundManager {

    private static final Logger LOG = LogManager.getLogger(JavaSoundManager.class.getSimpleName());

    @Override
    protected Runnable getRunnable(SourceDataLine dataLine, RegionDetector.Region region) {

        return new AudioRunnable() {

            int[] fm_buf_ints = new int[fmSize];
            byte[] mix_buf_bytes16 = new byte[fm_buf_ints.length];
            byte[] psg_buf_bytes = new byte[psgSize];
            int fmSizeMono = fmSize / 2;
            boolean hasFm = getFm() != FmProvider.NO_SOUND;

            @Override
            public void run() {
                try {
                    //sleeps for 1/10 of the buffer length
                    long sleepNs = Math.max(OVERRIDE_AUDIO_BUFFER_LEN_MS * Util.MILLI_IN_NS / 10, Util.MILLI_IN_NS);
                    int count = 0;
                    long until = 0;
                    do {
                        count = playOnce();
                        until = count > 0 ? sleepNs : Util.MILLI_IN_NS;
                        do {
                            Util.parkUntil(System.nanoTime() + until);
                        } while (dataLine.available() == 0); //half buffer
                    } while (!close);
                } catch (Exception e) {
                    LOG.error("Unexpected sound error, stopping", e);
                }
                LOG.info("Stopping sound thread");
                psg.reset();
                fm.reset();
            }

            @Override
            public int playOnce() {
                return playOnce(fmSizeMono);
            }

            public int playOnce(int fmBufferLenMono) {
                if(hasFm) {
                    fmBufferLenMono = fm.update(fm_buf_ints, 0, fmBufferLenMono);
                    if (fmBufferLenMono == 0) {
                        return 0;
                    }
                }
                psg.output(psg_buf_bytes, 0, fmBufferLenMono);
                int fmBufferLenStereo = fmBufferLenMono << 1;

                try {
                    Arrays.fill(mix_buf_bytes16, SoundUtil.ZERO_BYTE);
                    if(hasFm) {
                        //FM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
                        SoundUtil.intStereo14ToByteMono16Mix(fm_buf_ints, mix_buf_bytes16, psg_buf_bytes, fmBufferLenStereo);
                    } else {
                        SoundUtil.byteMono8ToByteMono16Mix(psg_buf_bytes, mix_buf_bytes16);
                    }

                    updateSoundWorking(mix_buf_bytes16);
                    if (!isMute()) {
                        SoundUtil.writeBufferInternal(dataLine, mix_buf_bytes16, fmBufferLenStereo);
                    }
                    if (isRecording()) {
                        soundPersister.persistSound(DEFAULT_SOUND_TYPE, mix_buf_bytes16);
                    }

                } catch (Exception e) {
                    LOG.error("Unexpected sound error", e);
                }
                Arrays.fill(fm_buf_ints, 0);
                Arrays.fill(psg_buf_bytes, SoundUtil.ZERO_BYTE);
                return fmBufferLenStereo;
            }
        };
    }

    interface AudioRunnable extends Runnable {

        int playOnce();
    }

}
