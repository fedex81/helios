package mcd.asic;

import java.util.StringJoiner;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class AsicModel {

    //0 end, 1 start
    public enum AsicEvent {AS_STOP, AS_START}

    enum StampRepeat {
        BLANK, REPEAT_MAP;

        static StampRepeat[] vals = StampRepeat.values();
    }

    enum StampSize {
        _16x16(16),
        _32x32(32);

        public final int pixelSize;

        StampSize(int px) {
            pixelSize = px;
        }


        static StampSize[] vals = StampSize.values();
    }

    enum StampMapSize {
        _256x256(256), _4096x4096(4096);

        public final int pixelSize;

        StampMapSize(int px) {
            pixelSize = px;
        }

        static StampMapSize[] vals = StampMapSize.values();
    }

    public enum StampPriorityMode {
        PM_OFF, UNDERWRITE, OVERWRITE, ILLEGAL;
        static StampPriorityMode[] vals = StampPriorityMode.values();
    }

    enum StampRotationDegrees {
        _0, _90, _180, _270;

        static StampRotationDegrees[] vals = StampRotationDegrees.values();
    }

    static class StampData {
        int stampId;

        //applied before rotation
        int horizontalFlip;
        StampRotationDegrees rotation = StampRotationDegrees._0;
    }

    static class TraceTableEntry {
        int startPos, deltax, deltay;
    }

    static class StampConfig {
        StampSize stampSize = StampSize._16x16;
        StampMapSize stampMapSize = StampMapSize._256x256;
        StampRepeat stampRepeat = StampRepeat.BLANK;
        StampPriorityMode priorityMode = StampPriorityMode.PM_OFF;
        int stampStartLocation = 0;
        int vCellSize = 1;

        int imgDestBufferLocation = 0, imgTraceTableLocation = 0;

        int hPixelOffset, vPixelOffset, imgOffset;

        int imgHeightPx, imgWidthPx;

        @Override
        public String toString() {
            return new StringJoiner(", ", StampConfig.class.getSimpleName() + "[", "]")
                    .add("stampSize=" + stampSize)
                    .add("stampMapSize=" + stampMapSize)
                    .add("stampRepeat=" + stampRepeat)
                    .add("priorityMode=" + priorityMode)
                    .add("stampStartLocation=" + th(stampStartLocation))
                    .add("vCellSize=" + vCellSize)
                    .add("imgDestBufferLocation=" + th(imgDestBufferLocation))
                    .add("imgTraceTableLocation=" + th(imgTraceTableLocation))
                    .add("hPixelOffset=" + hPixelOffset)
                    .add("vPixelOffset=" + vPixelOffset)
                    .add("imgHeightPx=" + imgHeightPx)
                    .add("imgWidthPx=" + imgWidthPx)
                    .toString();
        }
    }
}
