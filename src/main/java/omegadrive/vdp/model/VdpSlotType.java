/*
 * VdpSlotType
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

import java.util.Arrays;
import java.util.stream.IntStream;

import static omegadrive.vdp.model.MdVdpProvider.H32_SLOTS;
import static omegadrive.vdp.model.MdVdpProvider.H40_SLOTS;

public enum VdpSlotType {
    NONE,
    REFRESH,
    EXTERNAL;

    public static final VdpSlotType[] h32Slots = new VdpSlotType[H32_SLOTS];
    public static final VdpSlotType[] h40Slots = new VdpSlotType[H40_SLOTS];

    private static final int[] refreshSlotsH32 = {
            32 * 0 + 38,
            32 * 1 + 38,
            32 * 2 + 38,
            32 * 3 + 38,
    };

    private static final int[] refreshSlotsH40 = {
            32 * 0 + 38,
            32 * 1 + 38,
            32 * 2 + 38,
            32 * 3 + 38,
            32 * 4 + 38,
    };

    private static final int[] externalSlotsH32 = {
            32 * 0 + 14, 32 * 0 + 22, 32 * 0 + 30,
            32 * 1 + 14, 32 * 1 + 22, 32 * 1 + 30,
            32 * 2 + 14, 32 * 2 + 22, 32 * 2 + 30,
            32 * 3 + 14, 32 * 3 + 22, 32 * 3 + 30,
            141, 142, 156, 170
    };

    private static final int[] externalSlotsH40 = {
            32 * 0 + 14, 32 * 0 + 22, 32 * 0 + 30,
            32 * 1 + 14, 32 * 1 + 22, 32 * 1 + 30,
            32 * 2 + 14, 32 * 2 + 22, 32 * 2 + 30,
            32 * 3 + 14, 32 * 3 + 22, 32 * 3 + 30,
            32 * 4 + 14, 32 * 4 + 22, 32 * 4 + 30,
            173, 174, 198
    };

    static {
        Arrays.fill(h32Slots, VdpSlotType.NONE);
        Arrays.fill(h40Slots, VdpSlotType.NONE);
        IntStream.of(externalSlotsH32).forEach(i -> h32Slots[i] = VdpSlotType.EXTERNAL);
        IntStream.of(externalSlotsH40).forEach(i -> h40Slots[i] = VdpSlotType.EXTERNAL);
        IntStream.of(refreshSlotsH32).forEach(i -> h32Slots[i] = VdpSlotType.REFRESH);
        IntStream.of(refreshSlotsH40).forEach(i -> h40Slots[i] = VdpSlotType.REFRESH);
    }
}
