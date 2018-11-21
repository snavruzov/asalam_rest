package com.dgtz.web.api.tools;

import com.dgtz.mcache.api.factory.Constants;
import com.dgtz.mcache.api.factory.RMemoryAPI;

/**
 * Created by Sardor Navruzov on 9/15/15.
 * Copyrights Digitizen Co.
 */
public class LanguageDetector {

    public LanguageDetector() {
    }

    public static String detectLang(String code) {

        if (code != null && !code.isEmpty()) {
            code = code.trim();
            code = code.substring(0, 2).toLowerCase();
            boolean doTranslate = RMemoryAPI.getInstance().pullIfSetElem(Constants.TRANSLATION + ":list", code);
            if (!doTranslate) {
                code = "en";
            }
        } else {
            code = "en";
        }

        return code;
    }
}
