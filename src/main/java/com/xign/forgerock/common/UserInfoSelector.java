/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock.common;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author palle
 */
public class UserInfoSelector implements Serializable {

    public static final int IGNORE = 0;
    public static final int REQUIRED = 1;
    public static final int OPTIONAL = 2;

    public int sub;
    public int name;
    public int given_name;
    public int family_name;
    public int middle_name;
    public int nickname;
    public int preferred_username;
    public int picture;
    public int profile;
    public int website;
    public int email;
    public int gender;
    public int birthdate;
    public int address;
    public int place_of_birth;
    public int nationality;
    private Map<String, Integer> extras;

    public int getSub() {
        return sub;
    }

    public void setSub(int sub) {
        this.sub = sub;
    }

    public int getName() {
        return name;
    }

    public void setName(int name) {
        this.name = name;
    }

    public int getGiven_name() {
        return given_name;
    }

    public void setGiven_name(int given_name) {
        this.given_name = given_name;
    }

    public int getFamily_name() {
        return family_name;
    }

    public void setFamily_name(int family_name) {
        this.family_name = family_name;
    }

    public int getMiddle_name() {
        return middle_name;
    }

    public void setMiddle_name(int middle_name) {
        this.middle_name = middle_name;
    }

    public int getNickname() {
        return nickname;
    }

    public void setNickname(int nickname) {
        this.nickname = nickname;
    }

    public int getPreferred_username() {
        return preferred_username;
    }

    public void setPreferred_username(int preferred_username) {
        this.preferred_username = preferred_username;
    }

    public int getPicture() {
        return picture;
    }

    public void setPicture(int picture) {
        this.picture = picture;
    }

    public int getProfile() {
        return profile;
    }

    public void setProfile(int profile) {
        this.profile = profile;
    }

    public int getWebsite() {
        return website;
    }

    public void setWebsite(int website) {
        this.website = website;
    }

    public int getEmail() {
        return email;
    }

    public void setEmail(int email) {
        this.email = email;
    }

    public int getGender() {
        return gender;
    }

    public void setGender(int gender) {
        this.gender = gender;
    }

    public int getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(int birthdate) {
        this.birthdate = birthdate;
    }

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public int getPlaceOfBirth() {
        return place_of_birth;
    }

    public void setPlaceOfBirth(int place_of_birth) {
        this.place_of_birth = place_of_birth;
    }

    public int getNationality() {
        return nationality;
    }

    public void setNationality(int nationality) {
        this.nationality = nationality;
    }

    public int getPlace_of_birth() {
        return place_of_birth;
    }

    public void setPlace_of_birth(int place_of_birth) {
        this.place_of_birth = place_of_birth;
    }

    public Map<String, Integer> getExtras() {
        return extras;
    }

    public void setExtras(Map<String, Integer> extras) {
        this.extras = extras;
    }

    public Map<String, Integer> getUserInfoSelectorMap() {
        HashMap<String, Integer> map = new HashMap<>();

        Class c = this.getClass();
        ArrayList<Field> settable = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) & (f.getType() != Map.class)) {
                settable.add(f);
            }
        }

        for (Field f : settable) {
            try {
                map.put(f.getName(), f.getInt(this));
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(UserInfoSelector.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalAccessException ex) {
                Logger.getLogger(UserInfoSelector.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return map;
    }

    public void setItems(Map<String, Integer> items) {
        Class c = this.getClass();
        ArrayList<Field> settable = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) & (f.getType() != Map.class)) {
                settable.add(f);
            }
        }

        for (Field f : settable) {
            if (items.containsKey(f.getName())) {
                try {
                    f.setInt(this, items.get(f.getName()));
                } catch (IllegalArgumentException ex) {
                    Logger.getLogger(UserInfoSelector.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(UserInfoSelector.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

    }

    public void setItems(String scope) {

        Class c = this.getClass();

        String[] attrs = scope.split(" ");
        for (String s : attrs) {
            try {
                if (!s.equals("openid")) {
                    Field f = c.getField(s);

                    f.setInt(this, 1);
                }

            } catch (NoSuchFieldException | SecurityException
                    | IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(UserInfoSelector.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void setItems(Set<String> scopeValues) {

        Class c = this.getClass();

        for (String s : scopeValues) {
            try {
                Field f = c.getField(s);
                f.setInt(this, 1);
            } catch (NoSuchFieldException | SecurityException
                    | IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(UserInfoSelector.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }


    public String getReadableName(String key) {

        String result = "";

        switch (key) {
            case "given_name":
                result = "Vorname";
                break;
            case "family_name":
                result = "Nachname";
                break;
            case "middle_name":
                result = "Zweiter Vorname";
                break;
            case "nickname":
                result = "Benutzername";
                break;
            case "picture":
                result = "Profilbild";
                break;
            case "email":
                result = "E-Mail";
                break;
            case "birthdate":
                result = "Geburtsdatum";
                break;
            case "address":
                result = "Adresse";
                break;
            default:
                break;
        }

        return result;
    }


    public static UserInfoSelector processExtensions(JsonObject req) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException {

        UserInfoSelector result = new UserInfoSelector();

        JsonObject uiSelector = req.getAsJsonObject("ui_selector");
        Set<Entry<String, JsonElement>> selectors = uiSelector.entrySet();

        Set<String> fieldkeys = new HashSet<>();
        Class c = UserInfoSelector.class;
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) & (f.getType() != Map.class)) {
                fieldkeys.add(f.getName());
            }
        }

        HashMap<String, Integer> extras = new HashMap<>();
        for (Entry<String, JsonElement> e : selectors) {
            if (fieldkeys.contains(e.getKey())) {
                c.getDeclaredField(e.getKey()).setInt(result, e.getValue().getAsInt());
            } else {
                // ignore wenn extras als feld gesetzt (von Proxy zu RPEndpoint)
                if (!e.getKey().equals("extras")) {
                    extras.put(e.getKey(), e.getValue().getAsInt());
                } else {
                    Set<Entry<String, JsonElement>> eMap = e.getValue().getAsJsonObject().entrySet();
                    if (eMap.size() > 0) {
                        for (Entry<String, JsonElement> entry : eMap) {
                            extras.put(entry.getKey(), entry.getValue().getAsInt());
                        }
                    }
                }
            }
        }

        c.getDeclaredField("extras").set(result, extras);

        return result;
    }

}
