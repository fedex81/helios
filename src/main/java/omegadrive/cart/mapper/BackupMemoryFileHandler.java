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
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class BackupMemoryFileHandler {

    private final static Logger LOG = LogHelper.getLogger(BackupMemoryFileHandler.class.getSimpleName());

    public static final byte DEFAULT_BACKUP_RAM_BYTE = (byte) 0xFF;

    protected final String defaultBackupRamFolder;

    protected final String backupRamFolder;
    protected final String backupRamFolderProp;

    protected Path backupFile;
    protected byte[] backupRam = new byte[0];
    protected final String fileType;
    protected final String romName;

    protected final int backupRamSize, backupRamMask;

    private final AtomicInteger lastWrittenHash = new AtomicInteger();

    public BackupMemoryFileHandler(SystemLoader.SystemType systemType, String fileType, String romName, int backupRamSize) {
        backupRamFolderProp = systemType.getShortName().toLowerCase() + ".backupram.folder";
        defaultBackupRamFolder = getDefaultBackupFileFolder(systemType);
        backupRamFolder = System.getProperty(backupRamFolderProp, defaultBackupRamFolder);
        this.romName = romName;
        this.fileType = fileType;
        this.backupRamSize = backupRamSize;
        backupRamMask = Util.getRomMask(backupRamSize);
    }

    protected String getDefaultBackupFileFolder(SystemLoader.SystemType type) {
        return System.getProperty("user.home") + File.separator +
                ".helios" + File.separator + type.getShortName().toLowerCase() + File.separator +
                "sram";
    }

    public void initBackupFileIfNecessary() {
        if (backupFile == null) {
            try {
                backupFile = Paths.get(backupRamFolder,
                        romName + "." + fileType);
                long size;
                if (Files.isReadable(backupFile)) {
                    size = Files.size(backupFile);
                    if (size > 0) {
                        backupRam = FileUtil.readBinaryFile(backupFile);
                    } else {
                        LOG.error("Backup file with size 0, attempting to recreate it");
                        size = createBackupFile();
                    }
                } else {
                    size = createBackupFile();
                }
                LOG.info("Using backupRam file: {} size: {} bytes", backupFile, size);
                lastWrittenHash.set(Arrays.hashCode(backupRam));
            } catch (Exception e) {
                LOG.error("Unable to create file for: {}", romName);
            }
        }
    }

    public byte[] getBackupRam() {
        return backupRam;
    }

    private int createBackupFile() {
        LOG.info("Creating backup memory file: {}", backupFile);
        backupRam = new byte[backupRamSize];
        //see GenTechBulletins, StarTrek echoes fails when reading sram with all 0s
        Arrays.fill(backupRam, DEFAULT_BACKUP_RAM_BYTE);
        if (backupFile.getParent().toFile().mkdirs()) {
            LOG.info("Creating folders: {}", backupFile.getParent());
        }
        FileUtil.writeFileSafeAsync(backupFile, backupRam);
        return backupRam.length;
    }

    public void writeFile() {
        initBackupFileIfNecessary();
        if (backupRam.length == 0) {
            LOG.error("Unexpected backupRam length: {}", backupRam.length);
            return;
        }
        if (Files.isWritable(backupFile)) {
            int h = Arrays.hashCode(backupRam);
            boolean update = lastWrittenHash.get() != h;
            if (update) {
                FileUtil.writeFileSafeAsync(backupFile, backupRam);
                lastWrittenHash.set(h);
            }
            LOG.info((update ? "" : "Not ") + "writing to backupRam file: {}, len: {}, hc: {}",
                    backupFile, backupRam.length, h);
        }
    }
}
