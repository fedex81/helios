/*
 * BaseStateHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/06/19 17:15
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

import omegadrive.util.FileLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Paths;

public interface BaseStateHandler {

    Logger LOG = LogManager.getLogger(BaseStateHandler.class.getSimpleName());
    BaseStateHandler EMPTY_STATE = new BaseStateHandler() {
        @Override
        public Type getType() {
            return null;
        }

        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public int[] getData() {
            return new int[0];
        }
    };

    Type getType();

    String getFileName();

    int[] getData();

    default void storeData() {
        int[] data = getData();
        try {
            FileLoader.writeFile(Paths.get(getFileName()), data);
            LOG.info("Savestate persisted to: {}", getFileName());
        } catch (IOException e) {
            LOG.error("Unable to write file: {}", getFileName(), e);
        }
    }

    enum Type {SAVE, LOAD}
}
