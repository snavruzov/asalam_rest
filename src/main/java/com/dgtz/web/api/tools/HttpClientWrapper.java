package com.dgtz.web.api.tools;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by Intellij IDEA.
 * User: Sardor Navruzov
 * Date: 1/30/13
 * Time: 1:15 PM
 */
public abstract class HttpClientWrapper {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientWrapper.class);

    protected HttpClientWrapper() {
    }


    protected Object doRequestGet(Class clazz, String subUrl) {
        Object ret = null;

        try {
            logger.debug("URL: {}", subUrl);
            DefaultHttpClient httpClient = new DefaultHttpClient();
                /* Set client configurations */
            HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), 10000);
            HttpGet getRequest = new HttpGet(subUrl);
            HttpResponse response = httpClient.execute(getRequest);
            httpClient.getConnectionManager().shutdown();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            logger.error("ERROR IN WEB API ", e);
        }

        logger.debug(subUrl);
        return ret;
    }

    protected String doRequestPost(String url, String json) {
        String ret = "";
        try {
            DefaultHttpClient httpClient = new DefaultHttpClient();
                /* Set client configurations */
            HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), 10000);
            HttpPost postRequest = new HttpPost(url);
            StringEntity input = new StringEntity(json);
            input.setContentType("application/json");
            postRequest.setEntity(input);
            HttpResponse response = httpClient.execute(postRequest);

            if (response.getStatusLine().getStatusCode() != 201) {
                logger.error("Failed : HTTP error code : "
                        + response.getStatusLine().getStatusCode());
            }

            httpClient.getConnectionManager().shutdown();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            logger.error("ERROR IN WEB API ", e);
        }

        return ret;
    }

    protected Object doRequestPut(Class clazz, String subUrl) {
        Object ret = null;
        try {
            DefaultHttpClient httpClient = new DefaultHttpClient();
                /* Set client configurations */
            HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), 10000);
            HttpGet getRequest = new HttpGet(subUrl);
            HttpResponse response = httpClient.execute(getRequest);

            if (response != null) {
                int code = response.getStatusLine().getStatusCode();

                if (HttpStatus.SC_OK == code) {
                    ret = getObjFromResp(response, clazz, subUrl);
                }
            }

            httpClient.getConnectionManager().shutdown();
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            logger.error("ERROR IN WEB API ", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            logger.error("ERROR IN WEB API ", e);
        }

        return ret;
    }

    @SuppressWarnings("rawtypes")
    private Object getObjFromResp(HttpResponse response, Class clazz, String subUrl) {
        Object ret = null;

        try {
            if (response != null) {
                JsonReader reader = new JsonReader(new InputStreamReader(response.getEntity().getContent()));

                logger.debug(reader.peek().toString());
                ret = new Gson().fromJson(reader, clazz);
            } else {
                logger.warn("Response from " + subUrl + " is null");
            }
        } catch (Exception e) {
            logger.error("ERROR IN WEB API ", e);
        }

        return ret;
    }

}
