# Runner

_Runner_ is a simple app allowing you to update [**Deep sleeping apps**](https://www.samsung.com/us/support/answer/ANS00088422/) on **Samsung** devices running Android (One UI).

### Deep sleeping apps

If you have this feature enabled, unused (as well as manually added) apps will be added to this list. The problem is that these sleeping apps are not visible to _Play Store_, which cannot update them, unless they are temporarily enabled by launching.

This is pretty much how _Runner_ works: it activates user-installed (non-system) deep sleeping apps one at a time, verifies that each app became active, and retries apps that are still disabled. Only after all detected apps are active does Runner open the _Play Store_. Once you turn off the screen, One UI can put these apps back to sleep.

## How to update deep sleeping apps

1. Install the latest [apk release](https://github.com/moneytoo/Runner/releases/latest) of _Runner_
2. Start _Runner_, keep the device unlocked, and wait for _Play Store_ to open
3. Select _Check for updates_ and/or wait a few seconds for _Play Store_ to detect app updates
4. Once updated, turn off the display and all these apps will be put to sleep again

If an app remains disabled after three attempts, Runner opens Samsung's _Deep sleeping apps_ screen instead of opening Play Store. This prevents an incomplete activation pass from looking successful.
