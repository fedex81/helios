REM multi file, compressed iso + wav
FILE "../data/projectcd_minimal.iso.gz" BINARY
    TRACK 01 MODE1/2048
        INDEX 01 00:00:00
FILE "../data/projectcd_minimal_track02.wav" BINARY
    TRACK 02 AUDIO
        INDEX 01 00:02:00
FILE "../data/projectcd_minimal_track03.wav" BINARY
    TRACK 03 AUDIO
        INDEX 01 00:02:00
