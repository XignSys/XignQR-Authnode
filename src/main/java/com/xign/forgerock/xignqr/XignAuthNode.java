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
import com.xign.forgerock.common.MappingEnum;
import com.xign.forgerock.common.PropertiesFactory;
import com.xign.forgerock.common.TokenFetcherClient;
import com.xign.forgerock.common.Util;
import com.xign.forgerock.common.XignTokenException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Properties;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import static org.forgerock.openam.auth.node.api.Action.send;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;

@Node.Metadata(outcomeProvider = AbstractDecisionNode.OutcomeProvider.class,
        configClass = XignAuthNode.Config.class)
public class XignAuthNode extends AbstractDecisionNode {

    private final Config config;
    private final static String DEBUG_FILE = "XignQR";
    private Debug debug = Debug.getInstance(DEBUG_FILE);
    private final String redirectUri, clientId, managerUrl;

    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order = 100)
        String pathToXignConfig();

        @Attribute(order = 200)
        default MappingEnum mapping() {
            return MappingEnum.USERNAME;
        }

    }

    /**
     * Create the node.
     *
     * @param config The service config.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public XignAuthNode(@Assisted Config config) throws NodeProcessException {
        this.config = config;

        // read properties file
        Properties properties;
        try {
            properties = PropertiesFactory.getProperties(config.pathToXignConfig());
        } catch (IOException ex) {
            debug.error("error loading properties file", ex);
            throw new NodeProcessException("error loading properties file");
        }

        // openid connect redirect uri
        redirectUri = properties.getProperty("client.redirect_uri");

        // clientId of registered openam instance
        clientId = properties.getProperty("client.id");

        // where to connect for retrieval of qrcode in JS
        managerUrl = properties.getProperty("manager.url.token").replace("/token", "");

    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        String xignInScript;
        try {
            xignInScript = PropertiesFactory.getScript(managerUrl, redirectUri, clientId);
        } catch (IOException ex) {
            debug.error("error loading script from xign manager", ex);
            throw new NodeProcessException("error loading script from xign manager");
        }

        Optional<HiddenValueCallback> result = context.getCallback(HiddenValueCallback.class);
        if (result.isPresent()) { // triggered from javascript, attached to hidden field
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
            JWTClaims claims;
            try {
                TokenFetcherClient req
                        = new TokenFetcherClient(PropertiesFactory.getPropertiesAsInputStream(config.pathToXignConfig()), null, false);
                claims = req.requestIdToken(code);
            } catch (XignTokenException | IOException ex) {
                debug.error(ex.getMessage());
                throw new NodeProcessException("error fetching IdToken");
            }

            String mappingName = config.mapping().name();

            AMIdentity id = Util.getIdentity(mappingName, claims, context);

            return makeDecision(id, context);

        } else {
            ScriptTextOutputCallback scriptCallback
                    = new ScriptTextOutputCallback(xignInScript);

            HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("code");
            ImmutableList<Callback> callbacks = ImmutableList.of(scriptCallback, hiddenValueCallback);

            return send(callbacks).build();
        }

    }

    private Action makeDecision(AMIdentity id, TreeContext context) {
        if (id != null) { // exists, login user
            debug.message("logging in user '" + id.getName() + "'");
            JsonValue newSharedState = context.sharedState.copy();
            newSharedState.put("username", id.getName());
            return goTo(true).replaceSharedState(newSharedState).build();
        } else {
            debug.error("user not known");
            return goTo(false).build();
        }
    }
}
