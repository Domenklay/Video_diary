# Постоянная подпись Android APK

GitHub Actions собирает `debug` APK без секретов. Ключ отладочной подписи GitHub может меняться между запусками, поэтому обновление поверх предыдущего debug APK может быть отклонено.

Для стабильных обновлений **один раз** подготовьте отдельный закрытый ключ и сохраните его только у владельца приложения.

## Генерация

На компьютере с JDK 17+ в PowerShell:

```powershell
keytool -genkeypair -v -keystore quietmoments-release.jks -storetype PKCS12 -keyalg RSA -keysize 3072 -validity 10000 -alias quietmoments
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\quietmoments-release.jks)))
```

Скопируйте вывод второй команды в GitHub Secret. **Не помещайте файл keystore, пароли или его Base64 в Git, коммиты, сообщения чата или публичные логи**. Храните резервную копию ключа и паролей в безопасном месте.

## Настройка

GitHub → репозиторий `Domenklay/Video_diary` → Settings → Secrets and variables → Actions → New repository secret.

Создайте четыре секрета:

- `QM_KEYSTORE_B64`: Base64 содержимого `quietmoments-release.jks`;
- `QM_KEYSTORE_PASSWORD`: пароль хранилища;
- `QM_KEY_ALIAS`: `quietmoments`;
- `QM_KEY_PASSWORD`: пароль ключа.

После настройки сборки GitHub Actions будут дополнительно создавать **QuietMoments-Universe-stable-release-apk**. Нельзя заменить уже установленную отладочную версию release-сборкой с другим ключом без переноса данных. До переустановки создайте резервную копию, убедитесь, что файл не повреждён, и восстановите её в новой версии.

## Важно

Пока все четыре секрета не заданы, workflow продолжит собирать только debug APK. Сама конфигурация подписи в исходниках не создаёт постоянный ключ автоматически.
