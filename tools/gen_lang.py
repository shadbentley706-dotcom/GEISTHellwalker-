"""Writes assets/geist_hellblazer/lang/en_us.json (run from the project root)."""
import json, os, collections

L = collections.OrderedDict()
def put(k, v): L[k] = v

M = "geist_hellblazer"
put(f"itemGroup.{M}", "GEIST: Hellblazer")
put(f"item.{M}.sigil", "Sigil")
put(f"item.{M}.trench_coat", "Tan Trench Coat")
put(f"item.{M}.black_trousers", "Black Trousers")
put(f"tooltip.{M}.trench_coat", "Shirt open at the collar, the tie pulled loose. Smells of rain and cigarettes.")
put(f"tooltip.{M}.black_trousers", "Black wool. Has seen worse nights than this one.")
put(f"key.categories.{M}", "GEIST: Hellblazer")
put(f"key.{M}.casebook", "Casebook")
put(f"message.{M}.welcome", "The occult section has opened a casebook in your name. Press %s to read it.")

# ---- kinds of file
types = {"hunt": "Hunt", "target": "Named", "haunting": "Haunting", "relic": "Relic", "investigate": "Investigation"}
for k, v in types.items(): put(f"case.{M}.type.{k}", v)
put(f"case.{M}.target.haunting", "a restless place")
obj = {
    "hunt": "%2$s: put down %1$s",
    "target": "Find %3$s (%2$s) and put it down",
    "haunting": "Lay %1$s of the risen to rest at the marked place, after dark",
    "relic": "%2$s: recover %1$s and send it in",
    "investigate": "Set foot in: %2$s",
}
for k, v in obj.items(): put(f"case.{M}.objective.{k}", v)
# %1$s count, %2$s what, %3$s the specialist, %4$s the named one
brief = {
    "hunt": [
        "%3$s. The %2$s kind have been turning up where they've no business being - by the churchyard wall, at the well, in the lane after the lamps go out. We want %1$s of them put down. Don't ask what they were before. It doesn't help.",
        "Something is calling them up out of the dark - %2$s, by the look of it - and they're answering. Thin the choir: %1$s of them. When it's done the calling will stop for a while. It always does, for a while.",
        "Folk have stopped walking home at night. That's how we know. %1$s of them, %3$s - %2$s, the lot - and if one of them says your name, keep swinging.",
    ],
    "target": [
        "This one has a name: %4$s. It goes about in the shape of the %2$s, keeping company it shouldn't, out past the edge of the maps. It won't be alone, and it'll be harder to kill than it looks - they always are, once they've been named. Put it down, %3$s.",
        "%4$s was seen at the marked place three nights running, with others stood round it like a congregation. Our people won't go near. You will. It's wearing a borrowed shape (%2$s) - don't trust the shape.",
        "We've a file on %4$s going back further than any of us. Every time it's put down it comes back with a new face. This time it wears the %2$s. Make it count, %3$s - and don't take anything it offers you.",
    ],
    "haunting": [
        "There's a place where the dead won't stay down. Go there after dark and stand in it. They'll come up out of the ground around you. Put %1$s of them back where they belong and the place will rest.",
        "The ground's gone wrong at the marked place - soul-fire in the grass, a chill that follows you about. In daylight it's only a field. After sundown it's a door. Close it: %1$s of the risen, %3$s.",
        "Old burial ground, no stones left to say so. Something's woken it. Wait for the dark, walk into the ring, and keep putting them back until they stop coming. %1$s should do it.",
    ],
    "relic": [
        "We need %1$s of these off the street: %2$s. In the wrong hands it's more than it looks. Bring it in - send it from the casebook once you've got it on you.",
        "Someone has been buying up %2$s - %1$s, by our count - and we'd rather it ended up in our vault than in whatever circle is being chalked out. Find it. Send it in.",
        "Don't ask what %2$s is for. The answer will keep you up. Just gather %1$s, keep it wrapped, and send it in through the casebook, %3$s.",
    ],
    "investigate": [
        "Our maps have a blank where %2$s should be. Everyone we send comes back with nothing to say, or doesn't come back. Go and stand in it. That's all. Being there is the job.",
        "Something is listening from %2$s. We want someone to walk in and be heard walking in. You'll know you're there when the casebook does.",
        "Old reports put a door somewhere in %2$s. We don't need you to open it, %3$s. We need to know it's still shut. Set foot there and the file closes.",
    ],
}
for k, vs in brief.items():
    for i, v in enumerate(vs): put(f"case.{M}.brief.{k}.{i}", v)
structures = {
    "swamp_hut": "a witch's hut in the swamp", "ruined_portal": "a ruined portal", "mineshaft": "an abandoned mineshaft",
    "desert_pyramid": "a desert pyramid", "jungle_pyramid": "a jungle temple", "pillager_outpost": "a pillager outpost",
    "trail_ruins": "the trail ruins", "stronghold": "a stronghold", "mansion": "a woodland mansion", "ancient_city": "an ancient city",
    "fortress": "a nether fortress", "bastion_remnant": "a bastion remnant",
}
for k, v in structures.items(): put(f"structure.{M}.{k}", v)
put(f"case.{M}.deny.full", "You're working as many files as you can carry. Close one, or walk away from one.")
put(f"case.{M}.deny.items", "Not enough on you: %1$s of %2$s.")
put(f"case.{M}.deny.levels", "New leads cost %1$s levels of experience.")
put(f"case.{M}.deny.end", "Not out here. Take that one where there's ground to stand on.")

# ---- the HUD
put(f"case.{M}.hud.wait_dark", "Come back after dark")
put(f"case.{M}.hud.go_in", "Walk into the ring")
put(f"case.{M}.hud.rising", "They're rising - stand your ground")
put(f"case.{M}.hud.target_here", "%s is here")
put(f"case.{M}.banner.accepted", "FILE TAKEN")
put(f"case.{M}.banner.closed", "CASE CLOSED")
put(f"case.{M}.banner.paid", "%1$s  ·  +%2$s standing  ·  %3$s emeralds")
put(f"case.{M}.banner.failed", "FILE LOST")
put(f"case.{M}.banner.denied", "NOT NOW")
put(f"case.{M}.promo.title", "PROMOTED")
ranks = {
    "nobody": "Hunts, hauntings, relics and places no one should go. Three files on the board, two worked at a time.",
    "consultant": "Named figures start turning up. Strays, husks and pillagers; pyramids, outposts and old ruins.",
    "investigator": "Endermen, wither skeletons and blazes. Strongholds and nether fortresses.",
    "exorcist": "A fourth file on the board. Ghasts, wither skulls, bastions and witch covens.",
    "hexbreaker": "A third file worked at once. Evokers and vexes; the deep dark, mansions and ancient cities.",
    "demonologist": "Dragon's breath. Every file pays better than it did.",
    "hellblazer": "The top of the ladder. The section has nothing left to teach you.",
}
for k, v in ranks.items(): put(f"case.{M}.rank.{k}.opens", v)

# ---- the casebook
C = f"casebook.{M}"
put(f"{C}.title", "Casebook")
put(f"{C}.boot.head", "GEIST // OCCULT SECTION")
put(f"{C}.head", "GEIST // OCCULT SECTION")
put(f"{C}.sub", "CASEBOOK")
for i, v in enumerate(["FULL MOON", "WANING GIBBOUS", "LAST QUARTER", "WANING CRESCENT", "NEW MOON", "WAXING CRESCENT", "FIRST QUARTER", "WAXING GIBBOUS"]):
    put(f"{C}.moon.{i}", v)
put(f"{C}.tab.cases", "CASES")
put(f"{C}.tab.record", "RECORD")
put(f"{C}.standing", "STANDING %s")
put(f"{C}.footer", "[%s] CLOSE    [1-2 / TAB] PAGES    [WHEEL] SCROLL")
put(f"{C}.footer_right", "WHAT'S DEAD SHOULD STAY DEAD")
put(f"{C}.flash.closed", "Closed: %1$s  (+%2$s standing, %3$s emeralds)")
put(f"{C}.flash.failed", "Lost: %1$s - the clock ran out")
put(f"{C}.stamp.taken", "TAKEN")
put(f"{C}.stamp.closed", "CLOSED")
cases = {
    "offers": "ON THE BOARD", "refresh": "fresh files in %s", "reroll": "NEW LEADS (%s LVL)", "loading": "The files are still coming in...",
    "active": "BEING WORKED  %1$s/%2$s", "none_active": "Nothing being worked. Pick a file from the board and take it.",
    "tracked": "FOLLOWING", "no_limit_short": "NO CLOCK", "empty": "No files.", "file": "CASE FILE", "open": "OPEN", "on_offer": "ON THE BOARD",
    "subject": "SUBJECT", "objective": "WHAT'S ASKED", "threat": "DANGER", "time_left": "TIME LEFT", "time_limit": "TIME, ONCE TAKEN",
    "no_limit": "No clock on this one", "pays": "PAYS", "pays_v": "+%1$s standing   +%2$s XP   %3$s emeralds", "last_seen": "LAST SEEN",
    "the_place": "THE PLACE", "carrying": "ON YOU", "notes": "THE SECTION'S NOTES", "accept": "TAKE THE FILE", "full": "HANDS FULL",
    "follow": "FOLLOW", "following": "FOLLOWING", "deliver": "SEND IN", "drop": "WALK AWAY", "confirm_drop": "SURE? WALK AWAY",
}
for k, v in cases.items(): put(f"{C}.cases.{k}", v)
record = {
    "standing": "STANDING", "rep": "STANDING WITH THE SECTION", "top": "Nothing above you now.", "to_next": "%1$s more to %2$s",
    "tally": "THE TALLY", "rate": "%s%% of the files you took, you closed", "ladder": "THE LADDER", "notes": "FIELD NOTES",
    "stat.accepted": "FILES TAKEN", "stat.closed": "CLOSED", "stat.failed": "LOST TO THE CLOCK", "stat.abandoned": "WALKED AWAY",
    "stat.targets": "NAMED ONES PUT DOWN", "stat.kills": "THINGS PUT DOWN",
}
for k, v in record.items(): put(f"{C}.record.{k}", v)
how = {
    "hunt": "Put down the number asked, anywhere. Only the kind named counts.",
    "target": "Go to the marked place - the casebook and the HUD point the way. The named one waits there with company, three times as hard to kill.",
    "haunting": "Go to the marked place and wait for dark. Stand inside the ring of soul-fire and the dead come up round you. Put enough of them back.",
    "relic": "Gather what's asked for, carry it, and press SEND IN on the file.",
    "investigate": "Set foot in the place named. The file closes the moment you're there.",
}
for k, v in how.items(): put(f"{C}.how.{k}", v)

# ---- subtitles
subs = {
    "case_open": "Casebook comes on", "case_close": "Casebook goes off", "case_click": "Casebook clicks", "case_hover": "Casebook ticks",
    "case_tab": "Casebook page turns", "case_type": "Casebook types", "case_stamp": "Stamp comes down", "case_deny": "Casebook refuses",
    "case_closed": "Case closed", "case_failed": "Case lost", "rank_up": "Promoted",
}
for k, v in subs.items(): put(f"subtitles.{M}.{k}", v)

out = os.path.join("src/main/resources/assets", M, "lang/en_us.json")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", encoding="utf-8") as f:
    json.dump(L, f, ensure_ascii=False, indent=2)
    f.write("\n")
print(len(L), "keys ->", out)
