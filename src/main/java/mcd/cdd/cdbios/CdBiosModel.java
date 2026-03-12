package mcd.cdd.cdbios;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class CdBiosModel {

    interface NameCode {
        int getCode();

        String name();
    }


    enum BiosStatusByte1 {
        Stopped(0), Playing(1), Seeking(3), Paused(5), SeekPlay(8), NoDisk(16), ReadingToc(32), TrayOpen(64),
        NotReady(0x80), Unknown(-1);

        public final int code;

        BiosStatusByte1(int c) {
            this.code = c;
        }

        static BiosStatusByte1 getBiosStatus(int val) {
            if ((val & 0x80) == NotReady.code) {
                return NotReady;
            }
            for (var v : values()) {
                if (v.code == val) {
                    return v;
                }
            }
            return Unknown;
        }
    }

    enum BiosStatusByte0 {
        DataReadComplete(0), ReadingData(1), ReadingDataPaused(5), SeekRead(8), None(-1), Unknown(-2);

        public final int code;

        BiosStatusByte0(int c) {
            this.code = c;
        }

        static BiosStatusByte0 getBiosStatus(BiosStatusByte1 bs1, int val) {
            if (bs1 == BiosStatusByte1.NotReady) {
                return None;
            }
            for (var v : values()) {
                if (v.code == val) {
                    return v;
                }
            }
            return Unknown;
        }

    }

    enum BootstatEnum {
        CD_NOTREADY(-1), CD_NODISC(0), CD_MUSIC(1), CD_ROM(2), CD_MIXED(3),
        CD_GAMESYSTEM(4), CD_GAMEDATA(5), CD_GAMEBOOT(6), CD_GAMEDISC(7);

        public static final BootstatEnum[] vals = BootstatEnum.values();
        public final int val;

        BootstatEnum(int val) {
            this.val = val;
        }
    }

    public static NameCode invalidInstance(String name) {
        return new NameCode() {
            @Override
            public int getCode() {
                return 0;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }


    enum CdBiosFunction implements NameCode {
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCSTOP - Stops playing a track if it's currently playing.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCSTOP macro
         * CDBIOS #MSCSTOP
         * endm
         */
        MSCSTOP(0x0002),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCPAUSEON - Pauses the drive when a track is playing.  If the
         * ; drive is left paused it will stop after a programmable delay (see
         * ; CDBPAUSE).
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCPAUSEON macro
         * CDBIOS #MSCPAUSEON
         * endm
         */
        MSCPAUSEON(0x0003),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCPAUSEOFF - Resumes playing a track after a pause.  If the drive
         * ; has timed out and stopped, the BIOS will seek to the pause time (with
         * ; the attendant delay) and resume playing.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCPAUSEOFF macro
         * CDBIOS #MSCPAUSEOFF
         * endm
         */
        MSCPAUSEOFF(0x0004),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCSCANFF - Starts playing from the current position in fast
         * ; forward.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCSCANFF macro
         * CDBIOS #MSCSCANFF
         * endm
         */
        MSCSCANFF(0x0005),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCSCANFR - Same as MSCSCANFF, but backwards.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCSCANFR macro
         * CDBIOS #MSCSCANFR
         * endm
         */
        MSCSCANFR(0x0006),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCSCANOFF - Returns to normal play mode.  If the drive was
         * ; paused before the scan was initiated, it will be returned to pause.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCSCANOFF macro
         * CDBIOS #MSCSCANOFF
         * endm
         */
        MSCSCANOFF(0x0007),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_ROMPAUSEON - Stops reading data into the CDC and pauses.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_ROMPAUSEON macro
         * CDBIOS #ROMPAUSEON
         * endm
         */
        ROMPAUSEON(0x0008),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_ROMPAUSEOFF - Resumes reading data into the CDC from the current
         * ; logical sector.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_ROMPAUSEOFF macro
         * CDBIOS #ROMPAUSEOFF
         * endm
         */
        ROMPAUSEOFF(0x0009),

        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_DRVOPEN - Opens the drive.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_DRVOPEN macro
         * CDBIOS #DRVOPEN
         * endm
         */
        DRVOPEN(0x000A),

        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_DRVINIT - Closes the disk tray and reads the TOC from the CD.
         * ; Pauses for 2 seconds after reading the TOC.  If bit 7 of the TOC track
         * ; is set, the BIOS starts playing the first track automatically.  Waits
         * ; for a DRVOPEN request if there is no disk in the drive.
         * ;
         * ; input:
         * ;   a0.l  address of initialization parameters:
         * ;           dc.b    $01   ; Track # to read TOC from (normally $01)
         * ;           dc.b    $FF   ; Last track # ($FF = read all tracks)
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_DRVINIT macro
         * CDBIOS #DRVINIT
         * endm
         */
        DRVINIT(0x0010),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCPLAY - Starts playing at a specified track.  Continues playing
         * ; through subsequent tracks.
         * ;
         * ; input:
         * ;   a0.l  address of 16 bit track number
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCPLAY macro
         * CDBIOS #MSCPLAY
         * endm
         */
        MSCPLAY(0x0011),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCPLAY1 - Plays a track once and pauses.
         * ;
         * ; input:
         * ;   a0.l  address of a 16 bit track number
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCPLAY1 macro
         * CDBIOS #MSCPLAY1
         * endm
         */
        MSCPLAY1(0x0012),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCPLAYR - Plays the designated track repeatedly.
         * ;
         * ; input:
         * ;   a0.l  address of a 16 bit track number
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCPLAYR macro
         * CDBIOS #MSCPLAYR
         * endm
         */
        MSCPLAYR(0x0013),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCPLAYT - Starts playing from a specified time.
         * ;
         * ; input:
         * ;   a0.l  address of a 32 bit BCD time code in the format mm:ss:ff:00
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCPLAYT macro
         * CDBIOS #MSCPLAYT
         * endm
         */
        MSCPLAYT(0x0014),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCSEEK - Seeks to the beginning of the selected track and pauses.
         * ;
         * ; input:
         * ;   a0.l  address of a 16 bit track number
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCSEEK macro
         * CDBIOS #MSCSEEK
         * endm
         */
        MSCSEEK(0x0015),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCSEEKT - Seeks to a specified time.
         * ;
         * ; input:
         * ;   a0.l  address of a 32 bit BCD time code in the format mm:ss:ff:00
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCSEEKT macro
         * CDBIOS #MSCSEEKT
         * endm
         */
        MSCSEEKT(0x0016),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_ROMREAD - Begins reading data from the CDROM at the designated
         * ; logical sector.  Executes a CDCSTART to begin the read, but doesn't
         * ; stop automatically.
         * ;
         * ; Note - ROMREAD actually pre-seeks by 2 sectors, but doesn't start
         * ; passing data to the CDC until the desired sector is reached.
         * ;
         * ; input:
         * ;   a0.l  address of a 32 bit logical sector number
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_ROMREAD macro
         * CDBIOS #ROMREAD
         * endm
         */
        ROMREAD(0x0017),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_ROMSEEK - Seeks to the designated logical sector and pauses.
         * ;
         * ; input:
         * ;   a0.l  address of a 32 bit logical sector number
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_ROMSEEK macro
         * CDBIOS #ROMSEEK
         * endm
         */
        ROMSEEK(0x0018),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_MSCSEEK1 - Seeks to the beginning of the selected track and pauses.
         * ; Once the BIOS detects a pause state, it plays the track once.
         * ;
         * ; input:
         * ;   a0.l  address of a 16 bit track number
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_MSCSEEK1 macro
         * CDBIOS #MSCSEEK1
         * endm
         */
        MSCSEEK1(0x0019),
        TESTENTRY(0x001E),
        TESTENTRYLOOP(0x001F),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_ROMREADN - Same as ROMREAD, but stops after reading the requested
         * ; number of sectors.
         * ;
         * ; input:
         * ;   a0.l  address of a 32 bit sector number and 32 bit sector count
         * ;           dc.l    $00000001   ; First sector to read
         * ;           dc.l    $00001234   ; Number of sectors to read
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_ROMREADN macro
         * CDBIOS #ROMREADN
         * endm
         */
        ROMREADN(0x0020),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_ROMREADE - Same as ROMREAD, but reads between two logical sectors.
         * ;
         * ; input:
         * ;   a0.l  address of table of 32 bit logical sector numbers
         * ;           dc.l    $00000001   ; First sector to read
         * ;           dc.l    $00000123   ; Last sector to read
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_ROMREADE macro
         * CDBIOS #ROMREADE
         * endm
         */
        ROMREADE(0x0021),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDBCHK - Querys the BIOS on the status of the last command.
         * ; Returns success if the command has been executed, not if it's complete.
         * ; This means that CDBCHK will return success on a seek command once the
         * ; seek has started, NOT when the seek is actually finished.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   cc  Command has been executed
         * ;   cs  BIOS is busy
         * ;-----------------------------------------------------------------------
         * BIOS_CDBCHK macro
         * CDBIOS #CDBCHK
         * endm
         */
        CDBCHK(0x0080),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDBSTAT
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   a0.l  address of BIOS status table
         * ;-----------------------------------------------------------------------
         * BIOS_CDBSTAT macro
         * CDBIOS #CDBSTAT
         * endm
         */
        CDBSTAT(0x0081),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDBTOCWRITE - Writes data to the TOC in memory.  Don't write to
         * ; the TOC while the BIOS is performing a DRVINIT.
         * ;
         * ; input:
         * ;   a0.l  address of a table of TOC entries to write to the TOC.  Format
         * ;         of the entries is mm:ss:ff:## where ## is the track number.
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_CDBTOCWRITE macro
         * CDBIOS #CDBTOCWRITE
         * endm
         */
        CDBTOCWRITE(0x0082),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDBTOCREAD - Gets the time for the specified track from the TOC.
         * ; If the track isn't in the TOC, the BIOS will either return the time of
         * ; the last track read or the beginning of the disk.  Don't call this
         * ; function while the BIOS is loading the TOC (see DRVINIT).
         * ;
         * ; input:
         * ;   d1.w  16 bit track number
         * ;
         * ; returns:
         * ;   d0.l  BCD time of requested track in mm:ss:ff:## format where ## is
         * ;         the requested track number or 00 if there was an error
         * ;
         * ;   d1.b  Track type:
         * ;           $00 = CD-DA track
         * ;           $FF = CD-ROM track
         * ;-----------------------------------------------------------------------
         * BIOS_CDBTOCREAD macro
         * CDBIOS #CDBTOCREAD
         * endm
         */
        CDBTOCREAD(0x0083),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDBPAUSE - Sets the delay time before the BIOS switches from
         * ; pause to standby.  Normal ranges for this delay time are $1194 - $FFFE.
         * ; A delay of $FFFF prevents the drive from stopping, but can  damage the
         * ; drive if used improperly.
         * ;
         * ; input:
         * ;   d1.w  16 bit delay time
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_CDBPAUSE macro
         * CDBIOS #CDBPAUSE
         * endm
         */
        CDBPAUSE(0x0084),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_FDRSET - Sets the audio volume.  If bit 15 of the volume parameter
         * ; is 1, sets the master volume level.  There's a delay of up to 13ms
         * ; before the volume begins to change and another 23ms for the new volume
         * ; level to take effect.  The master volume sets a maximum level which the
         * ; volume level can't exceed.
         * ;
         * ; input:
         * ;   d1.w  16 bit volume         ($0000 = min    $0400 = max)
         * ;         16 bit master volume  ($8000 = min    $8400 = max)
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_FDRSET macro
         * CDBIOS #FDRSET
         * endm
         */
        FDRSET(0x0085),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_FDRCHG - Ramps the audio volume from its current level to a new
         * ; level at the requested rate.  As in FDRSET, there's a delay of up to
         * ; 13ms before the change starts.
         * ;
         * ; input:
         * ;   d1.l  32 bit volume change
         * ;         high word:  new 16 bit volume   ($0000 = min    $0400 = max)
         * ;         low word:   16 bit rate in steps/vblank
         * ;                     $0001 = slow
         * ;                     $0200 = fast
         * ;                     $0400 = set immediately
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_FDRCHG macro
         * CDBIOS #FDRCHG
         * endm
         */
        FDRCHG(0x0086),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDCSTART - Starts reading data from the current logical sector
         * ; into the CDC.  The BIOS pre-seeks by 2 to 4 sectors and data read
         * ; actually begins before the requested sector.  It's up to the caller
         * ; to identify the correct starting sector (usually by checking the time
         * ; codes in the headers as they're read from the CDC buffer).
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_CDCSTART macro
         * CDBIOS #CDCSTART
         * endm
         */
        CDCSTART(0x0087),
        CDCSTARTP(0x0088),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDCSTOP - Stops reading data into the CDC.  If a sector is being
         * ; read when CDCSTOP is called, it's lost.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_CDCSTOP macro
         * CDBIOS #CDCSTOP
         * endm
         */
        CDCSTOP(0x0089),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDCSTAT - Queries the CDC buffer.  If no sector is ready for
         * ; read, the carry bit will be set.  Up to 5 sectors can be buffered in
         * ; the CDC buffer.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   cc  Sector available for read
         * ;   cs  No sectors available
         * ;-----------------------------------------------------------------------
         * BIOS_CDCSTAT macro
         * CDBIOS #CDCSTAT
         * endm
         */
        CDCSTAT(0x008A),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDCREAD - If a sector is ready in the CDC buffer, the BIOS
         * ; prepares to send the sector to the current device destination.  Make
         * ; sure to set the device destination BEFORE calling CDCREAD.  If a
         * ; sector is ready, the carry bit will be cleared on return and it's
         * ; necessary to respond with a call to CDCACK.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   cc    Sector ready for transfer
         * ;   d0.l  Sector header in BCD mm:ss:ff:md format where md is sector mode
         * ;           $00 = CD-DA
         * ;           $01 = CD-ROM mode 1
         * ;           $02 = CD-ROM mode 2
         * ;   cs    Sector not ready
         * ;-----------------------------------------------------------------------
         * BIOS_CDCREAD macro
         * CDBIOS #CDCREAD
         * endm
         */
        CDCREAD(0x008B),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDCTRN - Uses the Sub-CPU to read one sector into RAM.  The
         * ; device destination must be set to SUB-CPU read before calling CDCTRN.
         * ;
         * ; input:
         * ;   a0.l  address of sector destination buffer (at least 2336 bytes)
         * ;   a1.l  address of header destination buffer (at least 4 bytes)
         * ;
         * ; returns:
         * ;   cc    Sector successfully transferred
         * ;   cs    Transfer failed
         * ;   a0.l  Next sector destination address (a0 + 2336)
         * ;   a1.l  Next header destination address (a1 + 4)
         * ;-----------------------------------------------------------------------
         * BIOS_CDCTRN macro
         * CDBIOS #CDCTRN
         * endm
         */
        CDCTRN(0x008C),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDCACK - Informs the CDC that the current sector has been read
         * ; and the caller is ready for the next sector.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_CDCACK macro
         * CDBIOS #CDCACK
         * endm
         */
        CDCACK(0x008D),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_SCDINIT - Initializes the BIOS for subcode reads.
         * ;
         * ; input:
         * ;   a0.l  address of scratch buffer (at least $750 long)
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_SCDINIT macro
         * CDBIOS #SCDINIT
         * endm
         */
        SCDINIT(0x008E),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_SCDSTART - Enables reading of subcode data by the CDC.
         * ;
         * ; input:
         * ;   d1.w  Subcode processing mode
         * ;           0 = --------
         * ;           1 = --RSTUVW
         * ;           2 = PQ------
         * ;           3 = PQRSTUVW
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_SCDSTART macro
         * CDBIOS #SCDSTART
         * endm
         */
        SCDSTART(0x008F),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_SCDSTOP - Disables reading of subcode data by the CDC.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_SCDSTOP macro
         * CDBIOS #SCDSTOP
         * endm
         */
        SCDSTOP(0x0090),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_SCDSTAT - Checks subcode error status.
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   d0.l  errqcodecrc / errpackcirc / scdflag / restrcnt
         * ;   d1.l  erroverrun / errpacketbufful / errqcodefufful / errpackfufful
         * ;-----------------------------------------------------------------------
         * BIOS_SCDSTAT macro
         * CDBIOS #SCDSTAT
         * endm
         */
        SCDSTAT(0x0091),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_SCDREAD - Reads R through W subcode channels.
         * ;
         * ; input:
         * ;   a0.l  address of subcode buffer (24 bytes minimum)
         * ;
         * ; returns:
         * ;   cc    Read successful
         * ;   cs    Read failed
         * ;   a0.l  address of next subcode buffer (a0.l + 24)
         * ;-----------------------------------------------------------------------
         * BIOS_SCDREAD macro
         * CDBIOS #SCDREAD
         * endm
         */
        SCDREAD(0x0092),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_SCDPQ - Gets P & Q codes from subcode.
         * ;
         * ; input:
         * ;   a0.l  address of Q code buffer (12 bytes minimum)
         * ;
         * ; returns:
         * ;   cc    Read successful
         * ;   cs    Read failed
         * ;   a0.l  address of next Q code buffer (a0.l + 12)
         * ;-----------------------------------------------------------------------
         * BIOS_SCDPQ macro
         * CDBIOS #SCDPQ
         * endm
         */
        SCDPQ(0x0093),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_SCDPQL - Gets the last P & Q codes.
         * ;
         * ; input:
         * ;   a0.l  address of Q code buffer (12 bytes minimum)
         * ;
         * ; returns:
         * ;   cc    Read successful
         * ;   cs    Read failed
         * ;   a0.l  address of next Q code buffer (a0.l + 12)
         * ;-----------------------------------------------------------------------
         * BIOS_SCDPQL macro
         * CDBIOS #SCDPQL
         * endm
         */
        SCDPQL(0x0094),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_LEDSET - Controls the Ready and Access LED's on the front panel
         * ; of the CD unit.
         * ;
         * ; input:
         * ;   d1.w  MODE          Ready (green)   Access (red)    System Indication
         * ;         ---------------------------------------------------------------
         * ;                           off             off         only at reset
         * ;         LEDREADY (0)      on              blink       CD ready / no disk
         * ;         LEDDISCIN (1)     on              off         CD ready / disk ok
         * ;         LEDACCESS (2)     on              on          CD accessing
         * ;         LEDSTANDBY (3)    blink           off         standby mode
         * ;         LEDERROR (4)      blink           blink       reserved
         * ;         LEDMODE5 (5)      blink           on          reserved
         * ;         LEDMODE6 (6)      off             blink       reserved
         * ;         LEDMODE7 (7)      off             on          reserved
         * ;         LEDSYSTEM (?)                                 rtn ctrl to BIOS
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_LEDSET macro
         * CDBIOS #LEDSET
         * endm
         */
        LEDSET(0x0095),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_CDCSETMODE - Tells the BIOS which mode to read the CD in.  Accepts
         * ; bit flags that allow selection of the three basic CD modes as follows:
         * ;
         * ;       Mode 0 (CD-DA)                              2
         * ;       Mode 1 (CD-ROM with full error correction)  0
         * ;       Mode 2 (CD-ROM with CRC only)               1
         * ;
         * ; input:
         * ;   d1.w  FEDCBA9876543210
         * ;                     ||||
         * ;                     |||+--> CD Mode 2
         * ;                     ||+---> CD-DA mode
         * ;                     |+----> transfer error block with data
         * ;                     +-----> re-read last data
         * ;
         * ; returns:
         * ;   nothing
         * ;-----------------------------------------------------------------------
         * BIOS_CDCSETMODE macro
         * CDBIOS #CDCSETMODE
         * endm
         */
        CDCSETMODE(0x0096),
        WONDERREQ(0x0097),
        WONDERCHK(0x0098);

        public final int code;

        CdBiosFunction(int code) {
            this.code = code;
        }

        @Override
        public int getCode() {
            return code;
        }
    }

    enum CdBootFunction implements NameCode {
        CBTINIT(0x0000),
        CBTINT(0x0001),
        CBTOPENDISC(0x0002),
        CBTOPENSTAT(0x0003),
        CBTCHKDISC(0x0004),
        CBTCHKSTAT(0x0005),
        CBTIPDISC(0x0006),
        CBTIPSTAT(0x0007),
        CBTSPDISC(0x0008),
        CBTSPSTAT(0x0009);

        public final int code;

        CdBootFunction(int code) {
            this.code = code;
        }

        @Override
        public int getCode() {
            return code;
        }
    }

    /**
     * ;-----------------------------------------------------------------------
     * ; NOTE:  The backup ram on the super target devlopment systems is write
     * ;         protected if the production Boot Rom is being used.  A
     * ;         Development Boot Rom must be obtained before the backup ram can
     * ;         be used.
     * ;
     * ;        The name of the save game files must be registered with SOJ before
     * ;         a game can be shipped.
     * ;
     * ;        Please make sure to read the CD Software Standards section in the
     * ;         manual.  There is a section on backup ram standards that must be
     * ;         followed.
     * ;
     * ;        For a full description of each Back-Up Ram function, see the BIOS
     * ;         section of the CD manual.
     * ;
     * ;        Some of the Back-Up RAM functions require a string buffer to
     * ;         be passed into the function.  Some of these functions return
     * ;         0 terminated text strings.
     * ;-------------------------------------------------------------------------
     */
    enum CdBuramFunction implements NameCode {
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_BRMINIT - Prepares to write into and read from Back-Up Ram.
         * ;
         * ; input:
         * ;   a0.l  pointer to scratch ram (size $640 bytes).
         * ;
         * ;   a1.l  pointer to the buffer for display strings (size: 12 bytes)
         * ;
         * ; returns:
         * ;   cc    SEGA formatted RAM is present
         * ;   cs    Not formatted or no RAM
         * ;   d0.w  size of backup RAM  $2(000) ~ $100(000)  bytes
         * ;   d1.w  0 : No RAM
         * ;         1 : Not Formatted
         * ;         2 : Other Format
         * ;   a1.l  pointer to display strings
         * ;-----------------------------------------------------------------------
         * BIOS_BRMINIT macro
         * BURAM #BRMINIT
         * endm
         */
        BRMINIT(0x0000),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_BRMSTAT - Returns how much Back-Up RAM has been used.
         * ;
         * ; input:
         * ;   a1.l  pointer to display string buffer (size: 12 bytes)
         * ;
         * ; returns:
         * ;   d0.w  number of blocks of free area
         * ;   d1.w  number of files in directory
         * ;-----------------------------------------------------------------------
         * BIOS_BRMSTAT macro
         * BURAM #BRMSTAT
         * endm
         */
        BRMSTAT(0x0001),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_BRMSERCH - Searches for the desired file in Back-Up Ram.  The file
         * ;                  names are 11 ASCII characters terminated with a 0.
         * ;
         * ; input:
         * ;   a0.l  pointer to parameter (file name) table
         * ;             file name = 11 ASCII chars [0~9 A~Z_]   0 terminated
         * ;
         * ; returns:
         * ;   cc    file name found
         * ;   cs    file name not found
         * ;   d0.w  number of blocks
         * ;   d1.b  MODE
         * ;         0 : normal
         * ;        -1 : data protected (with protect function)
         * ;   a0.l  backup ram start address for search
         * ;-----------------------------------------------------------------------
         * BIOS_BRMSERCH macro
         * BURAM #BRMSERCH
         * endm
         */
        BRMSERCH(0x0002),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_BRMREAD - reads data from Back-Up RAM.
         * ;
         * ; input:
         * ;   a0.l  pointer to parameter (file name) table
         * ;   a1.l  pointer to write buffer
         * ;
         * ; returns:
         * ;   cc    Read Okay
         * ;   cs    Error
         * ;   d0.w  number of blocks
         * ;   d1.b  MODE
         * ;         0 : normal
         * ;        -1 : data protected
         * ;-----------------------------------------------------------------------
         * BIOS_BRMREAD macro
         * BURAM #BRMREAD
         * endm
         */
        BRMREAD(0x0003),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_BRMWRITE - Writes data in Back-Up RAM.
         * ;
         * ; input:
         * ;   a0.l  pointer to parameter (file name) table
         * ;          flag.b       $00: normal
         * ;                       $FF: encoded (with protect function)
         * ;          block_size.w $00: 1 block = $40 bytes
         * ;                       $FF: 1 block = $20 bytes
         * ;   a1.l  pointer to save data
         * ;
         * ; returns:
         * ;   cc    Okay, complete
         * ;   cs    Error, cannot write in the file
         * ;-----------------------------------------------------------------------
         * BIOS_BRMWRITE macro
         * BURAM #BRMWRITE
         * endm
         */
        BRMWRITE(0x0004),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_BRMDEL - Deletes data on Back-Up Ram.
         * ;
         * ; input:
         * ;   a0.l  pointer to parameter (file name) table
         * ;
         * ; returns:
         * ;   cc    deleted
         * ;   cs    not found
         * ;-----------------------------------------------------------------------
         * BIOS_BRMDEL macro
         * BURAM #BRMDEL
         * endm
         */
        BRMDEL(0x0005),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_BRMFORMAT - First initializes the directory and then formats the
         * ;                   Back-Up RAM
         * ;
         * ;                  Call BIOS_BRMINIT before calling this function
         * ;
         * ; input:
         * ;   none
         * ;
         * ; returns:
         * ;   cc    Okay, formatted
         * ;   cs    Error, cannot format
         * ;-----------------------------------------------------------------------
         * BIOS_BRMFORMAT macro
         * BURAM #BRMFORMAT
         * endm
         */
        BRMFORMAT(0x0006),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_BRMDIR - Reads directory
         * ;
         * ; input:
         * ;   d1.l  H: number of files to skip when all files cannot be read in one try
         * ;         L: size of directory buffer (# of files that can be read in the
         * ;             directory buffer)
         * ;   a0.l  pointer to parameter (file name) table
         * ;   a1.l  pointer to directory buffer
         * ;
         * ; returns:
         * ;   cc    Okay, complete
         * ;   cs    Full, too much to read into directory buffer
         * ;-----------------------------------------------------------------------
         * BIOS_BRMDIR macro
         * BURAM #BRMDIR
         * endm
         */
        BRMDIR(0x0007),
        /**
         * ;-----------------------------------------------------------------------
         * ; BIOS_BRMVERIFY - Checks data written on Back-Up Ram.
         * ;
         * ; input:
         * ;   a0.l  pointer to parameter (file name) table
         * ;          flag.b       $00: normal
         * ;                       $FF: encoded (with protect function)
         * ;          block_size.w $00: 1 block = $40 bytes
         * ;                       $FF: 1 block = $20 bytes
         * ;   a1.l  pointer to save data
         * ;
         * ; returns:
         * ;   cc    Okay
         * ;   cs    Error
         * ;   d0.w  Error Number
         * ;        -1 : Data does not match
         * ;         0 : File not found
         * ;-----------------------------------------------------------------------
         * BIOS_BRMVERIFY macro
         * BURAM #BRMVERIFY
         * endm
         */
        BRMVERIFY(0x0008);

        public final int code;

        CdBuramFunction(int code) {
            this.code = code;
        }

        @Override
        public int getCode() {
            return code;
        }
    }

    enum CdBiosEntryPoint implements NameCode {
        _ADRERR(0x00005F40),
        _BURAM(0x00005F16),
        _CDBIOS(0x00005F22),
        _CDBOOT(0x00005F1C),
        _CDSTAT(0x00005E80),
        _CHKERR(0x00005F52),
        _CODERR(0x00005F46),
        _DEVERR(0x00005F4C),
        _LEVEL1(0x00005F76),
        _LEVEL2(0x00005F7C),
        _LEVEL3(0x00005F82), //TIMER INTERRUPT
        _LEVEL4(0x00005F88),
        _LEVEL5(0x00005F8E),
        _LEVEL6(0x00005F94),
        _LEVEL7(0x00005F9A),
        _NOCOD0(0x00005F6A),
        _NOCOD1(0x00005F70),
        _SETJMPTBL(0x00005F0A),
        _SPVERR(0x00005F5E),
        _TRACE(0x00005F64),
        _TRAP00(0x00005FA0),
        _TRAP01(0x00005FA6),
        _TRAP02(0x00005FAC),
        _TRAP03(0x00005FB2),
        _TRAP04(0x00005FB8),
        _TRAP05(0x00005FBE),
        _TRAP06(0x00005FC4),
        _TRAP07(0x00005FCA),
        _TRAP08(0x00005FD0),
        _TRAP09(0x00005FD6),
        _TRAP10(0x00005FDC),
        _TRAP11(0x00005FE2),
        _TRAP12(0x00005FE8),
        _TRAP13(0x00005FEE),
        _TRAP14(0x00005FF4),
        _TRAP15(0x00005FFA),
        _TRPERR(0x00005F58),
        _USERCALL0(0x00005F28), //INIT
        _USERCALL1(0x00005F2E), //MAIN
        _USERCALL2(0x00005F34), //VINT
        _USERCALL3(0x00005F3A), //NOT DEFINED
        _USERMODE(0x00005EA6),
        _WAITVSYNC(0x00005F10);

        public final int code;

        CdBiosEntryPoint(int code) {
            this.code = code;
        }

        @Override
        public int getCode() {
            return code;
        }
    }

    enum OtherBiosFun implements NameCode {
        BOOTSTAT(0);

        public final int code;

        OtherBiosFun(int code) {
            this.code = code;
        }

        @Override
        public int getCode() {
            return code;
        }
    }
}
