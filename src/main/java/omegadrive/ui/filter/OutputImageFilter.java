package omegadrive.ui.filter;

import omegadrive.util.VideoMode;

import java.awt.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public interface OutputImageFilter {

    class FilterResult {
        public int[] pixels;
        public final Dimension dim = new Dimension();
    }

    FilterResult apply(int[] srcArgb, Dimension srcDim, VideoMode mode, FilterResult out);

    String getName();

    OutputImageFilter NONE = new OutputImageFilter() {
        @Override
        public FilterResult apply(int[] srcArgb, Dimension srcDim, VideoMode mode, FilterResult out) {
            out.pixels = srcArgb;
            out.dim.setSize(srcDim);
            return out;
        }

        @Override
        public String getName() {
            return "None";
        }
    };
}
