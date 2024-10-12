/*
 * VdpMemoryInterface
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 19/07/19 11:07
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

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

public interface VdpMemoryInterface {

    Logger LOG = LogHelper.getLogger(VdpMemoryInterface.class.getSimpleName());

    void writeVideoRamWord(MdVdpProvider.VdpRamType vramType, int data, int address);

    void writeVideoRamByte(MdVdpProvider.VdpRamType vramType, int address, byte data);

    byte readVideoRamByte(MdVdpProvider.VdpRamType vramType, int address);

    int readVideoRamWord(MdVdpProvider.VdpRamType vramType, int address);

    int[] getJavaColorPalette();

    ByteBuffer getVram();

    default ByteBuffer getCram() {
        throw new RuntimeException("Cram not available");
    }

    default ByteBuffer getVsram() {
        throw new RuntimeException("Vsram not available");
    }

    default int[] getSatCache() {
        return new int[0];
    }

    default void setSatBaseAddress(int address) {
        //DO NOTHING
    }

    default void writeVideoRamWord(MdVdpProvider.VramMode mode, int data, int address) {
        if (mode == null) {
            LOG.warn("writeDataPort when vramMode is not set, address {} , size {}", address, Size.WORD);
            return;
        }
        writeVideoRamWord(mode.getRamType(), data, address);
    }

    default int readVideoRamWord(MdVdpProvider.VramMode mode, int address) {
        if (mode == null) {
            LOG.warn("readDataPort when vramMode is not set, address {} , size {}", address, Size.WORD);
            return 0;
        }
        return readVideoRamWord(mode.getRamType(), address);
    }

}
