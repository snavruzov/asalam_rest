package com.dgtz.web.api.beans;

import com.brocast.riak.api.beans.DcChannelsEntity;
import com.dgtz.db.api.domain.MediaNewsStatInfo;

import java.io.Serializable;
import java.util.List;

/**
 * Created by root on 1/30/14.
 */
public class SearchTitleTag implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<MediaNewsStatInfo> titles;
    private List<MediaNewsStatInfo> tags;
    private List<DcChannelsEntity> channels;


    public List<MediaNewsStatInfo> getTitles() {
        return titles;
    }

    public void setTitles(List<MediaNewsStatInfo> titles) {
        this.titles = titles;
    }

    public List<MediaNewsStatInfo> getTags() {
        return tags;
    }

    public void setTags(List<MediaNewsStatInfo> tags) {
        this.tags = tags;
    }

    public List<DcChannelsEntity> getChannels() {
        return channels;
    }

    public void setChannels(List<DcChannelsEntity> channels) {
        this.channels = channels;
    }
}
