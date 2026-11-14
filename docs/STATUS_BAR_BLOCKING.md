# Блокировка системной шторки (Notification Shade / Quick Settings) в Android

В данном документе подробно описаны архитектурные механизмы операционной системы Android, используемые для предотвращения опускания верхней шторки уведомлений и быстрых настроек, а также реализации этих методов для автомобильных головных устройств (ГУ), кастомных лаунчеров и Kiosk-режима.

---

## 1. Зачем нужна блокировка шторки в автомобильном лаунчере

В специализированных интерфейсах (автомобильные мультимедиа, таксометры, каршеринг, инфокиоски) системная шторка Android является уязвимой точкой:
1. **Безопасность вождения:** водитель не должен случайно опустить громоздкую шторку с мелкими переключателями во время движения.
2. **Изоляция окружения (Kiosk Mode):** через значок шестерёнки в шторке пользователь может попасть в системные настройки Android (`com.android.settings`), изменить параметры Wi-Fi/Bluetooth, сбросить систему или выйти из рабочего лаунчера.
3. **Эстетика Car UI:** стандартная телефонная/планшетная шторка выбивается из автомобильного дизайна приборной панели.

---

## 2. Архитектура обработки жеста шторки в Android

Шторка уведомлений управляется системным процессом **`SystemUI`** (`com.android.systemui`).

```
+-------------------------------------------------------------------+
|               Аппаратный тачскрин (Touch Driver)                  |
+-------------------------------------------------------------------+
                                  | MotionEvents (x, y)
                                  v
+-------------------------------------------------------------------+
|                     InputManagerService (IMS)                     |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                   WindowManagerService (WMS)                     |
|  - Находит окно с наивысшим Z-order под координатами касания       |
+-------------------------------------------------------------------+
            /                                           \
           / [Обычный режим]                             \ [При активном перехвате]
          v                                               v
+-----------------------------+               +------------------------------+
| SystemUI (StatusBarWindow)  |               |  Наш Launcher / Overlay View |
| - Распознаёт свайп сверху   |               |  - Поглощает касание         |
| - Анимирует панель вниз     |               |  - SystemUI не видит жест!   |
+-----------------------------+               +------------------------------+
```

В Android жест свайпа обрабатывается специальной невидимой полосой в самом верху экрана (высотой от 24 до 48dp). Если событие касания попадает в эту зону, `SystemUI` начинает отслеживать перемещение пальца по оси Y и разворачивает панель уведомлений (`NotificationPanelView`).

---

## 3. Основные методы блокировки

В зависимости от прав приложения в системе используются 5 основных подходов:

| Метод | Требуемые права | Версии Android | Надёжность | Применимость |
| :--- | :--- | :--- | :--- | :--- |
| **1. Touch Interceptor Overlay** | `SYSTEM_ALERT_WINDOW` | Android 6 – 15 | ⭐⭐⭐⭐⭐ (99%) | Обычные и кастомные лаунчеры |
| **2. Focus Listener + `collapsePanels`** | `EXPAND_STATUS_BAR` (не везде) | Android 4 – 15 | ⭐⭐⭐⭐ (90%) | Fallback-защита от случайных открытий |
| **3. DevicePolicyManager (Kiosk)** | Device Owner / Device Admin | Android 6 – 15 | ⭐⭐⭐⭐⭐ (100%) | Корпоративные устройства |
| **4. System StatusBar API (`disable`)** | `STATUS_BAR` (system/priv-app) | Все версии | ⭐⭐⭐⭐⭐ (100%) | Системные лаунчеры (в прошивке) |
| **5. Вендорные свойства ГУ (OEM)** | Root / Platform Signature | Android 8 – 13 | ⭐⭐⭐⭐ (OEM-зависимо) | Китайские ГУ (UIS7862, Allwinner, MTK) |

---

### Метод 1: Touch Interceptor Window (Полномочия оверлея) — Самый популярный в лаунчерах

#### Принцип работы:
Приложение поверх всех окон через `WindowManager` создаёт прозрачное невидимое окно полосой толщиной в несколько пикселей (или на всю высоту статус-бара), прижатое к верхнему краю экрана (`Gravity.TOP`).

Так как окно лаунчера имеет тип `TYPE_APPLICATION_OVERLAY` (или `TYPE_SYSTEM_ALERT`), оно перехватывает все события касания (`MotionEvent`) в этой зоне и возвращает `true` (поглощая их). В результате `SystemUI` просто **не получает координаты начала свайпа**.

#### Реализация на Kotlin:

```kotlin
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class StatusBarBlocker(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var interceptorView: View? = null

    fun enableBlocking() {
        if (interceptorView != null) return

        val view = CustomTouchConsumerView(context)
        
        // Высота статус-бара или тонкая полоса 2-4dp по верхнему краю
        val heightPx = getStatusBarHeight(context).coerceAtLeast(10)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            },
            // Флаги: не забирать фокус клавиатуры, но ловить касания
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    // Поглощаем свайп — шторка не начнет тянуться
                    true
                }
                else -> true
            }
        }

        windowManager.addView(view, params)
        interceptorView = view
    }

    fun disableBlocking() {
        interceptorView?.let {
            windowManager.removeView(it)
            interceptorView = null
        }
    }

    private fun getStatusBarHeight(ctx: Context): Int {
        val resId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) ctx.resources.getDimensionPixelSize(resId) else 48
    }

    private class CustomTouchConsumerView(ctx: Context) : View(ctx) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            // Блокируем передачу события в нижние слои
            return true
        }
    }
}
```

---

### Метод 2: Рефлексия `collapsePanels` и отслеживание потери фокуса окна

#### Принцип работы:
Если шторка всё же начала раскрываться (например, жест распознан на системном уровне), главное окно Activity теряет фокус (`onWindowFocusChanged(false)`). В этот момент лаунчер через рефлексию вызывает недокументированный системный метод `StatusBarManager.collapsePanels()`, который принудительно «захлопывает» шторку обратно за доли миллисекунды.

#### Реализация на Kotlin:

```kotlin
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class QuickCollapseController(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("WrongConstant")
    fun collapseStatusBar() {
        try {
            val statusBarService = activity.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val collapseMethod = statusBarManagerClass.getMethod("collapsePanels")
            collapseMethod.isAccessible = true
            collapseMethod.invoke(statusBarService)
        } catch (_: Exception) {
            // Fallback через системный широковещательный интент
            try {
                @Suppress("DEPRECATION")
                val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                activity.sendBroadcast(closeDialogs)
            } catch (_: Exception) {}
        }
    }

    fun handleWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) {
            // Окно потеряло фокус — возможно, потянули шторку
            collapseStatusBar()
            // Повторный импульс через 100мс для надежности
            handler.postDelayed({ collapseStatusBar() }, 100)
        }
    }
}
```

Внутри вашей `MainActivity`:
```kotlin
override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (!hasFocus && isBlockingEnabled) {
        quickCollapseController.handleWindowFocusChanged(hasFocus)
    }
}
```

---

### Метод 3: DevicePolicyManager (Kiosk Mode / Lock Task)

Официальный Android Enterprise путь для корпоративных устройств и таксометров. Если приложение является **Device Owner** (назначено через `dpm set-device-owner` через ADB):

```kotlin
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

fun setKioskStatusBarDisabled(context: Context, admin: ComponentName, disabled: Boolean) {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    if (dpm.isDeviceOwnerApp(context.packageName)) {
        // Полное отключение шторки на уровне ядра ОС
        dpm.setStatusBarDisabled(admin, disabled)
    }
}
```

После вызова `setStatusBarDisabled(admin, true)` ОС Android вообще перестаёт реагировать на любые свайпы сверху.

---

### Метод 4: Платформенные привилегии (`android.permission.STATUS_BAR`)

Если лаунчер собирается в прошивку ГУ (помещён в каталог `/system/priv-app/` или подписан платформенным ключом вендора `platform.x509.pem`):

1. В `AndroidManifest.xml` объявляется разрешение:
```xml
<uses-permission android:name="android.permission.STATUS_BAR" />
<uses-permission android:name="android.permission.EXPAND_STATUS_BAR" />
```

2. Вызов системного API:
```kotlin
import android.app.StatusBarManager
import android.content.Context

fun disableSystemShade(context: Context) {
    val sbm = context.getSystemService(Context.STATUS_BAR_SERVICE) as StatusBarManager
    // 0x00010000 = DISABLE_EXPAND
    // 0x00040000 = DISABLE_NOTIFICATION_ALERTS
    sbm.disable(StatusBarManager.DISABLE_EXPAND or StatusBarManager.DISABLE_NOTIFICATION_ICONS)
}
```

---

### Метод 5: Специфика китайских головных устройств (UIS7862, Allwinner, MTK)

Китайские производители плат ГУ (TS10, Topway, FYT, Microntek) добавляют свои механизмы управления шторкой, сохраняемые в `Settings.System`:

```bash
# Через ADB Shell или Root:
settings put system status_bar_expand 0
settings put secure status_bar_disabled 1
```

В коде лаунчера:
```kotlin
import android.provider.Settings

fun setVendorStatusBarBlocked(context: Context, blocked: Boolean) {
    try {
        Settings.System.putInt(
            context.contentResolver,
            "status_bar_expand",
            if (blocked) 0 else 1
        )
    } catch (_: Exception) {}
}
```

---

## 4. Рекомендуемый комбинированный Production-подход

Для 100% стабильной работы лаунчера без Root-прав лучшим решением является связка:
1. **Основной барьер:** невидимый `Touch Interceptor Overlay` через `WindowManager` (ловит 99% касаний пальцем).
2. **Страховочный барьер (Fallback):** `onWindowFocusChanged` + `collapsePanels()` (если пользователь каким-то образом зацепил шторку сбоку или через мышь/контроллер, она мгновенно схлопывается назад).
3. **Иммерсивный полноэкранный режим:** `WindowInsetsController.hide(statusBars())`.
