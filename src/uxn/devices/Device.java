package uxn.devices;

import uxn.UXN;

public abstract class Device {
    protected UXN uxn;
    public void setContainer(UXN uxn) {
        this.uxn = uxn;
    }
    public abstract void write(int address);
    public abstract void read(int address);
}
