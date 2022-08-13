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

The repository contains a intelliJ project in the root directory which you can open and intelliJ will import the project
for you. It will also download all the dependencies, and you get work on the project.

If you are using a different code editor e.g. VS Code or GitHub Codespaces your want to run `./gradlew assemble` before
you open the project. This will download all dependencies and build the project.

## Running the Tests

To run the tests you will need ProstgreSQL. The esiest way to get an instance up and running is via docker:

```
docker run --name some-postgres -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d postgres
```

This will start a container with a PostgreSQL sever and expose the server to port `5432`.

If you use this exact configuration you can run the test from intelliJ via the `Tests in 'cloud.skadi'`
runconfiguration. If you changed the port of the password you will need to change the environment variables in the
configuration to match yours. If you modify the configuration it's best to create copy to not accidentially commit the
changes.

If you don't use intelliJ to run the test you will need to set these four environment variables:

```
COOKIE_SALT="give me cookies"
SQL_USER=postgres
SQL_PASSWORD=mysecretpassword
SQL_HOST=localhost:5432
```

`COOKIE_SALT` is used to authenticate cookies during the tests. The value doesn't matter for testing choose what ever
you like. The `SQL_*` variables need match your PostgreSQL configuration.

You can then run the test via `./gradlew build`.

## Running the MPS plugin

Open the file `build.gradle.kts` in the `ide-plugin` folder and change the `intellij` block to use a local
path and disable instrumentation of the code, e.g.:
```
intellij {
    localPath.set("/Applications/mps/mac/MPS 2021.1.4.app/Contents")
    instrumentCode.set(false)
}
```

Then you have to make sure that the system variable `idea.platform.prefix` is set to 'Idea'.
Workaround: create a file with extension .sh or .bat in the bin folder of the MPS installation with the following content: `-Didea.platform.prefix=Idea`.

You can now open MPS with the plugin installed by calling `./gradlew runIde`.

# Running your own Instance

The preferred way of running skadi gist is via a container. The container image is available
at [docker hub](https://registry.hub.docker.com/r/skadicloud/gist-server).

## Storage Backend

skadi gist support to two backends for storing the screenshots: `directory` and `s3`. The models are always stored in
the database.

### S3 Storage

skadi gist support storing screenshots in AWS S3 or compatible storage (
e.g. [digitalocean spaces](https://www.digitalocean.com/products/spaces/)
, [scaleway object storage](https://www.scaleway.com/en/object-storage/) or self-hosted [minIO](https://min.io)). When
using this storage backend skadi will store all screenshots in S3 making the container entirely stateless.

Skadi gist will also leverage features like presigned urls for none public gists. This means the urls to screenshots are
only valid for short period of time.

#### Configuration

To configure S3 storage set the `STORAGE_KIND` environment varialbe to `s3`.

Additional configuration via environment variables is required for authentication and the endpoint at your cloud
provider:

```
S3_REGION=<AWS region of your S3 storage>
S3_ACCESS_KEY=<AWS Access Key>
S3_SECRET_KEY=<AWS Secret Key>
S3_BUCKET_NAME=<Bucket used for storing the screenshots>
```

If you are not using AWS but another provider you can omit `S3_REGION`. Instead, set `S3_ENDPOINT` according to your
providers' documentation.

### Directory Storage

Directory storage uses the file system to store the screenshots. It then serves these files directly from the local
filesystem. This storage is mainly useful for testing and small deployments.

#### Configuration

To confgiure directory storage set the `STORAGE_KIND` environment varialbe to `directory`.

For production deployments you definitly want to set `STORAGE_DIRECTORY` to point to a directory that is persistent and
not deleted when the container is updated. For testing no additional configuation is required.

## Database

skadi gist uses PostgreSQL for storing all data except the screenshots. This includes the models that are stored as JSON
in the database. You can configure the database connection with the following environment variables:

```
SQL_USER=<uses for the database>
SQL_PASSWORD=<password for the user>
SQL_HOST=<hostname including port e.g. localhost:5432>
SQL_DB=<database name, the database needs to exists and isn't created by the application.>
```

Make sure the user has `CREATE` permissions on the database in order to be able to create and update the database.

If you like to see support for a database backend that requires less configuration feel free to vode
up [this issue](https://github.com/skadi-cloud/gist/issues/55).

## GitHub Authentication

Skadi gist uses OAuth to authenticate the user. At the moment only GitHub is supported. To use GitHub authentication you 
need to [create a new](https://github.com/settings/applications/new) OAuth App. The callback URL is the url of your 
skadi cloud instance. If you are testing locally this is `http://localhost:8080`.

After the OAuth app is created you can start configuring skadi cloud to use it. 

### Configuration 

skadi cloud uses environment variables to configure authentication:

```
GITHUB_SECRET=<client secret generated on the GitHub>
GITHUB_ID=<client id from GitHub>
```

In addition to the OAuth configuration a key for signing the cookies is required. That key is used to authenticate that
the cookies a client presents aren't modified. Keep this key secret as others can use it to inpersionate any use with 
that key. If you change the key all existing user sessions are invalidated and the users need to login again.

To set the key use the environment variable `COOKIE_SALT`. The value has not requirements since it is not used directly
but the key for signing cookies is derived from the value. 

## Docker Compose Example

Make sure you replace the environemnt variables with your values!

### Direct Access Deployment 

This creates a deployment that exposes skadi gist directly on port 8080. 

```
version: '3'

services:
  skadi-gist:
    image: skadicloud/gist-server
    container_name: skadi-gist
    restart: unless-stopped
    ports:
      - 8080:8080
    security_opt:
      - no-new-privileges:true
    volumes:
      - ./data:/data
    environment:
      - STORAGE_KIND=directory
      - STORAGE_DIRECTORY=/data
      - SQL_HOST=database
      - SQL_USER=skadi
      - SQL_PASSWORD=123456789
      - SQL_DB=skadi-gist
      - GITHUB_SECRET=addme
      - GITHUB_ID=clientid
      - COOKIE_SALT=replaceme
      - IS_PRODUCTION=TRUE
    depends_on:
      - database
    links:
      - database
  database:
    image: 'postgres:latest'

    ports:
      - 5432

    environment:
      POSTGRES_USER: skadi # The PostgreSQL user (useful to connect to the database)
      POSTGRES_PASSWORD: 123456789 # The PostgreSQL password (useful to connect to the database)
      POSTGRES_DB: skadi-gist # The PostgreSQL default database (automatically created at first launch)
    volumes:
      - ./db/:/var/lib/postgresql/data/
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U skadi" ]
      interval: 5s
      timeout: 5s
      retries: 5
```

### With Treafik Ingress

This example uses [traefik](https://traefik.io) as an ingress handler. Traefik allows for automatic TLS configuration with certificates from
[Let's Encrypt](https://letsencrypt.org). 

docker-compose.yaml:

```
version: '3'

services:
  traefik:
      image: traefik:v2.4
      container_name: traefik
      restart: unless-stopped
      security_opt:
        - no-new-privileges:true
      networks:
        - proxy
      ports:
        - 80:80
        - 443:443
      volumes:
        - /etc/localtime:/etc/localtime:ro
        - /var/run/docker.sock:/var/run/docker.sock:ro
        - ./traefik.yml:/traefik.yml:ro
        - ./traefik/acme/:/data
  skadi-gist:
    image: skadicloud/gist-server
    container_name: skadi-gist
    restart: unless-stopped
    networks:
      - proxy
    security_opt:
      - no-new-privileges:true
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./data:/data
    environment:
      - STORAGE_KIND=directory
      - STORAGE_DIRECTORY=/data
      - SQL_HOST=database
      - SQL_USER=skadi
      - SQL_PASSWORD=123456789
      - SQL_DB=skadi-gist
      - GITHUB_SECRET=addme
      - GITHUB_ID=clientid
      - COOKIE_SALT=replaceme
      - IS_PRODUCTION=TRUE
    depends_on:
      - database
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.skadi.entrypoints=https"
      - "traefik.http.routers.skadi.rule=Host(`<my skadi url>`)"
      - "traefik.http.routers.skadi.tls=true"
      - "traefik.http.routers.skadi.tls.certresolver=myresolver"
      - "traefik.docker.network=proxy"
    links:
      - database
  database:
    image: 'postgres:latest'

    ports:
      - 5432
    networks:
      - proxy

    environment:
      POSTGRES_USER: skadi # The PostgreSQL user (useful to connect to the database)
      POSTGRES_PASSWORD: 123456789 # The PostgreSQL password (useful to connect to the database)
      POSTGRES_DB: skadi-gist # The PostgreSQL default database (automatically created at first launch)
    volumes:
      - ./db/:/var/lib/postgresql/data/
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U skadi" ]
      interval: 5s
      timeout: 5s
      retries: 5

networks:
  proxy:
    external: true
```

treafik.yml:

```
api:
  dashboard: true

entryPoints:
  http:
    address: ":80"
  https:
    address: ":443"

providers:
  docker:
    endpoint: "unix:///var/run/docker.sock"
    exposedByDefault: false

certificatesResolvers:
  myresolver:
    acme:
      email: <your email>
      storage: /data/acme.json
      httpChallenge:
        entryPoint: http
```

## Kubernetes Example

For a kubernets example take a look at the `kubernetes` subfolder of the repository. It contains the production confguration
for skadi-gist. It doesn't include a database deployment because is uses a hosted postgreSQL database. 