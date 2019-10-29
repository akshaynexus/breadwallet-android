package com.cspnwallet;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.KeyguardManager;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.support.constraint.ConstraintLayout;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import com.cspnwallet.app.ApplicationLifecycleObserver;
import com.cspnwallet.presenter.customviews.BRLockScreenConstraintLayout;
import com.cspnwallet.protocols.messageexchange.InboxPollingHandler;
import com.cspnwallet.tools.manager.BRApiManager;
import com.cspnwallet.tools.util.BRConstants;
import com.cspnwallet.tools.util.ServerBundlesHelper;
import com.cspnwallet.view.dialog.DialogActivity;
import com.cspnwallet.view.dialog.DialogActivity.DialogType;
import com.cspnwallet.app.util.UserMetricsUtil;
import com.cspnwallet.protocols.messageexchange.InboxPollingAppLifecycleObserver;
import com.cspnwallet.tools.crypto.Base32;
import com.cspnwallet.tools.crypto.CryptoHelper;
import com.cspnwallet.tools.manager.BRReportsManager;
import com.cspnwallet.tools.manager.BRSharedPrefs;
import com.cspnwallet.tools.manager.InternetManager;
import com.cspnwallet.tools.security.BRKeyStore;
import com.cspnwallet.tools.services.BRDFirebaseMessagingService;
import com.cspnwallet.tools.threads.executor.BRExecutor;
import com.cspnwallet.tools.util.EventUtils;
import com.cspnwallet.tools.util.TokenUtil;
import com.cspnwallet.tools.util.Utils;
import com.cspnwallet.wallet.util.SyncUpdateHandler;
import com.cspnwallet.wallet.util.WalletConnectionCleanUpWorker;
import com.cspnwallet.wallet.util.WalletConnectionWorker;
import com.cspnwallet.wallet.wallets.ethereum.WalletEthManager;
import com.crashlytics.android.Crashlytics;
import com.platform.APIClient;
import com.platform.HTTPServer;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric.sdk.android.Fabric;

/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 7/22/15.
 * Copyright (c) 2016 breadwallet LLC
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

public class BreadApp extends Application implements ApplicationLifecycleObserver.ApplicationLifecycleListener {
    private static final String TAG = BreadApp.class.getName();

    // The server(s) on which the API is hosted
    private static final String HOST = BuildConfig.DEBUG ? "stage2.breadwallet.com" : "api.breadwallet.com";
    private static final String WALLET_ID_PATTERN = "^[a-z0-9 ]*$"; // The wallet ID is in the form "xxxx xxxx xxxx xxxx" where x is a lowercase letter or a number.
    private static final String WALLET_ID_SEPARATOR = " ";
    private static final int NUMBER_OF_BYTES_FOR_SHA256_NEEDED = 10;
    private static final long SERVER_SHUTDOWN_DELAY_MILLIS = 60000; // 60 seconds

    private static BreadApp mInstance;
    public static int mDisplayHeightPx;
    public static int mDisplayWidthPx;
    private static long mBackgroundedTime;
    private static Activity mCurrentActivity;
    private int mDelayServerShutdownCode = -1;
    private boolean mDelayServerShutdown = false;
    private Handler mServerShutdownHandler = null;
    private Runnable mServerShutdownRunnable = null;

    @Override
    public void onCreate() {
        super.onCreate();

        mInstance = this;

        BRSharedPrefs.provideContext(this);

        final Fabric fabric = new Fabric.Builder(this)
                .kits(new Crashlytics.Builder().build())
                .debuggable(BuildConfig.DEBUG)// Enables Crashlytics debugger
                .build();
        Fabric.with(fabric);

        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mDisplayWidthPx = size.x;
        mDisplayHeightPx = size.y;

        // Initialize application lifecycle observer and register this application for events.
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new ApplicationLifecycleObserver());
        ApplicationLifecycleObserver.addApplicationLifecycleListener(mInstance);

        initialize(true);

        registerReceiver(InternetManager.getInstance(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // Start our local server as soon as the application instance is created, since we need to
        // display support WebViews during onboarding.
        HTTPServer.getInstance().startServer(this);
    }

    /**
     * Initializes the application state.
     *
     * @param isApplicationOnCreate True if the caller is {@link BreadApp#onCreate()}; false, otherwise.
     */
    public static void initialize(boolean isApplicationOnCreate) {
        // Things that should be done only if the wallet exists.
        if (isBRDWalletInitialized()) {
            // Initialize the wallet id (also called rewards id).
            initializeWalletId();

            // Initialize message exchange inbox polling.
            ApplicationLifecycleObserver.addApplicationLifecycleListener(new InboxPollingAppLifecycleObserver(mInstance));
            if (!isApplicationOnCreate) {
                InboxPollingHandler.getInstance().startPolling(mInstance);
            }

            // Initialize the Firebase Messaging Service.
            BRDFirebaseMessagingService.Companion.initialize(mInstance);

            // Initialize TokenUtil to load our tokens.json file from res/raw
            if(BRConstants.allowOthercoins)
            TokenUtil.initialize(mInstance);
        } else {
            // extract the bundles from the resources to be ready when the wallet is initialized
            BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(() ->
                    ServerBundlesHelper.extractBundlesIfNeeded(mInstance));
        }
    }

    /**
     * Returns whether the BRD wallet is initialized.  i.e. has the BRD wallet been created or recovered.
     *
     * @return True if the BRD wallet is initialized; false, otherwise.
     */
    private static boolean isBRDWalletInitialized() {
        return BRKeyStore.getMasterPublicKey(mInstance) != null;
    }

    /**
     * Initialize the wallet id (rewards id), and save it in the SharedPreferences.
     */
    private static void initializeWalletId() {
        if(BRConstants.allowOthercoins) {
            String walletId = generateWalletId();
            if (!Utils.isNullOrEmpty(walletId) && walletId.matches(WALLET_ID_PATTERN)) {
                BRSharedPrefs.putWalletRewardId(mInstance, walletId);
            } else {
                Log.e(TAG, "initializeWalletId: walletId is empty or faulty after generation");
                BRSharedPrefs.putWalletRewardId(mInstance, "");
                BRReportsManager.reportBug(new IllegalArgumentException("walletId is empty or faulty after generation: " + walletId));
            }
        }
    }

    /**
     * Generates the wallet id (rewards id) based on the Ethereum address. The format of the id is
     * "xxxx xxxx xxxx xxxx", where x is a lowercase letter or a number.
     *
     * @return The wallet id.
     */
    private static synchronized String generateWalletId() {
        try {
            // Retrieve the ETH address since the wallet id is based on this.
            String address = WalletEthManager.getInstance(mInstance).getAddress(mInstance);

            // Remove the first 2 characters i.e. 0x
            String rawAddress = address.substring(2, address.length());

            // Get the address bytes.
            byte[] addressBytes = rawAddress.getBytes("UTF-8");

            // Run SHA256 on the address bytes.
            byte[] sha256Address = CryptoHelper.sha256(addressBytes);
            if (Utils.isNullOrEmpty(sha256Address)) {
                BRReportsManager.reportBug(new IllegalAccessException("Failed to generate SHA256 hash."));
                return null;
            }

            // Get the first 10 bytes of the SHA256 hash.
            byte[] firstTenBytes = Arrays.copyOfRange(sha256Address, 0, NUMBER_OF_BYTES_FOR_SHA256_NEEDED);

            // Convert the first 10 bytes to a lower case string.
            String base32String = new String(Base32.encode(firstTenBytes));
            base32String = base32String.toLowerCase();

            // Insert a space every 4 chars to match the specified format.
            StringBuilder builder = new StringBuilder();
            Matcher matcher = Pattern.compile(".{1,4}").matcher(base32String);
            String separator = "";
            while (matcher.find()) {
                String piece = base32String.substring(matcher.start(), matcher.end());
                builder.append(separator + piece);
                separator = WALLET_ID_SEPARATOR;
            }
            return builder.toString();

        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unable to get address bytes.", e);
            return null;
        }
    }

    /**
     * Clears all app data from disk. This is equivalent to the user choosing to clear the app's data from within the
     * device settings UI. It erases all dynamic data associated with the app -- its private data and data in its
     * private area on external storage -- but does not remove the installed application itself, nor any OBB files.
     * It also revokes all runtime permissions that the app has acquired, clears all notifications and removes all
     * Uri grants related to this application.
     *
     * @throws IllegalStateException if the {@link ActivityManager} fails to wipe the user's data.
     */
    public static void clearApplicationUserData() {
        if (!((ActivityManager) mInstance.getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData()) {
            throw new IllegalStateException(TAG + ": Failed to clear user application data.");
        }
    }

    /**
     * Returns true if the application is in the background; false, otherwise.
     *
     * @return True if the application is in the background; false, otherwise.
     */
    public static boolean isInBackground() {
        return mBackgroundedTime > 0;
    }
    public static void setBackgroundImage(ConstraintLayout layout){
        InputStream imageStream = getBreadContext().getResources().openRawResource(R.raw.cspn_background_sm);
        Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
        Drawable d = new BitmapDrawable(getBreadContext().getResources(), bitmap);
        layout.setBackground(d);
    }
    public static void setBackgroundImageR(RecyclerView layout){
        InputStream imageStream = getBreadContext().getResources().openRawResource(R.raw.cspn_background_sm);
        Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
        Drawable d = new BitmapDrawable(getBreadContext().getResources(), bitmap);
        layout.setBackground(d);
    }
    public static void setBackgroundImageRL(RelativeLayout layout){
        InputStream imageStream = getBreadContext().getResources().openRawResource(R.raw.cspn_background_sm);
        Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
        Drawable d = new BitmapDrawable(getBreadContext().getResources(), bitmap);
        layout.setBackground(d);
    }
    public static void setBackgroundImageforNest(NestedScrollView layout){
        InputStream imageStream = getBreadContext().getResources().openRawResource(R.raw.cspn_background_sm);
        Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
        Drawable d = new BitmapDrawable(getBreadContext().getResources(), bitmap);
        layout.setBackground(d);
    }
    // TODO: Refactor so this does not store the current activity like this.
    @Deprecated
    public static Context getBreadContext() {
        Context app = mCurrentActivity;
        if (app == null) {
            app = mInstance;
        }
        return app;
    }

    // TODO: Refactor so this does not store the current activity like this.
    public static void setBreadContext(Activity app) {
        mCurrentActivity = app;
    }

    @Override
    public void onLifeCycle(Lifecycle.Event event) {
        switch (event) {
            case ON_START:
                Log.d(TAG, "onLifeCycle: START");

                // Each time the app resumes, check to see if the device state is valid. Even if the wallet is not
                // initialized, we may need tell the user to enable the password.
                if (isDeviceStateValid()) {
                    if (isBRDWalletInitialized()) {
                        WalletConnectionCleanUpWorker.cancelEnqueuedWork();

                        WalletConnectionWorker.enqueueWork();

                        HTTPServer.getInstance().startServer(this);

                        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(() -> TokenUtil.fetchTokensFromServer(mInstance));
                        APIClient.getInstance(this).updatePlatform();

                        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(() -> UserMetricsUtil.makeUserMetricsRequest(mInstance));
                        incrementAppForegroundedCounter();
                    }
                }
                BRApiManager.getInstance().startTimer(this);
                break;
            case ON_STOP:
                Log.d(TAG, "onLifeCycle: STOP");
                if (isBRDWalletInitialized()) {
                    mBackgroundedTime = System.currentTimeMillis();
                    WalletConnectionCleanUpWorker.enqueueWork();
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(() -> {
                        EventUtils.saveEvents(BreadApp.this);
                        EventUtils.pushToServer(BreadApp.this);
                    });
                    if (!mDelayServerShutdown) {
                        Log.i(TAG, "Shutting down HTTPServer.");
                        HTTPServer.getInstance().stopServer();
                    } else {
                        // If server shutdown needs to be delayed, it will occur after
                        // SERVER_SHUTDOWN_DELAY_MILLIS.  This may be cancelled if the app
                        // is closed before execution or the user returns to the app.
                        Log.i(TAG, "Delaying HTTPServer shutdown.");
                        if (mServerShutdownHandler == null) {
                            mServerShutdownHandler = new Handler(Looper.getMainLooper());
                        }
                        mServerShutdownRunnable = () -> {
                            Log.i(TAG, "Shutdown delay elapsed, shutting down HTTPServer.");
                            HTTPServer.getInstance().stopServer();
                            mServerShutdownRunnable = null;
                            mServerShutdownHandler = null;
                        };
                        mServerShutdownHandler.postDelayed(
                                mServerShutdownRunnable,
                                SERVER_SHUTDOWN_DELAY_MILLIS
                        );
                    }
                }
                BRApiManager.getInstance().stopTimerTask();
                SyncUpdateHandler.INSTANCE.cancelWalletSync();
                break;
            case ON_DESTROY:
                Log.d(TAG, "onLifeCycle: DESTROY");
                if (HTTPServer.getInstance().isRunning()) {
                    if (mServerShutdownHandler != null && mServerShutdownRunnable != null) {
                        Log.d(TAG, "Preempt delayed server shutdown callback");
                        mServerShutdownHandler.removeCallbacks(mServerShutdownRunnable);
                    }
                    Log.i(TAG, "Shutting down HTTPServer.");
                    HTTPServer.getInstance().stopServer();
                    mDelayServerShutdown = false;
                }
            default:
                break;
        }
    }

    /**
     * Reset the backgrounded time to 0. Intended to be called only from BRActivity.onResume
     */
    public void resetBackgroundedTime() {
        mBackgroundedTime = 0;
    }

    /**
     * Get the time when the app was sent to background.
     * @return the timestamp when the app was sent sent to background or 0 if it's in the foreground.
     */
    public long getBackgroundedTime() {
        return mBackgroundedTime;
    }

    /**
     * @return host or debug host if build is DEBUG
     */
    public static String getHost() {
        if (BuildConfig.DEBUG) {
            String host = BRSharedPrefs.getDebugHost(mInstance);
            if (!Utils.isNullOrEmpty(host)) {
                return host;
            }
        }
        return HOST;
    }

    /**
     * Sets the debug host into the shared preferences, only do that if the build is DEBUG.
     *
     * @param host
     */
    public static void setDebugHost(String host) {
        if (BuildConfig.DEBUG) {
            BRSharedPrefs.putDebugHost(mCurrentActivity, host);
        }
    }

    /**
     * Returns true if the device state is valid. The device state is considered valid, if the device password
     * is enabled and if the Android key store state is valid.  The Android key store can be invalided if the
     * device password was removed or if fingerprints are added/removed.
     *
     * @return True, if the device state is valid; false, otherwise.
     */
    public boolean isDeviceStateValid() {
        boolean isDeviceStateValid;
        DialogType dialogType = DialogType.DEFAULT;

        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Activity.KEYGUARD_SERVICE);
        if (!keyguardManager.isKeyguardSecure()) {
            isDeviceStateValid = false;
            dialogType = DialogType.ENABLE_DEVICE_PASSWORD;
        } else {
            switch (BRKeyStore.getValidityStatus()) {
                case VALID:
                    isDeviceStateValid = true;
                    break;
                case INVALID_WIPE:
                    isDeviceStateValid = false;
                    dialogType = DialogType.KEY_STORE_INVALID_WIPE;
                    break;
                case INVALID_UNINSTALL:
                    isDeviceStateValid = false;
                    dialogType = DialogType.KEY_STORE_INVALID_UNINSTALL;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid key store validity status.");
            }
        }

        if (dialogType != DialogType.DEFAULT) {
            DialogActivity.startDialogActivity(this, dialogType);
        }

        return isDeviceStateValid;
    }

    private void incrementAppForegroundedCounter() {
        BRSharedPrefs.putInt(this, BRSharedPrefs.APP_FOREGROUNDED_COUNT,
                BRSharedPrefs.getInt(this, BRSharedPrefs.APP_FOREGROUNDED_COUNT, 0) + 1);
    }

    /**
     * When delayServerShutdown is true, the HTTPServer will remain
     * running after onStop, until onDestroy.
     */
    public void setDelayServerShutdown(final boolean delayServerShutdown, final int requestCode) {
        synchronized (this) {
            Log.d(TAG, "setDelayServerShutdown(" + delayServerShutdown + ", " + requestCode + ")");
            boolean isMatchingRequestCode = mDelayServerShutdownCode == requestCode ||
                    requestCode == -1 || // Force the update regardless of current request
                    mDelayServerShutdownCode == -1; // No initial request
            if (isMatchingRequestCode) {
                mDelayServerShutdown = delayServerShutdown;
                mDelayServerShutdownCode = requestCode;
                if (!mDelayServerShutdown &&
                        mServerShutdownRunnable != null &&
                        mServerShutdownHandler != null) {
                    Log.d(TAG, "Cancelling delayed HTTPServer execution.");
                    mServerShutdownHandler.removeCallbacks(mServerShutdownRunnable);
                    mServerShutdownHandler = null;
                    mServerShutdownRunnable = null;
                }
                if (!mDelayServerShutdown) {
                    mDelayServerShutdownCode = -1;
                }
            }
        }
    }
}