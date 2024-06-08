package uxn;

// Adapted directly from https://git.sr.ht/~rabbits/uxn11

import uxn.devices.Device;

import java.util.LinkedList;
import java.util.Queue;

public class UXN {
    public final MemoryRegion memory = new MemoryRegion();
    protected final Stack rst = new Stack("RST"); // return stack
    protected final Stack wst = new Stack("WST"); // working stack
    public int pc;
    private Stack s;
    private int ins;
    public int cycles = 0;
    private final Device[] devices = new Device[16];
    private final byte[] deviceMemory = new byte[256];
    private boolean running = false;
    private final Queue<Integer> vectorQueue = new LinkedList<>();

    public UXN() {
        setDevice(0, new SystemDevice());
    }

    public void queueVector(int address) {
        vectorQueue.add(address);
    }

    public void setDevice(int index, Device device) {
        this.devices[index] = device;
        device.setContainer(this);
    }

    public void deo(int address, byte data) {
        deviceMemory[address] = data;
        int dev = (address & 0xF0) >> 4;
        Device device = this.devices[dev];
        if (device != null) {
            device.write(address);
        }
    }

    public void writeDev(int address, byte data) {
        address %= 256;
        deviceMemory[address] = data;
    }

    public byte readDev(int address) {
        address %= 256;
        return deviceMemory[address];
    }

    public byte dei(int address) {
        int dev = (address & 0xF0) >> 4;
        Device device = this.devices[dev];
        if (device != null) {
            device.read(address);
        }
        return deviceMemory[address];
    }


    private void set(int x, int y, int x2, int y2) {
        if ((ins & 0x20) > 0) {
            x = x2;
            y = y2;
        }
        s.shift((ins & 0x80) > 0 ? x + y : y);
    }
    private void set(int x, int y) {
        // y is how much this instruction moves the stack pointer by default
        // x is how much this changes when in "keep" mode
        set(x, y, x * 2, y * 2);
    }
    private void flip() {
        if (s == rst) {
            s = wst;
            return;
        }
        s = rst;
    }

    // execute 1 instruction
    private void step() {
        cycles++;
        if (pc == 0) {
            running = false;
            return;
        }
        int r, t, n, l;
        ins = memory.readByte(pc++);
        pc = pc % 0xFFFF;
        s = (ins & 0x40) > 0 ? rst : wst;
        boolean mode2 = (ins & 0x20) > 0;
        switch(ins & 0x3f) {
        case 0x00: case 0x20:
            switch(ins) {
            case 0x00: // BRK
                running = false;
                return;
            case 0x20: // JCI
                t = s.getT(false);
                s.shift(-1);
                if (t == 0) {
                    pc += 2;
                    break;
                } // intentional fall through
            case 0x40: // JMI
                // index into ram (replaces *rr)
                pc += 2 + (short)memory.readShort(pc);
                break;
            case 0x60: // JSI
                int rr = pc;
                s.shift(2);
                pc += 2;
                s.setT(true, pc);
                pc += memory.readShort(rr);
                break;
            case 0x80: case 0xc0: // LIT
            case 0xa0: case 0xe0: // LIT2
                set(0, 1);
                s.setT(mode2, memory.read(mode2, pc++));
                if (mode2) {
                    pc++;
                }
                break;
            } break;
        /* ALU */
        case 0x01: case 0x21: // INC
            t = s.getT(mode2);
            set(1, 0);
            s.setT(mode2, t + 1);
            break;
        case 0x02: case 0x22: // POP
            set(1, -1);
            break;
        case 0x03: case 0x23: // NIP
            t = s.getT(mode2);
            set(2, -1);
            s.setT(mode2, t);
            break;
        case 0x04: case 0x24: // SWP
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, 0);
            s.setT(mode2, n);
            s.setN(mode2, t);
            break;
        case 0x05: case 0x25: // ROT
            t = s.getT(mode2);
            n = s.getN(mode2);
            l = s.getL(mode2);
            set(3, 0);
            s.setT(mode2, l);
            s.setN(mode2, t);
            s.setL(mode2, n);
            break;
        case 0x06: case 0x26: // DUP
            t = s.getT(mode2);
            set(1, 1);
            s.setT(mode2, t);
            s.setN(mode2, t);
            break;
        case 0x07: case 0x27: // OVR
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, 1);
            s.setT(mode2, n);
            s.setN(mode2, t);
            s.setL(mode2, n);
            break;
        case 0x08: case 0x28: // EQU
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1, 4, -3);
            s.setT(false, n == t ? 1 : 0);
            break;
        case 0x09: case 0x29: // NEQ
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2,-1,4,-3);
            s.setT(false, n != t ? 1 : 0);
            break;
        case 0x0a: case 0x2a: // GTH
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1, 4, -3);
            s.setT(false, n > t ? 1 : 0);
            break;
        case 0x0b: case 0x2b: // LTH
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1, 4, -3);
            s.setT(false, n < t ? 1 : 0);
            break;
        case 0x0c: // JMP
            t = s.getT(false);
            set(1, -1);
            pc += (byte) t; // hopefully this will handle signed?
            break;
        case 0x2c: // JMP2
            t = s.getT(true);
            set(1, -1); // 2, -2 is implied
            pc = t;
            break;
        case 0x0d: // JCN
            t = s.getT(false);
            n = s.getN(false);
            set(2, -2);
            if (n > 0) pc += (byte) t;
            break;
        case 0x2d: // JCN2
            t = s.getT(true);
            n = s.getL(false);
            set(2, -2, 3, -3);
            if (n > 0) pc = t;
            break;
        case 0x0e: // JSR
            t = s.getT(false);
            set(1, -1);
            flip();
            s.shift(2);
            s.setT(true, pc);
            pc += (byte) t;
            break;
        case 0x2e: // JSR2
            t = s.getT(true);
            set(1, -1); // 2, -2 is implied
            flip();
            s.shift(2);
            s.setT(true, pc);
            pc = t;
            break;
        case 0x0f: case 0x2f: // STH
            t = s.getT(mode2);
            set(1, -1);
            flip();
            s.shift(mode2 ? 2 : 1);
            s.setT(mode2, t);
            break;
        case 0x10: case 0x30: // LDZ
            t = s.getT(false);
            set(1, 0, 1, 1);
            s.setT(mode2, memory.readZP(mode2, t));
            break;
        case 0x11: // STZ
            t = s.getT(false);
            n = s.getN(mode2);
            set(2, -2, 3, -3);
            memory.writeZP(mode2, t, n);
            break;
        case 0x31: // STZ2
            t = s.getT(false);
            n = s.getH2();
            set(2, -2, 3, -3);
            memory.writeZP(mode2, t, n);
            break;
        case 0x12: case 0x32: // LDR
            t = s.getT(false);
            set(1, 0, 1, 1);
                r = pc + (byte) t; // should be signed
                s.setT(mode2, memory.read(mode2, r));
            break;
        case 0x13: // STR
            t = s.getT(false);
            n = s.getN(false);
            set(2, -2, 3, -3);
            r = pc + (byte) t;
            memory.writeByte(r, (byte) n);
            break;
        case 0x33: // STR2
            t = s.getT(false);
            n = s.getH2();
            set(2, -2, 3, -3);
            r = pc + (byte) t;
            memory.writeShort(r, (short) n);
            break;
        case 0x14: case 0x34: // LDA
            t = s.getT(true);
            set(2, -1, 2, 0);
            s.setT(mode2, memory.read(mode2, t));
            break;
        case 0x15: // STA
            t = s.getT(true);
            n = s.getL(false);
            set(3, -3, 4, -4);
            memory.write(false, t, n);
            break;
        case 0x35: // STA2
            t = s.getT(true);
            n = s.getN(true);
            set(3, -3, 4, -4);
            memory.write(mode2, t, n);
            break;
        case 0x16: case 0x36: // DEI
            t = s.getT(false);
            set(1, 0, 1, 1);
            if (mode2) {
                s.setN(false, dei(t));
                s.setT(false, dei((t + 1) % 256));
            } else {
                s.setT(false, dei(t));
            }
            break;
        case 0x17: case 0x37: // DEO
            t = s.getT(false);
            n = s.getN(false);
            l = s.getL(false);
            set(2, -2, 3, -3);
            if (mode2) {
                deo(t, (byte) l);
                deo((t + 1) % 256, (byte) n);
            } else {
                deo(t, (byte) n);
            }
            break;
        case 0x18: case 0x38: // ADD
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1);
            s.setT(mode2, n + t);
            break;
        case 0x19: case 0x39: // SUB
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1);
            s.setT(mode2, n - t);
            break;
        case 0x1a: case 0x3a: // MUL
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1);
            s.setT(mode2, n * t);
            break;
        case 0x1b: case 0x3b: // DIV
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1);
            s.setT(mode2, t != 0 ? n / t : 0);
            break;
        case 0x1c: case 0x3c: // AND
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1);
            s.setT(mode2, n & t);
            break;
        case 0x1d: case 0x3d: // ORA
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1);
            s.setT(mode2, n | t);
            break;
        case 0x1e: case 0x3e: // EOR
            t = s.getT(mode2);
            n = s.getN(mode2);
            set(2, -1);
            s.setT(mode2, n ^ t);
            break;
        case 0x1f: // SFT
            t = s.getT(false);
            n = s.getN(false);
            set(2, -1);
            s.setT(false, n >> (t & 0xf) << (t >> 4));
            break;
        case 0x3f: // SFT2
            t = s.getT(false);
            n = s.getH2();
            set(2, -1, 3, -1);
            s.setT(true, n >> (t & 0xf) << (t >> 4));
            break;
        }
        pc = pc % 0xFFFF;
    }
    public void run() {
        running = true;
        while (running) {
            step();
        }
    }

    public void runLimited(int limit) {
        for (int i = 0; i < limit; i++) {
            if (!running) {
                // check for vector in the queue
                if (vectorQueue.isEmpty()) return;
                pc = vectorQueue.remove();
                running = true;
            }
            step();
        }
    }
}

class SystemDevice extends Device {
    @Override
    public void write(int address) {
        int port = address & 0x0F;
        switch (port) {
        case 0x00: case 0x01: // Reset vector*
            break;
        case 0x02: case 0x03: // expansion*
            break;
        case 0x04:
            uxn.wst.setPtr(uxn.readDev(address)); break;
        case 0x05:
            uxn.rst.setPtr(uxn.readDev(address)); break;
        case 0x08: case 0x09: // red*
        case 0x0a: case 0x0b: // green*
        case 0x0c: case 0x0d: // blue*
            // TODO
            break;
        case 0x0e: // debug
            int data = uxn.readDev(address);
            if (data == 0x01) {
                System.out.println(uxn.wst);
                System.out.println(uxn.rst);
            }
            break;
        case 0x0f: // state
            break;
        }
    }

    @Override
    public void read(int address) {
        int port = address & 0x0F;
        switch (port) {
        case 0x00: case 0x01: // Reset vector*
            break;
        case 0x02: case 0x03: // expansion*
            break;
        case 0x04:
            uxn.writeDev(address, (byte)uxn.wst.getPtr()); break;
        case 0x05:
            uxn.writeDev(address, (byte)uxn.rst.getPtr()); break;
        case 0x08: case 0x09: // red*
        case 0x0a: case 0x0b: // green*
        case 0x0c: case 0x0d: // blue*
            // TODO
            break;
        case 0x0e: // debug
            break;
        case 0x0f: // state
            break;
        }
    }
}