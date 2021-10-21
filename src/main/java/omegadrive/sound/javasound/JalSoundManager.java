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
import omegadrive.system.perf.Telemetry;
import omegadrive.util.SoundUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jaudiolibs.audioservers.AudioClient;
import org.jaudiolibs.audioservers.AudioConfiguration;
import org.jaudiolibs.audioservers.AudioServer;
import org.jaudiolibs.audioservers.AudioServerProvider;
import org.jaudiolibs.audioservers.ext.ClientID;
import org.jaudiolibs.audioservers.ext.Connections;
import org.jaudiolibs.audioservers.javasound.JSTimingMode;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.ServiceLoader;

public class JalSoundManager extends AbstractSoundManager implements AudioClient {

    private static final Logger LOG = LogManager.getLogger(JalSoundManager.class.getSimpleName());

    //in sample per channels, ie divide by 4 (4 bytes per channel)
    private static final int bufferSize = SoundUtil.getAudioLineBufferSize(audioFormat) >> 2;
    private static final String lib = "JavaSound"; // or "JACK";

    private static final int JAL_TIMING_MODE = Integer.parseInt(System.getProperty("helios.jal.timing.mode", "2"));
    private JSTimingMode timingMode = JSTimingMode.Estimated;

    volatile int[] fm_buf_ints;
    volatile byte[] mix_buf_bytes16Stereo;
    volatile byte[] psg_buf_bytes;
    volatile int fmSizeMono;
    private float[] buffer;

    //stats
    private Telemetry telemetry;
    private volatile int samplesProducedCount, samplesConsumedCount, audioThreadLoops, audioThreadEmptyLoops;

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
        telemetry = Telemetry.getInstance();
        startAudio();
    }

    private void startAudio() {
        AudioServerProvider provider = null;
        for (AudioServerProvider p : ServiceLoader.load(AudioServerProvider.class)) {
            if (lib.equals(p.getLibraryName())) {
                provider = p;
                break;
            }
        }
        if (provider == null) {
            throw new NullPointerException("No AudioServer found that matches : " + lib);
        }
        detectTimingMode();
        AudioClient client = this;
        AudioConfiguration config = new AudioConfiguration(
                audioFormat.getSampleRate(), //sample rate
                0, // input channels
                audioFormat.getChannels(), // output channels
                bufferSize, //buffer size
                false,
                new Object[]{
                        // extensions
                        new ClientID("JalSoundManager"),
                        timingMode,
                        Connections.OUTPUT
                });
        try {
            AudioServer server = provider.createServer(config, client);
            executorService.submit(getServerRunnable(server));
            LOG.info("Audio Max buffer size (in samples per channel): {}", bufferSize);
        } catch (Throwable t) {
            LOG.error(t);
            t.printStackTrace();
        }
    }

    private void detectTimingMode() {
        for (JSTimingMode mode : JSTimingMode.values()) {
            if (mode.ordinal() == JAL_TIMING_MODE) {
                timingMode = mode;
                break;
            }
        }
        LOG.info("Using timing mode: {}", timingMode);
    }

    private Runnable getServerRunnable(AudioServer server) {
        return () -> {
            try {
                server.run();
            } catch (InterruptedException ie) {
                LOG.info("interrupted");
                return;
            } catch (Exception | Error ex) {
                LOG.error(ex);
                ex.printStackTrace();
            }
        };
    }

    private int playOnceStereo(int fmBufferLenMono) {
        int fmMonoActual = fm.update(fm_buf_ints, 0, fmBufferLenMono);
        //if FM is present load a matching number of psg samples
        fmBufferLenMono = hasFm ? fmMonoActual : fmBufferLenMono;
        psg.output(psg_buf_bytes, 0, fmBufferLenMono);
        int fmBufferLenStereo = fmBufferLenMono << 1;
        samplesProducedCount += fmBufferLenStereo;

        try {
            //FM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
            SoundUtil.intStereo14ToByteStereo16MixFloat(fm_buf_ints, buffer, psg_buf_bytes, fmBufferLenStereo);
        } catch (Exception e) {
            LOG.error("Unexpected sound error", e);
        }
        return fmMonoActual;
    }

    @Override
    public void onNewFrame() {
        doStats();
        fm.onNewFrame();
    }

    private void doStats() {
        if (Telemetry.enable) {
            telemetry.addSample("audioThreadLoops", audioThreadLoops);
            telemetry.addSample("audioThreadEmptyLoops", audioThreadEmptyLoops);
            telemetry.addSample("audioSamplesConsumed", samplesConsumedCount);
            telemetry.addSample("audioSamplesProduced", samplesProducedCount);
        }
        audioThreadLoops = audioThreadEmptyLoops = samplesConsumedCount = samplesProducedCount = 0;
    }

    @Override
    public void configure(AudioConfiguration context) throws Exception {
        LOG.info("configure");
    }

    @Override
    public boolean process(long time, List<FloatBuffer> inputs, List<FloatBuffer> outputs, int nframes) {
        // get left and right channels from array list
        FloatBuffer left = outputs.get(0);
        FloatBuffer right = outputs.get(1);
        if (buffer == null || buffer.length != nframes << 1) {
            buffer = new float[nframes << 1]; //stereo
        }
        int resFrames = playOnceStereo(nframes);
        if (resFrames != nframes) {
//            LOG.info("xrun! req {}, actual {}", nframes, resFrames);
        }
        int k = 0;
        for (int i = 0; i < nframes; i++, k += 2) {
            left.put(buffer[k]);
            right.put(buffer[k + 1]);
            if (k == buffer.length) {
                k = 0;
                LOG.warn("wrap");
            }
        }
        return !close;
    }

    @Override
    public void shutdown() {
        LOG.info("Shutdown");
    }
}

