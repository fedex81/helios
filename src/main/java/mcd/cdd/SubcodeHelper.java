package mcd.cdd;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.HexUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static mcd.cdd.SubcodeHelper.SubFormat.*;
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


    public final static int SUBCODE_DUMP_BLOCK_LEN = 96;
    /**
     * 96 frames of user data + 2 frames of sync
     */
    public final static int SUBCODE_BLOCK_LEN = SUBCODE_DUMP_BLOCK_LEN + 2;

    public enum SubFormat {
        SUB, SUBCODE, CDG;

        public String asFileExt() {
            return "." + name().toLowerCase();
        }
    }


    private SubFormat subFormat;
    private RandomAccessFile subcodeFile;
    private final byte[] subcodeData = new byte[SUBCODE_DUMP_BLOCK_LEN];

    //only for fleet.subcode
    private int fileBase = 0;
    public boolean ok = false;

    private final MegaCdMemoryContext ctx;
    private final Path filePath;

    private void discoverSubcodeFile(Path path) {
        assert Files.isRegularFile(filePath) && !Files.isDirectory(filePath);
        String fname = Util.getNameWithoutExtension(path.getFileName().toString());
        String folder = path.getParent().toString();
        Path sub = Paths.get(folder, fname + SUB.asFileExt());
        Path subcode = Paths.get(folder, fname + SUBCODE.asFileExt());
        Path cdg = Paths.get(folder, fname + CDG.asFileExt());

        Path chosen = null;
        SubFormat fmt = null;
        int base = 0;
        if (Files.exists(cdg)) {
            chosen = cdg;
            fmt = CDG; //CDG is byte aligned (no lead-in)
        } else if (Files.exists(sub)) {
            chosen = sub;
            fmt = SUB;
        } else if (Files.exists(subcode)) {
            chosen = subcode;
            fmt = SUBCODE;
            int startSec = detectSubcodeDataStartSector(subcode);
            base = startSec * SUBCODE_DUMP_BLOCK_LEN;
            LOG.info("Detected subcode data start sector: {}", startSec);
        }

        RandomAccessFile f = null;
        boolean okInit = false;
        if (chosen != null) {
            LOG.info("{} file detected: {}", fmt, chosen.getFileName());
            try {
                f = new RandomAccessFile(chosen.toFile(), "r");
                okInit = true;
            } catch (FileNotFoundException e) {
                LOG.warn("Unable to load {} file: {}", fmt, chosen.getFileName());
            }
        }
        subcodeFile = f;
        subFormat = fmt;
        fileBase = base;
        ok = okInit;
        if (!ok) {
            LOG.info("Unable to find subcode/cdg file: {}", folder);
        }
    }

    public static SubcodeHelper create(MegaCdMemoryContext memoryContext, Path filePath) {
        return new SubcodeHelper(memoryContext, filePath);
    }

    private SubcodeHelper(MegaCdMemoryContext ctx, Path path) {
        this.ctx = ctx;
        this.filePath = path;
        discoverSubcodeFile(filePath);
    }


    public void cdd_process_subcode(int lba) {
        if (!ok) return;
        /* update subcode buffer pointer address */
        /* 16-bit register index */
        int index = bumpSubcodeAddress(ctx);
        if (lba < 0) {
            LogHelper.logWarnOnce(LOG, "Subcode lba < 0, ignoring: {}", lba);
            Arrays.fill(subcodeData, (byte) 0);
            return;
        } else if (subFormat == CDG) {
            readCdgSectorSymbols(lba, subcodeData);
        } else {
            /* read interleaved subcode data from .sub file (12 x 8-bit of P subchannel first, then Q subchannel, etc) */
            int pos = fileBase + lba * SUBCODE_DUMP_BLOCK_LEN;
            try {
                subcodeFile.seek(pos);
                subcodeFile.read(subcodeData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (subFormat == SUBCODE || subFormat == CDG) {
            for (int i = 0; i < SUBCODE_DUMP_BLOCK_LEN; i += 2) {
                int hi = subcodeData[i] & RW_MASK;
                int lo = subcodeData[i + 1] & RW_MASK;
                int code = (hi << 8) | lo;
                /* subcode buffer is accessed as 16-bit words */
                writeSubcodeBufferWord(ctx, index, code);
                /* subcode buffer is limited to 64 x 16-bit words */
                index = (index + 2) & MCD_SUBCODE_ADDR_REG_DATA_MASK;
            }
        } else {
            /* convert back to raw subcode format (96 bytes with 8 x P-W subchannel bits per byte) */
            for (int i = 0; i < SUBCODE_DUMP_BLOCK_LEN; i += 2) {
                int code = 0;
                for (int j = 0; j < 8; j++) {
                    int bits = (subcodeData[(j * 12) + (i / 8)] >> (6 - (i & 6))) & 3;
                    code |= ((bits & 1) << (7 - j));
                    code |= ((bits >> 1) << (15 - j));
                }

                /* subcode buffer is accessed as 16-bit words */
                writeSubcodeBufferWord(ctx, index, code);

                /* subcode buffer is limited to 64 x 16-bit words */
                index = (index + 2) & MCD_SUBCODE_ADDR_REG_DATA_MASK;
            }
        }
    }


    private static int bumpSubcodeAddress(MegaCdMemoryContext memoryContext) {
        int res = readBuffer(memoryContext.commonGateRegsBuf, MCD_SUBCODE_ADDR.addr + 1, Size.BYTE);
        res = (res + SUBCODE_BLOCK_LEN) & MCD_SUBCODE_ADDR_REG_DATA_MASK;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_SUBCODE_ADDR.addr + 1, res, Size.BYTE);
        return res;
    }

    private static void writeSubcodeBufferWord(MegaCdMemoryContext memoryContext, int index, int val) {
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_SUBCODE_BUFFER_REG_OFFSET + index, val, Size.WORD);
    }

    private static int detectSubcodeDataStartSector(Path file) {
        final byte[] in;
        try {
            in = Files.readAllBytes(file);
        } catch (Exception e) {
            LOG.warn("Could not read {} for data-start detection, defaulting to 0", file);
            return 0;
        }
        int sectors = in.length / SUBCODE_DUMP_BLOCK_LEN;
        if (sectors < 2) {
            return 0;
        }
        int firstNz = -1;
        for (int s = 0; s < sectors && firstNz < 0; s++) {
            int off = s * SUBCODE_DUMP_BLOCK_LEN;
            for (int i = 0; i < SUBCODE_DUMP_BLOCK_LEN; i++) {
                if ((in[off + i] & RW_MASK) != 0) {
                    firstNz = s;
                    break;
                }
            }
        }
        if (firstNz < 0) {
            return 0; //all 0 -> no CDG
        }
        int best = firstNz;
        double bestV = -1;
        for (int s = Math.max(0, firstNz - 8); s <= firstNz + 16 && s + 64 <= sectors; s++) {
            double v = cdgValidity(in, s, 64);
            if (v > bestV) {
                bestV = v;
                best = s;
            }
        }
        return best;
    }

    private static double cdgValidity(byte[] in, int startSec, int winSec) {
        int from = startSec * SUBCODE_DUMP_BLOCK_LEN;
        byte[] out = new byte[winSec * SECTOR_SIZE];
        for (int base = 0; base + SECTOR_SIZE < out.length; base += SECTOR_SIZE) {
            for (int i = 0; i < SECTOR_SIZE; i++) {
                int phys = from + base + DEINTERLEAVE[i];
                if (phys < in.length) {
                    out[base + i] = (byte) (in[phys] & RW_MASK);
                }
            }
        }
        int cdg = 0, valid = 0;
        for (int i = 0; i + PACK_SIZE < out.length; i += PACK_SIZE) {
            int cmd = out[i] & RW_MASK;
            if (cmd == 9) {
                cdg++;
                if (isValidCdgInst(out[i + 1] & RW_MASK)) {
                    valid++;
                }
            }
        }
        return cdg > 0 ? 100.0 * valid / cdg : 0;
    }

    private static boolean isValidCdgInst(int i) {
        return i == 1 || i == 2 || i == 6 || i == 20 || i == 24 || i == 30 || i == 31 || i == 38;
    }

    private void readCdgSectorSymbols(int lba, byte[] out) {
        int windowStartSec = Math.max(0, lba - (CDG_INVERSE_WINDOW_SECTORS - 1));
        int winSectors = lba - windowStartSec + 1; // 1..W
        int pos = fileBase + windowStartSec * SUBCODE_DUMP_BLOCK_LEN;
        int wantBytes = winSectors * SECTOR_SIZE;
        int read;
        try {
            subcodeFile.seek(pos);
            read = subcodeFile.read(cdgWindow, 0, wantBytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (read < 0) read = 0;
        if (read < wantBytes) {
            Arrays.fill(cdgWindow, read, wantBytes, (byte) 0);
        }
        Arrays.fill(out, (byte) 0);
        int targetBaseAbs = lba * SECTOR_SIZE; //absolute subcode byte of out[0]
        for (int wsec = 0; wsec < winSectors; wsec++) {
            int secAbs = (windowStartSec + wsec) * SECTOR_SIZE;
            int winOff = wsec * SECTOR_SIZE;
            for (int i = 0; i < SECTOR_SIZE; i++) {
                int targetAbs = secAbs + DEINTERLEAVE[i];
                int rel = targetAbs - targetBaseAbs;
                if (rel >= 0 && rel < SECTOR_SIZE) {
                    out[rel] = (byte) (cdgWindow[winOff + i] & RW_MASK);
                }
            }
        }
    }



    /**
     * TEST/RESEARCH stuff
     */

    private static final int SECTOR_SIZE = 96;
    private static final int PACK_SIZE = 24;
    private static final int SECTOR_SPREAD = 2;
    private static final int RW_MASK = 0x3f;

    private static final int[] DEINTERLEAVE = computeDeinterleave();

    private static final int CDG_INVERSE_WINDOW_SECTORS = 4;
    private static final byte[] cdgWindow = new byte[CDG_INVERSE_WINDOW_SECTORS * SECTOR_SIZE];

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



