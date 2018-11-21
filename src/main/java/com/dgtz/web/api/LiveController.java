package com.dgtz.web.api;

import com.brocast.riak.api.beans.DcMediaEntity;
import com.brocast.riak.api.beans.DcUsersEntity;
import com.brocast.riak.api.beans.LiveProps;
import com.dgtz.api.beans.LiveMediaInfo;
import com.dgtz.api.contents.LiveShelf;
import com.dgtz.api.contents.MediaShelf;
import com.dgtz.api.enums.EnumErrors;
import com.dgtz.api.feature.SystemDelivery;
import com.dgtz.api.settings.ISystemDelivery;
import com.dgtz.api.utils.LiveUrlGenerator;
import com.dgtz.api.utils.TagTokenizer;
import com.dgtz.db.api.beans.ScreenRotation;
import com.dgtz.db.api.domain.MediaStatus;
import com.dgtz.db.api.domain.Notification;
import com.dgtz.db.api.enums.EnumNotification;
import com.dgtz.db.api.enums.EnumSQLErrors;
import com.dgtz.mcache.api.factory.Constants;
import com.dgtz.mcache.api.factory.RMemoryAPI;
import com.dgtz.web.api.tools.Formulas;
import com.dgtz.web.api.tools.SendCompressingSignal;
import com.dgtz.web.api.tools.SpecificGeoLocation;
import com.dgtz.web.api.tools.UserStatisticParser;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Created by sardor on 1/2/14.
 */

@Path("/uplive")
public class LiveController extends Resource {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LiveController.class);

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/live/publish")
    public Response publishLive(@FormParam("title") String title,
                                @FormParam("idcateg") long idCateg,
                                @FormParam("idch") long idChannel,
                                @FormParam("tg") String tags,
                                @FormParam("location") String location,
                                @FormParam("idHash") String idHash,
                                @FormParam("dname") String dname,
                                @FormParam("download") int downloadable, //0 - false, 1=true
                                @FormParam("carrier") String carrier,
                                @FormParam("region") String region,
                                @FormParam("networkLocation") String networkLocation,
                                @HeaderParam("X-Forwarded-For") String IP,
                                @HeaderParam("User-Agent") String agent,
                                @HeaderParam("Accept-Language") String lang) throws IOException {

        IP = IP != null ? IP.split(",")[0] : null;
        JSONObject json = new JSONObject();
        LiveMediaInfo liveMediaInfo = new LiveMediaInfo();
        MediaShelf mediaShelf = new MediaShelf(lang);
        log.debug("LIVE JAVA PUBLISH device {} idHash {}", dname, idHash);

        Pattern alphanumeric = Pattern.compile("^\\s+$");
        boolean isBlockedIP = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:ip", IP + "");
        title = (title == null || title.isEmpty()) ? "undefined" : title;
        if (isBlockedIP || alphanumeric.matcher(title).matches() || idHash == null || idHash.isEmpty()) {
            log.error("BLOCKED IP ? :", isBlockedIP);
            json.put("error", EnumErrors.PUBLISH_ERR);
            return createResponse(json);
        }

        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user == null
                    || RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", user.getIdUser() + "")
                    || user.idUser == 0) {
                log.error("Null, Anon or Blocked user:", user != null ? user.toString() : null);
                json.put("error", EnumErrors.PUBLISH_ERR);
                return createResponse(json);
            }

            Long idUser = user.idUser;

            liveMediaInfo.setTitle(title.trim());
            liveMediaInfo.setTags(tags == null ? "" : tags);
            liveMediaInfo.setDescription("");
            liveMediaInfo.setIdCateg(1l); //video
            liveMediaInfo.setIdChannel(idChannel);
            liveMediaInfo.setIdHash(idHash);
            liveMediaInfo.setProcess(1);
            liveMediaInfo.setIdUser(idUser);
            liveMediaInfo.setUser(user);
            liveMediaInfo.setDebate(false);
            liveMediaInfo.setMethod("live");
            liveMediaInfo.setDownload(downloadable);

            /*Don't worry all is OK,idmedia and idlive are identical*/
            long idLive = RMemoryAPI.getInstance().currentTime(); //liveShelf.extractUniqueIdForLive();

            String[] parsedRtmpUrl = LiveUrlGenerator.buildRTMPUrl(idUser, dname, idLive);

            String rtmpUrl = "rtmp://" + parsedRtmpUrl[0] + "/" + parsedRtmpUrl[1] + "/" + parsedRtmpUrl[2];
            String htmlUrl = "https://hls.brocast.com/" + idLive + "/index.m3u8";

            liveMediaInfo.setRtmpUrl(rtmpUrl);
            liveMediaInfo.setParsedRtmpUrl(parsedRtmpUrl);
            liveMediaInfo.setIdLive(idLive);
            liveMediaInfo.setStop(false);
            liveMediaInfo.setHttpUrl(htmlUrl);

            if (dname != null && !dname.isEmpty()) {
                mediaShelf.saveMediaDeviceVendor(idLive, dname);
            }

            log.debug("TRYING GET LIVE LOCATIONS: IP {}, GPS {}", IP, location);
            int platformType = UserStatisticParser.parseAndSave(idLive, user.getIdUser(), 20, IP, dname, agent);

            if (platformType == 2) {
                String header = SpecificGeoLocation.geoLocationByWEBIP(IP);
                if (header.contains("@") && (location == null || location.isEmpty() || location.equals("0.0 0.0"))) {
                    location = (header.replace("@", " "));
                } else if (header.contains("@")) {
                    location = (SpecificGeoLocation.compareGeoDistance(location, header.replace("@", " ")));
                }
            }

            if (location != null && !location.isEmpty() && !location.equals("0.0 0.0")) {
                liveMediaInfo.setLatlong(location);
                location = mediaShelf.modifyLocation(location, idLive);
                liveMediaInfo.setLocation(location);
            } else {
                liveMediaInfo.setLocation("");
            }

            String langCode = new MediaShelf().getLanguageCodeByLocation(IP, location);

            liveMediaInfo.setPlatformType(platformType);
            liveMediaInfo.setLang(langCode);

            RMemoryAPI.getInstance().pushHashToMemory(Constants.LIVE_KEY + parsedRtmpUrl[2], "id_user", idUser + "");
            RMemoryAPI.getInstance().pushHashToMemory(Constants.LIVE_KEY + parsedRtmpUrl[2], "id_live", idLive + "");

            RMemoryAPI.getInstance().pushElemToMemory(Constants.LIVE_KEY + "publish:" + idLive, 5, liveMediaInfo.toString());


            json.put("idLive", idLive);
            json.put("rtmpUrl", rtmpUrl);

        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            json.put("error", EnumErrors.UNKNOWN_ERROR);
        }

        log.debug(json.toString());

        return createResponse(json);
    }


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/live/debate/init")
    public Response publishLive(@FormParam("idHash") String idHash,
                                @FormParam("idLive") long idOwnerLive,
                                @FormParam("region") String region,
                                @HeaderParam("X-Forwarded-For") String IP,
                                @HeaderParam("User-Agent") String agent,
                                @HeaderParam("Accept-Language") String lang) throws IOException {

        IP = IP != null ? IP.split(",")[0] : null;
        JSONObject json = new JSONObject();
        log.debug("LIVE keep on PUBLISH idHash {}", idHash);
        boolean isBlockedIP = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:ip", IP + "");
        if (isBlockedIP || idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.PUBLISH_ERR);
            return createResponse(json);
        }

        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user == null
                    || RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", user.getIdUser() + "")
                    || user.idUser == 0
                    || idOwnerLive < 1) {
                log.error("Null, Anon or Blocked user:", user != null ? user.toString() : null);
                json.put("error", EnumErrors.PUBLISH_ERR); //TODO create enum BLOCKED
                return createResponse(json);
            }


            Long idUser = user.idUser;

            Long idLive = RMemoryAPI.getInstance().currentTime();
            String[] parsedRtmpUrl = LiveUrlGenerator.buildRTMPUrl(idUser, idLive);

            String rtmpUrl = "rtmp://live.debate.brocast.com/debate/" + parsedRtmpUrl[2];

            RMemoryAPI.getInstance().pushHashToMemory(Constants.LIVE_KEY + parsedRtmpUrl[2], "id_user", idUser + "");
            RMemoryAPI.getInstance().pushHashToMemory(Constants.LIVE_KEY + parsedRtmpUrl[2], "id_live", idLive + "");

            String ownerAlive = RMemoryAPI.getInstance().pullElemFromMemory(Constants.LIVE_KEY + "system:stop:" + idOwnerLive);
            String debateUserQueue = RMemoryAPI.getInstance().pullElemFromMemory(Constants.LIVE_KEY + "debate.queue:" + idOwnerLive);
            if (ownerAlive != null) {
                log.error("Null or ended live: ", idLive);
                json.put("error", EnumErrors.LIVE_ENDED);
                RMemoryAPI.getInstance().delFromMemory(Constants.LIVE_KEY + parsedRtmpUrl[2]);
                return createResponse(json);
            } else if (debateUserQueue == null || !Long.valueOf(debateUserQueue).equals(idUser)) {
                log.error("Null or ended live: ", idLive);
                json.put("error", EnumErrors.FORBIDDEN);
                RMemoryAPI.getInstance().delFromMemory(Constants.LIVE_KEY + parsedRtmpUrl[2]);
                return createResponse(json);
            }

            RMemoryAPI.getInstance().pushHashToMemory(Constants.LIVE_KEY + parsedRtmpUrl[2], "parent.id_live", idOwnerLive + "");
            RMemoryAPI.getInstance().pushHashToMemory(Constants.MEDIA_KEY + idLive, "debate.iduser", user.idUser + "");
            RMemoryAPI.getInstance().pushHashToMemory(Constants.MEDIA_KEY + idLive, "debate.author", idOwnerLive + "");
            log.debug("Set deb hoster {} with {}", idOwnerLive, idLive);


            json.put("idLive", idLive);
            json.put("rtmpUrl", rtmpUrl);

        } catch (Exception ex) {
            log.error("ERROR IN WEBLVIE API ", ex);
            json.put("error", EnumErrors.UNKNOWN_ERROR);
        }

        log.debug(json.toString());

        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/media/rotation/{id}")
    @Context
    public Response setRotationTimeByIdMedia(@PathParam("id") long idMedia,
                                             @QueryParam("hash") String hash,
                                             @QueryParam("position") int rotation,
                                             @HeaderParam("Accept-Language") String lang) {

        String stime = RMemoryAPI.getInstance()
                .pullHashFromMemory(Constants.MEDIA_KEY + idMedia, "start-time");

        if (stime == null) {
            stime = RMemoryAPI.getInstance().currentTime() + "";
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("errors", EnumErrors.NO_ERRORS);
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);

        if (user != null && user.getIdUser() != 0) {

            long now = RMemoryAPI.getInstance().currentTime() - Long.valueOf(stime);
            boolean timeExist = RMemoryAPI.getInstance()
                    .pullIfSetElem(Constants.MEDIA_KEY + "properties:rotatimes:" + idMedia, now+"");

            ScreenRotation rota = new ScreenRotation();
            rota.setTime(now);
            rota.setRotation(rotation);

            if(timeExist){
                Formulas.cleanCamRotationDubplicats(idMedia, now);
            }

            RMemoryAPI.getInstance()
                    .pushListElemToMemory(Constants.MEDIA_KEY + "properties:rotatime:" + idMedia, rota.toString());
            RMemoryAPI.getInstance()
                    .pushElemToMemory(Constants.MEDIA_KEY + "properties:rotalast:" + idMedia, 3, rotation+"");
            RMemoryAPI.getInstance()
                    .pushSetElemToMemory(Constants.MEDIA_KEY + "properties:rotatimes:" + idMedia, 3, now+"");

            Notification notification = new Notification();
            String hostLiveID = RMemoryAPI.getInstance().pullHashFromMemory(Constants.MEDIA_KEY + idMedia, "debate.author");
            log.debug("ROtation host {}, idmedia {}", hostLiveID, idMedia);
            if (hostLiveID == null) {
                notification.setIdMedia(idMedia);
                notification.setIdHoster(idMedia);
                notification.setType(11); //main live ws
            } else {
                notification.setIdMedia(idMedia);
                notification.setIdHoster(Long.valueOf(hostLiveID));
                notification.setType(16); //debate ws
            }
            notification.setIdFrom(user.getIdUser());
            notification.setRotation(rotation);

            ISystemDelivery systemDelivery = SystemDelivery
                    .builder(notification).socket();

        } else {
            jsonObject.put("errors", EnumErrors.INVALID_HASH);
        }
        return createResponse(jsonObject);
    }


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/live/schedule")
    public Response publishScheduleLive(@FormParam("title") String title,
                                        @FormParam("idch") long idChannel,
                                        @FormParam("tags") String tags,
                                        @FormParam("idHash") String idHash,
                                        @FormParam("idMedia") long idMedia,
                                        @FormParam("location") String location,
                                        @FormParam("dname") String dname,
                                        @FormParam("stime") String start_time,
                                        @HeaderParam("X-Forwarded-For") String IP,
                                        @HeaderParam("User-Agent") String agent,
                                        @HeaderParam("Accept-Language") String lang) throws IOException {

        IP = IP != null ? IP.split(",")[0] : null;
        JSONObject json = new JSONObject();
        LiveMediaInfo liveMediaInfo = new LiveMediaInfo();
        MediaShelf mediaShelf = new MediaShelf(lang);
        log.debug("EVENT JAVA PUBLISH idmedia {} idHash {}", idMedia, idHash);

        Pattern alphanumeric = Pattern.compile("^\\s+$");
        boolean isBlockedIP = RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:ip", IP + "");
        title = (title == null || title.isEmpty()) ? "undefined" : title;
        if (isBlockedIP || alphanumeric.matcher(title).matches()
                || start_time == null || idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.PUBLISH_ERR);
            return createResponse(json);
        }

        EnumErrors errors = EnumErrors.NO_ERRORS;
        try {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

            if (user == null
                    || RMemoryAPI.getInstance().pullIfSetElem("dc_users:blocked:user", user.getIdUser() + "")
                    || user.idUser == 0) {
                log.error("Null, Anon or Blocked user:", user != null ? user.toString() : null);
                json.put("error", EnumErrors.PUBLISH_ERR);
                return createResponse(json);
            }

            if (idMedia <= 4) {
                idMedia = RMemoryAPI.getInstance().currentTime();
                liveMediaInfo.setDefclip(0);
            } else {
                liveMediaInfo.setDefclip(-1);
            }

            Long idUser = user.idUser;

            String[] parsedRtmpUrl = LiveUrlGenerator.buildRTMPUrl(idUser, dname, idMedia);

            String rtmpUrl = "rtmp://" + parsedRtmpUrl[0] + "/" + parsedRtmpUrl[1] + "/" + parsedRtmpUrl[2];
            String htmlUrl = "https://hls.brocast.com/" + idMedia + "/index.m3u8";

            liveMediaInfo.setTitle(title.trim());
            liveMediaInfo.setTags(tags == null ? "" : tags);
            liveMediaInfo.setDescription("");
            liveMediaInfo.setIdCateg(1l); //video
            liveMediaInfo.setIdChannel(idChannel);
            liveMediaInfo.setIdHash(idHash);
            liveMediaInfo.setProcess(0);
            liveMediaInfo.setIdUser(idUser);
            liveMediaInfo.setUser(user);
            liveMediaInfo.setIdMedia(idMedia);
            liveMediaInfo.setStop(false);
            liveMediaInfo.setMethod("event");
            liveMediaInfo.setEventTime(start_time);

            liveMediaInfo.setRtmpUrl(rtmpUrl);
            liveMediaInfo.setHttpUrl(htmlUrl);

            if (dname != null && !dname.isEmpty()) {
                mediaShelf.saveMediaDeviceVendor(idMedia, dname);
            }

            log.debug("TRYING GET LIVE LOCATIONS: IP {}, GPS {}", IP, location);
            int platformType = UserStatisticParser.parseAndSave(idMedia, user.getIdUser(), 20, IP, dname, agent);

            if (platformType == 2) {
                String header = SpecificGeoLocation.geoLocationByWEBIP(IP);
                if (header.contains("@") && (location == null || location.isEmpty() || location.equals("0.0 0.0"))) {
                    location = (header.replace("@", " "));
                } else if (header.contains("@")) {
                    location = (SpecificGeoLocation.compareGeoDistance(location, header.replace("@", " ")));
                }
            }

            if (location != null && !location.isEmpty() && !location.equals("0.0 0.0")) {
                liveMediaInfo.setLatlong(location);
                location = mediaShelf.modifyLocation(location, idMedia);
                liveMediaInfo.setLocation(location);
            } else {
                liveMediaInfo.setLocation("");
            }

            String langCode = new MediaShelf().getLanguageCodeByLocation(IP, location);

            liveMediaInfo.setPlatformType(platformType);
            liveMediaInfo.setLang(langCode);

            log.debug("Event props {}", liveMediaInfo.toString());
            errors = mediaShelf.saveVideoProperties(liveMediaInfo, "event");

            if (errors == EnumErrors.NO_ERRORS) {
                RMemoryAPI.getInstance().pushHashToMemory(Constants.LIVE_KEY + parsedRtmpUrl[2], "id_user", idUser + "");
                RMemoryAPI.getInstance().pushHashToMemory(Constants.LIVE_KEY + parsedRtmpUrl[2], "id_live", idMedia + "");

                RMemoryAPI.getInstance().pushElemToMemory(Constants.LIVE_KEY + "publish:" + idMedia, 1, liveMediaInfo.toString());

                Notification notification = new Notification();
                notification.setType(EnumNotification.SCHEDULED_EVENT.value);
                notification.setIdFrom(idUser);
                notification.setIdMedia(idMedia);
                notification.setText(title);
                notification.setUsername(user.username);

                ISystemDelivery systemDelivery = SystemDelivery
                        .builder(notification).push();
            }


        } catch (Exception ex) {
            log.error("ERROR IN WEB API ", ex);
            json.put("error", EnumErrors.UNKNOWN_ERROR);
        }

        log.debug("LIVE EVENT UPLOAD: {}", errors.toString());
        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/event/publish")
    public Response eventPublish(@FormParam("idLive") long idLive,
                                 @FormParam("idHash") String idHash,
                                 @FormParam("region") String region,
                                 @HeaderParam("Accept-Language") String lang) throws IOException {
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash != null && idLive != 0) {
            try {
                log.debug("idLive: {}, idHAsh: {}", idLive, idHash);

                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user == null || user.idUser == 0) {
                    json.put("error", EnumErrors.INVALID_HASH);
                    return createResponse(json);
                }

                LiveMediaInfo info = (LiveMediaInfo) RMemoryAPI.getInstance()
                        .pullElemFromMemory(Constants.LIVE_KEY + "publish:" + idLive, LiveMediaInfo.class);

                if (info != null) {
                    json.put("idLive", idLive);

                    String[] parsedRtmpUrl = LiveUrlGenerator.buildRTMPUrl(info.getIdUser(), "", idLive);
                    String rtmpUrl = "rtmp://" + parsedRtmpUrl[0] + "/" + parsedRtmpUrl[1] + "/" + parsedRtmpUrl[2];

                    json.put("rtmpUrl", rtmpUrl);
                    log.debug("Publish error info cache is NULL");
                } else {
                    errors = EnumErrors.PUBLISH_ERR;
                }

            } catch (Exception ex) {
                log.error("ERROR IN WEB API: ", ex);
                errors = EnumErrors.UNKNOWN_ERROR;
            }
        } else {
            errors = EnumErrors.NULL_FIELD_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/live/edit")
    public Response editPublishLive(@FormParam("title") String title,
                                    @FormParam("description") String description,
                                    @FormParam("idcateg") long idCateg,
                                    @FormParam("idch") long idChannel,
                                    @FormParam("tg") String tags,
                                    @FormParam("key") String key,
                                    @FormParam("idHash") String idHash,
                                    @HeaderParam("Accept-Language") String lang) throws IOException {
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        Pattern alphanumeric = Pattern.compile("^\\s+$");
        title = (title == null || title.isEmpty()) ? "undefined" : title;
        if (!alphanumeric.matcher(title).matches() && title.length() >= 3 && title.length() <= 500) {
            try {
                log.debug("key: {}, title: {} tags: {} CHANNEL {}", new Object[]{key, tags, idChannel});

                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user == null || user.idUser == 0) {
                    json.put("error", EnumErrors.INVALID_HASH);
                    return createResponse(json);
                }

                LiveMediaInfo info = (LiveMediaInfo) RMemoryAPI.getInstance().pullElemFromMemory(Constants.LIVE_KEY + "publish:" + key, LiveMediaInfo.class);

                if (info == null) {

                    MediaShelf mediaShelf = new MediaShelf();
                    String idLive = RMemoryAPI.getInstance().pullHashFromMemory(Constants.MEDIA_KEY + key, "id_media");

                    if (idLive == null) {
                        json.put("error", EnumErrors.PUBLISH_ERR);
                        return createResponse(json);
                    }

                    Set<String> tagList = TagTokenizer.normilizeTag(info.getTags());

                    DcMediaEntity mediaEntity = new DcMediaEntity();
                    mediaEntity.setTitle(title);
                    mediaEntity.setIdMedia(Long.valueOf(idLive));
                    mediaEntity.setDescription(description);
                    mediaEntity.setIdChannel(idChannel);
                    mediaEntity.setTags(tagList);
                    mediaEntity.setMethod("live");

                    mediaShelf.updateContentInfo(mediaEntity);

                    log.debug("Publish error info cache is NULL");

                } else {
                    info.setTitle(title);
                    info.setDescription(description);
                    info.setIdChannel(idChannel);
                    info.setTags(tags);

                    RMemoryAPI.getInstance().pushElemToMemory(Constants.LIVE_KEY + "publish:" + key, 5, info.toString());

                }

            } catch (Exception ex) {
                log.error("ERROR IN WEB API: ", ex);
                errors = EnumErrors.UNKNOWN_ERROR;
            }
        } else {
            errors = EnumErrors.NULL_FIELD_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/live/gps/ack")
    public Response updateLiveLocation(@FormParam("location") String location,
                                       @FormParam("idm") long idMedia,
                                       @FormParam("idHash") String idHash,
                                       @HeaderParam("Accept-Language") String lang) throws IOException {
        EnumErrors errors = EnumErrors.NO_ERRORS;
        JSONObject json = new JSONObject();
        if (idHash != null && idMedia > 0 && location != null) {
            try {
                log.debug("idm: {}, local: {}", idMedia, location);

                DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

                if (user == null || user.idUser == 0) {
                    json.put("error", EnumErrors.INVALID_HASH);
                    return createResponse(json);
                }

                Long idUser = user.getIdUser();
                MediaShelf mediaShelf = new MediaShelf();
                String idAuthor = RMemoryAPI.getInstance().pullHashFromMemory(Constants.MEDIA_KEY + idMedia, "id_user");

                if (idAuthor == null || !idAuthor.equals(String.valueOf(idUser))) {
                    json.put("error", EnumErrors.FORBIDDEN);
                    return createResponse(json);
                }

                mediaShelf.updateLiveGeoLocation(idMedia, location);

            } catch (Exception ex) {
                log.error("ERROR IN WEB API: ", ex);
                errors = EnumErrors.UNKNOWN_ERROR;
            }
        } else {
            errors = EnumErrors.NULL_FIELD_ERROR;
        }

        json.put("error", errors);
        return createResponse(json);
    }


    @GET
    @Produces("application/json")
    @Path("/publish")
    public Response startPublishLive(@QueryParam("idl") long id_live,
                                     @QueryParam("idu") long id_user,
                                     @QueryParam("pr") Integer progress,
                                     @QueryParam("cdn") int cdn,
                                     @QueryParam("region") String region) {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        int response = 500;


        if (id_live == 0 || progress == null) {
            errors = EnumErrors.PUBLISH_ERR;
        }

        if (errors == EnumErrors.NO_ERRORS) {

            log.debug("<================PUBLISH LIVE===================> {}", progress);

            try {

                LiveMediaInfo info = (LiveMediaInfo) RMemoryAPI
                        .getInstance().pullElemFromGetMemory(Constants.LIVE_KEY + "publish:" + id_live, LiveMediaInfo.class);

                if (info != null) {

                    LiveShelf liveShelf = new LiveShelf(info.getLang());

                    RMemoryAPI.getInstance().delFromMemory(Constants.LIVE_KEY + "publish:" + id_live);
                    //RMemoryAPI.getInstance().delFromSetElem(Constants.LIVE_KEY + "channels", info.getChName()); todo

                    log.debug("HLS URL Region {}", region);
                    String rtmp = "rtmp://play." + region +".brocast.com/dgtz/live_id" + id_user + "_" + id_live;

                    info.setHttpUrl("https://hls.brocast.com/dgtz/live_id" + id_user + "_" + id_live + "/index.m3u8");
                    info.setRtmpUrl(rtmp);
                    info.setProcess(0);
                    RMemoryAPI.getInstance()
                            .pushHashToMemory(Constants.LIVE_KEY + id_live, "rtmp_liveurl", rtmp);

                    if (info.getMethod().equals("event")) {
                        DcMediaEntity entity = new DcMediaEntity();
                        LiveProps props = new LiveProps();
                        props.rtmp_url = info.getRtmpUrl();
                        props.hls_url = info.getHttpUrl();
                        entity.setLiveProps(props);
                        entity.setMethod("live");
                        entity.setIdMedia(id_live);

                        MediaShelf mediaShelf = new MediaShelf();
                        mediaShelf.updateEventInfo(entity);
                    } else {
                        errors = liveShelf.saveLiveProperties(info);
                    }
                } else {
                    log.error("NULL LiveMediaInfo");
                }

                DcMediaEntity media = RMemoryAPI.getInstance()
                        .pullHashFromMemory(Constants.MEDIA_KEY + id_live, "detail", DcMediaEntity.class);

                if (media != null && errors == EnumErrors.NO_ERRORS) {
                    response = 200;
                } else {
                    errors = EnumErrors.PUBLISH_ERR;
                }
            } catch (Exception e) {
                errors = EnumErrors.PUBLISH_ERR;
                response = 500;
            }
        }

        log.debug("ERROR: {}", errors.toString());
        JSONObject json = new JSONObject();
        json.put("status", response);
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/publish/thumb")
    public Response startPublishLiveWithThumbnail(@QueryParam("idl") long id_live,
                                                  @QueryParam("idu") long id_user,
                                                  @QueryParam("pr") Integer progress,
                                                  @QueryParam("cdn") int cdn,
                                                  @HeaderParam("Accept-Language") String lang) throws InterruptedException, ExecutionException, TimeoutException, IOException {

        EnumErrors errors = EnumErrors.NO_ERRORS;
        int response = 500;

        if (id_live == 0 || progress == null) {
            errors = EnumErrors.PUBLISH_ERR;
        }

        if (errors == EnumErrors.NO_ERRORS && id_live != 0) {
            response = 200;
            String stop = RMemoryAPI.getInstance().pullElemFromMemory(Constants.LIVE_KEY + "system:stop:" + id_live);

            if (stop == null) {
                {
                    DcMediaEntity media = RMemoryAPI.getInstance()
                            .pullHashFromMemory(Constants.MEDIA_KEY + id_live, "detail", DcMediaEntity.class);
                    if (media != null) {
                        String username = RMemoryAPI.getInstance().pullHashFromMemory(Constants.USER_KEY + id_user, "username");
                        Notification notification = new Notification();
                        notification.setType(6);
                        notification.setIdFrom(id_user);
                        notification.setIdMedia(id_live);
                        notification.setText(media.title);
                        notification.setUsername(username);

                        ISystemDelivery systemDelivery = SystemDelivery
                                .builder(notification).push();
                    }
                }
            }
        }

        log.debug("ERROR: {}", errors.toString());
        JSONObject json = new JSONObject();
        json.put("status", response);
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/live/stop")
    public Response stopBroadcasting(@QueryParam("red") String hash,
                                     @QueryParam("idl") long idLive) {
        return stopBroadcastCommon(hash, idLive);
    }

    @GET
    @Produces("application/json")
    @Path("/live/info")
    public Response liveInfo(@QueryParam("idl") long idLive) {
        String stop = RMemoryAPI.getInstance().pullElemFromMemory(Constants.LIVE_KEY + "system:stop:" + idLive);
        //String livelost = RMemoryAPI.getInstance().pullElemFromMemory(Constants.LIVE_KEY + "system:livelost:" + idLive);
        /*String status = "active";
        if(stop==null && livelost!=null){
            status = "lost";
        } else if(stop!=null){
            status = "ended";
        }*/
        JSONObject json = new JSONObject();
        json.put("status", stop==null?"active":"ended");
        return createResponse(json);

    }

    @POST
    @Produces("application/json")
    @Path("/live/stop")
    public Response stopBroadcastingPost(@FormParam("red") String hash,
                                         @FormParam("idl") long idLive) {
        return stopBroadcastCommon(hash, idLive);
    }

    private Response stopBroadcastCommon(String hash, long idLive){
        JSONObject json = new JSONObject();
        EnumErrors errors = EnumErrors.NO_ERRORS;

        RMemoryAPI.getInstance().pushElemToMemory(Constants.LIVE_KEY + "system:stop:" + idLive, -1, "1");
        RMemoryAPI.getInstance().delFromMemory(Constants.LIVE_KEY + "debate.status:" + idLive);
        RMemoryAPI.getInstance()
                .pushHashToMemory(Constants.MEDIA_KEY + idLive, "stop-time", RMemoryAPI.getInstance().currentTime() + "");
        log.debug("ERROR STATUS OF STOP LIVE: {}", errors.toString());
        json.put("error", errors);

        sendLiveEndNotification(idLive + "", hash);
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/live/debate/stop")
    public Response stopDebateBroadcasting(@QueryParam("red") String hash,
                                           @QueryParam("idl") long idLive) {

        JSONObject json = new JSONObject();
        EnumErrors errors = EnumErrors.NO_ERRORS;

        RMemoryAPI.getInstance().pushElemToMemory(Constants.LIVE_KEY + "system:stop:" + idLive, -1, "1");

        log.debug("ERROR STATUS: {}", errors.toString());
        json.put("error", errors);
        String idHost = RMemoryAPI.getInstance().pullHashFromMemory(Constants.MEDIA_KEY + idLive, "debate.author");
        if (idHost != null) {
            RMemoryAPI.getInstance().delFromMemory(Constants.LIVE_KEY + "debate.status:" + idHost);
            RMemoryAPI.getInstance().delFromMemory(Constants.LIVE_KEY + "debate.queue:" + idHost);
            sendDebateLiveEndNotification(idHost, hash);
        }

        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/done")
    @Deprecated
    public Response donePublish(@QueryParam("name") String stream,
                                @QueryParam("pr") Short progress,
                                @QueryParam("dura") Short duration,
                                @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        LiveShelf liveShelf = new LiveShelf();
        EnumErrors errors = EnumErrors.NO_ERRORS;
        log.debug(stream);
        long idLive = 0;
        long idUser = 0;
        int rotana = 0;

//        log.debug("ROTATION/Process: {} {}", rotation, progress);
//
//        if (rotation != null && !rotation.isEmpty() && (rotation.equals("0") || rotation.equals("1"))) {
//            rotana = Integer.valueOf(rotation);
//        }

        try {

            idLive = Long.valueOf(stream.substring(stream.lastIndexOf("_") + 1, stream.length()));
            idUser = Long.valueOf(stream.substring(0, stream.lastIndexOf("_")).replace("live_id", ""));

            if (idLive > 0 && idUser >= 0 && !checkIfVideoRemoved(idLive)) {

                log.debug("SET IN_PROGRESS STATUS");
                updateLiveInfo(liveShelf, idLive, progress, duration);

                if (progress == 1) {
                    SendCompressingSignal compressingSignal = new SendCompressingSignal();
                    compressingSignal.sendToCompress(idLive, idUser, duration, true, rotana);
                }

            }

        } catch (Exception e) {
            log.error("PUBLISH DONE_ERR ", e);
            errors = EnumErrors.PUBLISH_ERR;
        }

        if (errors != EnumErrors.NO_ERRORS) {
            updateLiveInfo(liveShelf, idLive, 2, duration);
        }

        log.debug("ERROR STATUS: {}", errors.toString());
        json.put("error", errors);

        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/live_done")
    @Deprecated
    public Response doneLivePublish(@QueryParam("id") Long idLive,
                                    @QueryParam("r") String rotation,
                                    @QueryParam("pr") Short progress,
                                    @QueryParam("idu") Long idUser,
                                    @HeaderParam("Accept-Language") String lang) {

        JSONObject json = new JSONObject();
        LiveShelf liveShelf = new LiveShelf();
        EnumErrors errors = EnumErrors.NO_ERRORS;
        log.debug("Id LIVE {}", idLive);
        short duration = 0;

        int rotana = 0;

        log.debug("ROTATION/Process: {} {}", rotation, progress);

        if (rotation != null && !rotation.isEmpty() && (rotation.equals("0") || rotation.equals("1"))) {
            rotana = Integer.valueOf(rotation);
        }

        try {

            String pr = (String) RMemoryAPI.getInstance().pullElemFromMemory(Constants.MEDIA_KEY + "encstate:" + idLive, String.class);

            if (idLive > 0 && idUser >= 0 && !checkIfVideoRemoved(idLive)) {

                log.debug("SET IN_PROGRESS STATUS");
                updateLiveInfo(liveShelf, idLive, progress, duration);

                if (progress == 1) {
                    SendCompressingSignal compressingSignal = new SendCompressingSignal();
                    compressingSignal.sendToCompress(idLive, idUser, duration, true, rotana);
                }

            }

        } catch (Exception e) {
            log.error("PUBLISH DONE_ERR ", e);
            errors = EnumErrors.PUBLISH_ERR;
        }

        if (errors != EnumErrors.NO_ERRORS) {
            updateLiveInfo(liveShelf, idLive, 2, duration);
        }

        log.debug("ERROR STATUS: {}", errors.toString());
        json.put("error", errors);

        return createResponse(json);
    }

    private EnumErrors updateLiveInfo(LiveShelf liveShelf, Long idLive, int process, Short duration) {
        EnumErrors errors = EnumErrors.NO_ERRORS;
        try {
            MediaStatus progress = RMemoryAPI.getInstance()
                    .pullHashFromMemory(Constants.MEDIA_KEY + idLive, "progress", MediaStatus.class);

            if (progress != null && progress.getProgress() != 3) {
                EnumSQLErrors sqlError = liveShelf.doneLivePublish(idLive, process, duration);
                if (sqlError != EnumSQLErrors.OK) {
                    errors = EnumErrors.PUBLISH_ERR;
                }
            }
        } catch (Exception e) {
            log.error("ERROR IN WEB API ", e);
            errors = EnumErrors.PUBLISH_ERR;
        }
        return errors;
    }

    private boolean checkIfVideoRemoved(Long idMedia) {
        String val = RMemoryAPI.getInstance().pullHashFromMemory(Constants.MEDIA_KEY + idMedia, "id_media");

        return val == null || val.isEmpty();
    }

    protected void sendLiveEndNotification(String idLive, String hash) {

       /*Notifying Live publisher through WS server that Live ended*/
        JsonNode node =
                new JsonNode("{\"time\":\"12345678\",\"idHash\":\"" + hash + "\"," +
                        "\"text\":\" \",\"idMedia\":\"" + idLive + "\",\"wsType\":3}");

        Future<HttpResponse<JsonNode>> rStatus =
                Unirest.post(Constants.WEBSOCKET_URL + "/ws/media/" + idLive)
                        .header("Content-Type", "application/json")
                        .body(node)
                        .asJsonAsync(new Callback<JsonNode>() {
                            public void failed(UnirestException e) {
                                log.debug("The request has failed");
                            }

                            public void completed(HttpResponse<JsonNode> response) {
                                int code = response.getStatus();
                                log.debug("Completed {}", code);
                            }

                            public void cancelled() {
                                log.debug("The request has been cancelled");
                            }
                        });

        Future<HttpResponse<JsonNode>> rBroad =
                Unirest.post(Constants.WEBSOCKET_URL + "/ws/media/all/map")
                        .header("Content-Type", "application/json")
                        .body(node)
                        .asJsonAsync(new Callback<JsonNode>() {
                            public void failed(UnirestException e) {
                                log.debug("The request has failed");
                            }

                            public void completed(HttpResponse<JsonNode> response) {
                                int code = response.getStatus();
                                log.debug("Completed {}", code);
                            }

                            public void cancelled() {
                                log.debug("The request has been cancelled");
                            }
                        });
    }

    protected void sendDebateLiveEndNotification(String idLive, String hash) {

            /*Notifying Live publisher through WS server that Live ended*/
        JsonNode node =
                new JsonNode("{\"time\":\"12345678\",\"idHash\":\"" + hash + "\"," +
                        "\"text\":\" \",\"idMedia\":\"" + idLive + "\",\"wsType\":15}");

        Future<HttpResponse<JsonNode>> rStatus =
                Unirest.post(Constants.WEBSOCKET_URL + "/ws/media/" + idLive)
                        .header("Content-Type", "application/json")
                        .body(node)
                        .asJsonAsync(new Callback<JsonNode>() {
                            public void failed(UnirestException e) {
                                log.debug("The request has failed");
                            }

                            public void completed(HttpResponse<JsonNode> response) {
                                int code = response.getStatus();
                                log.debug("Completed {}", code);
                            }

                            public void cancelled() {
                                log.debug("The request has been cancelled");
                            }
                        });
    }
}
