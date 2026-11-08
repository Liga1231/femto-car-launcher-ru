#!/system/bin/sh
# Magisk root policy setup for Femto Car Launcher
# Grants non-interactive root policy without interactive prompts

UID_LAUNCHER=$(pm list packages -U | grep "io.github.seijikohara.femto" | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -n 1)

if [ -n "$UID_LAUNCHER" ]; then
    echo "Setting Magisk root grant for Femto Car Launcher (UID $UID_LAUNCHER)..."
    magisk --sqlite "INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($UID_LAUNCHER, 2, 0, 1, 1);"
    echo "Done."
else
    echo "Femto Car Launcher not installed or UID not found."
fi
