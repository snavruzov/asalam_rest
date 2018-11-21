package com.dgtz.web.api;

import com.brocast.riak.api.beans.DcCommentsEntity;
import com.brocast.riak.api.beans.DcMediaEntity;
import com.brocast.riak.api.beans.DcUsersEntity;
import com.dgtz.api.beans.LiveMediaInfo;
import com.dgtz.api.contents.LiveShelf;
import com.dgtz.api.contents.MediaShelf;
import com.dgtz.api.contents.UsersShelf;
import com.dgtz.api.enums.EnumErrors;
import com.dgtz.api.utils.TagTokenizer;
import com.dgtz.db.api.enums.EnumSQLErrors;
import com.dgtz.mcache.api.factory.Constants;
import com.dgtz.mcache.api.factory.RMemoryAPI;
import com.dgtz.web.api.tools.SpecificGeoLocation;
import com.dgtz.web.api.tools.UserStatisticParser;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

@Path("/up")
public class UpController extends Resource {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(UpController.class);


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/video/publish")
    public Response publishVideo(@FormParam("title") String title,
                                 @FormParam("description") String description,
                                 @FormParam("idCategory") int idCateg,
                                 @FormParam("idch") long idChannel,
                                 @FormParam("tags") String tags,
                                 @FormParam("idHash") String idHash,
                                 @FormParam("idMedia") final long idMedia,
                                 @FormParam("location") String location,
                                 @FormParam("dname") String dname,
                                 @FormParam("time") String time,
                                 @HeaderParam("X-Forwarded-For") String IP,
                                 @HeaderParam("User-Agent") String agent,
                                 @HeaderParam("Accept-Language") String lang) throws IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        LiveMediaInfo mediaProperties = new LiveMediaInfo();
        MediaShelf mediaShelf = new MediaShelf(lang);
        IP = IP != null ? IP.split(",")[0] : null;


        Pattern alphanumeric = Pattern.compile("^\\s+$");
        boolean isBlockedIP = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:ip", IP + "");

        if (title == null || isBlockedIP || title.isEmpty()
                || alphanumeric.matcher(title).matches() || title.length() < 3 || title.length() > 500
                || idHash == null || idHash.isEmpty() || idMedia <= 0 || description.length() > 1000) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }


        String mimeType = RMemoryAPI.getInstance().pullHashFromMemory(Constants.MEDIA_KEY + "properties:" + idMedia, "type");
        if (mimeType == null) {
            log.error("FILE EXTENSION ERROR FILE IS NOT VIDEO");
            json.put("error", EnumErrors.PUBLISH_ERR);
            return createResponse(json);
        }
        log.debug("title {}, idHash {}", title, idHash);

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user != null && !RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", user.getIdUser() + "") && user.idUser != 0) {
            try {

                if (dname != null && !dname.isEmpty()) {
                    mediaShelf.saveMediaDeviceVendor(idMedia, dname);
                }

                Long idUser = user.getIdUser();

                log.debug("TAG: {} IDUSER: {}", tags, idUser);

                log.debug("LOCATION: {} IP: {}", location, IP);

                mediaProperties.setIdChannel(idChannel);
                mediaProperties.setTitle(title);
                mediaProperties.setDescription(description);
                mediaProperties.setTags(tags == null ? "" : tags);
                mediaProperties.setIdCateg(1l); // video
                mediaProperties.setIdMedia(idMedia);
                mediaProperties.setIdHash(idHash);
                mediaProperties.setIdUser(idUser);
                mediaProperties.setUser(user);
                mediaProperties.setMethod("upload");

                log.debug("TRYING GET VIDEO LOCATIONS: IP {}, GPS {}", IP, location);
                int platformType = UserStatisticParser.parseAndSave(idMedia, user.getIdUser(), 21, IP, dname, agent);

                if (platformType == 2) {
                    String header = SpecificGeoLocation.geoLocationByWEBIP(IP);
                    if (header.contains("@") && (location == null || location.isEmpty() || location.equals("0.0 0.0"))) {
                        location = (header.replace("@", " "));
                    } else if (header.contains("@")) {
                        location = (SpecificGeoLocation.compareGeoDistance(location, header.replace("@", " ")));
                    }
                }

                if (location != null && !location.isEmpty() && !location.equals("0.0 0.0")) {
                    mediaProperties.setLatlong(location);
                    location = mediaShelf.modifyLocation(location, idMedia);
                    mediaProperties.setLocation(location);
                } else {
                    mediaProperties.setLocation(location);
                }

                String langCode = new MediaShelf().getLanguageCodeByLocation(IP, location);

                mediaProperties.setPlatformType(platformType);
                mediaProperties.setLang(langCode);

                log.debug("MediaUpload props {}", mediaProperties.toString());
                errors = mediaShelf.saveVideoProperties(mediaProperties, "upload");

            } catch (Exception ex) {
                log.error("UNKNOWN_ERROR IN WEB API: ", ex);
                errors = EnumErrors.UNKNOWN_ERROR;
            }
        } else {
            errors = EnumErrors.NULL_FIELD_ERROR;
        }

        log.debug("VIDEO UPLOAD: {}", errors.toString());
        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/post/text/publish")
    public Response publishVideo(@FormParam("idch") long idChannel,
                                 @FormParam("body") String textbody,
                                 @FormParam("idHash") String idHash,
                                 @HeaderParam("X-Forwarded-For") String IP,
                                 @HeaderParam("User-Agent") String agent,
                                 @HeaderParam("Accept-Language") String lang) throws IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        LiveMediaInfo mediaProperties = new LiveMediaInfo();
        MediaShelf mediaShelf = new MediaShelf();
        LiveShelf liveShelf = new LiveShelf();
        IP = IP != null ? IP.split(",")[0] : null;


        boolean isBlockedIP = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:ip", IP + "");

        if (isBlockedIP || idHash == null || idHash.isEmpty() || textbody.length() > 5000) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }


        log.debug("channel {}, idHash {}", idChannel, idHash);

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user != null && !RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", user.getIdUser() + "") && user.idUser != 0) {
            try {

                Long idUser = user.getIdUser();
                Long idMedia = liveShelf.extractUniqueIdForLive();
                mediaProperties.setIdChannel(idChannel);
                mediaProperties.setTitle("");
                mediaProperties.setDescription(textbody);
                mediaProperties.setTags("");
                mediaProperties.setIdCateg(2l); // text
                mediaProperties.setIdMedia(idMedia);
                mediaProperties.setIdHash(idHash);
                mediaProperties.setIdUser(idUser);
                mediaProperties.setUser(user);

                log.debug("MediaUpload props {}", mediaProperties.toString());
                errors = mediaShelf.saveTextPostProperties(mediaProperties, "upload");

            } catch (Exception ex) {
                log.error("UNKNOWN_ERROR IN WEB API: ", ex);
                errors = EnumErrors.UNKNOWN_ERROR;
            }
        } else {
            errors = EnumErrors.NULL_FIELD_ERROR;
        }

        log.debug("TEXT UPLOAD: {}", errors.toString());
        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/up/edit")
    public Response editMediaByUpload(
            @FormParam("idMedia") long idMedia,
            @FormParam("idUser") long idUser,
            @FormParam("title") String title,
            @FormParam("idCategory") int idCategory,
            @FormParam("description") String description,
            @FormParam("tags") String tags,
            @FormParam("token") String token,
            @FormParam("idChannel") long idChannel,
            @HeaderParam("Accept-Language") String lang) throws IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        DcMediaEntity mediaProperties = new DcMediaEntity();
        MediaShelf mediaShelf = new MediaShelf();
        Pattern alphanumeric = Pattern.compile("^\\s+$");

        String mToken = RMemoryAPI.getInstance().pullHashFromMemory(Constants.MEDIA_KEY + "properties:" + idMedia, "token");

        if (!mToken.equals(token) || title == null || title.isEmpty() || alphanumeric.matcher(title).matches() || title.length() < 3 || title.length() > 500
                || description == null || description.length() > 500
                || token == null || token.isEmpty() || idMedia == 0 || idCategory == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            Set<String> tagList = null;

            if (tags == null || tags.isEmpty()) {
                tags = "";
            }

            tagList = TagTokenizer.normilizeTag(tags);
            log.debug(tagList.toString());

            mediaProperties.setIdMedia(idMedia);
            mediaProperties.setTitle(title);
            mediaProperties.setDescription(description);
            mediaProperties.setTags(tagList);
            mediaProperties.setIdChannel(idChannel);

            log.debug(mediaProperties.toString());


            if (idUser >= 0) {

                UsersShelf usersShelf = new UsersShelf();

                if (idChannel != 0 && idUser != 0 && !usersShelf.amIjoinedToChannel(idUser, idChannel)) {
                    json.put("error", EnumErrors.NO_CHANNEL_ERROR);
                    return createResponse(json);
                }
                mediaProperties.setIdUser(idUser);
                mediaShelf.updateContentInfo(mediaProperties);

            } else {
                errors = EnumErrors.INVALID_HASH;
            }


        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/comment/publish")
    public Response publishCommentaries(@FormParam("idMedia") long idMedia,
                                        @FormParam("idHash") String idHash,
                                        @FormParam("text") String text,
                                        @FormParam("tp") int commentType,
                                        @FormParam("dr") long duration,
                                        @FormParam("idcmt") long idComment,
                                        @FormParam("playerDuration") long timeFrac,
                                        @HeaderParam("Accept-Language") String lang
    ) throws IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();

        if (idMedia == 0 || idHash == null || idHash.isEmpty() || text == null || text.isEmpty() || text.length() > 500) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        DcCommentsEntity commentsEntity = new DcCommentsEntity();
        MediaShelf mediaShelf = new MediaShelf();

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            DcMediaEntity media = mediaShelf.retrieveMediaByIdValue(idMedia);

            if (user != null && user.getIdUser() != 0
                    && media != null && (media.getProgress() == 0 || media.getProgress() == 1)) {

                if(timeFrac>media.duration || timeFrac<0){
                    errors = EnumErrors.WRONG_DURATION;
                } else {
                /*check for blocked user*/
                    if (RMemoryAPI.getInstance().pullIfSetElem("dc_users:comment:blocked:users:" + media.getIdUser()
                            , user.getIdUser() + "")) {
                        json.put("error", EnumErrors.DENIED_TO_COMMENT);
                        return createResponse(json);
                    }
                    String url = "";
                    if (commentType == 1) {
                        url = RMemoryAPI.getInstance().pullElemFromMemory(Constants.COMMENT_KEY + "voice-url:" + user.getIdUser() + idMedia);
                    }
                    commentsEntity.idUser = (user.getIdUser());
                    commentsEntity.idComment = idComment;
                    commentsEntity.idMedia = (idMedia);
                    commentsEntity.text = (text);
                    commentsEntity.commentType = (commentType); //0 - common, 1 - voice, 2 - System
                    commentsEntity.url = (url);
                    commentsEntity.duration = (duration);

                    idComment = new UsersShelf().insertFreshComments(commentsEntity, media.idUser, timeFrac);
                    json.put("id_comment", idComment);
                }

            } else {
                errors = EnumErrors.WRONG_ID_VALUE;
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/encode/done")
    public Response encodingDoneProcess(@QueryParam("status") String status,
                                        @QueryParam("cuid") String cuid,
                                        @HeaderParam("Accept-Language") String lang) throws IOException {

        JSONObject json = new JSONObject();
        log.debug("Encding done status {}/{}",status, cuid);

        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/encode/done_error")
    public Response encodingComErrorProcess(@FormParam("json") String jsn,
                                            @HeaderParam("Accept-Language") String lang) throws IOException {

        MediaShelf mediaShelf = new MediaShelf();
        mediaShelf.updateEncodingErrorStatus(jsn);
        log.debug("JSON_ERROR::: {}", jsn);
        //TODO Do the same thing in case of wrong response.
        JSONObject json = new JSONObject();
        json.put("status", "OK");

        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("media/rate/action")
    public Response publishRateAction(@FormParam("id_media") long idMedia,
                                      @FormParam("idHash") String idHash,
                                      @FormParam("duration") long duration,
                                      @FormParam("type") int type,
                                      @FormParam("color") int color,
                                      @HeaderParam("X-Forwarded-For") String IP,
                                      @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();

        log.debug("idMedia {} idType {}", idMedia, type);

        if (idHash == null || idHash.isEmpty() || idMedia == 0 || duration < 0) {
            json.put("error", EnumErrors.RATE_ERROR);
            return createResponse(json);
        }
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

        if (user != null && user.getIdUser() != 0) {
            UsersShelf userShelf = new UsersShelf();
            DcMediaEntity media = new MediaShelf().retrieveMediaByIdValue(idMedia);
            if (media == null) {
                json.put("error", EnumErrors.RATE_ERROR);
                return createResponse(json);
            }
            EnumSQLErrors sqlErrors = userShelf.saveVideoRate(media, type, color, duration);

            if (sqlErrors != EnumSQLErrors.OK) {
                errors = EnumErrors.RATE_ERROR;
            }

        } else {
            errors = EnumErrors.INVALID_HASH;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("event/in/rates")
    public Response publishIwillWatch(@FormParam("id_media") long idMedia,
                                      @FormParam("hash") String idHash,
                                      @FormParam("type") int type) { // 0 - unjoin, 1 - will watch

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();

        log.debug("idHash {} idMedia {} idType {} IP {}", new Object[]{idHash, idMedia, type});

        if (idHash == null || idHash.isEmpty() || idMedia == 0 ) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

        if (user != null && user.getIdUser() != 0) {
            DcMediaEntity media = new MediaShelf().retrieveMediaByIdValue(idMedia);
            if (media == null) {
                json.put("error", EnumErrors.NO_MEDIA_FOUND);
                return createResponse(json);
            }

            switch (type){
                case 0:{
                    RMemoryAPI.getInstance()
                        .delFromSetElem(Constants.MEDIA_KEY + "event_viewers:"+idMedia, user.getIdUser()+"");
                    break;
                }
                case 1:{
                    RMemoryAPI.getInstance()
                            .pushSetElemToMemory(Constants.MEDIA_KEY + "event_viewers:"+idMedia, user.getIdUser()+"");
                    break;
                }
            }

        } else {
            errors = EnumErrors.INVALID_HASH;
        }

        json.put("error", errors);
        return createResponse(json);
    }


}