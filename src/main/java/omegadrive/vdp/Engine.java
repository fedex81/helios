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

/**
 * TODO remove
 */
public class Engine {

    public static boolean is_gg = false;
    public static boolean is_sms = true;

    /**
     *  Set SMS Mode
     */
    @Deprecated
    public static void setSMS()
    {
        is_sms = true;
        is_gg  = false;

        SmsVdp.h_start = 0;
        SmsVdp.h_end   = 32;

//        emuWidth = 256;
//        emuHeight = 192;
    }

    /**
     *  Set GG Mode
     */
    @Deprecated
    public static void setGG()
    {
        is_gg  = true;
        is_sms = false;

        SmsVdp.h_start = 5;
        SmsVdp.h_end   = 27;

//        emuWidth = 160;
//        emuHeight = 144;
    }


}
