package com.dgtz.web.api;

import com.dgtz.api.constants.Media;
import com.dgtz.api.contents.UsersShelf;
import com.dgtz.db.api.enums.EnumSQLErrors;
import com.mashape.unirest.http.Unirest;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by sardor on 1/3/14.
 */

@Path("/tools")
public class UtilsController extends Resource {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(UtilsController.class);

    public UtilsController() {
    }

    @GET
    @Produces("application/json")
    @Path("/urls/list")
    @Context
    public Response getMediaByStatistics() {

        List<String> urlz = new ArrayList<String>();
        urlz.add("video");
        urlz.add(Media.PATH_SETTINGS.getVideoUrl());
        //Media.PATH_SETTINGS mediaShelf = new MediaShelf();

        return createResponse(urlz);
    }

    @GET
    @Produces("application/json")
    @Path("/alarm/off")
    @Context
    public Response shutDownAllConnections(
            @HeaderParam("X-Secret") String secret) {

        log.debug("Secret value {}", secret);
        try {
            if (secret != null && secret.contains("ubgjgjnfv84637&")) {
                log.debug("Shutting down connections...");
                Unirest.shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        JSONObject json = new JSONObject();
        json.put("result", "Wrong URL");
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/bug/track")
    @Context
    public Response sendAppBugTracker(@FormParam("ver") String ver, @FormParam("msg") String text) {

        UsersShelf usersShelf = new UsersShelf();
        EnumSQLErrors sqlErrors = EnumSQLErrors.UNKNOWN_SQL_ERROR;
        if (ver != null && (text != null && !text.isEmpty())) {
            sqlErrors = usersShelf.publishBugTracker(ver, text);
        }
        return createResponse(sqlErrors);
    }
}
