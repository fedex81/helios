/*
 * MekaStateHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 18:42
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

package omegadrive.savestate;

import omegadrive.bus.z80.MsxBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MsxStateHandler extends TmsZ80StateHandler<MsxBus> {

    private static final Logger LOG = LogManager.getLogger(MsxStateHandler.class.getSimpleName());

    {
        FIXED_SIZE_LIMIT = 0x9000;
    }

    @Override
    public void loadBusContext(MsxBus bus) {
        MsxBus.MsxBusContext context = bus.getContext();
        context.psgAddressLatch = buffer.get() & 0xFF;
        context.slotSelect = buffer.get() & 0xFF;
        context.ppiC_Keyboard = buffer.get() & 0xFF;
        for (int i = 0; i < context.pageSlotMapper.length; i++) {
            context.pageSlotMapper[i] = buffer.getInt();
        }
        for (int i = 0; i < context.pageStartAddress.length; i++) {
            context.pageStartAddress[i] = buffer.getInt();
        }

    }

    @Override
    public void saveBusContext(MsxBus bus) {
        MsxBus.MsxBusContext context = bus.getContext();
        buffer.put((byte) context.psgAddressLatch);
        buffer.put((byte) context.slotSelect);
        buffer.put((byte) context.ppiC_Keyboard);
        StateUtil.setData(buffer, context.pageSlotMapper);
        StateUtil.setData(buffer, context.pageStartAddress);
    }
}
