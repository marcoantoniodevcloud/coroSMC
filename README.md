# OpenSongApp - v6 (MaterialApp)
![splash_logo](https://user-images.githubusercontent.com/7831274/205077524-2b0f7c42-de5c-432f-aa59-8e73746b2f96.png)
Android port of OpenSong (http://www.opensong.org).

Use your mobile device as a portable song book.  Please feel free to help out by forking this repository and improving this app!  Translation improvements and additions are always welcome.

If you add new text strings (to strings.xml), please translate them into the other language files in the app (use https://translate.google.com). If you add new settings, please add a comment line in the Preferences.java file that notes what it is, what it does and what the default value should be.

The app online manual can be found at https://www.opensongapp.com

To use (and install) this code, copy the git address (https://github.com/thebigg73/OpenSongTablet.git). Open Android Studio and select File > New > Project from Version Control. Paste in the git address. You can then run on an emulator or plugged in Android device as appropriate.

The master branch represents the stable version of the app (currently v5.6.0). This is not likely to be further updated. All new developments (v6+) are currently in the MaterialApp branch (this one!)

Gareth Evans

## Default song library and OCR

Files under `app/src/main/assets/DefaultSongs` are installed into `Songs`,
preserving the library's original category folders. On a library update, the
previous managed `Songs/OpenSongLibrary` copy is removed and the song index is
rebuilt. Other files in `Songs` are left untouched.

The song menu's batch OCR action processes PDFs one page at a time and images
without an XML companion. It preserves each source file and writes the
recognized OpenSong XML beside it. A multi-song PDF produces one XML companion
for the whole document, and OCR output should be reviewed and corrected.
Every bundled image and PDF now has a draft ChordPro (`.cho`) companion,
generated from selectable PDF text where available and OCR for scanned pages
and images. OCR, chord recognition, and chord placement are approximate and
should be reviewed and corrected.
When a media file has a non-empty XML or ChordPro companion, its lyrics are
shown by default. Use the third quick action under the `+` button to switch
between the editable lyrics and the original image or PDF; this action can also
be assigned to another quick-action button. Edits to a ChordPro companion are
saved back in ChordPro format.

`DefaultSongs/adoracion/Bendito bendito.cho` is a reviewed ChordPro
text-and-chords companion for `Bendito bendito.png`.
