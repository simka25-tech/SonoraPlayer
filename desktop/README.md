# Sonora для Windows

Версия плеера для ПК на Compose Desktop. Слой данных (Synology API и демо-библиотека) общий с Android-приложением, звук идёт через JavaFX Media.

## Сборка

Нужен JDK 17. Путь к проекту содержит кириллицу, а jpackage и WiX с ней не работают, поэтому каталог сборки указывается отдельно:

```powershell
$env:JAVA_HOME = "C:\Users\artemkaro\AndroidDev\jdk17"
$env:SONORA_BUILD_DIR = "C:\Users\artemkaro\AndroidDev\build\sonora-desktop"
.\gradlew.bat run                                   # запуск из исходников
.\gradlew.bat createDistributable packageMsi        # папка с Sonora.exe и установщик MSI
```

Результат появится в `$SONORA_BUILD_DIR\compose\binaries\main\` (`app\Sonora\Sonora.exe` и `msi\Sonora-1.0.0.msi`).

## Музыка из интернета

Раздел «Интернет» ищет музыку в открытых каталогах, где её разрешено слушать и скачивать:

- **Internet Archive** работает без ключа. Поиск ограничен netlabels, живыми концертами (etree) и релизами с лицензией Creative Commons или в общественном достоянии.
- **Jamendo** — это независимые исполнители под лицензиями CC. Нужен бесплатный Client ID с devportal.jamendo.com, его вводят в настройках. Треки, скачивание которых исполнитель запретил, можно только слушать.

Скачанные файлы по умолчанию сохраняются в `%USERPROFILE%\Music\Sonora`, папку можно сменить в настройках. Список загрузок хранится в `sonora.json`. Лицензию указывает тот, кто загрузил музыку в каталог; условия использования (например, NC — только некоммерческое) смотрите на странице релиза.

## Данные

Настройки, избранное и история хранятся в `%APPDATA%\Sonora\sonora.json`, кэш обложек лежит в `%APPDATA%\Sonora\cache`. Журнал воспроизведения пишется в `%APPDATA%\Sonora\sonora.log`.
