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

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.util.FileLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

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
        public ByteBuffer getDataBuffer() {
            return null;
        }
    };

    Type getType();

    String getFileName();

    ByteBuffer getDataBuffer();

    static BaseStateHandler createInstance(SystemLoader.SystemType systemType, Path filePath,
                                           Type type, Set<Device> devices) {
        return createInstance(systemType, filePath.toAbsolutePath().toString(), type, devices);
    }

    static BaseStateHandler createInstance(SystemLoader.SystemType systemType, String fileName,
                                           Type type, Set<Device> devices) {
        BaseStateHandler h = BaseStateHandler.EMPTY_STATE;
        switch (systemType) {
            case GENESIS:
                h = GshStateHandler.createInstance(fileName, type, devices);
                break;
            case SMS:
            case GG:
                h = MekaStateHandler.createInstance(systemType, fileName, type, devices);
                break;
            case MSX:
            case COLECO:
            case SG_1000:
                h = Z80StateBaseHandler.createInstance(fileName, systemType, type, devices);
                break;
            case NES:
                h = NesStateHandler.createInstance(fileName, type);
                break;
            default:
                LOG.error("{} doesn't support savestates", systemType);
                break;
        }
        return h;
    }

    default void processState() {
        //DO NOTHING
    }

    default byte[] getData() {
        return getDataBuffer().array();
    }

    default void storeData() {
        LOG.info("Persisting savestate to: {}", getFileName());
        FileLoader.writeFileSafe(Paths.get(getFileName()), getData());
    }

    enum Type {SAVE, LOAD}
}
