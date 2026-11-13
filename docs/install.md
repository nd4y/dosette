# Installing Dosette / Установка Dosette

Dosette is published as an APK in [GitHub Releases](https://github.com/nd4y/dosette/releases/latest).
No account, sign-in or payment is required.
[English](#english) · [Русский](#русский)

## English

### 1. Download the phone APK

- You need a phone with Android 8.0 or newer.
- On the [latest release page](https://github.com/nd4y/dosette/releases/latest), open
  **Assets** and download `dosette-vX.Y.Z.apk`. `X.Y.Z` is the version number in the
  actual file name.
- Do **not** download `dosette-wear-vX.Y.Z.apk` or an `.aab` file for a normal phone
  install. The AAB is not an installer, and the Wear APK is the separate watch build.
- One phone APK already includes the matching Wear OS app. You do not need a second
  download to install the watch app.

### 2. Install the APK

1. Open the downloaded `.apk` file on the phone.
2. Android may ask for permission to install unknown apps. Allow it only for the
   browser or file manager that opened the file, then return to the installer. If
   Android does not ask, nothing is missing. This browser permission can be turned
   off again after installation.
3. Tap **Install**, then **Open**.

### 3. Permissions and what denial means

Allow these when Android asks. They are standard Android controls and can be changed
later in **Settings > Apps > Dosette**.

| Permission | What it is for | If you deny it |
| --- | --- | --- |
| Notifications | Showing dose reminders and low-stock alerts. | Reminders cannot be shown. Your medications, schedules and history are still stored; only the alerts are missing. |
| Exact alarms (Android 12+) | Firing a reminder at its scheduled time. | Android falls back to inexact scheduling. Reminders can arrive late; Dosette does not pretend they are exact. |
| Battery-optimization exemption | Keeping alarms working while the phone is idle. | Doze or a manufacturer's battery manager may delay reminders. The app otherwise works normally. |

These permissions are not workarounds and cannot be forced by Dosette. If reminders
are still late with all grants, check the phone manufacturer's battery restrictions.

### 4. Updates keep your data

Download the newer phone APK and install it over the existing Dosette installation:
tap **Update** in the Android installer. Do not uninstall Dosette and do not clear its
storage to update, because that deletes medications, history and settings.

If Android refuses to update (for example, because the installed copy came from
another signing identity), export a backup from Dosette before removing anything. If
the installer still refuses, uninstall only after the backup is saved, install the new
copy and restore the backup.

Manual updates from GitHub are enough. Automatic updates are optional: add
`https://github.com/nd4y/dosette` in [Obtainium](https://github.com/ImranR98/Obtainium)
and set **Filter APK links by regular expression** (older versions: **Filter APKs by regular expression**) to `dosette-v[0-9].*[.]apk$`. This filter makes
Obtainium offer only the phone APK, not the Wear APK. Obtainium is a third-party tool
and is not required.

### 5. Watch app (Wear OS 3+)

Update both apps when using multiple cabinets. After a phone update, use the phone wizard below to install the matching watch version. Older watch versions cannot send a cabinet choice and cannot display the new cabinet-aware snapshots.

The normal install needs no computer. The phone-side installer requires Android 10 or
newer on the phone.

1. Put the phone and watch on the same reachable Wi-Fi network. Keep the watch charging.
2. On the watch, enable developer options: **Settings > System > About** and tap the
   build number seven times. Then open **Developer options** and enable **ADB debugging**
   and **Wireless debugging** manually. These are not enabled automatically.
3. On the phone, open **More > Settings > Watch > Install or update**.
4. Pair with the watch. If several watches are advertised, choose yours. Enter the
   six-digit pairing code on the phone by hand; it is not copied or filled in for you.
   If discovery is blocked, use the manual address option. The connection port can
   differ from the pairing port.
5. Review the versions and signature check, then confirm **Install or Update**. The
   wizard installs the Wear app bundled inside the phone APK. A normal update keeps
   watch app preferences and queued actions.
6. Open Dosette on the watch and check synchronization. Disable ADB debugging and
   wireless debugging when you are done.

Detailed trust, signature and data-preservation notes:
[watch install documentation](watch-adb-install.md).

### 6. Reminder sound and test notification

Android controls notification sound per app notification channel, not with one global
app switch.

1. In Dosette, open **More > Settings > Reminder sound > Channel settings**. Or open **Settings > Apps > Dosette > Notifications** (or long-press a Dosette
   notification and open its settings). Select the dose-reminder channel, enable sound,
   choose a sound and make sure the channel is not set to Silent. Menu names vary by
   manufacturer.
2. In Dosette, send the explicit **test notification** from the reminder/sound setup
   and confirm that it makes a sound. If it does not, adjust the Android channel above.
3. Silent mode, Do Not Disturb and manufacturer notification rules still apply. Dosette
   does not bypass them. If you need audible alerts, allow Dosette in Do Not Disturb or
   choose an appropriate sound mode.

## Русский

Dosette распространяется как APK на [GitHub Releases](https://github.com/nd4y/dosette/releases/latest).
Аккаунт, вход и оплата не нужны.
[English](#english) · [Русский](#русский)

### 1. Скачайте APK для телефона

- Нужен телефон на Android 8.0 или новее.
- На [странице последнего релиза](https://github.com/nd4y/dosette/releases/latest)
  откройте **Assets** и скачайте `dosette-vX.Y.Z.apk`. `X.Y.Z` - номер версии в
  реальном имени файла.
- Для обычной установки на телефон **не** скачивайте `dosette-wear-vX.Y.Z.apk` и
  файл `.aab`. AAB не является установщиком, а Wear APK - отдельная сборка для часов.
- Один APK для телефона уже содержит подходящее приложение Wear OS. Второй файл для
  установки приложения на часы не нужен.

### 2. Установите APK

1. Откройте скачанный `.apk` на телефоне.
2. Android может запросить разрешение на установку неизвестных приложений. Разрешите
   его только браузеру или файловому менеджеру, из которого открыт файл, и вернитесь
   к установщику. Если запроса нет, ничего дополнительно включать не нужно. После
   установки это разрешение можно снова отключить.
3. Нажмите **Установить**, затем **Открыть**.

### 3. Разрешения и что означает отказ

Разрешите следующие пункты, когда Android их запросит. Это обычные системные
разрешения; позже их можно изменить в **Настройки > Приложения > Dosette**.

| Разрешение | Для чего нужно | Если отказать |
| --- | --- | --- |
| Уведомления | Показ напоминаний о приёме и предупреждений о низком запасе. | Напоминания не будут показаны. Лекарства, расписание и история сохранятся; пропадут только оповещения. |
| Точные будильники (Android 12+) | Запуск напоминания в назначенное время. | Android перейдёт к неточному планированию. Напоминания могут приходить позже; Dosette не изображает их точными. |
| Исключение из оптимизации батареи | Работа будильников, когда телефон не используется. | Doze или фирменный менеджер батареи может задержать напоминания. В остальном приложение работает как обычно. |

Эти разрешения нельзя обойти или выдать за приложение принудительно. Если напоминания
всё равно опаздывают, проверьте ограничения батареи производителя телефона.

### 4. Обновления сохраняют данные

Скачайте новый APK для телефона и установите его поверх уже установленного Dosette:
нажмите **Обновить** в системном установщике. Не удаляйте Dosette и не очищайте его
данные ради обновления - так удалятся лекарства, история и настройки.

Если Android отказывается обновлять (например, установленная копия подписана другим
ключом), сначала экспортируйте резервную копию из Dosette. Если установщик всё равно
отказывает, удаляйте приложение только после сохранения копии, затем установите новую
версию и восстановите данные.

Ручных обновлений с GitHub достаточно. Автообновление необязательно: добавьте
`https://github.com/nd4y/dosette` в [Obtainium](https://github.com/ImranR98/Obtainium)
и укажите в **Filter APK links by regular expression** (older versions: **Filter APKs by regular expression**) выражение `dosette-v[0-9].*[.]apk$`. С ним
Obtainium предлагает только APK телефона, а не Wear APK. Obtainium - сторонний
инструмент, и без него можно обойтись.

### 5. Приложение для часов (Wear OS 3+)

Для нескольких аптечек обновляйте оба приложения. После обновления телефона установите соответствующую версию на часы через мастер ниже. Старые версии часов не передают выбор аптечки и не поддерживают новые данные.

Для обычной установки компьютер не нужен. Встроенный установщик на телефоне требует
Android 10 или новее.

1. Подключите телефон и часы к одной доступной сети Wi-Fi. Держите часы на зарядке.
2. На часах включите параметры разработчика: **Настройки > Система > О телефоне**
   (на часах - «О часах») и нажмите на номер сборки семь раз. Затем откройте
   **Параметры разработчика** и вручную включите **Отладку по ADB** и
   **Отладку по Wi-Fi**. Сами они не включаются.
3. На телефоне откройте **Ещё > Настройки > Часы > Установить или обновить**.
4. Сопрягите телефон с часами. Если найдено несколько часов, выберите свои.
   Шестизначный код сопряжения введите на телефоне вручную: он не подставляется
   автоматически. Если поиск заблокирован, укажите адрес вручную. Порт подключения
   может отличаться от порта сопряжения.
5. Проверьте версии и результат проверки подписи, затем подтвердите
   **Установить или обновить**. Установщик ставит приложение Wear, встроенное в APK
   телефона. Обычное обновление сохраняет настройки приложения на часах и очередь
   действий.
6. Откройте Dosette на часах и проверьте синхронизацию. После установки отключите
   отладку по ADB и по Wi-Fi.

Подробности о подписи, доверии и сохранении данных:
[документация по установке на часы](watch-adb-install.md).

### 6. Звук напоминаний и тестовое уведомление

Android настраивает звук отдельно для каждого канала уведомлений приложения, а не
одним общим переключателем.

1. В Dosette откройте **Ещё > Настройки > Звук напоминаний > Настройки канала**. Также можно открыть **Настройки > Приложения > Dosette > Уведомления** (или нажмите и
   удерживайте уведомление Dosette, затем откройте его настройки). Выберите канал
   напоминаний о приёме, включите звук, выберите мелодию и убедитесь, что канал не
   стоит в режиме «Без звука». Названия пунктов зависят от производителя.
2. В Dosette отправьте явное **тестовое уведомление** в настройках напоминаний и
   звука и убедитесь, что оно звучит. Если звука нет, измените канал Android выше.
3. Беззвучный режим, «Не беспокоить» и правила производителя всё равно действуют.
   Dosette их не обходит. Если нужны слышимые оповещения, разрешите Dosette в
   «Не беспокоить» или выберите подходящий режим звука.

Channel behavior follows the [Android notification-channel model](https://developer.android.com/develop/ui/compose/notifications/channels). Obtainium filters are described in its [official guide](https://wiki.obtainium.imranr.dev/sources/).
