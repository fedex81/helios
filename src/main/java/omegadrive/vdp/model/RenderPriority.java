/*
 * RenderPriority
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.vdp.model;

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

    public static RenderPriority[] enums = RenderPriority.class.getEnumConstants();

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