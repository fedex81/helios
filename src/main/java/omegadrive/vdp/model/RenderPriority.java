package omegadrive.vdp.model;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public enum RenderPriority { //implements Comparable<RenderPriority> {
    BACK_PLANE(RenderType.BACK_PLANE, PriorityType.NO, 0),
    PLANE_B_NO_PRIO(RenderType.PLANE_B, PriorityType.NO, 1),
    PLANE_A_NO_PRIO(RenderType.PLANE_A, PriorityType.NO, 2),
    WINDOW_PLANE_NO_PRIO(RenderType.WINDOW_PLANE, PriorityType.NO, 3),
    SPRITE_NO_PRIO(RenderType.SPRITE, PriorityType.NO, 4),

    PLANE_B_PRIO(RenderType.PLANE_B, PriorityType.YES, 5),
    PLANE_A_PRIO(RenderType.PLANE_A, PriorityType.YES, 6),
    WINDOW_PLANE_PRIO(RenderType.WINDOW_PLANE, PriorityType.YES, 7),
    SPRITE_PRIO(RenderType.SPRITE, PriorityType.YES, 8),;

    RenderType renderType;
    PriorityType priorityType;
    int priorityOrder;

    static RenderPriority[] enums = RenderPriority.class.getEnumConstants();

    RenderPriority(RenderType renderType, PriorityType priorityType, int priorityOrder) {
        this.renderType = renderType;
        this.priorityType = priorityType;
        this.priorityOrder = priorityOrder;
    }

    public RenderType getRenderType() {
        return renderType;
    }

    public PriorityType getPriorityType() {
        return priorityType;
    }

    public int getPriorityOrder() {
        return priorityOrder;
    }


    public static RenderPriority getRenderPriority(RenderType renderType, boolean isPriority) {
        PriorityType pt = isPriority ? PriorityType.YES : PriorityType.NO;
        for (RenderPriority r : enums) {
            if (r.priorityType == pt && r.renderType == renderType) {
                return r;
            }
        }
        return null;
    }
}