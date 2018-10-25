
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;


/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author palle
 */
public class TestUtil {

    public static class TestException extends Exception {

        public TestException(String message) {
            super(message);
        }
    }

    public static KeyStore getKeyStore() throws TestException {
        InputStream in = TestUtil.class.getResourceAsStream("/test/181025_test_client.p12");
        KeyStore keystore;
        try {
            keystore = KeyStore.getInstance("pkcs12", "BC");
            keystore.load(in, "123456".toCharArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException | NoSuchProviderException ex) {
            Logger.getLogger(TestUtil.class.getName()).log(Level.SEVERE, null, ex);
            throw new TestException(ex.getMessage());
        }
        return keystore;
    }
    
    public static KeyStore getTrustStore() throws TestException {
        InputStream in = TestUtil.class.getResourceAsStream("/test/181025_test_server.p12");
        KeyStore keystore;
        try {
            keystore = KeyStore.getInstance("pkcs12", "BC");
            keystore.load(in, "123456".toCharArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException | NoSuchProviderException ex) {
            Logger.getLogger(TestUtil.class.getName()).log(Level.SEVERE, null, ex);
            throw new TestException(ex.getMessage());
        }
        
        
        return keystore;
    }

}
