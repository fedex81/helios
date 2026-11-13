package omegadrive.sound.fm.ym2612.ymfm;

import java.util.Arrays;

import static omegadrive.sound.fm.ym2612.ymfm.Ymfm.bitfield;
import static omegadrive.sound.fm.ym2612.ymfm.Ymfm.clamp;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class FmChannel {

    private final int m_choffs;
    private final short[] m_feedback = new short[2];
    private short m_feedbackIn;
    private final FmOperator[] m_op = new FmOperator[4];
    private final OpnaRegisters m_regs;
    private final FmEngine m_owner;

    public FmChannel(FmEngine engine, int choffs) {
        m_choffs = choffs;
        Arrays.fill(m_op, null);
        m_regs = engine.regs();
        m_owner = engine;
        reset();
    }

    public void saveRestore(YmfmSavedState state) {
        m_feedback[0] = (short) state.saveRestore16(m_feedback[0]);
        m_feedback[1] = (short) state.saveRestore16(m_feedback[1]);
        m_feedbackIn = (short) state.saveRestore16(m_feedbackIn);
    }

    public void reset() {
        Arrays.fill(m_feedback, (short) 0);
        m_feedbackIn = 0;
    }

    public int choffs() {
        return m_choffs;
    }

    public void assign(int index, FmOperator op) {
        m_op[index] = op;
        if (op != null)
            op.setChoffs(m_choffs);
    }

    public void keyonoff(int states, int type, int chnum) {
        for (int opnum = 0; opnum < m_op.length; opnum++) {
            if (m_op[opnum] != null) {
                m_op[opnum].keyonoff(bitfield(states, opnum), type);
            }
        }
    }

    public boolean prepare() {
        int activeMask = 0;
        for (int opnum = 0; opnum < m_op.length; opnum++) {
            if (m_op[opnum] != null) {
                if (m_op[opnum].prepare())
                    activeMask |= 1 << opnum;
            }
        }
        return activeMask != 0;
    }

    public void clock(int envCounter, int lfoRawPm) {
        m_feedback[0] = m_feedback[1];
        m_feedback[1] = m_feedbackIn;
        for (int opnum = 0; opnum < m_op.length; opnum++) {
            if (m_op[opnum] != null) {
                m_op[opnum].clock(envCounter, lfoRawPm);
            }
        }
    }


    //output2op
    public void output2op(YmfmOutput output, int rshift, int clipmax) {
        int amOffset = m_regs.lfoAmOffset(m_choffs);

        int opmod = 0;
        int feedback = m_regs.chFeedback(m_choffs);
        if (feedback != 0) {
            opmod = (m_feedback[0] + m_feedback[1]) >> (10 - feedback);
        }

        m_feedbackIn = (short) m_op[0].computeVolume(m_op[0].phase() + opmod, amOffset);
        int op1value = m_feedbackIn;

        if (m_regs.chOutputAny(m_choffs) == 0)
            return;

        int result;
        if (bitfield(m_regs.chAlgorithm(m_choffs), 0) == 0) {
            opmod = op1value >> 1;
            result = m_op[1].computeVolume(m_op[1].phase() + opmod, amOffset) >> rshift;
        } else {
            result = op1value >> rshift;
            result += m_op[1].computeVolume(m_op[1].phase(), amOffset) >> rshift;
            int clipmin = -clipmax - 1;
            result = clamp(result, clipmin, clipmax);
        }

        addToOutput(m_choffs, output, result);
    }

    //output4op
    private static int algorithm(int op2in, int op3in, int op4in, int op1out, int op2out, int op3out) {
        return op2in | ((op3in) << 1) | ((op4in) << 4) | ((op1out) << 7) | ((op2out) << 8) | (op3out << 9);
    }


    private static final int[] S_ALGORITHM_OPS = {
            algorithm(1, 2, 3, 0, 0, 0), //0
            algorithm(0, 5, 3, 0, 0, 0), //1
            algorithm(0, 2, 6, 0, 0, 0), //2
            algorithm(1, 0, 7, 0, 0, 0), //3
            algorithm(1, 0, 3, 0, 1, 0), //4
            algorithm(1, 1, 1, 0, 1, 1), //5
            algorithm(1, 0, 0, 0, 1, 1), //6
            algorithm(0, 0, 0, 1, 1, 1), //7
            algorithm(1, 2, 3, 0, 0, 0), //8
            algorithm(0, 2, 3, 1, 0, 0), //9
            algorithm(1, 0, 3, 0, 1, 0), //10
            algorithm(0, 2, 0, 1, 0, 1), //11

    };

    public void output4op(YmfmOutput output, int rshift, int clipmax) {
        int amOffset = m_regs.lfoAmOffset(m_choffs);

        int opmod = 0;
        int feedback = m_regs.chFeedback(m_choffs);
        if (feedback != 0) {
            opmod = (m_feedback[0] + m_feedback[1]) >> (10 - feedback);
        }

        m_feedbackIn = (short) m_op[0].computeVolume(m_op[0].phase() + opmod, amOffset);
        int op1value = m_feedbackIn;

        if (m_regs.chOutputAny(m_choffs) == 0)
            return;

        int algorithmOps = S_ALGORITHM_OPS[m_regs.chAlgorithm(m_choffs)];

        short[] opout = new short[8];
        opout[0] = 0;
        opout[1] = (short) op1value;

        opmod = opout[bitfield(algorithmOps, 0, 1)] >> 1;
        opout[2] = (short) m_op[1].computeVolume(m_op[1].phase() + opmod, amOffset);
        opout[5] = (short) (opout[1] + opout[2]);

        opmod = opout[bitfield(algorithmOps, 1, 3)] >> 1;
        opout[3] = (short) m_op[2].computeVolume(m_op[2].phase() + opmod, amOffset);
        opout[6] = (short) (opout[1] + opout[3]);
        opout[7] = (short) (opout[2] + opout[3]);

        int result;
        if (m_regs.noiseEnable() != 0 && m_choffs == 7) {
            result = m_op[3].computeNoiseVolume(amOffset);
        } else {
            opmod = opout[bitfield(algorithmOps, 4, 3)] >> 1;
            result = m_op[3].computeVolume(m_op[3].phase() + opmod, amOffset);
        }
        result >>= rshift;

        int clipmin = -clipmax - 1;
        if (bitfield(algorithmOps, 7) != 0) {
            result = clamp(result + (opout[1] >> rshift), clipmin, clipmax);
        }
        if (bitfield(algorithmOps, 8) != 0) {
            result = clamp(result + (opout[2] >> rshift), clipmin, clipmax);
        }
        if (bitfield(algorithmOps, 9) != 0) {
            result = clamp(result + (opout[3] >> rshift), clipmin, clipmax);
        }
        addToOutput(m_choffs, output, result);
    }

    public boolean is4op() {
        return (OpnaRegisters.OPERATORS / OpnaRegisters.CHANNELS == 4);
    }

    public OpnaRegisters regs() {
        return m_regs;
    }

    private void addToOutput(int choffs, YmfmOutput output, int value) {
        //OUTPUTS == 2
        if (m_regs.chOutput0(choffs) != 0)
            output.data[0] += value;
        if (m_regs.chOutput1(choffs) != 0)
            output.data[1] += value;
    }

}
