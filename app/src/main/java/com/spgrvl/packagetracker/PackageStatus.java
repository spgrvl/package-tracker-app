package com.spgrvl.packagetracker;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * Delivery stage derived from a carrier's status text, shared by the packages list and the
 * details timeline so both show the same label and color.
 */
public enum PackageStatus {
    DELIVERED(R.string.delivered, R.color.success_green),
    OUT_FOR_DELIVERY(R.string.out_for_delivery, R.color.status_out_for_delivery),
    PENDING(R.string.pending, R.color.status_pending),
    IN_TRANSIT(R.string.in_transit, R.color.accent_blue);

    @StringRes
    public final int labelRes;
    @ColorRes
    private final int colorRes;

    PackageStatus(@StringRes int labelRes, @ColorRes int colorRes) {
        this.labelRes = labelRes;
        this.colorRes = colorRes;
    }

    @ColorInt
    public int getColor(Context context) {
        return ContextCompat.getColor(context, colorRes);
    }

    public static PackageStatus of(Context context, boolean completed, String statusText) {
        if (completed || matchesKeywords(context, statusText, R.array.status_keywords_delivered)) {
            return DELIVERED;
        }
        if (matchesKeywords(context, statusText, R.array.status_keywords_out_for_delivery)) {
            return OUT_FOR_DELIVERY;
        }
        if (statusText == null || statusText.trim().isEmpty() || statusText.equals("Status: None")) {
            return PENDING;
        }
        return IN_TRANSIT;
    }

    /**
     * Matches carrier status text against known phrases (English/Greek) so the stage
     * does not depend on a single hardcoded English substring.
     */
    private static boolean matchesKeywords(Context context, String statusText, int keywordsArrayRes) {
        if (statusText == null || statusText.trim().isEmpty()) {
            return false;
        }
        String normalized = statusText.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("status:")) {
            normalized = normalized.substring("status:".length()).trim();
        }
        String[] keywords = context.getResources().getStringArray(keywordsArrayRes);
        for (String kw : keywords) {
            if (kw != null && !kw.isEmpty() && normalized.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
