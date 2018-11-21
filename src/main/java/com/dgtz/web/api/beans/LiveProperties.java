package com.dgtz.web.api.beans;

import java.io.Serializable;

/**
 * Created by root on 1/19/14.
 */
public class LiveProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;
    private String description;
    private Long idCateg;
    private Long idChannel;
    private String tags;
    private String key;
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

    public Long getIdCateg() {
        return idCateg;
    }

    public void setIdCateg(Long idCateg) {
        this.idCateg = idCateg;
    }

    public Long getIdChannel() {
        return idChannel;
    }

    public void setIdChannel(Long idChannel) {
        this.idChannel = idChannel;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getIdHash() {
        return idHash;
    }

    public void setIdHash(String idHash) {
        this.idHash = idHash;
    }

    @Override
    public String toString() {
        return "LiveProperties{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", idCateg=" + idCateg +
                ", idChannel=" + idChannel +
                ", tags='" + tags + '\'' +
                ", key='" + key + '\'' +
                ", idHash='" + idHash + '\'' +
                '}';
    }
}
