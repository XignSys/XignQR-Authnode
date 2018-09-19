/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 *
 * @author palle
 */
public class Util {

    public static SSLContext getSSLContext(X509Certificate cert) throws NoSuchAlgorithmException, KeyManagementException, IOException, CertificateException, KeyStoreException, NoSuchProviderException {
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
}
