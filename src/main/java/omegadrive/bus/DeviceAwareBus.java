/*
 * DeviceAwareBus
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

package omegadrive.bus;

import omegadrive.Device;
import omegadrive.system.SystemProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.m68k.M68kProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;

import java.util.HashSet;
import java.util.Set;

public abstract class DeviceAwareBus implements BaseBusProvider {

    private Set<Device> deviceSet = new HashSet<>();

    protected IMemoryProvider memoryProvider;
    protected JoypadProvider joypadProvider;
    protected SoundProvider soundProvider;
    protected SystemProvider systemProvider;
    protected BaseVdpProvider vdpProvider;
    protected Z80Provider z80Provider;
    protected M68kProvider m68kProvider;

    @Override
    public BaseBusProvider attachDevice(Device device) {
        deviceSet.add(device);
        loadMappings();
        return this;
    }

    private static <T extends Device> T getDevice(Set<Device> deviceSet, Class<T> clazz) {
        return deviceSet.stream().filter(t -> clazz.isAssignableFrom(t.getClass())).findFirst().map(clazz::cast).orElse(null);
    }

    protected Set<Device> getDeviceSet() {
        return deviceSet;
    }

    private void loadMappings() {
        if (memoryProvider == null) {
            memoryProvider = getDevice(getDeviceSet(), IMemoryProvider.class);
        }
        if (joypadProvider == null) {
            joypadProvider = getDevice(getDeviceSet(), JoypadProvider.class);
        }
        if (soundProvider == null) {
            soundProvider = getDevice(getDeviceSet(), SoundProvider.class);
        }
        if (systemProvider == null) {
            systemProvider = getDevice(getDeviceSet(), SystemProvider.class);
        }
        if (vdpProvider == null) {
            vdpProvider = getDevice(getDeviceSet(), BaseVdpProvider.class);
        }
        if (z80Provider == null) {
            z80Provider = getDevice(getDeviceSet(), Z80Provider.class);
        }
        if (m68kProvider == null) {
            m68kProvider = getDevice(getDeviceSet(), M68kProvider.class);
        }

    }

    @Override
    public void reset() {
        deviceSet.clear();
        memoryProvider = null;
        joypadProvider = null;
        soundProvider = null;
        systemProvider = null;
        vdpProvider = null;
        z80Provider = null;
        m68kProvider = null;
    }
}
