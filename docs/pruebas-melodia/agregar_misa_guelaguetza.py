"""Transcripción de las ocho imágenes aportadas el 24-10-2026. No sobrescribe cantos."""
from pathlib import Path
import re
import json
import shutil

ROOT = Path(__file__).resolve().parents[2]
LIBRARY = ROOT / "app/src/main/assets/DefaultSongs"
TEMP = Path("C:/Users/degel/AppData/Local/Temp")
SONGS = []

def song(folder, title, key, image, lines):
    lines = [(lyric, re.sub(r" +(?=<[^<>]+>$)", "", melody) if melody else melody) for lyric, melody in lines]
    SONGS.append((folder, title, key, image, lines))

# Cada tupla contiene la letra con acordes y su copia con anclajes de melodía.
# None significa que la fuente no incluye melodía en ese verso.
song("Piedad", "Piedad - Misa Guelaguetza", "Em", "0c7154b9-1b4b-43c5-884f-c67c4003817f", [
("{comment: Intro; segunda frase dos veces.}", None),
("[Am]Intro 1[Em]", "<do la fa# la fa# si sol>Intro 1"),
("[B7]Intro 2[Em] (2)", "<fa# fa# sol fa# mi re# mi>Intro 2 (2)"),
("", None),
("[Em]Tú que borras nuestras [Am]culpas", "<sol>Tú <sol>que <sol>bo<sol>rras <fa#>nuestras <sol>cul<fa#>pa<mi>s"),
("[B7]Señor ten [Em]piedad, [Am]Señor ten [Em]piedad.", "<fa#>Se<fa#>ñor <fa#>ten <fa#>pie<sol>dad, <do>Se<fa#>ñor ten <sol>piedad. <la si>"),
("", None),
("[Em]Tú que creas un corazón [G]puro", "<mi>Tú <mi>que <mi>cre<mi>as <mi>un <mi>co<mi>ra<mi>zón <sol>pu<sol>ro"),
("[B7]Cristo ten [Em]piedad, [Am]Cristo ten [Em]piedad.", "<si>Cris<si>to <si>ten <la>pie<si>dad, <do>Cris<fa#>to ten <sol>piedad. <la si>"),
("", None),
("[Em]Tú que nos devuelves la [Am]alegría", "<sol>Tú <sol>que <sol>nos <sol>de<sol>vuel<sol>ves <sol>la <fa#>a<sol>le<fa#>grí<mi>a"),
("[B7]Señor ten [Em]piedad, [Am]Señor ten [Em]piedad.", "<fa#>Señor <fa#>ten <fa#>pie<fa#>dad, <sol>Señor <do>ten <fa#>pie<sol>dad. <la si>"),
])
song("Gloria", "Gloria - Misa Guelaguetza", "C", "c3166e95-bcaa-49d1-a780-206e5b24fa57", [
("[C]Intro [G7]1[C]", "<mi mi mi mi fa fa sol sol>Intro 1"),
("Intro 2", "<re re mi fa mi re do mi sol do>Intro 2"),
("", None),
("[C]GLORIA A DIOS EN EL CIELO", "<sol>GLO<sol>RIA A <sol>DIOS <fa>EN <mi>EL <sol>CIE<sol>LO"),
("TE ALABA TU PUEBLO SE[G7]ÑOR", "<mi>TE <mi>A<mi>LABA <mi>TU <re>PUEBLO <mi>SE<fa>ÑOR"),
("PAZ EN LA TIERRA A LOS HOMBRES", "<si>PAZ <si>EN <si>LA <la>TIERRA <sol>A LOS <si>HOM<si>BRES"),
("Y A LOS QUE AMA EL SE[C]ÑOR,", "<fa>Y A <fa>LOS <sol>QUE <la>AMA <sol>EL <fa>SE<mi>ÑOR,"),
("", None),
("[F]Te bendecimos, Glorificado al Se[C]ñor", "<fa>Te <la>ben<do>de<fa>ci<fa>mos, <fa>Glo<fa>ri<sol>ficado <la>al <sol>Se<fa>ño<mi>r"),
("Juntos te damos [G7]gracias y te adoramos tam[C]bién.", "<mi mi>Jun<mi>tos <mi>te <fa>damos <fa>gracias <sol>y <sol>te <re>ado<re>ramos <mi>tam<fa>bi<mi>én. <re do>"),
("", None),
("[F]Rey celestial, poderoso es mi [C]Dios", "<fa>Rey <la>ce<do>les<fa>ti<fa>al, <fa>po<fa>de<sol>roso <la>es <sol>mi <fa>Di<mi>os"),
("Jesucristo Hijo [G7]único, Hijo del Padre y Cor[C]dero.", "<mi>Je<mi>su<mi>cris<mi>to <fa>Hi<fa>jo <sol>ú<sol>nico, <re>Hi<re>jo <mi>del <fa>Padre <mi>y <re>Cor<do>dero."),
("", None),
("[F]Ten piedad de nosotros, quita el pecado del [C]mundo", "<fa>Ten <la>pie<la>dad <do>de <do>no<fa>so<fa>tros, <fa>quita <fa>el <sol>pecado <la>del <sol>mun<fa>do <mi mi>"),
("Atiende a nuestra [G7]súplica, desde la diestra del [C]padre", "<mi mi>A<mi>tien<mi>de <fa>a <fa>nuestra <sol>sú<sol>plica, <re>desde <re>la <mi>diestra <fa>del <mi>padre <re do>"),
("", None),
("[F]Tu eres Santo, solo Señor el Al[C]tísimo", None),
("Con el Espíritu [G7]Santo, Gloria al Padre y al [C]Hijo.", None),
])
song("Aleluya", "Aleluya - Misa Guelaguetza", "G", "6771290a-65bb-4289-b002-14ce8abb65eb", [
("{comment: Intro. Se conservan los superíndices 3 de la fuente sin asignarles otra octava.}", None),
("[G]Intro 1", "<re si sol³ re si sol³>Intro 1"),
("[C]Intro 2", "<sol mi do sol mi do>Intro 2"),
("[D7]Intro [G]3", "<re la fa#³ re la fa#³ sol³>Intro 3"),
("", None),
("[G]ALELUYA [Em]ALELUYA, [D7]ALE[G]LUYA (2)", "<sol>A<sol>LE<la>LU<si>YA <si>ALE<la>LU<sol>YA, <la>ALE<sol>LU<fa#>YA <sol>(2)"),
("", None),
("[G]Cristo sube a las al[Em]turas", "<re>Cris<re>to <re>sube <re>a <mi>las <fa#>al<sol>tu<mi>ras"),
("[Am]Cristo reina en no[D7]sotros.", "<la>Cris<la>to <la>rei<la>na <la>en <sol>no<si>so<la>tros."),
])
song("ofertorio", "Ofertorio - Misa Guelaguetza", "D", "da6f3f69-9fd9-46aa-87e2-2bc23fc3c904", [
("{comment: Intro: D G A7 D A7 D.}", None),
("[D]Intro [G]1[A7]", "<la sol fa# si la sol fa# mi fa# sol mi>Intro 1"),
("[D]Intro [A7]2", "<la sol fa# mi re mi fa# re>Intro 2"),
("[D]Intro 3", "<sol fa# mi re do# mi re>Intro 3"),
("", None),
("[D]JUSTO AL PONERSE EL [Bm]SOL", "<re>JUSTO <re>AL <la>PO<la>NERSE <si>EL <do#>SO<re>L"),
("[G]DIOS NOS HABLA A CADA [A7]UNO", "<sol>DIOS <sol>NOS <sol>HA<sol>BLA <fa#>A <sol>CADA <fa#>U<mi>NO"),
("[D]PIDE MUESTRAS DE SU A[G]MOR", "<la>PI<la>DE <la>MUES<la>TRAS <sol>DE <fa#>SU A<si>MOR"),
("[A7]CADA DÍA QUE HA PA[D]SADO.", "<mi>CADA <fa#>DÍA <sol>QUE <sol>HA <fa#>PA<mi>SA<mi>DO. <re>"),
("", None),
("[D]Recibe nuestras ofrendas, [Em]Que [A7]te sean agra[D]dables", "<re>Re<re>ci<re>be <re>nuestras <do#>ofren<si>das, <mi>Que <si>te <mi>sean <fa#>a<sol>gra<sol>da<fa#>bles <mi la fa#>"),
("[Bm]Estos dones son el signo, de a[G]mistad [A7]y grati[D]tud,", "<si>Es<si>tos <si>dones <si>son <la>el <si>signo, <la>de <sol>a<mi>mistad <fa#>y <sol>gra<sol>ti<fa#>tud, <mi la fa#>"),
("", None),
("[D]El cultivo de la tierra, [Em]esperanza [A7]de este [D]pueblo", None),
("[Bm]Esta sangre derramada, [G]redención [A7]que trae [D]paz,", None),
("", None),
("[D]Con cariño estos presentes, [Em]hoy [A7]ponemos en tu [D]altar", None),
("[Bm]Y con ellos nuestra vida, es o[G]frenda [A7]y amis[D]tad.", None),
])
song("santo", "Santo - Misa Guelaguetza", "C", "e8698c01-6ddf-4261-afe8-78e6ca23199d", [
("[C]Santo, Santo, Santo, Santo eres Se[G7]ñor", "<sol>San<mi>to, <do>San<sol>to, <la>San<sol>to, <do>Santo <re>eres <mi>Se<re>ñor <do sol>"),
("Dios del universo, Gloria en la tierra sin [C]fin", "<fa>Dios <fa>del <la>uni<sol>ver<la>so, <sol>Gloria <re>en <re>la <mi>tierra <fa>sin <mi>fin <re do>"),
("[F]HOSANNA en el [C]cielo, [G7]HOSANNA al Padre [C]Dios,", "<fa>HO<fa>SAN<fa>NA <la>en <sol>el <la>cie<sol>lo, <si>HOSANNA <si>al <do>Pa<re>dre <do>Dios, <si do>"),
("", None),
("[C]Santo, Santo, Santo, Santo eres Se[G7]ñor", "<sol>San<mi>to, <do>San<sol>to, <la>San<sol>to, <do>Santo <re>eres <mi>Se<re>ñor <do sol>"),
("Bendito el que viene, hecho Cordero y Se[C]ñor", "<fa>Ben<fa>dito <la>el <sol>que <la>viene, <sol>hecho <re>Cor<re>de<mi>ro <fa>y <mi>Señor <re do>"),
("[F]HOSANNA en el [C]cielo, [G7]HOSANNA al Padre [C]Dios.", "<sol>HO<mi>SAN<do>NA <sol>en <la>el <sol>cielo, <do>HOSANNA <re>al <mi>Padre <re>Dios. <do sol>"),
])
song("CristoConElyEnEl", "Por Cristo con Él y en Él - Misa Guelaguetza", "C", "5a188a26-0827-4736-a8f1-c92e5aa26eaf", [
("[C]Por Cristo con Él y en Él, a ti [F]Dios Padre", "<sol>Por <mi>Cristo <do>con <sol>Él <mi>y <do>en <la>Él, <la>a <la>ti <la ···>Dios Padre"),
("Omnipo[C]tente", "<sol>Om<fa>ni<sol>po<mi>tente"),
("[Am]En la unidad del Espíritu [Dm]Santo,", "<mi>En <mi>la <mi>uni<mi>dad <mi>del <mi>Es<mi>pí<re>ri<do>tu <re>San<re>to,"),
("[G7]Todo honor y toda [C]Gloria", "<re>To<re>do <re>honor <re>y <mi>toda <fa>Glo<la>ri<sol>a"),
("[G7]Por los siglos de los siglos, A[C]mén.", "<re>Por <re>los <re>siglos <re>de <re>los <re>siglos, <mi>A<fa>mé<mi>n."),
("", None),
("[F]AMÉN, [G7]AMÉN, [C]AMÉN (2)", "<do>A<la>MÉN, <la>A<sol>MÉN, <si>A<do>MÉN (2)"),
])
song("Oracion", "Padre nuestro - Misa Guelaguetza", "C", "514c3cc7-c7e9-4972-8b84-2b38a05826c5", [
("[C]Padre nuestro que estás en el [G7]cielo", "<do>Pa<re>dre <mi>nuestro <mi>que <mi>estás <mi>en <re>el <do>cie<sol>lo <fa>"),
("Santificado sea tu [C]nombre", "<si>San<si>ti<do>fi<re>cado <re>sea <re>tu <do>nom<si>bre <re do>"),
("[F]Venga a nosotros tu [G7]Reino", "<la ···>Venga a nosotros tu <sol>Reino"),
("Hágase tu voluntad", "<si>Há<do>gase tu <re>voluntad"),
("En la tierra como en el [C]cielo", "<re ···>En la tierra como <do>en <si>el <re>cie<do>lo"),
("", None),
("[C]Danos hoy de nuestro [G7]pan", "<do ···>Danos hoy <si>de <do>nuestro <re>pan"),
("De cada [C]día", "<fa>De <mi>ca<re>da <fa>dí<mi>a"),
("[Dm]Perdona nuestras o[C]fensas", "<re ···>Perdona nuestras <do#>o<re>fen<fa>sa<mi>s"),
("[F]Como nosotros perdo[G7]namos", "<la ···>Como nosotros <sol#>per<la>do<do>na<si>mos"),
("[Dm]A los que nos o[C]fenden", "<do>A <re>los <fa>que <mi>nos <re>o<fa>fen<mi>den"),
("No nos dejes caer en ten[Am]tación", "<do>No <do>nos <do>de<do>jes <si>ca<do>er <si>en <do>ten<re>ta<mi>ción"),
("[F]Y líbranos del [G]mal y líbranos del [C]mal.", "<mi>Y <fa ···>líbranos del <sol>mal <sol>y <si ···>líbranos del <do>mal."),
])
song("cordero", "Cordero - Misa Guelaguetza", "Em", "6126daa2-319b-4fe3-876c-32f40f4dc629", [
("[Em]Cordero de [D]Dios", "<si>Cor<mi>dero <re>de <re>Di<fa#>os"),
("[Am]Que quitas el [Em]pecado", "<re>Que <la>qui<la>tas <la>el <sol>pe<si>ca<sol>do"),
("Ten [B7]piedad de no[Em]sotros (2)", "<si>Ten <si>pie<fa#>dad <mi>de <re#>no<fa#>so<mi>tros (2)"),
("", None),
("[Em]Cordero de [D]Dios", "<si>Cor<mi>dero <re>de <re>Di<fa#>os"),
("[Am]Que quitas el [Em]pecado", "<re>Que <la>qui<la>tas <la>el <sol>pe<si>ca<sol>do"),
("Danos la [D]paz", "<mi>Da<fa#>nos <sol>la <la>paz"),
("[Am]Danos la [G]paz.", "<la>Da<la>nos <sol>la <si>paz."),
])


def main():
    # Check ALL targets and anchors before touching the library.
    import re
    targets = []
    for folder, title, key, image, lines in SONGS:
        basename = LIBRARY / folder / title
        source_image = TEMP / ("codex-clipboard-" + image + ".png")
        assert source_image.is_file(), source_image
        for suffix in (".cho", ".png"):
            target = basename.with_suffix(suffix)
            assert not target.exists(), "Already exists: " + str(target)
        for lyric, melody in lines:
            if melody is not None:
                plain = re.sub(r"\[[^\]]+\]", "", lyric)
                melody_plain = re.sub(r"<[^<>]+>", "", melody)
                assert plain == melody_plain, (title, plain, melody_plain)
        targets.append((basename, source_image))
    report_path = LIBRARY / "revision-cantos.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    for (folder, title, key, image, lines), (basename, source_image) in zip(SONGS, targets):
        basename.parent.mkdir(parents=True, exist_ok=True)
        content = ["{title: " + title.removesuffix(" - Misa Guelaguetza") + "}",
                   "{subtitle: Misa Guelaguetza}", "{key: " + key + "}",
                   "{comment: Melodía transcrita de la imagen. Alineación aproximada pendiente de revisión.}", ""]
        for lyric, melody in lines:
            content.append(lyric)
            if melody is not None:
                content.append("{comment: melodia: " + melody + "}")
        with basename.with_suffix(".cho").open("x", encoding="utf-8", newline="\n") as output:
            output.write("\n".join(content) + "\n")
        shutil.copyfile(source_image, basename.with_suffix(".png"))
        report["songs"].append({"folder": folder, "title": title, "status": "pendiente",
            "needsAttention": True,
            "notes": "Añadido desde imagen el 24-10-2026. Letra, acordes y melodía textual transcritos; anclajes aproximados pendientes de cotejo musical. Se conservan las secuencias instrumentales y prolongaciones visibles; no se inventa melodía en los versos sin notas. Las líneas subrayadas de la fuente no se interpretan como duración musical.",
            "files": [str(basename.with_suffix(suffix).relative_to(LIBRARY)).replace("\\", "/") for suffix in (".cho", ".png")]})
        print(folder + "/" + title)
    backup = ROOT / "docs/pruebas-melodia/revision-cantos.antes-guelaguetza.json.bak"
    with backup.open("xb") as output:
        output.write(report_path.read_bytes())
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
