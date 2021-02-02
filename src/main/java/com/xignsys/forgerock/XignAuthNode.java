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

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.nimbusds.jwt.JWTClaimsSet;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.sun.identity.idm.AMIdentity;
import com.xignsys.forgerock.common.ForgerockMappingEnum;
import com.xignsys.forgerock.common.Util;
import com.xignsys.forgerock.common.XignInMappingEnum;
import com.xignsys.xignin.exception.XignInException;
import com.xignsys.xignin.exception.XignTokenException;
import com.xignsys.xignin.service.TokenFetcherClient;
import com.xignsys.xignin.service.TokenFetcherClientBuilder;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import java.io.IOException;
import java.util.Optional;

import static org.forgerock.openam.auth.node.api.Action.send;

@Node.Metadata(outcomeProvider = AbstractDecisionNode.OutcomeProvider.class,
        configClass = XignAuthNode.Config.class)
public class XignAuthNode extends AbstractDecisionNode {

    private final Config config;
    private final Logger LOG = LoggerFactory.getLogger(XignAuthNode.class);
    private final String redirectUri, clientId, managerBaseUrl;

    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order = 100)
        String baseUrl();

        @Attribute(order = 300)
        String redirectUri();

        @Attribute(order = 200)
        String clientId();

        @Attribute(order = 400)
        default XignInMappingEnum mapping() {
            return XignInMappingEnum.PREFERRED_USERNAME;
        }

        @Attribute(order = 500)
        default ForgerockMappingEnum forgerockMapping() {
            return ForgerockMappingEnum.USERNAME;
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
        this.clientId = config.clientId();
        this.redirectUri = config.redirectUri();
        this.managerBaseUrl = config.baseUrl();
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        String xignInScript = null;
        try {
            xignInScript = Util.loadTemplateFile(managerBaseUrl, clientId, redirectUri);
        } catch (IOException ex) {
            throw new NodeProcessException("error loading script from xign manager");
        }

        Optional<HiddenValueCallback> result = context.getCallback(HiddenValueCallback.class);
        if (result.isPresent()) { // triggered from javascript, attached to hidden field
            String code = result.get().getValue();
            // authorization code passed as query parameter, get it from redirect uri
            LOG.debug("received submit with code: {}", code);
            if (code == null) {
                return goTo(false).build();
            }

            // fetch token, decrypt and validate
            LOG.info("retrieving token response");
            JWTClaimsSet claims;
            try {
                // minimal required settings => no encryption, since no clientKeys given
                TokenFetcherClient tfc = new TokenFetcherClientBuilder()
                        .metaDataUrl(managerBaseUrl + "/openid/.well-known/openid-configuration")
                        .clientId(clientId)
                        .redirectUri(redirectUri)
                        .secret("supersecret").build();

                claims = tfc.requestIdToken(code);
            } catch (XignInException | XignTokenException ex) {
                LOG.error("error retrieving token response", ex);
                throw new NodeProcessException("error fetching IdToken");
                // send callbacks?
            }

            LOG.info("token response retrieved ... mapping identity");
            String mappingName = config.mapping().name();
            String claim = (String) claims.getClaim(mappingName.toLowerCase());
            AMIdentity id = Util.getIdentity(config.forgerockMapping().name(), claim, context);

            LOG.info("making authentication decision ...");
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
            JsonValue newSharedState = context.sharedState.copy();
            newSharedState.put("username", id.getName());
            return goTo(true).replaceSharedState(newSharedState).build();
        } else {
            LOG.debug("could not find user ... ");
            return goTo(false).build();
        }
    }
}
