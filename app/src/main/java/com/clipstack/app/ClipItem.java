package com.clipstack.app;

public class ClipItem {
    public long    id;
    public String  text;
    public long    date;
    public boolean starred;
    public String  sourcePackage;

    public ClipItem() {}
    public ClipItem(String text, long date, boolean starred, String sourcePackage) {
        this.text          = text;
        this.date          = date;
        this.starred       = starred;
        this.sourcePackage = sourcePackage != null ? sourcePackage : "";
    }
}
