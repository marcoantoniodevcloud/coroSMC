package com.garethevans.church.opensongtablet.songprocessing;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
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
        // This header is shared by performance mode and the PDF/print export layouts.
        linearLayout = new LinearLayout(c);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.setPadding(dp(8), dp(4), dp(8), dp(6));
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        Typeface typeface = mainActivityInterface.getMyFonts().getLyricFont();

        String title = thisSong.getTitle();
        String author = thisSong.getAuthor();
        String album = thisSong.getCopyright();
        LiturgicalSeason seasonColors = LiturgicalSeason.fromValue(thisSong.getLiturgicalSeason());

        float defFontSize;
        if (forExport) {
            defFontSize = mainActivityInterface.getProcessSong().getDefFontSize();
        } else {
            defFontSize = 12f;
        }

        songSheetHTML = "";

        int seasonColor = seasonColors.getTextColor();
        LinearLayout seasonRow = new LinearLayout(c);
        seasonRow.setOrientation(LinearLayout.HORIZONTAL);
        seasonRow.setGravity(Gravity.CENTER_VERTICAL);
        View seasonRule = new View(c);
        seasonRule.setBackgroundColor(seasonColor);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(0, dp(3), 1f);
        ruleParams.setMargins(0, 0, dp(6), 0);
        seasonRow.addView(seasonRule, ruleParams);

        MyMaterialSimpleTextView season = getSongSheetTexts(
                c.getString(seasonColors.getBadgeResource()), typeface, 0xFFFFFFFF,
                Math.max(8f, defFontSize * 0.72f));
        season.setGravity(Gravity.CENTER);
        season.setPadding(dp(9), dp(3), dp(9), dp(3));
        GradientDrawable seasonBadge = new GradientDrawable();
        seasonBadge.setColor(seasonColor);
        seasonBadge.setCornerRadius(dp(18));
        season.setBackground(seasonBadge);
        seasonRow.addView(season, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        linearLayout.addView(seasonRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout brandRow = new LinearLayout(c);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brandParams.topMargin = dp(3);
        linearLayout.addView(brandRow, brandParams);

        ImageView logo = new ImageView(c);
        logo.setContentDescription(c.getString(R.string.logo));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImageResource(R.drawable.logo_header);
        brandRow.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout brandText = new LinearLayout(c);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brandTextParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        brandTextParams.setMargins(dp(8), 0, 0, 0);
        brandRow.addView(brandText, brandTextParams);

        MyMaterialSimpleTextView choirName = getSongSheetTexts(
                c.getString(R.string.header_choir_name), typeface, 0xFF174C7D,
                Math.max(10f, defFontSize * 0.95f));
        choirName.setPaintFlags(choirName.getPaintFlags() | Paint.FAKE_BOLD_TEXT_FLAG);
        choirName.setTypeface(choirName.getTypeface(), Typeface.BOLD);
        brandText.addView(choirName);
        brandText.addView(getSongSheetTexts(c.getString(R.string.header_choir_subtitle),
                typeface, 0xFF174C7D, Math.max(7f, defFontSize * 0.68f)));

        if (title != null && !title.isEmpty()) {
            MyMaterialSimpleTextView titleView = getSongSheetTexts(title, typeface, 0xFF174C7D,
                    Math.max(18f, defFontSize * 2f));
            titleView.setGravity(Gravity.CENTER);
            titleView.setPaintFlags(titleView.getPaintFlags() | Paint.FAKE_BOLD_TEXT_FLAG);
            titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = dp(1);
            linearLayout.addView(titleView, titleParams);
        }

        String displayAuthor = author == null || author.trim().isEmpty()
                ? c.getString(R.string.header_to_be_confirmed) : author;
        String displayAlbum = album == null || album.trim().isEmpty()
                ? c.getString(R.string.header_to_be_confirmed) : album;
        MyMaterialSimpleTextView metadata = getSongSheetTexts(
                c.getString(R.string.header_song_metadata, displayAuthor, displayAlbum),
                typeface, 0xFF174C7D, Math.max(8f, defFontSize * 0.78f * commentScaling));
        metadata.setGravity(Gravity.CENTER);
        linearLayout.addView(metadata, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        String keyCapoTempo = getKeyCapoTempo(thisSong);
        if (!keyCapoTempo.isEmpty()) {
            MyMaterialSimpleTextView songDetails = getSongSheetTexts(keyCapoTempo.trim(), typeface,
                    textColor, Math.max(8f, defFontSize * 0.72f * commentScaling));
            songDetails.setGravity(Gravity.CENTER);
            linearLayout.addView(songDetails, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            songSheetHTML += keyCapoTempo + "<br>\n";
        }

        songSheetHTML = c.getString(R.string.header_song_metadata, displayAuthor, displayAlbum)
                + "<br>\n" + songSheetHTML;

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
        String displayAuthor = author == null || author.trim().isEmpty()
                ? c.getString(R.string.header_to_be_confirmed) : author;
        String displayAlbum = copyright == null || copyright.trim().isEmpty()
                ? c.getString(R.string.header_to_be_confirmed) : copyright;
        extras += "<div class=\"titleextras\">"
                + c.getString(R.string.header_song_metadata, displayAuthor, displayAlbum)
                + "</div>\n";
        LiturgicalSeason season = LiturgicalSeason.fromValue(thisSong.getLiturgicalSeason());
        extras += "<div class=\"titleextras\">" + c.getString(season.getBadgeResource())
                + "</div>\n";
        if (!keyCapoTempo.isEmpty()) {
            extras += "<div class=\"titleextras\">" + keyCapoTempo + "</div>\n";
        }

        return extras;
    }
}
