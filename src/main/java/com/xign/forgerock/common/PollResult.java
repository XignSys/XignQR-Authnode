/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock.common;

/**
 *
 * @author peterchenfrost
 */
public class PollResult {

    private String sessionState;
    private String authenticationState;
    private JWTClaims claims;

    public PollResult(String sessionState, String authenticationState, JWTClaims claims) {
        this.sessionState = sessionState;
        this.authenticationState = authenticationState;
        this.claims = claims;
    }

    public static final String SESSION_STATE_FINISHED = "finished";
    public static final String SESSION_STATE_NOT_FINISHED = "not-finished";
    public static final String AUTHENTICATION_SUCCESS = "authentication-success";
    public static final String AUTHENTICATION_FAILURE = "authentication-failure";

    public String getSessionState() {
        return sessionState;
    }

    public void setSessionState(String sessionState) {
        this.sessionState = sessionState;
    }

    public String getAuthenticationState() {
        return authenticationState;
    }

    public void setAuthenticationState(String authenticationState) {
        this.authenticationState = authenticationState;
    }

    public JWTClaims getClaims() {
        return claims;
    }

    public void setClaims(JWTClaims claims) {
        this.claims = claims;
    }
    
    

}
