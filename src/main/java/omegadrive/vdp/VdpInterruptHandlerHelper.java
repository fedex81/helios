/*
 * SmsVdpInterruptHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/04/19 11:33
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

package omegadrive.vdp;

import omegadrive.vdp.md.VdpInterruptHandler;
import omegadrive.vdp.model.BaseVdpProvider;

public class VdpInterruptHandlerHelper {

    public static VdpInterruptHandler createTmsInstance(BaseVdpProvider vdp) {
        VdpInterruptHandler handler = VdpInterruptHandler.createMdInstance(vdp);
        handler.onVdpEvent(BaseVdpProvider.VdpEvent.VIDEO_MODE, vdp.getVideoMode());
        return handler;
    }

    /**
     * http://www.smspower.org/forums/8161-SMSDisplayTiming
     * <p>
     * Out of lines 0-261:
     * - The counter is decremented on lines 0-191 and 192.
     * - The counter is reloaded on lines 193-261.
     * <p>
     * check
     * - OutRun
     * - Ys
     */
    public static VdpInterruptHandler createSmsInstance(BaseVdpProvider vdp) {
        return VdpInterruptHandler.createMdInstance(vdp);
    }
}