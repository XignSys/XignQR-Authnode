package com.xignsys.forgerock;

import com.google.common.collect.ImmutableList;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.util.i18n.PreferredLocales;

import java.util.List;

/**
 * @author Pascal Manaras <manaras at xignsys.com>
 */
public class XignInOutcomeProvider implements OutcomeProvider {
    @Override
    public List<Outcome> getOutcomes(
            PreferredLocales preferredLocales, JsonValue jsonValue
    ) throws NodeProcessException {
        return ImmutableList.of(
                new Outcome(XignInOutcomes.SUCCESS.name(), "success"),
                new Outcome(XignInOutcomes.FAILURE.name(), "fail"),
                new Outcome(XignInOutcomes.NO_ACCOUNT.name(), "no account")
        );
    }


    public static enum XignInOutcomes {
        SUCCESS,
        FAILURE,
        NO_ACCOUNT;
    }
}
