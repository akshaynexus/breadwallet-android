package com.cspnwallet.wallet.wallets.bitcoin;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import com.cspnwallet.BreadApp;
import com.cspnwallet.R;
import com.cspnwallet.core.BRCoreAddress;
import com.cspnwallet.core.BRCoreChainParams;
import com.cspnwallet.core.BRCoreKey;
import com.cspnwallet.core.BRCoreMasterPubKey;
import com.cspnwallet.core.BRCoreMerkleBlock;
import com.cspnwallet.core.BRCorePeer;
import com.cspnwallet.core.BRCorePeerManager;
import com.cspnwallet.core.BRCoreTransaction;
import com.cspnwallet.core.BRCoreWallet;
import com.cspnwallet.core.BRCoreWalletManager;
import com.cspnwallet.core.ethereum.BREthereumAmount;
import com.cspnwallet.presenter.customviews.BRToast;
import com.cspnwallet.presenter.entities.BRMerkleBlockEntity;
import com.cspnwallet.presenter.entities.BRPeerEntity;
import com.cspnwallet.presenter.entities.BRTransactionEntity;
import com.cspnwallet.presenter.entities.BlockEntity;
import com.cspnwallet.presenter.entities.CurrencyEntity;
import com.cspnwallet.presenter.entities.PeerEntity;
import com.cspnwallet.presenter.entities.TxUiHolder;
import com.cspnwallet.presenter.interfaces.BROnSignalCompletion;
import com.cspnwallet.tools.animation.UiUtils;
import com.cspnwallet.tools.animation.BRDialog;
import com.cspnwallet.tools.manager.BRApiManager;
import com.cspnwallet.tools.manager.BRNotificationManager;
import com.cspnwallet.tools.manager.BRReportsManager;
import com.cspnwallet.tools.manager.BRSharedPrefs;
import com.cspnwallet.tools.manager.InternetManager;
import com.cspnwallet.tools.sqlite.BtcBchTransactionDataStore;
import com.cspnwallet.tools.sqlite.MerkleBlockDataSource;
import com.cspnwallet.tools.sqlite.PeerDataSource;
import com.cspnwallet.tools.sqlite.RatesDataSource;
import com.cspnwallet.tools.sqlite.TransactionStorageManager;
import com.cspnwallet.tools.threads.executor.BRExecutor;
import com.cspnwallet.tools.util.BRConstants;
import com.cspnwallet.tools.util.CurrencyUtils;
import com.cspnwallet.tools.util.TypesConverter;
import com.cspnwallet.tools.util.Utils;
import com.cspnwallet.wallet.WalletsMaster;
import com.cspnwallet.wallet.abstracts.BaseWalletManager;
import com.cspnwallet.wallet.abstracts.BalanceUpdateListener;
import com.cspnwallet.wallet.abstracts.OnTxListModified;
import com.cspnwallet.wallet.abstracts.SyncListener;
import com.cspnwallet.wallet.configs.WalletSettingsConfiguration;
import com.cspnwallet.wallet.configs.WalletUiConfiguration;
import com.cspnwallet.wallet.wallets.CryptoAddress;
import com.cspnwallet.wallet.wallets.CryptoTransaction;
import com.cspnwallet.wallet.wallets.WalletManagerHelper;
import com.platform.entities.TxMetaData;
import com.platform.tools.KVStoreManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.cspnwallet.tools.util.BRConstants.ROUNDING_MODE;

public abstract class BaseBitcoinWalletManager extends BRCoreWalletManager implements BaseWalletManager {

    private static final String TAG = BaseBitcoinWalletManager.class.getSimpleName();

    public static final int ONE_BITCOIN_IN_SATOSHIS = 100000000; // 1 Crypto Sports in satoshis, 100 millions
    private static final long MAXIMUM_AMOUNT = 21000000; // Maximum number of coins available
    private static final int SYNC_MAX_RETRY = 2;

    public static final String BITCOIN_CURRENCY_CODE = "CSPN";
    public static final String BITCASH_CURRENCY_CODE = "BCH";

    private WalletSettingsConfiguration mSettingsConfig;

    private WalletManagerHelper mWalletManagerHelper;
    private int mSyncRetryCount = 0;
    private static final int CREATE_WALLET_MAX_RETRY = 3;
    private int mCreateWalletAllowedRetries = CREATE_WALLET_MAX_RETRY;
    private WalletUiConfiguration mUiConfig;

    private Executor mListenerExecutor = Executors.newSingleThreadExecutor();

    public enum RescanMode {
        FROM_BLOCK, FROM_CHECKPOINT, FULL
    }

    BaseBitcoinWalletManager(Context context, BRCoreMasterPubKey masterPubKey, BRCoreChainParams chainParams, double earliestPeerTime) {
        super(masterPubKey, chainParams, earliestPeerTime);
        mWalletManagerHelper = new WalletManagerHelper();

        Log.d(getTag(), "connectWallet:" + Thread.currentThread().getName());
        if (context == null) {
            Log.e(getTag(), "connectWallet: context is null");
            return;
        }
        String firstAddress = masterPubKey.getPubKeyAsCoreKey().address();
        BRSharedPrefs.putFirstAddress(context, firstAddress);

        mUiConfig = new WalletUiConfiguration("#c52e26", "#c52e26", true, WalletManagerHelper.MAX_DECIMAL_PLACES_FOR_UI);

    }

    protected abstract String getTag();

    protected abstract String getColor();

    protected abstract List<BigDecimal> getFingerprintLimits(Context context);

    protected BRCoreWallet createWalletRetry() {
        Context app = BreadApp.getBreadContext();
        if (0 == mCreateWalletAllowedRetries) {
            // The app is dead - tell the user...
            BRDialog.showSimpleDialog(app, "Wallet error!", "please contact support@breadwallet.com");
            // ... for now just this.  App crashes after this
            return null;
        }

        mCreateWalletAllowedRetries--;

        // clear out the SQL data - ensure that loadTransaction returns an empty array
        // mark this Manager a needing a sync.
        BtcBchTransactionDataStore.getInstance(app).deleteAllTransactions(app, getIso());
        BRReportsManager.reportBug(new RuntimeException("Wallet creation failed, after clearing tx size: " + loadTransactions().length));
        // Try again
        return createWallet();
    }


    @Override
    protected BRCoreWallet.Listener createWalletListener() {
        return new BRCoreWalletManager.WrappedExecutorWalletListener(
                super.createWalletListener(),
                mListenerExecutor);
    }

    @Override
    protected BRCorePeerManager.Listener createPeerManagerListener() {
        return new BRCoreWalletManager.WrappedExecutorPeerManagerListener(
                super.createPeerManagerListener(),
                mListenerExecutor);
    }

    @Override
    public CryptoTransaction[] getTxs(Context app) {
        BRCoreTransaction[] txs = getWallet().getTransactions();
        CryptoTransaction[] arr = new CryptoTransaction[txs.length];
        for (int i = 0; i < txs.length; i++) {
            arr[i] = new CryptoTransaction(txs[i]);
        }

        return arr;
    }

    public void setSettingsConfig(WalletSettingsConfiguration settingsConfig) {
        this.mSettingsConfig = settingsConfig;
    }

    @Override
    public BigDecimal getTxFee(CryptoTransaction tx) {
        return new BigDecimal(getWallet().getTransactionFee(tx.getCoreTx()));
    }

    @Override
    public BigDecimal getEstimatedFee(BigDecimal amount, String address) {
        BigDecimal fee;
        if (amount == null) {
            return null;
        }
        if (amount.longValue() == 0) {
            fee = BigDecimal.ZERO;
        } else {
            CryptoTransaction tx = null;
            if (isAddressValid(address)) {
                tx = createTransaction(amount, address);
            }

            if (tx == null) {
                fee = new BigDecimal(getWallet().getFeeForTransactionAmount(amount.longValue()));
            } else {
                fee = getTxFee(tx);
                if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
                    fee = new BigDecimal(getWallet().getFeeForTransactionAmount(amount.longValue()));
                }
            }
        }
        return fee;
    }

    @Override
    public BigDecimal getFeeForTransactionSize(BigDecimal size) {
        return new BigDecimal(getWallet().getFeeForTransactionSize(size.longValue()));
    }

    @Override
    public String getTxAddress(CryptoTransaction tx) {
        return getWallet().getTransactionAddress(tx.getCoreTx()).stringify();
    }

    @Override
    public BigDecimal getMaxOutputAmount(Context app) {
        return new BigDecimal(getWallet().getMaxOutputAmount());
    }

    @Override
    public BigDecimal getMinOutputAmount(Context app) {
        return new BigDecimal(getWallet().getMinOutputAmount());
    }

    @Override
    public BigDecimal getTransactionAmount(CryptoTransaction tx) {
        return new BigDecimal(getWallet().getTransactionAmount(tx.getCoreTx()));
    }

    @Override
    public BigDecimal getMinOutputAmountPossible() {
        return new BigDecimal(BRCoreTransaction.getMinOutputAmount());
    }

    @Override
    public void updateFee(Context app) {
        if (app == null) {
            app = BreadApp.getBreadContext();
            if (app == null) {
                Log.e(getTag(), "updateFee: FAILED, app is null");
                return;
            }
        }
        String jsonString = BRApiManager.urlGET(app, "https://" + BreadApp.HOST + "/fee-per-kb?currency=" + getIso());
        if (jsonString == null || jsonString.isEmpty()) {
            Log.e(getTag(), "updateFeePerKb: failed to update fee, response string: " + jsonString);
            return;
        }
        BigDecimal fee;
        BigDecimal economyFee;
        try {
            JSONObject obj = new JSONObject(jsonString);
            fee = new BigDecimal(obj.getString("fee_per_kb"));
            economyFee = new BigDecimal(obj.getString("fee_per_kb_economy"));
            Log.d(getTag(), "updateFee: " + getIso() + ":" + fee + "|" + economyFee);

            if (fee.compareTo(BigDecimal.ZERO) > 0 && fee.compareTo(new BigDecimal(getWallet().getMaxFeePerKb())) < 0) {
                BRSharedPrefs.putFeeRate(app, getIso(), fee);
                boolean favorStandardFee = BRSharedPrefs.getFavorStandardFee(app, getIso());
                getWallet().setFeePerKb(favorStandardFee ? fee.longValue() : economyFee.longValue());
                BRSharedPrefs.putFeeTime(app, getIso(), System.currentTimeMillis()); //store the time of the last successful fee fetch
            } else {
                BRReportsManager.reportBug(new NullPointerException("Fee is weird:" + fee));
            }
            if (economyFee.compareTo(BigDecimal.ZERO) > 0 && economyFee.compareTo(new BigDecimal(getWallet().getMaxFeePerKb())) < 0) {
                BRSharedPrefs.putEconomyFeeRate(app, getIso(), economyFee);
            } else {
                BRReportsManager.reportBug(new NullPointerException("Economy fee is weird:" + economyFee));
            }
        } catch (JSONException e) {
            Log.e(getTag(), "updateFeePerKb: FAILED: " + jsonString, e);
            BRReportsManager.reportBug(e);
            BRReportsManager.reportBug(new IllegalArgumentException("JSON ERR: " + jsonString));
        }
    }

    @Override
    public List<TxUiHolder> getTxUiHolders(Context app) {
        BRCoreTransaction[] txs = getWallet().getTransactions();
        if (txs == null || txs.length <= 0) {
            return null;
        }
        List<TxUiHolder> uiTxs = new ArrayList<>();
        for (int i = txs.length - 1; i >= 0; i--) { //revere order
            BRCoreTransaction tx = txs[i];
            String toAddress = null;
            //if sent
            if (getWallet().getTransactionAmount(tx) < 0) {
                toAddress = tx.getOutputAddresses()[0];
            } else {
                for (String to : tx.getOutputAddresses()) {
                    if (containsAddress(to)) {
                        toAddress = to;
                        break;
                    }
                }
            }
            if (toAddress == null) {
                throw new NullPointerException("Failed to retrieve toAddress");
            }
            boolean isReceived = getWallet().getTransactionAmount(tx) > 0;
            if (!isReceived) {
                //store the latest send transaction blockheight
                BRSharedPrefs.putLastSendTransactionBlockheight(app, getIso(), tx.getBlockHeight());
            }
            else{
                if(!(BRSharedPrefs.getLastReceiveTransactionBlockheight(app,getIso()) > 0))
                BRSharedPrefs.putLastReceiveTransactionBlockheight(app, getIso(), tx.getBlockHeight());
                Log.i("RECV","HEIGHTRECv : " + tx.getBlockHeight());

            }
            uiTxs.add(new TxUiHolder(tx, isReceived, tx.getTimestamp(), (int) tx.getBlockHeight(), tx.getHash(),
                    tx.getReverseHash(), new BigDecimal(getWallet().getTransactionFee(tx)),
                    toAddress, tx.getInputAddresses()[0],
                    new BigDecimal(getWallet().getBalanceAfterTransaction(tx)), (int) tx.getSize(),
                    new BigDecimal(getWallet().getTransactionAmount(tx)), getWallet().transactionIsValid(tx)));
        }

        return uiTxs;
    }

    @Override
    public boolean containsAddress(String address) {
        return !Utils.isNullOrEmpty(address) && getWallet().containsAddress(new BRCoreAddress(address));
    }

    @Override
    public boolean addressIsUsed(String address) {
        return !Utils.isNullOrEmpty(address) && getWallet().addressIsUsed(new BRCoreAddress(address));
    }

    @Override
    public boolean generateWallet(Context app) {
        //no need, one key for all wallets so far
        return true;
    }

    @Override
    public String getSymbol(Context app) {

        String currencySymbolString = BRConstants.BITS_SYMBOL;
        if (app != null) {
            int unit = BRSharedPrefs.getCryptoDenomination(app, getIso());
            switch (unit) {
                case BRConstants.CURRENT_UNIT_BITS:
                    currencySymbolString = "μ" + getIso();
                    break;
                case BRConstants.CURRENT_UNIT_MBITS:
                    currencySymbolString = "m" + getIso();
                    break;
                case BRConstants.CURRENT_UNIT_BITCOINS:
                    currencySymbolString = getIso();
                    break;
            }
        }
        return currencySymbolString;
    }

    @Override
    public abstract String getIso();

    @Override
    public abstract String getScheme();

    @Override
    public abstract String getName();

    @Override
    public String getDenominator() {
        return String.valueOf(ONE_BITCOIN_IN_SATOSHIS);
    }

    @Override
    public CryptoAddress getReceiveAddress(Context app) {
        BRCoreAddress addr = getWallet().getReceiveAddress();
        return new CryptoAddress(addr.stringify(), addr);
    }

    @Override
    public CryptoTransaction createTransaction(BigDecimal amount, String address) {
        if (Utils.isNullOrEmpty(address)) {
            Log.e(getTag(), "createTransaction: can't create, address is null");
            return null;
        }
        BRCoreTransaction tx = getWallet().createTransaction(amount.longValue(), new BRCoreAddress(address));
        return tx == null ? null : new CryptoTransaction(tx);
    }

    @Override
    public abstract String decorateAddress(String address);

    @Override
    public abstract String undecorateAddress(String address);

    @Override
    public WalletSettingsConfiguration getSettingsConfiguration() {
        return mSettingsConfig;
    }

    @Override
    public int getMaxDecimalPlaces(Context app) {
        int unit = BRSharedPrefs.getCryptoDenomination(app, getIso());
        switch (unit) {
            case BRConstants.CURRENT_UNIT_BITS:
                return 2;
            default:
                return WalletManagerHelper.MAX_DECIMAL_PLACES;
        }
    }

    @Override
    public BigDecimal getBalance() {
        return new BigDecimal(getWallet().getBalance());
    }


    @Override
    public BigDecimal getTotalSent(Context app) {
        return new BigDecimal(getWallet().getTotalSent());
    }
    @Override
    public BigDecimal getTotalRecived(Context app) {
        return new BigDecimal(getWallet().getTotalReceived());
    }
    @Override
    public void wipeData(Context app) {
        BtcBchTransactionDataStore.getInstance(app).deleteAllTransactions(app, getIso());
        MerkleBlockDataSource.getInstance(app).deleteAllBlocks(app, getIso());
        PeerDataSource.getInstance(app).deleteAllPeers(app, getIso());
        BRSharedPrefs.clearAllPrefs(app);
    }

    @Override
    public void refreshCachedBalance(Context context) {
        BigDecimal balance = new BigDecimal(getWallet().getBalance());
        onBalanceChanged(context, balance);
    }

    @Override
    public BigDecimal getMaxAmount(Context app) {
        return new BigDecimal(MAXIMUM_AMOUNT);
    }

    @Override
    public WalletUiConfiguration getUiConfiguration() {
        return mUiConfig;
    }

    @Override
    public BigDecimal getFiatExchangeRate(Context app) {
        CurrencyEntity ent = RatesDataSource.getInstance(app).getCurrencyByCode(app, getIso(), BRSharedPrefs.getPreferredFiatIso(app));
        if (ent == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(ent.rate); //dollars
    }

    @Override
    public BigDecimal getFiatBalance(Context app) {
        if (app == null) {
            return null;
        }
        BigDecimal balance = getFiatForSmallestCrypto(app, getBalance(), null);
        if (balance == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(balance.doubleValue());
    }

    @Override
    public BigDecimal getFiatForSmallestCrypto(Context app, BigDecimal amount, CurrencyEntity ent) {
        if (amount.doubleValue() == 0) {
            return amount;
        }
        String iso = BRSharedPrefs.getPreferredFiatIso(app);
        if (ent == null) {
            ent = RatesDataSource.getInstance(app).getCurrencyByCode(app, getIso(), iso);
        }
        if (ent == null) {
            return null;
        }
        double rate = ent.rate;
        //get crypto amount
        BigDecimal cryptoAmount = amount.divide(new BigDecimal(ONE_BITCOIN_IN_SATOSHIS), getMaxDecimalPlaces(app), BRConstants.ROUNDING_MODE);
        return cryptoAmount.multiply(new BigDecimal(rate));
    }

    @Override
    public BigDecimal getCryptoForFiat(Context app, BigDecimal fiatAmount) {
        if (fiatAmount.doubleValue() == 0) {
            return fiatAmount;
        }
        String iso = BRSharedPrefs.getPreferredFiatIso(app);
        CurrencyEntity ent = RatesDataSource.getInstance(app).getCurrencyByCode(app, getIso(), iso);
        if (ent == null) {
            return null;
        }
        double rate = ent.rate;
        //convert c to $.
        int unit = BRSharedPrefs.getCryptoDenomination(app, getIso());
        BigDecimal result = BigDecimal.ZERO;
        switch (unit) {
            case BRConstants.CURRENT_UNIT_BITS:
                result = fiatAmount.divide(new BigDecimal(rate), 2, ROUNDING_MODE).multiply(new BigDecimal("1000000"));
                break;
            case BRConstants.CURRENT_UNIT_BITCOINS:
                result = fiatAmount.divide(new BigDecimal(rate), getMaxDecimalPlaces(app), ROUNDING_MODE);
                break;
        }
        return result;

    }

    @Override
    public BigDecimal getCryptoForSmallestCrypto(Context app, BigDecimal amount) {
        if (amount.doubleValue() == 0) return amount;
        BigDecimal result = BigDecimal.ZERO;
        int unit = BRSharedPrefs.getCryptoDenomination(app, getIso());
        switch (unit) {
            case BRConstants.CURRENT_UNIT_BITS:
                result = amount.divide(new BigDecimal("100"), 2, ROUNDING_MODE);
                break;
            case BRConstants.CURRENT_UNIT_MBITS:
                result = amount.divide(new BigDecimal("100000"), 5, ROUNDING_MODE);
                break;
            case BRConstants.CURRENT_UNIT_BITCOINS:
                result = amount.divide(new BigDecimal("100000000"), 8, ROUNDING_MODE);
                break;
        }
        return result;
    }

    @Override
    public BigDecimal getSmallestCryptoForCrypto(Context app, BigDecimal amount) {
        if (amount.doubleValue() == 0) return amount;
        BigDecimal result = BigDecimal.ZERO;
        int unit = BRSharedPrefs.getCryptoDenomination(app, getIso());
        switch (unit) {
            case BRConstants.CURRENT_UNIT_BITS:
                result = amount.multiply(new BigDecimal("100"));
                break;
            case BRConstants.CURRENT_UNIT_MBITS:
                result = amount.multiply(new BigDecimal("100000"));
                break;
            case BRConstants.CURRENT_UNIT_BITCOINS:
                result = amount.multiply(new BigDecimal("100000000"));
                break;
        }
        return result;
    }

    @Override
    public BigDecimal getSmallestCryptoForFiat(Context app, BigDecimal amount) {
        if (amount.doubleValue() == 0) return amount;
        String iso = BRSharedPrefs.getPreferredFiatIso(app);
        CurrencyEntity ent = RatesDataSource.getInstance(app).getCurrencyByCode(app, getIso(), iso);
        if (ent == null) {
            Log.e(getTag(), "getSmallestCryptoForFiat: no exchange rate data!");
            return amount;
        }
        double rate = ent.rate;
        //convert c to $.
        return amount.divide(new BigDecimal(rate), 8, ROUNDING_MODE).multiply(new BigDecimal("100000000"));
    }

    // TODO only ETH and ERC20
    @Override
    public BREthereumAmount.Unit getUnit() {
        throw new RuntimeException("stub");
    }

    @Override
    public String getAddress(Context context) {
        return BRSharedPrefs.getReceiveAddress(context, getIso());
    }

    @Override
    public boolean isAddressValid(String address) {
        return !Utils.isNullOrEmpty(address) && new BRCoreAddress(address).isValid();
    }

    @Override
    public byte[] signAndPublishTransaction(CryptoTransaction tx, byte[] seed) {
        return super.signAndPublishTransaction(tx.getCoreTx(), seed);
    }

    // TODO only ETH and ERC20
    @Override
    public void watchTransactionForHash(CryptoTransaction tx, OnHashUpdated listener) {
    }

    @Override
    public long getRelayCount(byte[] txHash) {
        if (Utils.isNullOrEmpty(txHash)) return 0;
        return getPeerManager().getRelayCount(txHash);
    }

    @Override
    public double getSyncProgress(long startHeight) {
        return getPeerManager().getSyncProgress(startHeight);
    }

    @Override
    public double getConnectStatus() {
        BRCorePeer.ConnectStatus status = getPeerManager().getConnectStatus();
        if (status == BRCorePeer.ConnectStatus.Disconnected)
            return 0;
        else if (status == BRCorePeer.ConnectStatus.Connecting)
            return 1;
        else if (status == BRCorePeer.ConnectStatus.Connected)
            return 2;
        else if (status == BRCorePeer.ConnectStatus.Unknown)
            return 3;
        else
            throw new IllegalArgumentException();
    }

    @Override
    public void connect(Context app) {
        getPeerManager().connect();
    }

    @Override
    public void disconnect(Context app) {
        getPeerManager().disconnect();
    }

    @Override
    public boolean useFixedNode(String node, int port) {
        return false;
    }

    /**
     * The rescan now operates in 3 modes (Stage 1: Incremental rescan):
     * <p>
     * 1)The latest block in which a send transaction originating from the user's wallet was confirmed.
     * If there are no sends, only the following two starting points will be used.
     * 2)The latest block checkpoint that is hardcoded in the app.
     * 3)500 blocks before the the block height at which the wallet was created (from KV store),
     * or the first block after the introduction of BIP39 if there is no date stored in the KV store.
     *
     * @param app
     */
    @Override
    public void rescan(Context app) {
        //the last time the app has done a rescan (not a regular scan)
        long lastRescanTime = BRSharedPrefs.getLastRescanTime(app, getIso());
        long now = System.currentTimeMillis();
        //the last rescan mode that was used for rescan
        String lastRescanModeUsedValue = BRSharedPrefs.getLastRescanModeUsed(app, getIso());
        //the last successful send transaction's blockheight (if there is one, 0 otherwise)
        long lastSentTransactionBlockheight = BRSharedPrefs.getLastSendTransactionBlockheight(app, getIso());
        //was the rescan used within the last 24 hours
        boolean wasLastRescanWithin24h = now - lastRescanTime <= DateUtils.DAY_IN_MILLIS;
        rescan(app, RescanMode.FROM_CHECKPOINT);
    }

    @Override
    public void rescanX(Context app,boolean isRestored,boolean isFast) {
        //the last time the app has done a rescan (not a regular scan)
        long lastRescanTime = BRSharedPrefs.getLastRescanTime(app, getIso());
        long now = System.currentTimeMillis();
        //the last rescan mode that was used for rescan
        String lastRescanModeUsedValue = BRSharedPrefs.getLastRescanModeUsed(app, getIso());
        //the last successful recv transaction's blockheight (if there is one, 0 otherwise)
        long lastRecvTransactionBlockheight = BRSharedPrefs.getLastReceiveTransactionBlockheight(app, getIso());
        if(lastRecvTransactionBlockheight == 0 || lastRecvTransactionBlockheight == 292641){lastRecvTransactionBlockheight =400427; }

        //was the rescan used within the last 24 hours
        boolean wasLastRescanWithin24h = now - lastRescanTime <= DateUtils.DAY_IN_MILLIS;
if(isFast){rescan(app,RescanMode.FROM_CHECKPOINT);}
        else if (wasLastRescanWithin24h && isRestored) {
                rescan(app, RescanMode.FROM_BLOCK);
        } else {
            if (lastRecvTransactionBlockheight > 0) {
                rescan(app, RescanMode.FROM_BLOCK);
            } else {
                rescan(app, RescanMode.FROM_CHECKPOINT);
            }
        }
    }
    /**
     * Trigger the appropriate rescan and save the name and time to BRSharedPrefs
     *
     * @param app  android context to use
     * @param mode the RescanMode to be used
     */
    private void rescan(final Context app, RescanMode mode) {
        if (RescanMode.FROM_BLOCK == mode) {
            final long lastRecvTransactionBlockheight = BRSharedPrefs.getLastReceiveTransactionBlockheight(app, getIso());
            Log.d(TAG, "rescan -> with last block: " + lastRecvTransactionBlockheight);

            Thread thread = new Thread(new Runnable() {

                @Override
                public void run() {
                    try  {
                        long trialblockx;
                        String blockhashX;

                        trialblockx = (lastRecvTransactionBlockheight == 0 || lastRecvTransactionBlockheight == 292641) ? 400427 : lastRecvTransactionBlockheight;
                        //Your code goes here
                         blockhashX = BRApiManager.urlGET(app,"http://chain.cspn.io/api/getblockhash?height=" + trialblockx);
                        Log.i("Goethash",blockhashX);
                        BRSharedPrefs.putLastBlockhash(app,getIso(),blockhashX);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            thread.start();
            String lastBlockhash = BRSharedPrefs.getLastBlockhash(app,getIso());
            if(lastBlockhash != ""){getPeerManager().rescanFromBlockHash(lastBlockhash);}
            else
            getPeerManager().rescanFromBlock(lastRecvTransactionBlockheight);

        } else if (RescanMode.FROM_CHECKPOINT == mode) {
            Log.e(TAG, "rescan -> from checkpoint");
            getPeerManager().rescanFromCheckPoint();
        } else if (RescanMode.FULL == mode) {
            Log.e(TAG, "rescan -> full");
            getPeerManager().rescan();
        } else {
            throw new IllegalArgumentException("RescanMode is invalid, mode -> " + mode);
        }
        long now = System.currentTimeMillis();
        BRSharedPrefs.putLastRescanModeUsed(app, getIso(), mode.name());
        BRSharedPrefs.putLastRescanTime(app, getIso(), now);
    }

    /**
     * @param mode       the RescanMode enum to compare to
     * @param stringMode the stored enum value
     * @return true if the same mode
     */
    private boolean isModeSame(RescanMode mode, String stringMode) {
        if (stringMode == null) {
            //prevent NPE
            stringMode = "";
        }
        try {
            if (mode == RescanMode.valueOf(stringMode)) {
                return true;
            }
        } catch (IllegalArgumentException ex) {
            //do nothing, illegal argument
        }
        return false;

    }


    public void txPublished(final String error) {
        super.txPublished(error);
        final Context app = BreadApp.getBreadContext();
        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
            @Override
            public void run() {
                if (app instanceof Activity)
                    UiUtils.showBreadSignal((Activity) app, Utils.isNullOrEmpty(error) ? app.getString(R.string.Alerts_sendSuccess) : app.getString(R.string.Alert_error),
                            Utils.isNullOrEmpty(error) ? app.getString(R.string.Alerts_sendSuccessSubheader) : "Error: " + error, Utils.isNullOrEmpty(error) ? R.drawable.ic_check_mark_white : R.drawable.ic_error_outline_black_24dp, new BROnSignalCompletion() {
                                @Override
                                public void onComplete() {
                                    if (!((Activity) app).isDestroyed())
                                        ((Activity) app).getFragmentManager().popBackStack();
                                }
                            });

            }
        });

    }

    public void saveBlocks(boolean replace, BRCoreMerkleBlock[] blocks) {
        super.saveBlocks(replace, blocks);

        Context app = BreadApp.getBreadContext();
        if (app == null) return;
        if (replace) MerkleBlockDataSource.getInstance(app).deleteAllBlocks(app, getIso());
        BlockEntity[] entities = new BlockEntity[blocks.length];
        for (int i = 0; i < entities.length; i++) {
            entities[i] = new BlockEntity(blocks[i].serialize(), (int) blocks[i].getHeight());
        }

        MerkleBlockDataSource.getInstance(app).putMerkleBlocks(app, getIso(), entities);
    }

    public void savePeers(boolean replace, BRCorePeer[] peers) {
        super.savePeers(replace, peers);
        Context app = BreadApp.getBreadContext();
        if (app == null) return;
        if (replace) PeerDataSource.getInstance(app).deleteAllPeers(app, getIso());
        PeerEntity[] entities = new PeerEntity[peers.length];
        for (int i = 0; i < entities.length; i++) {
            entities[i] = new PeerEntity(peers[i].getAddress(), TypesConverter.intToBytes(peers[i].getPort()), TypesConverter.long2byteArray(peers[i].getTimestamp()));
        }
        PeerDataSource.getInstance(app).putPeers(app, getIso(), entities);

    }

    public boolean networkIsReachable() {
        Context app = BreadApp.getBreadContext();
        return InternetManager.getInstance().isConnected(app);
    }

    public BRCoreTransaction[] loadTransactions() {
        Context app = BreadApp.getBreadContext();

        List<BRTransactionEntity> txs = BtcBchTransactionDataStore.getInstance(app).getAllTransactions(app, getIso());
        if (txs == null || txs.size() == 0) return new BRCoreTransaction[0];
        BRCoreTransaction arr[] = new BRCoreTransaction[txs.size()];
        for (int i = 0; i < txs.size(); i++) {
            BRTransactionEntity ent = txs.get(i);
            try {
                arr[i] = new BRCoreTransaction(ent.getBuff(), ent.getBlockheight(), ent.getTimestamp());
            }
            catch (BRCoreTransaction.FailedToParse e){
                e.printStackTrace();
            }
        }
        return arr;
    }

    public BRCoreMerkleBlock[] loadBlocks() {
        Context app = BreadApp.getBreadContext();
        List<BRMerkleBlockEntity> blocks = MerkleBlockDataSource.getInstance(app).getAllMerkleBlocks(app, getIso());
        if (blocks == null || blocks.size() == 0) return new BRCoreMerkleBlock[0];
        BRCoreMerkleBlock arr[] = new BRCoreMerkleBlock[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            BRMerkleBlockEntity ent = blocks.get(i);
            arr[i] = new BRCoreMerkleBlock(ent.getBuff(), ent.getBlockHeight());
        }
        return arr;
    }

    public BRCorePeer[] loadPeers() {
        Context app = BreadApp.getBreadContext();
        List<BRPeerEntity> peers = PeerDataSource.getInstance(app).getAllPeers(app, getIso());
        if (peers == null || peers.size() == 0) return new BRCorePeer[0];
        BRCorePeer arr[] = new BRCorePeer[peers.size()];
        for (int i = 0; i < peers.size(); i++) {
            BRPeerEntity ent = peers.get(i);
            arr[i] = new BRCorePeer(ent.getAddress(), TypesConverter.bytesToInt(ent.getPort()), TypesConverter.byteArray2long(ent.getTimeStamp()));
        }
        return arr;
    }

    @Override
    public int getForkId() {
        return super.getForkId();
    }

    @Override
    public void addBalanceChangedListener(BalanceUpdateListener listener) {
        mWalletManagerHelper.addBalanceChangedListener(listener);
    }
    @Override
    public void removeBalanceChangedListener(BalanceUpdateListener listener) {
        mWalletManagerHelper.removeBalanceChangedListener(listener);
    }

    @Override
    public void onBalanceChanged(Context context, BigDecimal balance) {
        mWalletManagerHelper.onBalanceChanged(context, getIso(), balance);
    }

    protected void updateCachedAddress(Context context, String address) {
        if (Utils.isNullOrEmpty(address)) {
            Log.e(getTag(), "refreshAddress: WARNING, retrieved address:" + address);
        }
        BRSharedPrefs.putReceiveAddress(context, address, getIso());
    }

    @Override
    public void addSyncListener(SyncListener listener) {
        mWalletManagerHelper.addSyncListener(listener);
    }

    @Override
    public void removeSyncListener(SyncListener listener) {
        mWalletManagerHelper.removeSyncListener(listener);
    }

    public void onSyncStarted() {
        mWalletManagerHelper.onSyncStarted();
    }

    public void onSyncStopped(String error) {
        mWalletManagerHelper.onSyncStopped(error);
    }

    @Override
    public void addTxListModifiedListener(OnTxListModified listener) {
        mWalletManagerHelper.addTxListModifiedListener(listener);
    }

    public void onTxListModified(String hash) {
        mWalletManagerHelper.onTxListModified(hash);
    }

    /**
     * Core callback for balance updates.
     * @param balance
     */
    public void balanceChanged(final long balance) {
        super.balanceChanged(balance);
        final Context context = BreadApp.getBreadContext();
        onBalanceChanged(context, new BigDecimal(balance));
        refreshAddress(context);
    }

    public void txStatusUpdate() {
        super.txStatusUpdate();
        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
            @Override
            public void run() {
                onTxListModified(null);
            }
        });

        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                long blockHeight = getPeerManager().getLastBlockHeight();

                final Context context = BreadApp.getBreadContext();
                if (context != null) {
                    BRSharedPrefs.putLastBlockHeight(context, getIso(), (int) blockHeight);
                }
            }
        });
    }

    public void syncStarted() {
        super.syncStarted();
        Log.d(getTag(), "syncStarted: ");
        final Context app = BreadApp.getBreadContext();
        if (Utils.isEmulatorOrDebug(app)) {
            BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(app, "syncStarted " + getIso(), Toast.LENGTH_LONG).show();
                }
            });
        }

        onSyncStarted();
    }

    protected abstract void syncStopped(Context context);

    public void syncStopped(final String error) {
        super.syncStopped(error);
        Log.d(getTag(), "syncStopped: " + error);
        final Context context = BreadApp.getBreadContext();
        if (Utils.isNullOrEmpty(error)) {
            BRSharedPrefs.putAllowSpend(context, getIso(), true);
            syncStopped(context);
        }

        onSyncStopped(error);

        if (Utils.isEmulatorOrDebug(context)) {
            BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "SyncStopped " + getIso() + " err(" + error + ") ", Toast.LENGTH_LONG).show();
                }
            });
        }

        if (!Utils.isNullOrEmpty(error)) {
            if (mSyncRetryCount < SYNC_MAX_RETRY) {
                Log.e(getTag(), "syncStopped: Retrying: " + mSyncRetryCount);
                //Retry
                mSyncRetryCount++;
                BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        getPeerManager().connect();
                    }
                });

            } else {
                //Give up
                Log.e(getTag(), "syncStopped: Giving up: " + mSyncRetryCount);
                mSyncRetryCount = 0;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "Syncing failed, retried " + SYNC_MAX_RETRY + " times.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }

    }

    public void onTxAdded(BRCoreTransaction transaction) {
        super.onTxAdded(transaction);
        final Context ctx = BreadApp.getBreadContext();
        final WalletsMaster master = WalletsMaster.getInstance(ctx);

        TxMetaData metaData = KVStoreManager.createMetadata(ctx, this, new CryptoTransaction(transaction));
        KVStoreManager.putTxMetaData(ctx, metaData, transaction.getHash());

        final long amount = getWallet().getTransactionAmount(transaction);
        if (amount > 0) {
            BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                @Override
                public void run() {
                    String am = CurrencyUtils.getFormattedAmount(ctx, getIso(), getCryptoForSmallestCrypto(ctx, new BigDecimal(amount)));
                    BigDecimal bigAmount = master.getCurrentWallet(ctx).getFiatForSmallestCrypto(ctx, new BigDecimal(amount), null);
                    String amCur = CurrencyUtils.getFormattedAmount(ctx, BRSharedPrefs.getPreferredFiatIso(ctx), bigAmount == null ? BigDecimal.ZERO : bigAmount);
                    String formatted = String.format("%s (%s)", am, amCur);
                    final String strToShow = String.format(ctx.getString(R.string.TransactionDetails_received), formatted);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!BRToast.isToastShown()) {
                                if (Utils.isEmulatorOrDebug(ctx))
                                    BRToast.showCustomToast(ctx, strToShow,
                                            BreadApp.mDisplayHeightPx / 2, Toast.LENGTH_LONG, R.drawable.toast_layout_black);
                                AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                                if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                                    final MediaPlayer mp = MediaPlayer.create(ctx, R.raw.coinflip);
                                    if (mp != null) try {
                                        mp.start();
                                    } catch (IllegalArgumentException ex) {
                                        Log.e(getTag(), "run: ", ex);
                                    }
                                }
                                if (ctx instanceof Activity && BRSharedPrefs.getShowNotification(ctx))
                                    BRNotificationManager.sendNotification((Activity) ctx, R.drawable.notification_icon, ctx.getString(R.string.app_name), strToShow, 1);
                                else
                                    Log.e(getTag(), "onTxAdded: ctx is not activity");
                            }
                        }
                    }, DateUtils.SECOND_IN_MILLIS);


                }
            });
        }
        if (ctx != null)
            TransactionStorageManager.putTransaction(ctx, getIso(), new BRTransactionEntity(transaction.serialize(), transaction.getBlockHeight(), transaction.getTimestamp(), BRCoreKey.encodeHex(transaction.getHash()), getIso()));
        else
            Log.e(getTag(), "onTxAdded: ctx is null!");

        onTxListModified(transaction.getReverseHash());
    }

    public void onTxDeleted(final String hash, int notifyUser, int recommendRescan) {
        super.onTxDeleted(hash, notifyUser, recommendRescan);
        Log.e(getTag(), "onTxDeleted: " + String.format("hash: %s, notifyUser: %d, recommendRescan: %d", hash, notifyUser, recommendRescan));
        final Context ctx = BreadApp.getBreadContext();
        if (ctx != null) {
            if (recommendRescan != 0)
                BRSharedPrefs.putScanRecommended(ctx, getIso(), true);
            if (notifyUser != 0)
                BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        BRDialog.showSimpleDialog(ctx, "Transaction failed!", hash);
                    }
                });
            TransactionStorageManager.removeTransaction(ctx, getIso(), hash);
        } else {
            Log.e(getTag(), "onTxDeleted: Failed! ctx is null");
        }
        onTxListModified(hash);
    }

    public void onTxUpdated(String hash, int blockHeight, int timeStamp) {
        super.onTxUpdated(hash, blockHeight, timeStamp);
        Log.d(getTag(), "onTxUpdated: " + String.format("hash: %s, blockHeight: %d, timestamp: %d", hash, blockHeight, timeStamp));
        Context ctx = BreadApp.getBreadContext();
        if (ctx != null) {
            TransactionStorageManager.updateTransaction(ctx, getIso(), new BRTransactionEntity(null, blockHeight, timeStamp, hash, getIso()));

        } else {
            Log.e(getTag(), "onTxUpdated: Failed, ctx is null");
        }
        onTxListModified(hash);
    }

}
