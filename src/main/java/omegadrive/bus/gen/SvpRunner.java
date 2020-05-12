package omegadrive.bus.gen;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static omegadrive.bus.gen.SvpRunner.Result.FAILURE;
import static omegadrive.bus.gen.SvpRunner.Result.SUCCESS;

/**
 * SvpRunner
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class SvpRunner {

    static Path folder = Paths.get("test_roms", "svp");
    static Table<String, Result, Integer> results = HashBasedTable.create();

    static {
        results.put("test_pram_access.bin", SUCCESS, 0x5fb);
        results.put("test_pram_access.bin", FAILURE, 0x5fa);
        results.put("test_loads.bin", SUCCESS, 0x5b9);
        results.put("test_loads.bin", FAILURE, 0x5b8);
    }

    public static void main(String[] args) throws IOException {
        List<Path> pathList = Files.list(folder).filter(p -> p.toString().endsWith(".bin")).collect(Collectors.toList());

        pathList.forEach(SvpRunner::launch);
    }

    private static void launch(Path file) {
        System.out.println(file.toAbsolutePath().toString());
        try {
            GenesisBusProvider busProvider = GenesisBusProvider.createBus();
            IMemoryProvider mem = MemoryProvider.createGenesisInstance();
            mem.setRomData(Util.toUnsignedIntArray(Files.readAllBytes(file)));
            int pc = mem.getRomSize() < 0x400 ? 0 : 0x400;
//            SvpMapper.SVP_ROM_START_ADDRESS_BYTE = pc << 1; //TODO
            busProvider.attachDevice(mem);
            SvpMapper mapper = new SvpMapper((RomMapper) busProvider, mem);
            SvpMapper.svp.rPC.setH(pc);
            int lastPc = 0, nextPc = 0;
            int totalWords = mem.getRomSize() >> 1;

            int success = Optional.ofNullable(results.row(file.getFileName().toString())).
                    map(o -> o.get(SUCCESS)).orElse(-1);
            int failure = Optional.ofNullable(results.row(file.getFileName().toString())).
                    map(o -> o.get(FAILURE)).orElse(-1);
            do {
                lastPc = nextPc;
                SvpMapper.svp.ssp1601_run(1);
                nextPc = SvpMapper.svp.rPC.h;
//                System.out.println(Integer.toHexString(nextPc));
                if (nextPc == success || nextPc == failure) {
                    break;
                }
            } while (nextPc != lastPc && nextPc < totalWords);
            System.out.println(file.toAbsolutePath().toString());
            if (nextPc == success) {
                System.out.println(SUCCESS + ": " + nextPc);
            } else if (nextPc == failure) {
                System.out.println(FAILURE + ": " + nextPc);
            } else if (nextPc == totalWords) {
                System.out.println("EOF");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    enum Result {SUCCESS, FAILURE}
}
