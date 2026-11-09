REM multi file, compressed iso + ogg
FILE "../data/projectcd_minimal.iso.gz" BINARY
    TRACK 01 MODE1/2048
        INDEX 01 00:00:00
FILE "../data/projectcd_minimal_track02.ogg" BINARY
    TRACK 02 AUDIO
        INDEX 01 00:02:00
FILE "../data/projectcd_minimal_track03.ogg" BINARY
    TRACK 03 AUDIO
        INDEX 01 00:02:00
