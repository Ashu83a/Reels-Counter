# Reel Counter 4, Version 4.3: notes that travel with the code

Full plan, decisions and progress log: the Claude Doc "Version 3.1 Plan" (doc id fcaf76a3-5a22-46df-b2d4-d34a7c6ed0a1, https://claude.ai/artifact/YCknXw8RUYRJWfL7kLiEBJ). This file is a short copy of what matters.

Status: all of Version 4.3 is written, but it has not been built or tested yet (no compiler was available, everything was checked by hand). If the GitHub build is red, send the error screenshot. Uninstall version 4.2 first (same app id, new key).

## What is in it
- **Today screen** (first screen): big count, time on reels, a jar of dots filling toward the lock limit with Brain Buddy standing on the pile, daily goal bar, streak chip, setup banner, Stats and Settings buttons.
- **Brain Buddy**: 14 pictures (full and small versions). Damage 0 to 100 rises one reel at a time and recovers one reel every 3 minutes away; stages fresh, curious, concerned, tired, drained, worn, collapsed. Pictures stay (no flashing); thumbs-up for 1.8 seconds when you leave reels. Lock screen shows the break sign.
- **Bubble**: 75% pill, Buddy 30 dp on top or 28 dp inside on the left, or off.
- **Restart screen**: tapping the notification after the bank auto-off, or when a social app opens while the counter is off, opens a screen that goes straight to the accessibility switch.
- **Settings hub** with pages: Permissions and access, Daily goal, Kids mode, Auto Kids mode, Schedules, Break prompt and open-app card, Bubble and Brain Buddy, Limits, Vibration, Messages, Lock and relaxed time, Bedtime, Bank apps and restart, Password.
- **Stats**: Day/Week/Month/Year, Reels or Time, hour by hour (Day) and busiest hours (Week, Month, Year), Monday to Sunday streak circles.
- **Schedules** (adults, never block): Night, Pacing, At my limit, custom ones. **Break prompt**: breathing circle at the red limit (off by default). **Open-app card**: 3 seconds, once per 10 minutes (on).
- **Widget**: Brain Buddy picture, count and time.

## Kids mode (puppy replaces Brain Buddy)
- Parent PIN (stored as a hash); settings need the PIN while Kids mode is on.
- Swipe pacing 8 s (hold the swipe back, or only remind), sitting limit 15 min, daily limit 45 min, two-minute warning, rest screen with PIN to give 10 minutes.
- Rest time at night 19:30 to 07:00. Puppy reminders every 5 minutes. Allowed apps. Parent report (reels, time, games per day).
- Games umbrella: all games together 5 minutes a day (0 blocks), apps installed in the last 7 days count as games, allowed list, Play Store asks for the PIN.
- Auto Kids mode (off by default): points from kids apps (5), kids words (3), fast swiping (2), button mashing, child hours, opening a game, unsteady hand, lying still (1 each). 3 points = gentle level (puppy and reminders), 5 = full. Ends when the screen is off for 1 minute (0 to 10 settable); exit by holding I am an adult for 3 seconds or the PIN; adult mode 30 minutes.
- Quick Settings tile "Kids mode" and the Accessibility button switch Kids mode on; the PIN turns it off.

## Things to test first
1. Build is green; setup checklist ticks; Today screen and jar.
2. Bank app then social app, tap the notification, it opens the accessibility switch.
3. Bubble size, Buddy on top and inside, damage pictures, thumbs-up exit.
4. Settings pages, daily goal, schedules, break prompt, open-app card, hour charts, widget.
5. Kids mode: PIN, pacing hold, limits, rest screen, games cover, Play Store PIN, auto triggers, tile, accessibility button, motion readout.

## Honest limits
Hourly data starts from install. Motion sensors may be blocked in the background on some phones (keep the restart helper running, test with the readout). The Play Store guard only covers that screen (use Google Parental controls too). The app cannot close a game, only cover it. Kids mode helps but is not full parental control. Parked ideas: friend duel, the Buddy growing at a limit, Play Store release.
