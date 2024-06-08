package uxn.devices;

import uxn.UXN;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public class Console extends Device {
    private final Queue<Character> buffer = new LinkedList<>();

    public void handle(UXN u, char ch) {
        u.queueVector((uxn.readDev(0x10) << 8) | uxn.readDev(0x11));
        buffer.add(ch);
    }

    @Override
    public void write(int address) {
        byte data = uxn.readDev(address);
        int port = address & 0x0F;
        if (port == 0x08) { // write
            System.out.write((char) data);
        }
    }

    @Override
    public void read(int address) {
        int port = address & 0x0F;
        if (port == 0x02) {
            char ch = buffer.remove();
            uxn.writeDev(address, (byte) ch);
        }
    }
}
