/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock.xignpush.request;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.shared.debug.Debug;
import com.xign.forgerock.common.PropertiesFactory;
import com.xign.forgerock.common.PushFetcherClient;
import com.xign.forgerock.common.UserInfoSelector;
import static com.xign.forgerock.common.Util.XIGN_POLL_ID;
import com.xign.forgerock.common.XignTokenException;
import java.io.IOException;
import javax.inject.Inject;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.SharedStateConstants;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;

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
    public XignPushRequest(@Assisted Config config) {
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
        JsonValue newSharedState = context.sharedState.copy();
        String inputUsername = newSharedState.get(SharedStateConstants.USERNAME).asString();

        if (null == inputUsername || inputUsername.isEmpty()) {
            throw new NodeProcessException("Username must be collected prior to this node being executed");
        }

        // select which attributes should delivered in response
        UserInfoSelector selector = new UserInfoSelector();
        selector.setNickname(1);
        selector.setEmail(1);

        // request session id for result-polling
        String pollId;
        try {
            pollId = new PushFetcherClient(PropertiesFactory.getXignProperties(config.pathToXignConfig()),
                    null).requestPushWithUsername(inputUsername, selector);
        } catch (IOException ex) {
            debug.error("Error Loading Properties");
            throw new NodeProcessException("Error Loading Properties", ex);
        } catch (XignTokenException ex) {
            debug.error("Error Requesting Push Authentication");
            throw new NodeProcessException("Error Requesting Push Authentication", ex);
        }

        // save pollId in shared state
        newSharedState.put(XIGN_POLL_ID, pollId);

        // go to next node for polling the result
        return goToNext().replaceSharedState(newSharedState).build();
    }
}
