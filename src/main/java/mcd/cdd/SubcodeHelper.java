package mcd.cdd;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.HexUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_SUBCODE_ADDR;
import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.BufferUtil.writeBufferRaw;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class SubcodeHelper {

    /**
     * sector has 98 frames
     * each frame has 33 bytes = 3234 bytes
     * <p>
     * 24 bytes user data = 2352 bytes
     * 8 bytes ECC = 784 bytes
     * 1 byte subcode = 98 bytes
     * <p>
     * frame 1 and frame 2 contains sync words
     * frame 3 .. 98 have subchannel data
     */
    private final static Logger LOG = LogHelper.getLogger(SubcodeHelper.class.getSimpleName());

    //0xFF_8100 - 0xFF_817E words
    public final static int MCD_SUBCODE_ADDR_REG_DATA_MASK = 0x7E;
    public final static int MCD_SUBCODE_BUFFER_REG_OFFSET = 0x100;
    public final static int SUBCODE_BLOCK_LEN = 98;

    private static RandomAccessFile subcodeFile;
    private static byte[] subcodeData = new byte[96];

    private static int fileBase = 4330020;
    public static boolean rawSubcode;

    public static boolean ok = false;

    static {
        String path = "src/test/resources/subcodes";
        Path p = Paths.get(path, "fleet.subcode"); //4330020
        rawSubcode = false; //p.getFileName().toString().endsWith(".subcode");
        try {
            subcodeFile = new RandomAccessFile(p.toFile(), "r");
            ok = true;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


    public static void cdd_process_subcode(MegaCdMemoryContext memoryContext, int lba) {
        if (!ok) return;
        /* update subcode buffer pointer address */
        int res = bumpSubcodeAddress(memoryContext);
        /* 16-bit register index */
        int index = res;

        if (lba >= 0) {
            /* read interleaved subcode data from .sub file (12 x 8-bit of P subchannel first, then Q subchannel, etc) */
            int pos = fileBase + lba * 96;
            try {
                subcodeFile.seek(pos);
                subcodeFile.read(subcodeData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            LogHelper.logWarnOnce(LOG, "Subcode lba < 0, ignoring: {}", lba);
            Arrays.fill(subcodeData, (byte) 0);
        }
        if (rawSubcode) {
            for (int i = 0; i < 96; i += 2) {

                int code = ((subcodeData[i] << 8) & 0xFF00) | (subcodeData[i + 1]) & 0xFF;
                /* subcode buffer is accessed as 16-bit words */
                writeSubcodeBufferWord(memoryContext, index, code);
                /* subcode buffer is limited to 64 x 16-bit words */
                index = (index + 2) & 0x7e;
            }
        } else {
            /* convert back to raw subcode format (96 bytes with 8 x P-W subchannel bits per byte) */
            for (int i = 0; i < 96; i += 2) {
                int code = 0;
                for (int j = 0; j < 8; j++) {
                    int bits = (subcodeData[(j * 12) + (i / 8)] >> (6 - (i & 6))) & 3;
                    code |= ((bits & 1) << (7 - j));
                    code |= ((bits >> 1) << (15 - j));
                }

                /* subcode buffer is accessed as 16-bit words */
                writeSubcodeBufferWord(memoryContext, index, code);

                /* subcode buffer is limited to 64 x 16-bit words */
                index = (index + 2) & 0x7e;
            }
        }
    }

    public static int bumpSubcodeAddress(MegaCdMemoryContext memoryContext) {
        int res = readBuffer(memoryContext.commonGateRegsBuf, MCD_SUBCODE_ADDR.addr + 1, Size.BYTE);
        res = (res + SUBCODE_BLOCK_LEN) & MCD_SUBCODE_ADDR_REG_DATA_MASK;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_SUBCODE_ADDR.addr + 1, res, Size.BYTE);
        return res;
    }

    public static void writeSubcodeBufferWord(MegaCdMemoryContext memoryContext, int index, int val) {
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_SUBCODE_BUFFER_REG_OFFSET + index, val, Size.WORD);
    }


    private static final int SECTOR_SIZE = 96;
    private static final int PACK_SIZE = 24;
    private static final int SECTOR_SPREAD = 2;
    private static final int RW_MASK = 0x3f;

    private static final int[] DEINTERLEAVE = computeDeinterleave();

    public static int[] computeDeinterleave() {
        int[] offsets = new int[SECTOR_SIZE];

        for (int i = 0; i < SECTOR_SIZE; i++) {
            int pack = i / PACK_SIZE;
            int col = i % PACK_SIZE;

            // Perform the manual column swaps
            switch (col) {
                case 1:
                    col = 18;
                    break;
                case 18:
                    col = 1;
                    break;
                case 2:
                    col = 5;
                    break;
                case 5:
                    col = 2;
                    break;
                case 3:
                    col = 23;
                    break;
                case 23:
                    col = 3;
                    break;
                default:
                    break; // Keep original col
            }

            int lookahead = col % 8;
            offsets[i] = (pack + lookahead) * PACK_SIZE + col;
        }
        return offsets;
    }


    public static byte[] deinterleaveAndMask(byte[] buf, int start) {
        byte[] result = new byte[SECTOR_SIZE];

        for (int i = 0; i < SECTOR_SIZE; i++) {
            // Look up the physical offset from our pre-computed table
            byte b = buf[start + DEINTERLEAVE[i]];

            // Apply the mask and store
            // Note: (byte) cast is needed because bitwise ops in Java promote to int
            result[i] = (byte) (b & RW_MASK);
        }

        return result;
    }

    public static byte[] deinterleaveAll(byte[] buf) {
        byte[] result = new byte[buf.length];

        // Process the buffer in sector-sized steps
        // We stop before the very end to avoid IndexOutOfBounds from the 'lookahead'
        for (int sectorStart = 0; sectorStart <= buf.length - SECTOR_SIZE; sectorStart += SECTOR_SIZE) {

            for (int i = 0; i < SECTOR_SIZE; i++) {
                int physicalIndex = sectorStart + DEINTERLEAVE[i];

                // Safety check for the lookahead
                if (physicalIndex < buf.length) {
                    result[sectorStart + i] = (byte) (buf[physicalIndex] & RW_MASK);
                }
            }
        }
        return result;
    }


    /**
     * Equivalent to
     * ./redumper-extract-rw fleet fleet.redump.cdg
     */
    public static void main(String[] args) throws IOException {
        String path = "src/test/resources/subcodes";
        Path p = Paths.get(path, "fleet.subcode");
        Path p1 = Paths.get(path, "fleet.subcode.test");
        byte[] in = Files.readAllBytes(p);
        int len = in.length - SECTOR_SIZE;

        //redumper-extract-rw finds sectorStart by looking at the TOC
        int sectorStart = 45150 - SECTOR_SPREAD; //45150
        int ss = Math.max(0, sectorStart - SECTOR_SPREAD);
        int byteStart = ss * SECTOR_SIZE;
        System.out.println("Start: " + th(byteStart));
        byte[] trimmed = Arrays.copyOfRange(in, byteStart, len);
        byte[] out = deinterleaveAll(trimmed);
        System.out.println(out.length);
        Files.write(p1, out);
    }

    private static String getHexString(byte[] b, int start, int len) {
        StringBuilder sb = new StringBuilder();
        HexUtil.fillFormattedString_ZeroBased(sb, b, start, start + len, false, false);
        String exp = sb.toString();
        sb.setLength(0);
        return exp;
    }
}



