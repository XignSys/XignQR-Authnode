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
package com.xign.forgerock.xignpush.result;

import static com.xign.forgerock.common.Util.XIGN_POLL_ID;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.shared.debug.Debug;
import com.xign.forgerock.common.JWTClaims;
import com.xign.forgerock.common.PropertiesFactory;
import com.xign.forgerock.common.PushFetcherClient;
import com.xign.forgerock.common.Util;
import com.xign.forgerock.common.XignTokenException;
import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;
import javax.inject.Inject;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.SharedStateConstants;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;

@Node.Metadata(outcomeProvider = XignPushResult.XignPushResultOutcomeProvider.class,
        configClass = XignPushResult.Config.class)
public class XignPushResult extends AbstractDecisionNode {

    private final Config config;
    private final static String DEBUG_FILE = "XignPushResult";
    private Debug debug = Debug.getInstance(DEBUG_FILE);
    private static final String BUNDLE = XignPushResult.class.getName();


    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order = 100)
        String pathToXignConfig();

        @Attribute(order = 200)
        String mapping();
    }

    @Inject
    public XignPushResult(@Assisted Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        JWTClaims claims;

        JsonValue newSharedState = context.sharedState.copy();
        newSharedState.get(XIGN_POLL_ID);

        try {
            //TODO The config should be cached and reloaded from memory after the initial node reads it so that so
            // much file I/O is occurring
            // request push result for pollId and retrieve token
            claims = new PushFetcherClient(PropertiesFactory.getPropertiesAsInputStream(config.pathToXignConfig()), null)
                            .pollForResult(newSharedState.get(XIGN_POLL_ID).asString());
        } catch (XignTokenException | IOException ex) {
            debug.error(ex.getMessage());
            throw new NodeProcessException(ex.getMessage());
        }

        // get mapping of name = xign-id -> openam-id
        String mappingName = config.mapping();

        AMIdentity id = Util.getIdentity(mappingName, claims, context);

        return makeDecision(id, context);
    }

    private Action makeDecision(AMIdentity id, TreeContext context) {
        //check if identity exists with username
        if (id != null) { // exists, login user
            debug.message("logging in user '" + id.getName() + "'");
            JsonValue newSharedState = context.sharedState.copy();
            newSharedState.put(SharedStateConstants.USERNAME, id.getName());
            return Action.goTo(XignPushResultOutcome.TRUE.name()).replaceSharedState(newSharedState).build();
        } else {
            debug.error("user not known");
            return Action.goTo(XignPushResultOutcome.FALSE.name()).build();
        }
    }

    /**
     * Defines the possible outcomes from this XignPushResult node.
     */
    public static class XignPushResultOutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(XignPushResult.BUNDLE,
                    XignPushResult.class.getClassLoader());
            return ImmutableList.of(
                    new Outcome(XignPushResultOutcome.TRUE.name(), bundle.getString("trueOutcome")),
                    new Outcome(XignPushResultOutcome.FALSE.name(), bundle.getString("falseOutcome")),
                    new Outcome(XignPushResultOutcome.UNANSWERED.name(), bundle.getString("unansweredOutcome"))
            );
        }
    }

    /**
     * The possible outcomes for the LdapDecisionNode.
     */
    public enum XignPushResultOutcome {
        /**
         * Successful authentication.
         */
        TRUE,
        /**
         * Authentication failed.
         */
        FALSE,
        /**
         * The end user has not responded yet.
         */
        UNANSWERED

    }
}
