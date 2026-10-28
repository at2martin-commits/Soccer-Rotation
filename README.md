# Soccer Rotation

A web app that builds fair playing-time lineups for youth soccer teams.

**Open it on any phone:** https://at2martin-commits.github.io/Soccer-Rotation/
To keep it on your home screen: on iPhone, Share → Add to Home Screen; on Android, ⋮ → Add to Home screen.

- **Teams**: keep several teams (tap the team name at the top to switch). Each has its
  own players, formation (6v6, 7v7, or 9v9 with 3 or 2 defenders) and game format
  (4 quarters, 2 halves, or halves with a mid-half swap).
- **Players**: add players, check off who's here today, and tap a name to rename them,
  mark who can't play goalie, or pick preferred positions. A kid with a preferred
  position is put there whenever they're on the field.
- **Games**: add the dates from your schedule (with an optional opponent). On game day,
  open the game and make its lineup. Past games keep their lineups to look back on.
- **Lineups**: each game's lineup covers every period at once. Play time is split as evenly as
  possible, nobody sits twice in a row, goalie turns are spread out, and kids move
  between positions. Tap two players in a period to swap them. Share sends the lineup
  as text.
- **Season**: tap **Mark as played** after a game and future lineups give extra time to
  kids who've sat more, and spread goalie turns across the season.

Each coach's teams are saved in their own phone's browser. Use **Back up or move data**
in the team menu to keep a copy or move to another phone.

## Code

The whole app is `web/index.html`: plain HTML, CSS and JavaScript with no build step.
Pushing to `main` publishes it to GitHub Pages via `.github/workflows/pages.yml`.

An earlier native Android version lives in the Git history (removed in favor of the web app).
