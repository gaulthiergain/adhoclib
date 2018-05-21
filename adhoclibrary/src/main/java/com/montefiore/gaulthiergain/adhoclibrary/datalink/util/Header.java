package com.montefiore.gaulthiergain.adhoclibrary.datalink.util;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.io.Serializable;

/**
 * Created by gaulthiergain on 17/11/17.
 */

@JsonTypeName("header")
public class Header implements Serializable {
    protected int type;
    protected String label;
    protected String name;

    private String address;
    private String mac;

    private int deviceType;

    public Header() {

    }

    public Header(int type, String label, String name) {
        this.type = type;
        this.label = label;
        this.name = name;
    }

    public Header(int type, String mac, String label, String name) {
        this.type = type;
        this.mac = mac;
        this.label = label;
        this.name = name;
    }

    public Header(int type, String address, String mac, String label, String name) {
        this.type = type;
        this.address = address;
        this.mac = mac;
        this.label = label;
        this.name = name;
    }

    public Header(int type, String mac, String label, String name, int deviceType) {
        this.type = type;
        this.mac = mac;
        this.label = label;
        this.name = name;
        this.deviceType = deviceType;
    }

    public int getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getMac() {
        return mac;
    }

    public int getDeviceType() {
        return deviceType;
    }

    @Override
    public String toString() {
        return "Header{" +
                "type=" + type +
                ", label='" + label + '\'' +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", mac='" + mac + '\'' +
                '}';
    }

    public void setType(int type) {
        this.type = type;
    }
}