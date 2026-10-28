# Soccer Rotation

An Android app that builds a four-quarter lineup for a 6-on-field youth soccer team
(goalie, left/right defense, left/center/right forward).

- **Team**: add players, check off who's here today, and mark who can play goalie.
- **Lineup**: makes all four quarters at once. Play time is split as evenly as possible,
  nobody sits two quarters in a row, goalie turns are spread out, and kids move between
  positions. Tap two players in a quarter to swap them. Share sends the lineup as text.
- **Season**: tap **Save game** after a game and future lineups give extra time to kids
  who've sat more, and spread goalie turns across the season.

## Build

```
./gradlew assembleRelease
```

The APK is at `app/build/outputs/apk/release/app-release.apk`. It's signed with the
debug key, so it installs directly (allow "install unknown apps" on the phone).
Pushing to GitHub also builds it via the workflow in `.github/workflows/build.yml`;
download it from the run's Artifacts.
