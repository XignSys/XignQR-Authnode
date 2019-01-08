
import com.nimbusds.jose.JOSEException;
import com.xign.forgerock.common.JWTClaims;
import com.xign.forgerock.common.XignTokenException;
import com.xign.forgerock.xignqr.TokenFetcherClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Properties;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Test;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author palle
 */
public class AuthNodeTest {

    @Test
    public void testTokenFetcherClient() throws TestUtil.TestException, JOSEException, UnsupportedEncodingException, KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, XignTokenException {
        Security.addProvider(new BouncyCastleProvider());
        
        // get test keystores
        KeyStore keys = TestUtil.getKeyStore();
        KeyStore trust = TestUtil.getTrustStore();

        // mockup client.keystore
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        keys.store(bos, "123456".toCharArray());
        String ks = new String(Base64.getEncoder().encode(bos.toByteArray()),"UTF-8");
        
        // mockup client.trustcert
        String trustCert = new String(Base64.getEncoder().encode(trust.getCertificate("xyz").getEncoded()), "UTF-8");
        
        // mockup properties
        Properties dummyProps = new Properties();
        dummyProps.setProperty("client.keystore", ks);
        dummyProps.setProperty("client.keystore.password", "123456");
        dummyProps.setProperty("client.keystore.alias", "xyz");
        dummyProps.setProperty("client.redirect_uri", "http://redirect.uri");
        dummyProps.setProperty("client.id", "clientId");
        dummyProps.setProperty("manager.url.token", "https://prod.v22017042416647763.bestsrv.de/idp/test/dts");
        dummyProps.setProperty("syncfuel.master.secret", "secret");
        dummyProps.setProperty("manager.url.status", "https://prod.v22017042416647763.bestsrv.de/idp/test/state");
        dummyProps.setProperty("client.trustcert", trustCert);
        
        bos = new ByteArrayOutputStream();
        dummyProps.store(bos, "");
        ByteArrayInputStream in = new ByteArrayInputStream(bos.toByteArray());
        
        
        // test token retrieval, signature, encryption/decryption
        TokenFetcherClient tf = new TokenFetcherClient(in, null, false);
        JWTClaims jwtClaims = tf.requestIdToken("");
        
        // assert that claims successfully decryptes and verified
        Assert.assertEquals("dummy-issuer", jwtClaims.getIss());
        Assert.assertEquals("this-is-a-restricted-Id", jwtClaims.getSub());
        Assert.assertEquals("clientId", jwtClaims.getAud());
        Assert.assertEquals(1, jwtClaims.getTrustLevel());
        Assert.assertEquals("palle", jwtClaims.getNickname());
        Assert.assertEquals("email", jwtClaims.getEmail());

    }

}
