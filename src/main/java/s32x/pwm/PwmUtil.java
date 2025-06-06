package s32x.pwm;

import omegadrive.util.BufferUtil;
import omegadrive.util.Fifo;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class PwmUtil {

    private static final Logger LOG = LogHelper.getLogger(PwmUtil.class.getSimpleName());

    public static final Fifo<Integer> EMPTY_FIFO = Fifo.createIntegerFixedSizeFifo(0);

    public static final Warmup NO_WARMUP = new Warmup();
    public static final Warmup WARMUP = new Warmup();

    public static class Warmup {
        static final int stepSamples = 0x7FF;
        static final double stepFactor = 0.02;

        static {
            BufferUtil.assertPowerOf2Minus1("pwmStepSamples", stepSamples);
        }

        boolean isWarmup;
        int currentSamples;
        double currentFactor = 0.0;

        public int doWarmup(int sample) {
            if (isWarmup) {
                int res = (int) (sample * currentFactor);
                addSampleCount(1);
                return res;
            }
            return sample;
        }

        private void addSampleCount(int add) {
            int start = currentSamples & Warmup.stepSamples;
            currentSamples += add;
            if ((currentSamples & Warmup.stepSamples) < start) {
                currentFactor += Warmup.stepFactor;
                if (currentFactor >= 1.0) {
                    reset();
                    LOG.info("PWM warmup done");
                }
                assert currentFactor < 1.0;
            }
        }

        public void doWarmup(int[] out, int len) {
            if (isWarmup) {
                int start = currentSamples & Warmup.stepSamples;
                for (int i = 2; i < len; i += 2) {
                    out[i] *= currentFactor; //currentFactor [0.0,1.0]
                    out[i + 1] *= currentFactor;
                }
                addSampleCount(len); //TODO check this
            }
        }

        public void reset() {
            isWarmup = false;
            currentSamples = 0;
            currentFactor = 0.0;
        }
    }

    static class PwmStats {

        public static final PwmStats NO_STATS = new PwmStats();
        public int monoSamplesFiller = 0, monoSamplesPull = 0, monoSamplesPush = 0, monoSamplesDiscard = 0,
                monoSamplesDiscardHalf = 0;

        public void print(int monoLen) {
            LOG.info("Pwm frame monoSamples, push: {} (discard: {}, discardHalf: {}), pop: {}, filler: {}, " +
                            "tot: {}, monoQLen: {}",
                    monoSamplesPush, monoSamplesDiscard, monoSamplesDiscardHalf, monoSamplesPull, monoSamplesFiller,
                    monoSamplesPull + monoSamplesFiller, monoLen);
        }

        public void reset() {
            monoSamplesFiller = monoSamplesPull = monoSamplesPush = monoSamplesDiscard = monoSamplesDiscardHalf = 0;
        }
    }
}
