# Run once if the repository has no gradlew/gradlew.bat yet.
# Requires Gradle 9.6.1 available as `gradle` in PATH.
$ErrorActionPreference = "Stop"

gradle wrapper --gradle-version 9.6.1
Write-Host "Gradle Wrapper generated. Next use .\gradlew.bat ..."
