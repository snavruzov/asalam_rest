package com.dgtz.web.api;

import com.brocast.riak.api.beans.*;
import com.dgtz.api.beans.Principle;
import com.dgtz.api.beans.UserInfo;
import com.dgtz.api.contents.MediaShelf;
import com.dgtz.api.contents.UsersShelf;
import com.dgtz.api.enums.EnumAuthErrors;
import com.dgtz.api.enums.EnumErrors;
import com.dgtz.api.enums.EnumSocialType;
import com.dgtz.api.feature.SystemDelivery;
import com.dgtz.api.security.PrinciplesAuthorization;
import com.dgtz.api.security.PrinciplesCreation;
import com.dgtz.api.security.PrinciplesEdit;
import com.dgtz.api.security.PrinciplesRestore;
import com.dgtz.api.settings.ISystemDelivery;
import com.dgtz.api.utils.MD5;
import com.dgtz.api.utils.StringUtils;
import com.dgtz.api.utils.TagTokenizer;
import com.dgtz.db.api.beans.ChangeLogs;
import com.dgtz.db.api.domain.*;
import com.dgtz.db.api.enums.EnumSQLErrors;
import com.dgtz.mcache.api.factory.Constants;
import com.dgtz.mcache.api.factory.RMemoryAPI;
import com.dgtz.web.api.tools.Formulas;
import com.dgtz.web.api.tools.SecureLinkWrapper;
import com.dgtz.web.api.tools.SendCompressingSignal;
import com.dgtz.web.api.tools.SocketCoordinator;
import com.google.gson.Gson;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Created by sardor on 1/6/14.
 */
@Path("/body")
public class PrivateController extends Resource {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(PrivateController.class);
    private static final List FLAGS = Arrays.asList(1, 3, 5, 6, 7);

    public PrivateController() {
    }

    @POST
    @Produces("application/json")
    @Path("/fresh/init")
    public Response setBodyProperties(@FormParam("username") String username,
                                      @FormParam("email") String email,
                                      @FormParam("password") String password,
                                      @FormParam("lg") String languges,
                                      @FormParam("device") String device,
                                      @HeaderParam("Accept-Language") String lang) {

        PrinciplesCreation creation = new PrinciplesCreation(lang);

        JSONObject json = new JSONObject();
        Principle infoList = new Principle();

        EnumAuthErrors authErrors = null;
        try {
            email = email != null ? email.toLowerCase() : "";

            Pattern rfc2822 =
                    Pattern.compile("^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$");
            if (rfc2822.matcher(email).matches() && username != null && username.length() <= 50) {

                infoList.setUsername(username.trim());
                infoList.setFullName("");
                infoList.setPassword(password);
                infoList.setEmail(email.trim());
                log.debug(infoList.toString());

                log.debug("user langs keybrd {}, hder {}", languges, lang);
                authErrors = creation.processOfRegistration(infoList);

            } else {
                authErrors = EnumAuthErrors.WRONG_FIELD;
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            authErrors = EnumAuthErrors.AUTH_ERROR;
        }
        json.put("error", authErrors);

        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("media/subs/action")
    public Response followTheUserAction(@FormParam("idHash") String idHash,
                                        @FormParam("dest") long dest,
                                        @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        boolean refollowed = false;
        JSONObject json = new JSONObject();

        if (idHash == null || idHash.isEmpty() || dest == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }


        log.debug("followTheUserAction: {} {}", idHash, dest);
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        UsersShelf userShelf = new UsersShelf();
        if (user == null || user.getIdUser() == 0) {
            errors = EnumErrors.INVALID_HASH;
        } else if (user.getIdUser() == dest) {
            errors = EnumErrors.CANNOT_FOLLOW_YOURSELF;
        } else {
            DcUsersEntity entity = userShelf.getUserInfoById(dest);
            if (entity != null) {
                EnumSQLErrors sqlErrors = userShelf.followByUserVideo(user.getIdUser(), dest);
                refollowed =
                        RMemoryAPI.getInstance().pullIfSetElem(Constants.FOLLOWS + dest, user.idUser + "");

                if (sqlErrors != EnumSQLErrors.OK) {
                    errors = EnumErrors.UNKNOWN_ERROR;
                } else if (sqlErrors == EnumSQLErrors.NOTHING_UPDATED) {
                    errors = EnumErrors.ALREADEY_FOLLOWED;
                } else {
                    boolean followedBefore = RMemoryAPI.getInstance().pullIfSetElem(Constants.FOLLOWS+"archive:"+user.getIdUser(), dest+"");
                    if(!followedBefore) {
                        RMemoryAPI.getInstance().pushSetElemToMemory(Constants.FOLLOWS + "archive:" + user.getIdUser(), dest + "");


                        Notification notification = new Notification();
                        notification.setType(20);
                        notification.setUsername(user.username);
                        notification.setIdFrom(user.idUser);
                        notification.setIdUser(entity.idUser);

                        ISystemDelivery systemDelivery = SystemDelivery
                                .builder(notification).push().system();
                    }
                }
            } else {
                errors = EnumErrors.WRONG_ID_VALUE;
            }
        }

        json.put("friend", refollowed);
        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("media/subs/down")
    public Response removeFollowByTheUserAction(@FormParam("idHash") String idHash,
                                                @FormParam("dest") long dest,
                                                @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || dest == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        log.debug("idHash {} dest {}", idHash, dest);
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user == null || user.getIdUser() == 0) {
            errors = EnumErrors.INVALID_HASH;
        } else if (user.getIdUser() == dest) {
            errors = EnumErrors.CANNOT_FOLLOW_YOURSELF;
        } else {
            UsersShelf userShelf = new UsersShelf();
            EnumSQLErrors sqlErrors = userShelf.unFollowByUserVideo(user.getIdUser(), dest);

            if (sqlErrors != EnumSQLErrors.OK) {
                errors = EnumErrors.UNKNOWN_ERROR;
            }

        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("media/tag/follow")
    public Response followByTag(@FormParam("idHash") String idHash,
                                @FormParam("tags") String tags,
                                @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || tags == null || tags.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        log.debug("idHash {} tags {}", idHash, tags);
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user == null || user.getIdUser() == 0) {
            errors = EnumErrors.INVALID_HASH;
        } else {
            UsersShelf userShelf = new UsersShelf();
            errors = userShelf.followByTag(tags, user.idUser);
        }

        json.put("error", errors);
        return createResponse(json);
    }


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("media/tag/unfollow")
    public Response unfollowByTag(@FormParam("idHash") String idHash,
                                  @FormParam("tags") String tags,
                                  @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || tags == null || tags.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        log.debug("idHash {} tags {}", idHash, tags);
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user == null || user.getIdUser() == 0) {
            errors = EnumErrors.INVALID_HASH;
        } else {
            UsersShelf userShelf = new UsersShelf();
            errors = userShelf.unFollowByTag(tags, user.idUser);
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("media/channel/follow")
    public Response followByChannel(@FormParam("idHash") String idHash,
                                    @FormParam("chid") Long chid,
                                    @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || chid == null || chid <= 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        log.debug("idHash {} tags {}", idHash, chid);
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user == null || user.getIdUser() == 0) {
            errors = EnumErrors.INVALID_HASH;
        } else {
            UsersShelf userShelf = new UsersShelf();
            DcChannelsEntity channel = new MediaShelf().getChannelDataByID(chid);
            if (channel != null && !channel.idUser.equals(user.idUser)) {
                userShelf.followByChannel(channel, user.idUser);
            }
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("media/ch/custom")
    public Response editChannelInformationByOwner(@FormParam("idHash") String idHash,
                                                  @FormParam("title") String title,
                                                  @FormParam("descr") String descr,
                                                  @FormParam("idch") long idChannel,
                                                  @FormParam("np") int privacy,
                                                  @FormParam("op") int access,
                                                  @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();
        Pattern alphanumeric = Pattern.compile("^\\s+$");


        if (title == null || title.isEmpty() || alphanumeric.matcher(title).matches() || title.length() < 3 || title.length() > 500
                || descr == null || descr.isEmpty() || descr.length() < 4 || descr.length() > 1000
                || idHash == null || idHash.isEmpty() || idChannel == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);

        }

        if (privacy < 0 || privacy > 1 || access < 1 || access > 3) {
            json.put("error", EnumErrors.WRONG_TYPE_VALUE);
            return createResponse(json);

        }


        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

        if (user != null && user.getIdUser() != 0) {

            UsersShelf userShelf = new UsersShelf();
            DcChannelsEntity channel = new MediaShelf().getChannelDataByID(idChannel);
            EnumSQLErrors sqlErrors = EnumSQLErrors.UNKNOWN_ERROR;

            if (channel != null && Objects.equals(channel.idUser, user.getIdUser())) {
                ChannelProps props = new ChannelProps();
                props.access = access;
                channel.title = title;
                channel.description = descr;
                channel.setProps(props);
                channel.privacy = privacy;

                sqlErrors = userShelf.updateChannelInfo(channel);
            } else {
                errors = EnumErrors.WRONG_ID_VALUE;
            }

            if (sqlErrors != EnumSQLErrors.OK) {
                errors = EnumErrors.UNKNOWN_ERROR;
            }


        } else {
            errors = EnumErrors.INVALID_HASH;
        }
        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/fresh/f/auth")
    public Response authBodyPropertiesByFb(@FormParam("p") String token,
                                           @FormParam("lg") String languges,
                                           @FormParam("device") String device,
                                           @HeaderParam("Accept-Language") String lang) {

        PrinciplesAuthorization validate = new PrinciplesAuthorization(lang);

        UserInfo infoList = null;
        JSONObject json = new JSONObject();

        if (token == null || token.isEmpty()) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        }
        try {

            log.debug("TOKEN: {}", token);
            String session_queue = RMemoryAPI.getInstance().pullElemFromMemory(Constants.USER_KEY+"auth_session_queue:"+token);
            if(session_queue==null) {
                RMemoryAPI.getInstance().pushElemToMemory(Constants.USER_KEY + "auth_session_queue:" + token, 6, "1");
                infoList = validate.auth("", token, EnumSocialType.FACEBOOK);
            } else {
                json.put("error", EnumErrors.TOO_MANY_REQUESTS);
                return createResponse(json);
            }

            if (infoList == null) {
                log.error("info list is NULL getAuthFBFragment(..)");
            } else {

                boolean blocked = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", infoList.getIdUser() + "");
                if(blocked){
                    json.put("error", EnumErrors.USER_BLOCKED);
                    return createResponse(json);
                }
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
        }


        if (infoList == null) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        } else {
            return createResponse(infoList);
        }

    }

    @POST
    @Produces("application/json")
    @Path("/add/social/profile")
    public Response addUserSocialProfiles(@FormParam("red") String hash,
                                          @FormParam("type") String type,
                                          @FormParam("status") int status, //0 - off, 1 - on
                                          @FormParam("link") String link) {

        log.debug("social links::: {}", link);
        JSONObject json = new JSONObject();
        json.put("errors", EnumErrors.NO_ERRORS);

        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
            if (link != null && link.isEmpty() && user != null && user.getIdUser() != 0) {
                new PrinciplesEdit().updSocialURLLinks(link, type, user.idUser, status==1);
            } else {
                json.put("errors", EnumErrors.NULL_FIELD_ERROR);
            }
        } catch (Exception e) {
            log.error("Error in addUserSocialProfiles", e);
            json.put("errors", EnumErrors.UNKNOWN_ERROR);
        }
        return createResponse(json);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/coconut/webhook")
    public Response videoTranscodingCallback(String json) {

        log.debug("JSON::: {}", json);
        try {
            if (json != null && !json.isEmpty()) {
                com.google.gson.JsonObject jobj = new Gson().fromJson(json, com.google.gson.JsonObject.class);
                new MediaShelf().updateEncodingStatus(jobj);
            }
        } catch (Exception e) {
            log.error("Error in webhook", e);
        }

        new JSONObject().put("status", "OK");
        return createResponse(json);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/coconut/debate/webhook")
    public Response videoTranscodingDebateCallback(String json) {

        log.debug("JSON::: {}", json);
        try {
            if (json != null && !json.isEmpty()) {
                com.google.gson.JsonObject jobj = new Gson().fromJson(json, com.google.gson.JsonObject.class);
                new MediaShelf().updateEncodingDebateStatus(jobj);
            }
        } catch (Exception e) {
            log.error("Error in webhook", e);
        }

        new JSONObject().put("status", "OK");
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/debate/invite")
    public Response liveDebateInvite(@FormParam("hash") String hash,
                                     @FormParam("idu") long idUser,
                                     @FormParam("position") String position, //sbs, pop
                                     @FormParam("idLive") Long idLive) {
        JSONObject json = new JSONObject();
        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
            DcMediaEntity entity = new MediaShelf().retrieveMediaByIdValue(idLive);
            if (user != null
                    && user.getIdUser() != 0
                    && user.getIdUser() != idUser
                    && position != null
                    && !position.isEmpty()
                    && entity != null) {

                RMemoryAPI.getInstance()
                                .pushHashToMemory(Constants.MEDIA_KEY + idLive, "debate.position", position);
                RMemoryAPI.getInstance().pushElemToMemory(Constants.LIVE_KEY + "debate.queue:" + idLive, 3, idUser + "");

                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        UsersShelf usersShelf = new UsersShelf();
                        Notification notif = new Notification();
                        notif.setType(8); //debate
                        notif.setIdMedia(idLive);
                        notif.setIdFrom(user.idUser);
                        notif.setIdUser(idUser);
                        notif.setUsername(user.username);
                        notif.setText(entity.title);

                        ISystemDelivery systemDelivery = SystemDelivery
                                .builder(notif).inbox().push();
                    }
                };

                thread.setName("thread-inbox-send:" + System.currentTimeMillis());
                thread.start();

                json.put("error", EnumErrors.NO_ERRORS);
            } else {
                json.put("error", EnumErrors.INVALID_HASH);
            }
        } catch (Exception e) {
            json.put("error", EnumErrors.UNKNOWN_ERROR);
            log.error("Error in debate ivitation", e);
        }

        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/debate/cancel/invite")
    public Response liveDebateCancelInvite(@FormParam("hash") String hash,
                                           @FormParam("idu") Long idUser,
                                           @FormParam("idLive") Long idLive) {
        JSONObject json = new JSONObject();
        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
            String debateUserQueue = RMemoryAPI.getInstance().pullElemFromMemory(Constants.LIVE_KEY + "debate.queue:" + idLive);
            if (user != null
                    && debateUserQueue != null
                    && user.getIdUser() != 0
                    && idUser != null
                    && idUser != 0) { //TODO check for who can cancel

                RMemoryAPI.getInstance().delFromMemory(Constants.LIVE_KEY + "debate.queue:" + idLive);
                RMemoryAPI.getInstance().delFromMemory(Constants.LIVE_KEY + "debate.status:" + idLive);

                UsersShelf usersShelf = new UsersShelf();
                Notification notification = new Notification();
                notification.setIdFrom(user.getIdUser());
                notification.setType(14);
                notification.setIdMedia(idLive);
                notification.setIdHoster(idLive);

                ISystemDelivery systemDelivery = SystemDelivery
                        .builder(notification).socket();

                json.put("error", EnumErrors.NO_ERRORS);
            } else {
                json.put("error", EnumErrors.INVALID_HASH);
            }
        } catch (Exception e) {
            json.put("error", EnumErrors.UNKNOWN_ERROR);
            log.error("Error in debate ivitation", e);
        }

        return createResponse(json);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/coconut/thumb")
    public Response thumbTranscodingCallback(String json) {

        log.debug("JSON THUMB::: {}", json);
        try {
            if (json != null && !json.isEmpty()) {
                com.google.gson.JsonObject jobj = new Gson().fromJson(json, com.google.gson.JsonObject.class);
                new MediaShelf().updateVideoThumbnail(jobj);
            }
        } catch (Exception e) {
            log.error("Error in webhook", e);
        }

        new JSONObject().put("status", "OK");
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/social/link/{stp}")
    public Response linkSocialNetworkAccount(
            @PathParam("stp") String socialType,
            @FormParam("hash") String idHash,
            @FormParam("header") String header,
            @FormParam("token") String token) {

        PrinciplesAuthorization validate = new PrinciplesAuthorization();

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        if (idHash == null || idHash.isEmpty() || token == null || token.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        try {

            DcUsersEntity user = new UsersShelf().getUserInfoByHash(idHash);
            if (user != null && user.getIdUser() != 0) {
                EnumSocialType stp = null;
                switch (socialType) {
                    case "facebook": {
                        stp = EnumSocialType.FACEBOOK;
                        break;
                    }
                    case "google": {
                        stp = EnumSocialType.GOOGLE;
                        break;
                    }
                    case "twitter": {
                        stp = EnumSocialType.TWITTER;
                        break;
                    }
                    case "vk": {
                        stp = EnumSocialType.VK;
                        break;
                    }
                }
                if (stp != null) {
                    errors = validate.linkSocialNetworks(user.getIdUser(), header, token, stp);
                } else {
                    errors = EnumErrors.WRONG_TYPE_VALUE;
                }
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
    @Produces("application/json")
    @Path("/fresh/vk/auth")
    public Response authBodyPropertiesByVK(@FormParam("access_token") String token,
                                           @FormParam("lg") String languges,
                                           @FormParam("device") String device,
                                           @HeaderParam("Accept-Language") String lang) {

        PrinciplesAuthorization validate = new PrinciplesAuthorization(lang);

        UserInfo infoList = null;
        JSONObject json = new JSONObject();

        if (token == null || token.isEmpty()) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        }
        try {

            log.debug("TOKEN: {}", token);
            String session_queue = RMemoryAPI.getInstance().pullElemFromMemory(Constants.USER_KEY+"auth_session_queue:"+token);
            if(session_queue==null) {
                RMemoryAPI.getInstance().pushElemToMemory(Constants.USER_KEY + "auth_session_queue:" + token, 6, "1");
                infoList = validate.authVK(token);
            } else {
                json.put("error", EnumErrors.TOO_MANY_REQUESTS);
                return createResponse(json);
            }

            if (infoList == null) {
                log.error("info list is NULL getAuthVKFragment(..)");
            } else {
                boolean blocked = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", infoList.getIdUser() + "");
                if(blocked){
                    json.put("error", EnumErrors.USER_BLOCKED);
                    return createResponse(json);
                }
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
        }


        if (infoList == null) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        } else {
            return createResponse(infoList);
        }

    }

    @POST
    @Produces("application/json")
    @Path("/fresh/twitter/auth")
    public Response authBodyPropertiesByTwtr(@FormParam("header") String header,
                                             @FormParam("oauth_token") String oauth_token,
                                             @FormParam("lg") String languges,
                                             @FormParam("device") String device,
                                             @HeaderParam("Accept-Language") String lang) {

        PrinciplesAuthorization validate = new PrinciplesAuthorization(lang);

        UserInfo infoList = null;
        JSONObject json = new JSONObject();

        if (header == null || header.isEmpty() || oauth_token == null || oauth_token.isEmpty()) {

            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        }
        try {

            log.debug("TOKEN: {}", oauth_token);
            String session_queue = RMemoryAPI.getInstance().pullElemFromMemory(Constants.USER_KEY+"auth_session_queue:"+oauth_token);
            if(session_queue==null) {
                RMemoryAPI.getInstance().pushElemToMemory(Constants.USER_KEY + "auth_session_queue:" + oauth_token, 6, "1");
                infoList = validate.authTwitter(header, oauth_token, languges);
            } else {
                json.put("error", EnumErrors.TOO_MANY_REQUESTS);
                return createResponse(json);
            }


            if (infoList == null) {
                log.error("info list is NULL getAuthTwitterFragment(..)");
            }else {
                boolean blocked = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", infoList.getIdUser() + "");
                if(blocked){
                    json.put("error", EnumErrors.USER_BLOCKED);
                    return createResponse(json);
                }
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
        }

        if (infoList == null) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        } else {
            return createResponse(infoList);
        }

    }

    @POST
    @Produces("application/json")
    @Path("/fresh/twitter/auth2")
    public Response authBodyPropertiesByWEBTwtr(@FormParam("oauth_token") String oauth_token,
                                                @FormParam("oauth_secret") String oauth_secret,
                                                @FormParam("lg") String languges,
                                                @FormParam("device") String device,
                                                @HeaderParam("Accept-Language") String lang) {

        PrinciplesAuthorization validate = new PrinciplesAuthorization(lang);

        UserInfo infoList = null;
        JSONObject json = new JSONObject();

        if (oauth_secret == null || oauth_secret.isEmpty() || oauth_token == null || oauth_token.isEmpty()) {

            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        }
        try {

            log.info("TOKEN: {}", oauth_token);
            String session_queue = RMemoryAPI.getInstance().pullElemFromMemory(Constants.USER_KEY+"auth_session_queue:"+oauth_token);
            if(session_queue==null) {
                RMemoryAPI.getInstance().pushElemToMemory(Constants.USER_KEY + "auth_session_queue:" + oauth_token, 6, "1");
                infoList = validate.auth2Twitter(oauth_token, oauth_secret, languges);
            } else {
                json.put("error", EnumErrors.TOO_MANY_REQUESTS);
                return createResponse(json);
            }


            if (infoList == null) {
                log.error("info list is NULL getAuthTwitterFragment(..)");
            }else {
                boolean blocked = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", infoList.getIdUser() + "");
                if(blocked){
                    json.put("error", EnumErrors.USER_BLOCKED);
                    return createResponse(json);
                }
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
        }

        if (infoList == null) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        } else {
            return createResponse(infoList);
        }

    }


    @POST
    @Produces("application/json")
    @Path("/fresh/g/auth")
    public Response authBodyPropertiesByGoo(@FormParam("p") String token,
                                            @FormParam("lg") String languges,
                                            @FormParam("device") String device,
                                            @HeaderParam("Accept-Language") String lang) {

        PrinciplesAuthorization validate = new PrinciplesAuthorization(lang);

        UserInfo infoList = null;
        JSONObject json = new JSONObject();

        if (token == null || token.isEmpty()) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        }
        try {

            log.debug("TOKEN: {}", token);
            String session_queue = RMemoryAPI.getInstance().pullElemFromMemory(Constants.USER_KEY+"auth_session_queue:"+token);
            if(session_queue==null) {
                RMemoryAPI.getInstance().pushElemToMemory(Constants.USER_KEY + "auth_session_queue:" + token, 6, "1");
                infoList = validate.auth("", token, EnumSocialType.GOOGLE);
            } else {
                json.put("error", EnumErrors.TOO_MANY_REQUESTS);
                return createResponse(json);
            }

            if (infoList == null) {
                log.error("info list is NULL GOOGLE auth(..)");
            }else {
                boolean blocked = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", infoList.getIdUser() + "");
                if(blocked){
                    json.put("error", EnumErrors.USER_BLOCKED);
                    return createResponse(json);
                }
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
        }


        if (infoList == null) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        } else {
            return createResponse(infoList);
        }
    }

    @POST
    @Produces("application/json")
    @Path("/fresh/google/auth")
    public Response authBodyPropertiesByGoogle(@FormParam("id_token") String token,
                                               @FormParam("lg") String languges,
                                               @FormParam("device") String device,
                                               @HeaderParam("Accept-Language") String lang) {

        PrinciplesAuthorization validate = new PrinciplesAuthorization(lang);

        UserInfo infoList = null;
        JSONObject json = new JSONObject();

        if (token == null || token.isEmpty()) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        }
        try {

            log.debug("TOKEN {}", token);
            String session_queue = RMemoryAPI.getInstance().pullElemFromMemory(Constants.USER_KEY+"auth_session_queue:"+token);
            if(session_queue==null) {
                RMemoryAPI.getInstance().pushElemToMemory(Constants.USER_KEY + "auth_session_queue:" + token, 6, "1");
                infoList = validate.authGoogle(token, languges);
            } else {
                json.put("error", EnumErrors.TOO_MANY_REQUESTS);
                return createResponse(json);
            }

            if (infoList == null) {
                log.error("info list is NULL GOOGLE auth(..)");
            }else {
                boolean blocked = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", infoList.getIdUser() + "");
                if(blocked){
                    json.put("error", EnumErrors.USER_BLOCKED);
                    return createResponse(json);
                }
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
        }


        if (infoList == null) {
            json.put("error", EnumErrors.SOCIAL_AUTH_ERROR);
            return createResponse(json);
        } else {
            return createResponse(infoList);
        }
    }

    @POST
    @Produces("application/json")
    @Path("/profile/restore")
    public Response restoreBodyPassword(@FormParam("eml") String email,
                                        @HeaderParam("Accept-Language") String lang) {

        PrinciplesRestore restore = new PrinciplesRestore();

        JSONObject json = new JSONObject();

        EnumErrors errors = EnumErrors.NO_ERRORS;
        email = email != null ? email.toLowerCase() : "";
        Pattern rfc2822 =
                Pattern.compile("^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$");

        if (email == null || (email != null && !rfc2822.matcher(email).matches()) || email.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            log.debug("EMAIL: {}", email);

            Principle principle = new Principle();
            principle.setEmail(email);
            errors = restore.processOfPasswordRestore(principle);
            log.debug("EMAIL DEBUG: {}", errors);

        } catch (Exception ex) {
            log.error("ERROR IN WEB API: ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors.toString());

        return createResponse(json);
    }


    @POST
    @Produces("application/json")
    @Path("/profile/newpass")
    public Response changeBodyPassword(
            @FormParam("red") String idHash,
            @FormParam("oldPass") String oldPass,
            @FormParam("newPass") String newPass,
            @HeaderParam("Accept-Language") String lang) {

        PrinciplesEdit edit = new PrinciplesEdit();

        JSONObject json = new JSONObject();

        if (idHash == null || idHash.isEmpty() || newPass == null || newPass.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            DcUsersEntity user = new UsersShelf().getUserInfoByHash(idHash);

            String oldPassHash = MD5.hash(oldPass + MD5.SALT);
            log.debug("OLD PASS {}", oldPassHash);
            if (user != null && user.getIdUser() != 0) {
                //TODO check WHAT old pass is requested
                if (user.getSecword().equals(oldPassHash)
                        || (user.getSecword() == null && (user.idFBSocial != null || user.getIdGSocial() != null))) {
                    String newHash = edit.changeProfPass(idHash, oldPass, newPass);
                    if (!newHash.isEmpty()) {
                        json.put("error", EnumErrors.NO_ERRORS);
                    } else {
                        json.put("error", EnumErrors.UNKNOWN_ERROR);
                    }
                } else {
                    json.put("error", EnumErrors.INVALID_OLD_PASSWORD);
                }
            } else {
                json.put("error", EnumErrors.INVALID_HASH);
            }


        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            json.put("error", EnumErrors.UNKNOWN_ERROR);

        }

        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/personal/ava/save")
    public Response saveWebPicOfUser(@QueryParam("hash") String hash,
                                     @QueryParam("nano") String nano,
                                     @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        log.debug("HASH AVATAR SAVE {}, {}", hash, nano);

        try {

            if (hash != null && !hash.isEmpty()) {

                DcUsersEntity usersEntity = new UsersShelf().getUserInfoByHash(hash);
                usersEntity.setAvatar(nano);
                usersEntity.setHash(hash);

                PrinciplesEdit principlesEdit = new PrinciplesEdit();
                EnumSQLErrors sqlErrors = principlesEdit.setUserAvaByHash(usersEntity);

                if (sqlErrors != EnumSQLErrors.OK) {
                    errors = EnumErrors.ERROR_IN_IMG_CAPTURING;
                }
            }


        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.ERROR_IN_IMG_CAPTURING;
        }

        json.put("error", errors);
        json.put("ava", " ");

        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/personal/wall/save")
    public Response saveWallPicOfUser(@QueryParam("hash") String hash,
                                      @QueryParam("nano") String nano,
                                      @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        log.debug("WALL PICTURE PROFILE SAVE {}, {}", hash, nano);

        try {

            if (hash != null && !hash.isEmpty()) {

                DcUsersEntity usersEntity = new UsersShelf().getUserInfoByHash(hash);
                usersEntity.setWallpic(nano);
                usersEntity.setHash(hash);

                PrinciplesEdit principlesEdit = new PrinciplesEdit();
                principlesEdit.setUserWallByHash(usersEntity);
            }


        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.ERROR_IN_IMG_CAPTURING;
        }

        json.put("error", errors);
        json.put("ava", " ");

        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/channel/ava/save")
    public Response saveWebPicOfChannel(@QueryParam("id") Long idUser,
                                        @QueryParam("nano") String nano,
                                        @QueryParam("ch") Long idChannel,
                                        @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        json.put("ava", " ");

        log.debug("FIELDS: {}, CH: {}", idUser, idChannel);
        if (idChannel == null || idChannel == 0 || idUser == null || idUser == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        try {
            UsersShelf userShelf = new UsersShelf();

            DcChannelsEntity channel = new MediaShelf().getChannelDataByID(idChannel);
            if (channel != null && channel.idUser.equals(idUser)) {
                channel.avatar = nano;

                EnumSQLErrors sqlErrors = userShelf.updateChannelInfo(channel);

                if (sqlErrors != EnumSQLErrors.OK) {
                    errors = EnumErrors.ERROR_IN_IMG_CAPTURING;
                }
            } else {
                errors = EnumErrors.NO_CHANNEL_ERROR;
            }


        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.ERROR_IN_IMG_CAPTURING;
        }

        json.put("error", errors);

        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/channel/wall/save")
    public Response saveWallPicOfChannel(@QueryParam("id") Long idUser,
                                         @QueryParam("nano") String wallpic,
                                         @QueryParam("ch") Long idChannel,
                                         @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        json.put("ava", " ");

        log.debug("FIELDS: {}, CH: {}", idUser, idChannel);
        if (idChannel == null || idChannel == 0 || idUser == null || idUser == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        try {
            UsersShelf userShelf = new UsersShelf();

            DcChannelsEntity channel = new MediaShelf().getChannelDataByID(idChannel);
            if (channel != null && channel.idUser.equals(idUser)) {
                channel.wall = wallpic;

                EnumSQLErrors sqlErrors = userShelf.updateChannelInfo(channel);

                if (sqlErrors != EnumSQLErrors.OK) {
                    errors = EnumErrors.ERROR_IN_IMG_CAPTURING;
                }
            } else {
                errors = EnumErrors.NO_CHANNEL_ERROR;
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.ERROR_IN_IMG_CAPTURING;
        }

        json.put("error", errors);

        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("media/flag/action")
    public Response publishFlagAction(@FormParam("id_media") long idMedia,
                                      @FormParam("idHash") String idHash,
                                      @FormParam("idFlag") int idFlag,
                                      @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();

        log.debug("idHash {} idMedia {} idFlag {}", new Object[]{idHash, idMedia, idFlag});
        if (idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        if (!FLAGS.contains(idFlag)) {
            json.put("error", EnumErrors.WRONG_TYPE_VALUE);
            return createResponse(json);
        }

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

        if (user != null && user.getIdUser() != 0) {
            UsersShelf userShelf = new UsersShelf();
            EnumSQLErrors sqlErrors = userShelf.flagTheMedia(user.getIdUser(), idMedia, (short) idFlag);

            if (sqlErrors != EnumSQLErrors.OK) {
                errors = EnumErrors.REPORT_ERROR;
            }
        } else {
            errors = EnumErrors.INVALID_HASH;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/personal/moon/sed")
    public Response savePrivateInfoOfUser(@FormParam("idHash") String idHash,
                                          @FormParam("email") String email,
                                          @FormParam("fullName") String fullName,
                                          @FormParam("username") String username,
                                          @FormParam("moreEmail") String moreEmail,
                                          @FormParam("city") String city,
                                          @FormParam("country") String country,
                                          @FormParam("code") String code,
                                          @FormParam("mobileNumber") String mobileNumber,
                                          @FormParam("about") String about,
                                          @FormParam("lang") String mainLang,
                                          @FormParam("isAnonymous") boolean isAnonymous,
                                          @HeaderParam("Accept-Language") String lang) throws IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        email = email != null ? email.toLowerCase() : "";
        Pattern rfc2822 =
                Pattern.compile("^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$");

        Pattern mobile = Pattern.compile("^(\\+|\\d)[0-9]{1,16}$");
        mainLang = (mainLang == null || mainLang.isEmpty()) ? lang : mainLang;

        if (idHash == null || idHash.isEmpty() || username == null || username.isEmpty() || username.length() > 50
                || (fullName != null && !fullName.isEmpty() && fullName.length() > 100)
                || (about != null && !about.isEmpty() && about.length() > 500)
                || (city != null && !city.isEmpty() && city.length() > 167)
                || email.isEmpty() || !rfc2822.matcher(email).matches()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        if (mobileNumber != null && !mobileNumber.isEmpty() && !mobile.matcher(mobileNumber).matches()) {
            json.put("error", EnumErrors.MOBILE_FORMAT_ERROR);
            return createResponse(json);
        }

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {


                PrivateInfo privateInfo = new PrivateInfo();
                privateInfo.mobileNumber = mobileNumber != null ? mobileNumber : "";
                privateInfo.code = code;


                /*Country code to know what country in english, don't mix wtih language code!*/
                if (code != null && !code.isEmpty()) {
                    country = RMemoryAPI.getInstance().pullHashFromMemory(Constants.LOCATION + code, "country");

                }

                user.country = country.equals("None") ? "" : country;
                user.city = city;
                user.about = about;

                user.email = email;
                user.fullname = fullName;
                user.profile = privateInfo;
                user.username = username.trim();

                PrinciplesEdit principlesEdit = new PrinciplesEdit(mainLang);

                log.debug("savePrivateInfoOfUser {} {}", idHash, user.idUser);
                errors = principlesEdit.updateUserInfoByEntity(user);

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
    @Path("/personal/moon/account")
    public Response savePrivateInfoOfUser(@FormParam("red") String idHash,
                                          @FormParam("token") String token,
                                          @HeaderParam("Accept-Language") String lang) throws IOException {

        JSONObject json = new JSONObject();

        UsersShelf usersShelf = new UsersShelf();
        UserInfo infoList = null;

        if (idHash == null || idHash.isEmpty() || token == null || token.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        log.debug("TOKEN {}", token);

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0 && usersShelf.tokenAccept(token, user.getIdUser())) {

                PrivateInfo privateInfo = user.getProfile();
                infoList = new UserInfo(user, privateInfo, user.getIdUser(), user.getAvatar(), 0);
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
        }

        return createResponse(infoList);
    }

    @POST
    @Produces("application/json")
    @Path("/auth")
    public Response getAuthFragment(@FormParam("e") String email,
                                    @FormParam("p") String secw,
                                    @FormParam("lg") String languges,
                                    @FormParam("device") String device,
                                    @HeaderParam("Accept-Language") String lang) {

        PrinciplesAuthorization validate = new PrinciplesAuthorization(lang);
        UserInfo infoList = null;

        EnumErrors authErrors = EnumErrors.NO_ERRORS;
        if (email != null && !email.isEmpty() && secw != null && !secw.isEmpty()) {

            try {
                log.info("email: {} - dev:{}", email, device);

                infoList = validate.auth(email, secw, EnumSocialType.BROCAST);
                if (infoList != null && infoList.getError_msg()==EnumAuthErrors.PENDING_ACTIVATION) {
                    authErrors = EnumErrors.PENDING;
                } else if (infoList != null && infoList.getSecWord() != null && infoList.getSecWord().equals("EMPTY")) {
                    authErrors = EnumErrors.NO_PASS_ERROR;
                } else if (infoList != null && infoList.getError_msg()==EnumAuthErrors.WRONG_PASS_EMAIL) {
                    authErrors = EnumErrors.WRONG_PASS_EMAIL;
                } else if(infoList!=null) {
                    boolean blocked = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", infoList.getIdUser() + "");
                    if (blocked) {
                        authErrors = EnumErrors.USER_BLOCKED;
                    }
                }

            } catch (Exception ex) {
                log.error("ERROR IN WEB API ", ex);
                authErrors = EnumErrors.UNKNOWN_ERROR;
            }
        } else {
            authErrors = EnumErrors.AUTH_ERROR;
        }

        log.debug("INFO LIST: {}", infoList);

        if (authErrors != EnumErrors.NO_ERRORS) {
            JSONObject json = new JSONObject();
            json.put("error", authErrors);
            return createResponse(json);
        }

        return createResponse(infoList);
    }

    @POST
    @Produces("application/json")
    @Path("/logout")
    public Response killUser(@FormParam("red") String idHash,
                             @FormParam("did") String devId,
                             @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        UsersShelf usersShelf = new UsersShelf();
        JSONObject json = new JSONObject();

        if (idHash == null || idHash.isEmpty() || devId == null || devId.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            if (user != null) {
                EnumSQLErrors sqlErrors = usersShelf.loggingOut(user.getIdUser(), devId);
                if (sqlErrors != EnumSQLErrors.OK) {
                    errors = EnumErrors.UNKNOWN_ERROR;
                }
            } else {
                errors = EnumErrors.NULL_FIELD_ERROR;
            }
        } catch (Exception e) {
            errors = EnumErrors.UNKNOWN_ERROR;
            log.error("ERROR IN LOGOUT: ", e);
        }

        log.debug(errors.toString());

        json.put("error", errors.toString());

        return createResponse(json);
    }


    @POST
    @Produces("application/json")
    @Path("/reg/devid")
    public Response regDeviceId(@FormParam("devid") String device,
                                @FormParam("red") String idHash,
                                @FormParam("platform") String platform,
                                @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        EnumErrors errors = EnumErrors.NO_ERRORS;

        if (device != null && !device.isEmpty() && idHash != null && !idHash.isEmpty()
                && (platform.equals("ios") || platform.equals("android"))) {

            try {
                log.debug("deviceNewId {}", device);
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user != null && user.getIdUser() != 0) {
                    new UsersShelf().updateUserDevice(device, user.getIdUser(), platform);
                }

            } catch (Exception ex) {
                log.error("ERROR IN WEB API ", ex);
                errors = EnumErrors.UNKNOWN_ERROR;
            }
        } else {
            errors = EnumErrors.NULL_FIELD_ERROR;
        }

        log.debug(errors.toString());

        json.put("error", errors.toString());
        return createResponse(json);
    }

    @GET
    @Path("/my/follow/inter/list")
    @Produces("application/json")
    public Response getMyFollowIntersectionActivity(
            @QueryParam("red") String idHash,
            @QueryParam("off") long off,
            @QueryParam("lm") long limit,
            @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        List<UserShortInfo> followsEntities = new ArrayList<>();

        try {

            if (idHash != null && !idHash.isEmpty()) {
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user != null && user.getIdUser() != 0) {
                    UsersShelf usersShelf = new UsersShelf(lang);
                    followsEntities = usersShelf.showMyFollowerIntersection(user.getIdUser(), off, limit);

                } else {
                    errors = EnumErrors.INVALID_HASH;
                }

                log.debug("FOLLOW ACTIVITIES {}", followsEntities);
            } else {
                errors = EnumErrors.NULL_FIELD_ERROR;
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        Response response;

        if (errors == EnumErrors.NO_ERRORS) {
            response = createResponse(followsEntities);
        } else {
            json.put("error", errors);
            response = createResponse(json);
        }

        return response;
    }

    @GET
    @Path("/user/channel/search/{ref}")
    @Produces("application/json")
    public Response getChannelInfoByUserSearch(
                                    @PathParam("ref") int ref, //1-in my or others, 2-others
                                    @QueryParam("idu") long idUser,
                                    @QueryParam("name") String name,
                                    @QueryParam("off") long off,
                                    @QueryParam("lm") long limit,
                                    @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        List<PublicChannelsEntity> followsEntities = new ArrayList<>();

        try {

            if (name!=null && !name.isEmpty() && idUser!=0) {

                UsersShelf usersShelf = new UsersShelf(lang);
                if(ref==1) {
                    followsEntities = usersShelf.extractChannelByUserSearch(name, idUser, off, limit);
                } else if(ref == 2){
                    followsEntities = usersShelf.extractChannelByFollowSearch(name, idUser, off, limit);
                }
                log.debug("FOLLOW ACTIVITIES {}", followsEntities);
            } else {
                errors = EnumErrors.NULL_FIELD_ERROR;
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        Response response;

        if (errors == EnumErrors.NO_ERRORS) {
            response = createResponse(followsEntities);
        } else {
            json.put("error", errors);
            response = createResponse(json);
        }

        return response;
    }

    @POST
    @Produces("application/json")
    @Path("/share/inbox")
    public Response inboxMediaMultipleUsers(@FormParam("red") String hash,
                                            @FormParam("dev_old") String deviceOldId,
                                            @FormParam("red") String idHash,
                                            @FormParam("platform") String platform,
                                            @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        EnumErrors errors = EnumErrors.NO_ERRORS;

        /*if (deviceNewId != null && !deviceNewId.isEmpty()
                && deviceOldId != null && !deviceOldId.isEmpty()
                && idHash != null && !idHash.isEmpty()) {

            try {
                log.debug("deviceNewId {} deviceOldId {}", deviceNewId, deviceOldId);
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user != null && user.getIdUser() != 0) {
                    UsersShelf usersShelf = new UsersShelf();
                    EnumSQLErrors sqlErrors =
                            usersShelf.updateUserDevice(deviceNewId, deviceOldId, user.getIdUser(), 2);
                    if (sqlErrors != EnumSQLErrors.OK) {
                        errors = EnumErrors.UNKNOWN_ERROR;
                    }
                }

            } catch (Exception ex) {
                log.error("ERROR IN WEB API ", ex);
                errors = EnumErrors.UNKNOWN_ERROR;
            }
        } else {
            errors = EnumErrors.NULL_FIELD_ERROR;
        }*/

        log.debug(errors.toString());

        json.put("error", errors.toString());
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/rocket/ch/publish")
    public Response createNewChannel(@FormParam("t") String title,
                                     @FormParam("desc") String description,
                                     @FormParam("proc") int privacy,
                                     @FormParam("acc") int access,
                                     @FormParam("idHash") String idHash,
                                     @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        long idChannel = -1;

        if (title == null || title.isEmpty() || title.length() < 3 || title.length() > 500
                || description == null || description.isEmpty() || description.length() < 4 || description.length() > 1000
                || idHash == null || idHash.isEmpty()) {
            json.put("idChannel", idChannel);
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);

        }

        log.debug("CREATE CHANNEL idhash: {}", idHash);
        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {

                UsersShelf usersShelf = new UsersShelf();

                DcChannelsEntity entity = new DcChannelsEntity();

                ChannelProps props = new ChannelProps();
                props.access = access;

                entity.avatar = ("/" + System.nanoTime());
                entity.wall = ("/" + System.nanoTime());

                entity.description = (description);
                entity.enabled = (true);
                entity.idUser = user.getIdUser();
                entity.title = (title);
                entity.privacy = (privacy);
                entity.props = props;
                entity.ucount = 1l;
                entity.mcount = 0l;
                entity.dateadded = RMemoryAPI.getInstance().currentTimeMillis() + "";

                idChannel = usersShelf.insertFreshChannel(entity);

            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        if (idChannel == -1) {
            errors = EnumErrors.PUBLISH_ERR;
        }

        json.put("idChannel", idChannel);
        json.put("error", errors);
        return createResponse(json);
    }

    @GET
    @Path("/rocket/ch/join_suggest")
    @Produces("application/json")
    public Response suggestIntoChannelByOwner(@QueryParam("uid") long idUser,
                                              @QueryParam("red") String idHash,
                                              @QueryParam("idch") long idChannel,
                                              @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        log.debug("suggestToJoinChannelByOwner {}", idUser);
        if (idHash == null || idHash.isEmpty() || idChannel <= 0 || idUser <= 0) {
            log.debug("HASH: {}", idHash);
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        try {

            UsersShelf usersShelf = new UsersShelf();

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            DcChannelsEntity channelsEntity = new MediaShelf().getChannelDataByID(idChannel);

            if (channelsEntity != null && user != null && user.getIdUser() != 0
                    && user.getIdUser() != idUser && Objects.equals(channelsEntity.idUser, user.getIdUser())) {

                DcUsersEntity dest = usersShelf.getUserInfoById(idUser);
                if (dest != null && dest.getIdUser() != 0) {
                    JSONObject updated = usersShelf.joinTheUserInChannel(idUser, idChannel,
                            true, false, false);

                    log.debug("IS_JOINED: {}", updated);

                    if (updated.getBoolean("ispart")) {
                        errors = EnumErrors.ALREADY_JOINED;
                    } else if (updated.getBoolean("iswait")) {
                        errors = EnumErrors.WAIT_FOR_JOIN;
                    } else {
//                        if (errors == EnumErrors.NO_ERRORS && idUser != 0) {
//                            DcChannelsEntity channelInfo = new MediaShelf().getChannelDataByID(idChannel);
//                            DcUsersEntity userInfo = PrivateProperties.extractUserFullInfo(idHash);
//
//                            SendPushNotifications pushNotification = new SendPushNotifications();
//                            pushNotification.sendGCMNotifications(channelInfo.getTitle(), userInfo.getUsername(),
//                                    idUser, userInfo.getIdUser(), 0L, idChannel, EnumNotification.SUGGEST_TO_JOIN.value, false, true);
//
//                        }
                    }
                } else {
                    errors = EnumErrors.WRONG_ID_VALUE;
                }
            } else {
                errors = EnumErrors.UNKNOWN_ERROR;
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @GET
    @Path("/rocket/ch/join_me")
    @Produces("application/json")
    public Response tryToJoinIntoChannelByUser(@QueryParam("red") String idHash,
                                               @QueryParam("idch") long idChannel,
                                               @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        log.debug("tryToJoinIntoChannelByUser {}", idHash);
        if (idHash == null || idHash.isEmpty() || idChannel <= 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            UsersShelf usersShelf = new UsersShelf();

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            DcChannelsEntity channelsEntity = new MediaShelf().getChannelDataByID(idChannel);
            if (user != null && user.getIdUser() != 0 && channelsEntity != null && !Objects.equals(user.getIdUser(), channelsEntity.idUser)) {
                JSONObject updated = null;
                /*If channel is not public*/
                if (channelsEntity.getPrivacy() != 0) {
                    updated = usersShelf.joinTheUserInChannel(user.getIdUser(), idChannel, false, false, false);
                } else {
                    updated = usersShelf.joinTheUserInChannel(user.getIdUser(), idChannel, false, true, false);
                }

                log.debug("IS_JOINED: {}", updated);

                if (updated.getBoolean("ispart")) {
                    errors = EnumErrors.ALREADY_JOINED;
                } else if (updated.getBoolean("iswait")) {
                    errors = EnumErrors.WAIT_FOR_JOIN;
                }

//                if (errors == EnumErrors.NO_ERRORS && channelsEntity.getPrivacy() != 0) {
//                    SendPushNotifications pushNotification = new SendPushNotifications();
//                    pushNotification.sendGCMNotifications(channelsEntity.getTitle(), user.getUsername(),
//                            channelsEntity.idUser, user.getIdUser(), 0L, idChannel,
//                            EnumNotification.WANT_TO_JOIN.value, false, true);
//                } else if (errors == EnumErrors.NO_ERRORS) {
//                    SendPushNotifications pushNotification = new SendPushNotifications();
//                    pushNotification.sendGCMNotifications(channelsEntity.getTitle(), user.getUsername(),
//                            channelsEntity.idUser, user.getIdUser(), 0L, idChannel, EnumNotification.USER_ARE_JOINED.value, false, false);
//                    pushNotification.sendGCMNotifications(channelsEntity.getTitle(), user.getUsername(),
//                            user.getIdUser(), channelsEntity.idUser, 0L, idChannel, EnumNotification.YOU_ARE_JOINED.value, false, false);
//                }
            } else {
                errors = EnumErrors.UNKNOWN_ERROR;
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }


    @GET
    @Path("/profile/info")
    @Produces("application/json")
    public Response getUserProfileInfo(@QueryParam("uid") long idUser,
                                       @QueryParam("red") String idHash,
                                       @HeaderParam("Accept-Language") String lang) {

        DcUsersEntity usersEntity = new DcUsersEntity();
        JSONObject json = new JSONObject();
        if(idHash==null || idHash.isEmpty() || idHash.equals("9d8128a5e784e14fc64b137fae5c63fe")){
            idHash = Formulas.DEFAULT_HASH;
        }
        if (idUser >= 0) {
            try {
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
                if (user != null) {
                    Boolean followed = false;
                    Boolean refollowed = false;
                    boolean vc = false;
                    UsersShelf usersShelf = new UsersShelf(lang);

                    usersEntity = usersShelf.getUserInfoById(idUser);

                    if (usersEntity != null) {
                        if (user.idUser != Formulas.DEFAULT_USER) {
                            followed = RMemoryAPI.getInstance().pullIfSetElem(Constants.FOLLOWS + user.idUser , idUser+ "");
                            refollowed = RMemoryAPI.getInstance().pullIfSetElem(Constants.FOLLOWS + idUser, user.idUser + "");
                            vc = RMemoryAPI.getInstance().pullIfSetElem("dc_users:comment:voice:users:" + user.idUser, idUser + "");

                        }

                        json.put("flwnum", usersShelf.userProfileStatisticInfo(idUser, 0, false));
                        json.put("fcount", usersShelf.userProfileStatisticInfo(idUser, 1, false));
                        json.put("mcount", usersShelf.userProfileStatisticInfo(idUser, 2, false));
                        json.put("chcount", usersShelf.userProfileStatisticInfo(idUser, 3, false));

                        json.put("idUser", usersEntity.getIdUser());
                        json.put("username", usersEntity.getUsername());
                        json.put("fullName", usersEntity.getFullname());
                        json.put("avatar", usersEntity.getAvatar());
                        json.put("wallpic", usersEntity.wallpic);
                        String stars = RMemoryAPI.getInstance()
                                .pullHashFromMemory(Constants.USER_KEY + usersEntity.idUser, "stars");
                        json.put("stars", stars==null?'0':stars);
                        json.put("location", usersEntity.country+" "+usersEntity.city);
                        json.put("social_link", user.social_links);
                        json.put("follows_bage", usersShelf.getFirstThreeFollowsAva(idUser));
                        json.put("followers_bage", usersShelf.getFirstThreeFollowersAva(idUser));
                        json.put("about", usersEntity.about);

                        json.put("reFollow", followed);
                        json.put("verified", false);
                        json.put("friend", refollowed);
                        json.put("vPermit", (vc || user.getIdUser() == idUser) ? 1 : 0);
                    }
                }

            } catch (Exception e) {
                log.error("ERROR IN WEB API ", e);
            }
        }
        return createResponse(json);
    }

    @GET
    @Path("/profile/statistic/info")
    @Produces("application/json")
    public Response getUserProfileInfo(@QueryParam("red") String idHash,
                                       @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        if (idHash != null && !idHash.isEmpty()) {
            try {
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
                if (user != null && user.getIdUser() != 0) {
                    UsersShelf usersShelf = new UsersShelf();

                    json.put("flwnum", usersShelf.userProfileStatisticInfo(user.getIdUser(), 0, false));
                    json.put("follows_cnt", usersShelf.userProfileStatisticInfo(user.getIdUser(), 1, false));
                    json.put("medias_cnt", usersShelf.userProfileStatisticInfo(user.getIdUser(), 2, true));
                    json.put("channels_cnt", usersShelf.userProfileStatisticInfo(user.getIdUser(), 3, true));
                } else {
                    json.put("error", EnumErrors.INVALID_HASH);
                }

            } catch (Exception e) {
                json.put("error", EnumErrors.UNKNOWN_ERROR);
                log.error("ERROR IN WEB API ", e);
            }
        } else {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
        }
        return createResponse(json);
    }

    @GET
    @Path("/profile/public/info")
    @Produces("application/json")
    public Response getUserPublicProfileInfo(@QueryParam("uid") long idUser,
                                             @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        if (idUser != 0) {
            try {
                UsersShelf usersShelf = new UsersShelf();
                DcUsersEntity user = usersShelf.getUserInfoById(idUser);
                if (user != null) {

                    json.put("flwnum", usersShelf.userProfileStatisticInfo(user.getIdUser(), 0, false));
                    json.put("fcount", usersShelf.userProfileStatisticInfo(user.getIdUser(), 1, false));
                    json.put("mcount", usersShelf.userProfileStatisticInfo(user.getIdUser(), 2, false));
                    json.put("chcount", usersShelf.userProfileStatisticInfo(user.getIdUser(), 3, false));
                } else {
                    json.put("error", EnumErrors.WRONG_ID_VALUE);
                }

            } catch (Exception e) {
                json.put("error", EnumErrors.UNKNOWN_ERROR);
                log.error("ERROR IN WEB API ", e);
            }
        } else {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
        }
        return createResponse(json);
    }

    @GET
    @Path("/rocket/ch/accept")
    @Produces("application/json")
    public Response acceptToJoinIntoChannel(@QueryParam("red") String idHash,
                                            @QueryParam("idch") long idChannel,
                                            @QueryParam("type") String type,
                                            @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        log.debug("acceptToJoinIntoChannel: {} {}", idHash, idChannel);
        if (idHash == null || idHash.isEmpty() || type == null || type.isEmpty() || idChannel <= 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {

                UsersShelf usersShelf = new UsersShelf();
                DcChannelsEntity dcChannelsEntity = new MediaShelf().getChannelDataByID(idChannel);

                if (dcChannelsEntity != null && !Objects.equals(user.getIdUser(), dcChannelsEntity.getIdUser())) {
                    if (type.equals("YES")) {
                        JSONObject updated = usersShelf.joinTheUserInChannel(user.getIdUser(), idChannel,
                                false, true, false);
                        if (updated.getBoolean("ispart")) {
                            errors = EnumErrors.ALREADY_JOINED;
                        }

                    } else if (type.equals("NO")) {
                        JSONObject updated = usersShelf.joinTheUserInChannel(user.getIdUser(), idChannel,
                                false, false, true);
                    } else {
                        errors = EnumErrors.WRONG_TYPE_VALUE;
                    }
                } else {
                    errors = EnumErrors.UNKNOWN_ERROR;
                }
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        log.debug("ERROR IN ACCEPT: {}", errors.toString());

        return createResponse(json);
    }

    @GET
    @Path("/rocket/ch/bow/accept")
    @Produces("application/json")
    public Response acceptToJoinIntoChannelByOwner(@QueryParam("red") String idHash,
                                                   @QueryParam("idch") long idChannel,
                                                   @QueryParam("idu") long idUser,
                                                   @QueryParam("type") String type,
                                                   @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        log.debug("acceptToJoinIntoChannelByOwner: {}", idHash);
        if (idHash == null || idHash.isEmpty() || type == null || type.isEmpty() || idChannel <= 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0 && idUser > 0) {

                UsersShelf usersShelf = new UsersShelf();
                DcUsersEntity usrCheck = usersShelf.getUserInfoById(idUser);
                if (usrCheck != null) {
                    DcChannelsEntity dcChannelsEntity = new MediaShelf().getChannelDataByID(idChannel);

                    if (dcChannelsEntity != null && Objects.equals(user.getIdUser(), dcChannelsEntity.getIdUser())) {
                        if (type.equals("YES")) {
                            log.debug("CHANNEL CHECK {}", dcChannelsEntity.toString());
                            JSONObject updated = usersShelf.joinTheUserInChannel(idUser, idChannel,
                                    true, true, false);

                            log.debug("IS_JOINED: {} {}", idUser, updated);
                            if (updated.getBoolean("ispart")) {
                                errors = EnumErrors.ALREADY_JOINED;
                            }
                        } else if (type.equals("NO")) {
                            usersShelf.joinTheUserInChannel(idUser, idChannel, true, false, true);
                        } else {
                            errors = EnumErrors.WRONG_TYPE_VALUE;
                        }
                    } else {
                        errors = EnumErrors.UNKNOWN_ERROR;
                    }
                } else {
                    errors = EnumErrors.WRONG_ID_VALUE;
                }
            } else {
                errors = EnumErrors.INVALID_HASH;
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);


        log.debug("ERROR IN ACCEPT: {}", errors.toString());

        return createResponse(json);
    }

    @GET
    @Path("/my/follow/{idpoint}")
    @Produces("application/json")
    public Response getMyFollowsActivity(@PathParam("idpoint") int idDirect,
                                         @QueryParam("red") String idHash,
                                         @QueryParam("off") long off,
                                         @QueryParam("lm") long limit,
                                         @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        List<UserShortInfo> followsEntities = new ArrayList<>();

        try {

            if (idHash != null && !idHash.isEmpty()) {
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user != null && user.getIdUser() != 0) {

                    UsersShelf usersShelf = new UsersShelf(lang);

                    if (idDirect == 2) // Who are following my videos
                    {
                        followsEntities = usersShelf.showMyFollowers(user.getIdUser(), user.getIdUser(), off, limit);
                    }
                    else if (idDirect == 1) //Who's video I am following
                    {
                        followsEntities = usersShelf.showMyFollowing(user.getIdUser(), user.getIdUser(), off, limit);
                    } else {
                        errors = EnumErrors.WRONG_TYPE_VALUE;
                    }
                } else {
                    errors = EnumErrors.INVALID_HASH;
                }

                log.debug("FOLLOW ACTIVITIES {}", followsEntities);
            } else {
                errors = EnumErrors.NULL_FIELD_ERROR;
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        Response response;

        if (errors == EnumErrors.NO_ERRORS) {
            response = createResponse(followsEntities);
        } else {
            json.put("error", errors);
            response = createResponse(json);
        }

        return response;
    }

    @GET
    @Path("/my/follow/{idref}/search")
    @Produces("application/json")
    public Response searchMyFollowsActivity(
            @PathParam("idref") int idref,
            @QueryParam("red") String idHash,
            @QueryParam("name") String name,
            @QueryParam("off") int off,
            @QueryParam("lm") int limit,
            @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        List<UserShortInfo> followsEntities = new ArrayList<>();

        try {

            if (idHash != null && !idHash.isEmpty() && name != null && !name.isEmpty()) {
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user != null && user.getIdUser() != 0) {

                    UsersShelf usersShelf = new UsersShelf(lang);

                    if (idref == 1) // Who I am following
                    {
                        followsEntities = usersShelf.showMyFollowsByName(name, user.getIdUser(), off, limit);
                    } else if (idref == 2) //Who others are following me
                    {
                        followsEntities = usersShelf.showMyFollowersByName(name, user.getIdUser(), off, limit);
                    } else if (idref == 3) {
                        followsEntities = usersShelf.showMyInterFollowingByName(name, user.getIdUser(), off, limit);
                    } else {
                        errors = EnumErrors.WRONG_TYPE_VALUE;
                    }
                } else {
                    errors = EnumErrors.INVALID_HASH;
                }

                log.debug("FOLLOW ACTIVITIES {}", followsEntities);
            } else {
                errors = EnumErrors.NULL_FIELD_ERROR;
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        Response response;

        if (errors == EnumErrors.NO_ERRORS) {
            response = createResponse(followsEntities);
        } else {
            json.put("error", errors);
            response = createResponse(json);
        }

        return response;
    }

    @GET
    @Path("/user/follow/{idref}/search")
    @Produces("application/json")
    public Response searchUserFollowsActivity(
                                @PathParam("idref") int idref,
                                @QueryParam("red") String idHash,
                                @QueryParam("idu") long idUser,
                                @QueryParam("name") String name,
                                @QueryParam("off") int off,
                                @QueryParam("lm") int limit,
                                @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        List<UserShortInfo> followsEntities = new ArrayList<>();

        try {

            if (idHash != null && !idHash.isEmpty() && name != null && !name.isEmpty()) {
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user != null && user.getIdUser() != 0 && idUser != 0) {

                    UsersShelf usersShelf = new UsersShelf(lang);

                    if (idref == 1) // Who I am following
                    {
                        followsEntities = usersShelf.showUserFollowsByName(name, user.getIdUser(), idUser, off, limit);
                    } else if (idref == 2) //Who others are following me
                    {
                        followsEntities = usersShelf.showUserFollowersByName(name, user.getIdUser(), idUser, off, limit);
                    } else {
                        errors = EnumErrors.WRONG_TYPE_VALUE;
                    }
                } else {
                    errors = EnumErrors.INVALID_HASH;
                }

                log.debug("FOLLOW ACTIVITIES {}", followsEntities);
            } else {
                errors = EnumErrors.NULL_FIELD_ERROR;
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        Response response;

        if (errors == EnumErrors.NO_ERRORS) {
            response = createResponse(followsEntities);
        } else {
            json.put("error", errors);
            response = createResponse(json);
        }

        return response;
    }

    @GET
    @Path("/channel/subs/list")
    @Produces("application/json")
    public Response getChannelFollowsActivity(@QueryParam("idch") long idChannel,
                                              @QueryParam("red") String idHash,
                                              @QueryParam("off") long off,
                                              @QueryParam("lm") long limit,
                                              @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        List<UserShortInfo> subsEntities = null;

        try {

            if (idHash != null && !idHash.isEmpty()) {
                UsersShelf usersShelf = new UsersShelf(lang);
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
                DcChannelsEntity entity = new MediaShelf().getChannelDataByID(idChannel);

                if (user != null && user.getIdUser() != 0 && entity != null && Objects.equals(entity.getIdUser(), user.getIdUser())) {
                    subsEntities = usersShelf.extractChannelSubscribers(idChannel, entity.getIdUser(), off, limit);
                } else {
                    errors = EnumErrors.INVALID_HASH;
                }

                log.debug("CHANNEL SUBS ACTIVITIES {}", subsEntities);
            } else {
                errors = EnumErrors.NULL_FIELD_ERROR;
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        Response response;

        if (errors == EnumErrors.NO_ERRORS) {
            response = createResponse(subsEntities);
        } else {
            json.put("error", errors);
            response = createResponse(json);
        }

        return response;
    }

    @GET
    @Path("/follow/{idpoint}/list")
    @Produces("application/json")
    public Response getUserFollowsActivity(@PathParam("idpoint") int idDirect,
                                           @QueryParam("uid") long idUser,
                                           @QueryParam("hash") String idHash,
                                           @QueryParam("off") long off,
                                           @QueryParam("lm") long limit,
                                           @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        List<UserShortInfo> followsEntities = new ArrayList<>();

        try {
            if (idHash == null || idHash.isEmpty()) {
                errors = EnumErrors.NULL_FIELD_ERROR;
            } else if (idUser > 0) {
                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user != null) {

                    UsersShelf usersShelf = new UsersShelf(lang);

                    if (idDirect == 1) // Who are following my videos
                    {
                        followsEntities = usersShelf.showMyFollowers(idUser, user.getIdUser(), off, limit);
                    }
                    else if (idDirect == 2) //Who's video I am following
                    {
                        followsEntities = usersShelf.showMyFollowing(idUser, user.getIdUser(), off, limit);
                    }
                    else {
                        errors = EnumErrors.WRONG_TYPE_VALUE;
                    }
                } else {
                    errors = EnumErrors.INVALID_HASH;
                }

                log.debug("FOLLOW USERS {}", followsEntities);
            } else {
                errors = EnumErrors.NULL_FIELD_ERROR;
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        Response response;

        if (errors == EnumErrors.NO_ERRORS) {
            response = createResponse(followsEntities);
        } else {
            json.put("error", errors);
            response = createResponse(json);
        }

        return response;
    }

    @POST
    @Produces("application/json")
    @Path("/rocket/ch/remove")
    public Response removeChannelByOwner(@FormParam("idHash") String idHash,
                                         @FormParam("idCh") long idChannel,
                                         @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        if (idChannel == 0 || idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
        }
        log.debug(idHash);
        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {

                UsersShelf usersShelf = new UsersShelf();
                DcChannelsEntity channelInfo = new MediaShelf().getChannelDataByID(idChannel);
                if (channelInfo != null && Objects.equals(channelInfo.getIdUser(), user.getIdUser())) {
                    EnumSQLErrors sqlErrors = usersShelf.removeTheChannelByOwner(user.getIdUser(), idChannel, channelInfo.getAvatar());
                    if (sqlErrors != EnumSQLErrors.OK) {
                        errors = EnumErrors.CHANNEL_REMOVE_ERROR;
                    }
                } else {
                    errors = EnumErrors.NO_CHANNEL_ERROR;
                }

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

    @GET
    @Produces("application/json")
    @Path("/activate")
    public Response activateBody(@QueryParam("red") String idRed,
                                 @HeaderParam("Accept-Language") String lang) {

        PrinciplesCreation creation = new PrinciplesCreation(lang);

        log.debug("ACTIVATION NUM {}", idRed);
        JSONObject json = null;

        if (idRed != null && !idRed.isEmpty()) {
            json = creation.processOfBodyActivation(idRed);
        } else {
            json = new JSONObject();
            json.put("error", EnumErrors.INVALID_HASH);
        }

        log.debug("ERROR IN ACTIVATION {}", json);
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/confirm/restore")
    public Response passwordConfirmation(@QueryParam("uid") String hash,
                                         @HeaderParam("Accept-Language") String lang) {

        PrinciplesRestore creation = new PrinciplesRestore();
        log.debug("Restoring password, hash {}",hash);

        EnumErrors errors = EnumErrors.NO_ERRORS;
        if (hash != null && !hash.isEmpty()) {
            errors = creation.confirmOfPasswordRestore(hash);
        } else {
            errors = EnumErrors.NULL_FIELD_ERROR;
        }

        JSONObject json = new JSONObject();
        json.put("error", errors);

        log.debug("ERROR IN PAS CONFIRMATION: {}", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/confirm/accept")
    public Response acceptPassword(@FormParam("red") String hash,
                                   @FormParam("sec") String password,
                                   @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        PrinciplesRestore creation = new PrinciplesRestore();

        JSONObject json = new JSONObject();

        if (hash == null || hash.isEmpty() || password == null || password.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        log.debug("HASH IN PASS ACCEPT: pas {} hash {}", password, hash);
        try {
            errors = creation.acceptPasswordRestore(password, hash);
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/remove/ch/media")
    public Response removeMediaFromChannel(@FormParam("red") String idHash,
                                           @FormParam("id_media") Long idMedia,
                                           @FormParam("idch") Long idChannel,
                                           @HeaderParam("Accept-Language") String lang) {

        log.debug("idHash {} idMedia {} idChannel {}", new Object[]{idHash, idMedia, idChannel});

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || idMedia == null || idChannel == null) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {
                long iduser = user.getIdUser();

                UsersShelf usersShelf = new UsersShelf();
                MediaShelf mediaShelf = new MediaShelf();

                boolean joined = usersShelf.amIjoinedToChannel(user.getIdUser(), idChannel);

                DcMediaEntity media = mediaShelf.retrieveMediaByIdValue(idMedia);
                DcChannelsEntity channel = mediaShelf.getChannelDataByID(idChannel);

                if (joined && media != null && channel != null &&
                        (media.getIdUser() == iduser || channel.getIdUser() == iduser)) {

                    int props = 0;

                    EnumSQLErrors sqlErrors = usersShelf.removeTheMediaFromChannel(idMedia, idChannel, props);

                    if (sqlErrors != EnumSQLErrors.OK) {
                        errors = EnumErrors.NO_CHANNEL_ERROR;
                    }
                } else {
                    errors = EnumErrors.WRONG_ID_VALUE;
                }
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            errors = EnumErrors.UNKNOWN_ERROR;
            log.error("ERROR IN WEB API ", e);
        }
        log.debug("ERROR: {}", errors);
        json.put("error", errors);
        return createResponse(json);
    }


    @GET
    @Produces("application/json")
    @Path("/leave/channel")
    public Response leaveFromChannel(@QueryParam("red") String idHash,
                                     @QueryParam("idch") Long idChannel,
                                     @HeaderParam("Accept-Language") String lang) {

        log.debug(idHash);

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || idChannel == null || idChannel == 0L) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            UsersShelf usersShelf = new UsersShelf();

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            DcChannelsEntity channel = new MediaShelf().getChannelDataByID(idChannel);

            if (user != null && user.getIdUser() != 0 && channel != null && channel.getIdUser() != user.getIdUser()) {
                boolean joined = usersShelf.amIjoinedToChannel(user.getIdUser(), idChannel);

                if (joined) {
                    boolean left = usersShelf.leaveTheChannel(user.getIdUser(), idChannel);
                    if (!left) {
                        errors = EnumErrors.UNKNOWN_ERROR;
                    }
                } else {
                    errors = EnumErrors.WRONG_ID_VALUE;
                }
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            errors = EnumErrors.UNKNOWN_ERROR;
            log.error("ERROR IN WEB API ", e);
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/unfollow/channel")
    public Response unfollowFromChannel(@QueryParam("red") String idHash,
                                        @QueryParam("idch") Long idChannel,
                                        @HeaderParam("Accept-Language") String lang) {

        log.debug(idHash);

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || idChannel == null || idChannel == 0L) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            UsersShelf usersShelf = new UsersShelf();

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            DcChannelsEntity channel = new MediaShelf().getChannelDataByID(idChannel);

            if (user != null && user.getIdUser() != 0 && channel != null) {
                usersShelf.unFollowByChannel(channel, idChannel);
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            errors = EnumErrors.UNKNOWN_ERROR;
            log.error("ERROR IN WEB API ", e);
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/kick/u/channel")
    public Response kickUserFromChannel(@FormParam("red") String idHash,
                                        @FormParam("idu") Long idUser,
                                        @FormParam("idch") Long idChannel,
                                        @HeaderParam("Accept-Language") String lang) {

        log.debug("KICK THE USER FROM CHANNEL: {}", idHash);

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || idChannel == null || idChannel == 0L || idUser == null) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {
                UsersShelf usersShelf = new UsersShelf();
                DcChannelsEntity entity = new MediaShelf().getChannelDataByID(idChannel);

                if (entity != null && Objects.equals(entity.getIdUser(), user.getIdUser()) && idUser != 0L) {
                    boolean left = usersShelf.leaveTheChannel(idUser, idChannel);

                    if (!left) {
                        errors = EnumErrors.UNKNOWN_ERROR;
                    }
                } else {
                    errors = EnumErrors.UNKNOWN_ERROR;
                }
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            errors = EnumErrors.UNKNOWN_ERROR;
            log.error("ERROR IN WEB API ", e);
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/content/sharing")
    public Response mediaSharingIncrement(@FormParam("red") String idHash,
                                          @FormParam("users") String idusers,
                                          @FormParam("idm") Long idMedia,
                                          @HeaderParam("Accept-Language") String lang) {

        log.debug("SHARING HASH: {}", idHash);

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || idMedia == null || idMedia == 0L || idusers == null) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() >= 0) {
                UsersShelf usersShelf = new UsersShelf();

                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        String[] usrs = idusers.split(",");
                        for(String idu: usrs) {
                            Notification notif = new Notification();
                            notif.setType(2); //share
                            notif.setIdMedia(idMedia);
                            notif.setIdFrom(user.idUser);
                            notif.setIdUser(Long.valueOf(idu));
                            notif.setUsername(user.username);
                            notif.setText(user.username+" shared a video");

                            ISystemDelivery systemDelivery = SystemDelivery
                                    .builder(notif).inbox();
                        }
                    }
                };

                thread.setName("thread-inbox-send:" + System.currentTimeMillis());
                thread.start();

            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            errors = EnumErrors.UNKNOWN_ERROR;
            log.error("ERROR IN WEB API ", e);
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/content/params")
    public Response mediaDeviceParams(@FormParam("red") String idHash,
                                      @FormParam("idm") Long idMedia,
                                      @FormParam("dname") String params,
                                      @HeaderParam("Accept-Language") String lang) {

        log.debug("SHARING HASH: {}", idHash);

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || idMedia == null || idMedia == 0L || params == null || params.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() >= 0) {
                MediaShelf mediaShelf = new MediaShelf();
                mediaShelf.saveMediaDeviceVendor(idMedia, params);
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            errors = EnumErrors.UNKNOWN_ERROR;
            log.error("ERROR IN WEB API ", e);
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/search/users")
    @Context
    public Response getUsersInfoByFragment(@QueryParam("frm") String frm,
                                           @QueryParam("lm") int lm,
                                           @QueryParam("off") int off,
                                           @HeaderParam("Accept-Language") String lang) {
        UsersShelf usersShelf = new UsersShelf(lang);
        List<UserShortInfo> entities = new ArrayList<>();

        log.debug("frm {} lm {} off {}", new Object[]{frm, lm, off});
        if (frm == null || frm.isEmpty() || frm.length() < 1 || frm.length() > 255) {
            return createResponse(entities);
        }
        frm = org.apache.commons.lang3.StringUtils.normalizeSpace(frm);
        entities = usersShelf.showAllUsersByName(frm.trim(), Formulas.DEFAULT_USER, off, lm);

        return createResponse(entities);
    }


    @GET
    @Produces("application/json")
    @Path("/content/list/mine/{idhash}")
    @Context
    public Response getMediaListByOwner(@PathParam("idhash") String idHash,
                                        @QueryParam("off") int off,
                                        @QueryParam("lm") int limit,
                                        @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf(lang);

        List<MediaPublicInfo> entities = new ArrayList<>();
        if (idHash == null || idHash.isEmpty()) {
            JSONObject json = new JSONObject();
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            log.debug("MINE VIDEOS {}", idHash);
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            if (user != null && user.getIdUser() != 0) {
                entities = usersShelf.extractMediaByUserID(user.getIdUser(), off, limit, true);
            }

        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
        }
        return createResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/event/list/mine/{idhash}")
    @Context
    public Response getMediaEventListByOwner(@PathParam("idhash") String idHash,
                                        @QueryParam("off") int off,
                                        @QueryParam("lm") int limit,
                                        @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf(lang);

        List<EventListEntity> entities = new ArrayList<>();
        if (idHash == null || idHash.isEmpty()) {
            JSONObject json = new JSONObject();
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            log.debug("MINE VIDEOS {}", idHash);
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            if (user != null && user.getIdUser() != 0) {
                entities = usersShelf.extractUserEventList(user.getIdUser(), off, limit);
            }

        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
        }
        return createResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/content/list/{iduser}")
    @Context
    public Response getMediaListByUser(@PathParam("iduser") long idUser,
                                       @QueryParam("off") int off,
                                       @QueryParam("lm") int limit,
                                       @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf(lang);

        List<MediaPublicInfo> entities = new ArrayList<>();

        try {
            if (idUser >= 0) {
                entities = usersShelf.extractMediaByUserID(idUser, off, limit, false);
            }
        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
        }
        return createResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/user/notice/list")
    @Context
    public Response getUserNotifyList(@QueryParam("red") String idHash,
                                      @HeaderParam("Accept-Language") String lang) {

        PrinciplesEdit userSetting = new PrinciplesEdit();

        DcNotificationTypes ntf = new DcNotificationTypes();
        if (idHash == null || idHash.isEmpty()) {
            return createResponse(null);
        }

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            if (user != null && user.getIdUser() != 0) {
                ntf = userSetting.getUserNotificationSettings(user.getIdUser());
            }

        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
        }

        return createResponse(ntf);
    }

    @GET
    @Produces("application/json")
    @Path("/user/ch/joined")
    @Context
    public Response getUserIfJoined(@QueryParam("idu") long idUser,
                                    @QueryParam("idc") long idChannel,
                                    @HeaderParam("Accept-Language") String lang) {


        JSONObject json = new JSONObject();
        boolean joined = false;
        if (idUser != 0 && idChannel != 0) {
            UsersShelf usersShelf = new UsersShelf();
            joined = usersShelf.amIjoinedToChannel(idUser, idChannel);
        }
        json.put("joined", joined ? 1 : 0);

        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/user/block/{id}")
    @Context
    public Response blockUserForComment(@FormParam("red") String hash,
                                        @FormParam("idm") long idMedia,
                                        @PathParam("id") long idUser,
                                        @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        json.put("error", EnumErrors.UNKNOWN_ERROR);
        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
            DcUsersEntity userToBlock = PrivateProperties.extractUserFullInfoById(idUser);
            String idu = RMemoryAPI.getInstance().pullHashFromMemory(Constants.MEDIA_KEY + idMedia, "id_user");

            if (hash == null || user == null || user.getIdUser() == 0) {
                json.put("error", EnumErrors.INVALID_HASH);
            } else if (user.getIdUser() != idUser && idMedia > 0 && userToBlock != null
                    && idUser != 0 && idu != null && Long.valueOf(idu).equals(user.getIdUser())) {
                UsersShelf shelf = new UsersShelf();
                EnumErrors errors = shelf.blockUserForComment(idMedia, user.getIdUser(), idUser);
                json.put("error", errors);
            } else {
                json.put("error", EnumErrors.WRONG_ID_VALUE);
            }
        } catch (Exception e) {
            log.error("Error in the REST API", e);
        }
        log.debug("Params response {}, form {}", json.toString(), hash + " " + idUser + " " + idMedia);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/remove/comment/{id}")
    @Context
    public Response removeUsersComment(@FormParam("red") String hash,
                                       @FormParam("idm") long idMedia,
                                       @PathParam("id") long idComment,
                                       @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        json.put("error", EnumErrors.UNKNOWN_ERROR);
        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);

            if (user != null && user.getIdUser() > 0 && idComment > 0 && idMedia > 0) {
                EnumErrors shelf = new UsersShelf().removeUsersComment(idComment, user.getIdUser(), idMedia);
                json.put("error", shelf);
            } else {
                json.put("error", EnumErrors.WRONG_ID_VALUE);
            }
        } catch (Exception e) {
            log.error("Error in the REST API", e);
        }
        log.debug("Params response {}, form {}", json.toString(), hash + " " + idComment + " " + idMedia);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/allow/voice/{idu}")
    @Context
    public Response allowVoiceUserForComment(@FormParam("red") String hash,
                                             @FormParam("idm") String idm,
                                             @PathParam("idu") long idUser,
                                             @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        json.put("error", EnumErrors.UNKNOWN_ERROR);
        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
            DcUsersEntity userToVoice = PrivateProperties.extractUserFullInfoById(idUser);

            if (user != null && user.getIdUser() != 0 && user.getIdUser() != idUser && idUser != 0 && userToVoice != null) {
                UsersShelf shelf = new UsersShelf();
                EnumErrors errors = shelf.allowUserForVoiceComment(user.getIdUser(), idUser, idm);
                json.put("error", errors);
            } else {
                json.put("error", EnumErrors.NULL_FIELD_ERROR);
            }
        } catch (Exception e) {
            log.error("Error in the REST API", e);
        }
        log.debug("Params response {}, form {}", json.toString(), hash + " " + idUser);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/deny/voice/{id}")
    @Context
    public Response denyVoiceUserForComment(@FormParam("red") String hash,
                                            @FormParam("idm") String idm,
                                            @PathParam("id") long idUser,
                                            @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        json.put("error", EnumErrors.UNKNOWN_ERROR);
        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
            DcUsersEntity userToDeny = PrivateProperties.extractUserFullInfoById(idUser);

            if (user != null && user.getIdUser() != 0 && userToDeny != null && userToDeny.getIdUser() != 0) {
                UsersShelf shelf = new UsersShelf();
                EnumErrors errors = shelf.denyUserVoiceComment(user.getIdUser(), idUser, idm);
                json.put("error", errors);
            } else {
                json.put("error", EnumErrors.NULL_FIELD_ERROR);
            }
        } catch (Exception e) {
            log.error("Error in the REST API", e);
        }

        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/user/voice/list")
    @Context
    public Response voiceAllowedUsersList(@FormParam("red") String hash,
                                          @FormParam("off") int off,
                                          @FormParam("lm") int limit,
                                          @HeaderParam("Accept-Language") String lang) {

        List<UserShortInfo> resp = new ArrayList<>();
        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);

            if (user != null && user.getIdUser() != 0) {
                resp = new UsersShelf().voiceUsersList(user.getIdUser(), off, limit);
            }
        } catch (Exception e) {
            log.error("Error in the REST API", e);
        }

        return createResponse(resp);
    }

    @POST
    @Produces("application/json")
    @Path("/user/release/{id}")
    @Context
    public Response freeUserForComment(@FormParam("red") String hash,
                                       @PathParam("id") long idUser,
                                       @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        json.put("error", EnumErrors.UNKNOWN_ERROR);
        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
            DcUsersEntity userToBlock = PrivateProperties.extractUserFullInfoById(idUser);

            if (user != null && user.getIdUser() != 0 && userToBlock != null && userToBlock.getIdUser() != 0) {
                RMemoryAPI.getInstance().delFromSetElem("dc_users:comment:blocked:users:" + user.getIdUser(), idUser + "");
                json.put("error", EnumErrors.NO_ERRORS);
            } else {
                json.put("error", EnumErrors.NULL_FIELD_ERROR);
            }
        } catch (Exception e) {
            log.error("Error in the REST API", e);
        }

        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/user/block/list")
    @Context
    public Response blockedUsersListForComment(@FormParam("red") String hash,
                                               @HeaderParam("Accept-Language") String lang) {

        List<UserShortInfo> resp = new ArrayList<>();
        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);

            if (user != null && user.getIdUser() != 0) {
                resp = new UsersShelf().blockedUsersList(user.getIdUser(), 1);
            }
        } catch (Exception e) {
            log.error("Error in the REST API", e);
        }

        return createResponse(resp);
    }

    @POST
    @Produces("application/json")
    @Path("/user/notice/save")
    @Context
    public Response saveUserNotification(@FormParam("red") String idHash,
                                         @FormParam("new_media") boolean new_media,
                                         @FormParam("want_to_join") boolean want_to_join,
                                         @FormParam("suggest_to_join") boolean suggest_to_join,
                                         @FormParam("liked") boolean liked,
                                         @FormParam("commented") boolean commented,
                                         @FormParam("live_started") boolean live_started,
                                         @FormParam("news") boolean newsfeed,
                                         @FormParam("video_status") boolean video_status,
                                         @FormParam("type") int type,
                                         @HeaderParam("Accept-Language") String lang) {

        /*Type 1 - PUSH NOTIFICATIONS, 2 - EMAIL NOTIFICATION*/
        PrinciplesEdit edit = new PrinciplesEdit();
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        log.debug(idHash);

        if (idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {

                /*DcUserSettings ntf = edit.getUserNotificationSettings(user.getIdUser());

                log.debug("WHAT WE HAD: {}", ntf.toString());

                if (type == 1) {
                    ntf.getLiked().setNotification(liked);
                    ntf.getWant_to_join().setNotification(want_to_join);
                    ntf.getNew_media().setNotification(new_media);
                    ntf.getSuggest_to_join().setNotification(suggest_to_join);
                    ntf.getLive_started().setNotification(live_started);
                    ntf.getCommented().setNotification(commented);
                    ntf.getNewsfeed().setNotification(newsfeed);
                } else if (type == 2) {
                    ntf.getLiked().setEmail(liked);
                    ntf.getWant_to_join().setEmail(want_to_join);
                    ntf.getNew_media().setEmail(new_media);
                    ntf.getSuggest_to_join().setEmail(suggest_to_join);
                    ntf.getLive_started().setEmail(live_started);
                    ntf.getCommented().setEmail(commented);
                    ntf.getNewsfeed().setEmail(newsfeed);
                    ntf.getVideo_status().setEmail(video_status);
                } else */
                {
                    errors = EnumErrors.WRONG_TYPE_VALUE;
                }
                if (errors == EnumErrors.NO_ERRORS) {
                    // log.debug("WHAT WE HAVE: {}", ntf.toString());
                    //EnumSQLErrors sqlErrors = edit.changeNotifications(ntf, user.getIdUser());
                    /*if (sqlErrors != EnumSQLErrors.OK) {
                        errors = EnumErrors.UNKNOWN_ERROR;
                    }*/
                }
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/user/notification/save")
    @Context
    public Response saveUserNotification(@FormParam("red") String idHash,
                                         @FormParam("live") boolean live,
                                         @FormParam("promo") boolean promo,
                                         @FormParam("follower") boolean follower,
                                         @FormParam("comment") boolean comment,
                                         @FormParam("channel") boolean channel,
                                         @FormParam("inbox") boolean inbox,
                                         @FormParam("type") int type,
                                         @HeaderParam("Accept-Language") String lang) {

        /*Type 1 - PUSH NOTIFICATIONS, 2 - EMAIL NOTIFICATION
        *
        * .update("live", new FlagUpdate(true))
                    .update("promo", new FlagUpdate(true))
                    .update("follower", new FlagUpdate(true))
                    .update("comment", new FlagUpdate(true))
                    .update("exception", new SetUpdate())
                    .update("channel", new FlagUpdate(true));
                    */
        PrinciplesEdit edit = new PrinciplesEdit();
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        log.debug(idHash);

        if (idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {

                DcNotificationTypes ntf = edit.getUserNotificationSettings(user.getIdUser());

                log.debug("WHAT WE HAD: {}", ntf.toString());

                if (type == 1) {
                    ntf.push.live = live;
                    ntf.push.channel = channel;
                    ntf.push.follower = follower;
                    ntf.push.promo = promo;
                    ntf.push.comment = comment;
                    ntf.push.inbox = inbox;
                } else if (type == 2) {
                    ntf.email.live = live;
                    ntf.email.channel = channel;
                    ntf.email.follower = follower;
                    ntf.email.promo = promo;
                    ntf.email.comment = comment;
                    ntf.push.inbox = inbox;
                } else {
                    errors = EnumErrors.WRONG_TYPE_VALUE;
                }
                if (errors == EnumErrors.NO_ERRORS) {
                    log.debug("WHAT WE HAVE: {}", ntf.toString());
                    EnumSQLErrors sqlErrors = edit.changeNotifications(ntf, user.getIdUser());
                    if (sqlErrors != EnumSQLErrors.OK) {
                        errors = EnumErrors.UNKNOWN_ERROR;
                    }
                }
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }


    @GET
    @Produces("application/json")
    @Path("/user/mail/un_subscribe")
    @Context
    public Response offUserEmailNotification(@QueryParam("eid") String emlHash,
                                             @QueryParam("eml") String email,
                                             @QueryParam("sub_type") int subType,
                                             @HeaderParam("Accept-Language") String lang) {

        /*Type 1 - likes, 2 - want_toJoin, 3 - new_media, 4 - suggestion, 5 - live_started, 6 - commented, 7 - newsfeed */
        PrinciplesEdit edit = new PrinciplesEdit();
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        log.debug("EMAIL HASH {}", emlHash);

        if (emlHash == null || emlHash.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        try {
            errors = edit.unsubscribeFromEmailNotification(emlHash.trim(), email.trim(), subType);
        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/user/channel/subscribe")
    @Context
    public Response subscribeToChannel(@FormParam("red") String idHash,
                                       @FormParam("idch") Long idChannel) {
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || idChannel == null || idChannel == 0L) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {
                UsersShelf usersShelf = new UsersShelf();
                DcChannelsEntity entity = new MediaShelf().getChannelDataByID(idChannel);

                if (entity != null && !Objects.equals(entity.getIdUser(), user.getIdUser())) {
                    usersShelf.followByChannel(entity, user.getIdUser());
                    if (errors == EnumErrors.NO_ERRORS) {
                        Notification notification = new Notification();
                        notification.setType(21);
                        notification.setIdChannel(idChannel);
                        notification.setText(entity.title);
                        notification.setUsername(user.username);
                        notification.setIdFrom(user.idUser);
                        notification.setIdUser(entity.idUser);

                        ISystemDelivery systemDelivery = SystemDelivery
                                .builder(notification).push();

                    }
                } else {
                    errors = EnumErrors.UNKNOWN_ERROR;
                }
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            errors = EnumErrors.UNKNOWN_ERROR;
            log.error("ERROR IN WEB API ", e);
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/user/channel/unsubscribe")
    @Context
    public Response unSubscribeFromChannel(@FormParam("red") String idHash,
                                           @FormParam("idch") Long idChannel) {
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || idChannel == null || idChannel == 0L) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {
                UsersShelf usersShelf = new UsersShelf();
                DcChannelsEntity entity = new MediaShelf().getChannelDataByID(idChannel);

                if (entity != null && !Objects.equals(entity.getIdUser(), user.getIdUser())) {
                    usersShelf.unFollowByChannel(entity, user.getIdUser());
                } else {
                    errors = EnumErrors.UNKNOWN_ERROR;
                }
            } else {
                errors = EnumErrors.INVALID_HASH;
            }

        } catch (Exception e) {
            errors = EnumErrors.UNKNOWN_ERROR;
            log.error("ERROR IN WEB API ", e);
        }

        json.put("error", errors);
        return createResponse(json);
    }


    @POST
    @Produces("application/json")
    @Path("/user/local/save")
    @Context
    public Response saveUserPersonalLocal(@FormParam("red") String idHash,
                                          @FormParam("local") String location,
                                          @HeaderParam("Accept-Language") String lang) {

        PrinciplesEdit edit = new PrinciplesEdit();
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        log.debug("saveUserPersonalLocal {}, {}", location, idHash);

        Pattern p_1 = Pattern.compile("^(\\d)([,\\d]{0,8})\\d$");
        Pattern p_2 = Pattern.compile("\\d");

        if (location == null || location.isEmpty()
                || (location.length() == 1 && !p_2.matcher(location).matches())
                || !p_1.matcher(location).matches()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        if (idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }


        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
            if (user != null && user.getIdUser() != 0) {
                EnumSQLErrors sqlErrors = edit.setUserLocal(location, user.getIdUser());
                if (sqlErrors != EnumSQLErrors.OK) {
                    errors = EnumErrors.UNKNOWN_ERROR;
                }
            }

        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }


    @POST
    @Produces("application/json")
    @Path("/content/remove")
    @Context
    public Response disableTheMediaByOwner(@FormParam("idHash") String idHash,
                                           @FormParam("idm") final long idMedia,
                                           @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty() || idMedia == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user != null && user.getIdUser() != 0) {
                DcMediaEntity infoMem = new MediaShelf().retrieveMediaByIdValue(idMedia);

                if (infoMem == null || !Objects.equals(user.getIdUser(), infoMem.getIdUser())) {
                    errors = EnumErrors.MEDIA_REMOVE_ERROR;
                } else {
                    EnumSQLErrors sqlErrors = usersShelf.removeTheMediaByOwner(user.getIdUser(), idMedia);
                    if (sqlErrors != EnumSQLErrors.OK) {
                        errors = EnumErrors.MEDIA_REMOVE_ERROR;
                    }
                }

            } else {
                errors = EnumErrors.INVALID_HASH;
            }


        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        /*if (errors == EnumErrors.NO_ERRORS) {
            SocketCoordinator.sendLiveDeletedNotification(idMedia);
        }*/ //TODO

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces("application/json")
    @Path("/publish/feedback")
    @Context
    public Response portalFeedback(@FormParam("idHash") String idHash,
                                   @FormParam("text") String text,
                                   @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        log.debug(idHash);
        if (idHash == null || idHash.isEmpty() || text == null || text.isEmpty() || text.length() > 5000) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            DcUsersEntity usersEntity = PrivateProperties.extractUserFullInfo(idHash);

            if (usersEntity != null) {
                EnumSQLErrors sqlErrors = usersShelf.publishFeedback(usersEntity.getIdUser(), text);
                if (sqlErrors != EnumSQLErrors.OK) {
                    errors = EnumErrors.PUBLISH_ERR;
                }

            } else {
                errors = EnumErrors.INVALID_HASH;
            }
        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        log.debug("SENDING FEEDBACK ERR STATUS: {}", errors);
        json.put("error", errors);
        return createResponse(errors);
    }

    @POST
    @Produces("application/json")
    @Path("/publish/contactus")
    @Context
    public Response portalContact(@FormParam("name") String name,
                                  @FormParam("email") String email,
                                  @FormParam("phone") String phone,
                                  @FormParam("country") String country,
                                  @FormParam("text") String text,
                                  @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();


        if (name == null || name.isEmpty() || email == null || email.isEmpty()
                || text == null || text.isEmpty() || country == null || country.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {
            errors = usersShelf.publishContacUs(name, email, phone, country, text);

        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/media/choose/thumb")
    public Response editMediaThumbnailByOwner(@FormParam("red") String idHash,
                                              @FormParam("idm") long idMedia,
                                              @FormParam("t") int thumb,
                                              @HeaderParam("Accept-Language") String lang) throws IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        MediaShelf mediaShelf = new MediaShelf();

        try {

            DcUsersEntity usersEntity = PrivateProperties.extractUserFullInfo(idHash);

            if (usersEntity != null && usersEntity.getIdUser() != 0 && (thumb > 0 && thumb < 4)) {

                long id_user = usersEntity.getIdUser();
                DcMediaEntity mediaInfo = mediaShelf.retrieveMediaByIdValue(idMedia);
                if (mediaInfo != null && mediaInfo.getIdUser() == id_user) {
                    mediaShelf.alertMediaThumbnail(idMedia, id_user, thumb);
                } else {
                    errors = EnumErrors.UNKNOWN_ERROR;
                }
            } else {
                errors = EnumErrors.UNKNOWN_ERROR;
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
    @Path("/user/lang/save")
    public Response editUserLanguage(@FormParam("red") String idHash,
                                     @FormParam("lg") String languages,
                                     @FormParam("in") int checkin,
                                     @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        UsersShelf usersShelf = new UsersShelf();

        try {

            DcUsersEntity usersEntity = PrivateProperties.extractUserFullInfo(idHash);


            if (usersEntity != null && usersEntity.getIdUser() != 0 && languages != null) {
                long id_user = usersEntity.getIdUser();
                usersShelf.addExtraLanguageOfUser(languages, id_user, checkin);
            } else {
                errors = EnumErrors.NULL_FIELD_ERROR;
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
    @Path("/user/lang/list")
    public Response listOfUserLanguages(@FormParam("red") String idHash,
                                        @HeaderParam("Accept-Language") String lang) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        UsersShelf usersShelf = new UsersShelf();

        try {

            DcUsersEntity usersEntity = PrivateProperties.extractUserFullInfo(idHash);

            if (usersEntity != null && usersEntity.getIdUser() != 0) {
                long id_user = usersEntity.getIdUser();
                Collection<String> list = usersShelf.listOfuserLanguages(id_user);
                json.put("languages", list);
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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/logs/checkupdates")
    public Response getUserActivityLastChange(@QueryParam("red") String idHash,
                                              @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();
        ChangeLogs userLogs = null;

        try {

            DcUsersEntity usersEntity = PrivateProperties.extractUserFullInfo(idHash);

            if (usersEntity != null && usersEntity.getIdUser() != 0) {
                userLogs = usersShelf.userLastChangeLogs(usersEntity.getIdUser());
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
        }

        return createResponse(userLogs);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/logs/getlast")
    public Response listOfUserActivityChanges(@QueryParam("red") String idHash,
                                              @QueryParam("id") Long logID,
                                              @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();
        List<ChangeLogs> userLogList = new ArrayList<>();

        try {
            DcUsersEntity usersEntity = PrivateProperties.extractUserFullInfo(idHash);

            if (usersEntity != null && usersEntity.getIdUser() != 0) {
                userLogList = usersShelf.userChangeLogDiffs(usersEntity.getIdUser(), logID);
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
        }

        return createResponse(userLogList);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/media/thumb/list")
    public Response extractMediaThumbnailByOwner(@QueryParam("red") String idHash,
                                                 @QueryParam("idm") long idMedia,
                                                 @HeaderParam("Accept-Language") String lang) throws IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        MediaShelf mediaShelf = new MediaShelf();

        try {

            DcUsersEntity usersEntity = PrivateProperties.extractUserFullInfo(idHash);

            if (usersEntity != null && usersEntity.getIdUser() != 0) {

                long id_user = usersEntity.getIdUser();
                DcMediaEntity mediaInfo = mediaShelf.retrieveMediaByIdValue(idMedia);
                if (mediaInfo != null && mediaInfo.getIdUser() == id_user) {
                    SecureLinkWrapper secureLinkWrapper = new SecureLinkWrapper();
                    secureLinkWrapper.fillField(id_user, idMedia, "media/v");
                    List<String> lst = new CopyOnWriteArrayList<>();
                    for (int ik = 1; ik <= 3; ik++) {
                        lst.add(secureLinkWrapper.wrapSecureLinkForThumbEdit(ik));

                    }
                    json.put("urls", lst);

                } else {
                    errors = EnumErrors.UNKNOWN_ERROR;
                }
            } else {
                errors = EnumErrors.UNKNOWN_ERROR;
            }

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            errors = EnumErrors.UNKNOWN_ERROR;
        }

        if (errors != EnumErrors.NO_ERRORS) {
            json.put("error", errors);
        }

        return createResponse(json);
    }


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/media/edit")
    public Response editMediaByOwner(@FormParam("idHash") String idHash,
                                     @FormParam("idMedia") long idMedia,
                                     @FormParam("title") String title,
                                     @FormParam("description") String description,
                                     @FormParam("tags") String tags,
                                     @FormParam("idChannel") long idChannel,
                                     @HeaderParam("Accept-Language") String lang) throws IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        MediaShelf mediaShelf = new MediaShelf();
        Pattern alphanumeric = Pattern.compile("^\\s+$");

        if (title == null || title.isEmpty() || alphanumeric.matcher(title).matches() || title.length() < 3 || title.length() > 500
                || description == null || description.length() > 1000
                || idHash == null || idHash.isEmpty() || idMedia == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);

        }
        UsersShelf usersShelf = new UsersShelf();
        try {


            DcUsersEntity usersEntity = PrivateProperties.extractUserFullInfo(idHash);

            if (usersEntity != null && usersEntity.getIdUser() != 0) {
                DcMediaEntity mediaProperties = mediaShelf.retrieveMediaByIdValue(idMedia);
                if (mediaProperties != null) {

                    if (idChannel != 0 && !usersShelf.amIjoinedToChannel(usersEntity.idUser, idChannel)) {
                        json.put("error", EnumErrors.NO_CHANNEL_ERROR);
                        return createResponse(json);
                    }

                    Set<String> tagList = null;
                    if (tags == null || tags.isEmpty()) {
                        tags = "";
                    }

                    tagList = TagTokenizer.normilizeTag(tags);
                    log.debug("Tags: {}", tagList.toString());

                    mediaProperties.setIdMedia(idMedia);
                    mediaProperties.setTitle(title);
                    mediaProperties.setDescription(description);
                    mediaProperties.setTags(tagList);
                    mediaProperties.setIdChannel(idChannel);

                    log.debug("Media edit vals: {}", mediaProperties.toString());

                    mediaShelf.updateContentInfo(mediaProperties);
                } else {
                    errors = EnumErrors.NO_MEDIA_FOUND;
                }
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
    @Path("/media/edit/video/transpose")
    /*ROTATION ANGLE POINT 1-90_ClockWise, 0-default, -1-90_CounterClockwise, 2-180_upsideDown*/
    public Response editVideoRotationByOwner(@FormParam("red") String idHash,
                                             @FormParam("idm") long idMedia,
                                             @FormParam("r") int rotation,
                                             @HeaderParam("Accept-Language") String lang) throws IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();

        log.debug("idHash {}- idMedia {}- rotation {}", new Object[]{idHash, idMedia, rotation});

        if (idHash == null || idHash.isEmpty() || idMedia <= 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        try {

            DcUsersEntity usersEntity = PrivateProperties.extractUserFullInfo(idHash);

            if (usersEntity != null && usersEntity.getIdUser() != 0 && idMedia != 0) {


                MediaShelf mediaShelf = new MediaShelf();
                String status = mediaShelf.checkMediaStatus(idMedia);
                if (!status.equals("COMPLETED")) {
                    json.put("error", EnumErrors.UNKNOWN_ERROR);
                    return createResponse(json);
                }

                DcMediaEntity mediaInfoMem = mediaShelf.retrieveMediaByIdValue(idMedia);

                if (usersEntity.getIdUser() == mediaInfoMem.getIdUser()) {
                    log.debug("EXECUTER TRANSPOSE");
                    SendCompressingSignal compressingSignal = new SendCompressingSignal();
                    String errStat = compressingSignal.sendToTranspose(idMedia, usersEntity.getIdUser(), rotation);

                    if (errStat != null && !errStat.isEmpty()) {
                        errors = EnumErrors.valueOf(errStat);
                    }
                }
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
    @Path("playlist/add")
    public Response addNewPlaylist(@FormParam("title") String title,
                                   @FormParam("descr") String descr,
                                   @FormParam("idm") Long idMedia,
                                   @FormParam("hash") String idHash) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();

        log.debug("idHash {} idMedia {}", idHash, idMedia);

        if (idHash == null || idHash.isEmpty() || title == null || title.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user != null && user.getIdUser() != 0) {
            new MediaShelf().createPlaylist(title, descr, idMedia, user.getIdUser());
        } else {
            errors = EnumErrors.INVALID_HASH;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("playlist/upd")
    public Response updPlaylist(@FormParam("idp") String ID,
                                @FormParam("title") String title,
                                @FormParam("descr") String descr,
                                @FormParam("hash") String idHash) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();

        log.debug("idHash {} title {}", idHash, title);

        if (idHash == null || idHash.isEmpty() || title == null || title.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        String id_author = RMemoryAPI.getInstance()
                .pullHashFromMemory(Constants.MEDIA_PLAYLIST+ID, "id_user");
        if (user != null && user.getIdUser() != 0
                && id_author!=null && id_author.equals(user.getIdUser()+"")) {
            new MediaShelf().updatePlaylist(ID,title,descr);
        } else {
            errors = EnumErrors.INVALID_HASH;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("playlist/del")
    public Response updPlaylist(@FormParam("idp") String ID,
                                @FormParam("hash") String idHash) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();

        log.debug("idHash {} ID {}", idHash, ID);

        if (idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        String id_author = RMemoryAPI.getInstance()
                .pullHashFromMemory(Constants.MEDIA_PLAYLIST+ID, "id_user");
        if (user != null && user.getIdUser() != 0
                && id_author!=null && id_author.equals(user.idUser+"")) {
            new MediaShelf().deletePlaylist(ID, user.idUser);
        } else {
            errors = EnumErrors.INVALID_HASH;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("playlist/add/media")
    public Response addMediaToPlaylist(@FormParam("idp") String ID,
                                       @FormParam("idm") Long idMedia,
                                       @FormParam("hash") String idHash) {

        EnumErrors errors = EnumErrors.NO_ERRORS;

        JSONObject json = new JSONObject();

        log.debug("idHash {} ID {}", idHash, ID);

        if (idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        String id_author = RMemoryAPI.getInstance()
                .pullHashFromMemory(Constants.MEDIA_PLAYLIST+ID, "id_user");
        if (user != null && user.getIdUser() != 0
                && id_author!=null && id_author.equals(user.idUser+"")) {
            new MediaShelf().addMediaInPlaylist(idMedia, ID);
        } else {
            errors = EnumErrors.INVALID_HASH;
        }

        json.put("error", errors);
        return createResponse(json);
    }
}
