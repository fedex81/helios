package omegadrive.vdp.model;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class VdpMisc {

    public enum PriorityType {
        NO,
        YES
    }

    public enum RenderType {
        BACK_PLANE,
        WINDOW_PLANE,
        PLANE_A,
        PLANE_B,
        SPRITE,
        FULL
    }

    public enum ShadowHighlightType {
        SHADOW,
        NORMAL,
        HIGHLIGHT;

        public ShadowHighlightType brighter() {
            return this == NORMAL ? HIGHLIGHT : (this == SHADOW ? NORMAL : HIGHLIGHT);
        }

        public ShadowHighlightType darker() {
            return this == NORMAL ? SHADOW : (this == HIGHLIGHT ? NORMAL : SHADOW);
        }
    }
}
