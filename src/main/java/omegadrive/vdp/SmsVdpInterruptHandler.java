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

import omegadrive.vdp.gen.VdpInterruptHandler;
import omegadrive.vdp.model.VdpHLineProvider;

public class SmsVdpInterruptHandler extends VdpInterruptHandler {

    //TODO fix
    public static VdpInterruptHandler createTmsInstance() {
        SmsVdpInterruptHandler handler = new SmsVdpInterruptHandler() {
            @Override
            public boolean isDrawFrameSlot() {
                return hCounterInternal == 0 && vCounterInternal == vdpCounterMode.vBlankSet;
            }
        };
        handler.vdpHLineProvider = VdpHLineProvider.NO_PROVIDER;
        handler.reset();
        return handler;
    }

    public static VdpInterruptHandler createInstance(VdpHLineProvider vdpHLineProvider) {
        SmsVdpInterruptHandler handler = new SmsVdpInterruptHandler();
        handler.vdpHLineProvider = vdpHLineProvider;
        handler.reset();
        return handler;
    }

    @Override
    protected void handleHLinesCounterDecrement() {
        boolean reset = vBlankSet || vCounterInternal == 0 || vCounterInternal == VBLANK_CLEAR;
        hLinePassed = reset ? resetHLinesCounter(vdpHLineProvider.getHLinesCounter()) : hLinePassed - 1;
        if (hLinePassed < 0) {
            hIntPending = true;
            logVerbose("Set HIP: true, hLinePassed: %s", hLinePassed);
            eventFlag = true;
            resetHLinesCounter(vdpHLineProvider.getHLinesCounter());
        }
    }
}
