# just displays the recipes
_default:
    @just --list

# Build everything
build:
    ./gradlew build

# Run the server (serves client too)
run:
    ./gradlew :gk-server:run

# Build client only
client:
    ./gradlew :gk-client:jsBrowserProductionWebpack

# Build server only
server:
    ./gradlew :gk-server:build

# Build common module only
common:
    ./gradlew :gk-common:build

# Run bot: just bot <gameCode> [serverUrl]
bot *args:
    ./gradlew :gk-bot:run --console=plain --args="{{args}}"

# Clean all build artifacts
clean:
    ./gradlew clean

# Health check against running server
health:
    curl -s http://localhost:8080/health | python3 -m json.tool

# Setup the server
srv-setup:
    ssh root@188.245.227.21 'bash -s' < deploy/setup.sh