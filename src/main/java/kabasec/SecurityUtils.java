/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package kabasec;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Random;
import java.util.UUID;


public class SecurityUtils {

    

    private static final char[] alphaNumChars = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
            'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
            'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
            'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
            'u', 'v', 'w', 'x', 'y', 'z'
    };

    static final String JCEPROVIDER_IBM = "IBMJCE";
    static final String SECRANDOM_IBM = "IBMSecureRandom";
    static final String SECRANDOM_SHA1PRNG = "SHA1PRNG";

    /**
     * Generates a random alphanumeric string of length n.
     * 
     * @param length
     * @return
     */
    public static String getRandomAlphaNumeric(int length) {
        if (length <= 0) {
            return "";
        }

        Random r = getRandom();

        StringBuffer result = new StringBuffer(length);

        for (int i = 0; i < length; i++) {
            int n = r.nextInt(alphaNumChars.length);
            result.append(alphaNumChars[n]);
        }

        return result.toString();
    }

    public static Random getRandom() {
        Random result = null;
        try {
            if (Security.getProvider(JCEPROVIDER_IBM) != null) {
                result = SecureRandom.getInstance(SECRANDOM_IBM);
            } else {
                result = SecureRandom.getInstance(SECRANDOM_SHA1PRNG);
            }
        } catch (Exception e) {
            result = new Random();
        }
        return result;
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }
    
    
    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            int val = bytes[i];
            if (val < 0) {
                val += 256;
            }
            if (val < 16) {
                sb.append("0");
            }
            sb.append(Integer.toHexString(val));
        }
        return sb.toString();
    }

    public static byte[] hexStringToBytes(String string) throws NumberFormatException {
        if (string == null) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int i = 0;
        while (i < string.length() - 1) {
            baos.write(Integer.parseInt(string.substring(i, i + 2), 16));
            i = i + 2;
        }
        return baos.toByteArray();
    }
   
}