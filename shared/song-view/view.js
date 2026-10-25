"use strict";
let baseSize = 18;
function updateView() {
    const mode = document.querySelector("#mode").value;
    document.querySelector("#sheet").classList.toggle("hide-melody", mode !== "inline");
    document.querySelector("#sheet").classList.toggle("hide-chords", !document.querySelector("#chords").checked);
    document.querySelector("#separate").hidden = mode !== "separate";
    document.querySelector("#sheet").style.fontSize = `${baseSize * Number(document.querySelector("#size").value)}px`;
    try { localStorage.setItem("song-view", JSON.stringify({mode, chords:document.querySelector("#chords").checked, size:document.querySelector("#size").value})); } catch (_) {}
}
window.SongView = { load(data) {
    document.querySelector("#title").textContent = data.title || "";
    for (const [property, value] of Object.entries(data.theme || {})) {
        if (/^--song-(text|chord|melody|background)$/.test(property) && /^#[0-9a-f]{6}$/i.test(value)) document.body.style.setProperty(property, value);
    }
    baseSize = Math.max(12, Math.min(40, Number(data.fontSize) || 18));
    if (data.labels) {
        document.querySelector("#chords-label").textContent = data.labels.chords;
        document.querySelector("#melody-label").textContent = data.labels.melody;
        document.querySelector("#size-label").textContent = data.labels.size;
        [...document.querySelector("#mode").options].forEach((option,index) => option.textContent = data.labels.modes[index]);
    }
    Melody.renderSong(document, data.content || "", document.querySelector("#preview"), document.querySelector("#separate"));
    try {
        const prefs = JSON.parse(localStorage.getItem("song-view") || "null");
        if (prefs && ["inline","off","separate"].includes(prefs.mode)) {
            document.querySelector("#mode").value = prefs.mode;
            document.querySelector("#chords").checked = prefs.chords !== false;
            if (["0.85","1","1.2","1.5"].includes(prefs.size)) document.querySelector("#size").value = prefs.size;
        }
    } catch (_) {}
    updateView();
}};
for (const id of ["mode","chords","size"]) document.querySelector(`#${id}`).addEventListener("change", updateView);
