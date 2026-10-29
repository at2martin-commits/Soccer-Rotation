# Soccer Rotation: guide for Claude

A phone-first web app that builds fair playing-time lineups for youth soccer (AYSO). The owner
is **Steven Martin** (GitHub `at2martin-commits`). He coaches his kids' teams, uses an Android
phone and usually talks to Claude from the Claude app on his phone. He tests on that phone and
asks for changes in plain words.

## Links

- Regular version (other coaches use this): https://at2martin-commits.github.io/Soccer-Rotation/
- Beta: https://at2martin-commits.github.io/Soccer-Rotation/beta/
- Steven's private copy on claude.ai (an Artifact, follows `main`): https://claude.ai/artifact/4tUxR4umKELtai3MAUqUsP
- Repo: https://github.com/at2martin-commits/Soccer-Rotation (public)

## How we work: beta first, main when Steven says so

1. **All changes go on the `beta` branch.** Start with `git fetch origin && git checkout beta && git pull origin beta`.
   Never commit app changes to `main` directly.
2. After a change: run `node tests/run.js` (all checks must pass; add checks for new features),
   commit, `git push origin beta`. That push runs `.github/workflows/beta.yml`, which asks
   `pages.yml` (on main) to rebuild; the beta link updates in about 2 minutes. Confirm with
   `gh run list -R at2martin-commits/Soccer-Rotation -w pages.yml -L 1` (wait for `completed success`).
   Then tell Steven what changed and to reload the beta link.
3. **Only when Steven says to merge** (e.g. "merge beta to main", "push it to main", "I'm happy with the beta"):
   - Bump `APP_VERSION` in `web/index.html` (1.1 → 1.2 for features, 1.1.1 for small fixes) and the
     "Version" line at the top of `README.md`; make sure the README feature list matches the app.
   - `git checkout main && git merge --ff-only beta && git push origin main`, then
     `git checkout beta && git merge main && git push origin beta` so both match.
   - Wait for the Pages deploy (command above).
   - Update his claude.ai copy: Artifact tool `action: "read"` on the URL above, then publish
     `web/index.html` with that `url` (keep the stored capabilities: don't pass `capabilities`).
   - Tell him it's live and remind him he can make a GitHub release (see limits below).
4. Commit messages end with the attribution lines the session's system reminder gives.
5. Explain results to Steven plainly and briefly: what changed, how to use it, anything to try
   on his phone. He prefers being shown how things work in practice over technical detail.

## What a cloud session can't do

- Push tags, create/edit/delete GitHub releases, or change repo settings (all refused, HTTP 403).
  Give Steven the steps instead (Releases → Draft a new release; he named the 1.0 tag `V1.0`).
- Open `*.github.io` or `dl.google.com` (network policy), so the live site can't be fetched; verify
  through the deploy run's steps and the local test suite, and ask Steven to check on his phone.
- GitHub GraphQL is blocked; `gh` REST calls (`gh api repos/...`, `gh run list`) work.
- `git push` prints "This repository moved..." (remote is the lowercase name); it's harmless.

## Layout

- `web/index.html`: the whole app (HTML, CSS, one inline `<script>`; no libraries, no build step).
  Written **without** `<!doctype>`/`<head>` because the claude.ai viewer adds its own.
- `tools/build-site.py`: builds the GitHub Pages copy: adds a doctype and a Content Security Policy
  (script pinned by sha256 hash, only Google Fonts allowed, no network). It also makes the copy
  installable: `manifest.webmanifest`, icons from `web/icons/` (192 and 512 px), and `sw.js`, a
  network-first offline helper (regular and beta install as separate apps). In the app, the
  "Put Soccer Rotation on your home screen" card (`installCard`, `addToHomeScreen`) shows only when a
  manifest is linked and the app isn't already open from its icon; it uses Chrome's install prompt or
  shows step-by-step help. "Not now" hides the card on that device; the team menu keeps the button. `--beta` adds the BETA tag,
  "beta" version label and a separate storage key (`soccer-rotation-beta`). The app checks that key
  (`IS_BETA`) to show beta-only tools: the "fill with sample players and an 8–10 game schedule" switch
  on New team (`fillSampleTeam`).
- `.github/workflows/pages.yml`: deploys `main` at `/` and `beta` at `/beta/`. `beta.yml`: on beta
  pushes, dispatches `pages.yml` on main (Pages only deploys from main).
- `tests/run.js`: the browser test suite (`node tests/run.js`, about 4 minutes, preinstalled
  Playwright; run it in the background or with a long timeout). `node tests/run.js fair` (or
  `planner`, `screens`, `iphone`, `scorekeeper`, `safety`) runs one part. "Fair play" stress-tests every size,
  formation and style with up to 8 extra kids and random favorites, plus 8-game seasons.
- `THIRD_PARTY_NOTICES.md`: Material Icons (Apache 2.0, full text included), fonts (SIL OFL 1.1).
  If you add an icon, take it from Material Icons and add its name to the list there.

## Inside web/index.html (sections, in order)

1. **Soccer data**: `POS` (positions with line), `FORMATIONS` (label + `hint` + `fill` order; first
   entries are filled first when kids are missing). Teams pick only a size (`TEAM_SIZES`: 4v4 no goalie,
   6v6, 7v7, 9v9, 11v11; the first formation of each size is its default; 5v5 stays only for old
   saved teams); the formation is asked per game on "Make lineup"
   (`formationDialog`), and `t.formation` remembers the last one used. Each period stores its own
   `formation` and can be switched from the pitch card (`changeFormation`: same kids on/off/in goal,
   others move to the nearest open spot). `FORMATS` (quarters / halves; "halves with mid-half swap" is kept only for teams already using it),
   `STYLES` (`rotate`, `stick`; `defaultStyle` = stick for 9v9 and 11v11, rotate otherwise).
2. **Lineup planner**: `generate(players, formation, periods, season, style)` makes `ATTEMPTS` (400)
   random candidates with `candidate()` and keeps the lowest `score()`. Rules, roughly by weight:
   equal playing time (hard), no-goalie kids never in goal, no sitting twice in a row, goalie turns
   spread (game and season), sitting balanced across the season, then positions:
   - *rotate*: preferred spot is a fading bonus (≈2 of 3 periods), same-line spots a small bonus,
     repeats and one-line games cost, every kid with 2+ field periods gets a turn at forward and
     defense first. Kids who like goal get goal once per game first.
   - *stick*: preferred spot every period on the field; others keep their last spot/line; kids who
     like goal share goal evenly.
   `summarize`, `record` (season totals), `swap`, `playerAt`, `changeFormation`. Slots are `"F:LD"` or `"B:0"`.
3. **Share text / game summary**: `lineupText` (pre-game text share), `reportData` → `reportHTML`
   (preview) and `reportCanvas` (the PNG). The summary always lists: Team (Home/Away), Opponent,
   Score, Date, Game, then the playing-time table and absences.
4. **Schedule import**, **hidden** (`SHOW_IMPORT = false`; Steven: "doesn't work like I want", may
   return; the code and tests stay, tests open it with `importDialog(team())`) (`importDialog` → `readCalendar`; `parseCalendar`, `calDate`, `calendarGames`,
   `importPlan`): reads a `.ics` file the coach downloads (Blue Sombrero / Stack Sports team calendar
   Export/Sync gives a `webcal://` link; the app turns it into an https download link, since it never
   fetches anything itself). Titles "Home Vs Away" (Blue Sombrero) or "Away @ Home"; practices and
   other non-games skipped; your team = the name in the most games (or the team's own name). Games
   matching an existing one (same day, same or no opponent) are updated, not doubled.
5. **QR codes** (`qrMatrix`, `qrSVG`): a small built-in QR maker (byte mode, level M or L),
   checked against jsQR when written; tests compare fingerprints (`QR_PRINTS` in tests/run.js).
   If you change the maker, re-verify with a real decoder and update the fingerprints.
6. **Parent scorekeeper** (its own section near the end, before Actions; `scorekeeperLink`/`readScorekeeper`, `scoreResultLink`/`readScoreResult`,
   `readHash`, `scorekeeperView`, `scanDialog`, `applyScoreDialog`): the coach's QR is a link
   `…#sk=<lineup>`; opening it shows the parent page (`sk` set, tabs hidden, taps kept under
   `soccer-rotation-scorekeeper`, never touching the parent's own teams). "Game over" shows
   `…#skr=<score>`; the coach scans it (BarcodeDetector + camera, Chrome on Android), opens it
   with the phone's camera app, or pastes it. Applying replaces the game's goals and marks it played.
   Codes are untrusted: every field is checked like a backup.
7. **Saving**: `normalize()` cleans saved data and restored backups **field by field** (numbers,
   real dates, known positions, plain text). `commit()` saves to localStorage and, on claude.ai,
   to the viewer's private `db` doc `data/users/<id>/state` (`initCloud`).
8. **Rendering** (`render`, `playersView`, `gamesView`, `gameView`, `periodsHTML`, `goalsHTML`,
   `scoreboardHTML`, `playingTimeHTML`, `seasonView`, `archiveView`, `archivedGameView`) and
   **dialogs** (`teamDialog`, `gameDialog`, `playerDialog`, `shareResultsDialog`, `aboutDialog`,
   `backupDialog`, `archiveDialog`, `confirmDialog`). Clicks go through `data-action` (main) and
   `data-d` (dialogs) handlers.

Saved shape: `{version: 3, nextId, currentTeamId, savedAt, teams: [{id, name, formation, format,
style?, players: [{id, name, number: "" or up to 3 digits, goalie, preferred: [POS]}], absent: [id], games: [{id, date, opponent,
home: ""|"home"|"away", time: ""|"HH:MM", place, plan: [{positions: {POS: id}, bench: [id], formation?}], formation, format, played,
names: {id: name}, goals: [{id, p}], oppGoals: [{p}], absent?}], seasonCarry: {id: totals},
archives: [{id, name, archivedAt, games, carry, names}]}]}`.

## Rules for changes

- **Check saved values against the app's tables with `known(TABLE, value)`**, never `TABLE[value]`
  (that lets "constructor" or "__proto__" through). Ids go through `cleanId` (whole numbers under
  1e12) and lists through `uniqueIds`; `cleanPlan` keeps each kid once per period.
- **Any new saved field must be added to `normalize()` / `cleanGame()` with a type check**, and
  every place that builds a game object (search `goals: []`) must include new arrays, or older
  data and restored backups break.
- **Escape every piece of user text** with `esc()` before it goes into HTML, attributes included.
- Keep **one inline `<script>`**, no external scripts, no `fetch`/network calls, no inline event
  handlers (`onclick=`): the CSP would block them. Inline `style=` attributes are fine.
- Design: colors are CSS tokens on `:root` with dark-mode overrides; fonts Barlow Condensed
  (display) and Source Sans 3 (body); phone width 390 px, no sideways scrolling, 44 px tap targets.
- The pitch draws forwards at the top, goalie at the bottom. Formation labels spell out lines
  ("9v9: 3 defense, 3 mid, 2 forward"); Steven found "3-3-2" confusing.

## Decisions Steven has made (don't undo without asking)

- The native Android app was retired; the web app is the only version.
- Results are shared as a picture through the phone's share menu ("Share results") or downloaded;
  email-text/table features and the league-director address were removed on purpose.
- Preferred positions are a strong lean in *rotate*, a lock in *stick*; 9v9 defaults to stick.
- Sample team names mix boys and girls: Ava, Ben, Chloe, Diego, Emma, Finn, Grace, Hiro.
- About screen (ⓘ, top right): "Created by Steven Martin, with Claude, an AI model made by
  Anthropic", © 2026 Steven Martin, open-source credits.
- No license has been chosen for the app itself (all rights reserved). Ask before adding one.

## Ideas offered but not built

- Moving the Goalie chip out of preferred positions into its own "Likes playing goalie" switch.
- An app license (e.g. MIT) if he wants other clubs to reuse it.
