/*
 * Copyright (c) 2019 IBM Corporation and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kabasec;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

// Tracks JWT's that have been logged out
public class JwtTracker {
    private static BoundedHashMap loggedOutJwts = null;

    public static synchronized boolean isLoggedOut(String jwt) {
        init();
        boolean result = loggedOutJwts.get(getSha256Digest(jwt)) != null;
        return result;
    }

    public synchronized static void add(String rawToken) {
        init();
        // digest it to reduce size
        String digestedJwt = getSha256Digest(rawToken);
        if (digestedJwt != null) {
            loggedOutJwts.put(digestedJwt, System.currentTimeMillis());
        }
    }

    private static void init() {
        if (loggedOutJwts == null) {
            loggedOutJwts = (new JwtTracker()).new BoundedHashMap(5000);
        }
    }

    static String getSha256Digest(String in) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("caught unexpected exception: "+ e + " Cause: "+ e.getCause());
            e.printStackTrace(System.out);
            return null;
        }
        byte[] digest = md.digest(in.getBytes(Charset.forName("UTF-8")));
        return new String(digest);
    }

    class BoundedHashMap extends LinkedHashMap<String, Object> {

        private static final long serialVersionUID = 7306671418293201026L;

        private int MAX_ENTRIES = 10000;

        public BoundedHashMap(int maxEntries) {
            super();
            if (maxEntries > 0) {
                MAX_ENTRIES = maxEntries;
            }
        }

        public BoundedHashMap(int initSize, int maxEntries) {
            super(initSize);
            if (maxEntries > 0) {
                MAX_ENTRIES = maxEntries;
            }
        }

        public BoundedHashMap() {
            super();
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_ENTRIES;
        }

    }

}
