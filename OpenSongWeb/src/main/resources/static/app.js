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
    expandedFolder: null,
    ocrBusy: false,
    ocrBatch: false,
    stopOcrBatch: false
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
    review: document.querySelector("#review-song"),
    reviewNotes: document.querySelector("#review-notes"),
    ocrCurrent: document.querySelector("#ocr-current"),
    ocrPending: document.querySelector("#ocr-pending"),
    ocrPendingCount: document.querySelector("#ocr-pending-count"),
    reload: document.querySelector("#reload-song"),
    saveStatus: document.querySelector("#save-status"),
    message: document.querySelector("#message"),
    previewTitle: document.querySelector("#preview-title"),
    preview: document.querySelector("#chordpro-preview"),
    referenceSelect: document.querySelector("#reference-select"),
    referenceTitle: document.querySelector("#reference-title"),
    referenceName: document.querySelector("#reference-name"),
    referenceViewer: document.querySelector("#reference-viewer"),
    newSongDialog: document.querySelector("#new-song-dialog"),
    newSongDialogTitle: document.querySelector("#new-song-dialog-title"),
    newSongFolderStep: document.querySelector("#new-song-folder-step"),
    newSongFolder: document.querySelector("#new-song-folder"),
    newSongTitleStep: document.querySelector("#new-song-title-step"),
    newSongFolderSummary: document.querySelector("#new-song-folder-summary"),
    newSongTitle: document.querySelector("#new-song-title"),
    nextNewSong: document.querySelector("#next-new-song"),
    backNewSong: document.querySelector("#back-new-song"),
    createNewSong: document.querySelector("#create-new-song"),
    deleteSongDialog: document.querySelector("#delete-song-dialog"),
    deleteSongTitle: document.querySelector("#delete-song-title"),
    deleteSongFiles: document.querySelector("#delete-song-files")
};

let correctionBatch = false;
let stopCorrection = false;
let correctionScopeAll = false;

function openCorrection(all) {
    if (state.ocrBusy) return;
    if (all && state.dirty) {
        showMessage("Guarda o descarta los cambios del editor antes de corregir toda la biblioteca.", true);
        return;
    }
    correctionScopeAll = all;
    const files = state.songs.flatMap(song => song.files).filter(file => ["cho", "pro"].includes(file.extension));
    if (all && !files.length) {
        showMessage("No hay archivos ChordPro para corregir.");
        return;
    }
    document.querySelector("#correction-title").textContent = all ? "Corregir toda la biblioteca" : `Corregir: ${state.selectedSong?.title || "canto"}`;
    document.querySelector("#correction-scope").textContent = all
        ? `Se revisarán ${files.length} archivos de toda la biblioteca, incluidos los cantos ya revisados. Los cambios se guardan con una copia de respaldo por archivo modificado.`
        : "Se aplicará al texto actual del editor, incluidos tus cambios sin guardar. Revisa el resultado y pulsa Guardar para conservarlo.";
    document.querySelector("#apply-correction").textContent = all ? "Corregir y guardar todos" : "Aplicar al editor";
    document.querySelector("#correction-dialog").showModal();
}

async function applyCorrection() {
    const action = document.querySelector("#correction-action").value;
    document.querySelector("#correction-dialog").close();
    if (state.ocrBusy) return;
    if (correctionScopeAll && state.dirty) {
        showMessage("Guarda los cambios del editor antes de corregir todos los cantos.", true);
        return;
    }
    const content = elements.chordPro.value;
    const all = correctionScopeAll;
    state.ocrBusy = true;
    correctionBatch = all;
    stopCorrection = false;
    elements.chordPro.readOnly = true;
    elements.save.disabled = true;
    updateReviewButton();
    renderSongList();
    try {
        if (!all) {
            const result = await request("/api/library/correct", { method: "POST", body: JSON.stringify({ action, content }) });
            elements.chordPro.value = result.content;
            renderLineNumbers();
            renderChordProPreview(result.content);
            setDirty(result.content !== state.savedContent);
            showMessage(result.content === content ? "Los acordes ya están en notación americana o no hay acordes reconocibles."
                : "Acordes convertidos a notación americana. Revisa el resultado y pulsa Guardar.");
        } else {
            const paths = [...new Set(state.songs.flatMap(song => song.files)
                .filter(file => ["cho", "pro"].includes(file.extension)).map(file => file.path))];
            let changed = 0, unchanged = 0, processed = 0;
            const failures = [];
            for (const path of paths) {
                if (stopCorrection) break;
                showMessage(`Corrigiendo ${processed + 1}/${paths.length}: ${path}`);
                try {
                    const current = await request(`/api/library/chordpro?path=${encodeURIComponent(path)}`);
                    const saved = await request("/api/library/correct", { method: "POST",
                        body: JSON.stringify({ action, path, revision: current.revision }) });
                    if (saved.content === current.content) unchanged++; else changed++;
                } catch (error) { failures.push(`${path}: ${error.message}`); }
                processed++;
            }
            await refreshLibrary();
            showMessage(`${stopCorrection ? "Corrección detenida" : "Corrección terminada"}: ${changed} archivo(s) convertido(s), ${unchanged} sin cambios, ${failures.length} con error. ${paths.length - processed} sin procesar.${failures.length ? " Errores: " + failures.join("; ") : ""}`, failures.length > 0);
        }
    } catch (error) { showMessage(error.message, true); }
    finally {
        state.ocrBusy = false;
        correctionBatch = false;
        elements.chordPro.readOnly = false;
        setDirty(state.dirty);
        updateReviewButton();
        renderSongList();
    }
}

document.querySelector("#correct-all").addEventListener("click", () => {
    if (correctionBatch) { stopCorrection = true; showMessage("Deteniendo después del archivo actual…"); }
    else openCorrection(true);
});
document.querySelector("#correct-current").addEventListener("click", () => openCorrection(false));
document.querySelector("#apply-correction").addEventListener("click", applyCorrection);

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
    const matchesQuery = song => `${song.title} ${song.folder} ${kindLabel(song.files)}`
        .toLocaleLowerCase()
        .includes(query);
    const visibleSongs = state.songs.filter(matchesQuery);
    elements.count.textContent = `${visibleSongs.length}`;
    elements.summary.textContent = `Biblioteca de cantos · ${state.songs.length} títulos`;
    const pendingWithMedia = state.songs.filter(song => !song.reviewed && relatedMedia(song).length > 0).length;
    const pendingWithoutMedia = state.songs.filter(song => !song.reviewed && relatedMedia(song).length === 0).length;
    document.querySelector("#correct-all").disabled = state.ocrBusy && !correctionBatch;
    document.querySelector("#correct-all").textContent = correctionBatch ? "Detener corrección" : "Corregir todos…";
    document.querySelector("#correct-current").disabled = state.ocrBusy || !state.selectedSong || !state.chordProPath;
    elements.ocrPendingCount.textContent = pendingWithoutMedia
        ? `${pendingWithMedia} con referencia · ${pendingWithoutMedia} sin imagen/PDF`
        : `${pendingWithMedia} pendientes`;
    elements.ocrPending.textContent = state.ocrBusy && state.ocrBatch ? "Detener lote" : "OCR pendientes";
    elements.ocrPending.disabled = state.ocrBusy
        ? !state.ocrBatch
        : pendingWithMedia === 0;

    if (visibleSongs.length === 0) {
        elements.list.innerHTML = `<div class="song-list-empty">${
            state.songs.length
                ? "No hay cantos que coincidan con la búsqueda."
                : "No hay archivos compatibles en la biblioteca."
        }</div>`;
        return;
    }

    const root = {
        name: "", path: "", songs: [], folders: new Map(), count: 0, reviewedCount: 0, attentionCount: 0, visibleCount: 0
    };
    for (const song of state.songs) {
        const isVisible = matchesQuery(song);
        const segments = song.folder.split(/[\\/]/).filter(Boolean);
        let folder = root;
        folder.count += 1;
        folder.reviewedCount += song.reviewed ? 1 : 0;
        folder.attentionCount += song.needsAttention && !song.reviewed ? 1 : 0;
        folder.visibleCount += isVisible ? 1 : 0;
        for (const segment of segments) {
            const key = segment.normalize("NFD").replace(/\p{M}/gu, "").toLocaleLowerCase();
            if (!folder.folders.has(key)) {
                const path = folder.path ? `${folder.path}/${segment}` : segment;
                folder.folders.set(key, {
                    name: segment,
                    path,
                    songs: [],
                    folders: new Map(),
                    count: 0,
                    reviewedCount: 0,
                    attentionCount: 0,
                    visibleCount: 0
                });
            }
            folder = folder.folders.get(key);
            folder.count += 1;
            folder.reviewedCount += song.reviewed ? 1 : 0;
            folder.attentionCount += song.needsAttention && !song.reviewed ? 1 : 0;
            folder.visibleCount += isVisible ? 1 : 0;
        }
        if (isVisible) {
            folder.songs.push(song);
        }
    }

    const addSong = (container, song) => {
        const row = document.createElement("div");
        row.className = "song-item-row";
        const button = document.createElement("button");
        button.type = "button";
        button.className = "song-item";
        button.setAttribute("aria-current", song.id === state.selectedId ? "true" : "false");
        const titleLine = document.createElement("span");
        titleLine.className = "song-title-line";
        const status = document.createElement("span");
        status.className = `song-status-dot${song.reviewed ? " reviewed" : song.needsAttention ? " attention" : ""}`;
        const reviewLabel = song.reviewed ? "Revisado" : song.needsAttention
            ? `Requiere revisión: ${song.reviewNotes}` : "Borrador o pendiente";
        status.setAttribute("aria-label", reviewLabel);
        status.title = reviewLabel;
        const title = document.createElement("strong");
        title.textContent = song.title;
        titleLine.append(status, title);
        const types = document.createElement("span");
        types.textContent = kindLabel(song.files) || "Sin archivos";
        button.append(titleLine, types);
        button.addEventListener("click", () => selectSong(song));

        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "song-delete";
        remove.textContent = "🗑";
        remove.title = `Eliminar archivo de ${song.title}`;
        remove.setAttribute("aria-label", `Eliminar archivo de ${song.title}`);
        remove.disabled = state.ocrBusy;
        remove.addEventListener("click", () => openDeleteDialog(song));
        const ocr = document.createElement("button");
        ocr.type = "button";
        ocr.className = "song-ocr";
        ocr.textContent = "OCR";
        ocr.title = relatedMedia(song).length
            ? `Reemplazar la letra de ${song.title} con OCR`
            : ` ${song.title} no tiene imagen ni PDF para OCR`;
        ocr.setAttribute("aria-label", `Procesar ${song.title} con OCR`);
        ocr.disabled = state.ocrBusy || relatedMedia(song).length === 0;
        ocr.addEventListener("click", () => runSingleOcr(song));
        const correct = document.createElement("button");
        correct.type = "button";
        correct.className = "song-correct";
        correct.textContent = "Corregir";
        correct.title = `Corregir acordes de ${song.title}`;
        correct.disabled = state.ocrBusy || !fileForGroup(song);
        correct.addEventListener("click", async () => {
            if (state.selectedSong?.id !== song.id) await selectSong(song);
            if (state.selectedSong?.id === song.id) openCorrection(false);
        });
        row.append(button, correct, ocr, remove);
        container.append(row);
    };

    const renderFolder = (container, folder, depth) => {
        if (folder.visibleCount === 0) {
            return;
        }
        const details = document.createElement("details");
        details.className = "folder-group";
        details.dataset.depth = `${depth}`;
        details.open = query !== "" || depth === 1 || folder.path === state.expandedFolder;
        const summary = document.createElement("summary");
        const complete = folder.count > 0 && folder.reviewedCount === folder.count;
        details.classList.add(complete ? "reviewed" : "pending");
        const folderStatus = document.createElement("span");
        folderStatus.className = `folder-status-dot ${complete ? "reviewed" : folder.attentionCount ? "attention" : "pending"}`;
        const folderLabel = complete ? "Todos los cantos revisados" : folder.attentionCount
            ? `${folder.attentionCount} cantos requieren revisión; consulta el punto rojo de cada canto`
            : "Hay cantos pendientes";
        folderStatus.setAttribute("aria-label", folderLabel);
        folderStatus.title = folderLabel;
        const name = document.createElement("span");
        name.className = "folder-name";
        name.textContent = folder.name;
        name.title = folder.path;
        const count = document.createElement("span");
        count.className = "folder-count";
        count.textContent = `${folder.count}`;
        summary.append(folderStatus, name, count);
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

function updateReviewButton() {
    const song = state.selectedSong;
    document.querySelector("#rename-song").disabled = state.ocrBusy || !song || song.files.length === 0;
    document.querySelector("#upload-reference").disabled = state.ocrBusy || !song || song.files.length === 0;
    document.querySelector("#replace-reference").disabled = state.ocrBusy || !song || !elements.referenceSelect.value;
    elements.review.disabled = state.ocrBusy || !song || song.files.length === 0;
    elements.review.classList.toggle("reviewed", Boolean(song?.reviewed));
    elements.review.textContent = song?.reviewed ? "✓ Revisado" : "○ Marcar revisado";
    elements.review.title = song?.reviewed
        ? "Quitar el estado de revisado"
        : "Marcar el canto como completo o revisado";
    elements.reviewNotes.hidden = !song?.needsAttention || Boolean(song?.reviewed);
    elements.reviewNotes.textContent = song?.needsAttention ? `🔴 ${song.reviewNotes}` : "";
    updateOcrButton();
}

function updateOcrButton() {
    const song = state.selectedSong;
    elements.ocrCurrent.disabled = state.ocrBusy || !song || relatedMedia(song).length === 0;
}

async function toggleSongReviewed() {
    const song = state.selectedSong;
    if (!song || song.files.length === 0) {
        return;
    }
    const reviewed = !song.reviewed;
    elements.review.disabled = true;
    try {
        await request("/api/library/review", {
            method: "POST",
            body: JSON.stringify({ path: song.id, reviewed })
        });
        await refreshLibrary();
        showMessage(reviewed ? "Canto marcado como revisado." : "Se quitó el estado de revisado.");
    } catch (error) {
        updateReviewButton();
        showMessage(error.message, true);
    }
}

function ocrMediaFor(song) {
    const media = relatedMedia(song);
    if (song.id === state.selectedId) {
        const selected = media.find(file => file.path === elements.referenceSelect.value);
        if (selected) {
            return selected;
        }
    }
    return media[0] || null;
}

function applyOcrResult(song, result) {
    const current = state.songs.find(item => item.id === song.id
        || item.files.some(file => file.path === song.id));
    if (current) {
        current.reviewed = false;
    }
    const selectedSongMatches = state.selectedSong?.id === song.id
        || state.selectedSong?.files.some(file => file.path === song.id);
    if (selectedSongMatches) {
        state.selectedSong = current || { ...song, reviewed: false };
        state.chordProPath = result.path;
        state.revision = result.revision;
        state.savedContent = result.content;
        elements.editorFilename.textContent = result.path.split("/").at(-1);
        elements.chordPro.value = result.content;
        renderLineNumbers();
        renderChordProPreview(result.content);
        elements.reload.hidden = true;
        setDirty(false);
        updateReviewButton();
    }
    renderSongList();
}

async function runSingleOcr(song, requestedPath = null) {
    if (state.ocrBusy) {
        return;
    }
    const media = requestedPath
        ? relatedMedia(song).find(file => file.path === requestedPath)
        : ocrMediaFor(song);
    if (!media) {
        showMessage("Este canto no tiene una imagen ni un PDF para procesar.", true);
        return;
    }
    const hasUnsavedEdits = state.selectedSong?.id === song.id && state.dirty;
    const warning = hasUnsavedEdits ? " También se descartarán los cambios sin guardar." : "";
    if (!window.confirm(`OCR_PROJECT reemplazará la letra actual de “${song.title}”. Si existe una letra, guardaré una copia de respaldo.${warning} ¿Continuar?`)) {
        return;
    }

    state.ocrBusy = true;
    updateReviewButton();
    renderSongList();
    elements.chordPro.readOnly = true;
    elements.save.disabled = true;
    clearMessage();
    showMessage(`Procesando “${song.title}” con OCR_PROJECT…`);
    try {
        const result = await request("/api/library/ocr", {
            method: "POST",
            body: JSON.stringify({ path: media.path })
        });
        applyOcrResult(song, result);
        await refreshLibrary();
        showMessage(`OCR completado para “${song.title}”. El canto quedó pendiente de revisión.`);
    } catch (error) {
        showMessage(error.message, true);
    } finally {
        state.ocrBusy = false;
        elements.chordPro.readOnly = false;
        setDirty(state.dirty);
        updateReviewButton();
        renderSongList();
    }
}

async function runBatchOcr() {
    if (state.ocrBusy && state.ocrBatch) {
        state.stopOcrBatch = true;
        showMessage("El lote se detendrá cuando termine el canto actual.");
        return;
    }
    if (state.ocrBusy) {
        return;
    }
    const candidates = state.songs
        .filter(song => !song.reviewed && relatedMedia(song).length > 0)
        .map(song => ({ song, media: ocrMediaFor(song) }))
        .filter(item => item.media);
    const skippedWithoutMedia = state.songs.filter(song => !song.reviewed && relatedMedia(song).length === 0).length;
    if (candidates.length === 0) {
        showMessage(skippedWithoutMedia
            ? `${skippedWithoutMedia} canto(s) pendientes no tienen imagen ni PDF para OCR.`
            : "No hay cantos pendientes de OCR.");
        return;
    }
    const unsavedWarning = state.dirty ? " Los cambios abiertos sin guardar se descartarán si ese canto entra en el lote." : "";
    if (!window.confirm(`Se procesarán ${candidates.length} canto(s) sin revisar con imagen o PDF. OCR_PROJECT reemplazará sus letras actuales y guardará respaldo cuando exista una letra.${skippedWithoutMedia ? ` Se omitirán ${skippedWithoutMedia} sin imagen/PDF.` : ""}${unsavedWarning} ¿Continuar?`)) {
        return;
    }

    state.ocrBusy = true;
    state.ocrBatch = true;
    state.stopOcrBatch = false;
    elements.chordPro.readOnly = true;
    elements.save.disabled = true;
    updateReviewButton();
    renderSongList();
    const failures = [];
    let processed = 0;
    for (let index = 0; index < candidates.length; index++) {
        if (state.stopOcrBatch) {
            break;
        }
        const { song, media } = candidates[index];
        showMessage(`OCR ${index + 1}/${candidates.length}: ${song.title}`);
        try {
            const result = await request("/api/library/ocr", {
                method: "POST",
                body: JSON.stringify({ path: media.path })
            });
            applyOcrResult(song, result);
            processed += 1;
        } catch (error) {
            failures.push(`${song.title}: ${error.message}`);
        }
    }
    state.ocrBatch = false;
    try {
        await refreshLibrary();
    } catch (error) {
        failures.push(`Actualizar biblioteca: ${error.message}`);
    }
    const stopped = state.stopOcrBatch;
    state.stopOcrBatch = false;
    state.ocrBusy = false;
    elements.chordPro.readOnly = false;
    setDirty(state.dirty);
    updateReviewButton();
    const summary = `${stopped ? "Lote detenido" : "Lote terminado"}: ${processed} procesado(s), ${failures.length} con error${skippedWithoutMedia ? `, ${skippedWithoutMedia} sin imagen/PDF` : ""}. Los cantos procesados siguen pendientes de revisión.`;
    showMessage(failures.length ? `${summary} ${failures.slice(0, 3).join(" · ")}` : summary, failures.length > 0);
    renderSongList();
}

function renderChordProPreview(content) {
    Melody.renderSong(document, content, elements.preview, document.querySelector("#separate-melody"));
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
        renderChordProPreview(latest.content);
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
    if (state.ocrBusy) {
        return;
    }
    if (!hasUnsavedChanges()) {
        return;
    }
    const selectionVersion = ++state.selectionVersion;
    clearMessage();
    state.selectedId = song.id;
    state.selectedSong = song;
    updateReviewButton();
    elements.editorFilename.textContent = fileForGroup(song)?.name || `${song.title}.cho`;
    elements.editorFolder.textContent = song.folder || "Biblioteca principal";
    elements.referenceTitle.textContent = song.title;
    elements.previewTitle.textContent = song.title;
    elements.chordPro.value = "";
    renderChordProPreview("");
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
            renderChordProPreview(document.content);
        } catch (error) {
            if (selectionVersion !== state.selectionVersion) {
                return;
            }
            state.chordProPath = null;
            state.revision = null;
            state.savedContent = "";
            elements.chordPro.value = "";
            renderChordProPreview("");
            showMessage(error.message, true);
        }
    } else {
        state.chordProPath = editorPathFor(song);
        state.revision = null;
        state.savedContent = "";
        state.chordProPath = editorPathFor(song);
        renderChordProPreview("");
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
        updateReviewButton();
        return;
    }
    for (const file of media) {
        elements.referenceSelect.add(new Option(file.name, file.path));
    }
    elements.referenceSelect.disabled = false;
    elements.referenceSelect.value = media[0].path;
    renderReference(media[0]);
    updateReviewButton();
    updateOcrButton();
}

function setPanelWidths(libraryWidth, editorWidth, previewWidth, referenceWidth) {
    const workspace = document.querySelector(".workspace");
    const left = Math.max(170, libraryWidth);
    const editor = Math.max(220, editorWidth);
    const preview = Math.max(220, previewWidth);
    const right = Math.max(220, referenceWidth);
    workspace.style.gridTemplateColumns =
        `${left}px 7px ${editor}px 7px ${preview}px 7px ${right}px`;
}

for (const splitter of document.querySelectorAll(".panel-splitter")) {
    splitter.addEventListener("pointerdown", event => {
        event.preventDefault();
        const library = document.querySelector(".library-panel");
        const editor = document.querySelector(".editor-panel");
        const preview = document.querySelector(".preview-panel");
        const reference = document.querySelector(".reference-panel");
        const startX = event.clientX;
        const startLibrary = library.getBoundingClientRect().width;
        const startEditor = editor.getBoundingClientRect().width;
        const startPreview = preview.getBoundingClientRect().width;
        const startReference = reference.getBoundingClientRect().width;
        splitter.setPointerCapture(event.pointerId);

        const onMove = moveEvent => {
            const delta = moveEvent.clientX - startX;
            const widths = [startLibrary, startEditor, startPreview, startReference];
            const pairIndex = { library: 0, preview: 1, reference: 2 }[splitter.dataset.splitter];
            const pairWidth = widths[pairIndex] + widths[pairIndex + 1];
            const firstMinimum = pairIndex === 0 ? 170 : 220;
            const firstWidth = Math.max(firstMinimum,
                Math.min(pairWidth - 220, widths[pairIndex] + delta));
            widths[pairIndex] = firstWidth;
            widths[pairIndex + 1] = pairWidth - firstWidth;
            setPanelWidths(...widths);
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
        const previewWidth = document.querySelector(".preview-panel").getBoundingClientRect().width;
        const referenceWidth = document.querySelector(".reference-panel").getBoundingClientRect().width;
        const delta = event.key === "ArrowRight" ? 24 : -24;
        const widths = [libraryWidth, editorWidth, previewWidth, referenceWidth];
        const pairIndex = { library: 0, preview: 1, reference: 2 }[splitter.dataset.splitter];
        const pairWidth = widths[pairIndex] + widths[pairIndex + 1];
        const firstMinimum = pairIndex === 0 ? 170 : 220;
        const firstWidth = Math.max(firstMinimum,
            Math.min(pairWidth - 220, widths[pairIndex] + delta));
        widths[pairIndex] = firstWidth;
        widths[pairIndex + 1] = pairWidth - firstWidth;
        setPanelWidths(...widths);
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

    const source = `/api/library/media?path=${encodeURIComponent(file.path)}&v=${Date.now()}`;
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

function openDeleteDialog(song) {
    elements.deleteSongTitle.textContent = `¿Qué archivo de “${song.title}” quieres eliminar?`;
    elements.deleteSongFiles.replaceChildren();
    for (const file of song.files) {
        const option = document.createElement("button");
        option.type = "button";
        option.className = "delete-file-option";
        const name = document.createElement("span");
        name.textContent = file.name;
        const type = document.createElement("span");
        type.textContent = file.extension.toUpperCase();
        option.append(name, type);
        option.addEventListener("click", () => deleteLibraryFile(song, file));
        elements.deleteSongFiles.append(option);
    }
    elements.deleteSongDialog.showModal();
}

async function deleteLibraryFile(song, file) {
    if (!window.confirm(`¿Eliminar “${file.path}” permanentemente?`)) {
        return;
    }
    if (state.selectedSong?.id === song.id && state.chordProPath === file.path
        && !hasUnsavedChanges()) {
        return;
    }
    try {
        await request(`/api/library/file?path=${encodeURIComponent(file.path)}`, { method: "DELETE" });
        elements.deleteSongDialog.close();
        const selectedSongId = state.selectedSong?.id;
        const selectedChordProWasDeleted = selectedSongId === song.id && state.chordProPath === file.path;
        if (selectedChordProWasDeleted) {
            state.selectedId = null;
            state.selectedSong = null;
            updateReviewButton();
            state.chordProPath = null;
            state.revision = null;
            state.savedContent = "";
            elements.chordPro.value = "";
            renderChordProPreview("");
            elements.editorWrap.hidden = true;
            elements.emptyEditor.hidden = false;
            elements.editorFilename.textContent = "Selecciona un canto";
            elements.editorFolder.textContent = "";
            elements.previewTitle.textContent = "Canto";
            setDirty(false);
            state.songs = await request("/api/library");
            const remainingSong = state.songs.find(candidate =>
                candidate.folder === song.folder
                && candidate.title.localeCompare(song.title, "es", { sensitivity: "base" }) === 0
            );
            if (remainingSong) {
                await selectSong(remainingSong);
            } else {
                renderSongList();
            }
        } else {
            await refreshLibrary();
        }
        showMessage(`Se eliminó ${file.name}.`);
    } catch (error) {
        showMessage(error.message, true);
    }
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
            updateReviewButton();
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
                        renderChordProPreview(latest.content);
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
            updateReviewButton();
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

function openCreateSongDialog() {
    if (!hasUnsavedChanges()) {
        return;
    }
    const folders = [...new Set(state.songs.map(song => song.folder).filter(Boolean))]
        .sort((left, right) => left.localeCompare(right, "es", { sensitivity: "base" }));
    elements.newSongFolder.replaceChildren(new Option("Biblioteca principal", ""));
    folders.forEach(folder => elements.newSongFolder.add(new Option(folder, folder)));
    elements.newSongFolderStep.hidden = false;
    elements.newSongTitleStep.hidden = true;
    elements.newSongDialogTitle.textContent = "Selecciona una carpeta";
    elements.nextNewSong.hidden = false;
    elements.backNewSong.hidden = true;
    elements.createNewSong.hidden = true;
    elements.newSongTitle.value = "";
    elements.newSongDialog.showModal();
}

function showNewSongTitleStep() {
    elements.newSongFolderStep.hidden = true;
    elements.newSongTitleStep.hidden = false;
    elements.newSongDialogTitle.textContent = "Escribe el nombre del canto";
    elements.newSongFolderSummary.textContent =
        `Se guardará en: ${elements.newSongFolder.value || "Biblioteca principal"}`;
    elements.nextNewSong.hidden = true;
    elements.backNewSong.hidden = false;
    elements.createNewSong.hidden = false;
    elements.newSongTitle.focus();
}

function createSong() {
    const title = elements.newSongTitle.value.trim();
    if (!title) {
        elements.newSongTitle.focus();
        return;
    }
    const filename = title.trim().normalize("NFC").replace(/[<>:"/\\|?*\u0000-\u001f]/g, "-");
    const folder = elements.newSongFolder.value;
    state.selectionVersion += 1;
    const song = {
        id: `draft:${folder}/${filename}`,
        title,
        folder,
        files: []
    };
    state.selectedId = song.id;
    state.selectedSong = song;
    updateReviewButton();
    state.chordProPath = `${folder ? `${folder}/` : ""}${filename}.cho`;
    state.revision = null;
    state.savedContent = "";
    elements.editorFilename.textContent = `${filename}.cho`;
    elements.editorFolder.textContent = folder || "Biblioteca principal";
    elements.chordPro.value = "";
    elements.previewTitle.textContent = title;
    elements.referenceTitle.textContent = title;
    renderChordProPreview("");
    elements.editorWrap.hidden = false;
    elements.emptyEditor.hidden = true;
    renderLineNumbers();
    setDirty(true);
    renderReferenceOptions(song);
    renderSongList();
    elements.newSongDialog.close();
    elements.chordPro.focus();
}

elements.chordPro.addEventListener("input", () => {
    renderLineNumbers();
    renderChordProPreview(elements.chordPro.value);
    setDirty(elements.chordPro.value !== state.savedContent);
});
elements.chordPro.addEventListener("scroll", () => {
    elements.lineNumbers.scrollTop = elements.chordPro.scrollTop;
});
elements.review.addEventListener("click", toggleSongReviewed);
elements.ocrCurrent.addEventListener("click", () => {
    if (state.selectedSong) {
        runSingleOcr(state.selectedSong, elements.referenceSelect.value || null);
    }
});
elements.ocrPending.addEventListener("click", runBatchOcr);

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
document.querySelector("#new-song").addEventListener("click", openCreateSongDialog);
document.querySelector("#cancel-new-song").addEventListener("click", () => elements.newSongDialog.close());
document.querySelector("#next-new-song").addEventListener("click", showNewSongTitleStep);
document.querySelector("#back-new-song").addEventListener("click", () => {
    elements.newSongFolderStep.hidden = false;
    elements.newSongTitleStep.hidden = true;
    elements.newSongDialogTitle.textContent = "Selecciona una carpeta";
    elements.nextNewSong.hidden = false;
    elements.backNewSong.hidden = true;
    elements.createNewSong.hidden = true;
});
document.querySelector("#create-new-song").addEventListener("click", createSong);
elements.newSongTitle.addEventListener("keydown", event => {
    if (event.key === "Enter") {
        event.preventDefault();
        createSong();
    }
});
document.querySelector("#cancel-delete-song").addEventListener("click", () => elements.deleteSongDialog.close());
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
    if (!document.hidden && !state.ocrBusy) {
        refreshLibrary().catch(error => showMessage(error.message, true));
    }
}, 12000);

if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => navigator.serviceWorker.register("/sw.js"));
}


document.querySelector("#rename-song").addEventListener("click", async () => {
    const song = state.selectedSong;
    if (!song || state.ocrBusy) return;
    if (state.dirty) {
        showMessage("Guarda o descarta los cambios antes de renombrar los archivos.", true);
        return;
    }
    const name = window.prompt(`Nuevo nombre sin extensión. Se renombrarán estos archivos juntos:\n${song.files.map(file => file.name).join("\n")}\n\nEl título dentro de la letra se conserva.`, song.title);
    if (name === null || name === song.title) return;
    state.ocrBusy = true;
    state.selectionVersion++;
    elements.chordPro.readOnly = true;
    updateReviewButton();
    renderSongList();
    try {
        const renamed = await request("/api/library/rename", {
            method: "POST", body: JSON.stringify({ path: song.id, name })
        });
        state.selectedId = null;
        state.selectedSong = null;
        state.chordProPath = null;
        state.revision = null;
        state.songs = await request("/api/library");
        state.ocrBusy = false;
        renderSongList();
        await selectSong(renamed);
        showMessage("Se renombraron los archivos del canto y se conservaron su revisión y observaciones.");
    } catch (error) {
        showMessage(error.message, true);
    } finally {
        state.ocrBusy = false;
        elements.chordPro.readOnly = false;
        updateReviewButton();
        renderSongList();
    }
});


let replacingReference = false;
const referenceUploadInput = document.querySelector("#reference-upload-file");
document.querySelector("#upload-reference").addEventListener("click", () => {
    replacingReference = false;
    referenceUploadInput.accept = ".pdf,.png,.jpg,.jpeg,.webp";
    referenceUploadInput.click();
});
document.querySelector("#replace-reference").addEventListener("click", () => {
    replacingReference = true;
    const reference = state.selectedSong?.files.find(file => file.path === elements.referenceSelect.value);
    referenceUploadInput.accept = reference ? (reference.extension === "jpg" || reference.extension === "jpeg" ? ".jpg,.jpeg" : `.${reference.extension}`) : ".pdf,.png,.jpg,.jpeg,.webp";
    referenceUploadInput.click();
});
referenceUploadInput.addEventListener("change", () => {
    const file = referenceUploadInput.files[0];
    referenceUploadInput.value = "";
    if (file) uploadReference(file, replacingReference);
});
elements.referenceSelect.addEventListener("change", updateReviewButton);
const referencePanel = document.querySelector(".reference-panel");
referencePanel.addEventListener("dragover", event => {
    if (event.dataTransfer.types.includes("Files")) { event.preventDefault(); referencePanel.classList.add("upload-hover"); }
});
referencePanel.addEventListener("dragleave", event => {
    if (!referencePanel.contains(event.relatedTarget)) referencePanel.classList.remove("upload-hover");
});
referencePanel.addEventListener("drop", event => {
    event.preventDefault();
    referencePanel.classList.remove("upload-hover");
    if (event.dataTransfer.files.length !== 1) { showMessage("Arrastra un solo archivo a la vez.", true); return; }
    uploadReference(event.dataTransfer.files[0], false);
});
async function uploadReference(file, replace) {
    const song = state.selectedSong;
    if (state.ocrBusy) return;
    if (!song?.files.length) { showMessage("Crea y guarda primero el canto; después podrás adjuntar su referencia.", true); return; }
    if (file.size === 0 || file.size > 30 * 1024 * 1024) { showMessage("Selecciona un archivo de hasta 30 MB.", true); return; }
    const target = replace ? elements.referenceSelect.value : "";
    if (replace && !target) return;
    if (replace && !window.confirm(`¿Reemplazar ${target.split("/").pop()} por ${file.name}? Se conservará un respaldo y el canto quedará pendiente de revisión. La letra no se modifica.`)) return;
    state.ocrBusy = true;
    state.selectionVersion++;
    updateReviewButton();
    renderSongList();
    showMessage("Subiendo referencia…");
    try {
        const params = new URLSearchParams({path: song.id, filename: file.name});
        if (target) params.set("replace", target);
        const uploaded = await request(`/api/library/media?${params}`, {
            method: "POST", headers: {"Content-Type": "application/octet-stream"}, body: file
        });
        await refreshLibrary();
        const current = state.songs.find(item => item.files.some(entry => entry.path === uploaded.path));
        if (current) {
            state.selectedSong = current;
            state.selectedId = current.id;
            renderReferenceOptions(current);
            elements.referenceSelect.value = uploaded.path;
            renderReference(uploaded);
        }
        showMessage(replace ? "Referencia reemplazada; respaldo guardado. La letra se conserva y el canto queda pendiente de revisión." : "Referencia añadida. El canto queda pendiente de revisión; puedes aplicar OCR cuando quieras.");
    } catch (error) {
        showMessage(error.message, true);
    } finally {
        state.ocrBusy = false;
        updateReviewButton();
        renderSongList();
    }
}


function updateMelodyView() {
    const panel = document.querySelector(".preview-panel");
    const mode = document.querySelector("#melody-view").value;
    const chords = document.querySelector("#show-chords").checked;
    const size = document.querySelector("#song-view-size").value;
    const theme = document.querySelector("#song-view-theme").value;
    panel.classList.toggle("hide-chords", !chords);
    panel.classList.toggle("hide-melody", mode !== "inline");
    document.querySelector("#separate-melody").hidden = mode !== "separate";
    elements.preview.classList.add("song-sheet");
    const colors = theme === "dark" ? ["#191919", "#f0f0f0", "#f3c979", "#98d8f0"]
        : ["#fffdf8", "#202a35", "#245c76", "#795434"];
    for (const [index, name] of ["background", "text", "chord", "melody"].entries()) panel.style.setProperty(`--song-${name}`, colors[index]);
    elements.preview.style.fontSize = `${size}px`;
    document.querySelector("#separate-melody").style.fontSize = `${size}px`;
    try { localStorage.setItem("song-view-web", JSON.stringify({ mode, chords, size, theme })); } catch (_) {}
}
try {
    const prefs = JSON.parse(localStorage.getItem("song-view-web") || "null");
    if (prefs) {
        if (["inline","off","separate"].includes(prefs.mode)) document.querySelector("#melody-view").value = prefs.mode;
        document.querySelector("#show-chords").checked = prefs.chords !== false;
        if (["14","18","20","24"].includes(prefs.size)) document.querySelector("#song-view-size").value = prefs.size;
        if (["mobile","dark"].includes(prefs.theme)) document.querySelector("#song-view-theme").value = prefs.theme;
    }
} catch (_) {}
for (const selector of ["#melody-view", "#show-chords", "#song-view-size", "#song-view-theme"]) document.querySelector(selector).addEventListener("change", updateMelodyView);
updateMelodyView();


document.querySelector("#load-melody-example").addEventListener("click", async () => {
    if (state.ocrBusy || !hasUnsavedChanges()) return;
    try {
        const response = await fetch("/examples/misa-guelaguetza-entrada.cho");
        if (!response.ok) throw new Error("No se pudo cargar el ejemplo de melodía.");
        const content = await response.text();
        elements.newSongTitle.value = "Entrada - Misa Guelaguetza (prueba)";
        if (![...elements.newSongFolder.options].some(option => option.value === "")) {
            elements.newSongFolder.add(new Option("Biblioteca principal", ""));
        }
        elements.newSongFolder.value = "";
        createSong();
        elements.chordPro.value = content;
        renderChordProPreview(content);
        renderLineNumbers();
        setDirty(true);
        showMessage("Ejemplo abierto sin guardar. Las notas se transcribieron de la imagen; su alineación es aproximada y debe revisarse. La estrofa 3 no incluye melodía en el original.");
    } catch (error) {
        showMessage(error.message, true);
    }
});
