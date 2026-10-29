// Browser tests for the whole app. Run from the repo root:
//
//     node tests/run.js
//
// Builds both GitHub Pages copies (regular and beta) with tools/build-site.py, then drives
// them in headless Chromium at phone size, in light and dark mode, and checks every feature.
// Uses the preinstalled Playwright (require('playwright')); no npm install needed.
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const SITE = path.join(__dirname, ".site");
fs.rmSync(SITE, { recursive: true, force: true });
execFileSync("python3", ["tools/build-site.py", "web/index.html", SITE], { cwd: ROOT, stdio: "ignore" });
execFileSync("python3", ["tools/build-site.py", "web/index.html", path.join(SITE, "beta"), "--beta"], { cwd: ROOT, stdio: "ignore" });
const MAIN = "file://" + path.join(SITE, "index.html");
const BETA = "file://" + path.join(SITE, "beta", "index.html");

let failures = 0;
const check = (ok, label, detail = "") => {
  console.log(`${ok ? "  ok  " : "  FAIL"} ${label}${detail ? " — " + detail : ""}`);
  if (!ok) failures++;
};

async function openApp(browser, { scheme = "light", url = MAIN, share = true } = {}) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: scheme, acceptDownloads: true });
  await ctx.grantPermissions(["clipboard-read", "clipboard-write"]);
  const page = await ctx.newPage();
  const problems = [];
  page.on("pageerror", (e) => problems.push("error: " + e.message));
  page.on("console", (m) => { if (/Content Security Policy/i.test(m.text())) problems.push("blocked: " + m.text()); });
  page.on("dialog", (d) => { problems.push("dialog: " + d.message()); d.dismiss(); });
  if (share) {
    // Stand in for a phone's share menu and record what it receives.
    await page.addInitScript(() => {
      window.__shared = null;
      navigator.canShare = (d) => !!(d && d.files && d.files.length);
      navigator.share = async (d) => { window.__shared = { title: d.title, text: d.text, files: (d.files || []).map((f) => f.name) }; };
    });
  }
  await page.goto(url);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  return { ctx, page, problems };
}

// ---------------------------------------------------------------- Planner rules

async function plannerTests(browser) {
  console.log("Planner");
  const { ctx, page, problems } = await openApp(browser);
  const r = await page.evaluate(() => {
    const team = (n, extra = {}) => Array.from({ length: n }, (_, i) => ({ id: i + 1, name: "K" + i, goalie: true, preferred: [], ...(extra[i + 1] || {}) }));
    const res = { structure: "ok", rotate: null, stick: null, goalie: null, season: null };
    // Every formation, every roster size, both styles, quarters and halves.
    outer: for (const f of Object.keys(FORMATIONS)) for (const style of ["rotate", "stick"]) for (const periods of [2, 4])
      for (let n = 1; n <= 13; n++) for (let s = 0; s < 3; s++) {
        const ps = team(n, { 2: { goalie: false } });
        const plan = generate(ps, f, periods, {}, style);
        for (const q of plan) {
          const all = [...Object.values(q.positions), ...q.bench];
          if (all.length !== n || new Set(all).size !== n) { res.structure = `${f} ${n}: not everyone once`; break outer; }
          if (Object.keys(q.positions).length !== Math.min(FORMATIONS[f].fill.length, n)) { res.structure = `${f} ${n}: field size`; break outer; }
          if (!hasGoalie(f) && q.positions.GK != null) { res.structure = `${f}: goalie in no-goalie format`; break outer; }
          if (n > 1 && q.positions.GK === 2) { res.structure = `${f} ${n}: no-goalie kid in goal`; break outer; }
        }
        const played = summarize(plan, ps).map((x) => x.played);
        if (Math.max(...played) - Math.min(...played) > 1) { res.structure = `${f} ${n}: uneven time ${played}`; break outer; }
      }
    // Rotate: a preferred spot is a strong lean, not a lock; others still get forward.
    let lf = 0, field = 0, starved = 0;
    for (let g = 0; g < 30; g++) {
      const ps = team(8, { 1: { preferred: ["LF"] } });
      const sm = summarize(generate(ps, "SIX_2_3", 4, {}, "rotate"), ps);
      sm[0].positions.forEach((k) => { if (k && k !== "GK") { field++; if (k === "LF") lf++; } });
      sm.slice(1).forEach((x) => { const f2 = x.positions.filter((k) => k && k !== "GK"); if (f2.length >= 2 && !f2.some((k) => POS[k].line === "FORWARD")) starved++; });
    }
    res.rotate = { lfShare: lf / field, starved };
    // Stick: preferred kids always in their spot.
    let hit = 0, tot = 0;
    for (let g = 0; g < 20; g++) {
      const ps = team(12, { 1: { preferred: ["GK"] }, 3: { preferred: ["CD"] }, 4: { preferred: ["LF"] } });
      summarize(generate(ps, "NINE_3_3_2", 4, {}, "stick"), ps).forEach((x, i) => {
        if (ps[i].preferred.length) x.positions.forEach((k) => { if (k) { tot++; if (ps[i].preferred.includes(k)) hit++; } });
      });
    }
    res.stick = hit / tot;
    // Two goalie-lovers in stick mode split goal within one quarter of each other.
    let worst = 0;
    for (let g = 0; g < 20; g++) {
      const ps = team(9, { 1: { preferred: ["GK"] }, 2: { preferred: ["GK"] } });
      const gk = summarize(generate(ps, "SEVEN_2_3_1", 4, {}, "stick"), ps).slice(0, 2).map((x) => x.positions.filter((k) => k === "GK").length);
      worst = Math.max(worst, Math.abs(gk[0] - gk[1]));
    }
    res.goalie = worst;
    // Season balancing: 7 kids over 7 games all sit the same amount.
    const ps = team(7);
    let season = {};
    for (let g = 0; g < 7; g++) season = record(generate(ps, "SIX_2_3", 4, season), ps, season);
    res.season = Object.values(season).map((x) => x.sat);
    return res;
  });
  check(r.structure === "ok", "lineups valid for every formation, size and style", r.structure);
  check(r.rotate.lfShare > 0.5 && r.rotate.lfShare < 0.9 && r.rotate.starved === 0, "rotate: preferred spot is a lean; everyone gets forward", `LF ${Math.round(r.rotate.lfShare * 100)}%, starved ${r.rotate.starved}`);
  check(r.stick === 1, "stick: preferred kids always in their spot", `${Math.round(r.stick * 100)}%`);
  check(r.goalie <= 1, "stick: goalie-lovers split goal evenly", `max gap ${r.goalie}`);
  check(r.season.every((x) => x === r.season[0]), "season: sitting evens out", r.season.join(","));
  check(!problems.length, "no page errors", problems.join(" | "));
  await ctx.close();
}

// ---------------------------------------------------------------- A full season, on screen

async function flowTests(browser, scheme) {
  console.log(`Screens (${scheme})`);
  const { ctx, page: p, problems } = await openApp(browser, { scheme });
  await p.click("text=Try it with sample names");
  await p.evaluate(() => { state.teams[0].name = "U8 Thunder"; commit(); });
  // Players: edit, preference, absence.
  await p.click('.pinfo:has-text("Ava")');
  await p.fill("#player-name-input", "Ava M");
  await p.click('[data-pos="LF"]');
  await p.click('[data-d="save"]');
  check((await p.textContent(".roster")).includes("Ava M") && (await p.textContent(".roster")).includes("Likes LF"), "edit player name and preference");
  const diego = await p.$eval('.pinfo:has-text("Diego")', (b) => b.dataset.id);
  await p.uncheck("#here-" + diego);
  await p.fill("#add-name", "Zoe"); await p.press("#add-name", "Enter");
  check((await p.textContent(".count")).startsWith("8 of 9"), "attendance and add player", await p.textContent(".count"));
  // Schedule.
  await p.click("#tab-games");
  await p.click('[data-action="add-game"]');
  await p.fill("#game-date", "2026-09-12"); await p.fill("#game-opp", "Tigers"); await p.click("label:has(#side-away)");
  await p.click('[data-d="another"]');
  const flipped = await p.$eval('input[name="side"]:checked', (x) => x.value).catch(() => "");
  await p.fill("#game-opp", "Sharks"); await p.click('[data-d="save"]');
  check(flipped === "home" && (await p.$$(".game-row")).length === 2, "add games with home/away; next game flips side");
  // Lineup, swaps, goals.
  await p.click('.game-row:has-text("Tigers")');
  await p.click('[data-action="make-lineup"]');
  const plan = await p.evaluate(() => state.teams[0].games.find((g) => g.opponent === "Tigers").plan);
  check(plan.length === 4 && plan.every((q) => ![...Object.values(q.positions), ...q.bench].includes(Number(diego))), "lineup made without the absent kid");
  const before = plan[0];
  await p.click('[data-qi="0"][data-slot="F:GK"]'); await p.click('[data-qi="0"][data-slot="B:0"]');
  const after = await p.evaluate(() => state.teams[0].games.find((g) => g.opponent === "Tigers").plan[0]);
  check(after.positions.GK === before.bench[0] && after.bench[0] === before.positions.GK, "tap two players to swap");
  await p.waitForTimeout(500);
  await p.dblclick('[data-qi="1"][data-slot="F:CF"]'); await p.waitForTimeout(500);
  await p.dblclick('[data-qi="2"][data-slot="B:0"]'); await p.waitForTimeout(500);
  await p.click('[data-action="opp-goal"][data-qi="1"]'); await p.click('[data-action="opp-goal"][data-qi="3"]');
  await p.locator('[data-action="remove-opp-goal"]').last().click();
  const sb = (await p.innerText(".scoreboard")).replace(/\s+/g, " ").trim();
  check(/U8 Thunder Away 1\s*–\s*1 Tigers Home/i.test(sb), "goals for both teams, bench can't score, remove works", sb);
  await p.click('[data-action="share"]');
  const text = await p.evaluate(() => window.__shared && window.__shared.text);
  check(!!text && text.includes("Quarter 1") && text.includes("Score: U8 Thunder 1 – 1 Tigers"), "share lineup as text");
  // Played, summary, share and download.
  await p.click('[data-action="mark-played"]'); await p.click('[data-d="ok"]');
  await p.click('[data-action="share-results"]');
  const facts = await p.$$eval(".report-preview table:first-of-type tr", (r) => r.map((x) => [...x.children].map((c) => c.textContent.trim()).join(" ")));
  check(facts[0] === "Team U8 Thunder (Away)" && facts[1] === "Opponent Tigers (Home)" && facts[2] === "Score U8 Thunder 1 – 1 Tigers", "game summary: my team first, home/away, score", facts.slice(0, 3).join(" / "));
  await p.evaluate(() => { window.__shared = null; });
  await p.click('[data-d="share"]');
  await p.waitForFunction(() => window.__shared, null, { timeout: 10000 });
  const shared = await p.evaluate(() => window.__shared);
  check(shared.files.length === 1 && shared.files[0].endsWith(".png"), "share results sends a picture", shared.files[0]);
  const [dl] = await Promise.all([p.waitForEvent("download"), p.click('[data-d="download"]')]);
  check(dl.suggestedFilename().endsWith(".png"), "download picture", dl.suggestedFilename());
  await p.click('[data-d="cancel"]');
  await p.click('[data-action="back"]');
  check((await p.textContent('.game-row:has-text("Tigers") .status')).includes("1–1"), "games list shows final score");
  // Season, archive.
  await p.click("#tab-season");
  check((await p.$$eval(".season tbody tr", (r) => r.map((x) => x.children[1].textContent))).includes("1"), "season totals count played game");
  await p.click('[data-action="archive-season"]'); await p.click('[data-d="ok"]');
  await p.click('[data-action="open-archive"]');
  const archRows = await p.$$(".season tbody tr");
  await p.click(".game-row");
  check(archRows.length >= 8 && (await p.$$(".period")).length === 4 && !(await p.$$('.period [data-action]')).length, "archive keeps totals and read-only lineups");
  // Teams, formations, About.
  await p.click("#team-btn"); await p.click('[data-d="new"]');
  await p.fill("#team-name-input", "U12 Blaze"); await p.check("#formation-NINE_3_3_2");
  const style9 = await p.$eval('input[name="style"]:checked', (x) => x.value);
  await p.click('[data-d="save"]');
  check(style9 === "stick" && (await p.textContent("#team-name")) === "U12 Blaze", "second team; 9v9 defaults to stick");
  await p.click("#about-btn");
  check((await p.textContent(".dlg")).includes("Steven Martin"), "About credits");
  await p.click('[data-d="cancel"]');
  // Reload keeps everything.
  await p.reload();
  check((await p.evaluate(() => state.teams.length)) === 2, "data survives reload");
  check(!(await p.evaluate(() => document.documentElement.scrollWidth > innerWidth)), "no sideways scrolling");
  check(!problems.length, "no page errors or blocked loads", problems.join(" | "));
  await ctx.close();
}

// ---------------------------------------------------------------- Security and data

async function safetyTests(browser) {
  console.log("Security and data");
  const { ctx, page: p, problems } = await openApp(browser);
  await p.click("text=Try it with sample names");
  // A hostile backup with markup in every field it can reach.
  const evil = '"><img src=x onerror="window.__pwned=1">';
  const bad = { teams: [{ id: evil, name: evil, formation: "SIX_2_3", format: evil, style: evil,
    players: [{ id: evil, name: evil, preferred: ["LF", evil] }, { id: 2, name: "Kid" }], absent: [evil],
    games: [{ id: evil, date: evil, opponent: evil, home: evil, played: true, names: { [evil]: evil },
      plan: [{ positions: { GK: evil, [evil]: 2, LF: 2 }, bench: [evil] }], goals: [{ id: evil, p: evil }], oppGoals: [{ p: evil }, { p: 1 }] }],
    archives: [{ id: evil, name: evil, games: [{ id: 9, date: evil, opponent: evil, plan: [{ positions: { LF: 2 }, bench: [] }], played: true }] }] }],
    nextId: evil };
  await p.click("#team-btn"); await p.click('[data-d="backup"]');
  await p.fill("#backup-in", JSON.stringify(bad)); await p.click('[data-d="restore"]');
  for (const sel of ["#tab-players", ".pinfo", '[data-d="cancel"]', "#tab-games", ".game-row", '[data-action="share-results"]', '[data-d="cancel"]',
    '[data-action="edit-game"]', '[data-d="cancel"]', "#tab-season", '[data-action="open-archive"]', ".game-row", "#team-btn", '[data-d="edit"]', '[data-d="cancel"]']) {
    await p.click(sel).catch(() => {});
    await p.waitForTimeout(100);
  }
  const st = await p.evaluate(() => state);
  const ids = [st.teams[0].id, ...st.teams[0].players.map((x) => x.id), ...st.teams[0].games.map((g) => g.id)];
  check(!(await p.evaluate(() => window.__pwned)) && !(await p.$$("main img, [onerror]")).length, "hostile backup can't run code or inject markup");
  check(ids.every(Number.isFinite) && new Set(ids).size === ids.length, "restored ids cleaned and unique", JSON.stringify(ids));
  check(st.teams[0].games[0].home === "" && /^\d{4}-\d{2}-\d{2}$/.test(st.teams[0].games[0].date), "bad dates and home/away replaced");
  // Data from older versions upgrades.
  await p.evaluate(() => localStorage.setItem("soccer-rotation", JSON.stringify({ version: 2, nextId: 30, currentTeamId: 1, savedAt: 1, teams: [{ id: 1, name: "Old", formation: "SIX_2_3", format: "QUARTERS",
    players: [1, 2, 3, 4, 5, 6, 7].map((i) => ({ id: i + 1, name: "K" + i })), absent: [], planSaved: true,
    plan: [0, 1, 2, 3].map(() => ({ positions: { GK: 2, LD: 3, RD: 4, LF: 5, CF: 6, RF: 7 }, bench: [8] })),
    season: { 2: { played: 8, sat: 0, goalie: 8, games: 2 } } }] })));
  await p.reload();
  const up = await p.evaluate(() => ({ games: state.teams[0].games.length, totals: seasonTotals(state.teams[0])[2] }));
  check(up.games === 1 && up.totals.games === 2 && up.totals.goalie === 8, "older saved data upgrades without double-counting", JSON.stringify(up));
  check(!problems.length, "no page errors", problems.join(" | "));
  await ctx.close();
  // Beta keeps its own data.
  const m = await openApp(browser);
  await m.page.click("text=Try it with sample names");
  await m.page.goto(BETA);
  const betaTeams = await m.page.evaluate(() => state.teams.length);
  const tag = await m.page.textContent(".beta-tag").catch(() => "");
  check(betaTeams === 0 && tag === "BETA", "beta copy has its own data and a BETA tag");
  await m.ctx.close();
}

(async () => {
  const browser = await chromium.launch();
  try {
    await plannerTests(browser);
    await flowTests(browser, "light");
    await flowTests(browser, "dark");
    await safetyTests(browser);
  } catch (e) {
    failures++;
    console.log("  FAIL test crashed — " + e.message.split("\n")[0]);
  }
  await browser.close();
  fs.rmSync(SITE, { recursive: true, force: true });
  console.log(failures ? `\n${failures} check(s) failed.` : "\nAll checks passed.");
  process.exit(failures ? 1 : 0);
})();
