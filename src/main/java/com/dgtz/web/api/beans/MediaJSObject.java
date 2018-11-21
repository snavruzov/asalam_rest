package com.dgtz.web.api.beans;

import java.io.Serializable;

/**
 * Created by root on 2/3/14.
 */
public class MediaJSObject implements Serializable {

    private static final long serialVersionUID = 1L;

    private MediaTechFormats format;

    public MediaTechFormats getFormat() {
        return format;
    }

    public void setFormat(MediaTechFormats format) {
        this.format = format;
    }
}
