package omegadrive.sound.fm.ym2612.ymfm;

/**
 *
 * The YM3438 (OPN2C) chip. Differs from the YM2612 only in output scaling (no DAC discontinuity)
 *
 */
public class Ym3438 extends Ym2612 {

    public Ym3438(YmfmInterface intf) {
        super(intf);
    }

    public void generate(YmfmOutput[] output, int outOffset, int numsamples) {
        for (int samp = 0; samp < numsamples; samp++) {
            YmfmOutput out = output[outOffset + samp];

            m_fm.clock(FmEngine.ALL_CHANNELS);
            if (m_dacEnable == 0) {
                m_fm.output(out.clear(), 5, 256, FmEngine.ALL_CHANNELS);
            } else {
                int dacVal = ((short) (m_dacData << 7)) >> 7;
                out.data[0] = (m_fm.regs().chOutput0(0x102) != 0) ? dacVal : 0;
                out.data[1] = (m_fm.regs().chOutput1(0x102) != 0) ? dacVal : 0;
                m_fm.output(out, 5, 256, FmEngine.ALL_CHANNELS ^ (1 << 5));
            }

            //YM3438 is multiplexed like YM2612 but no DAC discontinuity
            out.data[0] = (out.data[0] * 128) / 6;
            out.data[1] = (out.data[1] * 128) / 6;
        }

    }
}
