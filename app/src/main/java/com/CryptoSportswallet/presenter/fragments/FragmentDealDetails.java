package com.CryptoSportswallet.presenter.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.CryptoSportswallet.R;
import com.CryptoSportswallet.presenter.customviews.BRText;
import com.CryptoSportswallet.presenter.entities.CurrencyEntity;
import com.CryptoSportswallet.presenter.entities.DealUiHolder;
import com.CryptoSportswallet.presenter.entities.TxUiHolder;
import com.CryptoSportswallet.tools.manager.BRClipboardManager;
import com.CryptoSportswallet.tools.manager.BRSharedPrefs;
import com.CryptoSportswallet.tools.util.BRDateUtil;
import com.CryptoSportswallet.tools.util.CurrencyUtils;
import com.CryptoSportswallet.tools.util.Utils;
import com.CryptoSportswallet.wallet.WalletsMaster;
import com.CryptoSportswallet.wallet.abstracts.BaseWalletManager;
import com.platform.entities.TxMetaData;
import com.platform.tools.KVStoreManager;

import java.math.BigDecimal;

/**
 * Created by MIPPL on 10/15/18.
 * <p>
 * Reusable dialog fragment that display details about a particular deal
 */

public class FragmentDealDetails extends DialogFragment {

    private static final String EXTRA_TX_ITEM = "deal_item";
    private static final String TAG = "FragmentDealDetails";

    private DealUiHolder mDeal;

    private BRText mTxAction;
    private BRText mTxAmount;
    private BRText mTxStatus;
    private BRText mTxDate;
    private BRText mToFrom;
    private BRText mToFromAddress;
    private BRText mMemoText;

    private BRText mStartingBalance;
    private BRText mEndingBalance;
    private BRText mExchangeRate;
    private BRText mConfirmedInBlock;
    private BRText mDealId;
    private BRText mShowHide;
    private BRText mActionLink;
    private BRText mAmountWhenSent;
    private BRText mAmountNow;

    private ImageButton mCloseButton;
    private RelativeLayout mDetailsContainer;

    boolean mDetailsShowing = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.deal_details, container, false);

        mAmountNow = rootView.findViewById(R.id.amount_now);
        mAmountWhenSent = rootView.findViewById(R.id.amount_when_sent);
        mTxAction = rootView.findViewById(R.id.tx_action);
        mTxAmount = rootView.findViewById(R.id.tx_amount);

        mTxStatus = rootView.findViewById(R.id.tx_status);
        mTxDate = rootView.findViewById(R.id.tx_date);
        mToFrom = rootView.findViewById(R.id.tx_to_from);
        mToFromAddress = rootView.findViewById(R.id.tx_to_from_address);
        mMemoText = rootView.findViewById(R.id.memo);
        mStartingBalance = rootView.findViewById(R.id.tx_starting_balance);
        mEndingBalance = rootView.findViewById(R.id.tx_ending_balance);
        mExchangeRate = rootView.findViewById(R.id.exchange_rate);
        mConfirmedInBlock = rootView.findViewById(R.id.confirmed_in_block_number);
        mDealId = rootView.findViewById(R.id.transaction_id);
        mShowHide = rootView.findViewById(R.id.show_hide_details);
        mActionLink = rootView.findViewById(R.id.escrow_sign_deal);
        mDetailsContainer = rootView.findViewById(R.id.details_container);
        mCloseButton = rootView.findViewById(R.id.close_button);

        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        mShowHide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mDetailsShowing) {
                    mDetailsContainer.setVisibility(View.VISIBLE);
                    mDetailsShowing = true;
                    mShowHide.setText("Hide Details");
                } else {
                    mDetailsContainer.setVisibility(View.GONE);
                    mDetailsShowing = false;
                    mShowHide.setText("Show Details");
                }
            }
        });

        String text = (mDeal.PendingType==DealUiHolder.PendingTypeEnum.ESCROW)?"Send Escrow":"Sign Deal";
        mActionLink.setText(text);

        mActionLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDeal.DoActionForManager();
                dismiss();
            }
        });

        updateUi();
        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    public void setTransaction(DealUiHolder item) {

        this.mDeal = item;

    }

    private void updateUi() {

        BaseWalletManager walletManager = WalletsMaster.getInstance(getActivity()).getCurrentWallet(getActivity());
        // Set mTransction fields
        if (mDeal != null) {

            boolean sent = (mDeal.PendingType == DealUiHolder.PendingTypeEnum.ESCROW);
            String amountWhenSent;
            String amountNow;
            String exchangeRateFormatted;
/*
            if (!mDeal.isValid()) {
                mTxStatus.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
*/
             //user prefers crypto (or fiat)
            boolean isCryptoPreferred = BRSharedPrefs.isCryptoPreferred(getActivity());
            String cryptoIso = walletManager.getIso(getActivity());
            String fiatIso = BRSharedPrefs.getPreferredFiatIso(getContext());

            String iso = isCryptoPreferred ? cryptoIso : fiatIso;

            BigDecimal cryptoAmount = new BigDecimal(mDeal.EscrowAmount*100000000);

            BigDecimal fiatAmountNow = walletManager.getFiatForSmallestCrypto(getActivity(), cryptoAmount.abs(), null);

            BigDecimal fiatAmountWhenSent;
            fiatAmountWhenSent = new BigDecimal(0);
            amountWhenSent = CurrencyUtils.getFormattedAmount(getActivity(), fiatIso, fiatAmountWhenSent,8);//always fiat amount
            /*
            TxMetaData metaData = KVStoreManager.getInstance().getTxMetaData(getActivity(), mDeal.getTxHash());
            if (metaData == null || metaData.exchangeRate == 0 || Utils.isNullOrEmpty(metaData.exchangeCurrency)) {
                fiatAmountWhenSent = new BigDecimal(0);
                amountWhenSent = CurrencyUtils.getFormattedAmount(getActivity(), fiatIso, fiatAmountWhenSent);//always fiat amount
            } else {

                CurrencyEntity ent = new CurrencyEntity(metaData.exchangeCurrency, null, (float) metaData.exchangeRate, walletManager.getIso(getActivity()));
                fiatAmountWhenSent = walletManager.getFiatForSmallestCrypto(getActivity(), cryptoAmount.abs(), ent);
                amountWhenSent = CurrencyUtils.getFormattedAmount(getActivity(), ent.code, fiatAmountWhenSent);//always fiat amount

            }
*/

            amountNow = CurrencyUtils.getFormattedAmount(getActivity(), fiatIso, fiatAmountNow,8);//always fiat amount

            mAmountWhenSent.setText(amountWhenSent);
            mAmountNow.setText(amountNow);

            BigDecimal tmpStartingBalance = new BigDecimal(mDeal.EscrowAmount*100000000); //.subtract(cryptoAmount.abs()).subtract(new BigDecimal(mDeal.getFee()).abs());
            BigDecimal tmpEndingBalance = new BigDecimal(mDeal.EscrowAmount*100000000);

            BigDecimal startingBalance = isCryptoPreferred ? walletManager.getCryptoForSmallestCrypto(getActivity(), tmpStartingBalance) : walletManager.getFiatForSmallestCrypto(getActivity(), tmpStartingBalance, null);
            BigDecimal endingBalance = isCryptoPreferred ? walletManager.getCryptoForSmallestCrypto(getActivity(), tmpEndingBalance) : walletManager.getFiatForSmallestCrypto(getActivity(), tmpEndingBalance, null);

            mStartingBalance.setText(CurrencyUtils.getFormattedAmount(getActivity(), iso, startingBalance == null ? null : startingBalance.abs(),8));
            mEndingBalance.setText(CurrencyUtils.getFormattedAmount(getActivity(), iso, endingBalance == null ? null : endingBalance.abs(),8));

            mTxAction.setText(mDeal.getPendingTypeDescription());
            mToFrom.setText(mDeal.PendingType==DealUiHolder.PendingTypeEnum.ESCROW ? "To " : "Signed ");

            mToFromAddress.setText(walletManager.decorateAddress(getActivity(), mDeal.EscrowAddress));

            // Allow the to/from address to be copyable
            mToFromAddress.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    // Get the default color based on theme
                    final int color = mToFromAddress.getCurrentTextColor();

                    mToFromAddress.setTextColor(getContext().getColor(R.color.light_gray));
                    String address = mToFromAddress.getText().toString();
                    BRClipboardManager.putClipboard(getContext(), address);
                    Toast.makeText(getContext(), getString(R.string.Receive_copied), Toast.LENGTH_LONG).show();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mToFromAddress.setTextColor(color);

                        }
                    }, 200);


                }
            });

            mTxAmount.setText(CurrencyUtils.getFormattedAmount(getActivity(), walletManager.getIso(getActivity()), cryptoAmount,8));//this is always crypto amount


            if (!sent)
                mTxAmount.setTextColor(getContext().getColor(R.color.transaction_amount_received_color));

            mMemoText.setText("");

            // timestamp is 0 if it's not confirmed in a block yet so make it now
            //mTxDate.setText(BRDateUtil.getLongDate(mDeal.getTimeStamp() == 0 ? System.currentTimeMillis() : (mDeal.getTimeStamp() * 1000)));
            mTxDate.setText("0");

            // Set the deal id
            mDealId.setText(mDeal.DealId);

            // Allow the deal id to be copy-able
            mDealId.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    // Get the default color based on theme
                    final int color = mDealId.getCurrentTextColor();

                    mDealId.setTextColor(getContext().getColor(R.color.light_gray));
                    String id = mDeal.DealId;
                    BRClipboardManager.putClipboard(getContext(), id);
                    Toast.makeText(getContext(), getString(R.string.Receive_copied), Toast.LENGTH_LONG).show();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mDealId.setTextColor(color);

                        }
                    }, 200);
                }
            });

            // Set the transaction block number
            //mConfirmedInBlock.setText(String.valueOf(mDeal.getBlockHeight()));
            mConfirmedInBlock.setText("0");

        } else {

            Toast.makeText(getContext(), "Error getting transaction data", Toast.LENGTH_SHORT).show();
        }


    }

    @Override
    public void onResume() {
        super.onResume();

    }
}
