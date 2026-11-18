BINARY  := wallet
VERSION := 0.0.0
MONOVA  := $(shell which monova 2> /dev/null)
APK     := app/build/outputs/apk/debug/app-debug.apk
FDROID_REPO := jsnjack/android-apps

version:
ifdef MONOVA
override VERSION = $(shell monova 2> /dev/null || echo 0.0.0)
else
	$(info "Install monova with: grm install jsnjack/monova")
endif

# Gradle 9.8 and AGP 9.4 run on JDK 17 or newer. Use JAVA_HOME when set,
# else a JDK 21 in ~/.jdks (same lookup as wahoo-share).
GRADLE = set -e; \
	if [ -z "$$JAVA_HOME" ]; then \
	  JDK=$$(ls -d $$HOME/.jdks/jdk-21* 2>/dev/null | head -1); \
	  if [ -z "$$JDK" ]; then echo "==> need JDK 21: set JAVA_HOME or unpack one into ~/.jdks (see README.md)"; exit 1; fi; \
	  export JAVA_HOME=$$JDK; \
	fi; \
	SDK=$${ANDROID_HOME:-$$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)}; \
	if [ ! -d "$$SDK/platforms/android-37.0" ] || [ ! -d "$$SDK/build-tools/37.0.0" ]; then \
	  echo "==> need the Android SDK with platform 37.0 and build tools 37.0.0 (see README.md)"; exit 1; \
	fi; \
	./gradlew --quiet -PappVersion=$(VERSION)

test:
	$(GRADLE) :app:testDebugUnitTest

lint:
	$(GRADLE) :app:lintDebug

# Debug-signed for now: personal sideload, and the debug signature lets each
# build update the installed app in place.
build: version
	$(GRADLE) :app:assembleDebug
	@mkdir -p bin
	cp $(APK) bin/$(BINARY).apk

# Kotlin has no formatter or vet step here yet: build compiles with warnings
# shown, and Android lint covers the static checks.
check: build test lint
	@echo "==> make check: all green"

install: build
	adb install -r bin/$(BINARY).apk

# Sync counts and errors only; card names and numbers are never logged.
logs:
	adb logcat -s Wallet

standards:
	curl -fsSL https://raw.githubusercontent.com/jsnjack/standards/master/AGENTS.universal.md \
	    -o AGENTS.universal.md
	curl -fsSL https://raw.githubusercontent.com/jsnjack/standards/master/AGENTS.android.md \
	    -o AGENTS.android.md

release-preflight:
	@command -v gh >/dev/null 2>&1 || { echo "Install GitHub CLI with: sudo dnf install gh"; exit 1; }
	@gh auth status --hostname github.com >/dev/null
	@test -z "$$(git status --porcelain)" || { echo "Commit your changes and push master before make release."; exit 1; }
	@test "$$(git rev-parse HEAD)" = "$$(git ls-remote origin refs/heads/master | cut -f1)" || { echo "Push this commit to origin/master before make release."; exit 1; }

release: release-preflight check
	grm release jsnjack/$(BINARY) -f bin/$(BINARY).apk -t "v`monova`"
	$(MAKE) fdroid-publish

fdroid-publish: version
	gh workflow run publish.yml --repo $(FDROID_REPO) -f app=$(BINARY) -f revision="$$(git rev-parse HEAD)" -f release_version=$(VERSION)
	@echo "F-Droid publication queued: https://github.com/$(FDROID_REPO)/actions/workflows/publish.yml"

clean:
	rm -rf app/build build bin/

.PHONY: version test lint build check install logs standards release-preflight release fdroid-publish clean
