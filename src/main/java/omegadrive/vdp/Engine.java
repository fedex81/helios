/*
 * Engine
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 19/04/19 15:54
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

import omegadrive.bus.sg1k.SmsBus;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;

/**
 * TODO remove
 */
public class Engine {

    public static boolean is_gg = false;
    public static boolean is_sms = true;
    public static int no_of_scanlines = BaseVdpProvider.NTSC_SCANLINES; //or PAL

    public static SmsBus smsBus;
    public static Z80Provider z80Provider;

    /** Game Gear Start Button */
    public static int ggstart;

    static class Z80 {
        private static boolean interruptLine;
    }

    public static void setInterruptLine(boolean value){
        Z80.interruptLine = value;
        z80Provider.interrupt(value);
    }

    /**
     *  Set SMS Mode
     */

    public static void setSMS()
    {
        is_sms = true;
        is_gg  = false;

        SmsVdp.h_start = 0;
        SmsVdp.h_end   = 32;
    }


}
