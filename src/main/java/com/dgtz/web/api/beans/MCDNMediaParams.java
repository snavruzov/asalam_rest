package com.dgtz.web.api.beans;

/**
 * Digital Citizen.
 * User: Sardor Navuzov
 * Date: 8/27/14
 */
public class MCDNMediaParams {

    private String originUrl;
    private String aspectRatio;
    private String title;
    private String description;

    public String getOriginUrl() {
        return originUrl;
    }

    public void setOriginUrl(String originUrl) {
        this.originUrl = originUrl;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

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
}
