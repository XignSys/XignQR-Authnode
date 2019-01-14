/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import org.forgerock.openam.auth.node.api.TreeContext;

/**
 *
 * @author palle
 */
public class Util {

    private static final Logger LOG = Logger.getLogger(Util.class.getName());

    private static final Gson GSON = new Gson();

    public static SSLContext getSSLContext(X509Certificate cert) throws NoSuchAlgorithmException,
            KeyManagementException, IOException, CertificateException, KeyStoreException {
        KeyStore trustStore = KeyStore.getInstance("pkcs12", new BouncyCastleProvider());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("1", cert);

        SSLContext ctx = SSLContext.getInstance("TLS");

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        ctx.init(null, tmf.getTrustManagers(), null);

        return ctx;
    }

    public static SSLContext getDefaultContext() throws NoSuchAlgorithmException {
        return SSLContext.getDefault();
    }

    public static JWTClaims processTokenResponse(JsonObject tokenResponse, KeyStore keyStore,
            String keyAlias, String keyPassword, KeyStore trustStore, String trustAlias) throws XignTokenException {

        if (tokenResponse.has("error")) {
            throw new XignTokenException("received error response :" + tokenResponse.get("error").getAsString());
        }

        ECDHDecrypter decrypter;
        try {
            decrypter = new ECDHDecrypter((ECPrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray()));
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error building decrypter");
        }

        JWEObject reqObject;
        try {
            reqObject = JWEObject.parse(tokenResponse.get("id_token").getAsString());
        } catch (ParseException ex) {
            System.err.println(ex.getMessage());
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error parsing idtoken jwe");
        }

        byte[] dec;
        try {
            dec = decrypter.decrypt(reqObject.getHeader(), null, reqObject.getIV(), reqObject.getCipherText(), reqObject.getAuthTag());
        } catch (JOSEException ex) {
            System.err.println(ex.getMessage());
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error decrypting idtoken");
        }

        JWSObject reqJws;
        try {
            String decrypted = new String(dec);
            reqJws = JWSObject.parse(decrypted);
        } catch (ParseException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error parsing idtoken jws");
        }

        ECDSAVerifier verifier;
        try {
            verifier = new ECDSAVerifier((ECPublicKey) trustStore.getCertificate(trustAlias).getPublicKey());
        } catch (KeyStoreException | JOSEException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error building verifier");
        }

        boolean verified;
        try {
            verified = verifier.verify(reqJws.getHeader(), reqJws.getSigningInput(), reqJws.getSignature());
        } catch (JOSEException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error verifying idtoken jws");
        }

        if (!verified) {
            throw new XignTokenException("jws is not verified!");
        }

        String jClaimsString = reqJws.getPayload().toString();
        JWTClaims claims;
        try {
            claims = GSON.fromJson(jClaimsString, JWTClaims.class);
        } catch (Exception ex) {
            throw new XignTokenException(ex.getMessage());
        }
        try {

            if (claims.getTransaction() != null) {
                JWSObject transactionJws = JWSObject.parse(claims.getTransaction());
                List<com.nimbusds.jose.util.Base64> chain = transactionJws.getHeader().getX509CertChain();
                X509Certificate c = validateCertificateChain(chain);
                verifier = new ECDSAVerifier((ECPublicKey) c.getPublicKey());
                boolean transactionVerified = verifier.verify(transactionJws.getHeader(), transactionJws.getSigningInput(), transactionJws.getSignature());
                if (!transactionVerified) {
                    throw new XignTokenException("transaction could not be verified");
                }
            }
        } catch (ParseException | JOSEException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error parsing transaction jws");
        }

        return claims;
    }

    private static X509Certificate validateCertificateChain(List<com.nimbusds.jose.util.Base64> chain) throws XignTokenException {
        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
            X509Certificate current;
            X509Certificate next;
            for (int i = 0; i < chain.size(); i++) {
                if ((i + 1) == (chain.size() - 1)) {
                    break;
                }
                current = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(chain.get(i).decode()));
                next = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(chain.get(i + 1).decode()));
                current.verify(next.getPublicKey());
            }

            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(chain.get(0).decode()));
        } catch (NoSuchAlgorithmException | InvalidKeyException
                | NoSuchProviderException | SignatureException | CertificateException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("certificate chain is invalid");
        }

    }

    private static String getMappedValue(String mappingName, JWTClaims claims) {
        switch (mappingName) {
            case "username":
                return claims.getNickname();
            case "mail":
                return claims.getEmail();
            case "givenName":
                return claims.getGiven_name();
            case "sn":
                return claims.getFamily_name();
            default:
                return null;
        }
    }

    public static AMIdentity getIdentity(String mappingName, JWTClaims claims,
            TreeContext context) throws NodeProcessException {
        String mappedValue = getMappedValue(mappingName, claims);
        
        if(mappedValue == null){
            throw new NodeProcessException("mapping with name "+mappingName+" is not supported");
        }
        
        Set<String> userSearchAttributes = new HashSet<>();
        userSearchAttributes.add(mappingName);
        return IdUtils.getIdentity(mappedValue, context.sharedState.get(REALM).asString(), userSearchAttributes);
    }
}
