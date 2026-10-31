# Soccer Rotation

A web app that builds fair playing-time lineups for youth soccer teams. Version 1.3.

**Open it on any phone:** https://at2martin-commits.github.io/Soccer-Rotation/
Tap **Add to home screen** in the app to put it on your phone like an app; it then opens
without internet too. (On iPhone the home screen copy keeps its own data, and the app explains
how to move your teams over.)

- **Teams**: keep several teams (tap the team name at the top to switch teams or start a
  new one). The **Team** tab (bottom right) edits the team, shares it with other coaches, saves the
  schedule as a calendar file, archives the season, backs up your data and deletes the team. Each team has its own players, size (4v4 with no goalie, 6v6, 7v7, 9v9 or 11v11), game format (4 quarters
  or 2 halves) and position setting (Rotate or Stick to positions).
- **Players**: add players, check off who's here today, and tap a name to rename them,
  give them a jersey number, mark who can't play goalie, or pick preferred positions. A kid with a preferred
  position plays there more often (and nearby spots on the same line a bit more), but
  still rotates, and every kid gets a turn at forward and defense first. A team can
  instead be set to **Stick to positions** (the default for 9v9 and 11v11): kids play their preferred
  spot every period they're on the field, and others keep the spot they've been playing.
- **Games**: add the dates from your schedule, with an optional start time, opponent, field
  and home or away.
  On game day, open the game and make its lineup. Past games keep their lineups to look back on.
  **Add games to your calendar (.ics)** on the Team tab saves the schedule as a calendar file
  that Google Calendar, Samsung Calendar, Apple Calendar or Outlook can import (games with a
  start time are an hour long; games without one are all-day).
- **Lineups**: making a lineup asks which formation you're playing (each size has several,
  from attacking to defensive, spelled out like "3 defense, 3 mid, 2 forward"). Each game's
  lineup covers every period at once. Play time is split as evenly as possible, nobody sits
  twice in a row, goalie turns are spread out, and kids move between positions. Each period
  can switch formation during the game: the same kids stay on the field and on the bench,
  and only spots change. Press and hold a player, then drag them onto another spot in the
  same period to swap them. **More → Share lineup as text** sends it as text. Double-tap a player on the field
  to record a goal, and double-tap **Opponent + Goal** on a period when the other team scores;
  the scoreboard keeps the running score. Remove a mistaken goal from the game's Goals list.
- **Parent scorekeeper**: once a game has a lineup, **More → Parent scorekeeper code** puts up a QR code. A parent
  points their phone's camera at it and gets the lineup with jersey numbers; they tap a kid
  when they score (and a button when the other team scores). At the end they tap **Game over**
  and show a code back: tap **More → Scan the parent's score** and the score fills in and the game is marked
  played. The codes carry everything (nothing goes through a server); if the camera can't be
  used, the parent can send the link instead and the coach pastes it.
- **Other coaches**: keep a team the same on the head coach's and assistant coaches' phones.
  On the Team tab, **Share** shows a QR code and **Send link** sends
  the team by text or email: players, schedule, lineups, goals, season totals and past seasons.
  The other coach scans the code or opens the link and gets the team. After that, whoever
  changed something shares again and the other phone shows what's new before updating: new and
  changed games and players, deletions, and scores. Each game's details, lineup and score merge
  separately, so one coach can fix the field while the other keeps score; for each, the change
  made last wins. When a whole season is too much for one code, the code holds the games
  changed most recently and the link holds everything. The team travels inside the link,
  compressed (nothing goes through a server), so the link is long rather than short.
- **Game summary**: once a game is marked played, **Share results** sends a picture of the
  game summary (your team and the opponent with home or away, the score, the date, and each
  kid's positions, playing time and goals) through your phone's share menu, or
  **Download picture** saves it.
- **Season**: tap **Mark as played** after a game and future lineups give extra time to
  kids who've sat more, and spread goalie turns across the season. **Archive season** (Team tab)
  saves the totals and played games under **Past seasons** (with each game's lineup) and
  starts a fresh season. The Season tab also shows the team's record (wins, losses, ties,
  goals for and against).

Each coach's teams are saved in their own phone's browser. Use **Back up or move data**
on the Team tab to keep a copy or move to another phone, or share a team with another coach.

## Privacy and security

- Everything stays on the phone, in the browser's storage. The app has no server, no
  accounts, no analytics and no tracking; the only thing it downloads is its two fonts.
- The published page carries a Content Security Policy that lets it run only its own
  script and load only those fonts, so it can't load anything else or send data anywhere.
  A small offline helper (`sw.js`) keeps a copy of the page and fonts so the home screen app
  opens without internet; it only ever fetches the app's own files and the fonts.
- Restored backups, scorekeeper codes and team links from other coaches are checked field by
  field before use. Only restore backup text you
  made yourself or got from someone you trust, since it contains your teams' names.

## Code

The whole app is `web/index.html`: plain HTML, CSS and JavaScript, with no libraries.
Pushing to `main` publishes it to GitHub Pages via `.github/workflows/pages.yml`, which
runs `tools/build-site.py` to add the doctype, the Content Security Policy and the home
screen files (manifest, icons, offline helper), and also
builds the `beta` branch into `/beta/`. Pushes to `beta` trigger that rebuild through
`.github/workflows/beta.yml`.

An earlier native Android version lives in the Git history (removed in favor of the web app).

## Beta

New work happens on the `beta` branch, published at
https://at2martin-commits.github.io/Soccer-Rotation/beta/ with a **BETA** tag in the header.
The beta keeps its own saved data, so trying it never touches teams in the regular version
(use **Back up or move data** to copy teams over for testing). When a beta is ready, `beta`
is merged into `main`, which updates the regular link.

## Credits

Created by Steven Martin, with Claude, an AI model made by Anthropic. © 2026 Steven Martin.
The app uses Material Icons (Apache License 2.0) and the Barlow Condensed and Source Sans 3
fonts (SIL Open Font License 1.1). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
