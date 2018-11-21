package com.dgtz.web.api.beans;

import java.io.Serializable;

/**
 * Created by root on 1/15/14.
 */
public class ChannelsProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;
    private String description;
    private String avatar;
    private String idHash;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getIdHash() {
        return idHash;
    }

    public void setIdHash(String idHash) {
        this.idHash = idHash;
    }

    @Override
    public String toString() {
        return "ChannelsProperties{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", avatar='" + avatar + '\'' +
                ", idHash='" + idHash + '\'' +
                '}';
    }
}
