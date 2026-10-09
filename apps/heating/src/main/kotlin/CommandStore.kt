import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID

/** Owns JDBC and maps rows into domain commands. */
class CommandStore(private val databaseUrl: String) {
    private fun open() = DriverManager.getConnection(databaseUrl, "heating", "heating")

    fun checkHealth() {
        open().use { it.createStatement().use { query -> query.execute("SELECT 1") } }
    }

    private fun read(row: ResultSet) =
        Command(
            row.getObject("id", UUID::class.java),
            row.getObject("idempotency_key", UUID::class.java),
            row.getInt("device_id"),
            row.getBoolean("desired"),
            row.getString("status"),
            row.getTimestamp("created_at").toInstant().toString(),
            row.getTimestamp("updated_at").toInstant().toString(),
        )

    private fun findBy(column: String, id: UUID): Command? =
        open().use { connection ->
            connection.prepareStatement("SELECT * FROM commands WHERE $column=?").use { query ->
                query.setObject(1, id)
                query.executeQuery().use { row -> if (row.next()) read(row) else null }
            }
        }

    fun find(id: UUID) = findBy("id", id)

    fun findByKey(key: UUID) = findBy("idempotency_key", key)

    fun create(device: Int, key: UUID, enabled: Boolean): Command? {
        val id = UUID.randomUUID()
        val inserted =
            open().use { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO commands(id,idempotency_key,device_id,desired,status) VALUES(?,?,?,?,'UNKNOWN') ON CONFLICT DO NOTHING"
                    )
                    .use { query ->
                        query.setObject(1, id)
                        query.setObject(2, key)
                        query.setInt(3, device)
                        query.setBoolean(4, enabled)
                        query.executeUpdate()
                    }
            }
        return if (inserted == 1) find(id) else null
    }

    fun saveResult(id: UUID, status: String): Command {
        open().use { connection ->
            connection
                .prepareStatement("UPDATE commands SET status=?,updated_at=now() WHERE id=?")
                .use { query ->
                    query.setString(1, status)
                    query.setObject(2, id)
                    query.executeUpdate()
                }
        }
        return find(id)!!
    }
}
