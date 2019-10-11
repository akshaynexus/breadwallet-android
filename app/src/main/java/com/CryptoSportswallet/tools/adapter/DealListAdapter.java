package com.CryptoSportswallet.tools.adapter;

import android.app.Activity;
import android.content.Context;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.CryptoSportswallet.R;
import com.CryptoSportswallet.presenter.customviews.BRText;
import com.CryptoSportswallet.presenter.entities.DealUiHolder;
import com.CryptoSportswallet.tools.manager.BRSharedPrefs;
import com.CryptoSportswallet.tools.threads.executor.BRExecutor;
import com.CryptoSportswallet.tools.util.BRDateUtil;
import com.CryptoSportswallet.tools.util.CurrencyUtils;
import com.CryptoSportswallet.tools.util.Utils;
import com.CryptoSportswallet.wallet.WalletsMaster;
import com.CryptoSportswallet.wallet.abstracts.BaseWalletManager;
import com.platform.tools.KVStoreManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * BreadWallet
 * <p>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 7/27/15.
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

public class DealListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final String TAG = DealListAdapter.class.getName();

    private final Context mContext;
    private final int txResId;
    private final int promptResId;
    private List<DealUiHolder> backUpFeed;
    private List<DealUiHolder> itemFeed;
    //    private Map<String, TxMetaData> mds;

    private final int dealType = 0;
    private final int promptType = 1;
    private boolean updatingReverseTxHash;
    private boolean updatingData;

//    private boolean updatingMetadata;

    public DealListAdapter(Context mContext, List<DealUiHolder> items) {
        this.txResId = R.layout.deal_item;
        this.promptResId = R.layout.prompt_item;
        this.mContext = mContext;
        items = new ArrayList<>();
        init(items);
//        updateMetadata();
    }

    public void setItems(List<DealUiHolder> items) {
        init(items);
    }

    private void init(List<DealUiHolder> items) {
        if (items == null) items = new ArrayList<>();
        if (itemFeed == null) itemFeed = new ArrayList<>();
        if (backUpFeed == null) backUpFeed = new ArrayList<>();
        this.itemFeed = items;
        this.backUpFeed = items;

    }

    public void updateData() {
        if (updatingData) return;
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                long s = System.currentTimeMillis();
                List<DealUiHolder> newItems = new ArrayList<>(itemFeed);
                DealUiHolder item;
                for (int i = 0; i < newItems.size(); i++) {
                    item = newItems.get(i);
                }
                backUpFeed = newItems;
                String log = String.format("newItems: %d, took: %d", newItems.size(), (System.currentTimeMillis() - s));
                Log.e(TAG, "updateData: " + log);
                updatingData = false;
            }
        });
    }

    public List<DealUiHolder> getItems() {
        return itemFeed;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // inflate the layout
        LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
        return new DealHolder(inflater.inflate(txResId, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case dealType:
                holder.setIsRecyclable(false);
                setTexts((DealHolder) holder, position);
                break;
            case promptType:
                //setPrompt((PromptHolder) holder);
                break;
        }

    }

    @Override
    public int getItemViewType(int position) {
        return dealType;
    }

    @Override
    public int getItemCount() {
        return itemFeed.size();
    }

    private void setTexts(final DealHolder convertView, int position) {
        BaseWalletManager wallet = WalletsMaster.getInstance(mContext).getCurrentWallet(mContext);
        DealUiHolder item = itemFeed.get(position);

        String commentString = "";


        boolean signature = (item.PendingType!=DealUiHolder.PendingTypeEnum.ESCROW );

        if (signature)
            convertView.dealAmount.setTextColor(mContext.getResources().getColor(R.color.transaction_amount_received_color, null));
        else
            convertView.dealAmount.setTextColor(mContext.getResources().getColor(R.color.total_assets_usd_color, null));

        BigDecimal cryptoAmount = new BigDecimal(item.EscrowAmount*100000000);  // expecting SATS
        Log.e(TAG, "setTexts: crypto:" + cryptoAmount);
        boolean isCryptoPreferred = BRSharedPrefs.isCryptoPreferred(mContext);
        String preferredIso = isCryptoPreferred ? wallet.getIso(mContext) : BRSharedPrefs.getPreferredFiatIso(mContext);

        BigDecimal amount = isCryptoPreferred ? cryptoAmount : wallet.getFiatForSmallestCrypto(mContext, cryptoAmount, null);
        Log.e(TAG, "setTexts: amount:" + amount);

        convertView.dealType.setText(item.JobTitle);
        convertView.dealAmount.setText(CurrencyUtils.getFormattedAmount(mContext, preferredIso, amount,8));
        convertView.dealDetail.setText(item.getPendingTypeDescription());

/*
        int blockHeight = item.getBlockHeight();
        int confirms = blockHeight == Integer.MAX_VALUE ? 0 : BRSharedPrefs.getLastBlockHeight(mContext, wallet.getIso(mContext)) - blockHeight + 1;

        int level = 0;
        if (confirms <= 0) {
            long relayCount = wallet.getPeerManager().getRelayCount(item.getTxHash());
            if (relayCount <= 0)
                level = 0;
            else if (relayCount == 1)
                level = 1;
            else
                level = 2;
        } else {
            if (confirms == 1)
                level = 3;
            else if (confirms == 2)
                level = 4;
            else if (confirms == 3)
                level = 5;
            else
                level = 6;
        }
        switch (level) {
            case 0:
                showTransactionProgress(convertView, 0);
                break;
            case 1:
                showTransactionProgress(convertView, 20);

                break;
            case 2:

                showTransactionProgress(convertView, 40);
                break;
            case 3:

                showTransactionProgress(convertView, 60);
                break;
            case 4:

                showTransactionProgress(convertView, 80);
                break;
            case 5:

                //showTransactionProgress(convertView, 100);
                break;
        }

        Log.d(TAG, "Level -> " + level);

        if (level > 4) {
            convertView.transactionDetail.setText(!commentString.isEmpty() ? commentString : (!received ? "sent to " : "received via ") + wallet.decorateAddress(mContext, item.getTo()[0]));
        } else {
            convertView.transactionDetail.setText(!commentString.isEmpty() ? commentString : (!received ? "sending to " : "receiving via ") + wallet.decorateAddress(mContext, item.getTo()[0]));

        }

        //if it's 0 we use the current time.
        long timeStamp = item.getTimeStamp() == 0 ? System.currentTimeMillis() : item.getTimeStamp() * 1000;

        String shortDate = BRDateUtil.getShortDate(timeStamp);

        convertView.transactionDate.setText(shortDate);
*/
    }

    public void filterBy(String query, boolean[] switches) {
        filter(query, switches);
    }

    public void resetFilter() {
        itemFeed = backUpFeed;
        notifyDataSetChanged();
    }

    private void filter(final String query, final boolean[] switches) {
        long start = System.currentTimeMillis();
        String lowerQuery = query.toLowerCase().trim();
        if (Utils.isNullOrEmpty(lowerQuery) && !switches[0] && !switches[1] && !switches[2] && !switches[3])
            return;
        int switchesON = 0;
        for (boolean i : switches) if (i) switchesON++;

        final List<DealUiHolder> filteredList = new ArrayList<>();
        DealUiHolder item;
        for (int i = 0; i < backUpFeed.size(); i++) {
            item = backUpFeed.get(i);
            boolean matchesDealId = item.DealId != null && item.DealId.contains(lowerQuery);
            boolean matchesAddress = item.EscrowAddress.contains(lowerQuery) || item.EscrowAddress.contains(lowerQuery);
            //boolean matchesMemo = item.metaData != null && item.metaData.comment != null && item.metaData.comment.toLowerCase().contains(lowerQuery);
            if (matchesDealId || matchesAddress ) {
                if (switchesON == 0) {
                    filteredList.add(item);
                } else {
                    boolean willAdd = true;
                    //filter by Escrow or Signature
                    if (switches[0] && (item.PendingType != DealUiHolder.PendingTypeEnum.ESCROW)) {
                        willAdd = false;
                    }
                    //filter by received and this is sent
                    if (switches[1] &&   item.PendingType != DealUiHolder.PendingTypeEnum.BUYER ) {
                        willAdd = false;
                    }

                    if (switches[2] &&   item.PendingType != DealUiHolder.PendingTypeEnum.SELLER ) {
                        willAdd = false;
                    }

                    if (switches[3] &&  item.PendingType != DealUiHolder.PendingTypeEnum.MEDIATED) {
                        willAdd = false;
                    }

                    if (willAdd) filteredList.add(item);
                }
            }

        }
        itemFeed = filteredList;
        notifyDataSetChanged();

        Log.e(TAG, "filter: " + query + " took: " + (System.currentTimeMillis() - start));
    }

    private class DealHolder extends RecyclerView.ViewHolder {
        public RelativeLayout mainLayout;
        public ConstraintLayout constraintLayout;
        public TextView DealId;
        public TextView EscrowAmount;
        public TextView JobTitle;
        public TextView ReceiverUserName;
        public TextView Type;
        public TextView MediatedPercentage;
        public ImageView arrowIcon;

        public BRText dealType;
        public BRText dealAmount;
        public BRText dealDetail;
        //public Button transactionFailed;
        //public ProgressBar transactionProgress;


        public DealHolder(View view) {
            super(view);

            dealType = view.findViewById(R.id.tx_type);
            dealAmount = view.findViewById(R.id.tx_amount);
            dealDetail = view.findViewById(R.id.tx_description);
            //transactionFailed = view.findViewById(R.id.tx_failed_button);
            //transactionProgress = view.findViewById(R.id.tx_progress);

        }
    }

}