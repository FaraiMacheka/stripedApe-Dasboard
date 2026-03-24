# stripedApe-Dasboard

Spring Boot dashboard for browsing and managing multiple PostgreSQL databases with RBAC, generic CRUD, and CSV export.

## Default logins

- `admin` / `Admin@1234!`
- `manager` / `Manager@1234!`
- `viewer` / `Viewer@1234!`

## Seed databases

The app seeds these PostgreSQL connections on startup:

- `amayezi`
- `fundisaflow`
- `fixdesk`
- `ukuqokelela`

## Notes

- The admin metadata store uses H2 at `./data/stripedape-admin`.
- PostgreSQL JDBC URLs are normalized from `?schema=public` to `?currentSchema=public`.
- Use the Databases screen to add another connection.
- RBAC roles:
  - `ADMIN` can manage databases and users.
  - `MANAGER` can edit rows.
  - `VIEWER` can read and search.

## Run

Use your local Gradle installation or IDE run configuration targeting StripedApeDashboardApplication.
The app listens on port 8090.

If you want, I can add a Gradle wrapper next so the project boots with `./gradlew`.

