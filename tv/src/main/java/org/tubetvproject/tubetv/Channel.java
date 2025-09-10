package org.tubetvproject.tubetv;

public class Channel {
    private int id;
    private String name;
    private String number;
    private boolean enabled;
    private String description;

    public Channel() {
    }

    public Channel(int id, String name, String number, boolean enabled, String description) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.enabled = enabled;
        this.description = description;
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
}
