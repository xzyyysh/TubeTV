package io.tubetvlol.tubetv.models;

public class Channel {
    private int id;
    private String name;
    private String number;
    private boolean enabled;
    private String description;
    private String streamUrl;
    private String logo;

    public Channel() {
    }

    public Channel(int id, String name, String number, boolean enabled, String description, String streamUrl, String logo) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.enabled = enabled;
        this.description = description;
        this.streamUrl = streamUrl;
        this.logo = logo;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }
}