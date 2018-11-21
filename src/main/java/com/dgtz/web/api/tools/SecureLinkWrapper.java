package com.dgtz.web.api.tools;

import com.dgtz.mcache.api.factory.Constants;
import org.slf4j.LoggerFactory;

/**
 * Digital Citizen.
 * User: Sardor Navuzov
 * Date: 3/3/14
 */
public class SecureLinkWrapper {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SecureLinkWrapper.class);

    private Long idUser;
    private Long idMedia;
    private String url;
    private String type = "video";

    public SecureLinkWrapper() {
    }


    public void fillField(Long idUser, Long idMedia, String url) {
        this.url = url;
        this.idMedia = idMedia;
        this.idUser = idUser;
    }


    public String[] wrapSecureLink(boolean isBrowse, int method) {

        String url_mp4_lo = "";
        String url_webm_hi = "";
        String url_webm_low = "";
        String url_rtmp = "";
        String url_hls_vod = "";
        String url_hls_lo = "";
        String url_thumb = "/media/thumb/0/default.jpg";

        if (url.contains("media/v")) {
            if (!isBrowse) {
                url = Constants.encryptAmazonURL(idUser, idMedia, null, "thumb", Constants.STATIC_URL);
            } else {
                url = Constants.encryptAmazonURL(idUser, idMedia, "_hi.mp4", "v", Constants.VIDEO_URL);
                //url = s3.generateTokenUrl(url, AmazonS3Module.VIDEO_BUCKET);

                url_thumb = Constants.encryptAmazonURL(idUser, idMedia, null, "p", Constants.STATIC_URL);

                url_mp4_lo = Constants.encryptAmazonURL(idUser, idMedia, "_lo.mp4", "v", Constants.VIDEO_URL);
                //url_mp4_lo = s3.generateTokenUrl(url_mp4_lo, AmazonS3Module.VIDEO_BUCKET);

                url_webm_hi = Constants.encryptAmazonURL(idUser, idMedia, "_hi.webm", "v", Constants.VIDEO_URL);
                //url_webm_hi = s3.generateTokenUrl(url_webm_hi, AmazonS3Module.VIDEO_BUCKET);

                url_webm_low = Constants.encryptAmazonURL(idUser, idMedia, "_lo.webm", "v", Constants.VIDEO_URL);
                //url_webm_low = s3.generateTokenUrl(url_webm_low, AmazonS3Module.VIDEO_BUCKET);

                url_hls_vod = Constants.encryptAmazonURL(idUser, idMedia, "", "hls", Constants.VIDEO_URL);
            }
            type = "video";
        } else if (url.contains("m3u8")) {
            if (!isBrowse) {
                url = Constants.encryptAmazonURL(idUser, idMedia, null, "thumb", Constants.STATIC_URL);
            } else {
                url_thumb = Constants.encryptAmazonURL(idUser, idMedia, null, "thumb", Constants.STATIC_URL);

                //url_rtmp = RMemoryAPI.getInstance().pullHashFromMemory(Constants.LIVE_KEY + idMedia, "rtmp_liveurl");
                //log.debug("Setting RTMP URL {}", url_rtmp);
                url_rtmp = Constants.RTMP_URL + "2010A02/live_id" + idUser + "_" + idMedia;
                url_hls_lo = url.replace("index", "index_lo");

            }

            type = "video";
        }

        return new String[]{url, url_webm_low, url_webm_hi, url_mp4_lo, url_thumb, url_rtmp, url_hls_vod, url_hls_lo};
    }

    public String wrapSecureLinkForThumbEdit(int seq) {

        //url = Constants.encryptAmazonURL(idUser, idMedia,null, "instance");
        return url;
    }

    public String getContentType() {
        return type;
    }
}
