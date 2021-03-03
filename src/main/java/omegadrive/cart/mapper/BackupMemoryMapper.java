/*
 * BackupMemoryMapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 20/09/19 22:41
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

import omegadrive.SystemLoader;
import omegadrive.util.FileLoader;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public abstract class BackupMemoryMapper {

    private final static Logger LOG = LogManager.getLogger(BackupMemoryMapper.class.getSimpleName());

    protected String defaultSramFolder;

    protected String sramFolder;
    protected String sramFolderProp;

    protected Path backupFile;
    protected int[] sram = new int[0];
    protected String fileType;
    protected String romName;

    protected int sramSize;

    protected BackupMemoryMapper(SystemLoader.SystemType systemType, String fileType, String romName, int sramSize) {
        sramFolderProp = systemType.getShortName().toLowerCase() + ".sram.folder";
        defaultSramFolder = getDefaultBackupFileFolder(systemType);
        sramFolder = System.getProperty(sramFolderProp, defaultSramFolder);
        this.romName = romName;
        this.fileType = fileType;
        this.sramSize = sramSize;
    }

    protected String getDefaultBackupFileFolder(SystemLoader.SystemType type) {
        return System.getProperty("user.home") + File.separator +
                ".helios" + File.separator + type.getShortName().toLowerCase() + File.separator +
                "sram";
    }

    protected void initBackupFileIfNecessary() {
        if (backupFile == null) {
            try {
                backupFile = Paths.get(sramFolder,
                        romName + "." + fileType);
                long size = 0;
                if (Files.isReadable(backupFile)) {
                    size = Files.size(backupFile);
                    if (size > 0) {
                        sram = Util.toUnsignedIntArray(FileLoader.readBinaryFile(backupFile));
                    } else {
                        LOG.error("Backup file with size 0, attempting to recreate it");
                        size = createBackupFile();
                    }
                } else {
                    size = createBackupFile();
                }
                LOG.info("Using sram file: {} size: {} bytes", backupFile, size);
            } catch (Exception e) {
                LOG.error("Unable to create file for: {}", romName);
            }
        }
    }

    private int createBackupFile() {
        LOG.info("Creating backup memory file: {}", backupFile);
        sram = new int[sramSize];
        //see GenTechBulletins, StarTrek echoes fails when reading sram with all 0s
        Arrays.fill(sram, 0xFF);
        FileLoader.writeFileSafe(backupFile, Util.unsignedToByteArray(sram));
        return sram.length;
    }

    protected void writeFile() {
        initBackupFileIfNecessary();
        if (sram.length == 0) {
            LOG.error("Unexpected sram length: {}", sram.length);
            return;
        }
        if (Files.isWritable(backupFile)) {
            LOG.info("Writing to sram file: {}, len: {}", this.backupFile, sram.length);
            FileLoader.writeFileSafe(backupFile, Util.unsignedToByteArray(sram));
        }
    }
}
