"""
One-shot script: injects a Design Notes sheet into HearthCraft_Encounter_Builder.xlsx.
Run from the tools/sim/ directory. Safe to re-run — overwrites the sheet if already present.
"""
import zipfile, shutil, re, os

SRC = "HearthCraft_Encounter_Builder.xlsx"
TMP = "_enc_tmp.xlsx"

ROWS = [
    (1,  [("A", "HearthCraft -- Encounter Design Notes")]),
    (2,  [("A", "Philosophy, food ladders, and tuning rationale for every encounter in the difficulty ramp.")]),
    (3,  [("A", "Keep this sheet updated whenever an encounter is tuned or a new one is added.")]),

    (5,  [("A", "DESIGN PHILOSOPHY")]),
    (6,  [("A", "Each rung of the difficulty ladder adds exactly one new question the fight asks. Early content (Rungs 0-2) has no Dread or Shadow by construction -- those are the last two hazards and cannot appear until the player has mastered the fundamentals.")]),
    (7,  [("A", "Food ladder rule: each encounter should have a win window spanning ~3-4 consecutive recipe levels. Lv1 of the tier = occasional win (~20-30%). Mid-tier = squeaky zone (55-70%). Tier ceiling = comfortable (95%+). Next tier entry = near-pristine.")]),
    (8,  [("A", "Spike variance: interval is randomised +/-50% around mean, damage +/-30% around base. Breaks the binary win/loss cliff that fixed spikes produce. Creates genuine run-to-run variance and near-miss stories.")]),
    (9,  [("A", "HP/s curve: convex within each tier (power=1.8). Small gains at tier entry, biggest reward approaching tier ceiling. Pulls players toward the next tier rather than farming the current one.")]),
    (10, [("A", "PhysMit: do not add armor until Rung 1 (mailed orcs, stone-trolls). Early encounters are pure sustain tests -- armor would confuse the lesson before penetration mechanics are introduced.")]),
    (11, [("A", "Dread: do not add until Rung 5 (barrow-wights, the Dead). Dread suppresses DPS -- the player must already understand the win axis before it is taken away.")]),
    (12, [("A", "Shadow: do not add until Rung 6 (great spiders, Balrog, the Nine). Shadow drains Will+Fate over time -- the deepest mechanic, reserved for the deepest content.")]),

    (14, [("A", "DIFFICULTY RAMP")]),
    (15, [("A", "Rung"), ("B", "New question"), ("C", "Teacher foes"), ("D", "Region"), ("E", "Provisioning answer")]),
    (16, [("A", "0"), ("B", "Survive + win at all?"), ("C", "Neekerbreekers, wolves, lone goblins"), ("D", "Bree-land, Midgewater, Chetwood"), ("E", "HP/s food + damage")]),
    (17, [("A", "1"), ("B", "Pierce armor?"), ("C", "Mailed orcs, stone-trolls"), ("D", "Trollshaws, Goblin-town"), ("E", "Agi/Mig food (penetration)")]),
    (18, [("A", "2"), ("B", "Weather the dice?"), ("C", "Warg packs, barghests"), ("D", "Misty Mountain passes"), ("E", "Feed fragile members; respect Keeper 5-rescue cap")]),
    (19, [("A", "3"), ("B", "Have the region antidote?"), ("C", "Huorns, midge-marsh, cold passes"), ("D", "Old Forest, Midgewater, Caradhras"), ("E", "One hazard food (Alert / Hale / Warmth)")]),
    (20, [("A", "4"), ("B", "Endure when you cannot win?"), ("C", "Old Man Willow, blizzard"), ("D", "Old Forest, high passes"), ("E", "All-sustain; survival mindset")]),
    (21, [("A", "5"), ("B", "Fight while afraid?"), ("C", "Barrow-wights, the Dead"), ("D", "Barrow-downs, Dead Marshes"), ("E", "Hope food + Captain Will")]),
    (22, [("A", "6"), ("B", "Fight while the light fails?"), ("C", "Great spiders, Balrog, the Nine"), ("D", "Mirkwood, Moria, Morgul-vale"), ("E", "Radiance")]),
    (23, [("A", "7"), ("B", "All at once."), ("C", "Sieges, combined raids"), ("D", "Fornost, the Black Gate"), ("E", "Multi-stage prep, the full toolkit")]),

    (25, [("A", "ENCOUNTER NOTES")]),
    (26, [("A", "encounter"), ("B", "recLevel"), ("C", "rung"), ("D", "food ladder (win rates by recipe level)"), ("E", "phys mit"), ("F", "hazards"), ("G", "tuning notes and validation status")]),

    (27, [
        ("A", "Neekerbreekers at the Marsh's Edge"),
        ("B", "1"),
        ("C", "0"),
        ("D", "Lv1=25% / Lv2=66% / Lv3=98% / Lv4=100%  -- confirmed 5000 runs"),
        ("E", "None -- no armor in Rung 0"),
        ("F", "None -- pure drain+spike sustain test"),
        ("G", "drain=12 spike=75 iv=13 (mean, +/-50% jitter) resolve=50000 duration=1500. Drain-dominant tutorial fight. Unwinnable with no food (wipe ~1min). Squeaky with Lv1-2. Clean from Lv3. The lesson is: go cook. VALIDATED."),
    ]),
    (28, [
        ("A", "Wolves in the Chetwood"),
        ("B", "3"),
        ("C", "0"),
        ("D", "TBD -- tune after Neekers ladder is locked. Target: Lv4 (Neekers ceiling) = squeaky entry, Lv6-7 = comfortable."),
        ("E", "None"),
        ("F", "None -- second pure sustain test, raises bar from Neekers"),
        ("G", "Adds second question: sustain AND whether Warden can hold the line for the Keeper. Food bar should roughly double vs Neekers. Numbers not yet validated -- run sim sweep before locking."),
    ]),
    (29, [("A", "(add rows here as encounters are tuned and validated)")]),

    (31, [("A", "HP/s TIER TABLE  (convex curve, power=1.8 within each tier)")]),
    (32, [("A", "Tier"), ("B", "Title"), ("C", "Cook Lv"), ("D", "HP/s range"), ("E", "Per-level values")]),
    (33, [("A", "1"), ("B", "Hearthkeeper"), ("C", "1-4"),   ("D", "5 - 10"),   ("E", "Lv1=5.0  Lv2=5.7  Lv3=7.4  Lv4=10.0")]),
    (34, [("A", "2"), ("B", "Initiate"),     ("C", "5-9"),   ("D", "10 - 18"),  ("E", "Lv5=10.0  Lv6=10.7  Lv7=12.3  Lv8=14.8  Lv9=18.0")]),
    (35, [("A", "3"), ("B", "Apprentice"),   ("C", "10-15"), ("D", "18 - 30"),  ("E", "Lv10=18.0  Lv11=22.2  Lv12=24.6  Lv13=26.6  Lv14=28.4  Lv15=30.0")]),
    (36, [("A", "4"), ("B", "Journeyman"),   ("C", "16-22"), ("D", "30 - 44"),  ("E", "Lv16=30.0  Lv17=34.4  Lv18=36.9  Lv19=38.9  Lv20=40.8  Lv21=42.4  Lv22=44.0")]),
    (37, [("A", "5"), ("B", "Adept"),        ("C", "23-30"), ("D", "44 - 62"),  ("E", "Lv23=44.0  Lv24=49.1  Lv25=52.0  Lv26=54.4  Lv27=56.5  Lv28=58.5  Lv29=60.3  Lv30=62.0")]),
    (38, [("A", "6"), ("B", "Master"),       ("C", "31-40"), ("D", "62 - 86"),  ("E", "Lv31=62.0  Lv32=67.8  Lv33=71.0  Lv34=73.8  Lv35=76.2  Lv36=78.4  Lv37=80.4  Lv38=82.4  Lv39=84.2  Lv40=86.0")]),
    (39, [("A", "7"), ("B", "Grandmaster"),  ("C", "41-50"), ("D", "86 - 110"), ("E", "Lv41=86.0  Lv42=91.8  Lv43=95.0  Lv44=97.8  Lv45=100.2  Lv46=102.4  Lv47=104.4  Lv48=106.4  Lv49=108.2  Lv50=110.0")]),

    (41, [("A", "AFTER-ACTION REPORT DESIGN")]),
    (42, [("A", "The after-action report does the emotional work that the binary win/loss outcome cannot. A player who loses with the enemy at 8% resolve should see that number -- it turns an opaque failure into a near-miss story.")]),
    (43, [("A", "Well-provisioned: low variance, consistent wins, clean aftermath. Preparation is rewarded reliably.")]),
    (44, [("A", "Squeaky zone: high variance, sometimes win, sometimes lose. Aftermath always explains why -- enemy resolve remaining, wounds, rescues spent, Inspirations that fired.")]),
    (45, [("A", "Underprepared: consistent fast wipe. The lesson is clear and fast: go level up your cooking.")]),
    (46, [("A", "Key stats to surface: enemy resolve remaining at defeat (%), fight duration, wounds per member, rescues used / Warden guards spent, Inspirations fired, one-line verdict.")]),
]


def build_sheet_xml(rows):
    parts = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">',
        '<sheetData>',
    ]
    for row_num, cells in rows:
        parts.append(f'<row r="{row_num}">')
        for col, val in cells:
            ref = f"{col}{row_num}"
            escaped = (val
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            )
            parts.append(f'<c r="{ref}" t="inlineStr"><is><t>{escaped}</t></is></c>')
        parts.append("</row>")
    parts += ["</sheetData>", "</worksheet>"]
    return "\n".join(parts)


sheet_xml = build_sheet_xml(ROWS)

# read existing xlsx
shutil.copy(SRC, TMP)
with zipfile.ZipFile(TMP, "r") as zin:
    files = {n: zin.read(n) for n in zin.namelist()}

# check if sheet19 already exists and pick next available id
existing_sheets = [k for k in files if k.startswith("xl/worksheets/sheet") and k.endswith(".xml")]
sheet_nums = [int(re.search(r"sheet(\d+)", s).group(1)) for s in existing_sheets]
next_num = max(sheet_nums) + 1
next_rid = f"rId{next_num + 4}"  # rId offset: styles=rId19, theme=rId20, existing sheets start rId1

# find true next rId
wb_text = files["xl/workbook.xml"].decode("utf-8")
rels_text = files["xl/_rels/workbook.xml.rels"].decode("utf-8")
existing_rids = [int(m) for m in re.findall(r'Id="rId(\d+)"', rels_text)]
next_rid_num = max(existing_rids) + 1
next_rid = f"rId{next_rid_num}"
sheet_file = f"xl/worksheets/sheet{next_num}.xml"

# inject sheet
files[sheet_file] = sheet_xml.encode("utf-8")

# patch workbook.xml
new_sheet_tag = f'<sheet name="Design Notes" sheetId="{next_num}" r:id="{next_rid}"/>'
wb_text = wb_text.replace("</sheets>", new_sheet_tag + "</sheets>")
files["xl/workbook.xml"] = wb_text.encode("utf-8")

# patch rels
new_rel = (f'<Relationship Id="{next_rid}" '
           f'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" '
           f'Target="worksheets/sheet{next_num}.xml"/>')
rels_text = rels_text.replace("</Relationships>", new_rel + "</Relationships>")
files["xl/_rels/workbook.xml.rels"] = rels_text.encode("utf-8")

# patch content types
ct_text = files["[Content_Types].xml"].decode("utf-8")
new_ct = (f'<Override PartName="/xl/worksheets/sheet{next_num}.xml" '
          f'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>')
ct_text = ct_text.replace("</Types>", new_ct + "</Types>")
files["[Content_Types].xml"] = ct_text.encode("utf-8")

# write output
with zipfile.ZipFile(SRC, "w", zipfile.ZIP_DEFLATED) as zout:
    for name, data in files.items():
        zout.writestr(name, data)

os.remove(TMP)

# verify
print(f"Done. New sheet: {sheet_file} ({next_rid})")
with zipfile.ZipFile(SRC) as z:
    wb_check = z.read("xl/workbook.xml").decode("utf-8")
    sheet_names = re.findall(r'name="([^"]+)"', wb_check)
    print("All sheets:", sheet_names)
