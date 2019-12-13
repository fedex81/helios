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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;

public class JavaSoundManager extends AbstractSoundManager {

    private static final Logger LOG = LogManager.getLogger(JavaSoundManager.class.getSimpleName());

    public static int sleepTotal = 0;
    public volatile static int samplesProducedCount = 0;
    public volatile static int samplesConsumedCount = 0;

    volatile int[] fm_buf_ints = new int[fmSize];
    volatile byte[] mix_buf_bytes16 = new byte[fm_buf_ints.length];
    volatile byte[] psg_buf_bytes = new byte[psgSize];
    volatile int fmSizeMono = fmSize / 2;
    volatile boolean hasFm = getFm() != FmProvider.NO_SOUND;

    private int playOnce(int fmBufferLenMono) {
//        fmBufferLenMono = Math.min(fmBufferLenMono, dataLine.available());
        if (hasFm) {
            fmBufferLenMono = fm.update(fm_buf_ints, 0, fmBufferLenMono);
            if (fmBufferLenMono == 0) {
                return 0;
            }
        }
        psg.output(psg_buf_bytes, 0, fmBufferLenMono);
        int fmBufferLenStereo = fmBufferLenMono << 1;
        samplesProducedCount += fmBufferLenStereo;

        try {
            Arrays.fill(mix_buf_bytes16, SoundUtil.ZERO_BYTE);
            if (hasFm) {
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


    @Override
    protected Runnable getRunnable(SourceDataLine dataLine, RegionDetector.Region region) {
        return new Runnable() {
            @Override
            public void run() {
                fm_buf_ints = new int[fmSize];
                mix_buf_bytes16 = new byte[fm_buf_ints.length];
                psg_buf_bytes = new byte[psgSize];
                fmSizeMono = fmSize / 2;
                hasFm = getFm() != FmProvider.NO_SOUND;
                try {
                    do {
                        samplesConsumedCount += playOnce(fmSizeMono);
                    } while (!close);
                } catch (Exception e) {
                    LOG.error("Unexpected sound error, stopping", e);
                }
                LOG.info("Stopping sound thread");
                psg.reset();
                fm.reset();
            }
        };
    }

    @Override
    public void output(long nanos) {
        //do nothing
    }
}

