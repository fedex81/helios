/*
 * JavaSoundManager
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

package omegadrive.sound.javasound;

import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.persist.FileSoundPersister;
import omegadrive.sound.persist.SoundPersister;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.PriorityThreadFactory;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JavaSoundManager implements SoundProvider {
    private static final Logger LOG = LogManager.getLogger(JavaSoundManager.class.getSimpleName());

    private static SoundPersister.SoundType DEFAULT_SOUND_TYPE = SoundPersister.SoundType.BOTH;

    private static ExecutorService executorService =
            Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY, JavaSoundManager.class.getSimpleName()));

    private PsgProvider psg;
    private FmProvider fm;

    private static int OUTPUT_SAMPLE_SIZE = 16;
    private static int OUTPUT_CHANNELS = 1;
    private AudioFormat audioFormat = new AudioFormat(SoundProvider.SAMPLE_RATE_HZ, OUTPUT_SAMPLE_SIZE, OUTPUT_CHANNELS, true, false);
    private SourceDataLine dataLine;
    private SoundPersister soundPersister;
    private boolean mute = false;
    public volatile boolean close;
    private volatile boolean isSoundWorking = false;
    private SoundHandler.AudioRunnable playSoundRunnable;
    int fmSize;
    int psgSize;

    public void init(RegionDetector.Region region) {
        dataLine = SoundUtil.createDataLine(audioFormat);
        soundPersister = new FileSoundPersister();
        fmSize = SoundProvider.getFmBufferIntSize(region.getFps());
        psgSize = SoundProvider.getPsgBufferByteSize(region.getFps());
        this.playSoundRunnable = getRunnable(dataLine, region);
        executorService.submit(playSoundRunnable);
        LOG.info("Output audioFormat: " + audioFormat);
    }

    private SoundHandler.AudioRunnable getRunnable(SourceDataLine dataLine, RegionDetector.Region region) {

        return new SoundHandler.AudioRunnable() {

            int[] fm_buf_ints = new int[fmSize];
            byte[] mix_buf_bytes16 = new byte[fm_buf_ints.length];
            byte[] psg_buf_bytes = new byte[psgSize];
            int fmSizeMono = fmSize / 2;
            boolean hasFm = getFm() != FmProvider.NO_SOUND;

            @Override
            public void run() {
                try {
                    long sleepNs = Util.MILLI_IN_NS >> 1;
                    int count = 0;
                    do {
                        playOnce();
                        count = 0;
                        do {
//                            System.out.println("wait " + count++);
                            Util.parkUntil(System.nanoTime() + sleepNs);
                        } while (dataLine.available() < fmSizeMono); //half buffer
                    } while (!close);
                } catch (Exception e) {
                    LOG.error("Unexpected sound error, stopping", e);
                }
                LOG.info("Stopping sound thread");
                psg.reset();
                fm.reset();
            }

            @Override
            public void playOnce() {
                playOnce(fmSizeMono);
            }

            public void playOnce(int fmBufferLenMono) {
                if(hasFm) {
                    fmBufferLenMono = fm.update(fm_buf_ints, 0, fmBufferLenMono);
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

            }
        };
    }

    protected void updateSoundWorking(byte[] b) {
        if (isSoundWorking) {
            return;
        }
        for (int i = 0; i < b.length; i++) {
            isSoundWorking |= b[i] != 0;
        }
    }

    public void setPsg(PsgProvider psg) {
        this.psg = psg;
    }

    public void setFm(FmProvider fm) {
        this.fm = fm;
    }

    @Override
    public PsgProvider getPsg() {
        return psg;
    }

    @Override
    public FmProvider getFm() {
        return fm;
    }

    @Override
    public void output(long nanos) {
//        LOG.info(micros + " micros");
    }

    @Override
    public void reset() {
        LOG.info("Resetting sound");
        close = true;
        if (dataLine != null) {
            dataLine.drain();
            dataLine.close();
        }
    }

    @Override
    public void close() {
        reset();
        List<Runnable> list = executorService.shutdownNow();
        LOG.info("Closing sound, stopping background tasks: #" + list.size());
    }

    @Override
    public boolean isRecording() {
        return soundPersister.isRecording();
    }

    @Override
    public void setRecording(boolean recording) {
        if (isRecording() && !recording) {
            soundPersister.stopRecording();
        } else if (!isRecording() && recording) {
            soundPersister.startRecording(DEFAULT_SOUND_TYPE);
        }
    }

    @Override
    public boolean isMute() {
        return mute;
    }

    @Override
    public void setMute(boolean mute) {
        this.mute = mute;
        LOG.info("Set mute: " + mute);
    }

    @Override
    public boolean isSoundWorking() {
        return isSoundWorking;
    }
}
