#!/system/bin/sh
# Magisk root policy setup for Femto Car Launcher
# Grants non-interactive root policy without interactive prompts

if [ "$(id -u)" -ne 0 ]; then
    echo "Error: This script must be run as root." >&2
    exit 1
fi

UID_LAUNCHER=$(pm list packages -U | grep -E '^package:io\.github\.seijikohara\.femto\s+' | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -n 1)

if [ -n "$UID_LAUNCHER" ]; then
    echo "Setting Magisk root grant for Femto Car Launcher (UID $UID_LAUNCHER)..."
    magisk --sqlite "INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($UID_LAUNCHER, 2, 0, 1, 1);"
    if [ $? -eq 0 ]; then
        echo "Done."
    else
        echo "Error: Failed to apply Magisk policy." >&2
        exit 1
    fi
else
    echo "Error: Femto Car Launcher not installed or UID not found." >&2
    exit 1
fi
