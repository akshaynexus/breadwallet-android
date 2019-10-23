package com.cspnwallet.presenter.activities.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.cspnwallet.BuildConfig;
import com.cspnwallet.R;
import com.cspnwallet.presenter.customviews.BaseTextView;
import com.cspnwallet.tools.animation.UiUtils;
import com.cspnwallet.tools.manager.BRClipboardManager;
import com.cspnwallet.tools.manager.BRSharedPrefs;
import com.cspnwallet.tools.util.BRConstants;
import com.cspnwallet.tools.util.LogsUtils;
import com.cspnwallet.BreadApp;

import java.util.Locale;

public class AboutActivity extends BaseSettingsActivity {
    private static final int VERSION_CLICK_COUNT_FOR_BACKDOOR = 5;
    private BaseTextView mCopy;
    private BaseTextView mRewardsId;
    private int mVersionClickedCount;

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
        if(BRConstants.enableWhiteonDarkCSPNStyle) {
            BreadApp.setBackgroundImage(findViewById(R.id.activity_intro_set_pit));
            BaseTextView tbrd_Rewards = findViewById(R.id.brd_rewards);
            BaseTextView tbrd_Rewards_id = findViewById(R.id.brd_rewards_id);
            BaseTextView tblog_text = findViewById(R.id.textView24);
            BaseTextView ttwitter_text = findViewById(R.id.textView22);
            BaseTextView treddit_text = findViewById(R.id.textView21);
            BaseTextView mabout_text = findViewById(R.id.title);
            //set text color to black to have it show properly on a white bg,disable this if we are using a dark theme
            tbrd_Rewards.setTextColor(getResources().getColor(R.color.textonlightbg, getTheme()));
            tbrd_Rewards_id.setTextColor(getResources().getColor(R.color.textonlightbg, getTheme()));
            tblog_text.setTextColor(getResources().getColor(R.color.textonlightbg, getTheme()));
            ttwitter_text.setTextColor(getResources().getColor(R.color.textonlightbg, getTheme()));
            treddit_text.setTextColor(getResources().getColor(R.color.textonlightbg, getTheme()));
            infoText.setTextColor(getResources().getColor(R.color.textonlightbg, getTheme()));
            mabout_text.setTextColor(getResources().getColor(R.color.textonlightbg, getTheme()));
        }
        infoText.setText(String.format(Locale.getDefault(), getString(R.string.About_footer), BuildConfig.VERSION_NAME, BuildConfig.BUILD_VERSION));
        infoText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mVersionClickedCount++;
                if (mVersionClickedCount >= VERSION_CLICK_COUNT_FOR_BACKDOOR) {
                    mVersionClickedCount = 0;
                    LogsUtils.shareLogs(AboutActivity.this);
                }
            }
        });

        ImageView redditShare = findViewById(R.id.reddit_share_button);
        ImageView twitterShare = findViewById(R.id.twitter_share_button);
        ImageView blogShare = findViewById(R.id.blog_share_button);
        mRewardsId = findViewById(R.id.brd_rewards_id);
        mCopy = findViewById(R.id.brd_copy);

        redditShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_REDDIT));
                startActivity(browserIntent);
                AboutActivity.this.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
            }
        });
        twitterShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_TWITTER));
                startActivity(browserIntent);
                AboutActivity.this.overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);
            }
        });
        blogShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_BLOG));
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
        if(BRConstants.allowOthercoins)
        mRewardsId.setText(BRSharedPrefs.getWalletRewardId(this));

        mCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BRClipboardManager.putClipboard(AboutActivity.this, mRewardsId.getText().toString());
                Toast.makeText(AboutActivity.this, getString(R.string.Receive_copied), Toast.LENGTH_SHORT).show();
            }
        });
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


}
