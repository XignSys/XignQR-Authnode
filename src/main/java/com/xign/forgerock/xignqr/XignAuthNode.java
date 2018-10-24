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
package com.xign.forgerock.xignqr;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.shared.debug.Debug;
import com.xign.forgerock.common.JWTClaims;
import com.xign.forgerock.common.Util;
import com.xign.forgerock.common.XignTokenException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.CoreWrapper;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.security.auth.callback.Callback;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import static org.forgerock.openam.auth.node.api.Action.send;

/**
 * A node that checks to see if zero-page login headers have specified username
 * and shared key for this request.
 */
@Node.Metadata(outcomeProvider = AbstractDecisionNode.OutcomeProvider.class,
        configClass = XignAuthNode.Config.class)
public class XignAuthNode extends AbstractDecisionNode {

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

        //TODO Remove filestore config, add as configuration option in node so we don't need to do file I/O for every
        // process call
        //TODO Add property name in XignAuthNode for localization
        @Attribute(order = 100)
        String pathToXignConfig();

        //TODO Add property name in XignAuthNode for localization
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
    public XignAuthNode(@Assisted Config config, CoreWrapper coreWrapper) throws NodeProcessException {
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
        
        debug.message("manager url: "+managerUrl);
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        String xignInScript = null;
        HttpClientBuilder builder = HttpClients.custom();
        builder.setRedirectStrategy(new LaxRedirectStrategy());
        try {
            SSLContext ctx = Util.getDefaultContext();
        } catch (NoSuchAlgorithmException ex) {
            debug.error(ex.getMessage());
            throw new NodeProcessException("error retrieving script");
        }
        CloseableHttpClient client = builder.build();
        HttpGet get = new HttpGet(managerUrl + "/js/v3/xignin-v3-forgerock.js");
        try {
            CloseableHttpResponse response = client.execute(get);
            byte[] scr = IOUtils.toByteArray(response.getEntity().getContent());
            xignInScript = new String(scr, "UTF-8");
            xignInScript = xignInScript.replace("###manager.url###", managerUrl)
                    .replace("###redirectUri###", redirectUri)
                    .replace("###clientId###", clientId);
            debug.message(xignInScript);
        } catch (IOException ex) {
            debug.error(ex.getMessage());
        }

        //TODO Pull out into a Constant in utility file
        Optional<HiddenValueCallback> result = context.getCallback(HiddenValueCallback.class);
        if (result.isPresent()) { // triggered from javascript, attached to hidden field
            String username;
            URI redirect;
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
            InputStream fin;
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
        AMIdentity id;
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
