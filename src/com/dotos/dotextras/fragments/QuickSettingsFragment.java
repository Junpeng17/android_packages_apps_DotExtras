package com.dotos.dotextras.fragments;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.preference.ListPreference;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import android.preference.Preference.OnPreferenceChangeListener;
import com.dotos.dotextras.preferences.CMSystemSettingListPreference;

import com.dotos.R;

import java.util.List;
import java.util.ArrayList;

public class QuickSettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String STATUS_BAR_QUICK_QS_PULLDOWN = "qs_quick_pulldown";
    private static final String PREF_ROWS_PORTRAIT = "qs_rows_portrait";
    private static final String PREF_ROWS_LANDSCAPE = "qs_rows_landscape";
    private static final String PREF_COLUMNS = "qs_columns";
    private static final String DAYLIGHT_HEADER_PACK = "daylight_header_pack";
    private static final String DEFAULT_HEADER_PACKAGE = "com.android.systemui";
    private static final String CUSTOM_HEADER_IMAGE_SHADOW = "status_bar_custom_header_shadow";
    private static final String CUSTOM_HEADER_PROVIDER = "custom_header_provider";
    private static final String CUSTOM_HEADER_BROWSE = "custom_header_browse";

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;

    private CMSystemSettingListPreference mQuickPulldown;
    private ListPreference mRowsPortrait;
    private ListPreference mRowsLandscape;
    private ListPreference mQsColumns;
    private ListPreference mDaylightHeaderPack;
    private SeekBarPreference mHeaderShadow;
    private ListPreference mHeaderProvider;
    private String mDaylightHeaderProvider;
    private PreferenceScreen mHeaderBrowse;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.dot_quicksettings);

	mQuickPulldown =
                (CMSystemSettingListPreference) findPreference(STATUS_BAR_QUICK_QS_PULLDOWN);
        mQuickPulldown.setOnPreferenceChangeListener(this);
        updateQuickPulldownSummary(mQuickPulldown.getIntValue(0));

	mRowsPortrait = (ListPreference) findPreference(PREF_ROWS_PORTRAIT);
        int rowsPortrait = Settings.Secure.getInt(resolver,
                Settings.Secure.QS_ROWS_PORTRAIT, 2);
        mRowsPortrait.setValue(String.valueOf(rowsPortrait));
        mRowsPortrait.setSummary(mRowsPortrait.getEntry());
        mRowsPortrait.setOnPreferenceChangeListener(this);

        int defaultValue = getResources().getInteger(com.android.internal.R.integer.config_qs_num_rows_landscape_default);
        mRowsLandscape = (ListPreference) findPreference(PREF_ROWS_LANDSCAPE);
        int rowsLandscape = Settings.Secure.getInt(resolver,
                Settings.Secure.QS_ROWS_LANDSCAPE, defaultValue);
        mRowsLandscape.setValue(String.valueOf(rowsLandscape));
        mRowsLandscape.setSummary(mRowsLandscape.getEntry());
        mRowsLandscape.setOnPreferenceChangeListener(this);

        mQsColumns = (ListPreference) findPreference(PREF_COLUMNS);
        int columnsQs = Settings.Secure.getInt(resolver,
                Settings.Secure.QS_COLUMNS, 4);
        mQsColumns.setValue(String.valueOf(columnsQs));
        mQsColumns.setSummary(mQsColumns.getEntry());
        mQsColumns.setOnPreferenceChangeListener(this);

	// headers
        String settingHeaderPackage = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK);
        if (settingHeaderPackage == null) {
            settingHeaderPackage = DEFAULT_HEADER_PACKAGE;
        }
        mDaylightHeaderPack = (ListPreference) findPreference(DAYLIGHT_HEADER_PACK);

        List<String> entries = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        getAvailableHeaderPacks(entries, values);
        mDaylightHeaderPack.setEntries(entries.toArray(new String[entries.size()]));
        mDaylightHeaderPack.setEntryValues(values.toArray(new String[values.size()]));

        int valueIndex = mDaylightHeaderPack.findIndexOfValue(settingHeaderPackage);
        if (valueIndex == -1) {
            // no longer found
            settingHeaderPackage = DEFAULT_HEADER_PACKAGE;
            Settings.System.putString(resolver,
                    Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK, settingHeaderPackage);
            valueIndex = mDaylightHeaderPack.findIndexOfValue(settingHeaderPackage);
        }
        mDaylightHeaderPack.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
        mDaylightHeaderPack.setSummary(mDaylightHeaderPack.getEntry());
        mDaylightHeaderPack.setOnPreferenceChangeListener(this);

        mHeaderShadow = (SeekBarPreference) findPreference(CUSTOM_HEADER_IMAGE_SHADOW);
        int headerShadow = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, 80);
        mHeaderShadow.setValue(headerShadow);
        mHeaderShadow.setOnPreferenceChangeListener(this);

        mDaylightHeaderProvider = getResources().getString(R.string.daylight_header_provider);
        String providerName = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER);
        if (providerName == null) {
            providerName = mDaylightHeaderProvider;
        }
        mHeaderProvider = (ListPreference) findPreference(CUSTOM_HEADER_PROVIDER);
        valueIndex = mHeaderProvider.findIndexOfValue(providerName);
        mHeaderProvider.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
        mHeaderProvider.setSummary(mHeaderProvider.getEntry());
        mHeaderProvider.setOnPreferenceChangeListener(this);
        mDaylightHeaderPack.setEnabled(providerName.equals(mDaylightHeaderProvider));

        mHeaderBrowse = (PreferenceScreen) findPreference(CUSTOM_HEADER_BROWSE);
        mHeaderBrowse.setEnabled(isBrowseHeaderAvailable());

    }

    @Override
    public void onResume() {
        super.onResume();

        // Adjust status bar preferences for RTL
        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            mQuickPulldown.setEntries(R.array.status_bar_quick_qs_pulldown_entries_rtl);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
	ContentResolver resolver = getActivity().getContentResolver();
        int value = Integer.parseInt((String) newValue);
        int intValue;
        int index;
	if (preference == mQuickPulldown) {
            updateQuickPulldownSummary(value);
        } else if (preference == mRowsPortrait) {
            intValue = Integer.valueOf((String) newValue);
            index = mRowsPortrait.findIndexOfValue((String) newValue);
            Settings.Secure.putInt(resolver,
                    Settings.Secure.QS_ROWS_PORTRAIT, intValue);
            preference.setSummary(mRowsPortrait.getEntries()[index]);
            return true;
        } else if (preference == mRowsLandscape) {
            intValue = Integer.valueOf((String) newValue);
            index = mRowsLandscape.findIndexOfValue((String) newValue);
            Settings.Secure.putInt(resolver,
                    Settings.Secure.QS_ROWS_LANDSCAPE, intValue);
            preference.setSummary(mRowsLandscape.getEntries()[index]);
            return true;
        } else if (preference == mQsColumns) {
            intValue = Integer.valueOf((String) newValue);
            index = mQsColumns.findIndexOfValue((String) newValue);
            Settings.Secure.putInt(resolver,
                    Settings.Secure.QS_COLUMNS, intValue);
            preference.setSummary(mQsColumns.getEntries()[index]);
            return true;
        } else if (preference == mDaylightHeaderPack) {
            String value = (String) newValue;
            Settings.System.putString(resolver,
                    Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK, value);
            index = mDaylightHeaderPack.findIndexOfValue(value);
            mDaylightHeaderPack.setSummary(mDaylightHeaderPack.getEntries()[index]);
            return true;
        } else if (preference == mHeaderShadow) {
            intValue = (Integer) newValue;
            Settings.System.putInt(resolver,
                    Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, intValue);
            return true;
        } else if (preference == mHeaderProvider) {
            String value = (String) newValue;
            Settings.System.putString(resolver,
                    Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, value);
            index = mHeaderProvider.findIndexOfValue(value);
            mHeaderProvider.setSummary(mHeaderProvider.getEntries()[index]);
            mDaylightHeaderPack.setEnabled(value.equals(mDaylightHeaderProvider));
            return true;
        }
        return false;
    }

    private void getAvailableHeaderPacks(List<String> entries, List<String> values) {
        Intent i = new Intent();
        PackageManager packageManager = getPackageManager();
        i.setAction("org.omnirom.DaylightHeaderPack");
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            if (packageName.equals(DEFAULT_HEADER_PACKAGE)) {
                values.add(0, packageName);
            } else {
                values.add(packageName);
            }
            String label = r.activityInfo.loadLabel(getPackageManager()).toString();
            if (label == null) {
                label = r.activityInfo.packageName;
            }
            if (packageName.equals(DEFAULT_HEADER_PACKAGE)) {
                entries.add(0, label);
            } else {
                entries.add(label);
            }
        }
        i.setAction("org.omnirom.DaylightHeaderPack1");
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            values.add(packageName  + "/" + r.activityInfo.name);

            String label = r.activityInfo.loadLabel(getPackageManager()).toString();
            if (label == null) {
                label = packageName;
            }
            entries.add(label);
        }
    }

    private boolean isBrowseHeaderAvailable() {
        PackageManager pm = getPackageManager();
        Intent browse = new Intent();
        browse.setClassName("org.omnirom.omnistyle", "org.omnirom.omnistyle.BrowseHeaderActivity");
        return pm.resolveActivity(browse, 0) != null;
    }

    private void updateQuickPulldownSummary(int value) {
        String summary="";
        switch (value) {
            case PULLDOWN_DIR_NONE:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_off);
                break;

            case PULLDOWN_DIR_LEFT:
            case PULLDOWN_DIR_RIGHT:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_summary,
                    getResources().getString(value == PULLDOWN_DIR_LEFT
                        ? R.string.status_bar_quick_qs_pulldown_summary_left
                        : R.string.status_bar_quick_qs_pulldown_summary_right));
                break;
        }
        mQuickPulldown.setSummary(summary);
    }
}