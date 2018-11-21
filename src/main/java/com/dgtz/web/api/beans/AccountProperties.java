package com.dgtz.web.api.beans;

import java.io.Serializable;

/**
 * Created by sardor on 1/13/14.
 */
public class AccountProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private String idHash;
    private String email;
    private String password;
    private String fullName;
    private String username;
    private transient String birthday;
    private String moreEmail;
    private String city;
    private String country;
    private String mobileNumber;
    private String about;
    private boolean isAnonymous;

    public String getIdHash() {
        return idHash;
    }

    public void setIdHash(String idHash) {
        this.idHash = idHash;
    }

    public boolean isAnonymous() {
        return isAnonymous;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setAnonymous(boolean isAnonymous) {
        this.isAnonymous = isAnonymous;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getMoreEmail() {
        return moreEmail;
    }

    public void setMoreEmail(String moreEmail) {
        this.moreEmail = moreEmail;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    @Override
    public String toString() {
        return "AccountProperties{" +
                "idHash='" + idHash + '\'' +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", fullName='" + fullName + '\'' +
                ", birthday='" + birthday + '\'' +
                ", moreEmail='" + moreEmail + '\'' +
                ", city='" + city + '\'' +
                ", country='" + country + '\'' +
                ", mobileNumber='" + mobileNumber + '\'' +
                ", about='" + about + '\'' +
                ", isAnonymous=" + isAnonymous +
                '}';
    }
}
