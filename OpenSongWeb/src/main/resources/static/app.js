const state = {
    songs: [],
    selectedId: null
};

const elements = {
    count: document.querySelector("#song-count"),
    list: document.querySelector("#song-list"),
    search: document.querySelector("#search-songs"),
    form: document.querySelector("#song-form"),
    id: document.querySelector("#song-id"),
    title: document.querySelector("#song-title"),
    author: document.querySelector("#song-author"),
    copyright: document.querySelector("#song-copyright"),
    folder: document.querySelector("#song-folder"),
    chordPro: document.querySelector("#song-chordpro"),
    heading: document.querySelector("#editor-heading"),
    preview: document.querySelector("#song-preview"),
    message: document.querySelector("#message"),
    deleteButton: document.querySelector("#delete-song")
};

async function request(url, options = {}) {
    const response = await fetch(url, {
        headers: { "Content-Type": "application/json" },
        ...options
    });
    if (!response.ok) {
        let message = `La solicitud falló (${response.status}).`;
        try {
            const body = await response.json();
            message = body.message || message;
        } catch {
            // Keep the HTTP status message when the server did not return JSON.
        }
        throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
}

function escapeHtml(value) {
    return value.replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
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

function renderSongList() {
    const query = elements.search.value.trim().toLocaleLowerCase();
    const songs = state.songs.filter(song =>
        `${song.title} ${song.author} ${song.folder}`.toLocaleLowerCase().includes(query)
    );
    elements.count.textContent = `${songs.length} ${songs.length === 1 ? "canción" : "canciones"}`;

    if (songs.length === 0) {
        elements.list.innerHTML = `<div class="song-list-empty">${
            state.songs.length ? "No hay canciones que coincidan con la búsqueda." : "Tu biblioteca está vacía. Crea la primera canción."
        }</div>`;
        return;
    }

    elements.list.replaceChildren(...songs.map(song => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "song-item";
        button.setAttribute("aria-current", song.id === state.selectedId ? "true" : "false");
        const title = document.createElement("strong");
        title.textContent = song.title;
        const description = document.createElement("span");
        description.textContent = [song.folder || "Raíz", song.author].filter(Boolean).join(" · ");
        button.append(title, description);
        button.addEventListener("click", () => loadSong(song.id));
        return button;
    }));
}

function renderPreview() {
    const lines = elements.chordPro.value.split(/\r?\n/).filter(line => !/^\s*\{[^}]+}\s*$/.test(line));
    elements.preview.innerHTML = lines.map(line =>
        escapeHtml(line).replace(/\[([^\]]+)]/g, '<span class="preview-chord">[$1]</span>')
    ).join("\n");
}

function resetForm() {
    state.selectedId = null;
    elements.form.reset();
    elements.id.value = "";
    elements.heading.textContent = "Nueva canción";
    elements.deleteButton.hidden = true;
    elements.chordPro.value = "";
    renderSongList();
    renderPreview();
    clearMessage();
}

async function refreshSongs() {
    state.songs = await request("/api/songs");
    renderSongList();
}

async function loadSong(id) {
    clearMessage();
    try {
        const song = await request(`/api/song?path=${encodeURIComponent(id)}`);
        state.selectedId = song.id;
        elements.id.value = song.id;
        elements.title.value = song.title;
        elements.author.value = song.author;
        elements.copyright.value = song.copyright;
        elements.folder.value = song.folder;
        elements.chordPro.value = song.chordPro;
        elements.heading.textContent = "Editar canción";
        elements.deleteButton.hidden = false;
        renderSongList();
        renderPreview();
    } catch (error) {
        showMessage(error.message, true);
    }
}

elements.form.addEventListener("submit", async event => {
    event.preventDefault();
    clearMessage();
    const payload = {
        id: elements.id.value || null,
        title: elements.title.value,
        author: elements.author.value,
        copyright: elements.copyright.value,
        folder: elements.folder.value,
        chordPro: elements.chordPro.value
    };
    try {
        const saved = await request("/api/song", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        state.selectedId = saved.id;
        elements.id.value = saved.id;
        elements.heading.textContent = "Editar canción";
        elements.deleteButton.hidden = false;
        elements.chordPro.value = saved.chordPro;
        await refreshSongs();
        showMessage("Canción guardada en tu biblioteca.");
        renderPreview();
    } catch (error) {
        showMessage(error.message, true);
    }
});

elements.deleteButton.addEventListener("click", async () => {
    if (!state.selectedId || !window.confirm("¿Eliminar esta canción de la biblioteca?")) {
        return;
    }
    try {
        await request(`/api/song?path=${encodeURIComponent(state.selectedId)}`, { method: "DELETE" });
        resetForm();
        await refreshSongs();
        showMessage("Canción eliminada.");
    } catch (error) {
        showMessage(error.message, true);
    }
});

document.querySelector("#new-song").addEventListener("click", resetForm);
document.querySelector("#reset-song").addEventListener("click", () => {
    if (state.selectedId) {
        loadSong(state.selectedId);
    } else {
        resetForm();
    }
});
elements.search.addEventListener("input", renderSongList);
elements.chordPro.addEventListener("input", renderPreview);

refreshSongs().catch(error => {
    elements.count.textContent = "No se pudo cargar la biblioteca";
    showMessage(error.message, true);
});
renderPreview();

if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => navigator.serviceWorker.register("/sw.js"));
}
