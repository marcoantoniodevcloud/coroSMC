package com.garethevans.church.opensongtablet.songprocessing;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.garethevans.church.opensongtablet.R;
import com.garethevans.church.opensongtablet.customviews.MyMaterialSimpleTextView;
import com.garethevans.church.opensongtablet.drummer.DrumCalculations;
import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;

public class SongSheetHeaders {

    private final Context c;
    @SuppressWarnings({"unused","FieldCanBeLocal"})
    private final String TAG = "SongSheetHeaders";
    private final MainActivityInterface mainActivityInterface;
    private boolean forExport;
    @SuppressWarnings({"unused","FieldCanBeLocal"})
    private String songSheetHTML= "";
    @SuppressWarnings({"unused","FieldCanBeLocal"})
    private LinearLayout linearLayout;

    public SongSheetHeaders(Context c) {
        this.c = c;
        mainActivityInterface = (MainActivityInterface) c;
    }

    public LinearLayout getSongSheet(Song thisSong, float commentScaling,  int textColor) {
        // Rather than assuming it is the current song, we get passed the song current or otherwise
        // This allows on the fly processing of other songs not processed (e.g. as part of a set)
        // IV - Uses SongSelect tricks of bold, simple separators and field names - to enhance readability
        linearLayout = new LinearLayout(c);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(Gravity.CENTER_VERTICAL);
        linearLayout.setPadding(dp(8), dp(6), dp(8), dp(6));
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        Typeface typeface = mainActivityInterface.getMyFonts().getLyricFont();

        String title = thisSong.getTitle();
        String author = thisSong.getAuthor();
        String description = thisSong.getCopyright();
        LiturgicalSeason seasonColors = LiturgicalSeason.fromValue(thisSong.getLiturgicalSeason());

        float defFontSize;
        if (forExport) {
            defFontSize = mainActivityInterface.getProcessSong().getDefFontSize();
        } else {
            defFontSize = 12f;
        }

        songSheetHTML = "";

        MyMaterialSimpleTextView season = getSongSheetTexts(c.getString(seasonColors.getBadgeResource()),
                typeface, seasonColors.getTextColor(), Math.max(10f, defFontSize * commentScaling));
        season.setBackgroundColor(seasonColors.getBadgeBackgroundColor());
        season.setPadding(dp(8), dp(5), dp(8), dp(5));
        season.setGravity(Gravity.CENTER);
        linearLayout.addView(season, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout details = new LinearLayout(c);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        detailsParams.setMargins(dp(10), 0, dp(8), 0);

        if (title!=null && !title.isEmpty()) {
            MyMaterialSimpleTextView textView = getSongSheetTexts(title,typeface,
                    seasonColors.getTextColor(),defFontSize*1.5f);
            textView.setPaintFlags(textView.getPaintFlags() | Paint.FAKE_BOLD_TEXT_FLAG);
            textView.setTypeface(textView.getTypeface(),Typeface.BOLD);
            details.addView(textView);
        }
        if (author!=null && !author.isEmpty()) {
            details.addView(getSongSheetTexts(author,typeface,textColor,defFontSize*commentScaling));
            songSheetHTML += author+ "<br>\n";
        }
        if (description!=null && !description.isEmpty()) {
            details.addView(getSongSheetTexts(description,typeface,textColor,defFontSize*commentScaling));
            songSheetHTML += description + "<br>\n";
        }

        String keyCapoTempo = getKeyCapoTempo(thisSong);

        if (!keyCapoTempo.isEmpty()) {
            MyMaterialSimpleTextView textView = getSongSheetTexts(keyCapoTempo.trim(),typeface,textColor,defFontSize*commentScaling);
            textView.setPaintFlags(textView.getPaintFlags() | Paint.FAKE_BOLD_TEXT_FLAG);
            textView.setTypeface(textView.getTypeface(),Typeface.BOLD);
            details.addView(textView);
            songSheetHTML += keyCapoTempo + "<br>\n";
        }

        linearLayout.addView(details, detailsParams);
        Uri logoUri = mainActivityInterface.getPresenterSettings().getLogo();
        if (logoUri != null) {
            ImageView logo = new ImageView(c);
            logo.setContentDescription(c.getString(R.string.app_name));
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try {
                logo.setImageURI(logoUri);
                linearLayout.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));
            } catch (RuntimeException e) {
                android.util.Log.e(TAG, "Unable to load song sheet logo", e);
            }
        }

        return linearLayout;
    }

    private int dp(float value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public MyMaterialSimpleTextView getSongSheetTexts(String content, Typeface typeface, int color, float size) {
        MyMaterialSimpleTextView textView = new MyMaterialSimpleTextView(c);
        textView.setTypeface(typeface);
        textView.setTextColor(color);
        textView.setTextSize(size);
        textView.setText(content);
        return textView;
    }

    public String getKeyCapoTempo(Song thisSong) {
        String key = thisSong.getKey();
        String capo = thisSong.getCapo();
        String tempo = DrumCalculations.getFixedTempoString(thisSong.getTempo(),false);
        String timesig = DrumCalculations.getFixedTimeSignatureString(thisSong.getTimesig(),false);

        String keyCapoTempo = "";

        if (capo!=null && !capo.isEmpty()) {
            keyCapoTempo += "| " + c.getString(R.string.capo) + " - " + capo + " ";
            keyCapoTempo += ("(" + mainActivityInterface.getTranspose().capoKeyTranspose(thisSong) + ") ").replace(" ()","");
        }

        if (key!=null && !key.isEmpty()) {
            keyCapoTempo += "| " + c.getString(R.string.key) + " - " + key + " ";
        }

        if (tempo!=null && !tempo.isEmpty()) {
            keyCapoTempo += "| " + c.getString(R.string.tempo) + " - " + tempo + " ";
        }

        if (timesig!=null && !timesig.isEmpty()) {
            keyCapoTempo += "| " + c.getString(R.string.time) + " - " + timesig + " ";
        }

        keyCapoTempo = keyCapoTempo.trim().replaceFirst("\\| ","");

        return keyCapoTempo;
    }

    public void setForExport(boolean forExport) {
        this.forExport = forExport;
    }

    public String getSongSheetTitleMainHTML(Song thisSong) {
        // This will generate a separate LinearLayout containing the songsheet info
        String title = thisSong.getTitle();

        if (title!=null && !title.isEmpty()) {
            return "<div class=\"titlemain\">" + title + "</div>\n";
        } else {
            return "";
        }
    }
    public String getSongSheetTitleExtrasHTML(Song thisSong) {
        // This will generate a separate LinearLayout containing the songsheet info
        String author = thisSong.getAuthor();
        String copyright = thisSong.getCopyright();
        String keyCapoTempo = getKeyCapoTempo(thisSong);
        String extras = "";
        if (author!=null && !author.isEmpty()) {
            extras += "<div class=\"titleextras\">" + author + "</div>\n";
        }
        if (copyright!=null && !copyright.isEmpty()) {
            extras += "<div class=\"titleextras\">" + copyright + "</div>\n";
        }
        if (!keyCapoTempo.isEmpty()) {
            extras += "<div class=\"titleextras\">" + keyCapoTempo + "</div>\n";
        }

        return extras;
    }
}
