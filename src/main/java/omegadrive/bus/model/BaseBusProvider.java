/*
 * BaseBusProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 11:51
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

package omegadrive.bus.model;

import omegadrive.Device;
import omegadrive.memory.ReadableByteMemory;
import omegadrive.util.Size;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public interface BaseBusProvider extends Device, ReadableByteMemory {

    int read(int address, Size size);

    void write(int address, int data, Size size);

    default void closeRom() {
        //do nothing
    }

    BaseBusProvider attachDevice(Device device);

    <T extends Device> Optional<T> getBusDeviceIfAny(Class<T> clazz);

    <T extends Device> Set<T> getAllDevices(Class<T> clazz);

    default void attachDevices(Device... device) {
        Arrays.stream(device).forEach(this::attachDevice);
    }
}
