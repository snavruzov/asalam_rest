package com.dgtz.web.api.tools;

import com.brocast.riak.api.beans.DcUsersEntity;
import com.dgtz.api.contents.MediaShelf;
import com.dgtz.api.utils.IP2LocationDefiner;
import com.dgtz.db.api.domain.DcLocationsEntity;
import com.dgtz.db.api.domain.DcUserSettings;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Digital Citizen.
 * User: Sardor Navuzov
 * Date: 3/27/14
 */
public final class SpecificGeoLocation {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SpecificGeoLocation.class);
    private final static Pattern rfc1123 =
            Pattern.compile("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");


    public SpecificGeoLocation() {
    }

    public String getLocationInfoByGeo(String header, DcUsersEntity user) {

        DcUserSettings json = null;

        String geoIds = "0";

        if (user.getIdUser() != 0) {
            geoIds = json.getLocation();
        }

        log.debug("geoIds: {} header: {}", geoIds, header);

        if (geoIds == null || geoIds.equals("0")) {
            DcLocationsEntity locationsEntity = null;
            if (locationsEntity != null) {
                geoIds = String.valueOf(locationsEntity.getId_country());
            } else {
                geoIds = "0";
            }
        }

        return geoIds;

    }

    public static String geoLocationByWEBIP(String ipAddr) {
        /*if (ipAddr != null && !ipAddr.isEmpty() && rfc1123.matcher(ipAddr).matches()) {
            ipAddr = IP2LocationDefiner.detectIPLocation(ipAddr);
            String[] spl = ipAddr.split(";");
            if (spl == null || spl.length == 0) {
                ipAddr = "";
            } else {
                ipAddr = spl[1] + "@" + spl[0];
            }
        } else*/ {
            ipAddr = "";
        }

        return ipAddr;
    }

    public static String geoLocationByIP(String ipAddr) {
        /*if (ipAddr != null && !ipAddr.isEmpty() && rfc1123.matcher(ipAddr).matches()) {
            ipAddr = IP2LocationDefiner.detectIPLocation(ipAddr);
            String[] spl = ipAddr.split(";");
            if (spl == null || spl.length == 0) {
                ipAddr = "Undefined location;Undefined location";
            } else {
                ipAddr = spl[1] + "@" + spl[0];
            }
        } else {
            ipAddr = "Undefined location;Undefined location";
        }*/

        ipAddr = "Undefined location;Undefined location";

        return ipAddr;
    }

    public static String compareGeoDistance(String gpsLatLong, String ipLatLong) {

        String trueCoordinate = gpsLatLong;
        float lat1 = Float.valueOf(gpsLatLong.split(" ")[1]);
        float lon1 = Float.valueOf(gpsLatLong.split(" ")[0]);

        float lat2 = Float.valueOf(ipLatLong.split(" ")[1]);
        float lon2 = Float.valueOf(ipLatLong.split(" ")[0]);

        int R = 6371; // Radius of the earth in km
        double dLat = deg2rad(lat2 - lat1);  // deg2rad below
        double dLon = deg2rad(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
                                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double distance = R * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))); // Distance in km

        if (distance > 300) {
            trueCoordinate = ipLatLong;
        }

        log.debug("GEO Deistance: {} ; {} dist: {}", new Object[]{gpsLatLong, ipLatLong, distance + ""});

        return trueCoordinate;

    }

    private static double deg2rad(float deg) {
        return deg * (Math.PI / 180);
    }


}
