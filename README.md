# HearthCraft

An offline idle game for Android set in high fantasy.

You are a warlock-culinarian — potion master, chef, alchemist — and the indispensable hidden engine of a roving band of fighters. You never fight. You gather, grow, brew, and cook. The band succeeds entirely because of what you provide.

The line between a perfect dish and a potion is a matter of intent. Food is borderline magical. This is not a cozy cooking sim — it is a specialist identity fantasy rooted in craft and deep knowledge.

---

## Build

Requires Android Studio (JBR included) or a standalone JDK 11+.

```bash
./gradlew build
```

Minimum SDK: API 26 (Android 8.0). No Google Play Services required.

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Database**: Room
- **Background work**: WorkManager
- **DI**: Hilt
- **Serialization**: kotlinx.serialization
- **Architecture**: MVVM + Repository

---

## License

GPL-3.0. See [LICENSE](LICENSE).
