package com.lanparty.dashboard.admin;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AppSetting {

    @Id
    private String key;
    private String value;

    protected AppSetting() {
    }

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
