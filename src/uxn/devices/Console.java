package uxn.devices;

import uxn.UXN;
import uxn.UXNEvent;

import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.util.LinkedList;
import java.util.Queue;

public class Console extends Device {

    public void handleKey(char ch) {
        uxn.queueEvent(new KeyEvent(ch));
    }

    public void queueArgs(String[] strs) {
        if (strs.length == 0) {return;}
        for (String str : strs) {
            try {
                byte[] bytes = str.getBytes("IBM437");
                for (byte byt : bytes) {
                    uxn.queueEvent(new KeyEvent((char) byt, (byte) 0x02, (byte) 0x01));
                }
                uxn.queueEvent(new KeyEvent(' ', (byte) 0x03, (byte) 0x01));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        uxn.queueEvent(new KeyEvent(' ', (byte) 0x04, (byte) 0x01));
    }

    @Override
    public void write(int address) {
        byte data = uxn.readDev(address);
        int port = address & 0x0F;
        switch (port) {
            case 0x08 -> {
                System.out.write((char) data);
            }
            case 0x09 -> {
                System.err.write((char) data);
            }
        }
    }

    @Override
    public void read(int address) {
        //all values should be set when the KeyEvent happens
    }
}

class KeyEvent implements UXNEvent {
    char ch;
    byte type;
    byte device;

    public KeyEvent(char ch, byte type, byte device) {
        this.ch = ch;
        this.type = type;
        this.device = device;
    }
    public KeyEvent(char ch) {
        this.ch = ch;
        this.type = 0x01; //stdin key
        this.device = 0x10; // this is where the device is on varvara
    }

    @Override
    public void handle(UXN uxn) {
        uxn.pc = (uxn.readDev(0x10) << 8) | uxn.readDev(0x11); //get the vector for PC at the time the event is handled
        uxn.writeDev(device + 0x02, (byte) ch);
        uxn.writeDev(device + 0x07, type);
    }
}