![Java CI](https://github.com/fedex81/helios/workflows/Java%20CI/badge.svg)

# Helios

A Java-based multi-system emulator.

Initially created as a Sega Megadrive/Genesis and then evolved to emulate
the following systems:
- Sega Megadrive/Genesis
- Sega Master System/Game Gear
- Sega Sg-1000
- Colecovision
- MSX v1
- NES

# How to Run
Requires java 8+ installed.

Get the most recent zip file from the download section,  
for example `helios-19.1108.zip` and extract to a folder.

## Windows
Double click on `launcher.bat`

## Linux
Open a terminal and run:  
`chmod +x launcher.sh`  
`./lanucher.sh`

# Gallery

![](res/site/bad_apple.gif)

[MD Bad Apple Demo](http://www.pouet.net/prod.php?which=60780)

<img src="res/site/super_uwol.png" width="300">    <img src="res/site/astro_force.png" width="300">    <img src="res/site/caos_begins.png" width="300">

[SG-1000 Super Uwol](http://www.mojontwins.com/juegos_mojonos/super-uwol-sg-1000)  
[MSX Caos Begins](http://msxdev.msxblue.com/?page_id=305)    
[SMS Astro Force](http://www.smspower.org/Homebrew/AstroForce-SMS) 




# Credits

Initially based on the [Genefusto](https://github.com/DarkMoe/genefusto) emulator by DarkMoe.

Tony Headford for his m68k core:
- [M68k](https://github.com/tonyheadford/m68k)

J. Sanchez for his Z80 core:
- [Z80](https://github.com/jsanchezv/Z80Core)

S. Dallongeville for his Ym2612 core from the Gens project:
- [YM2612](https://github.com/rofl0r/gens) 

Alexey Khokholov for his Ym2612 core:
- [Nuked-OPN2](https://github.com/nukeykt/Nuked-OPN2)

Chris White for his JavaGear project and in particular:
- [SN76489](http://javagear.sourceforge.net/source-repository.html)
- [SMS VDP](http://javagear.sourceforge.net/source-repository.html)

Tjitze Rienstra for his TMSX project and in particular:
- [AY38910](https://github.com/tjitze/TMSX)
- [TMS9918a VDP](https://github.com/tjitze/TMSX)

Digital Sound Antiques for his Ym2413 core:
- [emu2413](https://github.com/digital-sound-antiques/emu2413)

Andrew Hoffman for his NES emulator:
- [halfnes](https://github.com/andrew-hoffman/halfnes)

Tomek Rekawek for his GameBoy emulator:
- [coffee-gb](https://github.com/trekawek/coffee-gb)

C-BIOS Association for their open source [MSX bios](http://cbios.sourceforge.net/)

# License
Released under GPL v3.0
