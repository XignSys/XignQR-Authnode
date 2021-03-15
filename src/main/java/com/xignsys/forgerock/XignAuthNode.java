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
package com.xignsys.forgerock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.assistedinject.Assisted;
import com.nimbusds.jwt.JWTClaimsSet;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.sun.identity.idm.AMIdentity;
import com.xignsys.forgerock.common.Constants;
import com.xignsys.forgerock.common.ForgerockMappingEnum;
import com.xignsys.forgerock.common.MobileAppData;
import com.xignsys.forgerock.common.Util;
import com.xignsys.forgerock.common.XignInMappingEnum;
import com.xignsys.xignin.client.XignInClient;
import com.xignsys.xignin.client.XignInClientException;
import com.xignsys.xignin.client.XignInProperties;
import com.xignsys.xignin.client.XignInPropertiesException;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.TextOutputCallback;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.forgerock.openam.auth.node.api.Action.goTo;
import static org.forgerock.openam.auth.node.api.Action.send;

@Node.Metadata(outcomeProvider = XignInOutcomeProvider.class,
        configClass = XignAuthNode.Config.class)
public class XignAuthNode implements Node {

    private final Config config;
    private final Logger LOG = LoggerFactory.getLogger(XignAuthNode.class);
    private final XignInProperties properties;

    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order = 100)
        String jsonConfig();

        @Attribute(order = 200)
        default XignInMappingEnum mapping() {
            return XignInMappingEnum.PREFERRED_USERNAME;
        }

        @Attribute(order = 300)
        default ForgerockMappingEnum forgerockMapping() {
            return ForgerockMappingEnum.USERNAME;
        }

        @Attribute(order = 400)
        default boolean createUserIfNotExists() {
            return false;
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
        try {
            this.properties = Util.loadProperties(config.jsonConfig());
        } catch (XignInPropertiesException e) {
            throw new NodeProcessException(e);
        }
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        Map<String, Collection<String>> headers = context.request.headers.asMap();
        boolean isInAppHeaderPresent = headers.containsKey("x-request-source");
        String inAppHeader = null;
        if (isInAppHeaderPresent) {
            LOG.debug("x-request-source is present in request");
            inAppHeader = headers.get("x-request-source").stream().findFirst().get();
        }

        String xignInScript = null;
        URI url = properties.getTokenUri();
        String managerBaseUrl =
                url.getScheme() + "://" + url.getHost() + (url.getPort() == -1 ? "" : ":" + url.getPort());
        try {

            xignInScript = Util.loadTemplateFile(
                    managerBaseUrl,
                    properties.getClientId(),
                    properties.getRedirectUri().toString()
            );
        } catch (IOException ex) {
            throw new NodeProcessException("error loading script from xign manager");
        }

        Optional<HiddenValueCallback> result = context.getCallback(HiddenValueCallback.class);
        if (result.isPresent()) { // triggered from javascript, attached to hidden field
            String code = result.get().getValue();
            // authorization code passed as query parameter, get it from redirect uri
            LOG.debug("received submit with code: {}", code);
            if (code == null) {
                return goTo(XignInOutcomeProvider.XignInOutcomes.FAILURE.name()).build();
            }

            // fetch token, decrypt and validate
            LOG.debug("retrieving token response");
            JWTClaimsSet claims;

            try {
                XignInClient tfc = new XignInClient(properties);
                claims = tfc.requestIdToken(code);
            } catch (XignInClientException ex) {
                LOG.error("error retrieving token response", ex);
                throw new NodeProcessException("error fetching IdToken");
            }

            LOG.debug("token response retrieved ... mapping identity");
            String mappingName = config.mapping().name();
            String claim = (String) claims.getClaim(mappingName.toLowerCase());

            //  search in Forgerock database for user with mapping attribute set to claim value
            AMIdentity id = Util.getIdentity(config.forgerockMapping().name(), claim, context);

            if (id == null && config.createUserIfNotExists()) { // Provision Dynamic Account node must be present
                HashMap<String, ArrayList<String>> attr = new HashMap<>();

                // if email is set in claims, set email for the user that will be created
                if (claims.getClaims().containsKey(XignInMappingEnum.EMAIL.name().toLowerCase(Locale.ROOT))) {
                    attr.put("mail",
                             addAttribute((String) claims.getClaims()
                                                         .get(XignInMappingEnum.EMAIL.name().toLowerCase(Locale.ROOT)))
                    );
                }

                if(claims.getClaims().containsKey("given_name")){
                    attr.put("givenName",
                             addAttribute((String) claims.getClaims()
                                                         .get("given_name"))
                    );
                }

                if(claims.getClaims().containsKey("family_name")){
                    attr.put("sn",
                             addAttribute((String) claims.getClaims()
                                                         .get("family_name"))
                    );
                }

                if(claims.getClaims().containsKey("phone_number")){
                    attr.put("telephoneNumber",
                             addAttribute((String) claims.getClaims()
                                                         .get("phone_number"))
                    );
                }

                attr.put("uid", addAttribute(claim));

                HashMap<String, ArrayList<String>> userNamesparameters = new HashMap<>();
                userNamesparameters.put("username", addAttribute(claim));

                JsonValue newSharedState = context.sharedState.copy();
                newSharedState.put("userInfo", JsonValue.json(
                        JsonValue.object(
                                JsonValue.field("attributes", attr),
                                JsonValue.field("userNames", userNamesparameters)
                        )
                ));

                return goTo(XignInOutcomeProvider.XignInOutcomes.NO_ACCOUNT.name()).replaceSharedState(newSharedState)
                                                                                   .build();
            }

            LOG.debug("making authentication decision ...");
            return makeDecision(id, context);

        } else {

            // attach login.js script to callback to show the qrcode on login page
            ScriptTextOutputCallback scriptCallback
                    = new ScriptTextOutputCallback(xignInScript);

            // to be able to retrieve the code from hidden value field,
            // when login page is submitted after response from xign in is received
            HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("code");
            List<Callback> callbacks = Arrays.asList(scriptCallback, hiddenValueCallback);

            // when in app header is present in request, provide app with necessary information
            if (Constants.REQUEST_SOURCE_APP.equals(inAppHeader)) {
                ObjectMapper objectMapper = new ObjectMapper();
                MobileAppData mobileAppData = new MobileAppData(
                        managerBaseUrl,
                        properties.getClientId(),
                        properties.getRedirectUri().toString(),
                        headers.get("referer").stream().findFirst().get()
                );
                TextOutputCallback textOutputCallback = null;
                try {
                    textOutputCallback = new TextOutputCallback(
                            TextOutputCallback.INFORMATION,
                            objectMapper.writeValueAsString(mobileAppData)
                    );
                } catch (JsonProcessingException e) {
                    throw new NodeProcessException(e);
                }
                callbacks.add(textOutputCallback);
            }

            return send(callbacks).build();
        }

    }

    private Action makeDecision(AMIdentity id, TreeContext context) {
        if (id != null) { // exists, login user
            LOG.debug("authenticating user {}", id.getName());
            // necessary if dynamic provisioning node not present
            JsonValue newSharedState = context.sharedState.copy();
            newSharedState.put("username", id.getName());
            return goTo(XignInOutcomeProvider.XignInOutcomes.SUCCESS.name()).replaceSharedState(newSharedState).build();
        } else {
            LOG.debug("could not find user ... ");
            return goTo(XignInOutcomeProvider.XignInOutcomes.FAILURE.name()).build();
        }
    }

    private ArrayList<String> addAttribute(String attribute) {
        ArrayList<String> arr = new ArrayList<>();
        arr.add(attribute);
        return arr;
    }
}
