import uxn.UXN;
import uxn.UXNExecutor;
import uxn.devices.Console;
import uxn.devices.CalendarDev;
import uxn.devices.FileDev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        byte[] rom;
        try {
            rom = Files.readAllBytes(Paths.get(args[0]));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        UXN uxn = new UXN();
        Console console = new Console();
        uxn.setDevice(1, console);
        uxn.setDevice(10, new FileDev());
        uxn.setDevice(11, new FileDev());
        uxn.setDevice(12, new CalendarDev());

        String[] uxnArgs = Arrays.copyOfRange(args, 1, args.length);
        console.queueArgs(uxnArgs);
        System.err.printf("installing rom of size %s\n", rom.length);
        uxn._enable_debug = true;
        System.arraycopy(rom, 0, uxn.memory.getData(), 0x100, rom.length);
        uxn.pc = 0x100;
//        UXNExecutor thread = new UXNExecutor();
//        thread.addUXN(uxn);
//        thread.start();
//        while (true) {
//            try {
//                char ch = (char) System.in.read();
//                console.handleKey(ch);
//                if (!uxn.isRunning()) {break;}
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }
        uxn.run();
        System.out.println();
        System.out.println();
        System.out.println(uxn);
    }
}

