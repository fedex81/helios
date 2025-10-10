package omegadrive.sound.psg;

import omegadrive.sound.BlipSoundProvider;
import omegadrive.sound.IBlipSoundProvider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public abstract class BlipPsgHandler implements PsgProvider {

    private final static Logger LOG = LogHelper.getLogger(BlipPsgHandler.class.getSimpleName());

    private static int DEFAULT_CLOCK_RATE = AbstractSoundManager.SAMPLE_RATE_HZ;
    private final static int outputBufferSize = 0x10;
    private final static int outputBufferMask = outputBufferSize - 1;

    private final IBlipSoundProvider blipProvider;
    private int tickCnt = 0, samplePlayed = 0;
    private final byte[] output = new byte[outputBufferSize];

    protected BlipPsgHandler(String name) {
        blipProvider = new BlipSoundProvider(name, RegionDetector.Region.USA, AbstractSoundManager.audioFormat,
                DEFAULT_CLOCK_RATE);
    }

    @Override
    public abstract void updateMono8(byte[] out, int offset, int end);

    @Override
    public void updateRate(RegionDetector.Region region, int clockRate) {
        blipProvider.updateRegion(region, clockRate);
    }

    @Override
    public void tick() {
        tickCnt++;
        if ((tickCnt & outputBufferMask) == 0) {
            playAccumulatedSamples(outputBufferSize);
        }
    }

    @Override
    public SampleBufferContext getFrameData() {
        assert tickCnt == 0;
        return blipProvider.getDataBuffer();
    }

    @Override
    public void onNewFrame() {
        if (tickCnt > 0) {
            assert tickCnt - samplePlayed < outputBufferMask;
            playAccumulatedSamples(tickCnt & outputBufferMask);
            //generate new samples
            blipProvider.onNewFrame();
            tickCnt = 0;
        } else {
            LogHelper.logWarnOnce(LOG, "newFrame called with tickCnt: {}", tickCnt);
        }
    }

    private void playAccumulatedSamples(int num) {
        updateMono8(output, 0, num);
        for (int i = 0; i < num; i++) {
            blipProvider.playSample(output[i] << 8, output[i] << 8);
        }
        samplePlayed += num;
    }
}
