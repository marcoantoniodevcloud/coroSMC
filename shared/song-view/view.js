"use strict";
let baseSize = 20;
const palettes = { mobile:["#fffdf8", "#202a35", "#245c76", "#795434"], dark:["#191919", "#f0f0f0", "#f3c979", "#98d8f0"] };
// Fit the unzoomed song first; the size selector remains a manual zoom on top.
function fitSongWidth() {
    const sheet = document.querySelector("#sheet");
    const panels = [document.querySelector("#preview"), document.querySelector("#separate")];
    let low = 0;
    let high = baseSize;
    sheet.style.fontSize = `${high}px`;
    const fits = () => panels.every(panel => panel.hidden || panel.clientWidth === 0 || panel.scrollWidth <= panel.clientWidth);
    if (!fits()) {
        for (let i = 0; i < 16; i++) {
            const middle = (low + high) / 2;
            sheet.style.fontSize = `${middle}px`;
            if (fits()) low = middle;
            else high = middle;
        }
        high = low;
    }
    sheet.style.fontSize = `${high * Number(document.querySelector("#size").value)}px`;
}
function updateView() {
    const colors = palettes[document.querySelector("#theme").value] || palettes.mobile;
    ["background","text","chord","melody"].forEach((name,i) => document.body.style.setProperty(`--song-${name}`, colors[i]));
    const mode = document.querySelector("#mode").value;
    document.querySelector("#sheet").classList.toggle("hide-melody", mode !== "inline");
    document.querySelector("#sheet").classList.toggle("hide-chords", !document.querySelector("#chords").checked);
    document.querySelector("#separate").hidden = mode !== "separate";
    fitSongWidth();
    try { localStorage.setItem("song-view", JSON.stringify({theme:document.querySelector("#theme").value, mode, chords:document.querySelector("#chords").checked, size:document.querySelector("#size").value})); } catch (_) {}
}
window.SongView = { load(data) {
    document.querySelector("#size").value = "1";
    const content = data.content || "";
    const directive = name => content.match(new RegExp("^\\{(?:" + name + "):\\s*([^}]+)\\}", "im"))?.[1] || "";
    document.querySelector("#title").textContent = data.title || directive("title|t");
    document.querySelector("#metadata").textContent = [data.author || directive("artist|author"), data.key || directive("key")].filter(Boolean).join(" · ");
    const colors = palettes[data.options?.theme] || palettes.mobile;
    ["background","text","chord","melody"].forEach((name,i) => document.body.style.setProperty(`--song-${name}`, colors[i]));
    for (const [property, value] of Object.entries(data.theme || {})) {
        if (/^--song-(text|chord|melody|background)$/.test(property) && /^#[0-9a-f]{6}$/i.test(value)) document.body.style.setProperty(property, value);
    }
    baseSize = Math.max(12, Math.min(40, Number(data.fontSize) || 20));
    if (data.labels) {
        document.querySelector("#chords-label").textContent = data.labels.chords;
        document.querySelector("#melody-label").textContent = data.labels.melody;
        document.querySelector("#size-label").textContent = data.labels.size;
        const optionsLabel = data.labels.viewOptions || "Opciones de vista";
        document.querySelector("#view-options-label").textContent = optionsLabel;
        document.querySelector("#view-options-toggle").setAttribute("aria-label", optionsLabel);
        document.querySelector("#view-options-toggle").title = optionsLabel;
        document.querySelector("#view-options-close").textContent = data.labels.done || "Listo";
        [...document.querySelector("#mode").options].forEach((option,index) => option.textContent = data.labels.modes[index]);
    }
    Melody.renderSong(document, data.content || "", document.querySelector("#preview"), document.querySelector("#separate"));
    try {
        const prefs = JSON.parse(localStorage.getItem("song-view") || "null");
        if (prefs && ["inline","off","separate"].includes(prefs.mode)) {
            document.querySelector("#mode").value = prefs.mode;
            document.querySelector("#theme").value = prefs.theme === "dark" ? "dark" : "mobile";
            document.querySelector("#chords").checked = prefs.chords !== false;
        }
    } catch (_) {}
    if (data.options) {
        document.querySelector("#theme").value = data.options.theme || "mobile";
        document.querySelector("#mode").value = data.options.mode || "inline";
        document.querySelector("#chords").checked = data.options.chords !== false;
        baseSize = Number(data.options.size) || 20;
        document.querySelector("#size").value = "1";
    }
    updateView();
    document.fonts.ready.then(fitSongWidth);
}};
for (const id of ["mode","chords","size","theme"]) document.querySelector(`#${id}`).addEventListener("change", updateView);

// Refit after the bundled font loads and when the layout width changes.
// Pinch zoom uses the visual viewport and must not undo the user's zoom.
document.fonts.ready.then(fitSongWidth);
let layoutWidth = document.documentElement.clientWidth;
window.addEventListener("resize", () => {
    const width = document.documentElement.clientWidth;
    if (width !== layoutWidth) {
        layoutWidth = width;
        fitSongWidth();
    }
});

const optionsDialog = document.querySelector("#view-options");
const optionsToggle = document.querySelector("#view-options-toggle");
function positionOptions() {
    const bottom = optionsToggle.getBoundingClientRect().bottom;
    optionsDialog.style.setProperty("--options-top", `${Math.min(bottom + 8, window.innerHeight / 2)}px`);
}
optionsToggle.addEventListener("click", () => {
    positionOptions();
    optionsDialog.showModal();
    optionsToggle.setAttribute("aria-expanded", "true");
});
document.querySelector("#view-options-close").addEventListener("click", () => optionsDialog.close());
optionsDialog.addEventListener("close", () => {
    optionsToggle.setAttribute("aria-expanded", "false");
    optionsToggle.focus({preventScroll:true});
});
// Backdrop clicks target the dialog; only close when both ends are outside.
function outsideOptions(event) {
    const rect = optionsDialog.getBoundingClientRect();
    return event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom;
}
let backdropPressed = false;
optionsDialog.addEventListener("pointerdown", event => { backdropPressed = event.target === optionsDialog && outsideOptions(event); });
optionsDialog.addEventListener("click", event => {
    if (backdropPressed && event.target === optionsDialog && outsideOptions(event)) optionsDialog.close();
    backdropPressed = false;
});
window.addEventListener("resize", positionOptions);
