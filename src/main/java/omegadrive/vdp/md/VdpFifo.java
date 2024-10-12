/*
 * VdpFifo
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:37
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

package omegadrive.vdp.md;

import omegadrive.util.Fifo;
import omegadrive.vdp.model.MdVdpProvider;

import java.util.stream.IntStream;

public class VdpFifo extends Fifo.FixedSizeFifo<VdpFifo.VdpFifoEntry> {

    public static final int VDP_FIFO_SIZE = 4;

    public static class VdpFifoEntry {
        public MdVdpProvider.VdpPortType portType;
        public MdVdpProvider.VramMode vdpRamMode;
        public int addressRegister;
        public int data;
        public boolean firstByteWritten;

        @Override
        public String toString() {
            return "VdpFifoEntry{" +
                    "portType=" + portType +
                    ", vdpRamMode=" + vdpRamMode +
                    ", addressRegister=" + addressRegister +
                    ", data=" + data +
                    ", firstByteWritten=" + firstByteWritten +
                    '}';
        }
    }

    public VdpFifo() {
        super(VDP_FIFO_SIZE);
        fifo = new VdpFifoEntry[VDP_FIFO_SIZE];
        IntStream.range(0, VDP_FIFO_SIZE).forEach(i -> fifo[i] = new VdpFifoEntry());
    }

    public void push(MdVdpProvider.VramMode vdpRamMode, int addressReg, int data) {
        if (isFull()) {
            LOG.info("FIFO full");
            return;
        }
        VdpFifoEntry entry = fifo[pushPointer];
        entry.data = data;
        entry.addressRegister = addressReg;
        entry.vdpRamMode = vdpRamMode;
        entry.firstByteWritten = false;
        push(entry);
    }
}