package omegadrive.ui.filter;

import omegadrive.util.VideoMode;

import java.awt.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class BlendFilter implements OutputImageFilter {

    private float hBlend, vBlend;
    private int[] rowBuf = new int[0], rowBuf2 = new int[0];

    public BlendFilter(float h, float v) {
        setHBlend(h);
        setVBlend(v);
    }

    public void setHBlend(float hBlend) {
        this.hBlend = clamp01(hBlend);
    }

    public float getHBlend() {
        return hBlend;
    }

    public void setVBlend(float vBlend) {
        this.vBlend = clamp01(vBlend);
    }

    public float getVBlend() {
        return vBlend;
    }


    @Override
    public FilterResult apply(int[] srcArgb, Dimension srcDim, VideoMode mode, FilterResult out) {
        final int w = srcDim.width;
        final int h = srcDim.height;

        if (hBlend == 0f && vBlend == 0f) {
            out.pixels = srcArgb;
            out.dim.setSize(srcDim);
            return out;
        }

        int[] dst = out.pixels;
        if (dst == null || dst.length != w * h) {
            dst = new int[w * h];
        }
        for (int p = 0; p < dst.length; p++) {
            dst[p] = 0xFF00_0000 | (srcArgb[p] & 0x00FF_FFFE);
        }
        if (hBlend > 0f) {
            horizontalBlend(dst, w, h, hBlend);
        }
        if (vBlend > 0f) {
            verticalBlend(dst, w, h, vBlend);
        }

        out.pixels = dst;
        out.dim.setSize(w, h);
        return out;
    }

    private void horizontalBlend(int[] px, int w, int h, float b) {
        if (w < 3) {
            return;
        }
        final int side = Math.round(b * 128f);
        final int center = 256 - 2 * side;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int left = px[row];
            for (int x = 1; x < w - 1; x++) {
                int c = px[row + x];
                int r = px[row + x + 1];
                int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
                int lr = (left >> 16) & 0xFF, lg = (left >> 8) & 0xFF, lb = left & 0xFF;
                int rr = (r >> 16) & 0xFF, rg = (r >> 8) & 0xFF, rb = r & 0xFF;
                int nr = (cr * center + (lr + rr) * side) >> 8;
                int ng = (cg * center + (lg + rg) * side) >> 8;
                int nb = (cb * center + (lb + rb) * side) >> 8;
                left = c;
                px[row + x] = 0xFF00_0000 | (nr << 16) | (ng << 8) | nb;
            }
        }
    }

    private void verticalBlend(int[] px, int w, int h, float b) {
        if (h < 3) {
            return;
        }
        if (rowBuf.length < w) {
            rowBuf = new int[w];
        }
        if (rowBuf2.length < w) {
            rowBuf2 = new int[w];
        }
        final int side = Math.round(b * 128f);
        final int center = 256 - 2 * side;
        int[] above = rowBuf;
        int[] curr = rowBuf2;
        System.arraycopy(px, 0, above, 0, w);
        System.arraycopy(px, w, curr, 0, w);
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            int below = row + w;
            for (int x = 0; x < w; x++) {
                int c = curr[x];
                int a = above[x];
                int d = px[below + x];
                int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
                int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
                int dr = (d >> 16) & 0xFF, dg = (d >> 8) & 0xFF, db = d & 0xFF;
                int nr = (cr * center + (ar + dr) * side) >> 8;
                int ng = (cg * center + (ag + dg) * side) >> 8;
                int nb = (cb * center + (ab + db) * side) >> 8;
                px[row + x] = 0xFF00_0000 | (nr << 16) | (ng << 8) | nb;
            }
            int[] t = above;
            above = curr;
            curr = t;
            System.arraycopy(px, below, curr, 0, w);
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    @Override
    public String getName() {
        return "Blend";
    }
}
