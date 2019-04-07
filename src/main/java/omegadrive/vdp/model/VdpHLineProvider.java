/*
 * VdpHLineProvider
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

package omegadrive.vdp.model;

public interface VdpHLineProvider {

    VdpHLineProvider NO_PROVIDER = () -> 0x1FF;

//        The counter is loaded with the contents of register #10 in the following
//        situations:
//
//        - Line zero of the frame.
//        - When the counter has expired.
//        - Lines 225 through 261. (note that line 224 is not included)

    /**
     * getHLinesCounter
     *
     * @return
     */
    int getHLinesCounter();
}
