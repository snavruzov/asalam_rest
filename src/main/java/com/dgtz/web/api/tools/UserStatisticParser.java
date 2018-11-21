package com.dgtz.web.api.tools;

import com.brocast.riak.api.beans.DcMediaEntity;
import com.dgtz.api.beans.MediaVIewInfo;
import com.dgtz.api.contents.MediaShelf;
import com.dgtz.api.contents.UsersShelf;
import com.dgtz.db.api.enums.EnumAggregations;
import org.slf4j.LoggerFactory;

/**
 * Digital Citizen.
 * User: Sardor Navuzov
 * Date: 3/3/15
 */
public class UserStatisticParser {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(UserStatisticParser.class);


    public UserStatisticParser() {
    }

    /*type 10 - watching live, 11 - watching video, 20-stream live, 21-upload video, 22 - scheduled*/
    public static int parseAndSave(Long idMedia, Long idUser, int type, String IP, String dname, String agent) {
        int method = 2; //WEB
        if (dname == null || dname.isEmpty()) {
            dname = agent + "|" + 0;
        } else {
            method = 1; //Mobile
            dname = !dname.contains("|")?dname+"|0":dname;
        }

        if (agent != null && agent.contains("Apache-HttpClient/UNAVAILABLE")) {
            method = 1; //Mobile
        } else if (agent == null) {
            agent = "Just Browser";
        }

        String data = IP + "@" + dname.split("[|]")[1] + "@" + dname.split("[|]")[0];
        log.debug("DATA {}", data);
        new UsersShelf().updMediaDeviceParam(idMedia, idUser, type, agent, data);

        return method;
    }

    public static int saveVisitorsStat(MediaVIewInfo entities, MediaShelf mediaShelf, Long idMedia, Long idUser, String hash){
        int watch_type = 11;
        if (entities.method.equals("live")) {
            watch_type = 10;
            log.debug("START LIVE VIEWER NAME: {}", idMedia);
            mediaShelf.insertMediaCounterParam(idMedia, EnumAggregations.LIVE_VIEWED);
            if(idUser!=0 && idUser!=Formulas.DEFAULT_USER) {
                SocketCoordinator.broadcastViewerName(idMedia, hash, idUser);
            }
        } else {
            log.debug("START VIDEO THREAD VIEW COUNT: {}", idMedia);
            mediaShelf.insertMediaCounterParam(idMedia, EnumAggregations.REC_VIEWED);
        }

        return watch_type;
    }

}
