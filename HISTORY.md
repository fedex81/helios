v005 - 20181010
68k and z80: use delays when handling vdp interrupts
68k and z80: process interrupts when halted
68k: fix f-line and a-line emulation
68k: fix Zero flag when doing add.b or add.w
68k: rebuild lib
emu: fix rom wrapping
emu: init I/O control port to 0 - fixes SGDK
fm: dac 13 bit, avoids clicks
sram: add save/load support
vdp: add support for VRAM 8-bit read


v004 - 20180825
dma: rewrite and extract it to VpdDmaHandler
dma: initial support for DMA transfer rate
emu: add region override
ntsc: add support for V30 and H40
sram: refactor impl
vdp: do not set hblank on !displayEnable
vdp: extract memory handling to a separate class
z80: memory access fixes

v003 - 20180803
68k and z80: better error handling
eeprom: handle eeprom as sram
cart: ignore invalid SRAM setups
dma: refactor
emu: autodetect SSF2/Sega mapper
emu: initial headless support
fm: catch key_on exception
vdp: tweak hLinePassed

v002 - 20180714
- vdp: rebuild interrupt and hvc counter handling
- fm: refactor timers

v001 - 20180618
- first public build