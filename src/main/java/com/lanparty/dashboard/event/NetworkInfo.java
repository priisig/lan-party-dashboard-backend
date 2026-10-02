package com.lanparty.dashboard.event;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** WLAN, cable LAN and Teamspeak details shown on the overview (and encoded in the WLAN QR code). */
@Embeddable
public class NetworkInfo {

    private String wifiSsid;
    private String wifiPassword;

    @Enumerated(EnumType.STRING)
    private WifiSecurity wifiSecurity = WifiSecurity.WPA;

    private boolean wifiHidden;
    private String lanIpMode;
    private String lanSubnet;
    private String lanGateway;
    private String tsAddress;
    private Integer tsPort;
    private String tsPassword;

    public String getWifiSsid() { return wifiSsid; }
    public void setWifiSsid(String wifiSsid) { this.wifiSsid = wifiSsid; }
    public String getWifiPassword() { return wifiPassword; }
    public void setWifiPassword(String wifiPassword) { this.wifiPassword = wifiPassword; }
    public WifiSecurity getWifiSecurity() { return wifiSecurity; }
    public void setWifiSecurity(WifiSecurity wifiSecurity) { this.wifiSecurity = wifiSecurity; }
    public boolean isWifiHidden() { return wifiHidden; }
    public void setWifiHidden(boolean wifiHidden) { this.wifiHidden = wifiHidden; }
    public String getLanIpMode() { return lanIpMode; }
    public void setLanIpMode(String lanIpMode) { this.lanIpMode = lanIpMode; }
    public String getLanSubnet() { return lanSubnet; }
    public void setLanSubnet(String lanSubnet) { this.lanSubnet = lanSubnet; }
    public String getLanGateway() { return lanGateway; }
    public void setLanGateway(String lanGateway) { this.lanGateway = lanGateway; }
    public String getTsAddress() { return tsAddress; }
    public void setTsAddress(String tsAddress) { this.tsAddress = tsAddress; }
    public Integer getTsPort() { return tsPort; }
    public void setTsPort(Integer tsPort) { this.tsPort = tsPort; }
    public String getTsPassword() { return tsPassword; }
    public void setTsPassword(String tsPassword) { this.tsPassword = tsPassword; }
}
