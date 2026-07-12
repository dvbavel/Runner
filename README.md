# Runner

_Runner_ is a simple app allowing you to update [**Deep sleeping apps**](https://www.samsung.com/us/support/answer/ANS00088422/) on **Samsung** devices running Android (One UI).

### Deep sleeping apps

If you have this feature enabled, unused (as well as manually added) apps will be added to this list. The problem is that these sleeping apps are not visible to _Play Store_, which cannot update them, unless they are temporarily enabled by launching.

This is pretty much how _Runner_ works: it shows the detected deep sleeping apps in a terminal-style console, then activates them one at a time. Runner detects the Android "disabled until used" state One UI uses for deep sleeping apps, verifies that each app became active, and retries apps that remain in that state. A `[✅]` marks each verified active app. Once you turn off the screen, One UI can put these apps back to sleep.

## How to update deep sleeping apps

1. Install the latest [apk release](https://github.com/moneytoo/Runner/releases/latest) of _Runner_
2. Start _Runner_, keep the device unlocked, and select **Run activation**
3. Wait for the console to show `[✅]` for every app, then select **Open Play Store**
4. Select _Check for updates_ and/or wait a few seconds for _Play Store_ to detect app updates
5. Once updated, turn off the display and all these apps will be put to sleep again

If an app remains disabled after three attempts, Runner marks it with `[!!]` and offers Samsung's _Deep sleeping apps_ screen. This prevents an incomplete activation pass from looking successful.
