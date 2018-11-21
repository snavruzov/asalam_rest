package com.dgtz.web.api.tools;


import com.brocast.riak.api.beans.DcCommentsEntity;
import com.dgtz.api.contents.UsersShelf;
import com.dgtz.db.api.domain.Notification;
import com.dgtz.mcache.api.factory.Constants;
import com.dgtz.mcache.api.factory.RMemoryAPI;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * Created by Sardor Navruzov on 7/6/15.
 * Copyrights BroCast Co.
 * SocketCoordinator is designed to service/push requested queries to the WEB-SOCKET serverAPI (API_WEBSOCKET)
 */
public final class SocketCoordinator {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SocketCoordinator.class);

    public SocketCoordinator() {
    }

    public static void broadcastViewerNumber(Long idMedia, String idHash) {
        /*Notifying Live WS server*/
        JsonNode node =
                new JsonNode("{\"time\":\"" + RMemoryAPI.getInstance().currentTimeMillis() + "\",\"idHash\":\"" + idHash + "\"," +
                        "\"text\":\" \",\"idMedia\":\"" + idMedia + "\",\"wsType\":2}");

        try {
            Future<HttpResponse<JsonNode>> rStatus =
                    Unirest.post(Constants.WEBSOCKET_URL + "ws/media/" + idMedia)
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
        } catch (Exception e) {
            log.error("ERROR IN SENDING BROADCAST WS", e);
        }
    }

    public static void broadcastViewerName(Long idMedia, String idHash, Long idUser) {
        boolean ifSend = RMemoryAPI.getInstance().pullIfSetElem(Constants.MEDIA_KEY + "viewers:" + idMedia, idUser + "");


        if (!ifSend) {
        /*Notifying Live WS server*/
            JsonNode node =
                    new JsonNode("{\"time\":\"" + RMemoryAPI.getInstance().currentTimeMillis() + "\",\"idHash\":\"" + idHash + "\"," +
                            "\"text\":\" \",\"idMedia\":\"" + idMedia + "\",\"wsType\":8}");

            RMemoryAPI.getInstance().pushSetElemToMemory(Constants.MEDIA_KEY + "viewers:" + idMedia, idUser + "");

            try {
                String username = RMemoryAPI.getInstance().pullHashFromMemory(Constants.USER_KEY+idUser, "username");
                //pushSystemComment(idMedia, username+Constants.TXT_LIVE_JOINED);

                Future<HttpResponse<JsonNode>> rStatus =
                        Unirest.post(Constants.WEBSOCKET_URL + "ws/media/" + idMedia)
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
            } catch (Exception e) {
                log.error("ERROR IN SENDING BROADCAST WS", e);
            }
        }
    }

    public static void pushSystemComment(Long idMedia, String text) {

        String idAuthor = RMemoryAPI.getInstance().pullHashFromMemory(Constants.MEDIA_KEY + idMedia, "id_user");

        if (idAuthor != null) {
            DcCommentsEntity commentsEntity = new DcCommentsEntity();
            commentsEntity.idUser = Formulas.DEFAULT_USER;
            commentsEntity.idComment = System.currentTimeMillis();
            commentsEntity.idMedia = (idMedia);
            commentsEntity.text = text;
            commentsEntity.commentType = 2;
            commentsEntity.url = "";
            commentsEntity.duration = 0;

            String hash = RMemoryAPI.getInstance().pullHashFromMemory(Constants.USER_KEY + Formulas.DEFAULT_USER, "hash");
            String idTime = RMemoryAPI.getInstance()
                    .pullHashFromMemory(Constants.MEDIA_KEY + idMedia, "start-time");
            long timeFrac = (RMemoryAPI.getInstance().currentTime() - Long.valueOf(idTime));
            Long idComment = new UsersShelf().insertFreshComments(commentsEntity, Formulas.DEFAULT_USER, timeFrac);

            Notification notification = new Notification();
            notification.setIdFrom(Formulas.DEFAULT_USER);
            notification.setType(18);
            notification.setDuration((short) 0);
            notification.setIdMedia(idMedia);
            notification.setText(text);
            notification.setCommentType(2);
            notification.setIdUser(Long.valueOf(idAuthor));

            try {
                JsonNode node =
                        new JsonNode("{\"time\":\"" + RMemoryAPI.getInstance().currentTimeMillis() + "\",\"idHash\":\"" + hash + "\"," +
                                "\"text\":\"" + text + "\",\"idMedia\":\"" + idMedia + "\"," +
                                "\"wsType\":" + notification.getType() + ",\"ratingType\":" + 0 + "," +
                                "\"colorType\":" + 0 + "," +
                                "\"rotate\":" + 0 + ",\"duration\":" + 0 + "" +
                                ",\"commentType\":" + notification.getCommentType() + "}");
                Future<HttpResponse<JsonNode>> rStatus =
                        Unirest.post(Constants.WEBSOCKET_URL + "/ws/media/" + idMedia)
                                .header("Content-Type", "application/json")
                                .body(node)
                                .asJsonAsync(new Callback<JsonNode>() {
                                    public void failed(UnirestException e) {
                                        log.debug("The WS request has failed");
                                    }

                                    public void completed(HttpResponse<JsonNode> response) {
                                        int code = response.getStatus();
                                        log.debug("Completed {}", code);
                                    }

                                    public void cancelled() {
                                        log.debug("The request has been cancelled");
                                    }
                                });
            } catch (Exception e) {
                log.error("ERROR IN SENDING BROADCAST WS", e);
            }
        }
    }

    public static void broadcastVoiceMessage(Long idMedia, String idHash, long duration) {
        /*Notifying VOICE WS server*/
        JsonNode node =
                new JsonNode("{\"time\":\"" + RMemoryAPI.getInstance().currentTimeMillis() + "\",\"idHash\":\"" + idHash + "\"," +
                        "\"text\":\" \",\"idMedia\":\"" + idMedia + "\",\"commentType\":\"" + 1 + "\"" +
                        ",\"duration\":\"" + duration + "\",\"wsType\":0}");

        try {
            Future<HttpResponse<JsonNode>> rStatus =
                    Unirest.post(Constants.WEBSOCKET_URL + "web-socket/media/" + idMedia)
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
        } catch (Exception e) {
            log.error("ERROR IN SENDING BROADCAST WS", e);
        }

    }

    public static void broadcastLikeMessage(Long idMedia, String idHash) {
        /*Notifying VOICE WS server*/
        JsonNode node =
                new JsonNode("{\"time\":\"" + RMemoryAPI.getInstance().currentTimeMillis() + "\",\"idHash\":\"" + idHash + "\"," +
                        "\"text\":\" \",\"idMedia\":\"" + idMedia + "\",\"commentType\":\"" + 0 + "\",\"wsType\":1}");

        try {
            Future<HttpResponse<JsonNode>> rStatus =
                    Unirest.post(Constants.WEBSOCKET_URL + "web-socket/media/" + idMedia)
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
        } catch (Exception e) {
            log.error("ERROR IN SENDING BROADCAST WS", e);
        }

    }

    public static void broadcastLiveEndNotification(long idLive) {

            /*Notifying Live publisher through WS server that Live ended*/
        JsonNode node =
                new JsonNode("{\"time\":\"12345678\",\"idHash\":\""+Formulas.DEFAULT_HASH+"\"," +
                        "\"text\":\" \",\"idMedia\":\"" + idLive + "\",\"wsType\":3}");

        Future<HttpResponse<JsonNode>> rStatus =
                Unirest.post(Constants.WEBSOCKET_URL + "/web-socket/media/" + idLive)
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

    public static void sendLiveDeletedNotification(long idLive) {

            /*Notifying Live publisher through WS server that Live terminated*/
        JsonNode node =
                new JsonNode("{\"time\":\"12345678\",\"idHash\":\""+Formulas.DEFAULT_HASH+"\"," +
                        "\"text\":\" \",\"idMedia\":\"" + idLive + "\",\"wsType\":2}");

        Future<HttpResponse<JsonNode>> rStatus =
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

    public static void sendLiveStartedNotification(long idLive) {

            /*Notifying Live publisher through WS server that Live started*/
        JsonNode node =
                new JsonNode("{\"time\":\"12345678\",\"idHash\":\""+Formulas.DEFAULT_HASH+"\"," +
                        "\"text\":\" \",\"idMedia\":\"" + idLive + "\",\"wsType\":1}");

        Future<HttpResponse<JsonNode>> rStatus =
                Unirest.post(Constants.WEBSOCKET_URL + "/web-socket/media/all/map")
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
