/*******************************************************************************
 * Copyright (c) 2017, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package kabasec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.Subject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.jwt.JsonWebToken;

import com.ibm.websphere.security.WSSecurityException;

/**
 *  Provides methods to AES encrypt and decrypt Github Personal Access Tokens or passwords.
 */
// TODO: maybe just call passwordutil api instead of re-implementing it.
public class PATHelper {
    private static final String TRANSFORMATION_AES = "AES/CBC/PKCS5Padding";
    static final String AESKEY_PROPERTYNAME = "AESEncryptionKey";
    static String aesKey = null;
    
 
    /**
     * Extract the Json Web Token  (JWT) from the security subject, then  decrypt 
     * the user's Github Personal Access Token from the JWT.
     * @param  the Subject 
     * @return the PAT or password if available, null otherwise. 
     * 
     */
    public String extractGithubAccessTokenFromSubject() {
        String result = null;
        Subject s = null;
        try {
            s = com.ibm.websphere.security.auth.WSSubject.getRunAsSubject();
        } catch (WSSecurityException e) {            
            e.printStackTrace();
            return result;
        }
        Set<JsonWebToken> creds = s.getPrincipals(JsonWebToken.class);
        if(creds == null) {
            return result;
        }
        JsonWebToken j = creds.iterator().next();
        if (j == null) {
            return result;
        }
        String encryptedPat = j.getClaim(Constants.PAT_JWT_CLAIM);
        if (encryptedPat == null) {
            return result;
        }
        System.out.println("** encryptedPat: "+ encryptedPat);
        return decrypt(encryptedPat);
    }
    
    /**
     * AES encrypt the input string.
     * Use an encryption key obtained from the mp-config parameter AESEncryptionKey
     * If that environment variable / system prop / meta-inf prop is undefined,
     * then generate a random one, which won't survive a server restart. 
     * 
     * @param pat
     * @return encrypted String
     */
    String encrypt(String pat) {
        init();
        return aesEncrypt(pat);
    }
    
    /**
     * AES decrypt the input string. 
     * Use an encryption key obtained from the mp-config parameter AESEncryptionKey
     * If that environment variable / system prop / meta-inf prop is undefined,
     * then generate a random one, which won't survive a server restart. 
     * 
     * @param pat
     * @return decrypted String
     */
    String decrypt(String pat) {
        init();
        return aesDecrypt(pat);
    }
    
    /**
     * Try to obtain an encryption key from the mp-config parameter AESEncryptionKey
     * If that environment variable / system prop / meta-inf prop is undefined,
     * then generate a random key, which won't survive a server restart. 
     * @return
     */
    private String getEncryptionKey() {
        Config config = ConfigProvider.getConfig();
        String key = null;
        try {
            key = config.getValue(AESKEY_PROPERTYNAME, String.class);
            System.out.println("** got aes key from config");
        } catch (NoSuchElementException e) {
            // it's not there
        }
        if (key == null || key.isEmpty()) {
            System.out.println("** generating random aes key");
            key = getRandomString();
        }
        return key;
    }
    
    private static String getRandomString() {
        return SecurityUtils.getRandomAlphaNumeric(40);
    }
    
    private  void init() {
        if (aesKey == null) {
            aesKey = getEncryptionKey();
        }
    }
    
    
    private String aesEncrypt(String input)  {
        String output = null;
        if(input==null) { return null; }
        
        EncryptionParms params = setupEncryption();
        if(params == null) { return null; }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION_AES);
            cipher.init(Cipher.ENCRYPT_MODE, params.keySpec, params.ivSpec);
            byte[] encryptedBytes = cipher.doFinal(input.getBytes("UTF-8"));
            output = SecurityUtils.bytesToHexString(encryptedBytes);
        } catch (Exception e) {
            System.out.println("Exception occurred during encryption: " + e + " StackTrace:");
            e.printStackTrace(System.out);
        }
        System.out.println("** encrypt in: "+ input + " out: " + output);
        return output;
    }
    
    
    private String aesDecrypt(String input)  {
        String output = null;
        if(input==null) { return null; }
        EncryptionParms params = setupEncryption();
        if(params == null) { return null; }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION_AES);
            cipher.init(Cipher.DECRYPT_MODE, params.keySpec, params.ivSpec);
            byte[] decryptedBytes = cipher.doFinal(SecurityUtils.hexStringToBytes(input));
            output =  new String(decryptedBytes, "UTF-8");
        } catch (Exception e)  {
            System.out.println("Exception occurred during decryption: " + e + " StackTrace:");
            e.printStackTrace(System.out);
        }
        System.out.println("** decrypt in: "+ input + " out: " + output);
        return output;
    }
  
    class EncryptionParms{
        final SecretKeySpec keySpec;
        final IvParameterSpec ivSpec;
        public EncryptionParms(SecretKeySpec spec, IvParameterSpec ivpSpec){
           keySpec = spec;
           ivSpec =  ivpSpec;
        }
    }
    
    private EncryptionParms setupEncryption() {
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
           
            char[] password = aesKey.toCharArray();
            byte[] salt = new byte[] { -89, -94, -125, 57, 76, 90, -77, 79, 50, 21, 10, -98, 47, 23, 17, 56, -61, 46, 125, -128 };
            int iterationCount = 84756;
            int keyLength = 128;
            KeySpec aesKey = new PBEKeySpec(password, salt,iterationCount, keyLength);
            byte[] data = keyFactory.generateSecret(aesKey).getEncoded();
            return new PATHelper.EncryptionParms(new SecretKeySpec(data,"AES"), new IvParameterSpec(data));
        } catch (InvalidKeySpecException e) {
            return null;
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

 
}
