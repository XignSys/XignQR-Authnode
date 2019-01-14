/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock.xignpush.sender;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.shared.debug.Debug;
import com.xign.forgerock.common.PropertiesFactory;
import com.xign.forgerock.common.PushFetcherClient;
import com.xign.forgerock.common.UserInfoSelector;
import com.xign.forgerock.common.XignTokenException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import static org.forgerock.openam.auth.node.api.Action.send;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.CoreWrapper;

/**
 *
 * @author peterchenfrost
 */
@Node.Metadata(outcomeProvider = SingleOutcomeNode.OutcomeProvider.class,
        configClass = XignPushRequest.Config.class)
public class XignPushRequest extends SingleOutcomeNode {

    private final XignPushRequest.Config config;
    private final static String DEBUG_FILE = "XignPushRequest";
    private Debug debug = Debug.getInstance(DEBUG_FILE);

    @Inject
    public XignPushRequest(@Assisted Config config, CoreWrapper coreWrapper) {
        this.config = config;
    }

    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order = 100)
        String pathToXignConfig();
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        if (context.hasCallbacks()) {
            String inputUsername = findCallbackValue(context);

            // select which attributes should delivered in response
            UserInfoSelector selector = new UserInfoSelector();
            selector.setNickname(1);
            selector.setEmail(1);

            // request session id for result-polling
            String pollId = null;
            try {
                pollId = new PushFetcherClient(PropertiesFactory.getPropertiesAsInputStream(config.pathToXignConfig()), null).requestPushWithUsername(inputUsername, selector);
            } catch (IOException ex) {
                debug.error("error loading properties");
                throw new NodeProcessException("error loading properties");
            } catch (XignTokenException ex) {
                debug.error("error requesting push authentication");
                throw new NodeProcessException("error requesting push authentication");
            }

            // save pollId in shared state
            JsonValue newSharedState = context.sharedState.copy();
            newSharedState.put("pollId", pollId);

            // go to next node for polling the result
            return goToNext().replaceSharedState(newSharedState).build();
        } else {
            List<Callback> callbacks = new ArrayList<>(1);
            NameCallback nameCallback = new NameCallback("username");
            callbacks.add(nameCallback);
            return send(ImmutableList.copyOf(callbacks)).build();
        }
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

}
