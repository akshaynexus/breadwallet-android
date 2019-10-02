package com.cspnwallet.presenter.activities.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatDelegate;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.cspnwallet.BuildConfig;
import com.cspnwallet.R;
import com.cspnwallet.presenter.customviews.BRToast;
import com.cspnwallet.presenter.customviews.BaseTextView;
import com.cspnwallet.tools.animation.UiUtils;
import com.cspnwallet.tools.manager.BRSharedPrefs;
import com.cspnwallet.tools.util.BRConstants;
import com.cspnwallet.tools.util.FileHelper;

import com.platform.APIClient;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;

import java.io.InputStream;
import java.util.Locale;



public class AboutActivity extends BaseSettingsActivity {
    private static final int VERSION_CLICK_COUNT_FOR_BACKDOOR = 5;
    private static final String DEFAULT_LOGS_EMAIL = "android@brd.com";
    private static final String LOGCAT_COMMAND = String.format("logcat -d %s:V", BuildConfig.APPLICATION_ID); // Filters out our apps events at log level = verbose
    private static final String NO_EMAIL_APP_ERROR_MESSAGE = "No email app found.";
    private static final String FAILED_ERROR_MESSAGE = "Failed to get logs.";
    private static final String LOGS_EMAIL_SUBJECT = "BRD Android App Feedback [ID:%s]"; // Placeholder is for a unique id. 
    private static final String DEFAULT_LOG_ATTACHMENT_BODY = "No logs.";
    private static final String LOGS_FILE_NAME = "Logs.txt";

    private static final String MIME_TYPE = "text/plain";
    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }
    private BaseTextView mCopy;
    private BaseTextView mRewardsId;
    private int mVersionClickedCount;
    private ConstraintLayout constraintLayout;
    @Override
    public int getLayoutId() {
        return R.layout.activity_about;
    }

    @Override
    public int getBackButtonId() {
        return R.id.back_button;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView infoText = findViewById(R.id.info_text);
        TextView policyText = findViewById(R.id.policy_text);
constraintLayout = findViewById(R.id.activity_intro_set_pit);
        InputStream imageStream = this.getResources().openRawResource(R.raw.cspn_background_sm);
        Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
        Drawable d = new BitmapDrawable(getResources(), bitmap);

        constraintLayout.setBackground(d);
        infoText.setText(String.format(Locale.getDefault(), getString(R.string.About_footer), BuildConfig.VERSION_NAME, BuildConfig.BUILD_VERSION));
        infoText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mVersionClickedCount++;
                if (mVersionClickedCount >= VERSION_CLICK_COUNT_FOR_BACKDOOR) {
                    mVersionClickedCount = 0;
                    shareLogs();
                }
            }
        });

  ImageView discordShare = findViewById(R.id.discord_open_button);
        ImageView twitterShare = findViewById(R.id.twitter_share_button);
        ImageView websiteShare = findViewById(R.id.blog_share_button);
        ImageView telegramShare = findViewById(R.id.telegram_open_button);
        ImageView facebookShare = findViewById(R.id.facebook_open_button);
        mRewardsId = findViewById(R.id.brd_rewards_id);
        mRewardsId.setVisibility(View.INVISIBLE);
//        mCopy = findViewById(R.id.brd_copy);

//        redditShare.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_REDDIT));
//                startActivity(browserIntent);
//                AboutActivity.this.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
//            }
//        });
        twitterShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_TWITTER));
                startActivity(browserIntent);
                AboutActivity.this.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
            }
        });
        discordShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_DISCORD));
                startActivity(browserIntent);
                AboutActivity.this.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
            }
        });
        websiteShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_BLOG));
                startActivity(browserIntent);
                AboutActivity.this.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
            }
        });
        telegramShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_TELEGRAM));
                startActivity(browserIntent);
                AboutActivity.this.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
            }
        });
        facebookShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_FACEBOOK));
                startActivity(browserIntent);
                AboutActivity.this.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
            }
        });
        policyText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_PRIVACY_POLICY));
                startActivity(browserIntent);
                AboutActivity.this.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
            }
        });

//        mRewardsId.setText(BRSharedPrefs.getWalletRewardId(this));
//
//        mCopy.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                BRClipboardManager.putClipboard(AboutActivity.this, mRewardsId.getText().toString());
//                Toast.makeText(AboutActivity.this, getString(R.string.Receive_copied), Toast.LENGTH_SHORT).show();
//            }
//        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVersionClickedCount = 0;
    }

    @Override
    public void onBackPressed() {
        if (UiUtils.isLast(this)) {
            UiUtils.startBreadActivity(this, false);
        } else {
            super.onBackPressed();
        }
        overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
    }

    private void shareLogs() {
        File file = FileHelper.saveToExternalStorage(this, LOGS_FILE_NAME, getLogs());
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID, file);
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType(MIME_TYPE);
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{DEFAULT_LOGS_EMAIL});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format(LOGS_EMAIL_SUBJECT, BRSharedPrefs.getDeviceId(this)));
        emailIntent.putExtra(Intent.EXTRA_TEXT, getDeviceInfo());

        try {
            startActivity(Intent.createChooser(emailIntent, getString(R.string.Receive_share)));
        } catch (ActivityNotFoundException e) {
            BRToast.showCustomToast(this, NO_EMAIL_APP_ERROR_MESSAGE,
                    BRSharedPrefs.getScreenHeight(this) / 2, Toast.LENGTH_LONG, 0);
        }
    }

    private String getLogs() {
        try {
            Process process = Runtime.getRuntime().exec(LOGCAT_COMMAND);
            return IOUtils.toString(process.getInputStream());
        } catch (IOException ex) {
            BRToast.showCustomToast(this, FAILED_ERROR_MESSAGE,
                    BRSharedPrefs.getScreenHeight(this) / 2, Toast.LENGTH_LONG, 0);
        }
        return DEFAULT_LOG_ATTACHMENT_BODY;
    }

    private String getDeviceInfo() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Feedback\n");
        stringBuilder.append("------------\n");
        stringBuilder.append("[Please add your feedback.]\n\n");
        stringBuilder.append("Device Info\n");
        stringBuilder.append("------------\n");
        stringBuilder.append("Wallet id: " + BRSharedPrefs.getWalletRewardId(this));
        stringBuilder.append("\nDevice id: " + BRSharedPrefs.getDeviceId(this));
        stringBuilder.append("\nDebuggable: " + BuildConfig.DEBUG);
        stringBuilder.append("\nApplication id: " + BuildConfig.APPLICATION_ID);
        stringBuilder.append("\nBuild Type: " + BuildConfig.BUILD_TYPE);
        stringBuilder.append("\nBuild Flavor: " + BuildConfig.FLAVOR);
        stringBuilder.append("\nApp Version: " + (BuildConfig.VERSION_NAME + " " + BuildConfig.BUILD_VERSION));
        for (String bundleName : APIClient.BUNDLE_NAMES) {
            stringBuilder.append(String.format("\n Bundle %s - Version: %s", bundleName, BRSharedPrefs.getBundleHash(this, bundleName)));
        }

        stringBuilder.append("\nNetwork: " + (BuildConfig.BITCOIN_TESTNET ? "Testnet" : "Mainnet"));
        stringBuilder.append("\nOS Version: " + Build.VERSION.RELEASE);
        stringBuilder.append("\nDevice Type: " + (Build.MANUFACTURER + " " + Build.MODEL + "\n"));

        return stringBuilder.toString();
    }

}
