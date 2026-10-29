# Soccer Rotation

A web app that builds fair playing-time lineups for youth soccer teams. Version 1.1.

**Open it on any phone:** https://at2martin-commits.github.io/Soccer-Rotation/
To keep it on your home screen: on iPhone, Share → Add to Home Screen; on Android, ⋮ → Add to Home screen.

- **Teams**: keep several teams (tap the team name at the top to switch). Each has its
  own players, formation (4v4 or 5v5 with no goalie, 6v6, 7v7, or 9v9 with 3 or 2
  defenders), game format (4 quarters, 2 halves, or halves with a mid-half swap) and
  position setting (Rotate or Stick to positions).
- **Players**: add players, check off who's here today, and tap a name to rename them,
  mark who can't play goalie, or pick preferred positions. A kid with a preferred
  position plays there more often (and nearby spots on the same line a bit more), but
  still rotates, and every kid gets a turn at forward and defense first. A team can
  instead be set to **Stick to positions** (the default for 9v9): kids play their preferred
  spot every period they're on the field, and others keep the spot they've been playing.
- **Games**: add the dates from your schedule, with an optional opponent and home or away.
  On game day, open the game and make its lineup. Past games keep their lineups to look back on.
- **Lineups**: each game's lineup covers every period at once. Play time is split as evenly as
  possible, nobody sits twice in a row, goalie turns are spread out, and kids move
  between positions. Tap two players in a period to swap them. Share sends the lineup
  as text. Double-tap a player on the field to record a goal, and tap **+ Opponent goal** on a
  period when the other team scores; the scoreboard keeps the running score. Remove a
  mistaken goal from the game's Goals list.
- **Game summary**: once a game is marked played, **Share results** sends a picture of the
  game summary (your team and the opponent with home or away, the score, the date, and each
  kid's positions, playing time and goals) through your phone's share menu, or
  **Download picture** saves it.
- **Season**: tap **Mark as played** after a game and future lineups give extra time to
  kids who've sat more, and spread goalie turns across the season. **Archive season**
  saves the totals and played games under **Past seasons** (with each game's lineup) and
  starts a fresh season.

Each coach's teams are saved in their own phone's browser. Use **Back up or move data**
in the team menu to keep a copy or move to another phone.

## Privacy and security

- Everything stays on the phone, in the browser's storage. The app has no server, no
  accounts, no analytics and no tracking; the only thing it downloads is its two fonts.
- The published page carries a Content Security Policy that lets it run only its own
  script and load only those fonts, so it can't load anything else or send data anywhere.
- Restored backups are checked field by field before use. Only restore backup text you
  made yourself or got from someone you trust, since it contains your teams' names.

## Code

The whole app is `web/index.html`: plain HTML, CSS and JavaScript, with no libraries.
Pushing to `main` publishes it to GitHub Pages via `.github/workflows/pages.yml`, which
runs `tools/build-site.py` to add the doctype and the Content Security Policy.

An earlier native Android version lives in the Git history (removed in favor of the web app).

## Credits

Created by Steven Martin, with Claude, an AI model made by Anthropic. © 2026 Steven Martin.
The app uses Material Icons (Apache License 2.0) and the Barlow Condensed and Source Sans 3
fonts (SIL Open Font License 1.1). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
