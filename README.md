![Java CI](https://github.com/fedex81/helios/workflows/Java%20CI/badge.svg)
[![CodeQL](https://github.com/fedex81/helios/actions/workflows/codeql.yml/badge.svg)](https://github.com/fedex81/helios/actions/workflows/codeql.yml)

# Helios

A Java-based multi-system emulator mainly focused on Sega 16-bit systems.

Initially created as a Sega Megadrive/Genesis emulator and then evolved to emulate
the following systems:
- Sega Megadrive/32X
- Sega Master System/Game Gear
- Sega Sg-1000
- Colecovision
- MSX v1
- NES

## Performance
 
Sega 32x: The target is a modern mobile cpu capable of boosting ~4Ghz 
(ie. [AMD Ryzen 5 PRO 5650U](https://www.amd.com/en/products/apu/amd-ryzen-5-pro-5650u)),   
this should allow perf close to 60fps for most titles, YMMV.

# How to Run
Requires java 17+ installed.

Get the most recent zip file from the download section,  
for example `helios-19.1108.zip` and extract to a folder.

## Windows
Double click on `launcher.bat`

## Linux
Open a terminal and run:  
`chmod +x launcher.sh`  
`./lanucher.sh`

# Gallery

<p align="center">
<img src="res/site/bad_apple.gif" width="25%">
<img src="res/site/super_uwol.png" width="20%">    
<img src="res/site/astro_force.png" width="20%">    
<img src="res/site/caos_begins.png" width="20%">   
<img src="res/site/bobl.png" width="19%">
</p>

[MD Bad Apple Demo](http://www.pouet.net/prod.php?which=60780)

[SG-1000 Super Uwol](http://www.mojontwins.com/juegos_mojonos/super-uwol-sg-1000)  
[MSX Caos Begins](http://msxdev.msxblue.com/?page_id=305)    
[SMS Astro Force](http://www.smspower.org/Homebrew/AstroForce-SMS)     
[NES Böbl](http://forums.nesdev.com/viewtopic.php?f=35&t=19718)




# Credits

Initially based on the [Genefusto](https://github.com/DarkMoe/genefusto) emulator by DarkMoe.

Tony Headford, m68k core: [m68k](https://github.com/tonyheadford/m68k)

J. Sanchez, Z80 core: [Z80](https://github.com/jsanchezv/Z80Core)

Alexey Khokholov, Ym2612 core: [Nuked-OPN2](https://github.com/nukeykt/Nuked-OPN2)

Chris White, JavaGear project and in particular:
- [SN76489](http://javagear.sourceforge.net/source-repository.html)
- [SMS VDP](http://javagear.sourceforge.net/source-repository.html)

Tjitze Rienstra, TMSX project and in particular:
- [AY38910](https://github.com/tjitze/TMSX)
- [TMS9918a VDP](https://github.com/tjitze/TMSX)

notaz, SVP core: [Picodrive svp](https://notaz.gp2x.de/svp.php)

Digital Sound Antiques, Ym2413 core: [emu2413](https://github.com/digital-sound-antiques/emu2413)

Andrew Hoffman, NES emulator: [halfnes](https://github.com/andrew-hoffman/halfnes)

Hitachi SH2 cpu emulator adapted from [esoteric_emu](https://github.com/fedex81/esoteric_emu)'s
SH4 implementation

Tomek Rekawek, GameBoy emulator: [coffee-gb](https://github.com/trekawek/coffee-gb)

C-BIOS Association for their open source [MSX bios](http://cbios.sourceforge.net/)

Devster for their Freeware 32x BIOS files [Devster](https://devster.monkeeh.com/segapage.html)

# License
Released under GPL v3.0
