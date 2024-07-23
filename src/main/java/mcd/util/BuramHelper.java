package mcd.util;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.vdp.util.MemView;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static mcd.dict.MegaCdDict.MCD_SUB_BRAM_MEM_WINDOW_MASK;
import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.BufferUtil.writeBufferRaw;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class BuramHelper {

    private static final Logger LOG = LogHelper.getLogger(BuramHelper.class.getSimpleName());

    /**
     * 1fe0: 53 45 47 41 5f 43 44 5f 52 4f 4d 00 01 00 00 00   S E G A _ C D _ R O M . . . . .
     * 1ff0: 52 41 4d 5f 43 41 52 54 52 49 44 47 45 5f 5f 5f   R A M _ C A R T R I D G E _ _ _
     */
    public static final byte[] BRAM_FORMAT_TAIL = {
            0x53, 0x45, 0x47, 0x41, 0x5f, 0x43, 0x44, 0x5f, 0x52, 0x4f, 0x4d, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x52, 0x41, 0x4d, 0x5f, 0x43, 0x41, 0x52, 0x54, 0x52, 0x49, 0x44, 0x47, 0x45, 0x5f, 0x5f, 0x5f
    };

    public static void main(String[] args) {
        ByteBuffer bb = ByteBuffer.allocate(0x2000);
        check_format_bram(bb);
        check_format_bram(bb);

        StringBuilder sb = new StringBuilder();
        MemView.fillFormattedString(sb, bb.array(), 0, bb.capacity());
        System.out.println(sb);
    }

    public static void check_format_bram(ByteBuffer buffer) {
        byte[] data = new byte[BRAM_FORMAT_TAIL.length];
        buffer.get(buffer.capacity() - BRAM_FORMAT_TAIL.length, data);
        if (!Arrays.equals(BRAM_FORMAT_TAIL, data)) {
            LOG.info("Formatting internal BRAM");
            segacd_format_bram(buffer);
        }
    }

    /**
     * Only the odd byte in the 16 bit memory space maps to 8 bit bram
     * <p>
     * read 0xFE_0000,Byte -> open bus/0xFF ?
     * read 0xFE_0001,Byte -> bram[0]
     * read 0xFE_0000,Word -> bram[0] = 0xBB, 0x??BB
     */
    public static int readBackupRam(ByteBuffer buffer, int address, Size size) {
        return switch (size) {
            case BYTE -> {
                if ((address & 1) == 1) {
                    yield readBuffer(buffer, (address & MCD_SUB_BRAM_MEM_WINDOW_MASK) >> 1, Size.BYTE);
                }
                yield size.getMask();
            }
            case WORD -> {
                assert (address & 1) == 0;
                yield readBuffer(buffer, (address & MCD_SUB_BRAM_MEM_WINDOW_MASK) >> 1, Size.BYTE);
            }
            case LONG -> {
                assert false;
                yield size.getMask();
            }
        };
    }

    /**
     * Only the odd byte in the 16 bit memory space maps to 8 bit bram
     * <p>
     * write 0xFE_0000,Byte -> no effect
     * write 0xFE_0001,Byte -> bram[0]
     * write 0xFE_0000,Word, 0xAABB -> bram[0] = 0xBB
     */
    public static void writeBackupRam(ByteBuffer buffer, int address, int data, Size size) {
        switch (size) {
            case BYTE -> {
                if ((address & 1) == 1) {
                    writeBufferRaw(buffer, (address & MCD_SUB_BRAM_MEM_WINDOW_MASK) >> 1, data, Size.BYTE);
                }
            }
            case WORD -> {
                assert (address & 1) == 0;
                writeBufferRaw(buffer, (address & MCD_SUB_BRAM_MEM_WINDOW_MASK) >> 1, data, Size.BYTE);
            }
            case LONG -> {
                assert false;
            }
        }
    }


    /**
     * 1fc0: 5f 5f 5f 5f 5f 5f 5f 5f 5f 5f 5f 00 00 00 00 40   _ _ _ _ _ _ _ _ _ _ _ . . . . @
     * 1fd0: 00 7d 00 7d 00 7d 00 7d 00 00 00 00 00 00 00 00   . } . } . } . } . . . . . . . .
     * 1fe0: 53 45 47 41 5f 43 44 5f 52 4f 4d 00 01 00 00 00   S E G A _ C D _ R O M . . . . .
     * 1ff0: 52 41 4d 5f 43 41 52 54 52 49 44 47 45 5f 5f 5f   R A M _ C A R T R I D G E _ _ _
     */
    //from blastem
    public static void segacd_format_bram(ByteBuffer buffer) {
        int len = buffer.capacity();
        for (int i = 0; i < len; i++) {
            buffer.put(i, (byte) 0);
        }
        int free_blocks = (len / 64) - 3;
        int pos = len - 0x40;
        buffer.position(pos);
        buffer.put("___________".getBytes());
        buffer.position(buffer.position() + 4);
        buffer.put((byte) 0x40);
        for (int i = 0; i < 4; i++) {
            buffer.put((byte) (free_blocks >> 8));
            buffer.put((byte) free_blocks);
        }
        buffer.position(buffer.position() + 8);
        buffer.put(BRAM_FORMAT_TAIL);
    }
}
