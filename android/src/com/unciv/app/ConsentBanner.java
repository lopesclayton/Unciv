package com.unciv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AgeRestrictedTreatment;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/** Consent-gated banner. Debug resources always point to Google's test inventory. */
final class ConsentBanner {
    private final Activity activity;
    private final ConsentInformation consent;
    private final FrameLayout slot;
    private final Button privacy;
    private AdView banner;
    private boolean initializing;
    private boolean destroyed;
    private boolean paused = true;
    private boolean privacyFormOpen;
    private boolean ageScreenComplete;
    private AlertDialog ageDialog;
    private static final String AGE_PREFS = "wigo_audience_v1";
    private static final String POLICY_URL = "https://s3.sa-east-1.amazonaws.com/lupper.com.br/politica-privacidade-jogos.html";

    ConsentBanner(Activity activity, LinearLayout parent) {
        this.activity = activity;
        consent = UserMessagingPlatform.getConsentInformation(activity);
        privacy = new Button(activity);
        privacy.setText(R.string.wigo_privacy);
        privacy.setMinHeight(dp(48));
        privacy.setVisibility(View.VISIBLE);
        parent.addView(privacy, new LinearLayout.LayoutParams(-1, -2));
        slot = new FrameLayout(activity);
        // Reserve space from the outset so a loaded ad never moves the game controls.
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(58));
        slot.setPadding(0, dp(8), 0, 0);
        parent.addView(slot, params);
        privacy.setOnClickListener(v -> showPrivacyMenu());
    }

    private void showPrivacyMenu() {
        boolean options = consent.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        String[] items = options
                ? new String[]{activity.getString(R.string.wigo_policy), activity.getString(R.string.wigo_parent_title), activity.getString(R.string.ad_privacy_options)}
                : new String[]{activity.getString(R.string.wigo_policy), activity.getString(R.string.wigo_parent_title)};
        new AlertDialog.Builder(activity).setTitle(R.string.wigo_privacy)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) openPolicy();
                    else if (which == 1) activity.startActivity(new Intent(activity, WigoParentActivity.class));
                    else showAdPrivacyOptions();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void openPolicy() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(POLICY_URL)));
        } catch (ActivityNotFoundException error) {
            new AlertDialog.Builder(activity).setTitle(R.string.wigo_policy)
                    .setMessage(activity.getString(R.string.wigo_no_browser) + "\n\n" + POLICY_URL)
                    .setPositiveButton(android.R.string.ok, null).show();
        }
    }

    private void showAdPrivacyOptions() {
        privacyFormOpen = true;
        removeBanner();
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            privacyFormOpen = false;
            if (destroyed) return;
            if (error != null) Toast.makeText(activity, R.string.ad_privacy_unavailable, Toast.LENGTH_LONG).show();
            requestBanner();
        });
    }

    // No birth date or exact age is persisted, transmitted or used for ad targeting.
    private void screenAge() {
        android.content.Context dialogContext = new android.view.ContextThemeWrapper(
                activity, android.R.style.Theme_Material_Light_Dialog_Alert);
        LinearLayout form = new LinearLayout(dialogContext);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView description = new TextView(dialogContext);
        description.setText(R.string.wigo_age_description);
        form.addView(description);
        TextView label = new TextView(dialogContext);
        label.setText(R.string.wigo_age_label);
        form.addView(label);
        EditText age = new EditText(dialogContext);
        age.setId(View.generateViewId());
        age.setContentDescription(activity.getString(R.string.wigo_age_label));
        label.setLabelFor(age.getId());
        age.setInputType(InputType.TYPE_CLASS_NUMBER);
        age.setSingleLine(true);
        age.setMinHeight(dp(48));
        age.setSaveEnabled(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            age.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        form.addView(age);
        Button policy = new Button(dialogContext);
        policy.setText(R.string.wigo_policy);
        policy.setMinHeight(dp(48));
        policy.setOnClickListener(v -> openPolicy());
        form.addView(policy);
        ScrollView scroll = new ScrollView(dialogContext);
        scroll.addView(form);
        ageDialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(R.string.wigo_age_title).setView(scroll)
                .setPositiveButton(R.string.wigo_continue, null)
                .setNegativeButton(R.string.wigo_age_skip, (dialog, which) -> completeAge("unknown"))
                .setOnCancelListener(dialog -> completeAge("unknown"))
                .create();
        ageDialog.setOnShowListener(dialog -> ageDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int value;
            try { value = Integer.parseInt(age.getText().toString().trim()); }
            catch (NumberFormatException error) { value = -1; }
            if (value < 0 || value > 120) {
                age.setError(activity.getString(R.string.wigo_age_error));
                age.requestFocus();
                return;
            }
            // Conservative local categories; this is not proof of adulthood or parental consent.
            completeAge(value < 18 ? "under18" : "18plus");
            ageDialog.dismiss();
        }));
        ageDialog.show();
    }

    private void completeAge(String category) {
        if (destroyed || ageScreenComplete) return;
        activity.getSharedPreferences(AGE_PREFS, Activity.MODE_PRIVATE).edit()
                .putString("category", category).apply();
        ageScreenComplete = true;
        startConsent();
    }

    void start() {
        if (destroyed || ageScreenComplete || ageDialog != null) return;
        if (!activity.getSharedPreferences(AGE_PREFS, Activity.MODE_PRIVATE).contains("category")) {
            screenAge();
            return;
        }
        ageScreenComplete = true;
        startConsent();
    }

    private void startConsent() {
        // Apply the most restrictive treatment to EVERY user, including unknown ages.
        // Configure before SDK initialization; UMP does not forward its age flag to Ads.
        MobileAds.setRequestConfiguration(MobileAds.getRequestConfiguration().toBuilder()
                .setAgeRestrictedTreatment(AgeRestrictedTreatment.CHILD)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .setPublisherPrivacyPersonalizationState(
                        RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED)
                .build());
        consent.requestConsentInfoUpdate(activity, new ConsentRequestParameters.Builder()
                        .setTagForUnderAgeOfConsent(true).build(),
                () -> {
                    if (destroyed) return;
                    refreshPrivacy();
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> {
                        if (destroyed) return;
                        refreshPrivacy();
                        requestBanner();
                    });
                }, error -> {
                    if (destroyed) return;
                    refreshPrivacy();
                    requestBanner(); // UMP may permit use of consent from a previous session.
                });
    }

    private void refreshPrivacy() {
        // Policy stays accessible even when UMP has no privacy-options form.
        privacy.setVisibility(View.VISIBLE);
    }

    private void requestBanner() {
        if (!ageScreenComplete || paused || destroyed || privacyFormOpen || initializing || banner != null || !consent.canRequestAds()) return;
        initializing = true;
        new Thread(() -> MobileAds.initialize(activity.getApplicationContext(), status ->
                activity.runOnUiThread(() -> {
                    initializing = false;
                    if (paused || destroyed || privacyFormOpen || activity.isFinishing() || !consent.canRequestAds()) return;
                    slot.post(() -> {
                        if (paused || destroyed || privacyFormOpen || banner != null || !consent.canRequestAds()) return;
                        if (slot.getWidth() < dp(320)) return;
                        banner = new AdView(activity);
                        banner.setAdSize(AdSize.BANNER);
                        banner.setAdUnitId(activity.getString(R.string.admob_banner_id));
                        slot.addView(banner, new FrameLayout.LayoutParams(dp(320), dp(50), Gravity.CENTER));
                        banner.loadAd(new AdRequest.Builder().build());
                        if (paused) banner.pause();
                    });
                })), "admob-init").start();
    }

    void pause() { paused = true; if (banner != null) banner.pause(); }
    void resume() {
        paused = false;
        if (banner != null) banner.resume();
        else requestBanner(); // Retry initialization deferred while another screen was visible.
    }
    void destroy() {
        destroyed = true;
        if (ageDialog != null) ageDialog.dismiss();
        removeBanner();
    }
    private void removeBanner() {
        if (banner != null) {
            slot.removeView(banner);
            banner.destroy();
            banner = null;
        }
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
