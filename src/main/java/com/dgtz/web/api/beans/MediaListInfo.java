package com.dgtz.web.api.beans;


/**
 * Created by sardor on 1/4/14.
 */
public class MediaListInfo {

    private long idMedia;
    private java.lang.String title;
    private java.lang.String url;
    private java.lang.String url_mp4_low;
    private java.lang.String url_mp4_hi;
    private java.lang.String url_webm_low;
    private java.lang.String contentType;
    private java.lang.Short duration;
    private String dateadded;
    private int idCategory;
    private boolean live;
    private long idUser;
    private String username;
    private String ctitle;
    private String location;
    private long amount;
    private long liked;
    private long channel;

    public MediaListInfo() {
    }

    public MediaListInfo(long idMedia, String title, String url, Short duration, String dateadded, int idCategory, boolean live, long idUser) {
        this.idMedia = idMedia;
        this.title = title;
        this.url = url;
        this.duration = duration;
        this.dateadded = dateadded;
        this.idCategory = idCategory;
        this.live = live;
        this.idUser = idUser;
    }

    public String getUrl_mp4_low() {
        return url_mp4_low;
    }

    public void setUrl_mp4_low(String url_mp4_low) {
        this.url_mp4_low = url_mp4_low;
    }

    public String getUrl_mp4_hi() {
        return url_mp4_hi;
    }

    public void setUrl_mp4_hi(String url_mp4_hi) {
        this.url_mp4_hi = url_mp4_hi;
    }

    public String getUrl_webm_low() {
        return url_webm_low;
    }

    public void setUrl_webm_low(String url_webm_low) {
        this.url_webm_low = url_webm_low;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getLiked() {
        return liked;
    }

    public void setLiked(long liked) {
        this.liked = liked;
    }

    public long getChannel() {
        return channel;
    }

    public void setChannel(long channel) {
        this.channel = channel;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getIdMedia() {
        return idMedia;
    }

    public void setIdMedia(long idMedia) {
        this.idMedia = idMedia;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Short getDuration() {
        return duration;
    }

    public void setDuration(Short duration) {
        this.duration = duration;
    }

    public String getDateadded() {
        return dateadded;
    }

    public void setDateadded(String dateadded) {
        this.dateadded = dateadded;
    }

    public int getIdCategory() {
        return idCategory;
    }

    public void setIdCategory(int idCategory) {
        this.idCategory = idCategory;
    }

    public boolean isLive() {
        return live;
    }

    public void setLive(boolean live) {
        this.live = live;
    }

    public long getIdUser() {
        return idUser;
    }

    public void setIdUser(long idUser) {
        this.idUser = idUser;
    }

    public String getCtitle() {
        return ctitle;
    }

    public void setCtitle(String ctitle) {
        this.ctitle = ctitle;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "MediaListInfo{" +
                "idMedia=" + idMedia +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", duration=" + duration +
                ", dateadded=" + dateadded +
                ", idCategory=" + idCategory +
                ", isLive=" + live +
                ", idUser=" + idUser +
                ", username='" + username + '\'' +
                ", ctitle='" + ctitle + '\'' +
                ", amount=" + amount +
                ", channel=" + channel +
                '}';
    }
}
