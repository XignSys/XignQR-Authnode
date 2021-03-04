/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xignsys.forgerock.common;

import com.iplanet.dpro.session.SessionCategory;
import com.iplanet.dpro.session.SessionID;
import com.iplanet.dpro.session.SessionIDConstructorHelper;
import com.iplanet.dpro.session.SessionIDFactory;
import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.iplanet.sso.SSOTokenManager;
import com.sun.identity.authentication.internal.InvalidAuthContextException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import com.sun.identity.idm.IdUtils;
import com.xignsys.xignin.client.XignInProperties;
import com.xignsys.xignin.client.XignInPropertiesException;
import org.apache.commons.io.IOUtils;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;

/**
 * @author palle
 */
public class Util {
    private static final Logger LOG = LoggerFactory.getLogger(Util.class);
    private static String loginJsTemplate;

    public static AMIdentity getIdentity(
            String mappingName, String claim,
            TreeContext context
    ) {
        Set<String> userSearchAttributes = new HashSet<>();
        userSearchAttributes.add(mappingName);
        String realm = context.sharedState.get(REALM).asString();
        LOG.debug("searching for user {} with mapping {} in realm {}", claim, mappingName, realm);
        return IdUtils.getIdentity(claim, realm, userSearchAttributes);
    }

    public static void createUser(String claim, TreeContext context)
            throws InvalidAuthContextException, IdRepoException, SSOException {



        SSOToken ssoToken = SSOTokenManager.getInstance().createSSOToken(context.request.servletRequest);
        AMIdentity newIdentity = new AMIdentity(ssoToken);
        HashMap<String, String> attributes = new HashMap<>();
        attributes.put("username", claim);

        newIdentity.setActiveStatus(true);
        newIdentity.setAttributes(attributes);
        newIdentity.store();
    }

    public static String loadTemplateFile(String managerBaseUrl, String clientId, String redirectUri)
            throws IOException {

        if (loginJsTemplate == null) {
            InputStream in = Util.class.getResourceAsStream("/template/login.js");

            if (in == null) {
                throw new IOException("could not load js template");
            }

            loginJsTemplate = IOUtils.toString(in);

        }

        return loginJsTemplate.replace("${managerUri}", managerBaseUrl)
                              .replace("${clientId}", clientId)
                              .replace("${redirectUri}", redirectUri);
    }

    public static XignInProperties loadProperties(String jsonConfig) throws XignInPropertiesException {
        return XignInProperties.buildWithJson(jsonConfig);
    }
}
