# Aura Standing Rules

## Surfaces
This repo is a native Android app. There is no website and no HTML.
The app is `app/src/main/java/dev/aura/auradroid/` with resources in
`app/src/main/res/`. The repo page is `README.md`.

If a request says "the website" or "the page", stop and ask what is
meant — do not create one. Putting an image "in the repo" means
commit the file and reference it from `README.md`; committing it
alone renders nothing. The README's Screenshots section is still a
placeholder, so screenshots go there — not into the app's drawable
resources, which are app assets and a different thing entirely.

## This repo is a client, not the agent
`DusanCar-sudo/aura-code` is the actual Aura Code agent. Aura Droid
is its Android client and talks to a local or remote Aura instance
over the network. Agent behaviour changes belong in that repo. Do
not reimplement agent logic here.

## Build
Gradle with Kotlin DSL — `build.gradle.kts`, `app/build.gradle.kts`,
`settings.gradle.kts`. Build with `./gradlew assembleDebug`.

## Layout
`MainActivity.kt` and `AuraApplication.kt` are the entry points.
`ui/screens/` holds chat, sessions and settings, each with a screen
and a view model. `ui/navigation/AuraNavHost.kt` wires routing.
`ui/theme/` holds Material 3 theming and the logo. `data/local/`
holds the Room database and DAOs, `data/network/` the API service
and network adapter, `data/repository/` the repository layer, and
`di/AppModule.kt` the dependency graph.

Jetpack Compose with Material 3 and automatic dark/light switching.
Keep new UI in Compose; do not introduce XML layouts.

## Secrets
API keys belong in app settings or local properties, never committed
and never inlined into a shell command. Use `gh` for GitHub work.
