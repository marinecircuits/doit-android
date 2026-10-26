# build-release Specification

## Purpose

Defines how doit is built, versioned and released entirely on GitHub Actions, following the same Commitizen prepare/publish release flow as the other marinecircuits repositories.

## Requirements

### Requirement: Continuous build
Every push to `main` and every pull request SHALL run the unit tests and build a debug APK, uploaded as a workflow artifact named `doit-debug-<short sha>`. A failing test SHALL fail the run.

#### Scenario: Push to main
- **WHEN** a commit is pushed to `main`
- **THEN** the workflow runs the unit tests and the run's artifacts contain a debug APK for that commit

### Requirement: Debug and release side by side
Debug builds SHALL use the application id `com.marinecircuits.doit.debug` and the label "doit debug", so a debug build can be installed next to the release build.

#### Scenario: Both installed
- **WHEN** the user installs a release APK and a CI debug APK on the same phone
- **THEN** both apps are installed independently with distinguishable names

### Requirement: Single version source
The app version SHALL be managed by Commitizen with semantic versioning and tags without a prefix (e.g. `0.1.0`). The Android version name SHALL equal that version and the version code SHALL be `major × 10000 + minor × 100 + patch` (minimum 1).

#### Scenario: Version 1.2.3
- **WHEN** the Commitizen version is 1.2.3
- **THEN** the APK has versionName "1.2.3" and versionCode 10203

### Requirement: Prepare Release workflow
A manually started "Prepare Release" workflow on `main` SHALL compute the next version from Conventional Commits, update the version files and `CHANGELOG.md`, run the unit tests, push a `release/<version>` branch and open a pull request titled "Release <version>" with the `release` label. A dry-run option SHALL only print the next version.

#### Scenario: Dry run
- **WHEN** the workflow is started with dry run enabled
- **THEN** it prints the version it would release and pushes nothing

#### Scenario: Prepare a release
- **WHEN** the workflow is started after a `feat:` commit since release 0.1.0
- **THEN** a pull request "Release 0.2.0" from `release/0.2.0` exists with the bumped version and changelog

### Requirement: Publish Release workflow
Merging a release pull request SHALL validate that its title and branch match the Commitizen version, run the unit tests, tag the merge commit with the version, build the signed release APK and publish a GitHub release named "Release <version>" with the changelog section as notes and `doit-<version>.apk` attached. Re-running it SHALL NOT create a second tag or release.

#### Scenario: Merge release PR
- **WHEN** the "Release 0.2.0" pull request is merged
- **THEN** tag `0.2.0` points at the merge commit and the GitHub release "Release 0.2.0" has `doit-0.2.0.apk` attached

#### Scenario: Non-release PR merged
- **WHEN** a pull request without the `release` label is merged
- **THEN** the Publish Release workflow does not run its publishing job

### Requirement: Release signing
Release APKs SHALL be signed with the shared marinecircuits Android key from the organization secrets. If the signing secrets are missing, the release build MUST fail rather than produce an unsigned or debug-signed APK.

#### Scenario: Secrets missing
- **WHEN** Publish Release runs without `ANDROID_SIGNING_KEYSTORE_BASE64`
- **THEN** the build fails before a GitHub release is created
