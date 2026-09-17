# Postomat SIZ — Standard SAF Test

Минимальный тест, собранный обычным Android toolchain:
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- JDK 17
- compileSdk / targetSdk 35
- minSdk 23

Приложение НЕ запрашивает WRITE_EXTERNAL_STORAGE и не обращается к реальному постомату.

После запуска нажать «СОХРАНИТЬ ТЕСТОВЫЙ XLSX». Должно открыться системное окно выбора файла (Storage Access Framework). Сохранённый файл — реальный минимальный XLSX.

Для CI приложен `.github/workflows/build-apk.yml`.
