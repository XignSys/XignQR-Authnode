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

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.shared.debug.Debug;
import com.xign.forgerock.common.JWTClaims;
import com.xign.forgerock.common.PropertiesFactory;
import com.xign.forgerock.common.PushFetcherClient;
import com.xign.forgerock.common.Util;
import com.xign.forgerock.common.XignTokenException;
import java.io.IOException;
import javax.inject.Inject;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.CoreWrapper;

@Node.Metadata(outcomeProvider = AbstractDecisionNode.OutcomeProvider.class,
        configClass = XignPush.Config.class)
public class XignPush extends AbstractDecisionNode {

    private final Config config;
    private final static String DEBUG_FILE = "XignPush";
    private Debug debug = Debug.getInstance(DEBUG_FILE);

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
    public XignPush(@Assisted Config config, CoreWrapper coreWrapper) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        JWTClaims claims;

        JsonValue newSharedState = context.sharedState.copy();
        newSharedState.get("pollId");

        try {
            // request push result for pollId and retrieve token
            claims = new PushFetcherClient(PropertiesFactory.getPropertiesAsInputStream(config.pathToXignConfig()), null)
                            .pollForResult(newSharedState.get("pollId").asString());
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
            newSharedState.put("username", id.getName());
            return goTo(true).replaceSharedState(newSharedState).build();
        } else {
            debug.error("user not known");
            return goTo(false).build();
        }
    }
}
