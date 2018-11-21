package com.dgtz.web.api.beans;

import java.io.Serializable;
import java.util.List;

/**
 * Digital Citizen.
 * User: Sardor Navuzov
 * Date: 8/27/14
 */
public class MCDNEncoderBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<MCDNMediaParams> originSource;
    private String hostUntil;
    private List<String> locations;
    private List<String> outputFormats;
    private List<String> quality;
    private String mediaType;
    private Boolean duplicates;
    private String mediaBrandingTemplateName;
    private List<String> tags;

    public List<MCDNMediaParams> getOriginSource() {
        return originSource;
    }

    public void setOriginSource(List<MCDNMediaParams> originSource) {
        this.originSource = originSource;
    }

    public String getHostUntil() {
        return hostUntil;
    }

    public void setHostUntil(String hostUntil) {
        this.hostUntil = hostUntil;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public List<String> getOutputFormats() {
        return outputFormats;
    }

    public void setOutputFormats(List<String> outputFormats) {
        this.outputFormats = outputFormats;
    }

    public List<String> getQuality() {
        return quality;
    }

    public void setQuality(List<String> quality) {
        this.quality = quality;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Boolean getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(Boolean duplicates) {
        this.duplicates = duplicates;
    }

    public String getMediaBrandingTemplateName() {
        return mediaBrandingTemplateName;
    }

    public void setMediaBrandingTemplateName(String mediaBrandingTemplateName) {
        this.mediaBrandingTemplateName = mediaBrandingTemplateName;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
