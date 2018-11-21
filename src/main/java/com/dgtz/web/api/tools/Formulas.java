package com.dgtz.web.api.tools;

import com.dgtz.db.api.beans.ScreenRotation;
import com.dgtz.mcache.api.factory.Constants;
import com.dgtz.mcache.api.factory.RMemoryAPI;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.function.Predicate;

/**
 * Digital Citizen.
 * User: Sardor Navuzov
 * Date: 3/11/14
 */
public final class Formulas {

    public static final String ANONYMOUS_ABOUT = "Hi, I'm Anonymous. I have no faces, no nationalities and I'm from Nowhere.";
    public static final long DEFAULT_USER = 1497900520940L;
    public static final String DEFAULT_HASH = "8d5825fb638dd34cc6046d755c60c63d";

    public static void cleanCamRotationDubplicats(long idMedia, long now) {
        Predicate<ScreenRotation> scp = sp -> sp.getTime() == now;
        List<String> entities =
                RMemoryAPI.getInstance()
                        .pullListElemFromMemory(Constants.MEDIA_KEY + "properties:rotatime:" + idMedia, 0, -1);
        List<ScreenRotation> lrotation = new Gson().fromJson(entities.toString(),
                new TypeToken<List<ScreenRotation>>() {
                }.getType());
        lrotation.stream().filter(scp)
                .forEach(sr -> RMemoryAPI.getInstance().delFromListElem(Constants.MEDIA_KEY + "properties:rotatime:" + idMedia, sr.toString()));
    }
}
