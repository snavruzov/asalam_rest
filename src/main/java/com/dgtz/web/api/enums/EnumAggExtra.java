package com.dgtz.web.api.enums;

import com.dgtz.db.api.enums.EnumAggregations;

/**
 * Created by sardor on 1/2/14.
 */
public enum EnumAggExtra {
    RECOMMENDED(4),
    MOST_LIKED(1),
    MOST_VIEWED(2),
    MOST_COMMENTED(3),
    LAST(0);


    public long value;

    EnumAggExtra(int value) {
        this.value = value;
    }

    public static EnumAggregations getEnumProprietaryByValue(int value) {
        EnumAggregations aggregations = null;
        if (value == EnumAggExtra.LAST.value) {
            aggregations = EnumAggregations.LAST;
        } else if (value == EnumAggExtra.MOST_COMMENTED.value) {
            aggregations = EnumAggregations.MOST_COMMENTED;
        } else if (value == EnumAggExtra.MOST_VIEWED.value) {
            aggregations = EnumAggregations.MOST_VIEWED;
        } else if (value == EnumAggExtra.MOST_LIKED.value) {
            aggregations = EnumAggregations.MOST_LIKED;
        }

        return aggregations;

    }
}
