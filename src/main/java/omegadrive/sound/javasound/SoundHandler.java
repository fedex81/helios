package omegadrive.sound.javasound;

import omegadrive.sound.SoundProvider;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class SoundHandler {

    private static Logger LOG = LogManager.getLogger(SoundHandler.class.getSimpleName());

    static long avgSteps = 0;

    public static Runnable getSoundRunnable(JavaSoundManager jsm, SourceDataLine dataLine, RegionDetector.Region region) {
        int fmSize = SoundProvider.getFmBufferIntSize(region.getFps());
        int psgSize = SoundProvider.getPsgBufferByteSize(region.getFps());
        return () -> {
            int[] fm_buf_ints = new int[fmSize];
            byte[] mix_buf_bytes16 = new byte[fm_buf_ints.length];
            byte[] psg_buf_bytes = new byte[psgSize];
            long lastSync = System.nanoTime();
            long now = lastSync;
            long nanoSec = 490000;
            long totSteps = 0;
            long intvSyncNs = 0;
            long cycles = 0;

            byte[] mix_buf_bytes16_2 = new byte[4];
            int[] fm_buf_ints2 = new int[4];
            byte[] psg_buf_bytes2 = {0, 0};

            do {
                now = System.nanoTime();
                intvSyncNs = now - lastSync;
                if (intvSyncNs > nanoSec) {
                    cycles++;
                    int steps = (int) ((intvSyncNs - nanoSec) / 1000d) / 18;
                    totSteps += steps;
                    avgSteps = totSteps / cycles;
                    jsm.getFm().synchronizeTimers(steps);
                    lastSync = now;
                }
                if (!jsm.hasOutput) {
                    Arrays.fill(fm_buf_ints2, 0);
                    jsm.getFm().output(fm_buf_ints2);
                    Arrays.fill(mix_buf_bytes16_2, SoundUtil.ZERO_BYTE);
                    SoundUtil.intStereo14ToByteMono16Mix(fm_buf_ints2, mix_buf_bytes16_2, psg_buf_bytes2);
                    SoundUtil.writeBufferInternal(dataLine, mix_buf_bytes16_2, mix_buf_bytes16_2.length);
                    continue;
                }
                jsm.hasOutput = false;
                jsm.getPsg().output(psg_buf_bytes);
                jsm.getFm().output(fm_buf_ints);

                try {
                    Arrays.fill(mix_buf_bytes16, SoundUtil.ZERO_BYTE);
                    SoundUtil.intStereo14ToByteMono16Mix(fm_buf_ints, mix_buf_bytes16, psg_buf_bytes);
                    jsm.updateSoundWorking(mix_buf_bytes16);
                    if (!jsm.isMute()) {
                        SoundUtil.writeBufferInternal(dataLine, mix_buf_bytes16, mix_buf_bytes16.length);
                    }
//                    if (jsm.isRecording()) {
//                        soundPersister.persistSound(DEFAULT_SOUND_TYPE, mix_buf_bytes16);
//                    }
                } catch (Exception e) {
                    LOG.error("Unexpected sound error", e);
                }
                Arrays.fill(fm_buf_ints, 0);
                Arrays.fill(psg_buf_bytes, SoundUtil.ZERO_BYTE);
            } while (!jsm.close);
            LOG.info("Stopping sound thread");
            jsm.getPsg().reset();
            jsm.getFm().reset();
        };
    }
}
