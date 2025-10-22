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

import omegadrive.SystemLoader;
import omegadrive.sound.SoundDevice;
import omegadrive.sound.SoundDevice.SampleBufferContext;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke;
import omegadrive.system.perf.Telemetry;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static omegadrive.sound.SoundDevice.SoundDeviceType.*;
import static omegadrive.util.SoundUtil.mixTwoSources;

public class JavaSoundManagerBlip extends AbstractSoundManager {

    private static final Logger LOG = LogHelper.getLogger(JavaSoundManagerBlip.class.getSimpleName());

    static class AudioMixContext {
        public volatile int soundDeviceSetup;
        public final Map<SoundDevice.SoundDeviceType, SoundDevice> map;
        public final byte[] mix_buf_bytes16Stereo;

        public AudioMixContext(Map<SoundDevice.SoundDeviceType, SoundDevice> m, byte[] b) {
            map = m;
            mix_buf_bytes16Stereo = b;
        }
    }
    private final AtomicInteger sync = new AtomicInteger();
    private volatile AudioMixContext audioMixContext;

    private JavaSoundManager javaSoundManager;

    private boolean handleNuke = true;

    //stats
    private Telemetry telemetry;
    private volatile int samplesProducedCount, samplesConsumedCount;

    public JavaSoundManagerBlip(SystemLoader.SystemType systemType) {
        this.type = systemType;
    }

    @Override
    public void init(RegionDetector.Region region) {
        super.init(region);
        audioMixContext = new AudioMixContext(activeSoundDeviceMap, new byte[4000]);
        telemetry = Telemetry.getInstance();
        handleMigration();
    }

    /**
     * PSG migrated
     * FM migrated
     * - except FM nuke
     * PWM not migrated
     * PCM not migrated
     * CDDA not migrated
     */
    private void handleMigration() {
        javaSoundManager = new JavaSoundManager(type);
        javaSoundManager.init(region);

        var map = new HashMap<>(activeSoundDeviceMap);
        map.put(PSG, noSoundMap.get(PSG));

        //nuke still using old JSM
        SoundDevice fmp = super.getFm();
        if (!handleNuke && fmp instanceof Ym2612Nuke) {
            setEnabled(fmp, false);
        } else {
            map.put(FM, noSoundMap.get(FM));
        }
        setDisablePermanent(PWM);
        setDisablePermanent(PCM);

        for (var e : map.entrySet()) {
            boolean isEnabled = e.getValue() != noSoundMap.get(e.getKey());
            if (!isEnabled) {
                javaSoundManager.setDisablePermanent(e.getKey());
            }
            javaSoundManager.setEnabled(e.getValue(), isEnabled);
        }
        javaSoundManager.updateSoundDeviceSetup();
    }

    @Override
    public FmProvider getFm() {
        FmProvider fmp = super.getFm();
        if (!handleNuke && fmp == FmProvider.NO_SOUND) {
            fmp = javaSoundManager.getFm();
        }
        return fmp;
    }

    @Override
    public void onNewFrame() {
        doStats();
        activeSoundDeviceMap.values().forEach(SoundDevice::onNewFrame);
        playSound();
    }

    //FM,PWM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
    protected static int mixAudioProviders(AudioMixContext amc) {
        int len = 0;
        SoundDevice device = SoundDevice.NO_SOUND;
        SoundDevice fm = amc.map.get(FM);
        SoundDevice psg = amc.map.get(PSG);
        switch (amc.soundDeviceSetup) {
            case 1: //fm only
                device = fm;
            case 2: //psg only
                device = device == SoundDevice.NO_SOUND ? psg : device;
                SampleBufferContext sbc = device.getBufferContext();
                System.arraycopy(sbc.lineBuffer, 0, amc.mix_buf_bytes16Stereo, 0, sbc.stereoBytesLen);
                len = sbc.stereoBytesLen;
                break;
            case 3: //fm + psg
                SampleBufferContext fms = fm.getBufferContext();
                SampleBufferContext psgs = psg.getBufferContext();
                assert psgs.lineBuffer.length == fms.lineBuffer.length;
                len = mixTwoSources(psgs.lineBuffer, fms.lineBuffer, amc.mix_buf_bytes16Stereo,
                        psgs.stereoBytesLen, fms.stereoBytesLen);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + amc.soundDeviceSetup);
        }
        return len;
    }

    protected static final int MAX_SAMPLE_DIFF_PER_FRAME = 4;
    private AtomicInteger bufSel = new AtomicInteger(0);

    private static byte[][] output = new byte[2][0];

    private int getFrontAndFlip() {
        int bufNum = bufSel.incrementAndGet() & 1;
        bufSel.set(bufNum);
        return bufNum;
    }

    private void playSound() {
        if (!soundEnabled || soundDeviceSetup == 0) {
            return;
        }
        final long current = sync.incrementAndGet();
        audioMixContext.soundDeviceSetup = soundDeviceSetup;
        final AudioMixContext amc = audioMixContext;
        executorService.submit(Util.wrapRunnableEx(() -> {
            int syn = sync.get();
            int inputLen = mixAudioProviders(amc);
            int front = getFrontAndFlip();
            int sampleDelta = getRateControl((int) current, syn);
            adjustBlipBufferClockRate(sampleDelta);
            sampleDelta = 0; //avoid resampling
            int len = resampleHelper(amc.mix_buf_bytes16Stereo, output, front, inputLen, inputLen + sampleDelta);
            SoundUtil.writeBufferInternal(dataLine, output[front], 0, len);
            doStats(len, sampleDelta);
        }));
    }

    int baseInputClocksForInterval = 0;
    SoundDevice[] sd = {null, null};

    /**
     * TODO should this modify the microsPerTick in SoundDevice too?
     *
     * @param sampleDelta
     */
    private void adjustBlipBufferClockRate(int sampleDelta) {
        if (sd[0] == null) {
            sd[0] = getFm();
            sd[1] = getPsg();
        }
        if (baseInputClocksForInterval == 0) {
            baseInputClocksForInterval = sd[0].getBufferContext().inputClocksForInterval.get();
            assert baseInputClocksForInterval == sd[1].getBufferContext().inputClocksForInterval.get();
        }
        for (SoundDevice s : sd) {
            var inClocksRef = s.getBufferContext().inputClocksForInterval;
            int val = sampleDelta < 0 ? baseInputClocksForInterval - MAX_SAMPLE_DIFF_PER_FRAME :
                    baseInputClocksForInterval + MAX_SAMPLE_DIFF_PER_FRAME;
            val = sampleDelta == 0 ? baseInputClocksForInterval : val;
            int prev = inClocksRef.getAndSet(val);
            if (val != prev) {
                System.out.println(s.getType() + "," + prev + "->" + inClocksRef.get());
            }
        }
    }

    int frameCnt = 0, sampleRateFrameAcc = 0;
    int secCnt = 0;

    StringBuilder sb = new StringBuilder();
    private final NumberFormat rateFormatter = new DecimalFormat("#0.00");

    private void doStats(int sampleRateFrame, int samplesDiff) {
        frameCnt++;
        sampleRateFrameAcc += sampleRateFrame;
//        sb.append(secCnt + "," + frameCnt + "," + (sampleRateFrame >> 2) + "," + samplesDiff).append("\n");
        if ((frameCnt % region.getFps()) == 0) {
            secCnt++;
            double sampleRateHz = 0.25 * sampleRateFrameAcc;
            double avgSampleRate = sampleRateHz / frameCnt;

            sb.append(secCnt + ",sampleRateHz: " + (int) sampleRateHz + ", avgSampleRateFrame: " + rateFormatter.format(avgSampleRate));
//            LOG.info(sb.toString());
            sb.setLength(0);
            frameCnt = 0;
            sampleRateFrameAcc = 0;

        }
    }

    /**
     * We want to have a 1 frame delay
     * <p>
     * Frame delay, Sample adjust
     * 0, 4 //increase frame delay
     * 1, 0 //frame delay unchanged
     * 2,-4 //decrease frame delay
     */
    protected static int getRateControl(int current, int syn) {
        assert syn >= current;
        /**
         * 2 might cause buffer corruption/overwrite
         */
        if (Math.abs(current - syn) > 2) {
            LOG.warn("Audio thread too slow: {} vs {}", current, syn);
        }
        int sampleDelta = syn - current;
        if (sampleDelta == 0) {
            //System.out.println(syn + "," +  current  + "," + MAX_SAMPLE_DIFF_PER_FRAME);
            return MAX_SAMPLE_DIFF_PER_FRAME;
        }
        int val = (syn - current - 1) * MAX_SAMPLE_DIFF_PER_FRAME;
        //System.out.println(syn + "," +  current  + "," + val);
        return -Math.min(val, MAX_SAMPLE_DIFF_PER_FRAME);
    }

    private static int resampleHelper(byte[] data, byte[][] output, int bufNum, int inputLen, int outputLen) {
        if (output[bufNum].length < outputLen) {
            output[bufNum] = new byte[outputLen];
        }
        return SoundUtil.resample(data, output[bufNum], inputLen, outputLen);
    }

    private void doStats() {
        if (Telemetry.enableLogToFile) {
            telemetry.addSample("audioSamplesConsumed", samplesConsumedCount);
            telemetry.addSample("audioSamplesProduced", samplesProducedCount);
        }
        samplesConsumedCount = samplesProducedCount = 0;
    }
}

