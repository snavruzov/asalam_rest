package com.dgtz.web.api;

import com.brocast.riak.api.beans.*;
import com.dgtz.api.beans.EventClipsEntity;
import com.dgtz.api.beans.MediaFeaturedBean;
import com.dgtz.api.beans.MediaVIewInfo;
import com.dgtz.api.beans.RatingInfo;
import com.dgtz.api.constants.EventClips;
import com.dgtz.api.contents.MediaShelf;
import com.dgtz.api.contents.MenuShelf;
import com.dgtz.api.contents.UsersShelf;
import com.dgtz.api.enums.EnumErrors;
import com.dgtz.db.api.beans.DcDebateEntity;
import com.dgtz.db.api.beans.MediaMappingStatInfo;
import com.dgtz.db.api.domain.*;
import com.dgtz.db.api.enums.EnumAggregations;
import com.dgtz.mcache.api.factory.Constants;
import com.dgtz.mcache.api.factory.RMemoryAPI;
import com.dgtz.web.api.beans.SearchTitleTag;
import com.dgtz.web.api.tools.Formulas;
import com.dgtz.web.api.tools.UserStatisticParser;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;


/**
 * Created with IntelliJ IDEA.
 * User: sardor
 * Date: 12/22/13
 * Time: 10:03 AM
 * To change this template use File | Settings | File Templates.
 */

@Path("/contents")
public class ContentsController extends Resource {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ContentsController.class);

    public ContentsController() {
    }


    @GET
    @Produces("application/json")
    @Path("/new/stat/{id}/list")
    @Context
    public Response getNewMediaByStatistics(@PathParam("id") int idAgg,
                                            @QueryParam("off") long offset,
                                            @QueryParam("lm") int limit,
                                            @QueryParam("red") String idHash,
                                            @HeaderParam("X-Forwarded-For") String IP,
                                            @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf(lang);
        log.debug("LOCAL H1: {}", IP);

        IP = (IP != null && !IP.isEmpty()) ? IP.split(",")[0] : null;

        DcUsersEntity usersEntity = null;
        if (idHash != null && !idHash.isEmpty()) {
            usersEntity = PrivateProperties.extractUserFullInfo(idHash);
        } else {
            usersEntity = PrivateProperties.extractUserFullInfo("9d8128a5e784e14fc64b137fae5c63fe");
        }

        List<MediaNewsStatInfo> infoList
                = mediaShelf.extractMediaListByStat(idAgg, offset, limit, usersEntity, IP);

        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/media/trending")
    @Context
    public Response getMediaByTrendingStatistics(@QueryParam("off") int offset,
                                                 @QueryParam("lm") int limit,
                                                 @QueryParam("red") String idHash,
                                                 @HeaderParam("X-Forwarded-For") String IP,
                                                 @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf(lang);
        log.debug("LOCAL H1: {}", IP);

        IP = (IP != null && !IP.isEmpty()) ? IP.split(",")[0] : null;
        List<MediaPublicInfo> infoList = new ArrayList<>();
        DcUsersEntity usersEntity = null;

        if (idHash != null && !idHash.isEmpty() && !idHash.equals("9d8128a5e784e14fc64b137fae5c63fe")) {
            usersEntity = PrivateProperties.extractUserFullInfo(idHash);
        } else {
            usersEntity = PrivateProperties.extractUserFullInfo(Formulas.DEFAULT_HASH); //Guest hash
        }

        if (usersEntity!=null) {
            infoList = mediaShelf.extractMediaListByRating(offset, limit, usersEntity, IP);
        }

        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/media/featured")
    @Context
    public Response getMediaByFeaturedStatistics(@HeaderParam("X-Forwarded-For") String IP,
                                                 @HeaderParam("Accept-Language") String lang) {
        MediaShelf mediaShelf = new MediaShelf();
        List<MediaFeaturedBean> infoList = mediaShelf.extractFeaturedMediaList();
        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/collect/list")
    @Context
    public Response getMediaByCollection(@QueryParam("red") String idHash,
                                         @QueryParam("off") int offset,
                                         @QueryParam("lm") int limit,
                                         @HeaderParam("X-Forwarded-For") String IP,
                                         @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf(lang);
        List<MediaPublicInfo> infoList = new ArrayList<>();
        log.debug("LOCAL H1: {}", IP);

        DcUsersEntity usersEntity = null;
        if (idHash != null && !idHash.isEmpty() && !idHash.equals("9d8128a5e784e14fc64b137fae5c63fe")) {
            usersEntity = PrivateProperties.extractUserFullInfo(idHash);
        } else {
            usersEntity = PrivateProperties.extractUserFullInfo(Formulas.DEFAULT_HASH); //Guest hash
        }

        if (usersEntity != null) {
            infoList = usersShelf.extractMediaCollection(usersEntity.idUser, offset, limit);
        }


        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/map/stat/list")
    @Context
    public Response getMapMediaByStatistics() {

        MediaShelf mediaShelf = new MediaShelf();
        List<MediaMappingStatInfo> infoList = mediaShelf.extractMapMediaList();
        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/media/viewers/list")
    @Context
    public Response getMediaViewersByStatistics(
                                            @QueryParam("off") long offset,
                                            @QueryParam("lm") long limit,
                                            @QueryParam("idm") long idMedia) {

        MediaShelf mediaShelf = new MediaShelf();
        List<UserShortInfo> infoList = mediaShelf.extractLiveViewrsData(idMedia, offset, limit);
        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/ch/stat/{id}/list")
    @Context
    public Response getChannelsByStatistics(@PathParam("id") int idAgg,
                                            @QueryParam("off") long offset,
                                            @QueryParam("lm") int limit,
                                            @QueryParam("red") String idHash,
                                            @HeaderParam("X-Forwarded-For") String IP,
                                            @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf();

        log.debug("LOCAL H1: {}", IP.split(",")[0]);
        List<PublicChannelsEntity> entities = new ArrayList<>();
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

        if (user != null) {
            entities = mediaShelf.extractChannelsInfo(idAgg, user.idUser, offset, limit);
        }

        return createResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/ch/stat/popular/list")
    @Context
    public Response getPopularChannelsByStatistics(@QueryParam("off") long offset,
                                                   @QueryParam("lm") int limit,
                                                   @QueryParam("red") String idHash,
                                                   @HeaderParam("X-Forwarded-For") String IP,
                                                   @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf();

        log.debug("LOCAL H1: {}", IP.split(",")[0]);

        List<PublicChannelsEntity> entities = new ArrayList<>();
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

        if (user != null) {
            entities = mediaShelf.extractChannelsInfo(EnumAggregations.POPULAR.value, user.idUser, offset, limit);
        }

        return createResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/search/list")
    @Context
    public Response searchMediaByStatistics(@QueryParam("frm") String frm,
                                            @QueryParam("type") int type,
                                            @QueryParam("lm") int limit,
                                            @QueryParam("off") int off,
                                            @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf(lang);
        JSONObject json = new JSONObject();

        SearchTitleTag titleTag = new SearchTitleTag();

        log.debug("frm {} limit {} off {}", new Object[]{frm, limit, off});
        if (frm == null || frm.isEmpty() || frm.length() < 3) {
            json.put("titles", new Object[]{});
            json.put("tags", new Object[]{});
            json.put("channels", new Object[]{});
            json.put("places", new Object[]{});

            return createResponse(json);

        }

        if (type == 1) {
            List<MediaNewsStatInfo> entitiesTitle = mediaShelf.extractVideosByFragment(frm, limit, off);
            titleTag.setTitles(entitiesTitle);
        }
        if (type == 2) {
            /*List<MediaNewsStatInfo> entitiesTags = mediaShelf.extractMediaListByTag(frm, limit, off);
            titleTag.setTags(entitiesTags);*/
        }
        if (type == 3) {
            List<DcChannelsEntity> entitiesChannel = mediaShelf.castChannelsByChannelName(frm, off, limit);
            titleTag.setChannels(entitiesChannel);
        }

        json.put("titles", titleTag.getTitles());
        json.put("tags", titleTag.getTags());
        json.put("channels", titleTag.getChannels());

        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/search2/list")
    @Context
    public Response search2MediaByStatistics(@QueryParam("frm") String frm,
                                             @QueryParam("type") int type,
                                             @QueryParam("hash") String hash,
                                             @QueryParam("lm") int limit,
                                             @QueryParam("off") int off,
                                             @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf(lang);

        log.debug("frm {} limit {} off {}", new Object[]{frm, limit, off});
        if (frm == null || frm.isEmpty() || frm.length() < 1) {
            return createResponse(new Object[]{});
        }

        if(hash == null || hash.isEmpty() || hash.equals("9d8128a5e784e14fc64b137fae5c63fe")){
            hash = Formulas.DEFAULT_HASH;
        }

        Object entities = new Object[]{};
        if (type == 1) {
            entities = mediaShelf.extractVideosByFragment(frm, limit, off);
        }
        if (type == 2 && frm.length() >= 2) {
            entities = mediaShelf.extractMediaListByTag(frm, limit, off);
        }
        if (type == 3) {
            entities = mediaShelf.castChannelsByChannelName(frm, limit, off);
        }
        if (type == 4) {
            entities = mediaShelf.extractMediaListByPlace(frm, limit, off);
        }
        if (type == 5) {
            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
            if(user!=null) {
                entities = mediaShelf.extractTagListByName(frm,user.idUser, limit, off);
            }
        }

        return createNotNullResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/content/event/clip/list")
    @Context
    public Response getDefaultClipVideos() {
        List<EventClipsEntity> clips = EventClips.CLIPS;
        return createResponse(clips);
    }

    @GET
    @Produces("application/json")
    @Path("/mtag/list")
    @Context
    public Response getMediaByTag(@QueryParam("tg") String tag,
                                  @QueryParam("off") int off,
                                  @QueryParam("lm") int limit,
                                  @QueryParam("red") String hash,
                                  @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf(lang);
        JSONObject json = new JSONObject();
        json.put("reFollow", false);
        json.put("mcount", 0);
        log.debug("tag {}  off {}", tag, off);

        if(hash == null || hash.isEmpty() || hash.equals("9d8128a5e784e14fc64b137fae5c63fe")){
            hash = Formulas.DEFAULT_HASH;
        }
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);

        List<MediaNewsStatInfo> entities = new ArrayList<>();
        if(user!=null && tag!=null && !tag.isEmpty()) {
            long mcount = RMemoryAPI.getInstance().checkLSetAllElemCount(Constants.MEDIA_KEY + "tag:" + tag);
            boolean reflw = RMemoryAPI.getInstance().pullIfSetElem(Constants.FOLLOWS + "tags:" + user.idUser, tag);
            json.put("reFollow", reflw);
            json.put("mcount", mcount);
            entities = mediaShelf.extractMediaListByTag(tag,limit, off);
        }

        json.put("data", entities);
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/media/{idcateg}/list")
    @Context
    public Response getMediaByIdCategories(@PathParam("idcateg") int idCateg,
                                           @QueryParam("off") long offset,
                                           @QueryParam("lm") int limit,
                                           @QueryParam("sort") int sortType,
                                           @HeaderParam("Accept-Language") String lang) {

        List<MediaNewsStatInfo> infoList = null;
        /*MediaShelf mediaShelf = new MediaShelf(lang);
        if (idCateg != 0) {
            infoList = mediaShelf
                    .extractMediaListByCategory(idCateg, offset, limit, sortType, "");
        } else {
            infoList = mediaShelf
                    .extractMediaListByLive(offset, limit, "", "");
        }

        if (infoList == null) {
            infoList = new ArrayList<MediaNewsStatInfo>();
        }*/
        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/media/tags")
    @Context
    public Response getMediaPopularTags(@QueryParam("lm") int limit
                                        ,@QueryParam("hash")  String hash) {

        MediaShelf mediaShelf = new MediaShelf();
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
        Set<TagInfo> entities = new LinkedHashSet<>();
        if(user!=null) {
            entities = mediaShelf.extractMostPopularTags(user.idUser,limit);
        }

        return createResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/media/users")
    @Context
    public Response getMediaPopularUsers(@QueryParam("hash") String hash,
                                         @QueryParam("lm") int limit) {

        MediaShelf mediaShelf = new MediaShelf();

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
        List<com.dgtz.api.beans.UserPublicInfo> entities = new ArrayList<>();
        if (user != null) {
            entities = mediaShelf.extractMostPopularUsers(user, limit);
        }

        return createResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/countries/list")
    public Response getAllCountries(@HeaderParam("X-Forwarded-For") String IP,
                                    @HeaderParam("Accept-Language") String lang) {

        MenuShelf menuShelf = new MenuShelf();
        Collection<DcLocationsEntity> countryNames = menuShelf.getAllCountries();

        return createResponse(countryNames);
    }

    @GET
    @Produces("application/json")
    @Path("/media/{id}/comments")
    @Context
    public Response getCommentsByIdMedia(@PathParam("id") long idMedia,
                                         @QueryParam("off") long off,
                                         @QueryParam("lm") long limit,
                                         @QueryParam("duration") int duration,
                                         @QueryParam("rev") int reverse, //0 - auto, 1-manual
                                         @QueryParam("sort") int sort, //0 - real-time, 1-by last
                                         @HeaderParam("Accept-Language") String lang) {

        Map<Long, List<DcCommentsEntity>> entities = new HashMap<>();

        DcMediaEntity entity = new MediaShelf().retrieveMediaByIdValue(idMedia);
        if (entity != null) {
            MediaShelf mediaShelf = new MediaShelf();
            entities = mediaShelf.castCommentsByIdLive(idMedia, duration, off, limit, reverse==1, sort == 1);
        }

        return createResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/media/sorted/comments")
    @Context
    public Response getCommentsBySort(@QueryParam("id") long idMedia,
                                      @QueryParam("off") long off,
                                      @QueryParam("lm") long limit,
                                      @QueryParam("sort") int sort, //0 - real-time, 1-by last
                                      @HeaderParam("Accept-Language") String lang) {

        List<DcCommentsEntity> entities = new ArrayList<>();

        DcMediaEntity entity = new MediaShelf().retrieveMediaByIdValue(idMedia);
        if (entity != null) {
            MediaShelf mediaShelf = new MediaShelf();
            entities = mediaShelf.castCommentsByIdMedia(idMedia, off, limit, sort == 1);
        }

        return createResponse(entities);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("media/rates")
    @Context
    public Response listMediaRates(@QueryParam("idm") long idMedia) {

        List<RatingInfo> list = new ArrayList<>();

        log.debug("idMedia {}", idMedia);
        DcMediaEntity entity = new MediaShelf().retrieveMediaByIdValue(idMedia);
        if (entity != null) {
            list = new UsersShelf().getVideoRate(entity);
        }

        return createResponse(list);
    }

    @GET
    @Produces("application/json")
    @Path("/media/{id}/info")
    @Context
    public Response getMediaInfoByIdMedia(@PathParam("id") long idMedia,
                                          @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf();
        JSONObject jsonObject = new JSONObject();
        if (idMedia == 0) {
            jsonObject.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(jsonObject);
        }

        com.dgtz.api.beans.MediaListInfo entities = mediaShelf.extractMediaByIdMedia(idMedia);
        if (entities != null) {
            jsonObject.put("title", entities.getTitle());
            jsonObject.put("dateadded", entities.getDateadded());

            jsonObject.put("live", entities.isLive());
            jsonObject.put("username", entities.getUsername());
            jsonObject.put("idUser", entities.getIdUser());
            jsonObject.put("vCount", entities.getAmount());
            jsonObject.put("ratio", entities.getRatio());
            jsonObject.put("lCount", entities.getLiked());
            jsonObject.put("method", entities.getMethod());

        } else {
            jsonObject.put("error", EnumErrors.NO_MEDIA_FOUND);
        }
        return createResponse(jsonObject);
    }

    @GET
    @Produces("application/json")
    @Path("/error/{code}")
    @Context
    public Response showBeautyErrorPage(@PathParam("code") int code,
                                        @HeaderParam("Accept-Language") String lang) {

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("ERROR", code);
        return createErrorResponse(jsonObject, code);
    }

    @GET
    @Produces("application/json")
    @Path("/media/{id}/status")
    @Context
    public Response getMediaStatusByIdMedia(@PathParam("id") final long idMedia,
                                            @QueryParam("type") final int type,
                                            @HeaderParam("Accept-Language") String lang) {

        final MediaShelf mediaShelf = new MediaShelf();
        String status = "BROKEN";
        try {
            status = mediaShelf.checkMediaStatus(idMedia);
            //log.debug("STATUS OF MEDIA: {} - {}", idMedia, status);
            if (status.equals("COMPLETED")) {
                log.debug("STATUS OF MEDIA: {} - {}", idMedia, status);
            }

        } catch (Exception e) {
            log.error("ERROR IN WEB API: ", e);
        }
        return createResponse(status);
    }


    @GET
    @Produces("application/json")
    @Path("/video/state/{id}")
    @Context
    public Response getMediaStatusPercentByIdMedia(@PathParam("id") final long idMedia,
                                                   @HeaderParam("Accept-Language") String lang) {

        //TODO check for private video
        MediaShelf mediaShelf = new MediaShelf();
        JSONObject json = new JSONObject();
        String status = "BROKEN";
        String errMesg = "Video encoding status.";
        try {
            if (idMedia > 0) {
                json.put("hls", "wait");
                json.put("hls_url", "");
                if (MediaShelf.isLiveHLSReady(idMedia)) {
                    json.put("hls", "done");
                    String hlsUrl = RMemoryAPI.getInstance()
                            .pullHashFromMemory(Constants.MEDIA_KEY + idMedia, "temp_hls");
                    json.put("hls_url", hlsUrl.replace("null",Constants.VIDEO_URL));
                }

                status = mediaShelf.checkMediaStatus(idMedia);
                json.put("status", status);

                Integer progress = mediaShelf.checkMediaEncStatus(idMedia);
                json.put("progress", progress);
                json.put("msg", errMesg);
            }

        } catch (Exception e) {
            log.error("ERROR IN WEB API: ", e);
        }
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/media/{id}/full/{idHash}/info")
    @Context
    public Response getMediaFullInfoByIdMedia(@PathParam("id") long idMedia,
                                              @PathParam("idHash") String idHash,
                                              @QueryParam("dname") String dname,
                                              @HeaderParam("X-Forwarded-For") String IP,
                                              @HeaderParam("User-Agent") String agent,
                                              @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf(lang);

        MediaVIewInfo entities = new MediaVIewInfo();

        log.debug("idMedia: {}, IP {}", idMedia, IP.split(",")[0]);

        if (idMedia == 0) {
            JSONObject json = new JSONObject();
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        if(idHash == null || idHash.isEmpty() || idHash.equals("9d8128a5e784e14fc64b137fae5c63fe")){
            idHash = Formulas.DEFAULT_HASH;
        }

        log.debug("Hash value {}", idHash);
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);

        try {
            if (user != null) {

                entities = mediaShelf.extractMediaViewActivityByIdMedia(idMedia, user.getIdUser());
                if (entities == null) {
                    JSONObject json = new JSONObject();
                    json.put("error", EnumErrors.NO_MEDIA_FOUND);
                    return createResponse(json);
                }

                log.debug("FULL_ENTITY: - {}", entities.toString());

                if (!entities.access) {
                    JSONObject json = new JSONObject();
                    json.put("error", EnumErrors.FORBIDDEN);
                    return createResponse(json);
                }

                if(user.idUser!=0 && user.idUser!=Formulas.DEFAULT_USER) {
                    int watch_type = UserStatisticParser.saveVisitorsStat(entities, mediaShelf, idMedia, user.idUser, idHash);
                    UserStatisticParser.parseAndSave(idMedia, user.getIdUser(), watch_type, IP, dname, agent);
                }


            } else {
                JSONObject json = new JSONObject();
                json.put("error", EnumErrors.INVALID_HASH);
                return createResponse(json);
            }
        } catch (Exception ex) {
            log.error("ERROR IN WEB API: ", ex);
        }

        return createResponse(entities);
    }

    @GET
    @Produces("application/json")
    @Path("/media/arch/list")
    @Context
    public Response getMediaActivityInTrash(@QueryParam("red") String idHash,
                                            @QueryParam("off") long off,
                                            @QueryParam("lm") long limit,
                                            @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf(lang);
        JSONObject json = new JSONObject();
        if (idHash == null || idHash.isEmpty()) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        List<MediaNewsStatInfo> infoList = new ArrayList<MediaNewsStatInfo>();

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user != null && user.getIdUser() != 0) {
            infoList = mediaShelf.extractVideosInTrash(user.getIdUser(), off, limit);
        } else {
            json.put("error", EnumErrors.INVALID_HASH);
            return createResponse(json);
        }

        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/media/debate/list/{id}")
    @Context
    public Response getMediaActivityInDebate(@PathParam("id") Long idMedia,
                                             @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf();
        JSONObject json = new JSONObject();
        if (idMedia == null || idMedia == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        List<DcDebateEntity> infoList = mediaShelf.extractVideosInDebate(idMedia);

        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/media/related/list")
    @Context
    public Response getRelatedMediaList(@QueryParam("idm") Long idMedia,
                                        @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf(lang);
        JSONObject json = new JSONObject();
        if (idMedia == null || idMedia == 0) {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        List<MediaNewsStatInfo> infoList = new ArrayList<MediaNewsStatInfo>();

        infoList = mediaShelf.getRelatedContents(idMedia);


        return createResponse(infoList);
    }

    @GET
    @Produces("application/json")
    @Path("/media/{id}/activity/{iduser}")
    @Context
    public Response getMediaActivityByUser(@PathParam("id") long idMedia,
                                           @PathParam("iduser") long idUser,
                                           @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf();
        JSONObject json = new JSONObject();
        long result = mediaShelf
                .extractVideoRate(idUser, idMedia);

        json.put("rate", result);
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/media/{id}/flag/{iduser}")
    @Context
    public Response getMediaReportByUser(@PathParam("id") long idMedia,
                                         @PathParam("iduser") long idUser,
                                         @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();
        JSONObject json = new JSONObject();
        long result = usersShelf.isFlaggedTheMedia(idUser, idMedia);

        json.put("flag", result);
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/media/subs/{idsrc}/d/{iddest}")
    @Context
    public Response getMediaSubsByUser(@PathParam("idsrc") long source,
                                       @PathParam("iddest") long dest,
                                       @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();
        JSONObject json = new JSONObject();
        if (source != 0) {
            long result = usersShelf.videoIsFollowed(source, dest);

            json.put("subscribed", result);
        }
        return createResponse(json);
    }

    @GET
    @Produces("application/json")
    @Path("/channels/list/u/{iduser}")
    @Context
    public Response getChannelByActivity(@PathParam("iduser") long idUser,
                                         @QueryParam("off") long off,
                                         @QueryParam("lm") long lm,
                                         @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();

        log.debug("USER ENTER, CHECK CHANNELS: {}", idUser);
        List<PublicChannelsEntity> entity = new ArrayList<>();
        if (idUser != 0) {
            entity = usersShelf.extractChannelByUserSubs(idUser, off, lm);
        }

        return createResponse(entity);
    }

    @GET
    @Produces("application/json")
    @Path("/channels/list/mine/{idhash}")
    @Context
    public Response getMyChannelsByActivity(@PathParam("idhash") String idHash,
                                            @QueryParam("off") long off,
                                            @QueryParam("lm") long lm,
                                            @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();


        if (idHash == null || idHash.isEmpty()) {
            JSONObject json = new JSONObject();
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        List<PublicChannelsEntity> entity = new ArrayList<>();
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user != null && user.getIdUser() != 0) {
            log.debug("USER ENTER, CHECK MY CHANNELS: {} {}", user.getIdUser(), off + ":" + lm);
            entity = usersShelf.extractChannelByUserSubs(user.getIdUser(), off, lm);
        }

        return createResponse(entity);
    }

    @GET
    @Produces("application/json")
    @Path("/channels/list/follow/{idhash}")
    @Context
    public Response getMyChannelsByFollowActivity(@PathParam("idhash") String idHash,
                                                  @QueryParam("off") long off,
                                                  @QueryParam("lm") long lm,
                                                  @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();


        if (idHash == null || idHash.isEmpty()) {
            JSONObject json = new JSONObject();
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        List<PublicChannelsEntity> entity = new ArrayList<>();
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user != null && user.getIdUser() != 0) {
            log.debug("USER ENTER, CHECK MY CHANNELS: {} {}", user.getIdUser(), off + ":" + lm);
            entity = usersShelf.extractChannelByFollowUserSubs(user.getIdUser(), off, lm);
        }

        return createResponse(entity);
    }


    @GET
    @Produces("application/json")
    @Path("/channels/users/{idChannel}/list")
    @Context
    public Response getChannelUsers(@PathParam("idChannel") long idChannel,
                                    @QueryParam("red") String idHash,
                                    @QueryParam("off") long off,
                                    @QueryParam("lm") long limit,
                                    @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf(lang);
        Deque<UserShortInfo> entity = new LinkedList<>();

        log.debug("getChannelUsers {}", idChannel);
        if (idHash == null || idHash.isEmpty() || idChannel == 0) {
            JSONObject json = new JSONObject();
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }
        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        if (user != null) {
            DcChannelsEntity channelsEntity = new MediaShelf().getChannelDataByID(idChannel);
            if (channelsEntity != null && channelsEntity.getPrivacy() != 0) {
                boolean joined = usersShelf.amIFollowChannel(user.getIdUser(), idChannel);
                if (joined) {
                    entity = usersShelf.extractChannelsUsers(idChannel, user.getIdUser(), off, limit);
                } else {
                    JSONObject json = new JSONObject();
                    json.put("error", EnumErrors.FORBIDDEN);
                    return createResponse(json);
                }
            } else if (channelsEntity != null) {
                entity = usersShelf.extractChannelsUsers(idChannel, user.getIdUser(), off, limit);
            }

        }

        return createResponse(entity);
    }


    @GET
    @Produces("application/json")
    @Path("/channels/ch/{idch}")
    @Context
    public Response getChannelInfoByIdChannel(@PathParam("idch") long idChannel,
                                              @QueryParam("red") String hash,
                                              @HeaderParam("Accept-Language") String lang) {

        UsersShelf usersShelf = new UsersShelf();
        JSONObject json = new JSONObject();
        PublicChannelsEntity entity = null;
        boolean access = true;
        if (idChannel > 0 && hash != null && !hash.isEmpty()) {

            DcUsersEntity user = PrivateProperties.extractUserFullInfo(hash);
            if (user == null) {
                json.put("error", EnumErrors.INVALID_HASH);
                return createResponse(json);
            }

            entity = usersShelf.extractChannelByIdChannel(idChannel, user.idUser);
            if (entity != null) {
                if (entity.getPrivacy() == 1) {
                    access = usersShelf.amIFollowChannel(user.idUser, idChannel);
                }

                entity.setState(access ? (short) 1 : 0);

                if (!access && entity.getPrivacy() == 2) {
                    json.put("error", EnumErrors.NO_CHANNEL_ERROR);
                    return createResponse(json);
                }

            } else {
                json.put("error", EnumErrors.NO_CHANNEL_ERROR);
                return createResponse(json);
            }

        } else {
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        return createResponse(entity);
    }

    @GET
    @Produces("application/json")
    @Path("/media/ch/list/{idch}")
    @Context
    public Response getMediaOfChannel(@PathParam("idch") long idChannel,
                                      @QueryParam("off") long offset,
                                      @QueryParam("red") String idHash,
                                      @QueryParam("lm") long limit,
                                      @HeaderParam("Accept-Language") String lang) {

        MediaShelf mediaShelf = new MediaShelf(lang);
        if (idChannel == 0 || idHash == null || idHash.isEmpty()) {
            JSONObject json = new JSONObject();
            json.put("error", EnumErrors.NULL_FIELD_ERROR);
            return createResponse(json);
        }

        DcUsersEntity user = PrivateProperties.extractUserFullInfo(idHash);
        boolean joined = false;
        if (user != null) {
            UsersShelf usersShelf = new UsersShelf();
            DcChannelsEntity channelsEntity = new MediaShelf().getChannelDataByID(idChannel);
            if (channelsEntity != null && channelsEntity.getPrivacy() != 0) {
                joined = usersShelf.amIFollowChannel(user.getIdUser(), idChannel);
            } else if (channelsEntity != null) {
                joined = true;
            }
        }

        List<MediaNewsStatInfo> infoList = new ArrayList<MediaNewsStatInfo>();

        if (joined) {
            infoList = mediaShelf.extractVideoByIdChannel(idChannel, offset, limit);

        } else {
            JSONObject json = new JSONObject();
            json.put("error", EnumErrors.FORBIDDEN);
            return createResponse(json);
        }

        return createResponse(infoList);
    }


    @GET
    @Produces("application/json")
    @Path("/playlist/{id}/info")
    @Context
    public Response getPlaylistInfo(@PathParam("id") String ID,
                                    @HeaderParam("Accept-Language") String lang) {

        DcMediaPlaylist playlist = null;

        if (ID != null) {
            MediaShelf mediaShelf = new MediaShelf();
            playlist = mediaShelf.getPlaylistInfo(ID);
        }
        return createResponse(playlist);
    }

    @GET
    @Produces("application/json")
    @Path("/playlist/media/{id}/")
    @Context
    public Response getMediaFromPlaylist(@PathParam("id") String ID,
                                         @QueryParam("off") int off,
                                         @QueryParam("lm") int lm,
                                         @HeaderParam("Accept-Language") String lang) {

        List<MediaFeaturedBean> mlist = new ArrayList<>();

        if (ID != null) {
            MediaShelf mediaShelf = new MediaShelf();
            mlist = mediaShelf.getMediaFromPlaylist(ID, off, lm);
        }
        return createResponse(mlist);
    }

    @GET
    @Produces("application/json")
    @Path("/users/playlist/{id}/")
    @Context
    public Response getPlaylistByUser(@PathParam("id") Long idUser,
                                         @QueryParam("off") int off,
                                         @QueryParam("lm") int lm,
                                         @HeaderParam("Accept-Language") String lang) {

        List<DcMediaPlaylist> mlist = new ArrayList<>();

        if (idUser != null && idUser!=0) {
            MediaShelf mediaShelf = new MediaShelf();
            mlist = mediaShelf.getPlaylistByIdUser(idUser, off, lm);
        }
        return createResponse(mlist);
    }

    @GET
    @Produces("application/json")
    @Path("/playlist/top/")
    @Context
    public Response getTOPPlaylist(@QueryParam("off") int off,
                                   @QueryParam("lm") int lm,
                                   @HeaderParam("Accept-Language") String lang) {

        List<DcMediaPlaylist> mlist = new ArrayList<>();

        MediaShelf mediaShelf = new MediaShelf();
        mlist = mediaShelf.getTrendingPlaylists(off, lm);

        return createResponse(mlist);
    }

}
