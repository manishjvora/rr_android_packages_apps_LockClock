/*
 * Copyright (C) 2012 The CyanogenMod Project (DvTonder)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.lockclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import com.cyanogenmod.lockclock.misc.Constants;
import static com.cyanogenmod.lockclock.misc.Constants.PREF_NAME;
import com.cyanogenmod.lockclock.weather.*;

import java.util.ArrayList;
import java.util.List;

public class Preferences extends PreferenceActivity implements
    OnPreferenceClickListener, OnSharedPreferenceChangeListener {
    private static final String TAG = "Preferences";

    private static final int LOC_WARNING = 101;
    private static final int WEATHER_CHECK = 0;

    private CheckBoxPreference mUseCustomLoc;
    private CheckBoxPreference mUseMetric;
    private CheckBoxPreference mShowLocation;
    private CheckBoxPreference mShowTimestamp;
    private EditTextPreference mCustomWeatherLoc;
    private MultiSelectListPreference mCalendarList;

    private Context mContext;
    private ContentResolver mResolver;
    private ProgressDialog mProgressDialog;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(PREF_NAME);
        addPreferencesFromResource(R.xml.widget_prefs);
        mContext = this;
        mResolver = getContentResolver();

        // Load the required settings from preferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);

        // Some preferences need to be set to a default value in code since we cannot do them in XML
        mUseMetric = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_METRIC);
        mUseMetric.setChecked(prefs.getBoolean(Constants.WEATHER_USE_METRIC, true));
        mShowLocation = (CheckBoxPreference) findPreference(Constants.WEATHER_SHOW_LOCATION);
        mShowLocation.setChecked(prefs.getBoolean(Constants.WEATHER_SHOW_LOCATION, true));
        mShowTimestamp = (CheckBoxPreference) findPreference(Constants.WEATHER_SHOW_TIMESTAMP);
        mShowTimestamp.setChecked(prefs.getBoolean(Constants.WEATHER_SHOW_TIMESTAMP, true));

        // Load items that need custom summaries etc.
        mUseCustomLoc = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_CUSTOM_LOCATION);
        mCustomWeatherLoc = (EditTextPreference) findPreference(Constants.WEATHER_CUSTOM_LOCATION_STRING);
        updateLocationSummary();
        mCustomWeatherLoc.setOnPreferenceClickListener(this);

        // Show a warning if location manager is disabled and there is no custom location set
        if (!Settings.Secure.isLocationProviderEnabled(mResolver,
                LocationManager.NETWORK_PROVIDER)
                && !mUseCustomLoc.isChecked()) {
            showDialog(LOC_WARNING);
        }

        // The calendar list entries and values are determined at run time, not in XML
        mCalendarList = (MultiSelectListPreference) findPreference(Constants.CALENDAR_LIST);
        CalendarEntries calEntries = CalendarEntries.findCalendars(this);
        mCalendarList.setEntries(calEntries.getEntries());
        mCalendarList.setEntryValues(calEntries.getEntryValues());

        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        Preference pref = findPreference(key);
        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }
        Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
        sendBroadcast(updateIntent);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {

        if (preference == mCustomWeatherLoc) {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
            String location = prefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, "");
            mCustomWeatherLoc.getEditText().setText(location);
            mCustomWeatherLoc.getEditText().setSelection(location.length());

            mCustomWeatherLoc.getDialog().findViewById(android.R.id.button1)
            .setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mProgressDialog = new ProgressDialog(mContext);
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    mProgressDialog.setMessage(mContext.getString(R.string.weather_progress_title));
                    mProgressDialog.show();
                    new Thread(new Runnable(){
                        @Override
                        public void run() {
                            String woeid = null;
                            try {
                                woeid = YahooPlaceFinder.GeoCode(mContext,
                                        mCustomWeatherLoc.getEditText().getText().toString());
                            } catch (Exception e) {
                            }
                            Message msg = Message.obtain();
                            msg.what = WEATHER_CHECK;
                            msg.obj = woeid;
                            mHandler.sendMessage(msg);
                        }
                    }).start();
                }
            });
            return true;
        }
        return false;
    }

    //===============================================================================================
    // Utility classes and supporting methods
    //===============================================================================================

    private void updateLocationSummary() {
        if (mUseCustomLoc.isChecked()) {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
            String location = prefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, 
                    getResources().getString(R.string.unknown));
            mCustomWeatherLoc.setSummary(location);
        } else {
            mCustomWeatherLoc.setSummary(R.string.weather_geolocated);
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case WEATHER_CHECK:
                if (msg.obj == null) {
                    Toast.makeText(mContext, mContext.getString(R.string.weather_retrieve_location_dialog_title),
                            Toast.LENGTH_SHORT).show();
                } else {
                    String cLoc = mCustomWeatherLoc.getEditText().getText().toString();
                    mCustomWeatherLoc.setText(cLoc);
                    SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
                    prefs.edit().putString(Constants.WEATHER_CUSTOM_LOCATION_STRING, cLoc).apply();
                    mCustomWeatherLoc.setSummary(cLoc);
                    mCustomWeatherLoc.getDialog().dismiss();
                }
                mProgressDialog.dismiss();
                break;
            }
        }
    };

    @Override
    public Dialog onCreateDialog(int dialogId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final Dialog dialog;

        switch (dialogId) {
            case LOC_WARNING:
                builder.setTitle(R.string.weather_retrieve_location_dialog_title);
                builder.setMessage(R.string.weather_retrieve_location_dialog_message);
                builder.setCancelable(false);
                builder.setPositiveButton(R.string.weather_retrieve_location_dialog_enable_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                Settings.Secure.setLocationProviderEnabled(mResolver,
                                        LocationManager.NETWORK_PROVIDER, true);
                            }
                        });
                builder.setNegativeButton(R.string.cancel, null);
                dialog = builder.create();
                break;
            default:
                dialog = null;
        }
        return dialog;
    }

    @SuppressWarnings("deprecation")
    private static class CalendarEntries {
        private final CharSequence[] mEntries;
        private final CharSequence[] mEntryValues;
        private static Uri uri = CalendarContract.Calendars.CONTENT_URI;

        // Calendar projection array
        private static String[] projection = new String[] {
               CalendarContract.Calendars._ID,
               CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        };

        // The indices for the projection array
        private static final int CALENDAR_ID_INDEX = 0;
        private static final int DISPLAY_NAME_INDEX = 1;

        static CalendarEntries findCalendars(Activity activity) {
            List<CharSequence> entries = new ArrayList<CharSequence>();
            List<CharSequence> entryValues = new ArrayList<CharSequence>();

            Cursor calendarCursor = activity.managedQuery(uri, projection, null, null, null);
            if (calendarCursor.moveToFirst()) {
                do {
                    entryValues.add(calendarCursor.getString(CALENDAR_ID_INDEX));
                    entries.add(calendarCursor.getString(DISPLAY_NAME_INDEX));
                } while (calendarCursor.moveToNext());
            }

            return new CalendarEntries(entries, entryValues);
        }

        private CalendarEntries(List<CharSequence> mEntries, List<CharSequence> mEntryValues) {
            this.mEntries = mEntries.toArray(new CharSequence[mEntries.size()]);
            this.mEntryValues = mEntryValues.toArray(new CharSequence[mEntryValues.size()]);
        }

        CharSequence[] getEntries() {
            return mEntries;
        }

        CharSequence[] getEntryValues() {
            return mEntryValues;
        }
    }
}
