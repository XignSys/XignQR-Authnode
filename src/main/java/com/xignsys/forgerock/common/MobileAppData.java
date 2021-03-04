package com.xignsys.forgerock.common;

import java.io.Serializable;

/**
 * @author Pascal Manaras <manaras at xignsys.com>
 */
public class MobileAppData implements Serializable {
      /*
    *
    * {
        "managerUri": "${managerUri}",
        "clientId": "${clientId}",
        "redirectUri": "${redirectUri}",
        "submitUri": "${url.loginAction}"
    }
    *
    * */

    private final String managerUri;
    private final String clientId;
    private final String redirectUri;
    private final String submitUri;

    public MobileAppData(String managerUri, String clientId, String redirectUri, String submitUri) {
        this.managerUri = managerUri;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.submitUri = submitUri;
    }

    public String getManagerUri() {
        return managerUri;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getSubmitUri() {
        return submitUri;
    }
}
