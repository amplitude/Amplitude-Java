## [1.12.5](https://github.com/amplitude/Amplitude-Java/compare/v1.12.4...v1.12.5) (2025-02-27)


### Bug Fixes

* adding currency property to events ([#110](https://github.com/amplitude/Amplitude-Java/issues/110)) ([eccb693](https://github.com/amplitude/Amplitude-Java/commit/eccb693651719577026bf77a240366bc3466dd84))

## [1.12.4](https://github.com/amplitude/Amplitude-Java/compare/v1.12.3...v1.12.4) (2024-10-10)


### Bug Fixes

* use sendThreadPool in sendEvents ([#107](https://github.com/amplitude/Amplitude-Java/issues/107)) ([88a9589](https://github.com/amplitude/Amplitude-Java/commit/88a9589811db2ff762c7b938dc3cd3ae2a1a61e7))

## [1.12.3](https://github.com/amplitude/Amplitude-Java/compare/v1.12.2...v1.12.3) (2024-08-21)


### Bug Fixes

* Add exception message in callback message ([#106](https://github.com/amplitude/Amplitude-Java/issues/106)) ([cadfa06](https://github.com/amplitude/Amplitude-Java/commit/cadfa0676c54e84b5dab2cbeec65f233d7816777))

## [1.12.2](https://github.com/amplitude/Amplitude-Java/compare/v1.12.1...v1.12.2) (2024-02-28)


### Bug Fixes

* Manually pushing a version update ([#103](https://github.com/amplitude/Amplitude-Java/issues/103)) ([aaf58b4](https://github.com/amplitude/Amplitude-Java/commit/aaf58b411b9d0deb6144eb88ea8c0e0d84637191))

## [1.12.1](https://github.com/amplitude/Amplitude-Java/compare/v1.12.0...v1.12.1) (2024-02-27)


### Bug Fixes

* Updating readme to force release a new version ([#101](https://github.com/amplitude/Amplitude-Java/issues/101)) ([9220917](https://github.com/amplitude/Amplitude-Java/commit/9220917372c51083a8ce827333b2897ebee391d2))

# [1.12.0](https://github.com/amplitude/Amplitude-Java/compare/v1.11.1...v1.12.0) (2023-08-03)


### Bug Fixes

* lock semantic-release-replace-plugin version to workaround error in the latest version ([#95](https://github.com/amplitude/Amplitude-Java/issues/95)) ([7c8fd83](https://github.com/amplitude/Amplitude-Java/commit/7c8fd83c00175dfbfa200734d85e2502e8afcd46))


### Features

* support setter for logMode and logLevel ([#94](https://github.com/amplitude/Amplitude-Java/issues/94)) ([d07f830](https://github.com/amplitude/Amplitude-Java/commit/d07f83085a874a26d734bceea77f44b290157508))

## [1.11.1](https://github.com/amplitude/Amplitude-Java/compare/v1.11.0...v1.11.1) (2023-06-28)


### Bug Fixes

* should retry on 429 ([#91](https://github.com/amplitude/Amplitude-Java/issues/91)) ([bb9ce66](https://github.com/amplitude/Amplitude-Java/commit/bb9ce6680c268db318feb25ec12ac0d0f3d76df0))

# [1.11.0](https://github.com/amplitude/Amplitude-Java/compare/v1.10.0...v1.11.0) (2023-06-20)


### Bug Fixes

* fix failed unit tests due to async methods ([#74](https://github.com/amplitude/Amplitude-Java/issues/74)) ([026c3cc](https://github.com/amplitude/Amplitude-Java/commit/026c3cc42f957272dfdef9522e9cd9ceac1c1b54))
* handle non-json response bodies and retry on 5xx errors. ([#85](https://github.com/amplitude/Amplitude-Java/issues/85)) ([1d4567e](https://github.com/amplitude/Amplitude-Java/commit/1d4567e9f5c0c19ca6e6245ec8e8731437e24bd2))


### Features

* add ingestion_metadata field ([#78](https://github.com/amplitude/Amplitude-Java/issues/78)) ([727bb51](https://github.com/amplitude/Amplitude-Java/commit/727bb51ba9d88060e4480e6260e29baea59194cd))
* add library context field ([#76](https://github.com/amplitude/Amplitude-Java/issues/76)) ([c874396](https://github.com/amplitude/Amplitude-Java/commit/c87439673ddcab22868cde8be53047b9a7d4dc31))


### Reverts

* Revert "feat: add library context field (#76)" (#77) ([7d9e474](https://github.com/amplitude/Amplitude-Java/commit/7d9e474ffcf292a9329015aadc17379b6bfe729c)), closes [#76](https://github.com/amplitude/Amplitude-Java/issues/76) [#77](https://github.com/amplitude/Amplitude-Java/issues/77)
