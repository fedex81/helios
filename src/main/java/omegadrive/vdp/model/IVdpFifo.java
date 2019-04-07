/*
 * IVdpFifo
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

import omegadrive.vdp.model.GenesisVdpProvider.VramMode;

public interface IVdpFifo {

    class VdpFifoEntry {
        public GenesisVdpProvider.VdpPortType portType;
        public VramMode vdpRamMode;
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

    void push(VramMode vdpRamMode, int addressReg, int data);

    VdpFifoEntry pop();

    VdpFifoEntry peek();

    boolean isEmpty();

    boolean isFull();

    static IVdpFifo createNoFifo(VdpMemoryInterface memoryInterface) {
        return new IVdpFifo() {

            private VdpFifoEntry latest = new VdpFifoEntry();

            @Override
            public void push(VramMode vdpRamMode, int addressReg, int data) {
                latest.data = data;
                latest.vdpRamMode = vdpRamMode;
                latest.addressRegister = addressReg;
                memoryInterface.writeVideoRamWord(vdpRamMode, data, addressReg);
            }

            @Override
            public VdpFifoEntry pop() {
                throw new IllegalStateException("FIFO is always empty!");
            }

            @Override
            public VdpFifoEntry peek() {
                return latest;
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public boolean isFull() {
                return false;
            }
        };
    }

}
