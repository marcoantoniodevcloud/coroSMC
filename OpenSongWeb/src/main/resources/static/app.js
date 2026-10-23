const state = {
    songs: [],
    selectedId: null,
    selectedSong: null,
    selectionVersion: 0,
    chordProPath: null,
    revision: null,
    savedContent: "",
    dirty: false,
    scale: 1,
    expandedFolder: null
};

const elements = {
    summary: document.querySelector("#library-summary"),
    count: document.querySelector("#song-count"),
    list: document.querySelector("#song-list"),
    search: document.querySelector("#search-songs"),
    editorFilename: document.querySelector("#editor-filename"),
    editorFolder: document.querySelector("#editor-folder"),
    editorWrap: document.querySelector("#editor-wrap"),
    emptyEditor: document.querySelector("#empty-editor"),
    chordPro: document.querySelector("#song-chordpro"),
    lineNumbers: document.querySelector("#line-numbers"),
    save: document.querySelector("#save-song"),
    reload: document.querySelector("#reload-song"),
    saveStatus: document.querySelector("#save-status"),
    message: document.querySelector("#message"),
    referenceSelect: document.querySelector("#reference-select"),
    referenceTitle: document.querySelector("#reference-title"),
    referenceName: document.querySelector("#reference-name"),
    referenceViewer: document.querySelector("#reference-viewer")
};

async function request(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            ...options.headers
        }
    });
    if (!response.ok) {
        let message = `La solicitud falló (${response.status}).`;
        try {
            const body = await response.json();
            message = body.message || message;
        } catch {
            // The server may return an empty or non-JSON error response.
        }
        const error = new Error(message);
        error.status = response.status;
        throw error;
    }
    return response.status === 204 ? null : response.json();
}

function showMessage(text, isError = false) {
    elements.message.textContent = text;
    elements.message.classList.toggle("error", isError);
    elements.message.hidden = false;
}

function clearMessage() {
    elements.message.hidden = true;
    elements.message.textContent = "";
    elements.message.classList.remove("error");
}

function hasUnsavedChanges() {
    if (!state.dirty) {
        return true;
    }
    return window.confirm("Hay cambios sin guardar. ¿Descartarlos?");
}

function kindLabel(files) {
    return [...new Set(files.map(file => file.extension.toUpperCase()))].join(" · ");
}

function renderSongList() {
    const query = elements.search.value.trim().toLocaleLowerCase();
    const songs = state.songs.filter(song =>
        `${song.title} ${song.folder} ${kindLabel(song.files)}`
            .toLocaleLowerCase()
            .includes(query)
    );
    elements.count.textContent = `${songs.length}`;
    elements.summary.textContent = `Biblioteca de cantos · ${state.songs.length} títulos`;

    if (songs.length === 0) {
        elements.list.innerHTML = `<div class="song-list-empty">${
            state.songs.length
                ? "No hay cantos que coincidan con la búsqueda."
                : "No hay archivos compatibles en la biblioteca."
        }</div>`;
        return;
    }

    const root = { name: "", path: "", songs: [], folders: new Map(), count: 0 };
    for (const song of songs) {
        const segments = song.folder.split(/[\\/]/).filter(Boolean);
        let folder = root;
        for (const segment of segments) {
            const key = segment.normalize("NFD").replace(/\p{M}/gu, "").toLocaleLowerCase();
            if (!folder.folders.has(key)) {
                const path = folder.path ? `${folder.path}/${segment}` : segment;
                folder.folders.set(key, {
                    name: segment,
                    path,
                    songs: [],
                    folders: new Map(),
                    count: 0
                });
            }
            folder = folder.folders.get(key);
        }
        folder.songs.push(song);
    }

    const addSong = (container, song) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "song-item";
        button.setAttribute("aria-current", song.id === state.selectedId ? "true" : "false");
        const title = document.createElement("strong");
        title.textContent = song.title;
        const types = document.createElement("span");
        types.textContent = kindLabel(song.files) || "Sin archivos";
        button.append(title, types);
        button.addEventListener("click", () => selectSong(song));
        container.append(button);
    };

    const countSongs = folder => {
        folder.count = folder.songs.length;
        for (const child of folder.folders.values()) {
            folder.count += countSongs(child);
        }
        return folder.count;
    };
    countSongs(root);

    const renderFolder = (container, folder, depth) => {
        const details = document.createElement("details");
        details.className = "folder-group";
        details.dataset.depth = `${depth}`;
        details.open = query !== "" || depth === 1 || folder.path === state.expandedFolder;
        const summary = document.createElement("summary");
        const name = document.createElement("span");
        name.className = "folder-name";
        name.textContent = folder.name;
        name.title = folder.path;
        const count = document.createElement("span");
        count.className = "folder-count";
        count.textContent = `${folder.count}`;
        summary.append(name, count);
        details.append(summary);

        const contents = document.createElement("div");
        contents.className = "folder-contents";
        folder.songs
            .sort((left, right) => left.title.localeCompare(right.title, "es", { sensitivity: "base" }))
            .forEach(song => addSong(contents, song));
        [...folder.folders.values()]
            .sort((left, right) => left.name.localeCompare(right.name, "es", { sensitivity: "base" }))
            .forEach(child => renderFolder(contents, child, depth + 1));
        details.addEventListener("toggle", () => {
            if (details.open) {
                state.expandedFolder = folder.path;
            }
        });
        details.append(contents);
        container.append(details);
    };

    elements.list.replaceChildren();
    root.songs.forEach(song => addSong(elements.list, song));
    [...root.folders.values()]
        .sort((left, right) => left.name.localeCompare(right.name, "es", { sensitivity: "base" }))
        .forEach(folder => renderFolder(elements.list, folder, 1));
}

function renderLineNumbers() {
    const lines = elements.chordPro.value.split("\n").length;
    elements.lineNumbers.textContent = Array.from({ length: lines }, (_, index) => index + 1).join("\n");
}

function setDirty(dirty) {
    state.dirty = dirty;
    elements.save.disabled = !dirty || !state.chordProPath;
    elements.saveStatus.classList.toggle("changed", dirty);
    elements.saveStatus.innerHTML = dirty
        ? "<i></i> Cambios sin guardar"
        : "<i></i> Cambios guardados";
}

async function reloadCurrentFile() {
    if (!state.chordProPath || (state.dirty
        && !window.confirm("Se descartarán tus cambios locales. ¿Recargar el archivo desde disco?"))) {
        return;
    }
    clearMessage();
    try {
        const latest = await request(
            `/api/library/chordpro?path=${encodeURIComponent(state.chordProPath)}`
        );
        state.revision = latest.revision;
        state.savedContent = latest.content;
        elements.chordPro.value = latest.content;
        elements.reload.hidden = true;
        renderLineNumbers();
        setDirty(false);
        showMessage("Archivo recargado desde disco.");
    } catch (error) {
        showMessage(error.message, true);
    }
}

function fileForGroup(song) {
    return song.files.find(file => file.extension.toLowerCase() === "cho")
        || song.files.find(file => file.extension.toLowerCase() === "pro");
}

function relatedMedia(song) {
    return song.files.filter(file => file.kind === "image" || file.kind === "pdf");
}

function editorPathFor(song) {
    const chordPro = fileForGroup(song);
    return chordPro?.path || `${song.folder ? `${song.folder}/` : ""}${song.title}.cho`;
}

async function selectSong(song) {
    if (!hasUnsavedChanges()) {
        return;
    }
    const selectionVersion = ++state.selectionVersion;
    clearMessage();
    state.selectedId = song.id;
    state.selectedSong = song;
    elements.editorFilename.textContent = fileForGroup(song)?.name || `${song.title}.cho`;
    elements.editorFolder.textContent = song.folder || "Biblioteca principal";
    elements.referenceTitle.textContent = song.title;
    elements.chordPro.value = "";
    elements.editorWrap.hidden = false;
    elements.emptyEditor.hidden = true;
    state.chordProPath = null;
    state.revision = null;
    state.savedContent = "";
    renderLineNumbers();
    setDirty(false);
    renderReferenceOptions(song);
    renderSongList();

    const file = fileForGroup(song);
    if (file) {
        try {
            const document = await request(`/api/library/chordpro?path=${encodeURIComponent(file.path)}`);
            if (selectionVersion !== state.selectionVersion) {
                return;
            }
            state.chordProPath = document.path;
            state.revision = document.revision;
            state.savedContent = document.content;
            elements.chordPro.value = document.content;
        } catch (error) {
            if (selectionVersion !== state.selectionVersion) {
                return;
            }
            state.chordProPath = null;
            state.revision = null;
            state.savedContent = "";
            elements.chordPro.value = "";
            showMessage(error.message, true);
        }
    } else {
        state.chordProPath = editorPathFor(song);
        state.revision = null;
        state.savedContent = "";
        state.chordProPath = editorPathFor(song);
    }

    elements.editorFilename.textContent = state.chordProPath?.split("/").at(-1) || `${song.title}.cho`;
    renderLineNumbers();
    setDirty(false);
    renderSongList();
}

function renderReferenceOptions(song) {
    const media = relatedMedia(song);
    elements.referenceSelect.replaceChildren();
    if (media.length === 0) {
        elements.referenceSelect.add(new Option("Sin referencias", ""));
        elements.referenceSelect.disabled = true;
        renderReference(null);
        return;
    }
    for (const file of media) {
        elements.referenceSelect.add(new Option(file.name, file.path));
    }
    elements.referenceSelect.disabled = false;
    elements.referenceSelect.value = media[0].path;
    renderReference(media[0]);
}

function setPanelWidths(libraryWidth, editorWidth, referenceWidth) {
    const workspace = document.querySelector(".workspace");
    const total = Math.max(680, libraryWidth + editorWidth + referenceWidth);
    const left = Math.max(180, Math.min(total - 500, libraryWidth));
    const middle = Math.max(280, Math.min(total - left - 220, editorWidth));
    const right = total - left - middle;
    workspace.style.gridTemplateColumns =
        `${left}px 7px ${middle}px 7px ${right}px`;
}

for (const splitter of document.querySelectorAll(".panel-splitter")) {
    splitter.addEventListener("pointerdown", event => {
        event.preventDefault();
        const library = document.querySelector(".library-panel");
        const editor = document.querySelector(".editor-panel");
        const reference = document.querySelector(".reference-panel");
        const startX = event.clientX;
        const startLibrary = library.getBoundingClientRect().width;
        const startEditor = editor.getBoundingClientRect().width;
        const startReference = reference.getBoundingClientRect().width;
        splitter.setPointerCapture(event.pointerId);

        const onMove = moveEvent => {
            const delta = moveEvent.clientX - startX;
            if (splitter.dataset.splitter === "library") {
                const libraryWidth = Math.max(180, Math.min(startLibrary + startEditor - 300,
                    startLibrary + delta));
                setPanelWidths(libraryWidth, startLibrary + startEditor - libraryWidth, startReference);
            } else {
                const editorWidth = Math.max(280, Math.min(startEditor + startReference - 220,
                    startEditor + delta));
                setPanelWidths(startLibrary, editorWidth, startEditor + startReference - editorWidth);
            }
        };
        const onEnd = () => {
            splitter.removeEventListener("pointermove", onMove);
            splitter.removeEventListener("pointerup", onEnd);
            splitter.removeEventListener("pointercancel", onEnd);
        };
        splitter.addEventListener("pointermove", onMove);
        splitter.addEventListener("pointerup", onEnd);
        splitter.addEventListener("pointercancel", onEnd);
    });

    splitter.addEventListener("keydown", event => {
        if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
            return;
        }
        event.preventDefault();
        const libraryWidth = document.querySelector(".library-panel").getBoundingClientRect().width;
        const editorWidth = document.querySelector(".editor-panel").getBoundingClientRect().width;
        const referenceWidth = document.querySelector(".reference-panel").getBoundingClientRect().width;
        const delta = event.key === "ArrowRight" ? 24 : -24;
        if (splitter.dataset.splitter === "library") {
            setPanelWidths(libraryWidth + delta, editorWidth - delta, referenceWidth);
        } else {
            setPanelWidths(libraryWidth, editorWidth + delta, referenceWidth - delta);
        }
    });
}

window.addEventListener("resize", () => {
    if (window.innerWidth <= 720) {
        document.querySelector(".workspace").style.gridTemplateColumns = "";
    }
});

function renderReference(file) {
    state.scale = 1;
    elements.referenceViewer.replaceChildren();
    if (!file) {
        const empty = document.createElement("div");
        empty.className = "reference-empty";
        empty.textContent = "Este canto no tiene PDF ni imágenes relacionados.";
        elements.referenceViewer.append(empty);
        elements.referenceName.textContent = "Sin archivo seleccionado";
        return;
    }

    const source = `/api/library/media?path=${encodeURIComponent(file.path)}`;
    if (file.kind === "image") {
        const image = document.createElement("img");
        image.className = "reference-image";
        image.src = source;
        image.alt = `Referencia de ${state.selectedSong.title}`;
        image.addEventListener("error", () => showMessage(`No se pudo mostrar ${file.name}.`, true));
        elements.referenceViewer.append(image);
    } else {
        const frame = document.createElement("iframe");
        frame.className = "reference-pdf";
        frame.src = source;
        frame.title = `PDF de ${state.selectedSong.title}`;
        elements.referenceViewer.append(frame);
    }
    elements.referenceName.textContent = file.name;
}

async function refreshLibrary() {
    const currentId = state.selectedId;
    const selectionVersion = state.selectionVersion;
    state.songs = await request("/api/library");
    renderSongList();
    if (state.selectionVersion !== selectionVersion) {
        return;
    }
    if (currentId) {
        const current = state.songs.find(song =>
            song.id === currentId || song.files.some(file => file.path === currentId)
        );
        if (current) {
            state.selectedSong = current;
            const referencePaths = relatedMedia(current).map(file => file.path).join("\n");
            const displayedPaths = [...elements.referenceSelect.options]
                .map(option => option.value)
                .filter(Boolean)
                .join("\n");
            if (referencePaths !== displayedPaths) {
                renderReferenceOptions(current);
            }
            const currentChordPro = fileForGroup(current);
            if (currentChordPro && state.revision === null) {
                state.chordProPath = currentChordPro.path;
                elements.editorFilename.textContent = currentChordPro.name;
            }
            if (state.chordProPath && (state.revision !== null || currentChordPro)) {
                const latest = await request(
                    `/api/library/chordpro?path=${encodeURIComponent(state.chordProPath)}`
                );
                if (state.selectedId !== currentId || state.selectionVersion !== selectionVersion) {
                    return;
                }
                if (latest.revision !== state.revision) {
                    if (state.dirty) {
                        elements.reload.hidden = false;
                        showMessage("El archivo cambió en disco. Tus cambios siguen intactos; recarga antes de guardar.",
                            true);
                    } else {
                        state.revision = latest.revision;
                        state.savedContent = latest.content;
                        elements.chordPro.value = latest.content;
                        elements.reload.hidden = true;
                        renderLineNumbers();
                        setDirty(false);
                        showMessage("Se actualizó el archivo con los cambios externos.");
                    }
                }
            }
        } else if (!state.dirty) {
            state.selectedId = null;
            state.selectedSong = null;
            state.chordProPath = null;
            elements.chordPro.value = "";
            elements.editorWrap.hidden = true;
            elements.emptyEditor.hidden = false;
            elements.editorFilename.textContent = "Selecciona un canto";
            elements.editorFolder.textContent = "";
            renderReference(null);
            setDirty(false);
        }
    } else if (state.songs.length) {
        await selectSong(state.songs[0]);
    }
}

async function createSong() {
    if (!hasUnsavedChanges()) {
        return;
    }
    const title = window.prompt("Nombre del canto nuevo:");
    if (!title?.trim()) {
        return;
    }
    const filename = title.trim().normalize("NFC").replace(/[<>:"/\\|?*\u0000-\u001f]/g, "-");
    const song = {
        id: `draft:${filename}`,
        title: title.trim(),
        folder: "",
        files: []
    };
    state.selectedId = song.id;
    state.selectedSong = song;
    state.chordProPath = `${filename}.cho`;
    state.revision = null;
    state.savedContent = "";
    elements.editorFilename.textContent = `${filename}.cho`;
    elements.editorFolder.textContent = "Biblioteca principal";
    elements.chordPro.value = "";
    elements.editorWrap.hidden = false;
    elements.emptyEditor.hidden = true;
    renderLineNumbers();
    setDirty(true);
    renderReferenceOptions(song);
    renderSongList();
    elements.chordPro.focus();
}

elements.chordPro.addEventListener("input", () => {
    renderLineNumbers();
    setDirty(elements.chordPro.value !== state.savedContent);
});
elements.chordPro.addEventListener("scroll", () => {
    elements.lineNumbers.scrollTop = elements.chordPro.scrollTop;
});

elements.save.addEventListener("click", async () => {
    if (!state.chordProPath || !state.dirty) {
        return;
    }
    clearMessage();
    try {
        const saved = await request("/api/library/chordpro", {
            method: "POST",
            body: JSON.stringify({
                path: state.chordProPath,
                content: elements.chordPro.value,
                revision: state.revision
            })
        });
        state.chordProPath = saved.path;
        state.revision = saved.revision;
        state.savedContent = saved.content;
        state.selectedId = saved.path;
        elements.reload.hidden = true;
        setDirty(false);
        await refreshLibrary();
        showMessage("Cambios guardados en el archivo original.");
    } catch (error) {
        if (error.status === 409) {
            elements.reload.hidden = false;
        }
        showMessage(error.message, true);
    }
});

elements.referenceSelect.addEventListener("change", () => {
    const file = state.selectedSong?.files.find(item => item.path === elements.referenceSelect.value);
    renderReference(file || null);
});
elements.search.addEventListener("input", renderSongList);
document.querySelector("#new-song").addEventListener("click", createSong);
elements.reload.addEventListener("click", reloadCurrentFile);
document.querySelector("#refresh-library").addEventListener("click", () => {
    refreshLibrary().catch(error => showMessage(error.message, true));
});

document.querySelector("#zoom-in").addEventListener("click", () => zoomReference(0.1));
document.querySelector("#zoom-out").addEventListener("click", () => zoomReference(-0.1));
document.querySelector("#zoom-reset").addEventListener("click", () => {
    state.scale = 1;
    applyReferenceZoom();
});

function zoomReference(amount) {
    state.scale = Math.min(2.5, Math.max(0.5, state.scale + amount));
    applyReferenceZoom();
}

function applyReferenceZoom() {
    const media = elements.referenceViewer.firstElementChild;
    if (media && media.classList.contains("reference-image")) {
        media.style.transform = `scale(${state.scale})`;
    }
}

refreshLibrary().catch(error => {
    elements.count.textContent = "!";
    showMessage(error.message, true);
});

window.setInterval(() => {
    if (!document.hidden) {
        refreshLibrary().catch(error => showMessage(error.message, true));
    }
}, 12000);

if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => navigator.serviceWorker.register("/sw.js"));
}
