# stripedApe-Dasboard

Spring Boot dashboard for browsing and managing multiple PostgreSQL databases with RBAC, generic CRUD, and CSV export.

## Requirements

- Java 17

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

From PowerShell in the project root:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17.0.12'
$env:GRADLE_USER_HOME="$PWD\.gradle-home"
.\gradlew.bat --no-daemon bootRun
```

The app listens on port `8090`.

Open:

- `http://localhost:8090/login`

## Troubleshooting

If startup fails with an H2 error like `The write format 1 is smaller than the supported format 2`, rename or delete the local H2 files and start again:

```powershell
Rename-Item .\data\stripedape-admin.mv.db stripedape-admin.mv.db.bak
Rename-Item .\data\stripedape-admin.trace.db stripedape-admin.trace.db.bak
.\gradlew.bat --no-daemon bootRun
```

