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
package com.xign.forgerock.xignpush;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.shared.debug.Debug;
import com.xign.forgerock.common.JWTClaims;
import com.xign.forgerock.common.PropertiesFactory;
import com.xign.forgerock.common.UserInfoSelector;
import com.xign.forgerock.common.Util;
import com.xign.forgerock.common.XignTokenException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.CoreWrapper;
import javax.inject.Inject;
import javax.security.auth.callback.NameCallback;
import org.forgerock.json.JsonValue;
import static org.forgerock.openam.auth.node.api.Action.send;
import javax.security.auth.callback.Callback;

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

    private String findCallbackValue(TreeContext context) {
        for (Callback callback : context.getAllCallbacks()) {
            NameCallback ncb = (NameCallback) callback;
            if ("username".equals(ncb.getPrompt())) {
                return ncb.getName();
            }
        }
        return "";
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        if (context.hasCallbacks()) {
            String inputUsername = findCallbackValue(context);
            JWTClaims claims;

            // select which attributes should delivered in response
            UserInfoSelector selector = new UserInfoSelector();
            selector.setNickname(1);
            selector.setEmail(1);
            //TODO Needs to be split up into two nodes, one for initial request with an identifier returned. This
            // identifier should be stored in shared state. THen in the next node, we grab that identifier and check
            // the status of the push request
            try {
                // request push login for username and retrieve token
                 claims =
                         new PushFetcherClient(PropertiesFactory.getPropertiesAsInputStream(config.pathToXignConfig()), null).requestPushWithUsername(inputUsername, selector);
            } catch (XignTokenException | IOException ex) {
                debug.error(ex.getMessage());
                throw new NodeProcessException(ex.getMessage());
            }

            // get mapping of name = xign-id -> openam-id
            String mappingName = config.mapping();

            AMIdentity id = Util.getIdentity(mappingName, claims, context);

            return makeDecision(id, context);

        } else {
            List<Callback> callbacks = new ArrayList<>(1);
            NameCallback nameCallback = new NameCallback("username");
            callbacks.add(nameCallback);
            return send(ImmutableList.copyOf(callbacks)).build();

        }
    }

    private Action makeDecision(AMIdentity id, TreeContext context) {
        //TODO Duplicated code with XignAuthNode
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
