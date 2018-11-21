package com.dgtz.web.api.beans;

/**
 * Created by root on 2/3/14.
 */
public class MediaTechFormats {

    private String format_name;
    private String start_time;
    private double duration;
    private long size;
    private long bit_rate;

    public String getFormat_name() {
        return format_name;
    }

    public void setFormat_name(String format_name) {
        this.format_name = format_name;
    }

    public String getStart_time() {
        return start_time;
    }

    public void setStart_time(String start_time) {
        this.start_time = start_time;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getBit_rate() {
        return bit_rate;
    }

    public void setBit_rate(long bit_rate) {
        this.bit_rate = bit_rate;
    }
}
