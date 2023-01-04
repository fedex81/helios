/*
 * VdpDmaHandler
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

import omegadrive.util.VideoMode;

public interface VdpDmaHandler {

    enum DmaMode {
        MEM_TO_VRAM, VRAM_FILL, VRAM_COPY
    }

    DmaMode getDmaMode();

    DmaMode setupDma(GenesisVdpProvider.VramMode vramMode, int data, boolean m1);

    boolean doDmaSlot(VideoMode videoMode);

    void setupDmaFillMaybe(boolean isDma, int data);

    boolean dmaInProgress();

    default String getDmaStateString() {
        return "Not implemented";
    }
}
