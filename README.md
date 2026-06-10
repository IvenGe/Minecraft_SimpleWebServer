# Minecraft SimpleWebServer

Just a simple web server inside a Minecraft server (Paper plugin).

## Requirements

- **Minecraft / Paper 26.1.x** (the current release line)
- **Java 25** (required by Paper 26.1)

## Building

```
gradle wrapper   # if the wrapper scripts are not present yet
./gradlew build
```

The plugin jar is written to `build/libs/`. Drop it into your server's `plugins/` folder.

## Configuration (`plugins/WebServer/config.yml`)

| Key | Default | Description |
| --- | --- | --- |
| `port` | `8080` | Port the web server listens on (ports < 1024 need root) |
| `WebSiteFolder` | `Website` | Folder inside `plugins/WebServer/` served as the web root |
| `LogNewConnections` | `false` | Log each incoming connection to the console |

Put your `index.html` and other site files in `plugins/WebServer/Website/`.
