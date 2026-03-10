# just displays the recipes
_default:
    @just --list

# Build everything
build:
    ./gradlew build

# Run the server (serves client too)
run:
    ./gradlew :server:run

# Build client only
client:
    ./gradlew :client:jsBrowserProductionWebpack

# Build server only
server:
    ./gradlew :server:build

# Build common module only
common:
    ./gradlew :common:build

# Clean all build artifacts
clean:
    ./gradlew clean

# Health check against running server
health:
    curl -s http://localhost:8080/health | python3 -m json.tool
