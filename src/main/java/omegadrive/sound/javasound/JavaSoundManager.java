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
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

public class JavaSoundManager extends AbstractSoundManager {

    private static final Logger LOG = LogManager.getLogger(JavaSoundManager.class.getSimpleName());

    public static int sleepTotal = 0;
    public static final long EMPTY_QUEUE_SLEEP_NS = 500_000;
    public volatile static int samplesProducedCount = 0;
    public volatile static int samplesConsumedCount = 0;

    volatile int[] fm_buf_ints;
    volatile byte[] mix_buf_bytes16Stereo;
    volatile byte[] psg_buf_bytes;
    volatile int fmSizeMono;

    @Override
    public void init() {
        fm_buf_ints = new int[fmSize];
        mix_buf_bytes16Stereo = new byte[fm_buf_ints.length << 1];
        psg_buf_bytes = new byte[psgSize];
        fmSizeMono = (int) Math.round(fmSize / 2d);
        hasFm = getFm() != FmProvider.NO_SOUND;
        hasPsg = getPsg() != PsgProvider.NO_SOUND;
        fm_buf_ints = hasFm ? fm_buf_ints : EMPTY_FM;
        psg_buf_bytes = hasPsg ? psg_buf_bytes : EMPTY_PSG;
    }

    private int playOnceStereo(int fmBufferLenMono) {
        int fmMonoActual = fm.update(fm_buf_ints, 0, fmBufferLenMono);
        //if FM is present load a matching number of psg samples
        fmBufferLenMono = hasFm ? fmMonoActual : fmBufferLenMono;
        psg.output(psg_buf_bytes, 0, fmBufferLenMono);
        int fmBufferLenStereo = fmBufferLenMono << 1;
        int bufferBytesMono = fmBufferLenMono << 1;
        int bufferBytesStereo = bufferBytesMono << 1;
        samplesProducedCount += fmBufferLenStereo;

        try {
            Arrays.fill(mix_buf_bytes16Stereo, SoundUtil.ZERO_BYTE);
            //FM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
            SoundUtil.mixFmPsgStereo(fm_buf_ints, mix_buf_bytes16Stereo, psg_buf_bytes, fmBufferLenStereo);
            updateSoundWorking(mix_buf_bytes16Stereo);
            if (!isMute()) {
                SoundUtil.writeBufferInternal(dataLine, mix_buf_bytes16Stereo, bufferBytesStereo);
            }
            if (isRecording()) {
                soundPersister.persistSound(DEFAULT_SOUND_TYPE, mix_buf_bytes16Stereo);
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
                init();

                try {
                    do {
                        int res = playOnceStereo(fmSizeMono);
                        samplesConsumedCount += res;
                        if (res <= 10) {
                            LockSupport.parkNanos(EMPTY_QUEUE_SLEEP_NS);
                        }
                    } while (!close);
                } catch (Exception | Error e) {
                    LOG.error("Unexpected sound error, stopping", e);
                }
                LOG.info("Stopping sound thread");
                psg.reset();
                fm.reset();
            }
        };
    }

    @Override
    public void onNewFrame() {
        fm.onNewFrame();
    }
}

