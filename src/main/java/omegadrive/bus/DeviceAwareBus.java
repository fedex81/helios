/*
 * DeviceAwareBus
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 11:12
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
import omegadrive.joypad.JoypadProvider;
import omegadrive.m68k.M68kProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.system.SystemProvider;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public abstract class DeviceAwareBus<V extends BaseVdpProvider> implements BaseBusProvider, BaseVdpProvider.VdpEventListener {

    private Set<Device> deviceSet = new HashSet<>();

    protected IMemoryProvider memoryProvider;
    protected JoypadProvider joypadProvider;
    protected SoundProvider soundProvider;
    protected SystemProvider systemProvider;
    protected V vdpProvider;
    protected Z80Provider z80Provider;
    protected M68kProvider m68kProvider;

    @Override
    public BaseBusProvider attachDevice(Device device) {
        deviceSet.add(device);
        loadMappings();
        return this;
    }

    @Override
    public <T extends Device> Optional<T> getDeviceIfAny(Class<T> clazz) {
        return deviceSet.stream().filter(t -> clazz.isAssignableFrom(t.getClass())).findFirst().map(clazz::cast);
    }

    private void loadMappings() {
        if (memoryProvider == null) {
            memoryProvider = getDeviceIfAny(IMemoryProvider.class).orElse(null);
        }
        if (joypadProvider == null) {
            joypadProvider = getDeviceIfAny(JoypadProvider.class).orElse(null);
        }
        if (soundProvider == null) {
            soundProvider = getDeviceIfAny(SoundProvider.class).orElse(null);
        }
        if (systemProvider == null) {
            systemProvider = getDeviceIfAny(SystemProvider.class).orElse(null);
        }
        if (vdpProvider == null) {
            vdpProvider = (V) getDeviceIfAny(BaseVdpProvider.class).orElse(null);
            Optional.ofNullable(vdpProvider).ifPresent(v -> v.addVdpEventListener(this));
        }
        if (z80Provider == null) {
            z80Provider = getDeviceIfAny(Z80Provider.class).orElse(null);
        }
        if (m68kProvider == null) {
            m68kProvider = getDeviceIfAny(M68kProvider.class).orElse(null);
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
