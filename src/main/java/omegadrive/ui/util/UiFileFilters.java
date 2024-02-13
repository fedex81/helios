package omegadrive.ui.util;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ObjectArrays;
import omegadrive.SystemLoader.SystemType;
import omegadrive.system.SysUtil;

import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.*;
import java.util.function.Function;

import static omegadrive.SystemLoader.SystemType.NONE;
import static omegadrive.system.SysUtil.compressedBinaryTypes;
import static omegadrive.system.SysUtil.sysFileExtensionsMap;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class UiFileFilters {

    public enum FileResourceType {ROM, SAVE_STATE_RES}

    private static final Function<String[], String[]> removeExtensionDot = as -> Arrays.stream(as).
            map(s -> s.replace(".", "")).toArray(String[]::new);

    public static final String[] extBinaryTypesList = removeExtensionDot.apply(SysUtil.binaryTypes);
    private static Set<FileFilter> romFilterSet = new LinkedHashSet<>();
    private static Map<String, SystemType> romFilterDescMap = new HashMap<>();

    static {
        FileNameExtensionFilter fn = new FileNameExtensionFilter(
                Arrays.toString(extBinaryTypesList) + " files", extBinaryTypesList);
        romFilterSet.add(fn);
        romFilterDescMap.put(fn.getDescription(), NONE);
        sysFileExtensionsMap.entrySet().stream().forEach(e -> {
            FileNameExtensionFilter fne = new FileNameExtensionFilter(e.getKey().getShortName() + " files",
                    removeExtensionDot.apply(ObjectArrays.concat(e.getValue(), compressedBinaryTypes, String.class)));
            romFilterSet.add(fne);
            romFilterDescMap.put(fne.getDescription(), e.getKey());
        });
    }

    public static final FileFilter SAVE_STATE_FILTER = new FileFilter() {
        @Override
        public String getDescription() {
            return "state files";
        }

        @Override
        public boolean accept(File f) {
            String name = f.getName().toLowerCase();
            return f.isDirectory() || name.contains(".gs") || name.contains(".s0") || name.contains(".n0");
        }
    };

    public static final FileFilter ROM_FILTER = new FileNameExtensionFilter(
            Arrays.toString(extBinaryTypesList) + " files", extBinaryTypesList);

    public static SystemType getSystemTypeFromFilterDesc(FileResourceType resourceType, String desc, SystemType current) {
        if (resourceType == FileResourceType.SAVE_STATE_RES) {
            return current;
        }
        return romFilterDescMap.getOrDefault(desc, NONE);
    }

    public static Set<FileFilter> getFilterSet(FileResourceType resourceType) {
        return switch (resourceType) {
            case ROM -> romFilterSet;
            case SAVE_STATE_RES -> ImmutableSet.of(SAVE_STATE_FILTER);
        };
    }
}
