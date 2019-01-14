/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock.common;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.PKCS12ParametersGenerator;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;

/**
 *
 * @author palle
 */
public class Crypto {

    public static byte[] encryptCode(String pin, String code) throws UnsupportedEncodingException, DataLengthException, IllegalStateException, InvalidCipherTextException {
        byte[] key = PBEParametersGenerator.PKCS12PasswordToBytes(pin.toCharArray());
        PBEParametersGenerator pbeGen = new PKCS12ParametersGenerator(new SHA256Digest());

        pbeGen.init(key, new byte[]{}, 128);
        CipherParameters cp = pbeGen.generateDerivedParameters(256);
        PaddedBufferedBlockCipher encryptCipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), new PKCS7Padding());
        encryptCipher.init(true, cp);

        byte[] in = code.getBytes("ISO8859-1");
        byte[] out = new byte[1024];
        int i = encryptCipher.processBytes(in, 0, in.length, out, 0);
        i = encryptCipher.doFinal(out, i);

        return Arrays.copyOf(out, i);

    }

    private static CipherParameters getCipherParameter(int pin) {
        byte[] key = PBEParametersGenerator.PKCS12PasswordToBytes(String.valueOf(pin).toCharArray());
        PBEParametersGenerator pbeGen = new PKCS12ParametersGenerator(new SHA256Digest());
        pbeGen.init(key, new byte[]{}, 128);
        CipherParameters cp = pbeGen.generateDerivedParameters(256);
        return cp;
    }

    public static byte[] decryptCode(int pin, byte[] encrypted) throws DataLengthException, IllegalStateException, InvalidCipherTextException {

        PaddedBufferedBlockCipher decryptCipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), new PKCS7Padding());
        decryptCipher.init(false, getCipherParameter(pin));
        byte[] decrypted = new byte[1024];
        int i = decryptCipher.processBytes(encrypted, 0, encrypted.length, decrypted, 0);
        i = decryptCipher.doFinal(decrypted, i);
//        System.out.println(new String(Arrays.copyOf(decrypted, i)));
        return Arrays.copyOf(decrypted, i);
    }

    public static byte[] encryptAESBinaryCBCPKCS5Pad(byte[] toEncrypt, SecretKey skey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, UnsupportedEncodingException {
        IvParameterSpec ivs = new IvParameterSpec(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        return encryptAESBinaryCBCPKCS5Pad(ivs.getIV(), toEncrypt, skey);
    }

    public static byte[] encryptAESBinaryCBCPKCS5Pad(byte[] iv, byte[] toEncrypt, SecretKey skey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec skeySpec = new SecretKeySpec(skey.getEncoded(), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(iv));
        return cipher.doFinal(toEncrypt);
    }

    @Deprecated //Verwendet in BankingTransaction, RPEndpoint, LoginEndpoint, OIDCEndpoint, ShopTransaction
    public static String encryptAES(byte[] toEncrypt, SecretKey skey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance("AES");
        SecretKeySpec skeySpec = new SecretKeySpec(skey.getEncoded(), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        return new String(Hex.encode(cipher.doFinal(toEncrypt)));
    }

    public static String decryptAESBinaryCBCPKCS5Pad(String toDecrypt, SecretKey skey, byte[] iv) throws UnsupportedEncodingException {
        byte[] decValue = null;
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec skeySpec = new SecretKeySpec(skey.getEncoded(), "AES");
            c.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(iv));
            byte[] bytes = toDecrypt.getBytes("ISO8859-1");
            decValue = c.doFinal(bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException ex) {
            Logger.getLogger(Crypto.class.getName()).log(Level.SEVERE, null, ex);
        }

        return new String(decValue, "ISO8859-1");
    }

    public static String decryptAESBinaryCBCPKCS5Pad(String toDecrypt, SecretKey skey) throws UnsupportedEncodingException {
        byte[] decValue = null;
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec skeySpec = new SecretKeySpec(skey.getEncoded(), "AES");
            c.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
            byte[] bytes = toDecrypt.getBytes("ISO8859-1");
            decValue = c.doFinal(bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException ex) {
            Logger.getLogger(Crypto.class.getName()).log(Level.SEVERE, null, ex);
        }

        return new String(decValue, "ISO8859-1");
    }

    public static byte[] sign(byte[] hash, PrivateKey pkey) throws IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, InvalidKeyException, InvalidKeySpecException, SignatureException {
        Signature sig = Signature.getInstance("SHA1withECDSA");
        sig.initSign(pkey);
        sig.update(hash);
        byte[] signed_hash = sig.sign();
        return signed_hash;
    }

    public static byte[] signRSA(byte[] hash, PrivateKey pkey) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, InvalidKeyException, InvalidKeySpecException, SignatureException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(pkey);
        sig.update(hash);
        byte[] signed_hash = sig.sign();
        return signed_hash;
    }


    public static boolean verify(byte[] ce, byte[] signature, PublicKey pub) {

        PublicKey pk = pub;
        boolean verified = false;
        try {
            verified = verifySigBC(ce, signature, pk);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException | SignatureException ex) {
            ex.printStackTrace();
            return false;
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Crypto.class.getName()).log(Level.SEVERE, null, ex);
        }
        return verified;
    }

    //Hilfsmethode f+ür verify(TransmitObjectProto.Payload r, byte[] ce, byte[] signature, KeyStore trustStore)
    public static boolean verifySigBC(byte[] am, byte[] signature, PublicKey k) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException {
        try {
            ASN1InputStream decoder = new ASN1InputStream(signature);
            ASN1Sequence seq = (ASN1Sequence) decoder.readObject();
            ASN1Integer rrr = (ASN1Integer) seq.getObjectAt(0);
            ASN1Integer sss = (ASN1Integer) seq.getObjectAt(1);
            decoder.close();

            ECDSASigner verifier = new ECDSASigner();
            verifier.init(false, ECUtil.generatePublicKeyParameter(k));
            return verifier.verifySignature(doSHA1Hash(am), rrr.getPositiveValue(), sss.getPositiveValue());
        } catch (IOException ex) {
            Logger.getLogger(Crypto.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

//    @Deprecated
//    public static boolean verify(XignRPMessage r, String ce, String signature, KeyStore trustStore) {
//
//        X509Certificate c = null;
//        String rp = r.getRelyingParty();
//        try {
//            c = (X509Certificate) trustStore.getCertificate(rp);
//        } catch (KeyStoreException ex) {
//            ex.printStackTrace();
//            return false;
//        }
//        PublicKey pk = c.getPublicKey();
//        boolean verified = false;
//        try {
//            verified = verifySig(ce, signature, pk);
//        } catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException | SignatureException ex) {
//            ex.printStackTrace();
//            return false;
//        } catch (UnsupportedEncodingException ex) {
//            Logger.getLogger(Crypto.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return verified;
//    }

    //Hilfsmethode für verify(EasyRequest r, String ce, String signature, KeyStore trustStore)
    @Deprecated
    public static boolean verifySig(String am, String signature, PublicKey k) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException {
        Signature sig = Signature.getInstance("SHA1withECDSA", new BouncyCastleProvider());
        sig.initVerify(k);
        sig.update(am.getBytes("ISO8859-1"));
        return sig.verify(Hex.decode(signature));
    }

    //Verwendet in AuthEndpoint für Verifizierung der CHALLENGE!!!!!!
    public static boolean verifyChallenge(byte[] am, byte[] signature, PublicKey k) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException {
        Signature sig = Signature.getInstance("SHA1withECDSA", new BouncyCastleProvider());
        sig.initVerify(k);
        sig.update(am);
        return sig.verify(signature);
    }

    public static byte[] doSHA1Hash(byte[] toHash) {
        byte[] buffer = new byte[20];
        SHA1Digest sha1 = new SHA1Digest();
        sha1.update(toHash, 0, toHash.length);
        sha1.doFinal(buffer, 0);
        return buffer;
    }

    public static String doSHA256Hash(String toHash) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(toHash.getBytes("UTF-8")); // Change this to "UTF-16" if needed
        byte[] digest = md.digest();
        return new String(Base64.encode(digest));
    }

    public static String doSHA256Hash(byte[] toHash) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(toHash); // Change this to "UTF-16" if needed
        byte[] digest = md.digest();
        return new String(Base64.encode(digest));
    }

    //    public static String encryptAESBinaryCBCPKCS5PadAsString(byte[] toEncrypt, SecretKey skey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, UnsupportedEncodingException {
//        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", new BouncyCastleProvider());
//        SecretKeySpec skeySpec = new SecretKeySpec(skey.getEncoded(), "AES");
//        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
//        return new String(cipher.doFinal(toEncrypt), "ISO8859-1");
//    }
    //    public static String decryptAESBinary(String toDecrypt, SecretKey skey) throws UnsupportedEncodingException {
//        byte[] decValue = null;
//        try {
//            Cipher c = Cipher.getInstance("AES", new BouncyCastleProvider());
//            SecretKeySpec skeySpec = new SecretKeySpec(skey.getEncoded(), "AES");
//            c.init(Cipher.DECRYPT_MODE, skeySpec);
//            byte[] bytes = toDecrypt.getBytes("ISO8859-1");
//            decValue = c.doFinal(bytes);
//        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
//            Logger.getLogger(Crypto.class.getName()).log(Level.SEVERE, null, ex);
//        }
//
//        return new String(decValue, "ISO8859-1");
//    }
//    @Deprecated
//    public static String decryptAES(String toDecrypt, SecretKey skey) throws UnsupportedEncodingException {
//        byte[] decValue = null;
//        try {
//            Cipher c = Cipher.getInstance("AES", new BouncyCastleProvider());
//            SecretKeySpec skeySpec = new SecretKeySpec(skey.getEncoded(), "AES");
//            c.init(Cipher.DECRYPT_MODE, skeySpec);
//            byte[] bytes = Hex.decode(toDecrypt);
//            decValue = c.doFinal(bytes);
//        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
//            Logger.getLogger(Crypto.class.getName()).log(Level.SEVERE, null, ex);
//        }
//
//        return new String(decValue, "ISO8859-1");
//    }
    //    public static String encryptAESBinary(byte[] toEncrypt, SecretKey skey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException {
//        Cipher cipher = Cipher.getInstance("AES", new BouncyCastleProvider());
//        SecretKeySpec skeySpec = new SecretKeySpec(skey.getEncoded(), "AES");
//        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
//        return new String(cipher.doFinal(toEncrypt), "ISO8859-1");
//    }
}
