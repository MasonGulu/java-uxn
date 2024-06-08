import uxn.UXN;
import uxn.UXNExecutor;
import uxn.devices.Console;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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
        System.arraycopy(rom, 0, uxn.memory.getData(), 0x100, rom.length);
        uxn.queueVector(0x0100);
        UXNExecutor thread = new UXNExecutor();
        thread.addUXN(uxn);
        thread.start();
        while (true) {
            try {
                char ch = (char) System.in.read();
                console.handle(uxn, ch);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

