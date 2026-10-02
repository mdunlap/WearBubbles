# GitHub release response parsed by Gson (UpdateChecker); without this R8 strips its
# fields and the update check silently reports "up to date" in release builds
-keep class com.wearbubbles.companion.GitHubRelease { *; }
