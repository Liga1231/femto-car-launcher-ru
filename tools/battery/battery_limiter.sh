#!/system/bin/sh
# Magisk boot service: limit battery charge to 70%
# Target device: In-car head unit / tablet / phone on continuous charger
# Prevents lithium battery swelling and thermal degradation

CHARGING_NODE="/sys/class/power_supply/battery/mmi_charging_enable"
if [ ! -f "$CHARGING_NODE" ]; then
    CHARGING_NODE="/sys/class/power_supply/battery/charging_enabled"
fi
CAPACITY_NODE="/sys/class/power_supply/battery/capacity"

MAX_CHARGE=70
RESUME_CHARGE=65

# Wait for system boot to complete
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# Initial safety wait
sleep 5

while true; do
    if [ -f "$CAPACITY_NODE" ] && [ -f "$CHARGING_NODE" ]; then
        CAP=$(cat "$CAPACITY_NODE" 2>/dev/null)
        if [ -n "$CAP" ]; then
            if [ "$CAP" -ge "$MAX_CHARGE" ]; then
                echo 0 > "$CHARGING_NODE"
            elif [ "$CAP" -le "$RESUME_CHARGE" ]; then
                echo 1 > "$CHARGING_NODE"
            fi
        fi
    fi
    sleep 15
done
