package com.garethevans.church.opensongtablet.songprocessing;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.garethevans.church.opensongtablet.R;
import com.garethevans.church.opensongtablet.customviews.ExposedDropDownArrayAdapter;
import com.garethevans.church.opensongtablet.customviews.MyMaterialSimpleTextView;
import com.garethevans.church.opensongtablet.databinding.EditSongMainBinding;
import com.garethevans.church.opensongtablet.interfaces.EditSongFragmentInterface;
import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;
import com.garethevans.church.opensongtablet.preferences.TextInputBottomSheet;
import com.garethevans.church.opensongtablet.tags.TagsBottomSheet;

import java.util.ArrayList;

public class EditSongFragmentMain extends Fragment  {

    // The variable used in this fragment
    private EditSongMainBinding myView;
    private MainActivityInterface mainActivityInterface;
    @SuppressWarnings({"unused","FieldCanBeLocal"})
    private final String TAG = "EditSongMain";
    private EditSongFragmentInterface editSongFragmentInterface;
    private String newFolder, new_folder_add_string="", new_folder_string="", new_folder_name_string="";
    private TextInputBottomSheet textInputBottomSheet;
    private TagsBottomSheet tagsBottomSheet;
    private ArrayList<String> folders;
    private ExposedDropDownArrayAdapter seasonAdapter;
    private ExposedDropDownArrayAdapter arrayAdapter;
    private String[] keyChoiceStrings = {};
    private String searchIndexWaitString = "";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mainActivityInterface = (MainActivityInterface) context;
        editSongFragmentInterface = (EditSongFragmentInterface) context;
    }

    @Override
    public void onResume() {
        super.onResume();
        mainActivityInterface.getThreadPoolExecutor().execute(() -> {
            prepareStrings();

            // Initialise the views
            setupValues();

            // Set listeners
            setUpListeners();

            if (folders == null || folders.isEmpty()) {
                getFoldersFromStorage();
                mainActivityInterface.getMainHandler().post(this::updateExposedDropdown);
            }
        });
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        myView = EditSongMainBinding.inflate(inflater, container, false);
        return myView.getRoot();
    }

    private void prepareStrings() {
        if (getContext()!=null) {
            new_folder_add_string = getString(R.string.new_folder_add);
            new_folder_string = getString(R.string.new_folder);
            new_folder_name_string = getString(R.string.new_folder_name);
            searchIndexWaitString = getString(R.string.index_songs_wait);
            keyChoiceStrings = getResources().getStringArray(R.array.key_choice);
            keyChoiceStrings = mainActivityInterface.getTranspose().fixTempKeys(keyChoiceStrings);
        }
    }

    // Initialise the views
    private void setupValues() {
        if (mainActivityInterface.getTempSong()==null) {
            mainActivityInterface.setTempSong(mainActivityInterface.getSong());
        }
        if (mainActivityInterface.getTempSong()!=null) {
            mainActivityInterface.getMainHandler().post(() -> {
                if (myView!=null) {
                    myView.title.setText(mainActivityInterface.getTempSong().getTitle());
                    myView.author.setText(mainActivityInterface.getTempSong().getAuthor());
                    myView.copyright.setText(mainActivityInterface.getTempSong().getCopyright());
                    Context context = getContext();
                    if (context != null) {
                        ExposedDropDownArrayAdapter keyAdapter = new ExposedDropDownArrayAdapter(
                                context, myView.key, R.layout.view_exposed_dropdown_item, keyChoiceStrings);
                        myView.key.setAdapter(keyAdapter);
                    }
                    myView.key.setText(mainActivityInterface.getTranspose().getFixedKey(
                            mainActivityInterface.getTempSong().getKey()));
                    updateTagsView();
                    myView.tags.setFocusable(false);
                    ArrayList<String> seasons = new ArrayList<>();
                    LiturgicalSeason[] liturgicalSeasons = LiturgicalSeason.values();
                    for (LiturgicalSeason season : liturgicalSeasons) {
                        seasons.add(getString(season.getNameResource()));
                    }
                    if (context != null) {
                        seasonAdapter = new ExposedDropDownArrayAdapter(context, myView.liturgicalSeason,
                                R.layout.view_exposed_dropdown_item, seasons);
                        myView.liturgicalSeason.setAdapter(seasonAdapter);
                    }
                    LiturgicalSeason selectedSeason = LiturgicalSeason.fromValue(
                            mainActivityInterface.getTempSong().getLiturgicalSeason());
                    myView.liturgicalSeason.setText(getString(selectedSeason.getNameResource()), false);
                    myView.filename.setText(mainActivityInterface.getTempSong().getFilename());

                }
            });

            newFolder = "+ " + new_folder_add_string;

            textInputBottomSheet = new TextInputBottomSheet(this, "EditSongFragmentMain", new_folder_string, new_folder_name_string, null, "", "", true);
            if (tagsBottomSheet == null) {
                tagsBottomSheet = new TagsBottomSheet(this, "EditSongFragmentMainTags");
            }

            // Resize the bottom padding to the soft keyboard height or half the screen height for the soft keyboard (workaround)
            mainActivityInterface.getMainHandler().post(() -> mainActivityInterface.getWindowFlags().adjustViewPadding(mainActivityInterface, myView.resizeForKeyboardLayout));
        }
    }

    // Sor the view visibility, listeners, etc.
    private void setUpListeners() {
        mainActivityInterface.getMainHandler().post(() -> {
            if (myView!=null) {
                myView.title.addTextChangedListener(new MyTextWatcher("title"));
                myView.folder.addTextChangedListener(new MyTextWatcher("folder"));
                myView.filename.addTextChangedListener(new MyTextWatcher("filename"));
                myView.author.addTextChangedListener(new MyTextWatcher("author"));
                myView.copyright.addTextChangedListener(new MyTextWatcher("copyright"));
                myView.key.addTextChangedListener(new MyTextWatcher("key"));
                myView.tags.setOnClickListener(v -> {
                    if (mainActivityInterface.getSongListBuildIndex().getIndexComplete()) {
                        if (tagsBottomSheet != null && !tagsBottomSheet.isAdded() && getActivity() != null) {
                            tagsBottomSheet.show(getActivity().getSupportFragmentManager(), "ThemesBottomSheet");
                        }
                    } else {
                        String progressText = "";
                        if (mainActivityInterface.getSongMenuFragment() != null) {
                            MyMaterialSimpleTextView progressView =
                                    mainActivityInterface.getSongMenuFragment().getProgressText();
                            if (progressView != null && progressView.getText() != null) {
                                progressText = " " + progressView.getText().toString();
                            }
                        }
                        mainActivityInterface.getShowToast().doIt(searchIndexWaitString + progressText);
                    }
                });
                myView.liturgicalSeason.getAutoCompleteTextView().setOnItemClickListener((parent, view, position, id) ->
                        mainActivityInterface.getTempSong().setLiturgicalSeason(
                                LiturgicalSeason.values()[position].getValue()));

                // Scroll listener
                myView.mainScrollView.setExtendedFabToAnimate(editSongFragmentInterface.getSaveButton());
            }
        });
    }

    private void updateExposedDropdown() {
        folders.add(newFolder);

        if (getContext() != null) {
            arrayAdapter = new ExposedDropDownArrayAdapter(getContext(), myView.folder, R.layout.view_exposed_dropdown_item, folders);
            mainActivityInterface.getMainHandler().post(() -> {
                if (myView != null) {
                    myView.folder.setAdapter(arrayAdapter);
                    myView.folder.setText(mainActivityInterface.getTempSong().getFolder());
                }
            });
        }
    }

    private class MyTextWatcher implements TextWatcher {

        String what;
        String value = "";

        MyTextWatcher(String what) {
            this.what = what;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s != null) {
                value = s.toString();
                if (what.equals("filename")) {
                    value = mainActivityInterface.getStorageAccess().safeFilename(value);
                }
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (what.equals("folder") && value.equals(newFolder) && getActivity()!=null) {
                myView.folder.setText(mainActivityInterface.getTempSong().getFolder());
                showFolderInput();
            } else {
                updateTempSong();
            }
        }

        public void updateTempSong() {
            switch (what) {
                case "folder":
                    if (!what.equals(newFolder)) {
                        mainActivityInterface.getTempSong().setFolder(value);
                    }
                    break;
                case "filename":
                    mainActivityInterface.getTempSong().setFilename(value);
                    break;
                case "title":
                    mainActivityInterface.getTempSong().setTitle(value);
                    break;
                case "copyright":
                    mainActivityInterface.getTempSong().setCopyright(value);
                    break;
                case "key":
                    mainActivityInterface.getTempSong().setKey(
                            mainActivityInterface.getTranspose().getOriginalFixedKey(value));
                    break;
                case "author":
                    mainActivityInterface.getTempSong().setAuthor(value);
                    break;
            }
        }
    }


    public void updateValue(String value) {
        Log.d(TAG, "value:" + value);
        // New folder name given.  If it isn't null/empty try to create it and select it
        if (value != null && !value.isEmpty()) {
            mainActivityInterface.getThreadPoolExecutor().execute(() -> {
                boolean success = mainActivityInterface.getStorageAccess().createFolder("Songs", "", value, true);
                if (success) {
                    mainActivityInterface.getMainHandler().post(() -> {
                        getFoldersFromStorage();
                        newFolder = "+ " + new_folder_add_string;
                        folders.add(newFolder);
                        if (getContext() != null) {
                            arrayAdapter = new ExposedDropDownArrayAdapter(getContext(), myView.folder, R.layout.view_exposed_dropdown_item, folders);
                            myView.folder.setAdapter(arrayAdapter);
                            myView.folder.setText(value);
                        }
                    });
                }
            });
        }
    }

    private void updateTagsView() {
        if (myView == null || mainActivityInterface.getTempSong() == null) {
            return;
        }
        String tags = mainActivityInterface.getTempSong().getTheme();
        if (tags == null) {
            tags = "";
        }
        mainActivityInterface.getProcessSong().editBoxToMultiline(myView.tags);
        myView.tags.setText(tags.replace("&amp;", "&").replace("; ", "\n").replace(";", "\n"));
        mainActivityInterface.getProcessSong().stretchEditBoxToLines(myView.tags, 2);
    }

    public void updateTagsValue() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::updateTagsView);
        }
    }

    public void removeTags(ArrayList<String> tag) {
        String tagToRemove = "";
        int position = -1;
        if (tag != null && tag.size() == 2) {
            tagToRemove = tag.get(0);
            position = Integer.parseInt(tag.get(1));
        }
        if (position >= 0 && !tagToRemove.isEmpty()) {
            ArrayList<Song> songs = mainActivityInterface.getSQLiteHelper().getSongsByFilters(
                    false, false, false, true, false, false,
                    null, null, null, tagToRemove, null, null,
                    mainActivityInterface.getPreferences().getMyPreferenceBoolean("songMenuSortTitles", true));
            for (Song thisSong : songs) {
                thisSong = mainActivityInterface.getSQLiteHelper().getSpecificSong(
                        thisSong.getFolder(), thisSong.getFilename());
                thisSong.setTheme(removeTagFromTheme(thisSong.getTheme(), tagToRemove));
                thisSong.setAlttheme(removeTagFromTheme(thisSong.getAlttheme(), tagToRemove));
                mainActivityInterface.getSQLiteHelper().updateSong(thisSong);
                if (thisSong.getFiletype() == null) {
                    thisSong.setFiletype(mainActivityInterface.getStorageAccess()
                            .tryToFixFileTypeFromNull(thisSong.getFilename()));
                }
                if ("PDF".equals(thisSong.getFiletype()) || "IMG".equals(thisSong.getFiletype())) {
                    mainActivityInterface.getNonOpenSongSQLiteHelper().updateSong(thisSong);
                } else if ("XML".equals(thisSong.getFiletype())) {
                    mainActivityInterface.getSaveSong().updateSong(thisSong, false);
                }
            }
            if (tagsBottomSheet != null) {
                tagsBottomSheet.deleteTags(position);
            }
        }
        Song tempSong = mainActivityInterface.getTempSong();
        tempSong.setTheme(removeTagFromTheme(tempSong.getTheme(), tagToRemove));
        updateTagsView();
    }

    private String removeTagFromTheme(String currentTheme, String tagToRemove) {
        if (currentTheme == null) {
            return "";
        }
        if (currentTheme.contains(";")) {
            currentTheme += ";";
        }
        StringBuilder updatedTheme = new StringBuilder();
        for (String bit : currentTheme.split(";")) {
            bit = bit.trim();
            if (!bit.equals(tagToRemove.trim())) {
                updatedTheme.append(bit).append("; ");
            }
        }
        String result = updatedTheme.toString().trim();
        if (result.endsWith(";")) {
            result = result.substring(0, result.lastIndexOf(";"));
        }
        return result;
    }

    private void getFoldersFromStorage() {
        ArrayList<String> songIds = mainActivityInterface.getStorageAccess().listSongs(false);
        mainActivityInterface.getStorageAccess().writeSongIDFile(songIds);
        folders = mainActivityInterface.getStorageAccess().getSongFolders(songIds,true,null);
    }

    private void showFolderInput() {
        // Check if the fragment is already there by tag
        Fragment existing = getParentFragmentManager().findFragmentByTag("TextInputBottomSheet");

        if (existing == null) {
            if (textInputBottomSheet == null) {
                textInputBottomSheet = new TextInputBottomSheet();
            }

            // Only show if it's not currently added
            if (!textInputBottomSheet.isAdded()) {
                textInputBottomSheet.show(getParentFragmentManager(), "TextInputBottomSheet");
            }
        }
    }

    // Finished with this view
    @Override
    public void onDetach() {
        super.onDetach();
        myView = null;
        mainActivityInterface = null;
        editSongFragmentInterface = null;
    }
}
