package com.dgtz.web.api.tools;

import com.dgtz.db.api.factory.GsonInsta;
import com.dgtz.mcache.api.factory.Constants;
import com.dgtz.mcache.api.factory.RMemoryAPI;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Created by Sardor Navruzov CEO, DGTZ.
 */
public final class SendCompressingSignal extends HttpClientWrapper {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SendCompressingSignal.class);

    private String title;
    private String username;
    private long toId;
    private long fromId;
    private Long idChannel;
    private boolean isMulti;
    private boolean push;

    public SendCompressingSignal() {

    }

    public SendCompressingSignal(String title, String username, long toId, long fromId, Long idChannel,
                                 boolean isMulti, boolean push) {
        this.title = title;
        this.username = username;
        this.toId = toId;
        this.fromId = fromId;
        this.idChannel = idChannel;
        this.isMulti = isMulti;
        this.push = push;

    }

    private static final String COMPRESS_URL = Constants.ENCODING_URL + "compresser/algo/tools/compress" +
            "?idm=%s&idu=%s&dura=%s&live=%s&rotation=%s";

    private static final String TRANSPOSE_URL = Constants.ENCODING_URL + "compresser/algo/tools/transpose" +
            "?idm=%s&idu=%s&rotation=%s";

    public void sendToCompress(final long idMedia, final long idUser, final short duration, final boolean isLive, final int rotation) {

        RMemoryAPI.getInstance().pushSetElemToMemory(Constants.MEDIA_KEY + "queue", idMedia + "");
        Thread thread = new Thread() {
            public void run() {
                JsonErrorStatus errorStatus = (JsonErrorStatus) doRequestGet(JsonErrorStatus.class,
                        String.format(COMPRESS_URL, idMedia, idUser, duration, isLive, rotation));
                log.debug("ERROR STATUS: {}", errorStatus);
            }
        };

        thread.setName("send-compress-thread-" + System.currentTimeMillis());
        thread.start();

    }


    public String sendToTranspose(final long idMedia, final long idUser, final int rotation) {

        JsonErrorStatus errorStatus = (JsonErrorStatus) doRequestGet(JsonErrorStatus.class,
                String.format(TRANSPOSE_URL, idMedia, idUser, rotation));
        log.debug("ERROR TRANSPOSE STATUS: {}", errorStatus.getError());

        return errorStatus.getError();

    }


}


class JsonErrorStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    private String error = "NULL";

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return GsonInsta.getInstance().toJson(this);
    }
}
