package com.CryptoSportswallet.tools.manager;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.NetworkOnMainThreadException;
import android.util.Log;

import com.CryptoSportswallet.CryptoSportsApp;
import com.CryptoSportswallet.presenter.activities.util.ActivityUTILS;
import com.CryptoSportswallet.presenter.entities.CurrencyEntity;
import com.CryptoSportswallet.tools.sqlite.CurrencyDataSource;
import com.CryptoSportswallet.tools.threads.executor.BRExecutor;
import com.CryptoSportswallet.tools.util.Utils;
import com.CryptoSportswallet.wallet.WalletsMaster;
import com.CryptoSportswallet.wallet.abstracts.BaseWalletManager;
import com.CryptoSportswallet.wallet.wallets.bitcoin.WalletBitcoinManager;
import com.platform.APIClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Request;
import okhttp3.Response;

/**
 * BreadWallet
 * <p>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 7/22/15.
 * Copyright (c) 2016 breadwallet LLC
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

public class BRApiManager {
    private static final String TAG = BRApiManager.class.getName();

    private static BRApiManager instance;
    private Timer timer;

    private TimerTask timerTask;

    private Handler handler;

    private BRApiManager() {
        handler = new Handler();
    }

    public static BRApiManager getInstance() {

        if (instance == null) {
            instance = new BRApiManager();
        }
        return instance;
    }

    private Set<CurrencyEntity> getCurrencies(Activity context, BaseWalletManager walletManager) {
        if (ActivityUTILS.isMainThread()) {
            throw new NetworkOnMainThreadException();
        }
        Set<CurrencyEntity> set = new LinkedHashSet<>();
        try {
            JSONArray arr = fetchRatesCoinLib(context, walletManager);
            updateFeePerKb(context);
            if (arr != null) {
                int length = arr.length();
                for (int i = /*1*/0; i < length; i++) {
                    CurrencyEntity tmp = new CurrencyEntity();
                    try {
                        JSONObject tmpObj = (JSONObject) arr.get(i);
                        tmp.name = tmpObj.getString("name");
                        tmp.code = tmpObj.getString("code");
                        tmp.rate = (float) tmpObj.getDouble("rate");
                        String selectedISO = BRSharedPrefs.getPreferredFiatIso(context);
                        Log.i(TAG, "selectedISO: " + selectedISO);
                        if (tmp.code.equalsIgnoreCase(selectedISO)) {
//                            Log.e(TAG, "theIso : " + theIso);
//                                Log.e(TAG, "Putting the shit in the shared prefs");
                            BRSharedPrefs.putPreferredFiatIso(context, tmp.code);
                        }
                        Log.e("TAG", tmp.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    set.add(tmp);
                }
            } else {
                Log.e(TAG, "getCurrencies: failed to get currencies, response string: " + arr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.e(TAG, "getCurrencies: " + set.size());
        return new LinkedHashSet<>(set);
    }
    public static JSONArray fetchRatesCoinMarketCap(Activity app, BaseWalletManager walletManager) {
        JSONArray jsonArray = null;

        if ("RVN".equals(walletManager.getIso(app))) {
            String rvn_url = "https://api.coinmarketcap.com/v1/ticker/ravencoin/";
            String rvn_jsonString = urlGET(app, rvn_url);
            try {
                jsonArray = new JSONArray();
                JSONObject rvnobj = new JSONObject();

                Double btcRate;
                if (rvn_jsonString != null) {
                    JSONArray arr = new JSONArray(rvn_jsonString);
                    JSONObject obj = arr.getJSONObject(0);
                    String name = "US Dollar";//obj.getString("name");
                    String code = "USD"; //obj.getString("symbol");
                    Double rate = Double.parseDouble(obj.getString("price_usd"));
                    btcRate = Double.parseDouble(obj.getString("price_btc"));
                    rvnobj.put("code", code);
                    rvnobj.put("name", name);
                    rvnobj.put("rate", rate);
                    jsonArray.put(rvnobj);

                    JSONArray multiFiatJson = multiFiatCurrency(app);

                    int length = multiFiatJson.length();
                    for (int i = 0; i < length; i++) {
                        try {
                            JSONObject tmpObj = (JSONObject) multiFiatJson.get(i);

                            if (tmpObj.getString("code").equalsIgnoreCase("USD"))
                                continue;

//                            rvnobj.put("code", tmpObj.getString("code"));
//                            rvnobj.put("name", tmpObj.getString("name"));
                            double rvnRate = (float) tmpObj.getDouble("rate") * btcRate;
                            tmpObj.remove("rate");
                            tmpObj.put("rate", rvnRate);

                            jsonArray.put(tmpObj);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    return jsonArray;

                } else {
                    Log.e(TAG, "getCurrencies: failed to get currencies, response string: " + rvn_jsonString);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return jsonArray;
    }
    public static JSONArray multiFiatCurrency(Activity app) {

        String jsonString = urlGET(app, "https://bitpay.com/rates");

        JSONArray jsonArray = null;
        if (jsonString == null) return null;
        try {
            JSONObject obj = new JSONObject(jsonString);

            jsonArray = obj.getJSONArray("data");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonArray;
    }

    private void initializeTimerTask(final Context context) {
        timerTask = new TimerTask() {
            public void run() {
                //use a handler to run a toast that shows the current timestamp
                handler.post(new Runnable() {
                    public void run() {
                        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                            @Override
                            public void run() {
                                if (CryptoSportsApp.isAppInBackground(context)) {
                                    Log.e(TAG, "doInBackground: Stopping timer, no activity on.");
                                    stopTimerTask();
                                }
                                for (BaseWalletManager w : WalletsMaster.getInstance(context).getAllWallets()) {
                                    String iso = w.getIso(context);
                                    Set<CurrencyEntity> tmp = getCurrencies((Activity) context, w);
                                    CurrencyDataSource.getInstance(context).putCurrencies(context, iso, tmp);
                                }
                            }
                        });
                    }
                });
            }
        };
    }

    public void startTimer(Context context) {
        //set a new Timer
        if (timer != null) return;
        timer = new Timer();
        Log.e(TAG, "startTimer: started...");
        //initialize the TimerTask's job
        initializeTimerTask(context);

        timer.schedule(timerTask, 1000, 60000);
    }

    public void stopTimerTask() {
        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public static JSONArray fetchRates(Activity app, BaseWalletManager walletManager) {
        String url = "https://" + CryptoSportsApp.HOST + "/rates?currency=" + walletManager.getIso(app);
        String jsonString = urlGET(app, url);
        JSONArray jsonArray = null;
        if (jsonString == null) {
            Log.e(TAG, "fetchRates: failed, response is null");
            return null;
        }
        try {
            JSONObject obj = new JSONObject(jsonString);
            jsonArray = obj.getJSONArray("body");

        } catch (JSONException ignored) {
        }
        return jsonArray == null ? backupFetchRates(app, walletManager) : jsonArray;
    }

    public static JSONArray backupFetchRates(Activity app, BaseWalletManager walletManager) {
        if (!walletManager.getIso(app).equalsIgnoreCase(WalletBitcoinManager.getInstance(app).getIso(app))) {
            //todo add backup for BCH
            return null;
        }
        String jsonString = urlGET(app, "https://bitpay.com/rates");

        JSONArray jsonArray = null;
        if (jsonString == null) return null;
        try {
            JSONObject obj = new JSONObject(jsonString);

            jsonArray = obj.getJSONArray("data");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonArray;
    }
    public static JSONArray fetchRatesCoinLib(Activity app, BaseWalletManager walletManager) {
        JSONArray jsonArray = null;
        String coinlibapikey = "d331daab2f5f4f35";
        String ticker = walletManager.getIso(app);

            String rvn_url = "https://coinlib.io/api/v1/coin?key=" + coinlibapikey +"&pref=BTC&symbol=" + ticker;
            String rvn_url_usd = "https://coinlib.io/api/v1/coin?key=" + coinlibapikey +"&pref=USD&symbol=" + ticker;

            String rvn_jsonString = urlGET(app, rvn_url);
            String rvn_jsonString_usd = urlGET(app, rvn_url_usd);
            try {
                jsonArray = new JSONArray();
                JSONObject rvnobj = new JSONObject();

                Double btcRate;
                if (rvn_jsonString != null) {
//                    JSONArray arr = new JSONArray(rvn_jsonString);
//                    JSONArray arrusd = new JSONArray(rvn_jsonString_usd);

                    JSONObject obj = new JSONObject(rvn_jsonString);
                    JSONObject objusd = new JSONObject(rvn_jsonString_usd);

                    String name = "US Dollar";//obj.getString("name");
                    String code = "USD"; //obj.getString("symbol");
                    Double rate = Double.parseDouble(objusd.getString("price"));
                    btcRate = Double.parseDouble(obj.getString("price"));
                    rvnobj.put("code", code);
                    rvnobj.put("name", name);
                    rvnobj.put("rate", rate);
                    jsonArray.put(rvnobj);

                    JSONArray multiFiatJson = multiFiatCurrency(app);

                    int length = multiFiatJson.length();
                    for (int i = 0; i < length; i++) {
                        try {
                            JSONObject tmpObj = (JSONObject) multiFiatJson.get(i);

                            if (tmpObj.getString("code").equalsIgnoreCase("USD"))
                                continue;

//                            rvnobj.put("code", tmpObj.getString("code"));
//                            rvnobj.put("name", tmpObj.getString("name"));
                            double rvnRate = (float) tmpObj.getDouble("rate") * btcRate;
                            tmpObj.remove("rate");
                            tmpObj.put("rate", rvnRate);

                            jsonArray.put(tmpObj);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    return jsonArray;

                } else {
                    Log.e(TAG, "getCurrencies: failed to get currencies, response string: " + rvn_jsonString);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        return jsonArray;
    }
    /**
     *
     * @param app
     * @param walletManager
     * @return average rate for Coin referenced to 1 BTC
     *          taken from last price
     */
    public static float fetchRatesCryptoSports(Activity app, BaseWalletManager walletManager) {
//        String url1 = "https://api.coinmarketcap.com/v1/ticker/CryptoSports/";
 //use crex api
        String url1 = "https://api.crex24.com/CryptoExchangeService/BotPublic/ReturnTicker?request=[NamePairs=BTC_CSPN]";
        String jsonString1 = urlGET(app, url1);
        float price1=0;

        JSONArray jsonArray1 = null;
        if (jsonString1 == null) {
            Log.e(TAG, "fetchratesCryptoSports: coinmarketcap failed, response is null");
            return 0.1f;
        }

        try {
                        JSONObject obj1 = new JSONObject(jsonString1);
            String tickerss = obj1.getString("Tickers");
           // price1 = (1 / (float)obj1.getDouble("Last")) ;     // rate of BBP per BTC
            JSONArray finalobj = new JSONArray(tickerss);

//            JSONArray arr1 = new JSONArray(jsonString1);
//            JSONObject obj1 = (JSONObject)arr1.get(1);
            price1 = ( finalobj.getLong(2)) ;     // rate of BBP per BTC

        } catch (JSONException ignored) {
            ignored.printStackTrace();
        }
        //return 0.1f;
        Log.i("proce",price1 + "");
        return price1;
    }

    public static void updateFeePerKb(Context app) {
        WalletsMaster wm = WalletsMaster.getInstance(app);
        for (BaseWalletManager wallet : wm.getAllWallets()) {
            wallet.updateFee(app);
        }
    }

    public static String urlGET(Context app, String myURL) {
//        System.out.println("Requested URL_EA:" + myURL);
        if (ActivityUTILS.isMainThread()) {
            Log.e(TAG, "urlGET: network on main thread");
            throw new RuntimeException("network on main thread");
        }
        Map<String, String> headers = CryptoSportsApp.getBreadHeaders();

        Request.Builder builder = new Request.Builder()
                .url(myURL)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-agent", Utils.getAgentString(app, "android/HttpURLConnection"))
                .get();
        Iterator it = headers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            builder.header((String) pair.getKey(), (String) pair.getValue());
        }

        Request request = builder.build();
        String response = null;
        Response resp = APIClient.getInstance(app).sendRequest(request, false, 0);

        try {
            if (resp == null) {
                Log.e(TAG, "urlGET: " + myURL + ", resp is null");
                return null;
            }
            response = resp.body().string();
            String strDate = resp.header("date");
            if (strDate == null) {
                Log.e(TAG, "urlGET: strDate is null!");
                return response;
            }
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            Date date = formatter.parse(strDate);
            long timeStamp = date.getTime();
            BRSharedPrefs.putSecureTime(app, timeStamp);
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        } finally {
            if (resp != null) resp.close();

        }
        return response;
    }

}
