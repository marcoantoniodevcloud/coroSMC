package com.garethevans.church.opensongtablet.songprocessing;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.garethevans.church.opensongtablet.R;
import com.garethevans.church.opensongtablet.customviews.ExposedDropDownArrayAdapter;
import com.garethevans.church.opensongtablet.customviews.MyMaterialSimpleTextView;
import com.garethevans.church.opensongtablet.databinding.EditSongTagsBinding;
import com.garethevans.church.opensongtablet.interfaces.EditSongFragmentInterface;
import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;

import java.util.ArrayList;

public class EditSongFragmentTags extends Fragment {

    private MainActivityInterface mainActivityInterface;
    private EditSongFragmentInterface editSongFragmentInterface;
    private EditSongTagsBinding myView;
    private PresentationOrderBottomSheet presentationOrderBottomSheet;
    private String midi_channel_string="";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mainActivityInterface = (MainActivityInterface) context;
        editSongFragmentInterface = (EditSongFragmentInterface) context;
        if (getActivity()!=null) {
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateViews();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        myView = EditSongTagsBinding.inflate(inflater, container, false);
        return myView.getRoot();
    }

    private void updateViews() {
        mainActivityInterface.getThreadPoolExecutor().execute(() -> {
            prepareStrings();

            // Set up the current values
            setupValues();

            // Set up the listeners
            mainActivityInterface.getMainHandler().post(this::setupListeners);
        });
    }
    private void prepareStrings() {
        if (getContext()!=null) {
            midi_channel_string = getContext().getString(R.string.midi_channel);
        }
    }

    private void setupValues() {
        if (mainActivityInterface.getTempSong()!=null) {
            presentationOrderBottomSheet = new PresentationOrderBottomSheet(this, "EditSongFragmentTags");
            mainActivityInterface.getMainHandler().post(() -> {
                if (myView!=null) {
                    myView.aka.setText(mainActivityInterface.getTempSong().getAka());
                    myView.ccli.setText(mainActivityInterface.getTempSong().getCcli());
                    myView.user1.setText(mainActivityInterface.getTempSong().getUser1());
                    myView.user2.setText(mainActivityInterface.getTempSong().getUser2());
                    myView.user3.setText(mainActivityInterface.getTempSong().getUser3());
                    myView.hymnnum.setText(mainActivityInterface.getTempSong().getHymnnum());
                    myView.midiIndex.setText(mainActivityInterface.getTempSong().getMidiindex());
                    myView.presorder.setFocusable(false);
                    myView.presorder.setText(mainActivityInterface.getTempSong().getPresentationorder());
                    myView.useImported.setChecked(mainActivityInterface.getBeatBuddy().getBeatBuddyUseImported());
                    myView.beatBuddySong.setText(mainActivityInterface.getTempSong().getBeatbuddysong());
                    myView.beatBuddyKit.setText(mainActivityInterface.getTempSong().getBeatbuddykit());
                    // Resize the bottom padding to the soft keyboard height or half the screen height for the soft keyboard (workaround)
                    mainActivityInterface.getWindowFlags().adjustViewPadding(mainActivityInterface, myView.resizeForKeyboardLayout);
                }
            });

            checkBeatBuddyValues();
        }
    }

    private void setupListeners() {
        myView.presorder.setOnClickListener(v -> {
            if (!presentationOrderBottomSheet.isAdded() && getActivity()!=null) {
                presentationOrderBottomSheet.show(getActivity().getSupportFragmentManager(), "PresentationOrderBottomSheet");
            }
        });
        myView.aka.addTextChangedListener(new MyTextWatcher("aka"));
        myView.ccli.addTextChangedListener(new MyTextWatcher("ccli"));
        myView.user1.addTextChangedListener(new MyTextWatcher("user1"));
        myView.user2.addTextChangedListener(new MyTextWatcher("user2"));
        myView.user3.addTextChangedListener(new MyTextWatcher("user3"));
        myView.hymnnum.addTextChangedListener(new MyTextWatcher("hymnnum"));
        myView.midiIndex.addTextChangedListener(new MyTextWatcher("midiindex"));

        myView.useImported.setOnCheckedChangeListener((compoundButton, b) -> {
            mainActivityInterface.getBeatBuddy().setBeatBuddyUseImported(b);
            checkBeatBuddyValues();
        });

        myView.beatBuddySong.addTextChangedListener(new MyTextWatcher("beatbuddysong"));
        myView.beatBuddyKit.addTextChangedListener(new MyTextWatcher("beatbuddykit"));

        // Scroll listener
        myView.tagsScrollView.setExtendedFabToAnimate(editSongFragmentInterface.getSaveButton());
    }

    private void checkBeatBuddyValues() {
        mainActivityInterface.getThreadPoolExecutor().execute(() -> {
            // Decide which songs and kits to use
            if (getContext()!=null && mainActivityInterface.getBeatBuddy().getBbsqLite()!=null) {
                try {
                    String tableSongs = mainActivityInterface.getBeatBuddy().getBbsqLite().TABLE_NAME_DEFAULT_SONGS;
                    String tableKits = mainActivityInterface.getBeatBuddy().getBbsqLite().TABLE_NAME_DEFAULT_DRUMS;
                    if (mainActivityInterface.getBeatBuddy().getBeatBuddyUseImported()) {
                        tableSongs = mainActivityInterface.getBeatBuddy().getBbsqLite().TABLE_NAME_MY_SONGS;
                        tableKits = mainActivityInterface.getBeatBuddy().getBbsqLite().TABLE_NAME_MY_DRUMS;
                    }
                    ArrayList<String> songs = mainActivityInterface.getBeatBuddy().getBbsqLite().getUnique(mainActivityInterface.getBeatBuddy().getBbsqLite().COLUMN_SONG_NAME, tableSongs);
                    ArrayList<String> kits = mainActivityInterface.getBeatBuddy().getBbsqLite().getUnique(mainActivityInterface.getBeatBuddy().getBbsqLite().COLUMN_KIT_NAME, tableKits);
                    mainActivityInterface.getMainHandler().post(() -> {
                        if (myView != null) {
                            ExposedDropDownArrayAdapter songsAdapter = new ExposedDropDownArrayAdapter(getContext(), myView.beatBuddySong, R.layout.view_exposed_dropdown_item, songs);
                            ExposedDropDownArrayAdapter kitsAdapter = new ExposedDropDownArrayAdapter(getContext(), myView.beatBuddyKit, R.layout.view_exposed_dropdown_item, kits);
                            myView.beatBuddySong.setAdapter(songsAdapter);
                            myView.beatBuddyKit.setAdapter(kitsAdapter);
                            // If we don't have a value, look for one and auto add it
                            if (mainActivityInterface.getTempSong().getFilename() != null && songs.contains(mainActivityInterface.getTempSong().getFilename().replace(",", ""))) {
                                mainActivityInterface.getTempSong().setBeatbuddysong(mainActivityInterface.getTempSong().getFilename().replace(",", ""));
                            } else if (mainActivityInterface.getTempSong().getTitle() != null && songs.contains(mainActivityInterface.getTempSong().getTitle().replace(",", ""))) {
                                mainActivityInterface.getTempSong().setBeatbuddysong(mainActivityInterface.getTempSong().getTitle().replace(",", ""));
                            }
                            if (mainActivityInterface.getTempSong().getBeatbuddysong() == null) {
                                mainActivityInterface.getTempSong().setBeatbuddysong("");
                            }
                            if (mainActivityInterface.getTempSong().getBeatbuddykit() == null) {
                                mainActivityInterface.getTempSong().setBeatbuddykit("");
                            }
                            myView.beatBuddySong.setText(mainActivityInterface.getTempSong().getBeatbuddysong());
                            myView.beatBuddyKit.setText(mainActivityInterface.getTempSong().getBeatbuddykit());
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    private class MyTextWatcher implements TextWatcher {

        String what;
        MyTextWatcher(String what) {
            this.what = what;
        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

        @Override
        public void afterTextChanged(Editable editable) {
            switch (what) {
                case "aka":
                    mainActivityInterface.getTempSong().setAka(editable.toString());
                    break;
                case "user1":
                    mainActivityInterface.getTempSong().setUser1(editable.toString());
                    break;
                case "user2":
                    mainActivityInterface.getTempSong().setUser2(editable.toString());
                    break;
                case "user3":
                    mainActivityInterface.getTempSong().setUser3(editable.toString());
                    break;
                case "ccli":
                    mainActivityInterface.getTempSong().setCcli(editable.toString());
                    break;
                case "presorder":
                    mainActivityInterface.getTempSong().setPresentationorder(editable.toString());
                    break;
                case "beatbuddysong":
                    mainActivityInterface.getTempSong().setBeatbuddysong(editable.toString());
                    break;
                case "beatbuddykit":
                    mainActivityInterface.getTempSong().setBeatbuddykit(editable.toString());
                    break;
                case "midiindex":
                    String originalMidiIndexString = editable.toString();
                    String newMidiIndexString = originalMidiIndexString.replaceAll("\\D","");
                    int val = -1;
                    Song foundSong = null;
                    if (!newMidiIndexString.isEmpty()) {
                        val = Integer.parseInt(newMidiIndexString);
                        if (val<0 || val>16000) {
                            newMidiIndexString = "";
                            myView.midiIndexInfo.setHint(null);
                            myView.midiIndexInfo.setTextColor(mainActivityInterface.getPalette().textColor);
                        } else {
                            // Check that no other song has this MIDI index already
                            foundSong = mainActivityInterface.getSQLiteHelper().getSongFromMidiIndex(val);
                        }
                    }
                    if (!originalMidiIndexString.equals(newMidiIndexString)) {
                        myView.midiIndex.setText(newMidiIndexString);
                    }

                    if (foundSong!=null) {
                        // Song exists, so show a warning
                        String message = val+" = "+foundSong.getFolder() + "/" + foundSong.getFilename();
                        myView.midiIndexInfo.setHint(message);
                        myView.midiIndexInfo.setTextColor(mainActivityInterface.getPalette().errorColor);
                    } else if (val>=0 && val<16000){
                        // All good with this song code
                        // Get the MIDI code from the midi index
                        // Subtract 1 as MIDI ranges from 0-127, we want numbers starting at 1 for the song midi index
                        // Divide by 127 to get the MSB value and the remainder is the PC
                        double msbVal = Math.floorDiv(val-1,127);
                        double pcVal = Math.floorMod(val-1,127);
                        String midiCode = midi_channel_string + ":" +
                                mainActivityInterface.getMidi().getMidiInputChannelSong() +
                                " MSB:" + msbVal + " PC:" + pcVal;
                        myView.midiIndexInfo.setHint(midiCode);
                        myView.midiIndexInfo.setTextColor(mainActivityInterface.getPalette().textColor);
                    }
                    mainActivityInterface.getTempSong().setMidiindex(editable.toString());
            }
        }
    }

    public void updateValue() {
        // Updating the presentation order from its bottom sheet callback
        myView.presorder.setText(mainActivityInterface.getTempSong().getPresentationorder());
    }

    @Override
    public void onDetach() {
        super.onDetach();
        myView = null;
        mainActivityInterface = null;
        editSongFragmentInterface = null;
    }
}