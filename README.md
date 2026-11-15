# Детский журнал

Android-приложение для ведения журнала температуры и приёма лекарств у двух детей.

## Скачать APK

**[Скачать последнюю версию приложения](https://github.com/umnyj0810/DetskiyZhurnal/releases/latest/download/DetskiyZhurnal.apk)**

Все версии доступны на странице [GitHub Releases](https://github.com/umnyj0810/DetskiyZhurnal/releases).

## Возможности
- два профиля детей с изменяемыми именами;
- запись температуры и комментария;
- запись названия лекарства и дозировки;
- автоматические дата и время;
- история по каждому ребёнку;
- удаление записей;
- синхронизация Android ↔ iOS через Firebase Firestore;
- автоматическое получение изменений с другого устройства.

## Настройка Firebase для Android

Для сборки Android-приложения нужен Firebase-конфигурационный файл:
1. Firebase Console → проект приложения → **Добавить приложение Android**.
2. Укажи package name: `ru.example.detskiyzhurnal`.
3. Скачай `google-services.json` и помести его в каталог `app/` (рядом с `app/build.gradle.kts`).
4. Firebase Console → Authentication → Sign-in method → включи **Anonymous**.
5. Firestore Rules должны разрешать чтение/запись авторизованным пользователям для `families/shared` и `families/shared/entries`. Анонимный вход Android считается авторизованным Firebase пользователем.

**Не добавляй `google-services.json` в публичный GitHub-репозиторий.** Положи его локально в `app/` перед сборкой.

## Установка APK
1. Открой ссылку скачивания на Android-телефоне.
2. Скачай `DetskiyZhurnal.apk`.
3. Открой скачанный файл и разреши установку, если Android попросит.

Сборка APK и публикация в GitHub Releases запускаются автоматически при push в `main`. Файл также доступен как артефакт GitHub Actions в течение 30 дней.

> Журнал помогает фиксировать данные, но не заменяет назначения врача. Дозировки и интервалы приёма лекарств сверяй с назначением и инструкцией.
