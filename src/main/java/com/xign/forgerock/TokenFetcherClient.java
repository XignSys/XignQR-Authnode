/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.xign.api.json.JWTClaims;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Base64;

/**
 *
 * @author palle
 */
public class TokenFetcherClient implements ResponseHandler<JsonObject> {

    private static final Logger LOG = Logger.getLogger(TokenFetcherClient.class.getName());

    private KeyStore trustStore = null, keyStore = null;
    private String trustAlias, keyAlias, keyPassword,
            clientId, redirectUri, tokenUrl, masterSecret, statusUrl;
    private X509Certificate httpsTrust = null;
    private CloseableHttpClient httpClient;
    private final JsonParser PARSER = new JsonParser();
    private final Gson GSON = new Gson();

    public TokenFetcherClient(String configPath, X509Certificate httpsTrust, boolean useProxy) throws XignTokenException {

        InputStream pin;

        try {
            pin = new FileInputStream(configPath);
        } catch (FileNotFoundException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error reading config file");
        }

        init(pin, httpsTrust, useProxy);
    }

    public TokenFetcherClient(InputStream config, X509Certificate httpsTrust, boolean useProxy) throws XignTokenException {
        init(config, httpsTrust, useProxy);
    }

    private void init(InputStream config, X509Certificate httpsTrust, boolean useProxy) throws XignTokenException {
        Properties properties = new Properties();
        try {
            properties.load(config);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error loading config file");
        }

        String encodedKeystore = properties.getProperty("client.keystore");
        String password = properties.getProperty("client.keystore.password");
        String kAlias = properties.getProperty("client.keystore.alias");
        redirectUri = properties.getProperty("client.redirect_uri");
        clientId = properties.getProperty("client.id");
        tokenUrl = properties.getProperty("manager.url.token");
        masterSecret = properties.getProperty("syncfuel.master.secret");
        statusUrl = properties.getProperty("manager.url.status");
        String proxy = properties.getProperty("http_proxy");

        ByteArrayInputStream in = new ByteArrayInputStream(Base64.decode(encodedKeystore.getBytes()));

        try {
            this.keyStore = KeyStore.getInstance("pkcs12");
            this.keyPassword = password;
            this.keyAlias = kAlias;
            this.keyStore.load(in, keyPassword.toCharArray());
        } catch (IOException | NoSuchAlgorithmException
                | CertificateException | KeyStoreException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error constructing requester");
        }

        try {
            this.trustStore = KeyStore.getInstance("pkcs12", new BouncyCastleProvider());
            this.trustAlias = "trust";
            this.trustStore.load(null, null);
            String encodedSignatureTrustCert = properties.getProperty("client.trustcert");
            in = new ByteArrayInputStream(Base64.decode(encodedSignatureTrustCert.getBytes()));
            CertificateFactory cf = CertificateFactory.getInstance("X.509", new BouncyCastleProvider());
            this.trustStore.setCertificateEntry(trustAlias, (X509Certificate) cf.generateCertificate(in));

        } catch (IOException | NoSuchAlgorithmException
                | CertificateException | KeyStoreException ex) {
            Logger.getLogger(TokenFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("error constructing requester");
        }

        this.httpsTrust = httpsTrust;
        httpClient = buildClient(useProxy, proxy);

    }

    private CloseableHttpClient buildClient(boolean useProxy, String proxy) throws XignTokenException {
        HttpClientBuilder builder = HttpClients.custom();
        builder.setRedirectStrategy(new LaxRedirectStrategy());
        if (useProxy) {
            builder.useSystemProperties();

            try {
                URL u = new URL(proxy);
                builder.setProxy(new HttpHost(u.getHost(), u.getPort(), u.getProtocol()));
            } catch (MalformedURLException ex) {
                Logger.getLogger(TokenFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
                throw new XignTokenException("this is not an url: " + proxy);
            }

        }
        try {

            SSLContext ctx;
            if (httpsTrust == null) {
                ctx = Util.getDefaultContext();
            } else {
                ctx = Util.getSSLContext(httpsTrust);
            }

            builder.setSSLContext(ctx);
            builder.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
        } catch (NoSuchAlgorithmException | KeyManagementException | IOException | CertificateException | KeyStoreException | NoSuchProviderException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
            throw new XignTokenException("error constructing ssl context");
        }

        return builder.build();
    }

    @Override
    public JsonObject handleResponse(HttpResponse response) throws ClientProtocolException, IOException {

        int statusCode = response.getStatusLine().getStatusCode();
        JsonObject responseObject = null;
        switch (statusCode) {
            case 200:
                byte[] content = IOUtils.toByteArray(response.getEntity().getContent());
                responseObject = PARSER.parse(new String(content, "UTF-8")).getAsJsonObject();
                break;
            default:
                throw new ClientProtocolException("error handling request with status code " + statusCode);
        }

        return responseObject;
    }

    public JWTClaims requestIdToken(String code) throws XignTokenException {

        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("code", code));
        nvps.add(new BasicNameValuePair("client_id", this.clientId));
        nvps.add(new BasicNameValuePair("redirect_uri", this.redirectUri));
        nvps.add(new BasicNameValuePair("grant_type", "authorization_code"));

        UrlEncodedFormEntity entity;
        try {
            entity = new UrlEncodedFormEntity(nvps);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(TokenFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new XignTokenException("error building entity");
        }

        HttpPost p = new HttpPost(tokenUrl);
        p.setEntity(entity);

        JsonObject o;
        try {
            o = httpClient.execute(p, this);
        } catch (IOException ex) {
            Logger.getLogger(TokenFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new XignTokenException("error executing token request");
        }

        return processTokenResponse(o);
    }

    private JWTClaims processTokenResponse(JsonObject tokenResponse) throws XignTokenException {

        if (tokenResponse.has("error")) {
            throw new XignTokenException("received error response :" + tokenResponse.get("error").getAsString());
        }

        ECDHDecrypter decrypter = null;
        try {
            decrypter = new ECDHDecrypter((ECPrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray()));
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | JOSEException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new XignTokenException("error building decrypter");
        }catch(Exception ex){
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
        JWTClaims claims = GSON.fromJson(jClaimsString, JWTClaims.class);
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

    private X509Certificate validateCertificateChain(List<com.nimbusds.jose.util.Base64> chain) throws XignTokenException {
        CertificateFactory cf = null;
        try {
            cf = CertificateFactory.getInstance("X.509");
            X509Certificate current = null;
            X509Certificate next = null;
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

}
