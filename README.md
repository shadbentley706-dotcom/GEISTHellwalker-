# GEIST: Hellblazer (Forge 1.20.1) - by Grapefruit Company
The occult side of GEIST: the section that takes the cases the rest of the agency won't touch.

Version 1.0 adds three things: the **casebook** (a missions system with its own red, cracked-screen UI), the **outfit**
(a tan trench coat with the shirt and tie built in, and black trousers), and the **sigil** tab icon. All the missions use
vanilla creatures, items and places. The mod adds no new mobs, blocks or tools.

It runs on its own or alongside GEIST: it has its own mod id (`geist_hellblazer`), package, network channel and keys.

## Setting up the repo
Hellblazer is a separate mod, so it needs its own repo. Make a copy of your GEIST repo, so you keep the same Gradle
files and the same GitHub Actions workflow. Then:
1. Replace `src` with the `src` folder from this zip. Keep `tools` and this README if you like.
2. In `gradle.properties`, set:
   - `mod_id=geist_hellblazer`
   - `mod_name=GEIST: Hellblazer`
   - `mod_version=1.0`
   - `mod_group_id=com.grapefruit.hellblazer`
   - `mod_authors=Grapefruit Company`
3. Commit, push and download the jar from the Actions run, the same way you do for GEIST.

`mods.toml` is written out in full, so those properties only change the jar's file name and the dev-run setup.

## The casebook (key K)
Press **K** to open the casebook. It's a screen of dark red glass with a crack through it. Opening it plays a short
start-up:
1. A bright line opens into a picture.
2. Something strikes the glass and the crack runs out from where it hit.
3. The sigil burns up out of the dark.
4. "GEIST // OCCULT SECTION" bleeds in, with your name and rank under it.

The crack is the same every time you open the book, because it's your casebook. Reopening it within 30 seconds runs a
quick version of the start-up. Click or press any key to skip it.

Across the top you'll see the moon phase and the in-world time (red at night), plus your rank and standing.

**Controls:** **1-2** or **TAB** switch pages, the mouse wheel scrolls lists, and **K** or **Esc** closes the book.
Closing squeezes the picture to a line and puts it out. The game doesn't pause while the book is open. You can rebind
K under Controls -> GEIST: Hellblazer. (It isn't on J because GEIST uses J for the jet.)

### CASES
**Left side: the board and your active files.**
- **On the board:** three files on offer, or four from EXORCIST. The board refreshes every ten minutes.
- **NEW LEADS** replaces the board straight away for 3 levels of experience.
- **Being worked:** the files you've taken. You can work two at a time, or three from HEXBREAKER.
- Each file shows how far along it is, its clock, and whether the HUD is following it.

**Right side: the file itself.**
- **Photo:** the creature itself, turning to follow your mouse, or the item turning in 3D. A named figure gets a red
  ring round its face and its name under the photo.
- **What's asked**, a danger rating (1-5), the time limit (or time left), and the pay.
- For a named figure or a haunting: the marked place's coordinates, with distance and compass direction.
- For a relic: how many you're carrying.
- **The section's notes:** a short briefing that types itself in. There are three versions for each kind of file.
- **Buttons:**
  - **TAKE THE FILE**
  - **FOLLOW** pins the file to the HUD.
  - **SEND IN** (relics only)
  - **WALK AWAY** asks you to confirm first.

When you take a file, a TAKEN stamp comes down on it. When you close one, a CLOSED stamp comes down.

### RECORD
- **Standing:** your rank, your standing figure (it rolls up as it changes), a bar to the next rank, and your tally:
  files taken, closed, lost to the clock, walked away from, named figures put down, and hostile mobs killed.
- **The ladder:** all seven ranks and what each one opens. Your rank is lit and the next one is marked in gold.
- **Field notes:** what each kind of file asks of you and how to do it.

## The five kinds of file
| Kind | What you do |
|---|---|
| **HUNT** | Kill N of one mob, anywhere. The mob pool depends on the dimension you're in: zombies, drowned, witches, phantoms, strays, pillagers, endermen, magma cubes, blazes, wither skeletons, ghasts, evokers, shulkers and more. |
| **NAMED** | A named figure (for example "Agnes Hollis, the Widow") waits at a marked place 140-240 blocks away, with 2-4 followers. It has three times the health, Resistance I, and a glow, and it drops no gear. It appears when you get within 80 blocks, and a bell rings when it does. Starts at CONSULTANT. |
| **HAUNTING** | A marked place 80-160 blocks away, ringed with soul fire. Go after dark (or anywhere unlit, or in the Nether) and stand inside the ring. Risen mobs come up out of the ground around you every 12 seconds until you've killed enough of them. Undead risen wear carved pumpkins so they don't burn. |
| **RELIC** | Collect N of something cursed (bones, ender pearls, crying obsidian, ghast tears, wither skeleton skulls, echo shards, a totem, dragon's breath...), then press SEND IN with them in your inventory. Relic files have no time limit. |
| **INVESTIGATION** | Set foot in a biome or structure, such as a swamp, the dark forest, the deep dark, a soul sand valley, a witch hut, a ruined portal, a stronghold, a nether fortress, a bastion or an ancient city. The file closes the moment you're inside. |

- Named figures and hauntings are placed on solid ground, including under the Nether's roof. They can't be taken in
  the End, and the End board doesn't offer them.
- Hunts, named figures, hauntings and investigations each run on a clock of one to two in-game days. If the clock runs
  out, the file is lost.

**Pay:** standing with the section (which raises your rank), experience points and emeralds. Each rank adds about
15% to the pay.

## Ranks
| Rank | Standing | Opens |
|---|---|---|
| NOBODY | 0 | Hunts, hauntings, relics and investigations. Three files on the board, two worked at a time. |
| CONSULTANT | 150 | Named figures. Strays, husks and pillagers; pyramids, outposts and the trail ruins. |
| INVESTIGATOR | 500 | Endermen, wither skeletons and blazes. Strongholds and nether fortresses. |
| EXORCIST | 1,200 | A fourth file on the board. Ghasts, wither skulls, bastions and witch covens. |
| HEXBREAKER | 2,400 | A third file worked at once. Evokers and vexes; the deep dark, mansions and ancient cities; an extra follower around each named figure. |
| DEMONOLOGIST | 4,000 | Dragon's breath relics. |
| HELLBLAZER | 6,500 | The top of the ladder. |

## On the HUD
- **Tracker:** the file you're following sits on the right, on a dark red card with a crack across its corner. It shows
  the file's name, what's asked, progress and the clock (which flashes in its last 30 seconds). For marked places it
  also shows an arrow and the distance.
- **Haunting status:** when you get near a haunting, the tracker tells you to come back after dark, to walk into the
  ring, or that the dead are rising.
- **Named figure:** when the figure is up, the tracker says it's here.
- **Banners:** FILE TAKEN, CASE CLOSED (with the pay), FILE LOST (with a crack running through it) and NOT NOW (when
  something is refused).
- **Promotion:** the sigil burns up in the middle of the screen, the glass cracks, and PROMOTED appears with your new
  rank and what it opens.

## The outfit
- **Tan Trench Coat** (chest slot):
  - A long tan coat with a popped collar, worn open.
  - The shirt and tie are part of the coat: a white shirt unbuttoned at the throat (your skin shows in the open
    collar), a loose red tie and a glimpse of black belt.
  - The skirts hang open down the front, so the black trousers show between them, and are cut away at the back with a
    vent.
  - It comes with both sleeve cuts, so it fits wide and slim player skins.
- **Black Trousers** (legs slot): black wool with a crease, pockets and turn-ups.
- **In the inventory:** both pieces appear as themselves in 3D.
- **Stats:** cloth armour, 4 and 3 armour points, 0.5 toughness. Repair with leather.

Recipes (crafting table):

    Trench coat:  Leather | (empty)    | Leather
                  Leather | White Wool | Leather
                  Leather | Red Dye    | Leather

    Trousers:     Black Wool | Black Wool | Black Wool
                  Black Wool | (empty)    | Black Wool
                  Black Wool | (empty)    | Black Wool

## The tab icon
The creative tab uses the **sigil**: a cracked red circle with a dripping dagger-cross. The same art appears in the
casebook (in the start-up, the top bar and the promotion).

## Tools (Python, in /tools)
- `gen_outfit.py` builds the coat and trousers models (`client/outfit/OutfitModels.java`) and their textures.
  `python3 tools/gen_outfit.py [preview_dir]` also renders previews.
- `gen_lang.py` writes `en_us.json`.
- `gen_sounds.py` synthesizes the casebook's 11 sounds (numpy, scipy and ffmpeg) and writes `sounds.json`.
- `gen_icons.py` makes the flat item textures, which are used for the break particles.
- `modelgen.py` and `sndlib.py` are the shared building blocks.

Run them from the project root.
