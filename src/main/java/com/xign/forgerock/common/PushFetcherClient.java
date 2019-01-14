/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.LoggerFactory;

/**
 *
 * @author palle
 */
public class PushFetcherClient {

    private final String clientId, keyPassword, keyAlias, trustAlias;
    private final URL endpoint;
    private final KeyStore clientKeys, trustStore;
    private final X509Certificate trustCert;
    private final boolean isSSL;
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(PushFetcherClient.class.getName());

    private final JsonParser PARSER = new JsonParser();

    public PushFetcherClient(InputStream pin, X509Certificate httpsTrust) throws XignTokenException {
        
        // read and load properties configured in node settings
        Properties properties = new Properties();
        try {
            properties.load(pin);
        } catch (IOException ex) {
            Logger.getLogger(PushFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new XignTokenException("error loading properties");
        }

        String encodedKeystore = properties.getProperty("client.keystore");
        String password = properties.getProperty("client.keystore.password");
        String kAlias = properties.getProperty("client.keystore.alias");
        String pushEndpoint = properties.getProperty("manager.url.token").replace("/token", "/push");
        clientId = properties.getProperty("client.id");
        ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(encodedKeystore.getBytes()));

        try {
            this.clientKeys = KeyStore.getInstance("pkcs12");
        } catch (KeyStoreException ex) {
            Logger.getLogger(PushFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new XignTokenException("error loading client keystore");
        }

        this.keyPassword = password;
        try {
            this.keyAlias = kAlias;
            this.clientKeys.load(in, password.toCharArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException ex) {
            Logger.getLogger(PushFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new XignTokenException("error constructing requester");
        }

        try {
            this.trustStore = KeyStore.getInstance("pkcs12", new BouncyCastleProvider());
        } catch (KeyStoreException ex) {
            Logger.getLogger(PushFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new XignTokenException("error loading trust keystore");
        }

        this.trustAlias = "trust";
        try {
            this.trustStore.load(null, null);
            String encodedSignatureTrustCert = properties.getProperty("client.trustcert");
            in = new ByteArrayInputStream(Base64.getDecoder().decode(encodedSignatureTrustCert.getBytes()));
            CertificateFactory cf = CertificateFactory.getInstance("X.509", new BouncyCastleProvider());
            this.trustStore.setCertificateEntry(trustAlias, cf.generateCertificate(in));

        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException ex) {
            Logger.getLogger(PushFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new XignTokenException("error constructing requester");
        }

        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);

        this.trustCert = httpsTrust;

        try {
            this.endpoint = new URL(pushEndpoint);
        } catch (MalformedURLException ex) {
            Logger.getLogger(PushFetcherClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new XignTokenException("pushEndpoint " + pushEndpoint + " is not an url");
        }

        this.isSSL = pushEndpoint.contains("https://");

    }

    /**
     * Requests the authentication, triggered by Push message to registered device
     *
     * @param userid The username collected by XignPushRequestPlugin
     * @param uiselector The attributes, that are requested from XignQR System
     * @return pollId for polling the state of authentication in XignPushPlugin
     * @throws XignTokenException
     */
    public String requestPushWithUsername(String userid, UserInfoSelector uiselector) throws XignTokenException {
        JsonObject resultObject;

        try {
            PrivateKey pkey = (PrivateKey) clientKeys.getKey("xyz", "changeit".toCharArray());

            JsonObject payload = new JsonObject();
            payload.addProperty("userid", userid);
            payload.addProperty("nonce", RandomStringUtils.randomAlphanumeric(16));
            payload.addProperty("version", "2.0");
            if (uiselector != null) {
                payload.add("uiselector", new Gson().toJsonTree(uiselector));
            }

            byte[] signed = Base64.getEncoder().encode(Crypto.sign(payload.toString().getBytes("ISO8859-1"), pkey));
            String signature = new String(signed, "ISO8859-1");

            JsonObject o = new JsonObject();
            o.addProperty("type", 43);
            o.addProperty("client_id", this.clientId);
            o.add("payload", payload);
            o.addProperty("signature", signature);

            String result = sendMessage(o, endpoint.toString());
            assert result != null;
            resultObject = PARSER.parse(result).getAsJsonObject();

        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException
                | IOException | CertificateException | InvalidKeyException
                | InvalidKeySpecException | SignatureException
                | KeyManagementException ex) {
            LOG.error(null, ex);
            throw new XignTokenException("error requesting push authentication");
        }

        return resultObject.get("pollId").getAsString();
    }

    /**
     * Polls state of authentcation using pollId retrieved via {@link #requestPushWithUsername(java.lang.String, com.xign.forgerock.common.UserInfoSelector)}
     *
     * @param pollId
     * @return decrypted and verified JWTClaims returned by XignQR System
     */
    public JWTClaims pollForResult(String pollId) {

        int RETRIES_MAX = 20;
        int RETRIES_COUNT = 0;
        boolean resultReceived = false;
        JsonObject result = null;
        JWTClaims claims = null;

        // poll for result
        JsonObject pollRequest = new JsonObject();
        pollRequest.addProperty("pollId", pollId);
        while (!resultReceived) {
            try {

                String response = sendMessage(pollRequest, endpoint.toString().replace("/push", "/result"));
                LOG.debug("received response from server: " + response);
                result = new JsonParser().parse(response).getAsJsonObject();

                if (result.get("session-state").getAsString().equals("finished")) {
                    resultReceived = true;

                    String authstate = result.get("status").getAsString();
                    if (authstate.equals("authentication-success")) {
                        LOG.info("received authentication-success");
                        JsonObject tokenresponse = result.getAsJsonObject("result");

                        try {
                            claims = processTokenResponse(tokenresponse);
                        } catch (XignTokenException ex) {
                            LOG.error(null, ex);
                        }
                        
                        LOG.info(new Gson().toJson(claims));
                    }
                    break;
                }

                if (RETRIES_COUNT == RETRIES_MAX) {
                    LOG.debug("got max retries {}", RETRIES_COUNT);
                    break;
                }

                RETRIES_COUNT++;

                Thread.sleep(1000);

            } catch (IOException | KeyStoreException | CertificateException
                    | NoSuchAlgorithmException | KeyManagementException
                    | InterruptedException ex) {
                LOG.error(null, ex);
            }

        }

        return claims;

    }

    private HttpURLConnection makeConnection(String url) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, KeyManagementException, URISyntaxException, NoSuchProviderException {
        HttpURLConnection plainConnection;
        HttpsURLConnection sslConnection;
        if (isSSL) {
            SSLContext sslContext;
            if (trustCert != null) {
                sslContext = Util.getSSLContext(trustCert);
            } else {
                sslContext = Util.getDefaultContext();
            }

            sslConnection = (HttpsURLConnection) new URL(url).openConnection();
            sslConnection.setSSLSocketFactory(sslContext.getSocketFactory());
            sslConnection.setHostnameVerifier(new NullHostnameVerifier());

            sslConnection.setDoOutput(true);
            sslConnection.setRequestMethod("POST");
            sslConnection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            sslConnection.setChunkedStreamingMode(0);
            return sslConnection;
        } else {
            plainConnection = (HttpURLConnection) new URL(url).openConnection();
            plainConnection.setDoOutput(true);
            plainConnection.setRequestMethod("POST");
            plainConnection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            plainConnection.setChunkedStreamingMode(0);
            return plainConnection;
        }

    }

    public JWTClaims processTokenResponse(JsonObject tokenResponse) throws XignTokenException {
        return Util.processTokenResponse(tokenResponse, clientKeys, keyAlias, keyPassword, trustStore, trustAlias);
    }

    private String sendMessage(JsonObject to, String url) throws CertificateException,
            NoSuchAlgorithmException, KeyStoreException, KeyManagementException,
            IOException {
        try {
            HttpURLConnection con = makeConnection(url);

            OutputStream out = new BufferedOutputStream(con.getOutputStream());
            out.write(("cmd=" + to.toString()).getBytes());
            out.flush();
            out.close();

            InputStream in = new BufferedInputStream(con.getInputStream());
            JsonObject resp = readStream(in);
            in.close();

            return resp.toString();

        } catch (IOException | URISyntaxException | NoSuchProviderException ex) {
            LOG.error("Error in connection to endpoint, returning ...", ex);
            return null;
        }
    }

    private JsonObject readStream(InputStream in) throws IOException {
        JsonParser p = new JsonParser();
        byte[] msgBytes = IOUtils.toByteArray(in);
        return p.parse(new String(msgBytes)).getAsJsonObject();
    }

    public class NullHostnameVerifier implements HostnameVerifier {

        /**
         *
         * @param hostname
         * @param session
         * @return
         */
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }

    }
}
