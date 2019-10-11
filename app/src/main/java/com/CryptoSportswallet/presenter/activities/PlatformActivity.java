package com.CryptoSportswallet.presenter.activities;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.transition.TransitionManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.CryptoSportswallet.R;
import com.CryptoSportswallet.core.BRCoreAddress;
import com.CryptoSportswallet.core.BRCoreKey;
import com.CryptoSportswallet.core.BRCorePeer;
import com.CryptoSportswallet.presenter.activities.settings.WebViewActivity;
import com.CryptoSportswallet.presenter.activities.util.BRActivity;
import com.CryptoSportswallet.presenter.customviews.BRButton;
import com.CryptoSportswallet.presenter.customviews.BRNotificationBar;
import com.CryptoSportswallet.presenter.customviews.BRSearchPlatformBar;
import com.CryptoSportswallet.presenter.customviews.BRText;
import com.CryptoSportswallet.tools.animation.BRAnimator;
import com.CryptoSportswallet.tools.animation.BRDialog;
import com.CryptoSportswallet.tools.manager.BRClipboardManager;
import com.CryptoSportswallet.tools.manager.BRSharedPrefs;
import com.CryptoSportswallet.tools.manager.FontManager;
import com.CryptoSportswallet.tools.manager.InternetManager;
import com.CryptoSportswallet.tools.manager.SyncManager;
import com.CryptoSportswallet.tools.manager.PlatformManager;
import com.CryptoSportswallet.tools.sqlite.CurrencyDataSource;
import com.CryptoSportswallet.tools.threads.executor.BRExecutor;
import com.CryptoSportswallet.tools.util.CurrencyUtils;
import com.CryptoSportswallet.tools.util.Utils;
import com.CryptoSportswallet.wallet.WalletsMaster;
import com.CryptoSportswallet.wallet.abstracts.BaseWalletManager;
import com.CryptoSportswallet.wallet.abstracts.OnTxListModified;
import com.CryptoSportswallet.wallet.abstracts.SyncListener;
import com.CryptoSportswallet.wallet.wallets.util.CryptoUriParser;
import com.platform.HTTPServer;

import java.math.BigDecimal;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.CryptoSportswallet.tools.animation.BRAnimator.t1Size;
import static com.CryptoSportswallet.tools.animation.BRAnimator.t2Size;

/**
 * Created by byfieldj on 1/16/18.
 * <p>
 * <p>
 * This activity will display pricing and transaction information for any currency the user has access to
 * (BTC, BCH, ETH)
 */

public class PlatformActivity extends BRActivity implements InternetManager.ConnectionReceiverListener, OnTxListModified, SyncManager.OnProgressUpdate {
    private static final String TAG = PlatformActivity.class.getName();
    //BRText mCurrencyTitle;
    //BRText mCurrencyPriceUsd;
    BRText mBalancePrimary;
    BRText mBalanceSecondary;
    Toolbar mToolbar;
    ImageButton mBackButton;
    BRButton mSendButton;
    BRButton mReceiveButton;
    BRButton mBuyButton;
    BRText mBalanceLabel;
    BRText mProgressLabel;
    ProgressBar mProgressBar;
    EditText mEditUsername;
    BRText mPublicAddress;

    public ViewFlipper barFlipper;
    private BRSearchPlatformBar searchBar;
    private ImageButton mSearchIcon;
    private ImageButton mCopyIcon;
    private ImageButton mSwap;
    private ConstraintLayout toolBarConstraintLayout;

    private String publicKey;

    private BRNotificationBar mNotificationBar;

    private static PlatformActivity app;

    private InternetManager mConnectionReceiver;

    private TestLogger logger;

    public static PlatformActivity getApp() {
        return app;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_platform);

        //mCurrencyTitle = findViewById(R.id.currency_label);
        //mCurrencyPriceUsd = findViewById(R.id.currency_usd_price);
        mBalancePrimary = findViewById(R.id.balance_primary);
        mBalanceSecondary = findViewById(R.id.balance_secondary);
        mToolbar = findViewById(R.id.bread_bar);
        mBackButton = findViewById(R.id.back_icon);
        mSendButton = findViewById(R.id.send_button);
        mReceiveButton = findViewById(R.id.receive_button);
        mBuyButton = findViewById(R.id.buy_button);
        barFlipper = findViewById(R.id.tool_bar_flipper);
        mSearchIcon = findViewById(R.id.search_icon);
        mCopyIcon = findViewById(R.id.copy_icon);
        toolBarConstraintLayout = findViewById(R.id.bread_toolbar);
        mSwap = findViewById(R.id.swap);
        mBalanceLabel = findViewById(R.id.balance_label);
        mProgressLabel = findViewById(R.id.syncing_label);
        mProgressBar = findViewById(R.id.sync_progress);
        mNotificationBar = findViewById(R.id.notification_bar);
        mEditUsername = findViewById(R.id.editUsername);
        mPublicAddress = findViewById(R.id.public_address);
        //searchBar = findViewById(R.id.search_platform_bar);

        if (Utils.isEmulatorOrDebug(this)) {
            if (logger != null) logger.interrupt();
            logger = new TestLogger(); //Sync logger
            logger.start();
        }

        setUpBarFlipper();

        //mPublicAddress.setText();

        BRAnimator.init(this);
        mBalancePrimary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);//make it the size it should be after animation to get the X
        mBalanceSecondary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);//make it the size it should be after animation to get the X

        BRAnimator.init(this);
        mBalancePrimary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);//make it the size it should be after animation to get the X
        mBalanceSecondary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);//make it the size it should be after animation to get the X


        mSendButton.setHasShadow(false);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Activity app = WalletActivity.this;
//                BaseWalletManager wm = WalletsMaster.getInstance(app).getCurrentWallet(app);
//                CryptoUriParser.processRequest(WalletActivity.this, "bitcoin:?r=https://bitpay.com/i/HUsFqTFirmVtgE4PhLzcRx", wm);
                BRAnimator.showSendFragment(PlatformActivity.this, null);

            }
        });

        mSendButton.setHasShadow(false);
        mReceiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BRAnimator.showReceiveFragment(PlatformActivity.this, true);

            }
        });

//        BaseWalletManager wm = WalletsMaster.getInstance(this).getCurrentWallet(this);
//        Log.d(TAG, "Current wallet ISO -> " + wm.getIso(this));

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
                finish();
            }
        });
/*
        mEditUsername.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if ( mEditUsername.getText().toString()=="Platform Username..." ) {
                        mEditUsername.setText("");
                    }
                }
            }
        });
  */

        mSearchIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        if ( !mEditUsername.getText().toString().isEmpty() ) {
                            if (mEditUsername.getText().toString().equals("Platform Username...")) {
                                mEditUsername.setText("");
                                mEditUsername.requestFocus();
                            }
                            else {
                                boolean res = PlatformManager.getInstance().updateAddress(PlatformActivity.this, mEditUsername.getText().toString(), mPublicAddress.getText().toString(), publicKey);
                                if (res) {
                                    saveSettings();
                                }
                            }
                        }
                    }
                });

            }
        });

        mCopyIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get the default color based on theme
                final int color = mPublicAddress.getCurrentTextColor();
                mPublicAddress.setTextColor(app.getColor(R.color.light_gray));
                String address = mPublicAddress.getText().toString();
                BRClipboardManager.putClipboard(app, address);
                Toast.makeText(app, getString(R.string.Receive_copied), Toast.LENGTH_LONG).show();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mPublicAddress.setTextColor(color);
                    }
                }, 200);
            }
        });

        mBalancePrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swap();
            }
        });
        mBalanceSecondary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swap();
            }
        });

        final BaseWalletManager wallet = WalletsMaster.getInstance(this).getCurrentWallet(this);
        loadSettings();
        BRCoreAddress[] addresses = wallet.getAllAddresses();
        String address = addresses[0].stringify();
        //String firstAddress = BRSharedPrefs.getFirstAddress(this);
        mPublicAddress.setText(address);

        BRCoreKey key = wallet.getKeyFromAddress(this, address);
        if (key!=null) {
            publicKey = Utils.bytesToHex(key.getPubKey());
        }

        PlatformManager.getInstance().init(this);
        PlatformManager.getInstance().setData( mEditUsername.getText().toString(), address, publicKey);

        onConnectionChanged(InternetManager.getInstance().isConnected(this));


        updateUi();
//        exchangeTest();

        boolean cryptoPreferred = BRSharedPrefs.isCryptoPreferred(this);

        if (cryptoPreferred) {
            swap();
        }

        // Check if the "Twilight" screen altering app is currently running
        if (checkIfScreenAlteringAppIsRunning("com.urbandroid.lux")) {

            BRDialog.showSimpleDialog(this, getString(R.string.Dialog_screenAlteringTitle), getString(R.string.Dialog_screenAlteringMessage));


        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mConnectionReceiver != null)
            unregisterReceiver(mConnectionReceiver);

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        //since we have one instance of activity at all times, this is needed to know when a new intent called upon this activity
        handleUrlClickIfNeeded(intent);
    }

    private void handleUrlClickIfNeeded(Intent intent) {
        Uri data = intent.getData();
        if (data != null && !data.toString().isEmpty()) {
            //handle external click with crypto scheme
            CryptoUriParser.processRequest(this, data.toString(), WalletsMaster.getInstance(this).getCurrentWallet(this));
        }
    }

    private void saveSettings( )
    {
        BRSharedPrefs.putPlatformUsername(this, mEditUsername.getText().toString() );
        BRSharedPrefs.putPlatformAddress(this, mPublicAddress.toString() );
    }

    private boolean loadSettings( )
    {
        String sUsername = BRSharedPrefs.getPlatformUsername(this );
        //String sPublicAddress = BRSharedPrefs.getPlatformAddress(this );

        if (!sUsername.equals(""))
        {
            mEditUsername.setText(sUsername);
            return true;
        }

        return false;
    }

    private void updateUi() {
        final BaseWalletManager wallet = WalletsMaster.getInstance(this).getCurrentWallet(this);
        if (wallet == null) {
            Log.e(TAG, "updateUi: wallet is null");
            return;
        }

        if (wallet.getUiConfiguration().buyVisible) {
            mBuyButton.setHasShadow(false);
            mBuyButton.setVisibility(View.VISIBLE);
            mBuyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(PlatformActivity.this, WebViewActivity.class);
                    intent.putExtra("url", HTTPServer.URL_BUY);
                    Activity app = PlatformActivity.this;
                    app.startActivity(intent);
                    app.overridePendingTransition(R.anim.enter_from_bottom, R.anim.fade_down);
                }
            });

        } else {
            LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Utils.getPixelsFromDps(this, 65), 1.5f
            );

            LinearLayout.LayoutParams param2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Utils.getPixelsFromDps(this, 65), 1.5f
            );
            param.gravity = Gravity.CENTER;
            param2.gravity = Gravity.CENTER;

            param.setMargins(Utils.getPixelsFromDps(this, 8), Utils.getPixelsFromDps(this, 8), Utils.getPixelsFromDps(this, 8), 0);
            param2.setMargins(0, Utils.getPixelsFromDps(this, 8), Utils.getPixelsFromDps(this, 8), 0);

            mSendButton.setLayoutParams(param);
            mReceiveButton.setLayoutParams(param2);
            mBuyButton.setVisibility(View.GONE);

        }


//        String fiatIso = BRSharedPrefs.getPreferredFiatIso(this);

        String fiatExchangeRate = CurrencyUtils.getFormattedAmount(this, BRSharedPrefs.getPreferredFiatIso(this), wallet.getFiatExchangeRate(this),8);
        String fiatBalance = CurrencyUtils.getFormattedAmount(this, BRSharedPrefs.getPreferredFiatIso(this), wallet.getFiatBalance(this),8);
        String cryptoBalance = CurrencyUtils.getFormattedAmount(this, wallet.getIso(this), new BigDecimal(wallet.getCachedBalance(this)),8);

        //mCurrencyTitle.setText(wallet.getName(this));
        //mCurrencyPriceUsd.setText(String.format("%s per %s", fiatExchangeRate, wallet.getIso(this)));
        mBalancePrimary.setText(fiatBalance);
        mBalanceSecondary.setText(cryptoBalance);
        mToolbar.setBackgroundColor(Color.parseColor(wallet.getUiConfiguration().colorHex));
        mSendButton.setColor(Color.parseColor(wallet.getUiConfiguration().colorHex));
        mBuyButton.setColor(Color.parseColor(wallet.getUiConfiguration().colorHex));
        mReceiveButton.setColor(Color.parseColor(wallet.getUiConfiguration().colorHex));

        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                PlatformManager.getInstance().updateDealList(PlatformActivity.this);
            }
        });

/*
        if (!BRSharedPrefs.wasBchDialogShown(this)) {
            BRDialog.showHelpDialog(this, getString(R.string.Dialog_welcomeBchTitle), getString(R.string.Dialog_welcomeBchMessage), getString(R.string.Dialog_Home), getString(R.string.Dialog_Dismiss), new BRDialogView.BROnClickListener() {
                @Override
                public void onClick(BRDialogView brDialogView) {
                    brDialogView.dismiss();
                    onBackPressed();
                }
            }, new BRDialogView.BROnClickListener() {

                @Override
                public void onClick(BRDialogView brDialogView) {
                    brDialogView.dismiss();

                }
            }, new BRDialogView.BROnClickListener() {
                @Override
                public void onClick(BRDialogView brDialogView) {
                    Log.d(TAG, "help clicked!");

                    brDialogView.dismiss();
                    BRAnimator.showSupportFragment(WalletActivity.this, BRConstants.bchFaq);

                }
            });

            BRSharedPrefs.putBchDialogShown(WalletActivity.this, true);
        }
*/

    }

    // This method checks if a screen altering app(such as Twightlight) is currently running
    // If it is, notify the user that the BRD app will not function properly and they should
    // disable it
    private boolean checkIfScreenAlteringAppIsRunning(String packageName) {


        // Use the ActivityManager API if sdk version is less than 21
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Get the Activity Manager
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

            // Get a list of running tasks, we are only interested in the last one,
            // the top most so we give a 1 as parameter so we only get the topmost.
            List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            Log.d(TAG, "Process list count -> " + processes.size());


            String processName = "";
            for (ActivityManager.RunningAppProcessInfo processInfo : processes) {

                // Get the info we need for comparison.
                processName = processInfo.processName;
                Log.d(TAG, "Process package name -> " + processName);

                // Check if it matches our package name
                if (processName.equals(packageName)) return true;


            }


        }


        // Use the UsageStats API for sdk versions greater than Lollipop
        else {
            UsageStatsManager usm = (UsageStatsManager) this.getSystemService(USAGE_STATS_SERVICE);
            long time = System.currentTimeMillis();
            List<UsageStats> appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 1000, time);
            if (appList != null && appList.size() > 0) {
                SortedMap<Long, UsageStats> mySortedMap = new TreeMap<Long, UsageStats>();
                String currentPackageName = "";
                for (UsageStats usageStats : appList) {
                    mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
                    currentPackageName = usageStats.getPackageName();


                    if (currentPackageName.equals(packageName)) {
                        return true;
                    }


                }


            }

        }


        return false;
    }

    private void swap() {
        if (!BRAnimator.isClickAllowed()) return;
        boolean b = !BRSharedPrefs.isCryptoPreferred(this);
        setPriceTags(b, true);
        BRSharedPrefs.setIsCryptoPreferred(this, b);
    }

    private void setPriceTags(final boolean cryptoPreferred, boolean animate) {
        //mBalanceSecondary.setTextSize(!cryptoPreferred ? t1Size : t2Size);
        //mBalancePrimary.setTextSize(!cryptoPreferred ? t2Size : t1Size);
        ConstraintSet set = new ConstraintSet();
        set.clone(toolBarConstraintLayout);
        if (animate)
            TransitionManager.beginDelayedTransition(toolBarConstraintLayout);
        int px8 = Utils.getPixelsFromDps(this, 8);
        int px16 = Utils.getPixelsFromDps(this, 16);
//
//        //align first item to parent right
//        set.connect(!cryptoPreferred ? R.id.balance_secondary : R.id.balance_primary, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, px16);
//        //align swap symbol after the first item
//        set.connect(R.id.swap, ConstraintSet.START, !cryptoPreferred ? R.id.balance_secondary : R.id.balance_primary, ConstraintSet.START, px8);
//        //align second item after swap symbol
//        set.connect(!cryptoPreferred ? R.id.balance_secondary : R.id.balance_primary, ConstraintSet.START, mSwap.getId(), ConstraintSet.END, px8);
//

        // CRYPTO on RIGHT
        if (cryptoPreferred) {

            // Align crypto balance to the right parent
            set.connect(R.id.balance_secondary, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, px8);
            set.connect(R.id.balance_secondary, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, -px8);

            // Align swap icon to left of crypto balance
            set.connect(R.id.swap, ConstraintSet.END, R.id.balance_secondary, ConstraintSet.START, px8);

            // Align usd balance to left of swap icon
            set.connect(R.id.balance_primary, ConstraintSet.END, R.id.swap, ConstraintSet.START, px8);

            mBalancePrimary.setPadding(0, 0, 0, Utils.getPixelsFromDps(this, 6));
            mBalanceSecondary.setPadding(0, 0, 0, Utils.getPixelsFromDps(this, 4));
            mSwap.setPadding(0, 0, 0, Utils.getPixelsFromDps(this, 2));

            Log.d(TAG, "CryptoPreferred " + cryptoPreferred);

            mBalanceSecondary.setTextSize(t1Size);
            mBalancePrimary.setTextSize(t2Size);

            set.applyTo(toolBarConstraintLayout);

        }

        // CRYPTO on LEFT
        else {

            // Align primary to right of parent
            set.connect(R.id.balance_primary, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, px8);

            // Align swap icon to left of usd balance
            set.connect(R.id.swap, ConstraintSet.END, R.id.balance_primary, ConstraintSet.START, px8);


            // Align secondary currency to the left of swap icon
            set.connect(R.id.balance_secondary, ConstraintSet.END, R.id.swap, ConstraintSet.START, px8);

            mBalancePrimary.setPadding(0, 0, 0, Utils.getPixelsFromDps(this, 2));
            mBalanceSecondary.setPadding(0, 0, 0, Utils.getPixelsFromDps(this, 4));
            mSwap.setPadding(0, 0, 0, Utils.getPixelsFromDps(this, 2));

            //mBalancePrimary.setPadding(0,0, 0, Utils.getPixelsFromDps(this, -4));

            Log.d(TAG, "CryptoPreferred " + cryptoPreferred);

            mBalanceSecondary.setTextSize(t2Size);
            mBalancePrimary.setTextSize(t1Size);


            set.applyTo(toolBarConstraintLayout);

        }


        if (!cryptoPreferred) {
            mBalanceSecondary.setTextColor(getResources().getColor(R.color.currency_subheading_color, null));
            mBalancePrimary.setTextColor(getResources().getColor(R.color.white, null));
            mBalanceSecondary.setTypeface(FontManager.get(this, "CircularPro-Book.otf"));

        } else {
            mBalanceSecondary.setTextColor(getResources().getColor(R.color.white, null));
            mBalancePrimary.setTextColor(getResources().getColor(R.color.currency_subheading_color, null));
            mBalanceSecondary.setTypeface(FontManager.get(this, "CircularPro-Bold.otf"));

        }

        new Handler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        updateUi();
                    }
                }, toolBarConstraintLayout.getLayoutTransition().getDuration(LayoutTransition.CHANGE_APPEARING));
    }

    @Override
    protected void onResume() {
        super.onResume();

        app = this;

        WalletsMaster.getInstance(app).initWallets(app);

        setupNetworking();

        PlatformManager.getInstance().onResume(this);

        CurrencyDataSource.getInstance(this).addOnDataChangedListener(new CurrencyDataSource.OnDataChanged() {
            @Override
            public void onChanged() {
                BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        updateUi();
                    }
                });
            }
        });
        final BaseWalletManager wallet = WalletsMaster.getInstance(this).getCurrentWallet(this);
        wallet.addTxListModifiedListener(this);
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                long balance = wallet.getWallet().getBalance();
                wallet.setCashedBalance(app, balance);
                BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        updateUi();
                    }
                });

            }
        });

        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                if (wallet.getPeerManager().getConnectStatus() != BRCorePeer.ConnectStatus.Connected)
                    wallet.connectWallet(PlatformActivity.this);
            }
        });

        wallet.addSyncListeners(new SyncListener() {
            @Override
            public void syncStopped(String err) {

            }

            @Override
            public void syncStarted() {
                SyncManager.getInstance().startSyncing(PlatformActivity.this, wallet, PlatformActivity.this);
            }
        });

        SyncManager.getInstance().startSyncing(this, wallet, this);

        handleUrlClickIfNeeded(getIntent());

    }

    @Override
    protected void onPause() {
        super.onPause();
        SyncManager.getInstance().stopSyncing();
    }

    private void setUpBarFlipper() {
        barFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.flipper_enter));
        barFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.flipper_exit));
    }

    public void resetFlipper() {
        barFlipper.setDisplayedChild(0);
    }

    private void setupNetworking() {
        if (mConnectionReceiver == null) mConnectionReceiver = InternetManager.getInstance();
        IntentFilter mNetworkStateFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mConnectionReceiver, mNetworkStateFilter);
        InternetManager.addConnectionListener(this);
    }


    @Override
    public void onConnectionChanged(boolean isConnected) {
        Log.d(TAG, "onConnectionChanged");
        if (isConnected) {
            if (barFlipper != null && barFlipper.getDisplayedChild() == 2) {
                barFlipper.setDisplayedChild(0);
            }
            final BaseWalletManager wm = WalletsMaster.getInstance(PlatformActivity.this).getCurrentWallet(PlatformActivity.this);
            BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                @Override
                public void run() {
                    final double progress = wm.getPeerManager()
                            .getSyncProgress(BRSharedPrefs.getStartHeight(PlatformActivity.this,
                                    BRSharedPrefs.getCurrentWalletIso(PlatformActivity.this)));
//                    Log.e(TAG, "run: " + progress);
                    if (progress < 1 && progress > 0) {
                        SyncManager.getInstance().startSyncing(PlatformActivity.this, wm, PlatformActivity.this);
                    }
                }
            });

        } else {
            if (barFlipper != null)
                barFlipper.setDisplayedChild(2);

        }
    }


    @Override
    public void onBackPressed() {
        int c = getFragmentManager().getBackStackEntryCount();
        if (c > 0) {
            super.onBackPressed();
            return;
        }
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
        if (!isDestroyed()) {
            finish();
        }
    }

    public void onSearchPressed() {

    }

    @Override
    public void txListModified(String hash) {
        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
            @Override
            public void run() {
                updateUi();
            }
        });

    }

    @Override
    public boolean onProgressUpdated(double progress) {
        mProgressBar.setProgress((int) (progress * 100));
        if (progress == 1) {
            mProgressBar.setVisibility(View.GONE);
            mProgressLabel.setVisibility(View.GONE);
            mBalanceLabel.setVisibility(View.VISIBLE);
            mProgressBar.invalidate();
            return false;
        }
        mProgressBar.setVisibility(View.VISIBLE);
        mProgressLabel.setVisibility(View.VISIBLE);
        mBalanceLabel.setVisibility(View.GONE);
        mProgressBar.invalidate();
        return true;
    }


    //test logger
    class TestLogger extends Thread {
        private static final String TAG = "TestLogger";

        @Override
        public void run() {
            super.run();

            while (true) {
                StringBuilder builder = new StringBuilder();
                for (BaseWalletManager w : WalletsMaster.getInstance(PlatformActivity.this).getAllWallets()) {
                    builder.append("   " + w.getIso(PlatformActivity.this));
                    String connectionStatus = "";
                    if (w.getPeerManager().getConnectStatus() == BRCorePeer.ConnectStatus.Connected)
                        connectionStatus = "Connected";
                    else if (w.getPeerManager().getConnectStatus() == BRCorePeer.ConnectStatus.Disconnected)
                        connectionStatus = "Disconnected";
                    else if (w.getPeerManager().getConnectStatus() == BRCorePeer.ConnectStatus.Connecting)
                        connectionStatus = "Connecting";

                    double progress = w.getPeerManager().getSyncProgress(BRSharedPrefs.getStartHeight(PlatformActivity.this, w.getIso(PlatformActivity.this)));

                    builder.append(" - " + connectionStatus + " " + progress * 100 + "%     ");

                }

                Log.e(TAG, "testLog: " + builder.toString());

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }

}
