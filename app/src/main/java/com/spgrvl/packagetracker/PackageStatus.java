package com.spgrvl.packagetracker;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Delivery stage derived from a carrier's status text, shared by the packages list and the
 * details timeline so both show the same label and color.
 */
public enum PackageStatus {
    ISSUE(R.string.issue, R.color.error_red),
    DELIVERED(R.string.delivered, R.color.success_green),
    OUT_FOR_DELIVERY(R.string.out_for_delivery, R.color.status_out_for_delivery),
    PENDING(R.string.pending, R.color.status_pending),
    COMPLETED(R.string.status_completed, R.color.status_pending),
    IN_TRANSIT(R.string.in_transit, R.color.accent_blue);

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{Mn}+");

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
        return classify(completed, statusText,
                context.getResources().getStringArray(R.array.status_keywords_problem),
                context.getResources().getStringArray(R.array.status_keywords_delivered),
                context.getResources().getStringArray(R.array.status_keywords_not_delivered),
                context.getResources().getStringArray(R.array.status_keywords_out_for_delivery));
    }

    /** Resource-free core of {@link #of}, so it can be unit tested with plain keyword arrays. */
    static PackageStatus classify(boolean completed, String statusText, String[] problemKeywords,
                                  String[] deliveredKeywords, String[] notDeliveredKeywords,
                                  String[] outForDeliveryKeywords) {
        // Checked before "delivered" so texts like "not delivered" count as a problem
        if (matchesKeywords(statusText, problemKeywords)) {
            return ISSUE;
        }
        // e.g. Box Now's "Being delivered" mentions delivery but the parcel hasn't arrived yet
        if (!matchesKeywords(statusText, notDeliveredKeywords) && matchesKeywords(statusText, deliveredKeywords)) {
            return DELIVERED;
        }
        // Marked as completed by the user without a delivered/problem status from the carrier
        if (completed) {
            return COMPLETED;
        }
        if (matchesKeywords(statusText, outForDeliveryKeywords)) {
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
    private static boolean matchesKeywords(String statusText, String[] keywords) {
        if (statusText == null || statusText.trim().isEmpty()) {
            return false;
        }
        String normalized = normalize(statusText);
        if (normalized.startsWith("status:")) {
            normalized = normalized.substring("status:".length()).trim();
        }
        for (String kw : keywords) {
            if (kw != null && !kw.isEmpty() && normalized.contains(normalize(kw))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lowercases and strips accents/diaeresis, and folds final sigma, so "ΠΑΡΑΔΟΘΗΚΕ",
     * "Παραδόθηκε" and "παραδοθηκε" all compare equal.
     */
    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").replace('ς', 'σ');
    }
}
