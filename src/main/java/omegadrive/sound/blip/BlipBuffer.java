package omegadrive.sound.blip;// Band-limited sound synthesis buffer
// http://www.slack.net/~ant/

/* Copyright (C) 2003-2007 Shay Green. This module is free software; you
can redistribute it and/or modify it under the terms of the GNU Lesser
General Public License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version. This
module is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
details. You should have received a copy of the GNU Lesser General Public
License along with this module; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA */

import java.util.concurrent.atomic.AtomicInteger;

public final class BlipBuffer implements BlipBufferIntf {
    static final boolean muchFaster = true; // speeds synthesis at a cost of quality

    public BlipBuffer() {
        this.accum = new AtomicInteger(0);
        setVolume(1.0);
    }

    // Sets sample rate of output and changes buffer length to msec
    public void setSampleRate(int rate, int msec) {
        sampleRate = rate;
        buf = new int[(int) ((long) msec * rate / 1000) + 1024];
    }

    // Sets input clock rate. Must be set after sample rate.
    public void setClockRate(int rate) {
        assert sampleRate > 0;
        clockRate_ = rate;
        assert clockRate_ > 0;
        factor = (int) (sampleRate / (float) clockRate_ * (1 << timeBits) + 0.5);
    }

    // Current clock rate
    public int clockRate() {
        return clockRate_;
    }

    public int getBufLen() {
        return buf.length;
    }

    // Removes all samples from buffer
    public void clear() {
        offset = 0;
        accum.set(0);
        java.util.Arrays.fill(buf, 0, buf.length, 0);
    }

    // Sets overall volume, where 1.0 is normal
    public void setVolume(double v) {
        final int shift = 15;
        final int round = 1 << (shift - 1);

        volume = (int) ((1 << shift) * v + 0.5) & ~1;

        if (!muchFaster) {
            // build new set of kernels
            int[][] nk = new int[phaseCount + 1][];
            for (int i = nk.length; --i >= 0; ) {
                nk[i] = new int[halfWidth];
            }


            // must be even since center kernel uses same half twice
            final int mul = volume;

            final int pc = phaseCount;
            for (int p = 17; --p >= 0; ) {
                int remain = mul;
                for (int i = 8; --i >= 0; ) {
                    remain -= (nk[p][i] = (baseKernel[p * halfWidth + i] * mul + round) >> shift);
                    remain -= (nk[pc - p][i] = (baseKernel[(pc - p) * halfWidth + i] * mul + round) >> shift);
                }
                nk[p][7] += remain; // each pair of kernel halves must total mul
            }

            // replace kernel atomically
            kernel = nk;
        }
    }

    // Adds delta at given time
    private void addDeltaInternal(int time, int delta) {
        final int[] buf = this.buf;
        time = time * factor + offset;
        final int phase = (time) >>
                (timeBits - phaseBits) & (phaseCount - 1);
        time >>= timeBits;
        if (muchFaster) {
            delta *= volume;
            int right = (delta >> phaseBits) * phase;
//            System.out.println("Time: " + time);
            buf[time] += delta - right;
            buf[time + 1] += right;
        } else {
            // TODO: use smaller kernel

            // left half
            int[] k = kernel[phase];
            buf[time] += k[0] * delta;
            buf[time + 1] += k[1] * delta;
            buf[time + 2] += k[2] * delta;
            buf[time + 3] += k[3] * delta;
            buf[time + 4] += k[4] * delta;
            buf[time + 5] += k[5] * delta;
            buf[time + 6] += k[6] * delta;
            buf[time + 7] += k[7] * delta;

            // right half (mirrored version of a left half)
            k = kernel[phaseCount - phase];
            time += 8;
            buf[time] += k[7] * delta;
            buf[time + 1] += k[6] * delta;
            buf[time + 2] += k[5] * delta;
            buf[time + 3] += k[4] * delta;
            buf[time + 4] += k[3] * delta;
            buf[time + 5] += k[2] * delta;
            buf[time + 6] += k[1] * delta;
            buf[time + 7] += k[0] * delta;
        }
    }

    // Number of samples that would be available at time
    public int countSamples(int time) {
        int last_sample = (time * factor + offset) >> timeBits;
        int first_sample = offset >> timeBits;
        return last_sample - first_sample;
    }

    @Override
    public void addDelta(int time, int deltaL, int deltaR) {
        assert deltaL == deltaR;
        addDeltaInternal(time, deltaL);
    }

    // Ends current time frame and makes samples available for reading
    public void endFrame(int time) {
        offset += time * factor;
        assert samplesAvail() < buf.length;
    }

    // Number of samples available to be read
    public int samplesAvail() {
        return offset >> timeBits;
    }

    public int readSamples16bitStereo(byte[] out, int pos, int countMono) {
        return BlipBufferHelper.readSamples16bitMono_StereoOut(this, out, pos, countMono);
    }

// internal

    static final int timeBits = 16;
    static final int phaseBits = (muchFaster ? 8 : 5);
    final int phaseCount = 1 << phaseBits;
    static final int halfWidth = 8;
    final int stepWidth = halfWidth * 2;

    int factor;
    int offset;
    int[][] kernel;
    AtomicInteger accum;

    int[] buf;
    int sampleRate;
    int clockRate_;
    int volume;

    void removeSilence(int count) {
        offset -= count << timeBits;
        assert samplesAvail() >= 0;
    }

    void removeSamples(int count) {
        int remain = samplesAvail() - count + stepWidth;
        System.arraycopy(buf, count, buf, 0, remain);
        java.util.Arrays.fill(buf, remain, remain + count, 0);
        removeSilence(count);
    }

    // TODO: compute at run-time
    final int[] baseKernel =
            {
                    10, -61, 284, -615, 1359, -1753, 5911, 22498,
                    14, -71, 295, -616, 1314, -1615, 5259, 22472,
                    17, -80, 304, -611, 1260, -1468, 4626, 22402,
                    21, -88, 309, -603, 1200, -1313, 4015, 22285,
                    23, -94, 313, -589, 1134, -1151, 3426, 22122,
                    26, -100, 313, -572, 1063, -986, 2861, 21915,
                    28, -104, 312, -550, 986, -818, 2322, 21663,
                    30, -108, 308, -525, 906, -648, 1810, 21369,
                    31, -110, 302, -497, 823, -478, 1326, 21034,
                    33, -112, 295, -466, 737, -309, 871, 20657,
                    34, -112, 285, -433, 649, -143, 446, 20242,
                    34, -111, 274, -397, 561, 19, 51, 19790,
                    34, -110, 261, -359, 472, 176, -313, 19302,
                    35, -108, 247, -320, 383, 327, -646, 18781,
                    34, -105, 232, -280, 296, 472, -948, 18230,
                    34, -101, 216, -240, 210, 608, -1219, 17651,
                    33, -97, 199, -199, 126, 736, -1459, 17045,
                    32, -92, 182, -158, 45, 855, -1668, 16413,
                    31, -86, 164, -117, -33, 964, -1847, 15761,
                    30, -80, 145, -77, -107, 1063, -1996, 15091,
                    28, -74, 127, -38, -177, 1151, -2117, 14405,
                    26, -67, 108, 0, -243, 1228, -2211, 13706,
                    24, -60, 90, 37, -304, 1294, -2277, 12996,
                    22, -53, 72, 72, -360, 1349, -2318, 12278,
                    20, -46, 54, 105, -410, 1392, -2334, 11556,
                    18, -39, 37, 136, -455, 1425, -2327, 10831,
                    15, -31, 21, 164, -495, 1446, -2298, 10107,
                    13, -24, 5, 191, -529, 1456, -2249, 9385,
                    10, -17, -10, 215, -557, 1456, -2182, 8669,
                    8, -10, -24, 236, -580, 1446, -2096, 7962,
                    5, -3, -37, 255, -597, 1426, -1996, 7265,
                    3, 4, -50, 271, -608, 1397, -1881, 6580,
                    0, 10, -61, 284, -615, 1359, -1753, 5911,
            };
}

// Stereo sound buffer with center channel

