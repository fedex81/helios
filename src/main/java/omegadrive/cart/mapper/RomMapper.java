/*
 * RomMapper
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

package omegadrive.cart.mapper;

import omegadrive.util.Size;

public interface RomMapper {

    String NO_MAPPER_NAME = "NONE";

    RomMapper NO_OP_MAPPER = new RomMapper() {
        @Override
        public int readData(int address, Size size) {
            return -1;
        }

        @Override
        public void writeData(int address, int data, Size size) {

        }
    };

    enum SramMode {DISABLE, READ_ONLY, READ_WRITE}

    int readData(int address, Size size);

    void writeData(int address, int data, Size size);

    default void writeBankData(int addressL, int data) {
        //DO NOTHING
    }

    default void setSramMode(SramMode sramMode) {
        //DO NOTHING
    }

    default void closeRom() {
        //DO NOTHING
    }

    interface StateAwareMapper {
        int[] getState();

        void setState(int[] bankData);
    }

    StateAwareMapper NO_STATE = new StateAwareMapper() {
        @Override
        public int[] getState() {
            return new int[0];
        }

        @Override
        public void setState(int[] bankData) {
        }
    };
}
