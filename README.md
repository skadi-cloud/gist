# skadi gist

Share MPS code snippets. More than just screenshots.

## Repository Content

- `ide-plugin`: MPS Plugin that creates a gist from the IDE written in kotlin.
- `js`: Type script sources used by the front end. Mostly [hotwired.dev](https://hotwired.dev) progressive enhancement.
  All core functionality is server side rendered!
- `server`: Backend for the `ide-plugin` and serves the web interface. Written in kotlin with KTOR.
- `shared`: Shared classes between the `ide-plugin` and `server`` mostly constants and JSON messages.