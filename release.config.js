module.exports = {
  "branches": [
    {name: 'beta', prerelease: true},
    "main"
  ],
  "tagFormat": ["v${version}"],
  "plugins": [
    ["@semantic-release/commit-analyzer", {
      "preset": "angular",
      "parserOpts": {
        "noteKeywords": ["BREAKING CHANGE", "BREAKING CHANGES", "BREAKING"]
      }
    }],
    ["@semantic-release/release-notes-generator", {
      "preset": "angular",
    }],
    ["@semantic-release/changelog", {
      "changelogFile": "CHANGELOG.md"
    }],
    "@semantic-release/github",
    [
      "@google/semantic-release-replace-plugin",
      {
        "replacements": [
          {
            "files": ["gradle.properties"],
            "from": "VERSION_NAME=.*",
            "to": "VERSION_NAME=${nextRelease.version}",
            "results": [
              {
                "file": "gradle.properties",
                "hasChanged": true,
                "numMatches": 1,
                "numReplacements": 1
              }
            ],
            "countMatches": true
          },
          {
            "files": ["src/main/java/com/amplitude/Constants.java"],
            "from": "String SDK_VERSION = \".*\";",
            "to": "String SDK_VERSION = \"${nextRelease.version}\";",
            "results": [
              {
                "file": "src/main/java/com/amplitude/Constants.java",
                "hasChanged": true,
                "numMatches": 1,
                "numReplacements": 1
              }
            ],
            "countMatches": true
          },
        ]
      }
    ],
    ["@semantic-release/git", {
      "assets": ["gradle.properties", "CHANGELOG.md", "src/main/java/com/amplitude/Constants.java"],
      "message": "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}"
    }],
    ["@semantic-release/exec", {
      "publishCmd": "./gradlew publishAndReleaseToMavenCentral --no-configuration-cache",
    }],
  ],
}
