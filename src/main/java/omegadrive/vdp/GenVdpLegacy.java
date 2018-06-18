package omegadrive.vdp;

//	info de quirks a implementar:
//	https://emudocs.org/Genesis/Graphics/genvdp.txt

import omegadrive.bus.BusProvider;
import omegadrive.util.RegionDetector;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Objects;

/**
 * GenVdp
 */
public class GenVdpLegacy implements VdpProvider {

    private static Logger LOG = LogManager.getLogger(GenVdpLegacy.class.getSimpleName());

    public static int ROWS = VDP_VIDEO_ROWS;
    public static int COLS = VDP_VIDEO_COLS;

    int[] vram = new int[VDP_VRAM_SIZE];
    int[] cram = new int[VDP_CRAM_SIZE];
    int[] vsram = new int[VDP_VSRAM_SIZE];

    enum VramMode {
        vramRead, cramRead, vsramRead, vramWrite, cramWrite, vsramWrite;
    }

    VramMode vramMode;

//	VSRAM
//	 The VDP has 40x10 bits of on-chip vertical scroll RAM. It is accessed as
//	 40 16-bit words through the data port. Each word has the following format:
//
//	 ------yyyyyyyyyy
//
//	 y = Vertical scroll factor (0-3FFh)
//
//	 When accessing VSRAM, only address bits 6 through 1 are valid.
//	 The high-order address bits are ignored. Since VSRAM is word-wide, address
//	 bit zero has no effect.
//
//	 Even though there are 40 words of VSRAM, the address register will wrap
//	 when it passes 7Fh. Writes to the addresses beyond 50h are ignored.

    int[] registers = new int[VDP_REGISTERS_SIZE];

    //	FIFO Buffer 4 levels
    int[] fifoCode = new int[4];
    int[] fifoAddress = new int[4];
    int[] fifoData = new int[4];

    int currentFIFOReadEntry;
    int currentFIFOWriteEntry;

    int nextFIFOReadEntry;
    int nextFIFOWriteEntry;

    boolean controlSecond;

    boolean addressSecondWrite = false;
    long firstWrite;

    int dataPort;
    int addressPort;

    //	Reg 0
    //	Left Column Blank
    boolean lcb;
    //	Enable HINT
    boolean ie1;
    //	HV Counter Latch
    boolean m3;
    //	Display Enable
    boolean de;

    //	REG 1
    //	Extended VRAM
    boolean evram;
    //	Enable Display
    boolean disp;
    //	Enable VINT
    boolean ie0;
    //	Enable DMA
    boolean m1;
    //	Enable V30 Mode
    boolean m2;
    //	Enable Mode 5	(si esta inactivo, es mode = 4, compatibilidad con SMS)
    boolean m5;

    //	REG 0xC
    boolean h40;

    //	REG 0xF
    int autoIncrementData;

    //	reg 0x13
    int dmaLengthCounterLo;

    //	reg 0x14
    int dmaLengthCounterHi;

    //	reg 0x15
    int dmaSourceAddressLow;

    //	reg 0x16
    int dmaSourceAddressMid;

    //	reg 0x17
    int dmaSourceAddressHi;
    int dmaMode;

    boolean vramFill = false;
    boolean memToVram = false;

    //	Status register:
//	15	14	13	12	11	10	9		8			7	6		5		4	3	2	1	0
//	0	0	1	1	0	1	EMPTY	FULL		VIP	SOVR	SCOL	ODD	VB	HB	DMA	PAL

    //	EMPTY and FULL indicate the status of the FIFO.
//	When EMPTY is set, the FIFO is empty.
//	When FULL is set, the FIFO is full.
//	If the FIFO has items but is not full, both EMPTY and FULL will be clear.
//	The FIFO can hold 4 16-bit words for the VDP to process. If the M68K attempts to write another word once the FIFO has become full, it will be frozen until the first word can be delivered.
    int empty = 1;
    int full = 0;

    //	VIP indicates that a vertical interrupt has occurred, approximately at line $E0. It seems to be cleared at the end of the frame.
    int vip;

    //	SOVR is set when there are too many sprites on the current scanline. The 17th sprite in 32 cell mode and the 21st sprite on one scanline in 40 cell mode will cause this.
    int sovr;
    //	SCOL is set when any sprites have non-transparent pixels overlapping. This is cleared when the Control Port is read.
    int scol;

    //	ODD is set if the VDP is currently showing an odd-numbered frame while Interlaced Mode is enabled.
    int odd;
    //	VB returns the real-time status of the V-Blank signal. It is presumably set on line $E0 and unset at $FF.
    int vb;
    //	HB returns the real-time status of the H-Blank signal.
    int hb;
    //	DMA is set for the duration of a DMA operation. This is only useful for fills and copies, since the M68K is frozen during M68K to VRAM transfers.
    int dma;
    //	PAL seems to be set when the system's display is PAL, and possibly reflects the state of having 240 line display enabled.
// The same information can be obtained from the version register.
    int pal;

    long all;

    int line;

    private BusProvider bus;
    private VdpColorMapper colorMapper;
    private VdpInterruptHandler interruptHandler;
    private VideoMode videoMode;


    public GenVdpLegacy(BusProvider bus) {
        this.bus = bus;
        this.colorMapper = new VdpColorMapper();
        this.interruptHandler = new VdpInterruptHandler();
    }


    private VideoMode getVideoMode(RegionDetector.Region region, boolean isH40, boolean isV30) {
        return VideoMode.getVideoMode(region, isH40, isV30, videoMode);
    }

    @Override
    public int readControl() {
//  The value assigned to these bits will be whatever value these bits were set to from the last read the M68000 performed.
// Writes from the M68000 don't affect these bits, only reads.
        int control = (
                (empty << 9)
                        | (full << 8)
                        | (vip << 7)
                        | (sovr << 6)
                        | (scol << 5)
                        | (odd << 4)
                        | (vb << 3)
                        | (hb << 2)
                        | (dma << 1)
                        | (pal << 0)
        );

        return control;
    }

    @Override
    public int getVCounter() {
        return interruptHandler.getVCounterExternal();
    }

    @Override
    public int getHCounter() {
        return interruptHandler.getHCounterExternal();
    }

    @Override
    public boolean isIe0() {
        return ie0;
    }

    @Override
    public boolean isIe1() {
        return ie1;
    }

    @Override
    public int getVip() {
        return vip;
    }

    @Override
    public void setVip(int value) {
        this.vip = value;
    }

    private boolean isH40() {
        return h40;
    }

    @Override
    public VideoMode getVideoMode() {
        return videoMode;
    }

    private boolean isV30() {
        return m2;
    }

    private int maxSpritesPerFrame(boolean isH40) {
        return isH40 ? MAX_SPRITES_PER_FRAME_H40 : MAX_SPRITES_PER_FRAME_H32;
    }

    private int maxSpritesPerLine(boolean isH40) {
        return isH40 ? MAX_SPRITES_PER_LINE_H40 : MAX_SPRITES_PER_LiNE_H32;
    }

    private int getVerticalLines(boolean isV30) {
        return isV30 ? VERTICAL_LINES_V30 : VERTICAL_LINES_V28;
    }

    @Override
    public void init() {
        empty = 1;
        vb = 1;

        for (int i = 0; i < cram.length; i++) {
            if (i % 2 == 0) {
                cram[i] = 0x0E;
            } else {
                cram[i] = 0xEE;
            }
        }
        for (int i = 0; i < vsram.length; i++) {
            if (i % 2 == 0) {
                vsram[i] = 0x07;
            } else {
                vsram[i] = 0xFF;
            }
        }
        for (int i = 0; i < vram.length; i++) {
            if (i % 2 == 0) {
                vram[i] = 0x00;
            } else {
                vram[i] = 0x00;
            }
        }
        this.videoMode = getVideoMode(bus.getEmulator().getRegion(), false, false);
        this.interruptHandler.setMode(videoMode);
        this.pal = videoMode.isPal() ? 1 : 0;
    }

    //	https://wiki.megadrive.org/index.php?title=VDP_Ports#Write_2_-_Setting_RAM_address
//	First word
//	Bit	15	14	13	12	11	10	9	8	7	6	5	4	3	2	1	0
//	Def	CD1-CD0	A13		-										   A0

//	Second word
//	Bit	15	14	13	12	11	10	9	8	7	6	5	4	3	2	1	0
//	Def	0	 0	 0	 0	 0	 0	0	0	CD5		- CD2	0	A15	 -A14

//	Access mode	CD5	CD4	CD3	CD2	CD1	CD0
//	VRAM Write	0	0	0	0	0	1
//	CRAM Write	0	0	0	0	1	1
//	VSRAM Write	0	0	0	1	0	1
//	VRAM Read	0	0	0	0	0	0
//	CRAM Read	0	0	1	0	0	0
//	VSRAM Read	0	0	0	1	0	0

    //	DMA Mode		CD5	CD4
//	Memory to VRAM	1	0
//	VRAM Fill		1	0
//	VRAM Copy		1	1
    @Override
    public void writeControlPort(long data) {
        long mode = (data >> 13);

        if (!addressSecondWrite && mode == 0b100) {        //	Write 1 - Setting Register
            writeRegister(data);

        } else { // Write 2 - Setting RAM address
            writeRamAddress(data);
        }
    }

    private void writeRamAddress(long data) {
        if (!addressSecondWrite) {
            LOG.debug("first");
            firstWrite = data;
            addressSecondWrite = true;

        } else {
            addressSecondWrite = false;

            long first = firstWrite;
            long second = data;
            all = (first << 16) | second;

            int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
            int addr = (int) ((first & 0x3FFF) | ((second & 0x3) << 14));

            LOG.debug("second code " + Integer.toHexString(code));

            addressPort = addr;
            autoIncrementTotal = 0;    // reset este acumulador

            //	reset de los flags	TODO confirmar que van aca
            cramWrite2 = false;
            vramWrite2 = false;
            vsramWrite2 = false;

            int addressMode = code & 0xF;    // solo el primer byte, el bit 4 y 5 son para DMA
            // que ya fue contemplado arriba
            if (addressMode == 0b0000) { // VRAM Read
                vramMode = VramMode.vramRead;

            } else if (addressMode == 0b0001) { // VRAM Write
                vramMode = VramMode.vramWrite;

            } else if (addressMode == 0b1000) { // CRAM Read
                vramMode = VramMode.cramRead;

            } else if (addressMode == 0b0011) { // CRAM Write
                vramMode = VramMode.cramWrite;

            } else if (addressMode == 0b0100) { // VSRAM Read
                vramMode = VramMode.vsramWrite;

            } else if (addressMode == 0b0101) { // VSRAM Write
                vramMode = VramMode.vsramWrite;
            }

            LOG.debug("Video mode: " + Objects.toString(vramMode));

            //	https://wiki.megadrive.org/index.php?title=VDP_DMA
            if ((code & 0b100000) > 0) { // DMA
                int dmaBits = code >> 4;
                dmaRecien = true;

                if ((dmaBits & 0b10) > 0) {        //	VRAM Fill
                    if ((registers[0x17] & 0x80) == 0x80) {
//						FILL mode fills with same data from free even VRAM address.
//						FILL for only VRAM.
                        dmaModo = DmaMode.VRAM_FILL;
                        vramFill = true;

                    } else {
                        dmaModo = DmaMode.MEM_TO_VRAM;
                        memToVram = true;

                        if (m1) {
                            dmaMem2Vram(all);
                        } else {
                            LOG.warn("DMA but no m1 set !!");
                        }
                    }

                } else if ((dmaBits & 0b11) > 0) {        //	VRAM Copy
                    dmaModo = DmaMode.VRAM_COPY;
                    throw new RuntimeException();
                }
            }
        }
    }

    private void writeRegister(long data) {
        int dataControl = (int) (data & 0x00FF);
        int reg = (int) ((data >> 8) & 0x1F);

        if (reg >= VDP_REGISTERS_SIZE) {
            LOG.warn("Ignoring write to invalid VPD register: " + reg);
            return;
        }

//		LOG.debug("REG: " + VdpProvider.pad(reg) + " - data: " + VdpProvider.pad(dataControl));

        cramWrite2 = false;
        vramWrite2 = false;
        vsramWrite2 = false;

        registers[reg] = dataControl;
        updateVariables(reg, data);
    }

    private void updateVariables(int reg, long data) {

        if (reg == 0x00) {
            lcb = ((data >> 5) & 1) == 1;
            ie1 = ((data >> 4) & 1) == 1;
            m3 = ((data >> 1) & 1) == 1;
            de = ((data >> 0) & 1) == 1;

        } else if (reg == 0x01) {
            //TODO check this - not needed?
            if ((disp) && ((data & 0x40) == 0)) {    // el display estaba prendido pero se apago
                vb = 1;
            } else if ((!disp) && ((data & 0x40) == 0x40)) {    // el display se prende
                vb = 0;
            }
            //TODO check this - not needed?

            evram = ((data >> 7) & 1) == 1;
            disp = ((data >> 6) & 1) == 1;
            ie0 = ((data >> 5) & 1) == 1;
            m1 = ((data >> 4) & 1) == 1;
            m2 = ((data >> 3) & 1) == 1;
            m5 = ((data >> 2) & 1) == 1;
        } else if (reg == 0x0C) {
            boolean rs0 = Util.bitSetTest(data, 7);
            boolean rs1 = Util.bitSetTest(data, 0);
            h40 = rs0 && rs1;
        } else if (reg == 0x0F) {
            autoIncrementData = (int) (data & 0xFF);
        } else if (reg == 0x13) {
            dmaLengthCounterLo = (int) (data & 0xFF);

        } else if (reg == 0x14) {
            dmaLengthCounterHi = (int) (data & 0xFF);

        } else if (reg == 0x15) {
            dmaSourceAddressLow = (int) (data & 0xFF);

        } else if (reg == 0x16) {
            dmaSourceAddressMid = (int) (data & 0xFF);

        } else if (reg == 0x17) {
            dmaSourceAddressHi = (int) (data & 0x3F);
            dmaMode = (int) ((data >> 6) & 0x3);
        }
    }

    boolean dmaRecien = false;

    @Override
    public void dmaFill() {
        if (dma == 1) {
            int dmaLength = (dmaLengthCounterHi << 8) | dmaLengthCounterLo;

            int index;
            long data;
//			if (dmaRecien) {
//				System.out.println("DMA MODE: " + dmaModeEnum);
//				
//				currentFIFOReadEntry = nextFIFOReadEntry;
//				currentFIFOWriteEntry = nextFIFOWriteEntry;
//				index = currentFIFOReadEntry;
//				data = dataPort;
//				destAddr = addressPort;
//				
////				int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
//				
//				fifoData[index] = data;
////				fifoCode[index] = code;		// TODO implementar el code como esta en los otros writes de memory
//			} else {
//				index = currentFIFOReadEntry;
//				data = fifoData[index];
//				destAddr = fifoAddress[index];
//			}

//			System.out.println(pad4(dmaLength));

            long first = all >> 16;
            long second = all & 0xFFFF;

            int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
            int addr = (int) ((first & 0x3FFF) | ((second & 0x3) << 13));

            int destAddr = (int) (((all & 0x3) << 14) | ((all & 0x3FFF_0000L) >> 16));

            if (destAddr % 2 == 1) {
                LOG.warn("Address not even! " + destAddr);
            }

            destAddr += autoIncrementTotal;

            int data1 = dataPort & 0xFF;

//			int data1 = (int) bus.read(sourceTrue);
//			int data2 = (int) bus.read(sourceTrue + 1);

            destAddr = destAddr & 0xFFFF;    //	16 Zhang Majhong hace DMA length 0xFFFF que es el doble del limite (hace el doble de operaciones)

            if (vramMode == VramMode.vramWrite) {
                writeVramByte(destAddr, data1);
            } else {
                LOG.warn("Unxepected write to RAM: " + Objects.toString(vramMode));
//				throw new RuntimeException("SOLO ESCRIBE EN VRAM !! pasa este caso ?");
                return;
            }

            dmaLength = (dmaLength - 1);    // idem FIXME no es fijo
            if (dmaLength <= 0) {
                dma = 0;
                return;
            }

            autoIncrementTotal += registers[0xF];

            dmaLength = dmaLength & 0xFFFF;
            dmaLengthCounterHi = dmaLength >> 8;
            dmaLengthCounterLo = dmaLength & 0xFF;

            registers[0x14] = dmaLength >> 8;
            registers[0x13] = dmaLength & 0xFF;

            //	FIXME, no es fijo, se actualiza en paralelo mientras se siguen ejecutando instrucciones, hay q contar ciclos de cpu
//			addressPort += 2;
//			fifoAddress[index] = destAddr + 2;
//			
//			if (dmaRecien) {
//				dmaRecien = false;
//				
//				index = (index + 1) % 4;
//				nextFIFOReadEntry = index;
//				nextFIFOWriteEntry = index;
//			}
        }
    }

    boolean vramWrite2 = false;
    boolean cramWrite2 = false;
    boolean vsramWrite2 = false;

    int vramWriteData;
    int cramWriteData;
    int vsramWriteData;
    int firstData;

    DmaMode dmaModo;

    enum DmaMode {
        MEM_TO_VRAM, VRAM_FILL, VRAM_COPY;
    }

    boolean dmaRequested;

    @Override
    public void writeDataPort(int data, Size size) {
        this.dataPort = data;

        if (size == Size.BYTE) {
            if (vramFill) {
                if (vramMode == VramMode.vramWrite) {
                    vramWriteByte(data);
                } else {
                    LOG.warn("que hace ? otros modos ?");
                }

                autoIncrementTotal = 1;

                if (m1) {
                    dma = 1;
                    vramFill = false;

                    dataPort = (data << 8) | data;

                    return;
                } else {
                    LOG.warn("M1 should be 1 in the DMA transfer. otherwise we can't guarantee the operation.");
                }

            } else if (vramMode == VramMode.vramWrite) {
                vramWriteByte(data);
            } else if (vramMode == VramMode.cramWrite || vramMode == VramMode.vsramWrite) {
                LOG.error(vramMode + ", " + size + " wide write not supported: " + data);
            } else {
                LOG.warn("Unexpected write, data : " + data + ",vramMode: " + Objects.toString(vramMode));
            }

        } else if (size == Size.WORD) {
            if (vramFill) {
//Performing a DMA fill does perform a normal VRAM write.
//After the VRAM write has been processed however, a DMA fill operation is triggered immediately after.
//Normal VRAM writes are always 16-bit, so the first write that is carried out when you try and
//start a DMA fill will always be 16-bit. The DMA fill operation that follows will perform 8-bit writes.
                if (vramMode == VramMode.vramWrite) {
                    vramWriteWord(data);
                } else {
                    LOG.warn("Unexpected write during vramFill, data : " + data + ", vramMode: " + Objects.toString(vramMode));
                }
                autoIncrementTotal = 1;

                if (m1) {
                    dma = 1;
                    vramFill = false;

                    dataPort = data;

                    return;
                } else {
                    LOG.warn("M1 should be 1 in the DMA transfer. otherwise we can't guarantee the operation.");
                }

            } else if (vramMode == VramMode.vramWrite) {
                vramWriteWord(data);

            } else if (vramMode == VramMode.cramWrite) {
                cramWriteWord(data);

            } else if (vramMode == VramMode.vsramWrite) {
                vsramWriteWord(data);

            } else {
                LOG.warn("Unexpected write, data: " + data + ", vramMode: " + Objects.toString(vramMode));
            }

        } else {    //	LONG
            if (vramFill) {
                if (m1) {
                    dma = 1;
                    vramFill = false;

                    dataPort = data;

                    return;
                } else {
                    LOG.warn("M1 should be 1 in the DMA transfer. otherwise we can't guarantee the operation.");
                }

            } else if (vramMode == VramMode.vramWrite) {
                vramWriteWord(data >> 16);
                vramWriteWord(data & 0xFFFF);

            } else if (vramMode == VramMode.cramWrite) {
                cramWriteWord(data >> 16);
                cramWriteWord(data & 0xFFFF);

            } else if (vramMode == VramMode.vsramWrite) {
                vsramWriteWord(data >> 16);
                vsramWriteWord(data & 0xFFFF);

            } else {
                LOG.warn("Unexpected write, data: " + data + ", vramMode: " + Objects.toString(vramMode));
            }
        }

    }

//	 Registers 19, 20, specify how many 16-bit words to transfer:
//
//	 #19: L07 L06 L05 L04 L03 L02 L01 L00
//	 #20: L15 L14 L13 L12 L11 L10 L08 L08
//
//	 Note that a length of 7FFFh equals FFFFh bytes transferred, and a length
//	 of FFFFh = 1FFFF bytes transferred.
//
//	 Registers 21, 22, 23 specify the source address on the 68000 side:
//
//	 #21: S08 S07 S06 S05 S04 S03 S02 S01
//	 #22: S16 S15 S14 S13 S12 S11 S10 S09
//	 #23:  0  S23 S22 S21 S20 S19 S18 S17
//
//	 If the source address goes past FFFFFFh, it wraps to FF0000h.
//	 (Actually, it probably wraps at E00000h, but there's no way to tell as
//	  the two addresses are functionally equivelant)
//
//	 When doing a transfer to CRAM, the operation is aborted once the address
//	 register is larger than 7Fh. The only known game that requires this is
//	 Batman & Robin, which will have palette corruption in levels 1 and 3
//	 otherwise. This rule may possibly apply to VSRAM transfers as well.

    //	 The following events occur after the command word is written:
//
//		 - 68000 is frozen.
//		 - VDP reads a word from source address.
//		 - Source address is incremented by 2.
//		 - VDP writes word to VRAM, CRAM, or VSRAM.
//		   (For VRAM, the data is byteswapped if the address register has bit 0 set)
//		 - Address register is incremented by the value in register #15.
//		 - Repeat until length counter has expired.
//		 - 68000 resumes operation.
    private void dmaMem2Vram(long commandWord) {
        int dmaLength = (dmaLengthCounterHi << 8) | dmaLengthCounterLo;

        long sourceAddr = ((registers[0x17] & 0x7F) << 16) | (registers[0x16] << 8) | (registers[0x15]);
        long sourceTrue = sourceAddr << 1;    // duplica, trabaja asi
        int destAddr = (int) (((commandWord & 0x3) << 14) | ((commandWord & 0x3FFF_0000L) >> 16));

        int index, data;
        while (dmaLength > 0) {
//			currentFIFOReadEntry = nextFIFOReadEntry;
//			currentFIFOWriteEntry = nextFIFOWriteEntry;
//			index = currentFIFOReadEntry;
//			data = dataPort;
//			destAddr = addressPort;
//			
//			fifoData[index] = data;

            //TODO el pipe

//			int data1 = (int) bus.read(sourceTrue);
//			int data2 = (int) bus.read(sourceTrue + 1);

            int dataWord = (int) bus.read(sourceTrue, Size.WORD);
            int data1 = dataWord >> 8;
            int data2 = dataWord & 0xFF;

            if (destAddr % 2 == 1) {
                LOG.warn("Should be even! " + destAddr);
            }
            if (destAddr > 0xFFFF) {
                return;
            }
            if (vramMode == VramMode.vramWrite) {
                writeVramByte(destAddr, data1);
                writeVramByte(destAddr + 1, data2);

            } else if (vramMode == VramMode.cramWrite) {
                writeCramByte(destAddr, data1);
                writeCramByte(destAddr + 1, data2);

            } else if (vramMode == VramMode.vsramWrite) {
                destAddr &= VDP_VSRAM_SIZE - 1; //fixes Arrow Flash
                vsram[destAddr] = data1;
                vsram[destAddr + 1] = data2;

            } else {
                throw new RuntimeException("not");
            }

            sourceTrue += 2;
            destAddr += registers[15];

            dmaLength--;
        }

        int newSource = (int) (sourceTrue >> 1);
        registers[0x17] = ((registers[0x17] & 0x80) | ((newSource >> 16) & 0x7F));
        registers[0x16] = (newSource >> 8) & 0xFF;
        registers[0x15] = newSource & 0xFF;

        dmaLengthCounterHi = 0;
        dmaLengthCounterLo = 0;
        registers[0x14] = 0;
        registers[0x13] = 0;

//				int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));

//				fifoCode[index] = code;		// TODO implementar el code como esta en los otros writes de memory


//		dmaLength = dmaLength & 0xFFFF;
//		dmaLengthCounterHi = dmaLength >> 8;
//		dmaLengthCounterLo = dmaLength & 0xFF;
//			
//		addressPort += 2;
//		fifoAddress[index] = destAddr + 2;	//	FIXME, no es fijo, se actualiza en paralelo mientras se siguen ejecutando instrucciones, hay q contar ciclos de cpu
//		
//		if (dmaRecien) {
//			dmaRecien = false;
//			
//			index = (index + 1) % 4;
//			nextFIFOReadEntry = index;
//			nextFIFOWriteEntry = index;
//		}
    }

    //    The address register wraps past address 7Fh.
    private void writeCramByte(int address, int data) {
        address &= (VDP_CRAM_SIZE - 1);
        cram[address] = data;
//		System.out.println(Integer.toHexString(address) + ": " + Integer.toHexString(data));
    }

    private void writeVramByte(int address, int data) {
        vram[address] = data;
    }

    int autoIncrementTotal;

    private void vramWriteWord(int data) {
        int word = data;

        int index = nextFIFOReadEntry;
        int address = addressPort;

        long first = all >> 16;
        long second = all & 0xFFFF;

        int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
        int addr = (int) ((first & 0x3FFF) | ((second & 0x3) << 14));

        int offset = addr + autoIncrementTotal;

        int data1 = (word >> 8) & 0xFF;
        int data2 = word & 0xFF;

        //	hack por si se pasa
        if (offset > 0xFFFE) {
            return;
        }

        writeVramByte(offset, data1);
        writeVramByte(offset + 1, data2);

//		System.out.println("addr: " + Integer.toHexString(offset) + " - data: " + Integer.toHexString(data1));
//		System.out.println("addr: " + Integer.toHexString(offset + 1) + " - data: " + Integer.toHexString(data2));

        fifoAddress[index] = offset;
        fifoCode[index] = code;
        fifoData[index] = word;

        int incrementOffset = autoIncrementTotal + autoIncrementData;

        address = address + incrementOffset;    // FIXME wrap
        offset = offset + incrementOffset;
        index = (index + 1) % 4;

        nextFIFOReadEntry = index;
        nextFIFOWriteEntry = index;
        autoIncrementTotal = incrementOffset;
    }

    private void vramWriteByte(int data) {
        int index = nextFIFOReadEntry;
        int address = addressPort;

        long first = all >> 16;
        long second = all & 0xFFFF;

        int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
        int addr = (int) ((first & 0x3FFF) | ((second & 0x3) << 14));

        int offset = addr + autoIncrementTotal;

        data = data & 0xFF;

        //	hack por si se pasa
        if (offset > 0xFFFF) {
            return;
        }

        writeVramByte(offset, data);

//		System.out.println("addr: " + Integer.toHexString(offset) + " - data: " + Integer.toHexString(data1));
//		System.out.println("addr: " + Integer.toHexString(offset + 1) + " - data: " + Integer.toHexString(data2));

        fifoAddress[index] = offset;
        fifoCode[index] = code;
        fifoData[index] = data;

        int incrementOffset = autoIncrementTotal + autoIncrementData;

        address = address + incrementOffset;    // FIXME wrap
        offset = offset + incrementOffset;
        index = (index + 1) % 4;

        nextFIFOReadEntry = index;
        nextFIFOWriteEntry = index;
        autoIncrementTotal = incrementOffset;
    }

    //	https://emu-docs.org/Genesis/sega2f.htm
//The CRAM contains 128 bytes, addresses 0 to 7FH.
// For word wide writes to the CRAM, use:
    // D15 ~ D0 are valid when we use word for data set. If the writes are byte
    // wide, write the high byte to $C00000 and the low byte to $C00001. A long
    // word wide access is equivalent to two sequential word wide accesses.
    // Place the first data in D31 - D16 and the second data in D15 - D0. The
    // data may be written sequentially; the address is incremented by the value
    // of REGISTER #15 after every write, independent of whether the width is
    // byte of word.
    //Note that A0 is used in the increment but not in address decoding,
    // resulting in some interesting side-effects if writes are attempted at odd addresses.
    private void cramWriteWord(int data) {
//		if (!cramWrite2) {
//			cramWriteData = data;
//			cramWrite2 = true;
//		} else {
//			cramWrite2 = false;
//			int word = (cramWriteData << 16) | data;

        int word = data;

        int index = nextFIFOReadEntry;
        int address = addressPort;

        long first = all >> 16;
        long second = all & 0xFFFF;

        int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
        int addr = (int) ((first & 0x3FFF) | ((second & 0x3) << 13));

        int offset = address + autoIncrementTotal;

        int data1 = (word >> 8) & 0xFF;
        int data2 = word & 0xFF;

        writeCramByte(offset, data1);
        writeCramByte(offset + 1, data2);

        fifoAddress[index] = offset;
        fifoCode[index] = code;
        fifoData[index] = (data1 << 8) | data2;

        int incrementOffset = autoIncrementTotal + autoIncrementData;

        address = address + incrementOffset;    // FIXME wrap
        index = (index + 1) % 4;
        fifoAddress[index] = address;
        fifoCode[index] = code;

        nextFIFOReadEntry = (index + 1) % 4;
        nextFIFOWriteEntry = (index + 1) % 4;
        autoIncrementTotal = incrementOffset;
//		}
    }

    private void vsramWriteWord(int data) {
        int word = data;

        int index = nextFIFOReadEntry;
        int address = addressPort;

        address = address & 0xFF;    //	no decodifica todo, arregla scroll vertical en 16 zhang mahjong intro

        long first = all >> 16;
        long second = all & 0xFFFF;

        int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
        int addr = (int) ((first & 0x3FFF) | ((second & 0x3) << 13));

        int offset = address + autoIncrementTotal;

        int data1 = (word >> 8) & 0xFF;
        int data2 = word & 0xFF;

        if (offset < 0x50) {
            vsram[offset] = data1;
        }
        if (offset < 0x50) {
            vsram[offset + 1] = data2;
        }

        fifoAddress[index] = offset;
        fifoCode[index] = code;
        fifoData[index] = (data1 << 8) | data2;

//		int data3 = (word >> 8) & 0xFF;
//		int data4 = (word >> 0) & 0xFF;
//		
//		vsram[offset + 2] = data3;
//		vsram[offset + 3] = data4;

        int incrementOffset = autoIncrementTotal + autoIncrementData;

//		address = address + incrementOffset;	// FIXME wrap
//		index = (index + 1) % 4;
//		fifoAddress[index] = address;
//		fifoCode[index] = code;
//		fifoData[index] = (data3 << 8) | data4;

        nextFIFOReadEntry = (index + 1) % 4;
        nextFIFOWriteEntry = (index + 1) % 4;
        autoIncrementTotal = incrementOffset;
    }

    int totalCycles = 0;
    int scanline = 0;

    public int[][] screenData = new int[COLS][ROWS];

    public int[][] planeA = new int[COLS][ROWS];
    public int[][] planeB = new int[COLS][ROWS];
    public int[][] planeBack = new int[COLS][ROWS];

    public boolean[][] planePrioA = new boolean[COLS][ROWS];
    public boolean[][] planePrioB = new boolean[COLS][ROWS];

    public int[][] planeIndexColorA = new int[COLS][ROWS];
    public int[][] planeIndexColorB = new int[COLS][ROWS];

    public int[][] sprites = new int[COLS][ROWS];
    public int[][] spritesIndex = new int[COLS][ROWS];
    public boolean[][] spritesPrio = new boolean[COLS][ROWS];

    public int[][] window = new int[COLS][ROWS];
    public int[][] windowIndex = new int[COLS][ROWS];
    public boolean[][] windowPrio = new boolean[COLS][ROWS];

    private void runNew() {
        boolean displayEnable = isDisplayEnable();
        int hCounter = interruptHandler.increaseHCounter();
        boolean hBlankSet = interruptHandler.ishBlankSet();
        boolean hBlankToggle = (hBlankSet && hb == 0) || (!hBlankSet && hb == 1);

        if (hBlankToggle) {
            hb = hBlankSet ? 1 : 0;
        }
        //draw on the last counter (use 9bit internal counter value)
        if (hCounter == interruptHandler.COUNTER_LIMIT) {
            //draw the line
            drawScanline(displayEnable);
        }

        int vCounter = interruptHandler.getvCounter();
        boolean vBlankSet = interruptHandler.isvBlankSet();
        boolean vBlankToggle = (vBlankSet && vb == 0) || (!vBlankSet && vb == 1);
        if (vBlankToggle) {
//            LOG.info("VBlankToggle: hC " + hCounter + ", vC " + vCounter + ", vblank: " + vBlankSet);
            vb = vBlankSet ? 1 : 0;
            vip = vb;
        }
        //draw on the last counter (use 9bit internal counter value)
        if (vCounter == VdpInterruptHandler.COUNTER_LIMIT) {
            spritesFrame = 0;
            line = 0;
            //TODO check this
            if (displayEnable && vBlankToggle) {
                line = 0;
                evaluateSprites();
                compaginateImage();
                bus.getEmulator().renderScreen(screenData);
                resetMode();
                resetHLinesCounter();
            }
        }
    }

    private void resetHLinesCounter() {
        bus.setHLinesPassed(registers[0xA]); //set the next HINT line
    }

    private void resetMode() {
        boolean isH40 = isH40();
        boolean isV30 = isV30();
        VideoMode newVideoMode = getVideoMode(videoMode.getRegion(), isH40, isV30);
        if (videoMode != newVideoMode) {
            this.videoMode = newVideoMode;
            LOG.info("Video mode changed: " + videoMode + ", " + videoMode.getDimension());
            interruptHandler.setMode(videoMode);
            pal = videoMode.isPal() ? 1 : 0;
        }
    }

    private void drawScanline(boolean displayEnable) {
        //draw line
        int lineLimit = videoMode.getDimension().height;
        if (displayEnable) {
            if (line < lineLimit) {
                spritesLine = 0;

                renderBack();
                renderPlaneA();
                renderPlaneB();
                renderWindow();
                renderSprites();
            }
        }
//        The counter is loaded with the contents of register #10 in the following
//        situations:
//
//        - Line zero of the frame.
//        - When the counter has expired.
//        - Lines 225 through 261. (note that line 224 is not included)
        if (line < lineLimit) {
            bus.setHLinesPassed(bus.getHLinesPassed() - 1);
            if (bus.getHLinesPassed() == -1) {
                bus.setHIntPending(true);
                resetHLinesCounter();
            }
        }
        if (line == 0) {
            resetHLinesCounter();
        }
        line++;
    }

    public boolean isDisplayEnable() {
        return disp;
    }

    @Override
    public void run(int cycles) {
        runNew();
        //runOld(cycles);
    }

    private int LINE_LIMIT = VERTICAL_LINES_V30; //0xE0;

    private int CYCLE_LIMIT1 =
            Integer.valueOf(System.getProperty("vdp.limit1", "100"));
    private int CYCLE_LIMIT2 =
            Integer.valueOf(System.getProperty("vdp.limit2", "123"));

    private void runOld(int cycles) {
        boolean displayEnable = isDisplayEnable();
        totalCycles += cycles;
        if (totalCycles < CYCLE_LIMIT1) {
            hb = 0;
        } else if (totalCycles >= CYCLE_LIMIT1 && totalCycles <= CYCLE_LIMIT2) {
            hb = 1;
        } else if (totalCycles > CYCLE_LIMIT2) {
            if (displayEnable) {
                if (line < LINE_LIMIT) {
                    spritesLine = 0;

                    renderBack();
                    renderPlaneA();
                    renderPlaneB();
                    renderWindow();
                    renderSprites();
                }
            }

            if (line < LINE_LIMIT) {
                bus.setHLinesPassed(bus.getHLinesPassed() - 1);
                if (bus.getHLinesPassed() == -1) {
                    bus.setHIntPending(true);
                    bus.setHLinesPassed(registers[0xA]);
                }
            }

            line++;
            totalCycles = 0;
        }
        if (line > ROWS) {
            line = 0;
            evaluateSprites();

            bus.setHLinesPassed(registers[0xA]);
        }
        if (line == LINE_LIMIT && totalCycles == 0) {
            vip = 1;
            vb = 1;

            spritesFrame = 0;

            if (displayEnable) {
                compaginateImage();
                bus.getEmulator().renderScreen(screenData);
                LINE_LIMIT = getVerticalLines(isV30());
            }
            //	solo en 0 si el display esta prendido (apagado siempre esta en 1)
        } else if (line < LINE_LIMIT && displayEnable) {
            vb = 0;
        }

    }

    int spritesFrame = 0;
    int spritesLine = 0;


    static int INDEXES_NUM = ROWS;
    int[] lastIndexes = new int[INDEXES_NUM];
    int[][] spritesPerLine = new int[INDEXES_NUM][MAX_SPRITES_PER_FRAME_H40];

    private void evaluateSprites() {
        //	AT16 is only valid if 128 KB mode is enabled,
        // and allows for rebasing the Sprite Attribute Table to the second 64 KB of VRAM.
        int spriteTableLoc = registers[0x5] & 0x7F;
        int spriteTable = spriteTableLoc * 0x200;

        //reset
        int currSprite = 0;
        for (int i = 0; i < ROWS; i++) {
            lastIndexes[i] = 0;
            Arrays.fill(spritesPerLine[i], -1);
        }

        boolean isH40 = isH40();
        int maxSprites = maxSpritesPerFrame(isH40);

        for (int i = 0; i < maxSprites; i++) {
            long baseAddress = spriteTable + (i * 8);

            int byte0 = vram[(int) (baseAddress)];
            int byte1 = vram[(int) (baseAddress + 1)];
            int byte2 = vram[(int) (baseAddress + 2)];
            int byte3 = vram[(int) (baseAddress + 3)];
            int byte4 = vram[(int) (baseAddress + 4)];
            int byte5 = vram[(int) (baseAddress + 5)];
            int byte6 = vram[(int) (baseAddress + 6)];
            int byte7 = vram[(int) (baseAddress + 7)];

            int linkData = byte3 & 0x7F;

            int verticalPos = ((byte0 & 0x1) << 8) | byte1;
            int verSize = byte2 & 0x3;

            int verSizePixels = (verSize + 1) * 8;
            int realY = (int) (verticalPos - 128);
            for (int j = realY; j < realY + verSizePixels; j++) {
                if (j < 0 || j >= INDEXES_NUM) {
                    continue;
                }

                int last = lastIndexes[j];
                if (last < maxSprites) { //TODO why??
                    spritesPerLine[j][last] = i;
                    lastIndexes[j] = last + 1;
                }
            }

            if (linkData == 0) {
                return;
            }
        }
    }

    private void renderSprites() {
        int spriteTableLoc = registers[0x5] & 0x7F;    //	AT16 is only valid if 128 KB mode is enabled, and allows for rebasing the Sprite Attribute Table to the second 64 KB of VRAM.
        int spriteTable = spriteTableLoc * 0x200;

        long linkData = 0xFF;
        long verticalPos;

        int line = this.line;

        long baseAddress = spriteTable;
        int[] spritesInLine = spritesPerLine[line];
        int ind = 0;
        int currSprite = spritesInLine[0];

        int[] priors = new int[COLS];
        boolean isH40 = isH40();
        int maxSpritesPerLine = maxSpritesPerLine(isH40);
        int maxSpritesPerFrame = maxSpritesPerFrame(isH40);

        //Stop processing sprites, skip the remaining lines
//        if (spritesFrame > maxSpritesPerFrame) { //TODO breaks AyrtonSenna
//            return;
//        }

        while (currSprite != -1) {
            baseAddress = spriteTable + (currSprite * 8);

            int byte0 = vram[(int) (baseAddress)];
            int byte1 = vram[(int) (baseAddress + 1)];
            int byte2 = vram[(int) (baseAddress + 2)];
            int byte3 = vram[(int) (baseAddress + 3)];
            int byte4 = vram[(int) (baseAddress + 4)];
            int byte5 = vram[(int) (baseAddress + 5)];
            int byte6 = vram[(int) (baseAddress + 6)];
            int byte7 = vram[(int) (baseAddress + 7)];

            linkData = byte3 & 0x7F;
            verticalPos = ((byte0 & 0x1) << 8) | byte1;        //	bit 9 interlace mode only

//			if (linkData == 0) {
//				return;
//			}

            int horSize = (byte2 >> 2) & 0x3;
            int verSize = byte2 & 0x3;

            int horSizePixels = (horSize + 1) * 8;
            int verSizePixels = (verSize + 1) * 8;

            int nextSprite = (int) ((linkData * 8) + spriteTable);
            baseAddress = nextSprite;

            int realY = (int) (verticalPos - 128);

            spritesFrame++;
            spritesLine++;
            //Stop processing sprites, skip the remaining lines
            if (spritesLine > maxSpritesPerLine) {
// || spritesFrame > maxSpritesPerFrame) {  //TODO breaks AyrtonSenna
                return;
            }


            int pattern = ((byte4 & 0x7) << 8) | byte5;
            int palette = (byte4 >> 5) & 0x3;

            boolean priority = ((byte4 >> 7) & 0x1) == 1 ? true : false;
            boolean verFlip = ((byte4 >> 4) & 0x1) == 1 ? true : false;
            boolean horFlip = ((byte4 >> 3) & 0x1) == 1 ? true : false;

            int horizontalPos = ((byte6 & 0x1) << 8) | byte7;
            int horOffset = horizontalPos - 128;

            int spriteLine = (int) ((line - realY) % verSizePixels);

            int pointVert;
            if (verFlip) {
                pointVert = (spriteLine - (verSizePixels - 1)) * -1;
            } else {
                pointVert = spriteLine;
            }

            for (int cellHor = 0; cellHor < (horSize + 1); cellHor++) {
                //	16 bytes por cell de 8x8
                //	cada linea dentro de una cell de 8 pixeles, ocupa 4 bytes (o sea, la mitad del ancho en bytes)
                int currentVerticalCell = pointVert / 8;
                int vertLining = (currentVerticalCell * 32) + ((pointVert % 8) * 4);

                int cellH = cellHor;
                if (horFlip) {
                    cellH = (cellHor * -1) + horSize;
                }
                int horLining = vertLining + (cellH * ((verSize + 1) * 32));
                for (int i = 0; i < 4; i++) {
                    int sliver = i;
                    if (horFlip) {
                        sliver = (i * -1) + 3;
                    }

                    int grab = (pattern * 0x20) + (horLining) + sliver;
                    if (grab < 0) {
                        continue;    //	FIXME guardar en cache de sprites yPos y otros atrib
                    }
                    grab &= VDP_VRAM_SIZE - 1; //Troy Aikman
                    int data = vram[grab];

                    int pixel1, pixel2;
                    if (horFlip) {
                        pixel1 = data & 0x0F;
                        pixel2 = (data & 0xF0) >> 4;
                    } else {
                        pixel1 = (data & 0xF0) >> 4;
                        pixel2 = data & 0x0F;
                    }

                    int paletteLine = palette * 32;

                    int colorIndex1 = paletteLine + (pixel1 * 2);
                    int colorIndex2 = paletteLine + (pixel2 * 2);

                    int color1;
                    if (pixel1 == 0) {
                        if (horOffset >= 0 && horOffset < COLS) {
                            if (spritesIndex[horOffset][line] == 0) {    // solo pisa si la prioridad anterior era 0
                                spritesIndex[horOffset][line] = pixel1;
                                spritesPrio[horOffset][line] = priority;
                            }
                        }
                    } else {
                        if (horOffset >= 0 && horOffset < COLS) {
                            if (priors[horOffset] == 0 || (priors[horOffset] == 1 && priority)) {
                                if (priority) {
                                    priors[horOffset] = 1;
                                }

                                color1 = cram[colorIndex1] << 8 | cram[colorIndex1 + 1];

                                int r = (color1 >> 1) & 0x7;
                                int g = (color1 >> 5) & 0x7;
                                int b = (color1 >> 9) & 0x7;

                                int theColor1 = getColor(r, g, b);


                                sprites[horOffset][line] = theColor1;
                                spritesIndex[horOffset][line] = pixel1;
                                spritesPrio[horOffset][line] = priority;
                            }
                        }
                    }

                    int color2;
                    int horOffset2 = horOffset + 1;
                    if (pixel2 == 0) {
                        if (horOffset2 >= 0 && horOffset2 < COLS) {
                            if (spritesIndex[horOffset2][line] == 0) {    // solo pisa si la prioridad anterior era 0
                                spritesIndex[horOffset2][line] = pixel2;
                                spritesPrio[horOffset2][line] = priority;
                            }
                        }
                    } else {
                        if (horOffset2 >= 0 && horOffset2 < COLS) {
                            if (priors[horOffset2] == 0 || (priors[horOffset2] == 1 && priority)) {
                                if (priority) {
                                    priors[horOffset2] = 1;
                                }

                                color2 = cram[colorIndex2] << 8 | cram[colorIndex2 + 1];

                                int r2 = (color2 >> 1) & 0x7;
                                int g2 = (color2 >> 5) & 0x7;
                                int b2 = (color2 >> 9) & 0x7;

                                int theColor2 = getColor(r2, g2, b2);


                                sprites[horOffset2][line] = theColor2;
                                spritesIndex[horOffset2][line] = pixel2;
                                spritesPrio[horOffset2][line] = priority;
                            }
                        }
                    }

                    horOffset += 2;
                }
            }

            ind++;
            currSprite = spritesInLine[ind];
            if (currSprite > maxSpritesPerFrame) {
                return;
            }
        }
    }

    //The VDP has a complex system of priorities that can be used to achieve several complex effects.
    // The priority order goes like follows, with the least priority being the first item in the list:
    //
    //Backdrop Colour
    //Plane B with priority bit clear
    //Plane A with priority bit clear
    //Sprites with priority bit clear
    //Window Plane with priority bit clear
    //Plane B with priority bit set
    //Plane A with priority bit set
    //Sprites with priority bit set
    //Window Plane with priority bit set
    private void compaginateImage() {
        int limitHorTiles = getHorizontalTiles();

        //	TODO 256 en modo pal
        for (int j = 0; j < ROWS; j++) {
            for (int i = 0; i < limitHorTiles * 8; i++) {
                int backColor = planeBack[i][j];

                boolean aPrio = planePrioA[i][j];
                boolean bPrio = planePrioB[i][j];
                boolean sPrio = spritesPrio[i][j];
                boolean wPrio = windowPrio[i][j];

                int aColor = planeIndexColorA[i][j];
                int bColor = planeIndexColorB[i][j];
                int wColor = windowIndex[i][j];
                int spriteIndex = spritesIndex[i][j];

                boolean aDraw = (aColor != 0);
                boolean bDraw = (bColor != 0);
                boolean sDraw = (spriteIndex != 0);
                boolean wDraw = (wColor != 0);

                //	TODO if a draw W, don't draw A over it
                boolean W = (wDraw && ((wPrio)
                        || (!wPrio
                        && (!sDraw || (sDraw && !sPrio))
                        && (!aDraw || (aDraw && !aPrio))
                        && (!bDraw || (bDraw && !bPrio))
                )));

                int pix = 0;
                if (W) {
                    pix = window[i][j];
                    window[i][j] = 0;
                    windowIndex[i][j] = 0;
                } else {
                    boolean S = (sDraw && ((sPrio)
                            || (!sPrio && !aPrio && !bPrio)
                            || (!sPrio && aPrio && !aDraw)
                            || (!bDraw && bPrio && !sPrio && !aPrio)));
                    if (S) {
                        pix = sprites[i][j];
                        sprites[i][j] = 0;
                        spritesIndex[i][j] = 0;
                    } else {
                        boolean A = (aDraw && aPrio)
                                || (aDraw && ((!bPrio) || (!bDraw)));
                        if (A) {
                            pix = planeA[i][j];
                        } else if (bDraw) {
                            pix = planeB[i][j];
                        } else {
                            pix = backColor;
                        }
                    }
                }
                screenData[i][j] = pix;

                window[i][j] = 0;
                windowIndex[i][j] = 0;
                sprites[i][j] = 0;
                spritesIndex[i][j] = 0;
            }
        }
    }

    private int getHorizontalTiles(boolean isH40) {
        return isH40 ? 40 : 32;
    }

    private int getHorizontalTiles() {
        return getHorizontalTiles(isH40());
    }

    private void renderBack() {
        int line = this.line;
        int limitHorTiles = getHorizontalTiles();

        int backLine = (registers[7] >> 4) & 0x3;
        int backEntry = (registers[7]) & 0xF;
        int backIndex = (backLine * 32) + (backEntry * 2);
        int backColor = cram[backIndex] << 8 | cram[backIndex + 1];

        int r = (backColor >> 1) & 0x7;
        int g = (backColor >> 5) & 0x7;
        int b = (backColor >> 9) & 0x7;

        backColor = getColor(r, g, b);

        for (int pixel = 0; pixel < (limitHorTiles * 8); pixel++) {
            if (!disp) {
                planeBack[pixel][line] = 0;
            } else {
                planeBack[pixel][line] = backColor;
            }
        }
    }

    //Register 02 - Plane A Name Table Location
//7	6		5		4		3		2	1	0
//x	SA16	SA15	SA14	SA13	x	x	x
//	SA15-SA13 defines the upper three bits of the VRAM location of Plane A's nametable.
// This value is effectively the address divided by $400; however, the low three bits are ignored,
// so the Plane A nametable has to be located at a VRAM address that's a multiple of $2000.
// For example, if the Plane A nametable was to be located at $C000 in VRAM, it would be divided by $400,
// which results in $30, the proper value for this register.
//	SA16 is only valid if 128 KB mode is enabled, and allows for rebasing the Plane A nametable to the second 64 KB of VRAM.
    private void renderPlaneA() {
        int nameTableLocation = registers[2] & 0x38;    // bit 6 para modo extendido de vram, no lo emulo
        nameTableLocation *= 0x400;

        int tileLocator = nameTableLocation;

        int line = this.line;

        int reg10 = registers[0x10];
        int horScrollSize = reg10 & 3;
        int verScrollSize = (reg10 >> 4) & 3;

        int horPixelsSize = getHorizontalPixelSize();
        int limitHorTiles = getHorizontalTiles();

        int regD = registers[0xD];
        int hScrollBase = regD & 0x3F;    //	bit 6 = mode 128k
        hScrollBase *= 0x400;

        int regB = registers[0xB];
        int HS = regB & 0x3;
        int VS = (regB >> 2) & 0x1;

        int vertTileScreen = (line / 8);
        int scrollMap = 0;
        int scrollDataVer = 0;
        if (VS == 0) {    //	full screen scrolling
            scrollDataVer = vsram[0] << 8;
            scrollDataVer |= vsram[1];

            if (verScrollSize == 0) {    // 32 tiles (0x20)
                scrollMap = (scrollDataVer + line) & 0xFF;    //	32 * 8 lineas = 0x100
                if (horScrollSize == 0) {
                    tileLocator += ((scrollMap / 8) * (0x40));
                } else if (horScrollSize == 1) {
                    tileLocator += ((scrollMap / 8) * (0x80));
                } else {
                    tileLocator += ((scrollMap / 8) * (0x100));
                }

            } else if (verScrollSize == 1) {    // 64 tiles (0x40)
                scrollMap = (scrollDataVer + line) & 0x1FF;    //	64 * 8 lineas = 0x200
                tileLocator += ((scrollMap / 8) * 0x80);

            } else {
                scrollMap = (scrollDataVer + line) & 0x3FF;    //	128 * 8 lineas = 0x400
                tileLocator += ((scrollMap / 8) * 0x100);
            }

        } else {    // 16 columns (2 tiles) scrolling
            LOG.warn("16 columns (2 tiles) scrolling - not implemented"); //Kawasaki Superbike Challenge
        }

        long scrollDataHor = 0;
        long scrollTile = 0;
        if (HS == 0b00) {    //	entire screen is scrolled at once by one longword in the horizontal scroll table
            scrollDataHor = vram[hScrollBase] << 8;
            scrollDataHor |= vram[hScrollBase + 1];

            if (horScrollSize == 0) {    //	32 tiles
                scrollDataHor &= 0xFF;
                if (scrollDataHor != 0) {

                    scrollDataHor = 0x100 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }

            } else if (horScrollSize == 1) {    //	64 tiles
                scrollDataHor &= 0x1FF;
                if (scrollDataHor != 0) {

                    scrollDataHor = 0x200 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            } else {
                scrollDataHor &= 0xFFF;    //	128 tiles

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x1000 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            }

        } else if (HS == 0b10) {    //	long scrolls 8 pixels
            int scrollLine = hScrollBase + ((line / 8) * 32);    // 32 bytes por 8 scanlines

            scrollDataHor = vram[scrollLine] << 8;
            scrollDataHor |= vram[scrollLine + 1];

            if (scrollDataHor != 0) {
                if (horScrollSize == 0) {    //	32 tiles
                    scrollDataHor &= 0xFF;

                    scrollDataHor = 0x100 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;

                } else if (horScrollSize == 1) {    //	64 tiles
                    scrollDataHor &= 0x1FF;

                    scrollDataHor = 0x200 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;

                } else {        //	128 tiles
                    scrollDataHor &= 0x3FF;

                    scrollDataHor = 0x400 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            }

        } else if (HS == 0b11) {    //	scroll one scanline
            int scrollLine = hScrollBase + ((line) * 4);    // 4 bytes por 1 scanline

            scrollDataHor = vram[scrollLine] << 8;
            scrollDataHor |= vram[scrollLine + 1];

            if (horScrollSize == 0) {    //	32 tiles
                scrollDataHor &= 0xFF;

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x100 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }

            } else if (horScrollSize == 1) {    //	64 tiles
                scrollDataHor &= 0x1FF;

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x200 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }

            } else {
                scrollDataHor &= 0x3FF;

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x400 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            }

        }

        int loc = tileLocator;
        for (int pixel = 0; pixel < (limitHorTiles * 8); pixel++) {
            loc = (int) (((pixel + scrollDataHor)) % (horPixelsSize * 8)) / 8;

            int vertOffset = 0;
            if (VS == 1) {
                int scrollLine = (pixel / 16) * 4;    // 32 bytes por 8 scanlines

                scrollDataVer = vsram[scrollLine] << 8;
                scrollDataVer |= vsram[scrollLine + 1];

                if (verScrollSize == 0) {    // 32 tiles (0x20)
                    scrollMap = (scrollDataVer + line) & 0xFF;    //	32 * 8 lineas = 0x100
                    if (horScrollSize == 0) {
                        vertOffset += ((scrollMap / 8) * (0x40));
                    } else if (horScrollSize == 1) {
                        vertOffset += ((scrollMap / 8) * (0x80));
                    } else {
                        vertOffset += ((scrollMap / 8) * (0x100));
                    }

                } else if (verScrollSize == 1) {    // 64 tiles (0x40)
                    scrollMap = (scrollDataVer + line) & 0x1FF;    //	64 * 8 lineas = 0x200
                    vertOffset += ((scrollMap / 8) * 0x80);

                } else {
                    scrollMap = (scrollDataVer + line) & 0x3FF;    //	128 * 8 lineas = 0x400
                    vertOffset += ((scrollMap / 8) * 0x100);
                }
            }

            loc = tileLocator + (loc * 2);
            loc += vertOffset;

            int nameTable = vram[loc] << 8;
            nameTable |= vram[loc + 1];

//			An entry in a name table is 16 bits, and works as follows:
//			15			14 13	12				11		   			10 9 8 7 6 5 4 3 2 1 0
//			Priority	Palette	Vertical Flip	Horizontal Flip		Tile Index
            int tileIndex = (nameTable & 0x07FF);    // cada tile ocupa 32 bytes

            boolean horFlip = Util.bitSetTest(nameTable, 11);
            boolean vertFlip = Util.bitSetTest(nameTable, 12);
            int paletteLineIndex = (nameTable >> 13) & 0x3;
            boolean priority = Util.bitSetTest(nameTable, 15);

            int paletteLine = paletteLineIndex * 32;    //	16 colores por linea, 2 bytes por color

            tileIndex *= 0x20;

            int filas = (scrollMap % 8);

            int pointVert;
            if (vertFlip) {
                pointVert = (filas - 7) * -1;
            } else {
                pointVert = filas;
            }

            int pixelInTile = (int) ((pixel + scrollDataHor) % 8);

            int point = pixelInTile;
            if (horFlip) {
                point = (pixelInTile - 7) * -1;
            }

            if (!disp) {
                planeA[pixel][line] = 0;
                planePrioA[pixel][line] = false;
                planeIndexColorA[pixel][line] = 0;
            } else {
                point /= 2;

                int grab = (tileIndex + point) + (pointVert * 4);
                int data = vram[grab];

                int pixel1;
                if ((pixelInTile % 2) == 0) {
                    if (horFlip) {
                        pixel1 = data & 0x0F;
                    } else {
                        pixel1 = (data & 0xF0) >> 4;
                    }
                } else {
                    if (horFlip) {
                        pixel1 = (data & 0xF0) >> 4;
                    } else {
                        pixel1 = data & 0x0F;
                    }
                }

                int colorIndex1 = paletteLine + (pixel1 * 2);

                int color1 = cram[colorIndex1] << 8 | cram[colorIndex1 + 1];

                int r = (color1 >> 1) & 0x7;
                int g = (color1 >> 5) & 0x7;
                int b = (color1 >> 9) & 0x7;

                int theColor1 = getColor(r, g, b);

                planeA[pixel][line] = theColor1;
                planePrioA[pixel][line] = priority;
                planeIndexColorA[pixel][line] = pixel1;
            }
        }
    }

    private int getHorizontalPixelSize() {
        int reg10 = registers[0x10];
        int horScrollSize = reg10 & 3;
        int horPixelsSize = 0;
        if (horScrollSize == 0) {
            horPixelsSize = 32;
        } else if (horScrollSize == 1) {
            horPixelsSize = 64;
        } else {
            horPixelsSize = 128;
        }
        return horPixelsSize;
    }

    //	$04 - Plane B Name Table Location
//	Register 04 - Plane B Name Table Location
//	7	6	5	4	3		2		1		0
//	x	x	x	x	SB16	SB15	SB14	SB13
//	SB15-SB13 defines the upper three bits of the VRAM location of Plane B's nametable.
// This value is effectively the address divided by $2000, meaning that the Plane B nametable
// has to be located at a VRAM address that's a multiple of $2000.
// For example, if the Plane A nametable was to be located at $E000 in VRAM,
// it would be divided by $2000, which results in $07, the proper value for this register.
//	SB16 is only valid if 128 KB mode is enabled, and allows for rebasing the Plane B nametable to the second 64 KB of VRAM.
    private void renderPlaneB() {
        int nameTableLocation = (registers[4] & 0x7) << 3;    // bit 3 para modo extendido de vram, no lo emulo
        nameTableLocation *= 0x400;

        int tileLocator = nameTableLocation;

        int reg10 = registers[0x10];
        int horScrollSize = reg10 & 3;
        int verScrollSize = (reg10 >> 4) & 3;

        int horPixelsSize = getHorizontalPixelSize();
        int limitHorTiles = getHorizontalTiles();

        int line = this.line;

        int regD = registers[0xD];
        int hScrollBase = regD & 0x3F;    //	bit 6 = mode 128k
        hScrollBase *= 0x400;

        int regB = registers[0xB];
        int HS = regB & 0x3;
        int VS = (regB >> 2) & 0x1;

        int vertTileScreen = (line / 8);
        int scrollMap = 0;
        int scrollDataVer = 0;
        if (VS == 0) {    //	full screen scrolling
            scrollDataVer = vsram[2] << 8;
            scrollDataVer |= vsram[3];

            if (verScrollSize == 0) {    // 32 tiles (0x20)
                scrollMap = (scrollDataVer + line) & 0xFF;    //	32 * 8 lineas = 0x100
                if (horScrollSize == 0) {
                    tileLocator += ((scrollMap / 8) * (0x40));
                } else if (horScrollSize == 1) {
                    tileLocator += ((scrollMap / 8) * (0x80));
                } else {
                    tileLocator += ((scrollMap / 8) * (0x100));
                }

            } else if (verScrollSize == 1) {    // 64 tiles (0x40)
                scrollMap = (scrollDataVer + line) & 0x1FF;    //	64 * 8 lineas = 0x200
                tileLocator += ((scrollMap / 8) * 0x80);

            } else {
                scrollMap = (scrollDataVer + line) & 0x3FF;    //	128 * 8 lineas = 0x400
                tileLocator += ((scrollMap / 8) * 0x100);
            }
        } else {    // 16 columns (2 tiles) scrolling
            //FIXME support it
//		    LOG.info("16 columns (2 tiles) scrolling");
        }

        long scrollDataHor = 0;
        long scrollTile = 0;
        if (HS == 0b00) {    //	entire screen is scrolled at once by one longword in the horizontal scroll table
            scrollDataHor = vram[hScrollBase + 2] << 8;
            scrollDataHor |= vram[hScrollBase + 3];

            if (horScrollSize == 0) {    //	32 tiles
                scrollDataHor &= 0xFF;
                if (scrollDataHor != 0) {

                    scrollDataHor = 0x100 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }

            } else if (horScrollSize == 1) {    //	64 tiles
                scrollDataHor &= 0x1FF;
                if (scrollDataHor != 0) {

                    scrollDataHor = 0x200 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            } else {
                scrollDataHor &= 0xFFF;    //	128 tiles

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x1000 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            }

        } else if (HS == 0b10) {    //	long scrolls 8 pixels
            int scrollLine = hScrollBase + ((line / 8) * 32);    // 32 bytes por 8 scanlines

            scrollDataHor = vram[scrollLine + 2] << 8;
            scrollDataHor |= vram[scrollLine + 3];

            if (scrollDataHor != 0) {
                if (horScrollSize == 0) {    //	32 tiles
                    scrollDataHor &= 0xFF;

                    scrollDataHor = 0x100 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;

                } else if (horScrollSize == 1) {    //	64 tiles
                    scrollDataHor &= 0x1FF;

                    scrollDataHor = 0x200 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;

                } else {        //	128 tiles
                    scrollDataHor &= 0x3FF;

                    scrollDataHor = 0x400 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            }

        } else if (HS == 0b11) {    //	scroll one scanline
            int scrollLine = hScrollBase + ((line) * 4);    // 4 bytes por 1 scanline

            scrollDataHor = vram[scrollLine + 2] << 8;
            scrollDataHor |= vram[scrollLine + 3];

            if (horScrollSize == 0) {    //	32 tiles
                scrollDataHor &= 0xFF;

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x100 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }

            } else if (horScrollSize == 1) {    //	64 tiles
                scrollDataHor &= 0x1FF;

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x200 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }

            } else {
                scrollDataHor &= 0x3FF;

                if (scrollDataHor != 0) {
                    scrollDataHor = 0x400 - scrollDataHor;
                    scrollTile = scrollDataHor / 8;
                }
            }
        }

        int loc = tileLocator;
        for (int pixel = 0; pixel < (limitHorTiles * 8); pixel++) {
            loc = (int) (((pixel + scrollDataHor)) % (horPixelsSize * 8)) / 8;

            int vertOffset = 0;
            if (VS == 1) {
                int scrollLine = (pixel / 16) * 4;    // 32 bytes por 8 scanlines

                scrollDataVer = vsram[scrollLine + 2] << 8;
                scrollDataVer |= vsram[scrollLine + 3];

                if (verScrollSize == 0) {    // 32 tiles (0x20)
                    scrollMap = (scrollDataVer + line) & 0xFF;    //	32 * 8 lineas = 0x100
                    if (horScrollSize == 0) {
                        vertOffset += ((scrollMap / 8) * (0x40));
                    } else if (horScrollSize == 1) {
                        vertOffset += ((scrollMap / 8) * (0x80));
                    } else {
                        vertOffset += ((scrollMap / 8) * (0x100));
                    }

                } else if (verScrollSize == 1) {    // 64 tiles (0x40)
                    scrollMap = (scrollDataVer + line) & 0x1FF;    //	64 * 8 lineas = 0x200
                    vertOffset += ((scrollMap / 8) * 0x80);

                } else {
                    scrollMap = (scrollDataVer + line) & 0x3FF;    //	128 * 8 lineas = 0x400
                    vertOffset += ((scrollMap / 8) * 0x100);
                }
            }


            loc = tileLocator + (loc * 2);
            loc += vertOffset;

            loc &= VDP_VRAM_SIZE - 1; //Europa Sensen
            int nameTable = vram[loc] << 8;
            nameTable |= vram[loc + 1];

//			An entry in a name table is 16 bits, and works as follows:
//			15			14 13	12				11		   			10 9 8 7 6 5 4 3 2 1 0
//			Priority	Palette	Vertical Flip	Horizontal Flip		Tile Index
            int tileIndex = (nameTable & 0x07FF);    // cada tile ocupa 32 bytes

            boolean horFlip = Util.bitSetTest(nameTable, 11);
            boolean vertFlip = Util.bitSetTest(nameTable, 12);
            int paletteLineIndex = (nameTable >> 13) & 0x3;
            boolean priority = Util.bitSetTest(nameTable, 15);

            int paletteLine = paletteLineIndex * 32;    //	16 colores por linea, 2 bytes por color

            tileIndex *= 0x20;

            int filas = (scrollMap % 8);

            int pointVert;
            if (vertFlip) {
                pointVert = (filas - 7) * -1;
            } else {
                pointVert = filas;
            }

            int pixelInTile = (int) ((pixel + scrollDataHor) % 8);

            int point = pixelInTile;
            if (horFlip) {
                point = (pixelInTile - 7) * -1;
            }

            if (!disp) {
                planeB[pixel][line] = 0;
                planePrioB[pixel][line] = false;
                planeIndexColorB[pixel][line] = 0;
            } else {
                point /= 2;

                int grab = (tileIndex + point) + (pointVert * 4);
                int data = vram[grab];

                int pixel1;
                if ((pixelInTile % 2) == 0) {
                    if (horFlip) {
                        pixel1 = data & 0x0F;
                    } else {
                        pixel1 = (data & 0xF0) >> 4;
                    }
                } else {
                    if (horFlip) {
                        pixel1 = (data & 0xF0) >> 4;
                    } else {
                        pixel1 = data & 0x0F;
                    }
                }

                int colorIndex1 = paletteLine + (pixel1 * 2);
                int color1 = cram[colorIndex1] << 8 | cram[colorIndex1 + 1];

                int r = (color1 >> 1) & 0x7;
                int g = (color1 >> 5) & 0x7;
                int b = (color1 >> 9) & 0x7;

                int theColor1 = getColor(r, g, b);

                planeB[pixel][line] = theColor1;
                planePrioB[pixel][line] = priority;
                planeIndexColorB[pixel][line] = pixel1;
            }
        }
    }

    // This value is effectively the address divided by $400; however, the low
    // bit is ignored, so the Window nametable has to be located at a VRAM
    // address that's a multiple of $800. For example, if the Window nametable
    // was to be located at $F000 in VRAM, it would be divided by $400, which
    // results in $3C, the proper value for this register.
    private void renderWindow() {
        int reg12 = registers[0x12];
        int windowVert = reg12 & 0x1F;
        boolean down = ((reg12 & 0x80) == 0x80) ? true : false;

        if (windowVert != 0) {

            int line = this.line;
            int vertTile = (line / 8);

            int vertLimit = (windowVert * 8);

            if (!down) {
                if (line >= vertLimit) {
                    return;
                }
            } else {
                if (line < vertLimit) {
                    return;
                }
            }

            boolean isH40 = isH40();

            int limitHorTiles = getHorizontalTiles(isH40);
            int nameTableLocation;
            int tileLocator;
            if (isH40) {
                nameTableLocation = registers[0x3] & 0x3C;    //	WD11 is ignored if the display resolution is 320px wide (H40), which limits the Window nametable address to multiples of $1000.
                nameTableLocation *= 0x400;
                tileLocator = nameTableLocation + (128 * vertTile);
            } else {
                nameTableLocation = registers[0x3] & 0x3E;    //	bit 6 = 128k mode
                nameTableLocation *= 0x400;
                tileLocator = nameTableLocation + (64 * vertTile);
            }

            for (int horTile = 0; horTile < limitHorTiles; horTile++) {
                int loc = tileLocator;

                int nameTable = vram[loc] << 8;
                nameTable |= vram[loc + 1];

                tileLocator += 2;

//				An entry in a name table is 16 bits, and works as follows:
//				15			14 13	12				11		   			10 9 8 7 6 5 4 3 2 1 0
//				Priority	Palette	Vertical Flip	Horizontal Flip		Tile Index
                int tileIndex = (nameTable & 0x07FF);    // cada tile ocupa 32 bytes

                boolean horFlip = Util.bitSetTest(nameTable, 11);
                boolean vertFlip = Util.bitSetTest(nameTable, 12);
                int paletteLineIndex = (nameTable >> 13) & 0x3;
                boolean priority = Util.bitSetTest(nameTable, 15);

                int paletteLine = paletteLineIndex * 32;    //	16 colores por linea, 2 bytes por color

                tileIndex *= 0x20;

                int filas = (line % 8);

                int pointVert;
                if (vertFlip) {
                    pointVert = (filas - 7) * -1;
                } else {
                    pointVert = filas;
                }
                for (int k = 0; k < 4; k++) {
                    int point;
                    if (horFlip) {
                        point = (k - 3) * -1;
                    } else {
                        point = k;
                    }

                    int po = horTile * 8 + (k * 2);

                    if (!disp) {
                        window[po][line] = 0;
                        window[po + 1][line] = 0;

                        windowPrio[po][line] = false;
                        windowPrio[po + 1][line] = false;

                        windowIndex[po][line] = 0;
                        windowIndex[po + 1][line] = 0;
                    } else {
                        int grab = (tileIndex + point) + (pointVert * 4);
                        int data = vram[grab];

                        int pixel1, pixel2;
                        if (horFlip) {
                            pixel1 = data & 0x0F;
                            pixel2 = (data & 0xF0) >> 4;
                        } else {
                            pixel1 = (data & 0xF0) >> 4;
                            pixel2 = data & 0x0F;
                        }

                        int colorIndex1 = paletteLine + (pixel1 * 2);
                        int colorIndex2 = paletteLine + (pixel2 * 2);

                        int color1 = cram[colorIndex1] << 8 | cram[colorIndex1 + 1];
                        int color2 = cram[colorIndex2] << 8 | cram[colorIndex2 + 1];

                        int r = (color1 >> 1) & 0x7;
                        int g = (color1 >> 5) & 0x7;
                        int b = (color1 >> 9) & 0x7;

                        int r2 = (color2 >> 1) & 0x7;
                        int g2 = (color2 >> 5) & 0x7;
                        int b2 = (color2 >> 9) & 0x7;

                        int theColor1 = getColor(r, g, b);
                        int theColor2 = getColor(r2, g2, b2);

                        window[po][line] = theColor1;
                        window[po + 1][line] = theColor2;

                        windowPrio[po][line] = priority;
                        windowPrio[po + 1][line] = priority;

                        windowIndex[po][line] = pixel1;
                        windowIndex[po + 1][line] = pixel2;
                    }
                }
            }
        }
    }

    private int getColor(int red, int green, int blue) {
        return colorMapper.getColor(red, green, blue);
    }

    @Override
    public long readDataPort(Size size) {
        if (vramMode == VramMode.vramRead) {
            long data = readVram(size);
            return data;

        } else if (vramMode == VramMode.cramRead) {
            long data = readCram(size);
            return data;

        } else if (vramMode == VramMode.vsramRead) {
            long data = readVsram(size);
            return data;

        } else {
            LOG.warn("Read pero mando write, Modo video: " + Objects.toString(vramMode));
//			throw new RuntimeException("Modo video: " + vramMode.toString());
        }
        return 0;
    }

    private long readVram(Size size) {
        int index = nextFIFOReadEntry;
        int address = addressPort;

        long first = all >> 16;
        long second = all & 0xFFFF;

        int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
        int addr = (int) ((first & 0x3FFF) | ((second & 0x3) << 14));

        int offset = addr + autoIncrementTotal;

        long data1 = vram[offset];
        long data2 = vram[offset + 1];

        long data = ((data1 << 8) | (data2));

//		System.out.println("addr: " + Integer.toHexString(offset) + "-" + Integer.toHexString(offset + 1) + ": "
//				+ Integer.toHexString((int) data));

//		fifoAddress[index] = offset;
//		fifoCode[index] = code;
//		fifoData[index] = word;

//		if (incrementAddr) {
        int incrementOffset = autoIncrementTotal + autoIncrementData;
        autoIncrementTotal = incrementOffset;
//		}

        return data;

//		address = address + incrementOffset;	// FIXME wrap
//		offset = offset + incrementOffset;
//		index = (index + 1) % 4;
//		
//		nextFIFOReadEntry = index;
//		nextFIFOWriteEntry = index;
    }

    private long readCram(Size size) {
        int index = nextFIFOReadEntry;
        int address = addressPort;

        long first = all >> 16;
        long second = all & 0xFFFF;

        int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
        int addr = (int) ((first & 0x3FFF) | ((second & 0x3) << 14));

        int offset = addr + autoIncrementTotal;

        if (offset >= VDP_CRAM_SIZE) {
            return 0;
        }

        long data1 = cram[offset] & 0xEEE;
        long data2 = cram[offset + 1] & 0xEEE;

        long data = ((data1 << 8) | (data2));

//		System.out.println("addr: " + Integer.toHexString(offset) + "-" + Integer.toHexString(offset + 1) + ": "
//				+ Integer.toHexString((int) data));

//		fifoAddress[index] = offset;
//		fifoCode[index] = code;
//		fifoData[index] = word;

//		if (incrementAddr) {
        int incrementOffset = autoIncrementTotal + autoIncrementData;
        autoIncrementTotal = incrementOffset;
//		}

        return data;

//		address = address + incrementOffset;	// FIXME wrap
//		offset = offset + incrementOffset;
//		index = (index + 1) % 4;
//		
//		nextFIFOReadEntry = index;
//		nextFIFOWriteEntry = index;
    }

    private long readVsram(Size size) {
        int index = nextFIFOReadEntry;
        int address = addressPort;

        long first = all >> 16;
        long second = all & 0xFFFF;

        int code = (int) ((first >> 14) | (((second >> 4) & 0xF) << 2));
        int addr = (int) ((first & 0x3FFF) | ((second & 0x3) << 14));

        int offset = addr + autoIncrementTotal;

        long data1 = vsram[offset] & 0xEEE;
        long data2 = vsram[offset + 1] & 0xEEE;

        long data = ((data1 << 8) | (data2));

//		System.out.println("addr: " + Integer.toHexString(offset) + "-" + Integer.toHexString(offset + 1) + ": "
//				+ Integer.toHexString((int) data));

//		fifoAddress[index] = offset;
//		fifoCode[index] = code;
//		fifoData[index] = word;

//		if (incrementAddr) {
        int incrementOffset = autoIncrementTotal + autoIncrementData;
        autoIncrementTotal = incrementOffset;
//		}

        return data;

//		address = address + incrementOffset;	// FIXME wrap
//		offset = offset + incrementOffset;
//		index = (index + 1) % 4;
//		
//		nextFIFOReadEntry = index;
//		nextFIFOWriteEntry = index;
    }
}
