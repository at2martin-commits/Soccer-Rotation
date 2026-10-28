# Soccer Rotation

An Android app that builds fair playing-time lineups for youth soccer teams.

- **Teams**: keep several teams (tap the team name at the top to switch). Each has its
  own players, formation (6v6, 7v7, or 9v9 with 3 or 2 defenders) and game format
  (4 quarters, 2 halves, or halves with a mid-half swap).
- **Players**: add players, check off who's here today, and tap a name to rename them,
  mark who can't play goalie, or pick preferred positions. A kid with a preferred
  position is put there whenever they're on the field.
- **Lineup**: makes every period of the game at once. Play time is split as evenly as possible,
  nobody sits twice in a row, goalie turns are spread out, and kids move between
  positions. Tap two players in a quarter to swap them. Share sends the lineup as text.
- **Season**: tap **Save game** after a game and future lineups give extra time to kids
  who've sat more, and spread goalie turns across the season.

## Web version

`web/index.html` is the same app as a single web page, so it works in any phone's browser
(including iPhone). It saves to the browser, or to your Claude account when opened from
its claude.ai link. Its Teams menu has **Back up or move data** for copying everything to
another phone.

## Build

```
./gradlew assembleRelease
```

The APK is at `app/build/outputs/apk/release/app-release.apk`. It's signed with the
debug key, so it installs directly (allow "install unknown apps" on the phone).
Pushing to GitHub also builds it via the workflow in `.github/workflows/build.yml`;
download it from the run's Artifacts.
