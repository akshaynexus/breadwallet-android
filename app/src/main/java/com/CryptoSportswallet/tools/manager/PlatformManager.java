package com.CryptoSportswallet.tools.manager;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.support.annotation.WorkerThread;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.CryptoSportswallet.CryptoSportsApp;
import com.CryptoSportswallet.R;
import com.CryptoSportswallet.core.BRCoreAddress;
import com.CryptoSportswallet.core.BRCoreTransaction;
import com.CryptoSportswallet.presenter.activities.PlatformActivity;
import com.CryptoSportswallet.presenter.activities.util.ActivityUTILS;
import com.CryptoSportswallet.presenter.entities.CryptoRequest;
import com.CryptoSportswallet.presenter.entities.CurrencyEntity;
import com.CryptoSportswallet.presenter.entities.DealUiHolder;
import com.CryptoSportswallet.presenter.entities.TxUiHolder;
import com.CryptoSportswallet.tools.adapter.DealListAdapter;
import com.CryptoSportswallet.tools.animation.BRAnimator;
import com.CryptoSportswallet.tools.animation.SpringAnimator;
import com.CryptoSportswallet.tools.listeners.RecyclerItemClickListener;
import com.CryptoSportswallet.tools.sqlite.CurrencyDataSource;
import com.CryptoSportswallet.tools.threads.executor.BRExecutor;
import com.CryptoSportswallet.tools.util.Utils;
import com.CryptoSportswallet.wallet.WalletsMaster;
import com.CryptoSportswallet.wallet.abstracts.BaseWalletManager;
import com.platform.APIClient;
import com.platform.tools.KVStoreManager;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;


/**
 * BreadWalletP
 * <p/>
 * Created by Mihail Gutan on <mihail@breadwallet.com> 7/19/17.
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
public class PlatformManager {

    private static final String URL_PLATFORM = "https://dev.CryptoSports.org/api/wallet";

    private static final String TAG = PlatformManager.class.getName();
    private static PlatformManager instance;
    private RecyclerView txList;
    public DealListAdapter adapter;
    private PlatformActivity platformActivity;

    private String UserName = "";
    private String PublicAddress = "";
    private String PublicKey = "";

    public static PlatformManager getInstance() {
        if (instance == null) instance = new PlatformManager();
        return instance;
    }

    public void init(final PlatformActivity app) {

        platformActivity = app;
        txList = app.findViewById(R.id.tx_list);
        txList.setLayoutManager(new CustomLinearLayoutManager(app));
        txList.addOnItemTouchListener(new RecyclerItemClickListener(app,
                txList, new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position, float x, float y) {

                DealUiHolder item = adapter.getItems().get(position);
                BRAnimator.showDealDetails(app, item, position);
            }

            @Override
            public void onLongItemClick(View view, int position) {
                DealUiHolder item = adapter.getItems().get(position);
                item.DoActionForManager();
            }
        }));
        if (adapter == null)
            adapter = new DealListAdapter(app, null);
        txList.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        //setupSwipe(app);
    }

    private PlatformManager() {
    }

    public void onResume(final Activity app) {
        crashIfNotMain();
    }

    public void setData(String username, String publicaddress, String publickey)
    {
        UserName = (username==null)?"":username;
        PublicAddress = (publicaddress==null)?"":publicaddress;
        PublicKey = (publickey==null)?"":publickey;
    }

    @WorkerThread
    public synchronized void updateDealList(final Context app) {

        if (UserName.equals("") || PublicAddress.equals("") || PublicKey.equals("") || UserName.equals("Platform Username..."))
        {
            return;
        }

        long start = System.currentTimeMillis();
        /*BaseWalletManager wallet = WalletsMaster.getInstance(app).getCurrentWallet(app);
        if (wallet == null) {
            Log.e(TAG, "updateTxList: wallet is null");
            return;
        }*/
        final List<DealUiHolder> items = getDealUiHolders( app );

        long took = (System.currentTimeMillis() - start);
        if (took > 500)
            Log.e(TAG, "updateDealList: took: " + took);
        if (adapter != null) {
            ((Activity) app).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adapter.setItems(items);
                    txList.setAdapter(adapter);
                    adapter.notifyDataSetChanged();
                }
            });
        }

    }

    public void addEscrowDeals( Context app, List<DealUiHolder> uiDeals )
    {
        String url = String.format("%s/getPendingEscrows?username=%s&address=%s&publicKey=%s", URL_PLATFORM, UserName, PublicAddress, PublicKey);
        String jsonRes = urlSend( app, url, "GET");

        JSONArray jsonObject = null;
        if (jsonRes == null) {
            jsonRes = urlSend( app, url, "GET");   // retry
            if (jsonRes == null) {
                Log.e(TAG, "getPendingEscrows: platform failed, response is null");
                return;
            }
        }

        try {
            JSONObject obj = new JSONObject(jsonRes);
            boolean result = obj.getBoolean("result");
            JSONArray deals = obj.getJSONArray("data");
            if (result)
            {
                for(int n = 0; n < deals.length(); n++)
                {
                    JSONObject deal = deals.getJSONObject(n);
                    uiDeals.add(new DealUiHolder( this, deal.getString("DealId"), deal.getDouble("EscrowAmount"), deal.getString("JobTitle")
                            ,deal.getString("ReceiverUserName"), deal.getString("EscrowAddress"), deal.getString("type"),"",0,
                            "", "", "", DealUiHolder.PendingTypeEnum.ESCROW) );

                }
            }
        } catch (JSONException ignored) {
        }


    }

    public void addPendingDeals( Context app, List<DealUiHolder> uiDeals )
    {

    }

    public List<DealUiHolder> getDealUiHolders( Context app )
    {
        List<DealUiHolder> uiDeals = new ArrayList<>();

        addEscrowDeals(app, uiDeals);
        addPendingDeals(app, uiDeals);

/*
        for (int i = txs.length - 1; i >= 0; i--) { //revere order
            BRCoreTransaction tx = txs[i];
            uiTxs.add(new TxUiHolder(tx.getTimestamp(), (int) tx.getBlockHeight(), tx.getHash(),
                    tx.getReverseHash(), getWallet().getTransactionAmountSent(tx),
                    getWallet().getTransactionAmountReceived(tx), getWallet().getTransactionFee(tx),
                    tx.getOutputAddresses(), tx.getInputAddresses(),
                    getWallet().getBalanceAfterTransaction(tx), (int) tx.getSize(),
                    getWallet().getTransactionAmount(tx), getWallet().transactionIsValid(tx)));
        }
*/
        return uiDeals;
    }

    public void SendEscrow(DealUiHolder uiHolder)
    {
        WalletsMaster master = WalletsMaster.getInstance(platformActivity);
        BaseWalletManager wallet = master.getWalletByIso(platformActivity, "CSPN");
        String comment = "DealId: " + uiHolder.DealId;
        long amount = (long)(uiHolder.EscrowAmount*100000000);
        long balance = wallet.getCachedBalance(platformActivity);
        BigDecimal cryptoAmount = new BigDecimal(amount);
        boolean allFilled=true;

        BRCoreAddress address = new BRCoreAddress(uiHolder.EscrowAddress);
        BRCoreTransaction tx = wallet.getWallet().createTransaction(amount, address);

        if ( cryptoAmount.longValue() > balance ) {
            allFilled = false;
            Toast.makeText(platformActivity, "Insufficient funds!", Toast.LENGTH_LONG).show();
        }

        //                if (tx == null) {
//                    BRDialog.showCustomDialog(app, app.getString(R.string.Alert_error), app.getString(R.string.Send_creatTransactionError), app.getString(R.string.AccessibilityLabels_close), null, new BRDialogView.BROnClickListener() {
//                        @Override
//                        public void onClick(BRDialogView brDialogView) {
//                            brDialogView.dismissWithAnimation();
//                        }
//                    }, null, null, 0);
//                    return;
//                }

        if (allFilled) {
            CryptoRequest item = new CryptoRequest(tx, null, false, comment, uiHolder.EscrowAddress, cryptoAmount);
            SendManager.sendTransaction(platformActivity, item, wallet);
            String txId = new String(item.tx.getHash());
            String txId2 = KVStoreManager.txKey(item.tx.getHash());
            String txId3 = Utils.bytesToHex(item.tx.getHash());
        }
    }

    public void SignBuyer(DealUiHolder uiHolder)
    {

    }

    public void SignSeller(DealUiHolder uiHolder)
    {

    }

    public void SignMediated(DealUiHolder uiHolder)
    {

    }

    public boolean updateAddress( Context app, String username, String address, String pubkey )
    {
        boolean ret = false;

        if ( username.equals("") || address.equals("") || pubkey.equals("") )
            return ret;

        String url = String.format("%s/updatePubAddress?username=%s&address=%s&publicKey=%s", URL_PLATFORM, username, address, pubkey);
        String jsonRes = urlSend( app, url, "POST");

        JSONArray jsonObject = null;
        if (jsonRes == null) {
            jsonRes = urlSend( app, url, "POST");   // retry
            if (jsonRes == null) {
                Log.e(TAG, "updatePubAddress: platform failed, response is null");
                return false;
            }
        }

        try {
            JSONObject obj = new JSONObject(jsonRes);
            boolean result = obj.getBoolean("result");
            String message = obj.getString("message");
            ret = result;
        } catch (JSONException ignored) {
        }

        return ret;
    }


    public static String urlSend(Context app, String myURL, String method) {
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
                .header("User-agent", Utils.getAgentString(app, "android/HttpURLConnection"));

        if (method == "GET")
        {
            builder.get();
        }
        else
        {
            RequestBody requestBody = RequestBody.create( MediaType.parse("application/json; charset=utf-8"), "");
            builder.post( requestBody  );
        }
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


    private class CustomLinearLayoutManager extends LinearLayoutManager {

        public CustomLinearLayoutManager(Context context) {
            super(context);
        }

        /**
         * Disable predictive animations. There is a bug in RecyclerView which causes views that
         * are being reloaded to pull invalid ViewHolders from the internal recycler stack if the
         * adapter size has decreased since the ViewHolder was recycled.
         */
        @Override
        public boolean supportsPredictiveItemAnimations() {
            return false;
        }

        public CustomLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
        }

        public CustomLinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }
    }

    private void crashIfNotMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalAccessError("Can only call from main thread");
        }
    }

}
