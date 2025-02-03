## 25.0203

32x: reads to cache purge behave like writes
32x: hack for COMM race conditions, fixes Brutal
gg: always 60fps
helios: {pwm, pcm} can be muted
helios: initial TowerOfPower (MegaCd_S32x) support
helios: fix an issue with stuck keys
helios: improve multi-screen behaviour
infra: update to gradle 8
mcd: lots of fixes, still WIP
md: parse supported devices from rom header
md: ignore byte-wide ioWrites on even address, fix read timeControl area
sms: change default z80 SP value

## 24.0720

helios: fix setByteInWordBE
helios: show rom info dialog, show region flag
helios: fix clamp method (!)
sms: fix port 3E bug, fix region override
32x: quirk on rom read 0x1070 when RV=1
32x: implement soft reset
mcd: started work on it, not ready yet

## 23.1207

32x: improve handling of multiple interrupts
32x: add FRT support, disabled by default
32x: add eeprom games, tweak eeprom handling
32x: default to new interrupt controller
32x: uninitialised hInt vector should read 0 (tp)
fm: minor ssg-eg fix, ssg inv bit fix
helios: linux sleeps now considering timer slack
helios: (try to) improve sound on win11
md: add support for psolar mapper and eeprom
md: fix io ports word-wide reads, duplicate lsb to msb; stub serial ports
sh2: fix MOVBM, MOVWM, MOVLM when applied to the same register

## 23.0814

helios: add default keyboard mappings file, defaults to 1 player only
md: add flag to allow sram writes when in read-only mode
svp: fix cart loading as words (16 bits)
z80: tweak zreset, zbusreq handling

## 23.0506

helios: add Sega 32x emulation by merging the helios32x project: https://github.com/fedex81/helios32x
md: improve rom header parsing
md: improve support for roms > 4Mbit with sram

## 22.1205

68k: support cycle table from genPlusGx
68k: fix *bcd, link corner cases
fm: fix sustain level check
i2c: fix read/write when byte sized
md: fix bug on hblank toggling, tweak hint occurrence
md: further tweaks to controller emulation
md: version reg, set expansion unit not connected
ui: add flatlaf and theme selector (dark theme)

## 22.0802

NOTE: requires java 17 or later

helios: migrate to tinylog
helios: require java 17+, update gradle to 7.4.2
helios: add app icons
md: add support for most eeprom types, X24C01 family
md: improve exSSf mapper compat
md: improve 6 button pad detection
ui: store the last opened file, fix the soundEnabled toggle when un/pausing,
    recenter the screen when switching to fullscreen

## 22.0417
68k: handle edge case for int ack
68k: refactor int handling, add processing delay
helios: default to padding roms to a reasonable size
md: tweak cycle counting, should be less resource intensive
md: s/h perf tweak
helios: fix sound enabled ui, minor Federico Berti
md: encode per pixel blanking info in the vdp color output
md: add flag to init RAM with random values, defaults to false
md: allow to busy-wait when set so

## 21.1007
* 68k: update lib, fixes a few bugs
* helios: include jinput libs by default
* helios: auto-hide mouse cursor
* md: fix sprite rendering in interlace mode
* md: IM2 default to show one field only (even), reduces flickering
* md: shadow/highlight fixes and refactor
* md: vdp draw line on end of activeDisplay instead of hblankOn
* svp: handle missing or small rom, fixes a few tests
* svp: read initial pc from rom

## 21.0407
* 68k: overclock support
* helios: init SRAM with 0xFF, according to docs
* helios: init RAM with random values
* helios: async write to file, mitigate sporadic savestate corruption
* helios: add savestate support for sg1000, coleco, msx
* md: select between 3/6 buttons joypad
* md: vdp debug fixes
* md: improve SVP auto-detection
* md: change vdp registers init values, according to docs
* md: hint tweaks
* md: backPlane color issue
* md: further windowPlane fixes
* md: sh tweak
* md: add support for vdp left column blanking
* md: fix fm stereo
* md: partial support for exssf mapper
* md: unused bits on Z80_BUSREQ read, z80 zreset tweak
* md: fix on saving serialized data
* md: limit z80 pc range to RAM space only
* md: fix hscroll cell mode
* msumd: add support for CUE based loop points (MD+ style)
* msumd: support PLAY_OFFSET, volume
* nes: update lib
* tms: fix vdp vram size
* z80: update lib

## 20.1207
* gb: re-enable emulation, sound is much better
* 68k: update lib, subx.l flag fix, movem changes
* jinput: disable polling thread by default
* md: msu-md support
* md: support SEGA SSF system type, force load SSF2 mapper
* md: rework sram detection
* nes: rework audio wrapper, reduce audio popping
* sms: fix savestate handling
* ui: detect user changing screen
* z80: savestate to store regI too

## 20.0816
* 68k: update lib, tas fix
* helios: refactor fm stereo handling
* helios: ignore keys with modifiers when detecting a joypad button press
* helios: remember the folder when opening a resource
* helios: add option to busy-wait instead of sleeping, should help on windows
* md: avoid psg distortion, acquire data more often
* md: add support for SMD interleaved dumps, disabled by default
* md: fix savestate persist
* md: fix ssf2 mapper issue
* ui: scaling for HiDPI displays, add drag and drop - contributed by krlvm

## 20.0704
* gb: disable for now, buggy
* helios: default to stereo sound
* md: add svp support (using notaz implementation)
* md: fix bug on vdp long reads
* md: fix emulation stall on invalid vdp write
* md: fix joypad detection 6btn
* md: fix window plane
* md: fix Z80 SP default value
* md: avoid fm audio clipping
* md: add satCache
* md: add support for soft reset
* md: only stop 68k on DMA MEM_TO_VRAM
* md: savestate support for ssf2/sega mapper registers
* md: support flat ROM mappers > 4MB
* md: initial 128kb VRAM vdp support
* md: support roms that require both ssf2 mapper and sram
* sms: vdp handle tilemap mirroring
* z80: use z80disasm from mame

## 20.0418
* 68k: add delay on z80 rom access
* 68k: basic prefetch impl
* 68k: do not consume an int when the level doesn't change
* helios: add nes emulation based on the halfnes project
* helios: backup sram, do not load empty files
* helios: compute drift for more consistent framerates
* helios: fix recent files handling
* helios: load compressed roms
* helios: mantain 4:3 aspect ratio by default
* md: do not stop 68k on vdp fifo full, but only stop it if it tries to access vdp ports
* md: fix dma fill when len=0
* md: fm add dynamic rate control
* md: improve shadow/highlight
* md: use nuke Ym2612 emulation by default
* md: savestates, use gsh format
* md: support very small roms
* sms: savestate fixes
* ui: hide cursor on screen
* ui: improve multi-screen support
* ui: show info messages

## 19.1108
* helios: update z80 lib
* md: do not reset fm on z80 un-reset
* md: for a given line, window plane hides planeA
* md: improve interlace mode
* md: tweak fm busy time
* md: fix shadowHighlight
* msx: add ASCII16, Konami mappers
* ui: region selector, recent files
* sms: support fm detection + infra
* sms: fix mapper
* sms: hint fix
* sram: do not write an empty file

## 19.0706
* 68k: MOVEM fix pre-decrement long-writes on
* gg: initial support and lots of fixes
* md: fix z80 mem bounds
* md: the path for sram files can now be changed via prop
* md: improve cram viewer
* sms: add support for MEKA rom db
* sms: support pause button
* sms: savestate and quicksave handling
* emu: reset psg on load state
* sms: support backup mapper
* sms: support codem and korea mappers
* sms: add support for V28-V30 video modes
* sms: add EU/US support
* ui: fix keyboard actions

## 19.0430
* sg1000: add inital support + plenty of fixes
* coleco: add initial support + plenty of fixes
* msx: add initial support + plenty of fixes
* sms: add initial support + plenty of fixes
*
* emu: rename project to ... helios
* emu: big infra rewrite to accomodate new systems
* emu: load bios at startup if necessary
*
* 68k: fix movep.l issue
* 68k: fix flags for shift/rotate instructions when shift value = 0
* msx: add ay38910 psg
* msx: add carthw database
* sound: default to 44.1khz
* tms: add mode3


## 19.0217
* 68k: update 68k lib to latest and cleanup
* emu: reduce gc and perf tweaks
* emu: use better timers
* fm: refactor timers, increase precision
* psg: add attenuation property
* ui: handle save state to file
* ui: do not scale on EDT by default
* vdp: draw line on first slot
* vdp: fix dma copy bandwidth
* z80: interrupt work

## 19.0121
* 68k: fix buggy move.l
* 68k: move.l with a pre-decremented address register writes lsw and then msw
* app: fix close
* dma: fix 68kToVram
* emu: work on 68k and z80 timings
* emu: quick save states
* emu: initial savestate support
* emu: add ability to pause the emulation
* fifo: work in progress
* fm: busy flag and refactor
* pad: support for 6 buttons - disabled for now
* region: default to US
* ui: add pause and reset
* vdp: fix mid-frame cram updates
* vdp: fix headless mode
* vdp: fix rendering when sprites are close to the top/bottom
* vdp: vb on when !display
* vdp: dma bandwidth, consider REFRESH slots
* vdp: use slot granularity
* vdp: 8bit vram read
* vdp: hint fixes and tests
* z80: propagate un/reset to FM

## 18.1126
* fm: improve dac
* sound: close resources on exit
* emu: fix close and reload rom
* emu: default screen size scaling to 2x
* ui: add relevant help menus

## 18.1119
* 68k: rework Z-flag calc
* 68k: better handling of STOP, restart on exception
* 68k: RESET instruction should only reset external devices
* build: load version from manifest
* dma: fix fill and copy
* emu: basic cheat codes support
* emu: compute checksum and add autofix checksum
* fm: rework timers
* sram: write to file only on close
* ui: default scale set to 2
* vdp: rework rendering engine
* vdp: sprite masking, handle simple case
* vdp: shadow highlight - needs work
* vdp: refactor timings
* z80: fix reset logic

## v005
20181010  
* 68k and z80: use delays when handling vdp interrupts  
* 68k and z80: process interrupts when halted  
* 68k: fix f-line and a-line emulation  
* 68k: fix Zero flag when doing add.b or add.w  
* 68k: rebuild lib  
* emu: fix rom wrapping  
* emu: init I/O control port to 0 - fixes SGDK  
* fm: dac 13 bit, avoids clicks  
* sram: add save/load support  
* vdp: add support for VRAM 8-bit read  

## v004  
20180825    
* dma: rewrite and extract it to VpdDmaHandler  
* dma: initial support for DMA transfer rate  
* emu: add region override  
* ntsc: add support for V30 and H40  
* sram: refactor impl  
* vdp: do not set hblank on !displayEnable  
* vdp: extract memory handling to a separate class  
* z80: memory access fixes  

## v003  
20180803  
* 68k and z80: better error handling  
* eeprom: handle eeprom as sram  
* cart: ignore invalid SRAM setups  
* dma: refactor  
* emu: autodetect SSF2/Sega mapper  
* emu: initial headless support  
* fm: catch key_on exception  
* vdp: tweak hLinePassed  

## v002  
20180714  
* vdp: rebuild interrupt and hvc counter handling  
* fm: refactor timers  

## v001  
20180618  
* first public build