package com.garethevans.church.opensongtablet.appdata;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;

import java.util.Locale;

public class FixLocale {

    // Spanish is the default unless the user chooses another language.
    // If a user chooses a different language in the app, this is used regardless of the device settings

    @SuppressWarnings({"unused","FieldCanBeLocal"})
    private final String TAG = "FixLocale";
    private Locale userLocale;
    private String language;
    private final MainActivityInterface mainActivityInterface;


    public Locale getLocale() {
        return userLocale;
    }

    public FixLocale(Context c) {
        this.mainActivityInterface = (MainActivityInterface) c;
        getUpdatedPreferences();
    }

    // If we change load in a profile, this is called
    public void getUpdatedPreferences() {
        language = mainActivityInterface.getPreferences().getMyPreferenceString("language",null);
        setLocale();
    }

    public void setLocale() {
        // Locale
        boolean wasset = false;
        try {
            // Get the user's preference
            language = mainActivityInterface.getPreferences().getMyPreferenceString("language",null);

            // If this is already set, that's what we will use
            if (language!=null) {
                userLocale = new Locale(language);

            } else {
                language = "es";
                userLocale = new Locale(language);

            }

            // Load the appropriate translations
            // The old method no longer works on newer devices
            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(language);
            AppCompatDelegate.setApplicationLocales(appLocale);

            wasset = true;
        } catch (Exception e) {
            e.printStackTrace();
            userLocale = new Locale("en");
            language = "en";
        }

        if (!wasset) {
            try {
                LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(language);
                AppCompatDelegate.setApplicationLocales(appLocale);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}