package omegadrive.vdp.model;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
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
