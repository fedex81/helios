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

import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.persist.FileSoundPersister;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.perf.Telemetry;
import omegadrive.util.PriorityThreadFactory;
import omegadrive.util.RegionDetector;
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

import javax.sound.sampled.SourceDataLine;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class JalSoundManager extends AbstractSoundManager implements AudioClient {

    private static final Logger LOG = LogManager.getLogger(JalSoundManager.class.getSimpleName());

    volatile int[] fm_buf_ints;
    volatile byte[] mix_buf_bytes16Stereo;
    volatile byte[] psg_buf_bytes;
    volatile int fmSizeMono;
    private float[] buffer;
    private Thread runner;
    private AtomicLong counter = new AtomicLong();

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
    }

    @Override
    protected void init(RegionDetector.Region region) {
        this.region = region;
        soundPersister = new FileSoundPersister();
        fmSize = SoundProvider.getFmBufferIntSize(audioFormat);
        psgSize = SoundProvider.getPsgBufferByteSize(audioFormat);
        executorService = Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY, JavaSoundManager.class.getSimpleName()));
        LOG.info("Output audioFormat: " + audioFormat + ", bufferSize: " + fmSize);
        init();
        startAudio();
    }

    public void startAudio() {

        /* Search for an AudioServerProvider that matches the required library name
         * using the ServiceLoader mechanism. This removes the need for a direct
         * dependency on any particular server implementation.
         */
        String lib = "JavaSound"; // or "JACK";

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

        /* Create an instance of our client - see methods in the implementation
         * below for more information.
         */
        AudioClient client = this;

        /* Create an audio configuration.
         *
         * The configuration is a hint to the AudioServer. Some servers (eg. JACK)
         * will ignore the sample rate and buffersize here.
         * The correct values will be passed to the client during configuration.
         *
         * Various extension objects can be added to the AudioConfiguration.
         * The ClientID and Connections parameters here will be used by the JACK server.
         *
         */
        AudioConfiguration config = new AudioConfiguration(
                SAMPLE_RATE_HZ, //sample rate
                0, // input channels
                2, // output channels
                2205 / 2, //buffer size
                false,
                // extensions
                new Object[]{
                        new ClientID("JalSoundManager"),
                        JSTimingMode.FramePosition,
                        Connections.OUTPUT
                });


        /* Use the AudioServerProvider to create an AudioServer for the client.
         */
        try {
            AudioServer server = provider.createServer(config, client);

            /* Create a Thread to run our server. All servers require a Thread to run in.
             */
            runner = new Thread(getServerRunnable(server));
            // set the Thread priority as high as possible.
            runner.setPriority(Thread.MAX_PRIORITY);
            runner.setName("jalAudio-" + counter.getAndIncrement());
            // and start processing audio - you'll have to kill the program manually!
            runner.start();
        } catch (Throwable t) {
            LOG.error(t);
            t.printStackTrace();
        }
    }

    private Runnable getServerRunnable(AudioServer server) {
        return () -> {
            // The server's run method can throw an Exception so we need to wrap it
            try {
                server.run();
            } catch (InterruptedException ie) {
                LOG.info("interrupted");
                return;
            } catch (Exception ex) {
                LOG.error(ex);
                ex.printStackTrace();
            }
        };
    }

    @Override
    protected Runnable getRunnable(SourceDataLine dataLine, RegionDetector.Region region) {
        return () -> {
        };
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
            //FM: stereo 16 bit, PSG: mono 8 bit, OUT: stereo 16 bit
            SoundUtil.intStereo14ToByteStereo16MixFloat(fm_buf_ints, buffer, psg_buf_bytes, fmBufferLenStereo);
        } catch (Exception e) {
            LOG.error("Unexpected sound error", e);
        }
        Arrays.fill(fm_buf_ints, 0);
        Arrays.fill(psg_buf_bytes, SoundUtil.ZERO_BYTE);
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
        if (buffer == null || buffer.length != nframes) {
            buffer = new float[nframes << 1]; //stereo
        }
        int resFrames = playOnceStereo(nframes);
        if (resFrames != nframes) {
            LOG.info("xrun! req {}, actual {}", nframes, resFrames);
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
        LOG.info("shutdown");
        runner.interrupt();
    }

    @Override
    public void reset() {
        super.reset();
        shutdown();
    }
}

