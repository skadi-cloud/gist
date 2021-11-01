# skadi gist

Share MPS code snippets. More than just screenshots.

## Repository Content

- `ide-plugin`: MPS Plugin that creates a gist from the IDE written in kotlin.
- `js`: Type script sources used by the front end. Mostly [hotwired.dev](https://hotwired.dev) progressive enhancement.
  All core functionality is server side rendered!
- `server`: Backend for the `ide-plugin` and serves the web interface. Written in kotlin with KTOR.
- `shared`: Shared classes between the `ide-plugin` and `server` mostly constants and JSON messages.

# Getting Started with Development

## Prerequists

- an installed JDK at least Java 11 is required (IntelliJ can install it when opening the project)
- optionally: PostgreSQL (for running the tests)
- [Node](https://nodejs.org/en/) and [Yarn](https://yarnpkg.com)

## Editing Code 

The repository contains a intelliJ project in the root directory which you can open and intelliJ will import the 
project for you. It will also download all the dependencies, and you get work on the project. 

If you are using a different code editor e.g. VS Code or GitHub Codespaces your want to run `./gradlew assemble` before 
you open the project. This will download all dependencies and build the project.

## Running the Tests 

To run the tests you will need ProstgreSQL. The esiest way to get an instance up and running is via docker:

```
docker run --name some-postgres -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d postgres
```

This will start a container with a PostgreSQL sever and expose the server to port `5432`. 

If you use this exact configuration you can run the test from intelliJ via the `Tests in 'cloud.skadi'` runconfiguration.
If you changed the port of the password you will need to change the environment variables in the configuration to match 
yours. If you modify the configuration it's best to create copy to not accidentially commit the changes.

If you don't use intelliJ to run the test you will need to set these four environment variables:
```
COOKIE_SALT="give me cookies"
SQL_USER=postgres
SQL_PASSWORD=mysecretpassword
SQL_HOST=localhost:5432
```

`COOKIE_SALT` is used to authenticate cookies during the tests. The value doesn't matter for testing choose what ever
you like. 
The `SQL_*` variables need match your PostgreSQL configuration.

You can then run the test via `./gradlew build`. 

