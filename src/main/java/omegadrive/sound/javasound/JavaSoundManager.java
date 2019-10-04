/*
 * JavaSoundManager
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 04/10/19 11:08
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

import omegadrive.SystemLoader;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.Ym2413Provider;
import omegadrive.sound.persist.FileSoundPersister;
import omegadrive.sound.persist.SoundPersister;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.Sms;
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
    private static Logger LOG = LogManager.getLogger(JavaSoundManager.class.getSimpleName());

    private static SoundPersister.SoundType DEFAULT_SOUND_TYPE = SoundPersister.SoundType.BOTH;

    private static ExecutorService executorService =
            Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY, JavaSoundManager.class.getSimpleName()));

    private static long nsToMillis = 1_000_000;

    private PsgProvider psg;
    private FmProvider fm;

    private static int OUTPUT_SAMPLE_SIZE = 16;
    private static int OUTPUT_CHANNELS = 1;
    private AudioFormat audioFormat = new AudioFormat(SoundProvider.SAMPLE_RATE_HZ, OUTPUT_SAMPLE_SIZE, OUTPUT_CHANNELS, true, false);
    private SourceDataLine dataLine;
    private SoundPersister soundPersister;
    private boolean mute = false;
    public volatile boolean close;
    public volatile boolean hasOutput = false;
    private volatile boolean isSoundWorking = false;
    private SoundHandler.AudioRunnable playSoundRunnable;
    int fmSize;
    int psgSize;

    private final static boolean playOncePerFrame = false;

    public static JavaSoundManager createSoundProvider(SystemLoader.SystemType systemType, RegionDetector.Region region) {
        PsgProvider psgProvider;
        FmProvider fmProvider = FmProvider.NO_SOUND;
        switch (systemType){
            case MSX:
                psgProvider = PsgProvider.createAyInstance(region, SAMPLE_RATE_HZ);
                break;
            case GENESIS:
                psgProvider = PsgProvider.createSnInstance(region, SAMPLE_RATE_HZ);
                fmProvider = MdFmProvider.createInstance(region, SAMPLE_RATE_HZ);
                break;
            case SMS:
                if (Sms.ENABLE_FM) {
                    //TODO
                    fmProvider = new Ym2413Provider();
                    psgProvider = PsgProvider.NO_SOUND;
                } else {
                    psgProvider = PsgProvider.createSnInstance(region, SAMPLE_RATE_HZ);
                }
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

    private void init(RegionDetector.Region region) {
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
                    do {
                        if (playOncePerFrame) {
                            playOncePerFrame();
                        } else {
                            playOnce();
                        }
                        Util.parkUntil(System.nanoTime() + nsToMillis / 2);
                    } while (!close);
                } catch (Exception e) {
                    LOG.error("Unexpected sound error, stopping", e);
                }
                LOG.info("Stopping sound thread");
                psg.reset();
                fm.reset();
            }

            private void playOncePerFrame() {
                if (hasOutput) {
                    playOnce(fmBufferLen);
                    hasOutput = false;
//                    Util.sleep((long) (region.getFrameIntervalMs() - 2));
                }
            }

            @Override
            public void playOnce() {
                playOnce(fmSizeMono);
            }

            public void playOnce(int fmBufferLenMono) {
                psg.output(psg_buf_bytes, 0, fmBufferLenMono);
                if(hasFm) {
                    fm.update(fm_buf_ints, 0, fmBufferLenMono);
                }

                try {
                    Arrays.fill(mix_buf_bytes16, SoundUtil.ZERO_BYTE);
                    if(hasFm) {
                        //FM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
                        SoundUtil.intStereo14ToByteMono16Mix(fm_buf_ints, mix_buf_bytes16, psg_buf_bytes);
                    } else {
                        SoundUtil.byteMono8ToByteMono16Mix(psg_buf_bytes, mix_buf_bytes16);
                    }

                    updateSoundWorking(mix_buf_bytes16);
                    if (!isMute()) {
                        SoundUtil.writeBufferInternal(dataLine, mix_buf_bytes16, fmBufferLenMono * 2);
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

    double d = 1d / 1_000_000_000;
    int fmBufferLen = 0;

    @Override
    public void output(int nanos) {
//        LOG.info(micros + " micros");
        if (playOncePerFrame) {
            double sec = d * nanos;
            fmBufferLen = (int) (sec * SAMPLE_RATE_HZ);
            if (fmBufferLen > fmSize / 2) {
                LOG.info("{} secs, bufLen: {}, maxLen: {}", sec, fmBufferLen, fmSize / 2);
            }
            fmBufferLen = Math.min(fmBufferLen, fmSize / 2);
            hasOutput = true;
        }
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
