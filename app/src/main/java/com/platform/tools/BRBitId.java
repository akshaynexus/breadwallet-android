package com.platform.tools;

import android.content.Context;
import android.net.Uri;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Base64;
import android.util.Log;

import com.cspnwallet.core.BRCoreKey;
import com.cspnwallet.core.BRCoreMasterPubKey;
import com.cspnwallet.presenter.interfaces.BRAuthCompletion;
import com.cspnwallet.tools.manager.BRSharedPrefs;
import com.cspnwallet.tools.security.AuthManager;
import com.cspnwallet.tools.security.BRKeyStore;
import com.cspnwallet.tools.security.PostAuth;
import com.cspnwallet.tools.threads.executor.BRExecutor;
import com.cspnwallet.tools.util.BRConstants;
import com.cspnwallet.tools.util.Utils;
import com.platform.APIClient;
import com.platform.middlewares.plugins.WalletPlugin;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Request;
import okhttp3.RequestBody;


/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan on <mihail@breadwallet.com> 1/25/17.
 * Copyright (c) 2017 breadwallet LLC
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
public class BRBitId {
    public static final String TAG = BRBitId.class.getName();
    public static final String BITCOIN_SIGNED_MESSAGE_HEADER = "Crypto Sports Signed Message:\n";

    private static String _bitUri;
    private static String _bitIdUrl;
    private static String _strToSign = null;
    private static String _promptString = null;
    private static int _index = 0;
    private volatile static Map<String, Boolean> bitIdKeys = new HashMap<>();

    public static boolean isBitId(String uri) {
        try {
            URI bitIdUri = new URI(uri);
            if ("bitid".equals(bitIdUri.getScheme())) {
                return true;
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void signBitID(final Context context, String uri, JSONObject jsonBody) {

        if (uri == null && jsonBody != null) {
            try {
                uri = jsonBody.getString("bitid_url");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (uri == null) {
            Log.e(TAG, "signBitID: uri is null");
            return;
        }
        _bitUri = uri;

        final URI bitIdUri;
        try {
            bitIdUri = new URI(_bitUri);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            Log.e(TAG, "signBitID: returning false: ", e);
            return;
        }

        final String biUri = bitIdUri.getHost() == null ? bitIdUri.toString() : bitIdUri.getHost();
        final boolean authNeeded = !bitIdKeys.containsKey(biUri);

        if (jsonBody != null) {
            try {
                _promptString = jsonBody.getString("prompt_string");
                _bitIdUrl = jsonBody.getString("bitid_url");
                _index = jsonBody.getInt("bitid_index");
                _strToSign = jsonBody.getString("string_to_sign");
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
        } else if ("bitid".equals(bitIdUri.getScheme())) {
            if (context == null) {
                Log.e(TAG, "signBitID: app is null, returning true still");
                return;
            }
            _promptString = "BitID Authentication Request";
        }

//        Log.e(TAG, "signBitID: _bitUri: " + _bitUri);
//        Log.e(TAG, "signBitID: _strToSign: " + _strToSign);
//        Log.e(TAG, "signBitID: _index: " + _index);
        BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (authNeeded) {
                    BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                        @Override
                        public void run() {
                            AuthManager.getInstance().authPrompt(context, _promptString, bitIdUri.getHost(), true, false, new BRAuthCompletion() {
                                @Override
                                public void onComplete() {
                                    PostAuth.getInstance().onBitIDAuth(context, true);
                                }

                                @Override
                                public void onCancel() {
                                    PostAuth.getInstance().onBitIDAuth(context, false);
                                }
                            });
                        }
                    });

                } else {
                    PostAuth.getInstance().onBitIDAuth(context, true);
                }

            }
        });
    }

    public static void completeBitID(final Context context, boolean authenticated) {
        final byte[] phrase;
        final byte[] nulTermPhrase;
        final byte[] seed;
        if (!authenticated) {
            WalletPlugin.sendBitIdResponse(null, false);
            return;
        }
        if (context == null) {
            Log.e(TAG, "completeBitID: app is null");
            return;
        }
        if (_bitUri == null) {
            Log.e(TAG, "completeBitID: _bitUri is null");
            return;
        }

        final Uri uri = Uri.parse(_bitUri);

        try {
            phrase = BRKeyStore.getPhrase(context, BRConstants.REQUEST_PHRASE_BITID);
        } catch (UserNotAuthenticatedException e) {
            return;
        }
        if (Utils.isNullOrEmpty(phrase)) throw new NullPointerException("cant happen");
        seed = BRCoreKey.getSeedFromPhrase(phrase);
        if (Utils.isNullOrEmpty(seed)) {
            Log.e(TAG, "completeBitID: seed is null!");
            return;
        }

        //run the callback
        BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (_strToSign == null) {
                        //meaning it's a link handling
                        bitIdLink(context, uri, seed);
                    } else {
                        //meaning its the wallet plugin, glidera auth (platform)
                        bitIdPlatform(context, uri, seed);
                    }
                } finally {
                    //release everything
                    _bitUri = null;
                    _strToSign = null;
                    _bitIdUrl = null;
                    _promptString = null;
                    _index = 0;
                    Arrays.fill(phrase, (byte) 0);
                    Arrays.fill(seed, (byte) 0);
                }
            }
        });

    }

    private static void bitIdPlatform(Context context, Uri uri, byte[] seed) {

        final String biUri = uri.getHost() == null ? uri.toString() : uri.getHost();
        final byte[] key = BRCoreMasterPubKey.bip32BitIDKey(seed, _index, biUri);
        if (key == null) {
            Log.d(TAG, "bitIdPlatform: key is null!");
            return;
        }
        if (_strToSign == null) {
            Log.d(TAG, "bitIdPlatform: _strToSign is null!");
            return;
        }
        final String sig = BRBitId.signMessage(_strToSign, new BRCoreKey(key));
        final String address = new BRCoreKey(key).address();

        JSONObject postJson = new JSONObject();
        Log.e(TAG, "GLIDERA: address:" + address);
        try {
            postJson.put("address", address);
            postJson.put("signature", sig);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //keep auth off for this host for 60 seconds.
        if (!bitIdKeys.containsKey(biUri)) {
            bitIdKeys.put(biUri, true);
            Log.d(TAG, "run: saved temporary sig for key: " + biUri);
            BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "run: removed temporary sig for key: " + biUri);
                    if (bitIdKeys != null)
                        bitIdKeys.remove(biUri);
                }
            });
        }
        WalletPlugin.sendBitIdResponse(postJson, true);
    }

    private static void bitIdLink(Context context, Uri uri, byte[] seed) {
        String nonce = null;
        String scheme = "https";

        String u = uri.getQueryParameter("u");
        if (u != null && u.equalsIgnoreCase("1")) {
            scheme = "http";
        }
        String x = uri.getQueryParameter("x");
        if (Utils.isNullOrEmpty(x)) {
            nonce = newNonce(context, uri.getHost() + uri.getPath());     // we are generating our own nonce
        } else {
            nonce = x;              // service is providing a nonce
        }

        String callbackUrl = String.format("%s://%s%s", scheme, uri.getHost(), uri.getPath());

        // build a payload consisting of the signature, address and signed uri

        String uriWithNonce = String.format("bitid://%s%s?x=%s", uri.getHost(), uri.getPath(), nonce);

        Log.e(TAG, "LINK: callbackUrl:" + callbackUrl);
        final byte[] key = BRCoreMasterPubKey.bip32BitIDKey(seed, _index, _bitUri);

        if (key == null) {
            Log.d(TAG, "completeBitID: key is null!");
            return;
        }

        final String sig = BRBitId.signMessage(uriWithNonce, new BRCoreKey(key));
        final String address = new BRCoreKey(key).address();
        Log.e(TAG, "LINK: address: " + address);
        JSONObject postJson = new JSONObject();
        try {
            postJson.put("address", address);
            postJson.put("signature", sig);
            postJson.put("uri", uriWithNonce);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody requestBody = RequestBody.create(null, postJson.toString());
        Request request = new Request.Builder()
                .url(callbackUrl + "?x=" + nonce)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build();
        APIClient.BRResponse res = APIClient.getInstance(context).sendRequest(request, true);
    }

    public static String newNonce(Context context, String nonceKey) {
        // load previous nonces. we save all nonces generated for each service
        // so they are not used twice from the same device
        List<Integer> existingNonces = BRSharedPrefs.getBitIdNonces(context, nonceKey);

        String nonce = "";
        while (existingNonces.contains(Integer.valueOf(nonce))) {
            nonce = String.valueOf(System.currentTimeMillis() / 1000);
        }
        existingNonces.add(Integer.valueOf(nonce));
        BRSharedPrefs.putBitIdNonces(context, existingNonces, nonceKey);

        return nonce;
    }

    public static String signMessage(String message, BRCoreKey key) {
        byte[] signingData = formatMessageForBitcoinSigning(message);

        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        byte[] sha256First = digest.digest(signingData);
        byte[] sha256Second = digest.digest(sha256First);
        byte[] signature = key.compactSign(sha256Second);

        return Base64.encodeToString(signature, Base64.NO_WRAP);
    }

    private static byte[] formatMessageForBitcoinSigning(String message) {
        byte[] headerBytes = null;
        byte[] messageBytes = null;

        try {
            headerBytes = BITCOIN_SIGNED_MESSAGE_HEADER.getBytes("UTF-8");
            messageBytes = message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        assert (headerBytes != null);
        assert (messageBytes != null);
        if (headerBytes == null || messageBytes == null) return new byte[0];

        int cap = 1 + headerBytes.length + varIntSize(messageBytes.length) + messageBytes.length;

        ByteBuffer dataBuffer = ByteBuffer.allocate(cap).order(ByteOrder.LITTLE_ENDIAN);
        dataBuffer.put((byte) headerBytes.length);          //put header count
        dataBuffer.put(headerBytes);                        //put the header
        putVarInt(message.length(), dataBuffer);            //put message count
        dataBuffer.put(messageBytes);                       //put the message
        byte[] result = dataBuffer.array();

        Assert.assertEquals(cap, result.length);

        return result;
    }

    /**
     * Returns the encoding size in bytes of its input value.
     *
     * @param i the integer to be measured
     * @return the encoding size in bytes of its input value
     */
    private static int varIntSize(int i) {
        int result = 0;
        do {
            result++;
            i >>>= 7;
        } while (i != 0);
        return result;
    }

    /**
     * Encodes an integer in a variable-length encoding, 7 bits per byte, to a
     * ByteBuffer sink.
     *
     * @param v    the value to encode
     * @param sink the ByteBuffer to add the encoded value
     */
    private static void putVarInt(int v, ByteBuffer sink) {
        while (true) {
            int bits = v & 0x7f;
            v >>>= 7;
            if (v == 0) {
                sink.put((byte) bits);
                return;
            }
            sink.put((byte) (bits | 0x80));
        }
    }
}
