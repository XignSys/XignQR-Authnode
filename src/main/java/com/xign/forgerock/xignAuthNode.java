/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2018 ForgeRock AS.
 */
package com.xign.forgerock;

import com.xign.forgerock.exception.XignTokenException;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.shared.debug.Debug;
import com.xign.api.json.JWTClaims;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.CoreWrapper;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import static org.forgerock.openam.auth.node.api.Action.send;

/**
 * A node that checks to see if zero-page login headers have specified username
 * and shared key for this request.
 */
@Node.Metadata(outcomeProvider = AbstractDecisionNode.OutcomeProvider.class,
        configClass = xignAuthNode.Config.class)
public class xignAuthNode extends AbstractDecisionNode {

    private final Config config;
    private final CoreWrapper coreWrapper;
    private final static String DEBUG_FILE = "XignQR";
    protected Debug debug = Debug.getInstance(DEBUG_FILE);
    private final String redirectUri, clientId, managerUrl;
    private final InputStream propertiesInput;

    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order = 100)
        String pathToXignConfig();

        @Attribute(order = 200)
        Map<String, String> mapping();

    }

    /**
     * Create the node.
     *
     * @param config The service config.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public xignAuthNode(@Assisted Config config, CoreWrapper coreWrapper) throws NodeProcessException {
        this.config = config;
        this.coreWrapper = coreWrapper;

        // read properties file
        try {
            propertiesInput = new FileInputStream(config.pathToXignConfig());
        } catch (FileNotFoundException ex) {
            debug.error(ex.getMessage());
            throw new NodeProcessException(ex.getMessage());
        }

        Properties properties = new Properties();
        try {
            properties.load(propertiesInput);
        } catch (IOException ex) {
            debug.error(ex.getMessage());
            throw new NodeProcessException("error loading config file");
        }

        try {
            propertiesInput.close();
        } catch (IOException ex) {
            debug.error(ex.getMessage());
        }

        // openid connect redirect uri
        redirectUri = properties.getProperty("client.redirect_uri");

        clientId = properties.getProperty("client.id");

        // where to connect for retrieval of qrcode in JS
        managerUrl = properties.getProperty("manager.url.token").replace("/token", "");
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        String xignInScript = "if (document.readyState !== 'loading') {\n"
                + "    callback();\n"
                + "\n"
                + "} else {\n"
                + "    document.addEventListener(\"DOMContentLoaded\", callback);\n"
                + "}\n"
                + ";\n"
                + "\n"
                + "\n"
                + "function callback() {\n"
                + "    let xignDivTag = document.createElement(\"div\");\n"
                + "    xignDivTag.id = \"xign_tag\";\n"
                + "    xignDivTag.style.width = \"200px\";\n"
                + "    xignDivTag.style.marginRight = \"auto\";\n"
                + "    xignDivTag.style.marginLeft = \"auto\";\n"
                + "    xignDivTag.style.marginBottom = \"40px\";\n"
                + "    let loginContainer = document.getElementById(\"content\");\n"
                + "    let loginButton = loginContainer.firstChild;\n"
                + "    loginContainer.insertBefore(xignDivTag, loginButton);\n"
                + "    document.getElementById(\"loginButton_0\").style.display = \"none\" \n"
                + "\n"
                + "    let config = {\n"
                + "        redirect_uri: \"" + redirectUri + "\",\n"
                + "        client_id: \"" + clientId + "\",\n"
                + "        userinfo_selector: {\n"
                + "            nickname: 1,\n"
                + "            email: 1\n"
                + "        },\n"
                + "        mode: 6\n"
                + "    };\n"
                + "    var xignin = new XignINSSO(config);\n"
                + "    xignin.authenticate();\n"
                + "}\n"
                + "\n"
                + "function XignINSSO(configuration) {\n"
                + "    const XMGRURL = \"" + managerUrl + "\";\n"
                + "    const WSXMGRURL = XMGRURL.replace(\"http\", \"ws\");\n"
                + "    const status_uri = XMGRURL + \"/sso\";\n"
                + "    const xtag = document.getElementById(\"xign_tag\");\n"
                + "    var wSocket;\n"
                + "    var gui_channel;\n"
                + "\n"
                + "    this.authenticate = function () {\n"
                + "        wSocket = new WebSocket(WSXMGRURL + \"/login\");\n"
                + "        wSocket.onopen = onopen;\n"
                + "        wSocket.onmessage = onmessage;\n"
                + "        wSocket.onclose = onclose;\n"
                + "    };\n"
                + "\n"
                + "    var onopen = function () {\n"
                + "\n"
                + "        if (!isMobileBrowser()) {\n"
                + "            requestAuthentication(configuration[\"userinfo_selector\"],\n"
                + "                    configuration[\"client_id\"],\n"
                + "                    configuration[\"redirect_uri\"],\n"
                + "                    undefined,\n"
                + "                    configuration[\"mode\"],\n"
                + "                    configuration[\"version\"],\n"
                + "                    gui_channel);\n"
                + "        } else {\n"
                + "            requestAuthentication(configuration[\"userinfo_selector\"],\n"
                + "                    configuration[\"client_id\"],\n"
                + "                    configuration[\"redirect_uri\"],\n"
                + "                    undefined,\n"
                + "                    8,\n"
                + "                    configuration[\"version\"],\n"
                + "                    gui_channel);\n"
                + "        }\n"
                + "    };\n"
                + "\n"
                + "    var onmessage = function (event) {\n"
                + "        var message = event.data;\n"
                + "        var response = JSON.parse(message);\n"
                + "        var eventType = response[\"eventType\"];\n"
                + "        if (eventType === 7) {\n"
                + "\n"
                + "        } else if (eventType === eventTypes.QR_CODE_RECEIVED) {\n"
                + "            handleQRCode(response[\"data\"]);\n"
                + "        } else if (eventType === eventTypes.AUTH_RESULT) {\n"
                + "            handleAuthResult(response[\"data\"]);\n"
                + "        } else if (eventType === eventTypes.AUTH_ABORTED) {\n"
                + "            handleAbort();\n"
                + "        } else if (eventType === eventTypes.GUI_STATUS) {\n"
                + "            handleGuiStatus();\n"
                + "        }\n"
                + "    };\n"
                + "\n"
                + "\n"
                + "    var onclose = function (event) {\n"
                + "        reset();\n"
                + "    };\n"
                + "\n"
                + "    var handleAuthResult = function (data) {\n"
                + "\n"
                + "        var status = data.success;\n"
                + "        if (status === true) {\n"
                + "            handleCode(data);\n"
                + "        } else if (status === false) {\n"
                + "            feedback(\"authentication failed due to reasons\");\n"
                + "        }\n"
                + "    };\n"
                + "\n"
                + "\n"
                + "    var handleCode = function (data) {\n"
                + "        const refresh = data['redirectUri'];\n"
                + "        const code = data['code'];\n"
                + "        let ap;\n"
                + "        if (refresh.indexOf('?') > -1) {\n"
                + "            ap = \"&code=\";\n"
                + "        } else {\n"
                + "            ap = \"?code=\";\n"
                + "        }\n"
                + "\n"
                + "        document.forms[0].elements['code'].value = refresh + ap + code;\n"
                + "        document.querySelector(\"input[type=submit]\").click();\n"
                + "    };\n"
                + "\n"
                + "\n"
                + "\n"
                + "    var handleQRCode = function (data) {\n"
                + "        if (!isMobileBrowser()) {\n"
                + "            if (configuration[\"mode\"] !== 12) {\n"
                + "                if (configuration[\"mode\"] !== 9) {\n"
                + "                    buildQRCode(xtag, data[\"qrCode\"]);\n"
                + "                    function loaded() {\n"
                + "                        document.querySelector('#the_progress').style.backgroundColor = \"red\";\n"
                + "                        document.querySelector('#the_progress').style.width = \"100%\";\n"
                + "                    }\n"
                + "                    setTimeout(loaded, 100);\n"
                + "                } else {\n"
                + "                    sendToDesktop(data['token']);\n"
                + "                }\n"
                + "            } else {\n"
                + "                buildForm(xtag);\n"
                + "            }\n"
                + "        } else {\n"
                + "            let magicLink = document.createElement('a');\n"
                + "            if (isAndroid()) {\n"
                + "                magicLink.href = \"intent://view?id=123#Intent;package=com.xignsys;S.qrcode=\" + encodeURIComponent(data['token']) + \";scheme=XignQR;launchFlags=268435456;end;\";\n"
                + "                document.querySelector('body').appendChild(magicLink);\n"
                + "                magicLink.click();\n"
                + "            } else if (isIOS()) {\n"
                + "                magicLink.href = \"XignQR://?qrcode=\" + encodeURIComponent(data['token']);\n"
                + "                document.querySelector('body').appendChild(magicLink);\n"
                + "                magicLink.click();\n"
                + "            } else {\n"
                + "                alert('plattform not supported');\n"
                + "            }\n"
                + "        }\n"
                + "    };\n"
                + "\n"
                + "    var handleAbort = function () {\n"
                + "        feedback(\"Auth Aborted\");\n"
                + "    };\n"
                + "\n"
                + "    var handleGuiStatus = function (data) {\n"
                + "\n"
                + "    };\n"
                + "\n"
                + "    var sendMessage = function (obj) {\n"
                + "        wSocket.send(JSON.stringify(obj));\n"
                + "    };\n"
                + "\n"
                + "\n"
                + "    var requestAuthentication = function (ui_selector, client_id, refresh,\n"
                + "            transaction_data, mode, version) {\n"
                + "        const contextInformation = {};\n"
                + "        const request = {\n"
                + "            eventType: eventTypes.AUTH_REQUEST,\n"
                + "            redirect_uri: refresh,\n"
                + "            client_id: client_id,\n"
                + "            transaction: transaction_data,\n"
                + "            ui_selector: ui_selector,\n"
                + "            loginMode: mode,\n"
                + "            contextInformation: contextInformation,\n"
                + "            version: version,\n"
                + "            channel: random()\n"
                + "        };\n"
                + "        sendMessage(request);\n"
                + "    };\n"
                + "\n"
                + "    var getContextInformation = function () {\n"
                + "\n"
                + "        var client = new ClientJS();\n"
                + "        let os = client.getOS();\n"
                + "        let osVersion = client.getOSVersion();\n"
                + "        let browser = client.getBrowser();\n"
                + "        let browserVersion = client.getBrowserVersion();\n"
                + "        let engine = client.getEngine();\n"
                + "        let engineVersion = client.getEngineVersion();\n"
                + "        let isMobile = client.isMobile();\n"
                + "\n"
                + "        let isIPad = false;\n"
                + "        let isIPhone = false;\n"
                + "        if (client.isMobileIOS()) {\n"
                + "            isIPhone = client.isIphone();\n"
                + "            isIPad = client.isIpad();\n"
                + "        }\n"
                + "\n"
                + "        let vendor = client.getDeviceVendor();\n"
                + "        let fingerPrint = client.getFingerprint();\n"
                + "        let timezone = client.getTimeZone();\n"
                + "        let colorDepth = client.getColorDepth();\n"
                + "        let resolution = client.getCurrentResolution();\n"
                + "\n"
                + "        let isJava = client.isJava();\n"
                + "        let javaVersion = 0;\n"
                + "        if (isJava) {\n"
                + "            javaVersion = client.getJavaVersion();\n"
                + "        }\n"
                + "\n"
                + "        let isSilverlight = client.isSilverlight();\n"
                + "        let silverlightVersion = 0;\n"
                + "        if (isSilverlight) {\n"
                + "            silverlightVersion = client.getSilverlightVersion();\n"
                + "        }\n"
                + "\n"
                + "\n"
                + "        let isFlash = client.isFlash();\n"
                + "        let flashVersion = 0;\n"
                + "        if (isFlash) {\n"
                + "            flashVersion = client.getFlashVersion();\n"
                + "        }\n"
                + "\n"
                + "        let language = client.getLanguage();\n"
                + "\n"
                + "        let result = {\n"
                + "            os: os,\n"
                + "            osVersion: osVersion,\n"
                + "            browser: browser,\n"
                + "            browserVersion: browserVersion,\n"
                + "            engine: engine,\n"
                + "            engineVersion: engineVersion,\n"
                + "            isMobile: isMobile,\n"
                + "            isIPad: isIPad,\n"
                + "            isIPhone: isIPhone,\n"
                + "            vendor: vendor,\n"
                + "            fingerPrint: fingerPrint,\n"
                + "            timezone: timezone,\n"
                + "            colorDepth: colorDepth,\n"
                + "            resolution: resolution,\n"
                + "            isJava: isJava,\n"
                + "            javaVersion: javaVersion,\n"
                + "            isSilverlight: isSilverlight,\n"
                + "            silverlightVersion: silverlightVersion,\n"
                + "            isFlash: isFlash,\n"
                + "            flashVersion: flashVersion,\n"
                + "            language: language\n"
                + "        };\n"
                + "\n"
                + "        return result;\n"
                + "    };\n"
                + "\n"
                + "    var feedback = function (description) {\n"
                + "\n"
                + "        var img = document.querySelector('#qrimage');\n"
                + "        if (img) {\n"
                + "            xtag.removeChild(img);\n"
                + "        }\n"
                + "\n"
                + "        var progress = xtag.querySelector('#progress');\n"
                + "        progress.removeChild(progress.querySelector('#the_progress'));\n"
                + "\n"
                + "        xtag.insertBefore(document.createTextNode(description), progress);\n"
                + "    };\n"
                + "\n"
                + "    var STATE = STATE || {\n"
                + "        IGNORE: 0,\n"
                + "        REQUIRED: 1,\n"
                + "        OPTIONAL: 2\n"
                + "    };\n"
                + "\n"
                + "    var eventTypes = eventTypes || {\n"
                + "        CONNECTION_OPENED: 0,\n"
                + "        AUTH_REQUEST: 1,\n"
                + "        QR_CODE_RECEIVED: 2,\n"
                + "        AUTH_RESULT: 3,\n"
                + "        AUTH_ABORTED: 4,\n"
                + "        GUI_STATUS: 6,\n"
                + "        CHECK_LOGIN: 9,\n"
                + "        NONCE_RECEIVED: 10,\n"
                + "        CHECK_LOGIN_SESSION: 11\n"
                + "    };\n"
                + "\n"
                + "    var reset = function () {\n"
                + "        xtag.removeChild(document.getElementById(\"qrimage\"));\n"
                + "        xtag.removeChild(document.getElementById(\"progress\"));\n"
                + "        wSocket = new WebSocket(WSXMGRURL + \"/login\");\n"
                + "        wSocket.onopen = onopen;\n"
                + "        wSocket.onmessage = onmessage;\n"
                + "        wSocket.onclose = onclose;\n"
                + "    };\n"
                + "\n"
                + "    var buildQRCode = function (xtag, img_url) {\n"
                + "        var resetButton = document.createElement(\"button\");\n"
                + "        resetButton.id = \"resetbutton\";\n"
                + "        resetButton.addEventListener(\"click\", reset, false);\n"
                + "        resetButton.appendChild(document.createTextNode(\"Refresh QRCode\"));\n"
                + "\n"
                + "        var progress_div = document.createElement(\"div\");\n"
                + "        progress_div.id = \"the_progress\";\n"
                + "        progress_div.style.height = \"100%\";\n"
                + "        progress_div.style.width = \"1%\";\n"
                + "        progress_div.style.backgroundColor = \"green\";\n"
                + "        progress_div.style.transition = \"background-color 60s linear , width 60s linear\";\n"
                + "\n"
                + "        var progress_container = document.createElement(\"div\");\n"
                + "        progress_container.id = \"progress\";\n"
                + "        progress_container.style.width = \"196px\";\n"
                + "        progress_container.style.height = \"10px\";\n"
                + "        progress_container.margintop = \"10px\";\n"
                + "        progress_container.appendChild(progress_div);\n"
                + "        progress_container.appendChild(resetButton);\n"
                + "\n"
                + "        var img = document.createElement(\"img\");\n"
                + "        img.id = \"qrimage\";\n"
                + "        img.setAttribute(\"src\", \"data:image/png;base64,\" + img_url);\n"
                + "        img.style.width = \"200px\";\n"
                + "        xtag.appendChild(img);\n"
                + "        xtag.appendChild(progress_container);\n"
                + "    };\n"
                + "\n"
                + "    var sendToDesktop = function (qrContent) {\n"
                + "        var xhttp = new XMLHttpRequest();\n"
                + "        xhttp.open(\"POST\", \"http://127.0.0.1:20546/\", true);\n"
                + "        xhttp.setRequestHeader(\"Content-type\", \"text/html\");\n"
                + "        xhttp.send(qrContent);\n"
                + "    };\n"
                + "\n"
                + "    var isMobileBrowser = function () {\n"
                + "        if (navigator.userAgent.match(/Android/i)\n"
                + "                || navigator.userAgent.match(/webOS/i)\n"
                + "                || navigator.userAgent.match(/iPhone/i)\n"
                + "                || navigator.userAgent.match(/iPad/i)\n"
                + "                || navigator.userAgent.match(/iPod/i)\n"
                + "                || navigator.userAgent.match(/BlackBerry/i)\n"
                + "                || navigator.userAgent.match(/Windows Phone/i)\n"
                + "                ) {\n"
                + "            return true;\n"
                + "        } else {\n"
                + "            return false;\n"
                + "        }\n"
                + "    };\n"
                + "\n"
                + "    var isAndroid = function () {\n"
                + "        return navigator.userAgent.match(/Android/i);\n"
                + "    };\n"
                + "\n"
                + "    var isIOS = function () {\n"
                + "        return navigator.userAgent.match(/iPhone/i)\n"
                + "                || navigator.userAgent.match(/iPad/i)\n"
                + "                || navigator.userAgent.match(/iPod/i);\n"
                + "    };\n"
                + "}\n"
                + "\n"
                + "function random() {\n"
                + "    var text = \"\";\n"
                + "    var possible = \"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789\";\n"
                + "    for (var i = 0; i < 20; i++)\n"
                + "        text += possible.charAt(Math.floor(Math.random() * possible.length));\n"
                + "    return text;\n"
                + "}\n"
                + "";

        Optional<HiddenValueCallback> result = context.getCallback(HiddenValueCallback.class);
        if (result.isPresent()) { // triggered from javascript, attached to hidden field
            String username = null;
            URI redirect = null;
            try { // check if, URL-Syntax valid
                redirect = new URI(result.get().getValue());
            } catch (URISyntaxException ex) {
                debug.error(ex.getMessage());
                throw new NodeProcessException("this is not an redirect uri");
            }

            String code = null;
            // authorization code passed as query parameter, get it from redirect uri
            String[] s = redirect.getQuery().split("&");
            for (String s2 : s) {
                if (s2.contains("code=")) {
                    code = s2.split("=")[1];
                }
            }

            if (code == null) {
                return goTo(false).build();
            }

            // fetch token, decrypt and validate
            InputStream fin = null;
            try {
                fin = new FileInputStream(config.pathToXignConfig());
            } catch (FileNotFoundException ex) {
                debug.error(ex.getMessage());
                throw new NodeProcessException(ex.getMessage());
            }

            try {
                TokenFetcherClient req = new TokenFetcherClient(fin, null, false);
                JWTClaims claims = req.requestIdToken(code);
                username = claims.getNickname();
            } catch (XignTokenException ex) {
                debug.error(ex.getMessage());
                throw new NodeProcessException("error fetching IdToken");
            }

            try {
                fin.close();
            } catch (IOException ex) {
                debug.warning(ex.getMessage());
            }

            String mappingName = config.mapping().get(username);
            debug.message("mapping username '" + username + "' to AM Identity '" + mappingName + "'");

            if (mappingName == null) {
                debug.error("no mapping for username " + username);
                throw new NodeProcessException("no mapping for username " + username);
            }
            
            return makeDecision(mappingName, context);

        } else {
            ScriptTextOutputCallback scriptCallback
                    = new ScriptTextOutputCallback(xignInScript);

            HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("code");
            ImmutableList<Callback> callbacks = ImmutableList.of(scriptCallback, hiddenValueCallback);

            return send(callbacks).build();
        }

    }

    private Action makeDecision(String mappingName, TreeContext context) {
        //check if identity exists with username
        AMIdentity id = null;
        try {
            id = coreWrapper.getIdentity(mappingName, "/");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return goTo(false).build();
        }

        if (id != null) { // exists, login user
            debug.message("logging in user '" + id.getName() + "'");
            JsonValue newSharedState = context.sharedState.copy();
            newSharedState.put("username", mappingName);
            return goTo(true).replaceSharedState(newSharedState).build();
        } else {
            debug.error("user not known");
            return goTo(false).build();
        }
    }
}
