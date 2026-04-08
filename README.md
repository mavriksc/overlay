# overlay

Windows desktop overlay for League of Legends that adds simple training cues on top of the game.

## What it does

- Detects whether League is running and in the foreground
- Polls the League Live Client API on `https://localhost:2999`
- Shows a transparent always-on-top overlay with:
- spell pacing indicators
- minimap look reminders
- dodge-direction prompts

## Stack

- Kotlin
- Swing/AWT
- JNA
- Coroutines
- Gradle

## Requirements

- Windows
- Java 22
- League of Legends desktop client
- Local access to the League Live Client API while a game is running

## Run locally

```powershell
.\gradlew run
```

## Packaging

To build a runnable jar:

```powershell
.\gradlew jar
```

To build a local app image with `jpackage`:

```powershell
.\gradlew jpackageImage
```

## Notes

- This is a personal utility project and is tightly coupled to the current Windows and League setup.
- Overlay placement and scaling are based on local assumptions and persisted settings.
- The app is not intended as a Riot-supported or production-ready tool.
