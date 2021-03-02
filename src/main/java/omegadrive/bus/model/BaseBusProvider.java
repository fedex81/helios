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
import omegadrive.util.Size;

import java.util.Optional;
import java.util.Set;

public interface BaseBusProvider extends Device {

    long read(long address, Size size);

    void write(long address, long data, Size size);

    void writeIoPort(int port, int value);

    int readIoPort(int port);

    default void closeRom() {
        //do nothing
    }

    BaseBusProvider attachDevice(Device device);

    <T extends Device> Optional<T> getBusDeviceIfAny(Class<T> clazz);

    <T extends Device> Set<T> getAllDevices(Class<T> clazz);
}
