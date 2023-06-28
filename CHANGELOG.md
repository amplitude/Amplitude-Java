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
