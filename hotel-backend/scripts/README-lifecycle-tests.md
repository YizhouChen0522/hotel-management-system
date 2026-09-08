# Lifecycle tests

The integration tests use the separately initialized MySQL instance at `localhost:33079`, database `hotel_lifecycle_test`, with data under the workspace's `.lifecycle-test-mysql/data`. They never fall back to the development datasource. This Windows native alternative was used because Docker was unavailable. Verified engine: MySQL 5.7.37.

From `hotel-backend`:

```powershell
./scripts/Start-LifecycleTestMysql.ps1 -MySqlHome D:/MySQL
./mvnw.cmd '-Dhotel.mysql.tests=true' clean test
```

If the wrapper cannot obtain Maven, use your installed Maven executable. The first run may download test dependencies. Ordinary `mvn test` runs unit tests and skips the opt-in MySQL classes. Every Spring test also installs `IsolatedDatabaseGuard` before DataSource/Flyway creation, so accidental `contextLoads` against the development database is blocked.

The suite deletes only fixture rows in its explicitly identified isolated schema. Do not run two copies of the integration suite concurrently against that schema. Test methods themselves orchestrate their concurrent transactions. `SqlGate` uses a latch after a selected SQL lock; competing transactions are released only after observing an actual InnoDB lock wait. Polling is throttled for MySQL 5.7's cached diagnostic tables. Workers must finish before the next fixture cleanup.

RR tests intentionally establish an outer snapshot before a second transaction commits. They assert that an ordinary SUM/COUNT remains stale, then that the repaired current reads produce correct financial totals. Final invariants run in a fresh transaction after worker commits.

The native test instance uses an empty-password test root on loopback. No development credentials are copied. The script does not stop or alter the installed MySQL Windows service. To stop this test instance, verify the port and data directory, then use `D:/MySQL/bin/mysqladmin.exe --no-defaults --protocol=TCP --host=localhost --port=33079 --user=root shutdown`. Its data is retained for the next run.

MySQL 8.x is not verified by this suite: the diagnostic lock-wait query is specifically for 5.7. Before deploying V8 to any existing database, inspect conflicting active rows, null payment keys and broken source ownership; V8 fails rather than reconstructing historical money. MySQL DDL is not transactional, so a failed migration requires an explicitly reviewed recovery procedure. Never use `flyway clean/repair` on development data to make tests pass.
