package com.dgtz.web.api.enums;

/**
 * Created by sardor on 1/7/14.
 */
public enum EnumProgressStatus {
    OK(0),
    COMPRESSING(1),
    DISABLED(3),
    FILE_FORMAT_ERROR(4),
    ERR_COMPRESS(2);

    public int value;

    EnumProgressStatus(int value) {
        this.value = value;
    }

}
