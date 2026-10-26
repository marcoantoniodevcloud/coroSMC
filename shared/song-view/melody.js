/* ChordPro extension: a comment immediately after its lyric carries anchored melody. */
(function (root) {
    "use strict";
    function parseLyric(source) {
        let text = "", end = 0;
        const chords = [];
        for (const match of source.matchAll(/\[([^\]]+)\]/g)) {
            text += source.slice(end, match.index);
            chords.push({ position: text.length, label: match[1] });
            end = match.index + match[0].length;
        }
        return { text: text + source.slice(end), chords };
    }
    function parseMelody(source) {
        // Earlier Android imports changed angle brackets to parentheses.
        source = source.replace(/\(((?:(?:do|re|mi|fa|sol|la|si)[#b♯♭]?[³0-9]*[\s·.]*?)+)\)/gi, "<$1>");
        let text = "", end = 0;
        const notes = [];
        for (const match of source.matchAll(/<([^<>]+)>/g)) {
            text += source.slice(end, match.index);
            notes.push({ position: text.length, label: match[1] });
            end = match.index + match[0].length;
        }
        return { text: text + source.slice(end), notes };
    }
    function aligned(source, melody) {
        const lyric = parseLyric(source);
        const parsed = parseMelody(melody);
        if (!parsed.notes.length) return null;
        if (parsed.text !== lyric.text) {
            if (parsed.text.replace(/\s/g, "") !== lyric.text.replace(/\s/g, "")) return null;
            for (const chord of lyric.chords) {
                const before = lyric.text.slice(0, chord.position).replace(/\s/g, "").length;
                let letters = 0, position = 0;
                while (position < parsed.text.length && letters < before) {
                    if (!/\s/.test(parsed.text[position])) letters++;
                    position++;
                }
                while (/\s/.test(parsed.text[position] || "") && position < parsed.text.length) position++;
                chord.position = position;
            }
            lyric.text = parsed.text;
        }
        const positions = [...new Set([0, lyric.text.length,
            ...lyric.chords.map(item => item.position), ...parsed.notes.map(item => item.position),
            ...[...lyric.text.matchAll(/\s+/g)].map(match => match.index + match[0].length)])].sort((a,b) => a-b);
        return { text: lyric.text, notes: parsed.notes, cells: positions.map((position, index) => ({
            text: lyric.text.slice(position, positions[index + 1] ?? position),
            chord: lyric.chords.filter(item => item.position === position).map(item => item.label).join(" "),
            note: parsed.notes.filter(item => item.position === position).map(item => item.label).join(" ")
        })).filter(cell => cell.text || cell.chord || cell.note) };
    }
    function render(document, model) {
        const line = document.createElement("div");
        const instrumental = /^intro\s*\d+$/i.test(model.text.trim());
        line.className = "melody-aligned-line" + (instrumental ? " melody-instrumental" : "");
        let word = null;
        let startsWord = true;
        for (const cell of model.cells) {
            if (startsWord) {
                word = document.createElement("span");
                word.className = "melody-word";
                line.append(word);
            }
            const block = document.createElement("span");
            block.className = "melody-cell";
            for (const [className, text] of [["melody-chord", cell.chord], ["melody-lyric", cell.text], ["melody-note", cell.note]]) {
                if (instrumental && className === "melody-lyric") continue;
                const span = document.createElement("span");
                span.className = className;
                span.textContent = text || "\u00a0";
                block.append(span);
            }
            word.append(block);
            startsWord = /\s$/.test(cell.text);
        }
        return line;
    }
    function renderSong(document, content, preview, separate) {
    preview.replaceChildren();
    separate.replaceChildren();
    if (!content.trim()) {
        const empty = document.createElement("div");
        empty.className = "preview-empty";
        empty.textContent = "Escribe la letra y agrega acordes entre corchetes, por ejemplo [G].";
        preview.append(empty);
        return;
    }

    const metadata = new Set([
        "title", "t", "subtitle", "artist", "author", "composer", "lyricist",
        "copyright", "album", "year", "key", "time", "tempo", "capo", "duration",
        "ccli", "language", "meta"
    ]);
    const sections = new Map([
        ["start_of_chorus", "CORO"], ["soc", "CORO"],
        ["start_of_verse", "ESTROFA"], ["sov", "ESTROFA"],
        ["start_of_bridge", "PUENTE"], ["sob", "PUENTE"],
        ["start_of_tab", "TAB"], ["sot", "TAB"],
        ["start_of_grid", "ACORDES"], ["sog", "ACORDES"]
    ]);

    const addTextLine = (className, text) => {
        const line = document.createElement("div");
        line.className = className;
        line.textContent = text;
        preview.append(line);
    };

    const sourceLines = content.replace(/\r\n?/g, "\n").split("\n");
    const separateMelody = separate;
    separateMelody.replaceChildren();
    for (let lineIndex = 0; lineIndex < sourceLines.length; lineIndex++) {
        const rawLine = sourceLines[lineIndex];
        const melodyComment = sourceLines[lineIndex + 1]?.trim().match(/^\{(?:comment|c):\s*melodia:\s*(.*)\}$/i);
        if (melodyComment && rawLine.trim() && !rawLine.trim().startsWith("{")) {
            const model = aligned(rawLine, melodyComment[1]);
            if (model) {
                preview.append(render(document, model));
                const verse = document.createElement("p");
                const lyric = document.createElement("strong");
                lyric.textContent = model.text;
                const notes = document.createElement("span");
                notes.textContent = model.notes.map(note => note.label).join(" · ");
                if (!/^intro\s*\d+$/i.test(model.text.trim())) verse.append(lyric, document.createElement("br"));
                verse.append(notes);
                separateMelody.append(verse);
                lineIndex++;
                continue;
            }
            addTextLine("preview-melody-warning", "Melodía sin alinear: el texto del comentario debe coincidir exactamente con el verso.");
        }
        const trimmedLine = rawLine.trim();
        if (!trimmedLine) {
            const blank = document.createElement("div");
            blank.className = "preview-blank";
            blank.setAttribute("aria-hidden", "true");
            preview.append(blank);
            continue;
        }

        const directive = trimmedLine.match(/^\{([a-z_]+)(?::\s*([^}]*))?\}$/i);
        if (directive) {
            const name = directive[1].toLowerCase();
            const value = directive[2]?.trim() || "";
            if (metadata.has(name) || name.startsWith("end_of_")
                || ["eoc", "eov", "eob", "eot", "eog", "new_page", "np"].includes(name)) {
                continue;
            }
            const section = sections.get(name);
            if (section) {
                addTextLine("preview-section", section);
            } else if (name === "comment" || name === "c") {
                if (value) {
                    addTextLine("preview-comment", value);
                }
            } else {
                addTextLine("preview-comment", value || name.replaceAll("_", " "));
            }
            continue;
        }

        const lyric = [];
        const chords = [];
        const chordPattern = /\[([^\]]+)]/g;
        let lastIndex = 0;
        let lyricLength = 0;
        let match;
        while ((match = chordPattern.exec(rawLine)) !== null) {
            const text = rawLine.slice(lastIndex, match.index);
            lyric.push(text);
            lyricLength += text.length;
            chords.push({ position: lyricLength, label: match[1].trim() });
            lastIndex = match.index + match[0].length;
        }
        lyric.push(rawLine.slice(lastIndex));
        const lyricText = lyric.join("");
        const chordsOnly = chords.length > 0 && !lyricText.trim();

        const line = document.createElement("div");
        line.className = "preview-line";
        const chordRow = document.createElement("div");
        chordRow.className = "preview-chords" + (chordsOnly ? " preview-chords-only" : "");
        for (const chord of chords) {
            const label = document.createElement("span");
            label.className = "preview-chord";
            if (!chordsOnly) label.style.left = `${chord.position}ch`;
            label.textContent = chord.label;
            chordRow.append(label);
        }
        const lyricRow = document.createElement("div");
        lyricRow.className = "preview-lyrics";
        lyricRow.textContent = lyricText || "\u00a0";
        line.append(chordRow);
        if (!chordsOnly) line.append(lyricRow);
        preview.append(line);
    }

    }
    const api = { parseLyric, parseMelody, aligned, render, renderSong };
    if (typeof module !== "undefined" && module.exports) module.exports = api;
    else root.Melody = api;
})(typeof globalThis === "undefined" ? this : globalThis);
