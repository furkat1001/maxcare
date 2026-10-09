package uz.maxcare.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway

/** Пул соединений и транзакции. JDBC блокирующий, поэтому всё выполняется на [Dispatchers.IO]. */
class Db(private val dataSource: DataSource) {

    /** Коммит при нормальном завершении [block], откат при любом исключении. */
    suspend fun <T> tx(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            var committed = false
            try {
                val result = block(connection)
                connection.commit()
                committed = true
                result
            } finally {
                if (!committed) connection.rollback()
            }
        }
    }

    companion object {
        /** Создаёт пул и применяет миграции из `db/migration`. */
        fun connect(url: String, user: String, password: String): HikariDataSource {
            val dataSource = HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = url
                    username = user
                    this.password = password
                    maximumPoolSize = MAX_POOL_SIZE
                }
            )
            Flyway.configure().dataSource(dataSource).load().migrate()
            return dataSource
        }

        private const val MAX_POOL_SIZE = 10
    }
}

fun Connection.update(sql: String, vararg params: Any?): Int =
    prepareStatement(sql).use { it.bind(params).executeUpdate() }

fun <T> Connection.queryOne(sql: String, vararg params: Any?, map: (ResultSet) -> T): T? =
    prepareStatement(sql).use { statement ->
        statement.bind(params).executeQuery().use { rows -> if (rows.next()) map(rows) else null }
    }

fun <T> Connection.queryList(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        statement.bind(params).executeQuery().use { rows ->
            buildList { while (rows.next()) add(map(rows)) }
        }
    }

fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)

fun ResultSet.instantOrNull(column: String): Instant? =
    getObject(column, java.time.OffsetDateTime::class.java)?.toInstant()

private fun PreparedStatement.bind(params: Array<out Any?>): PreparedStatement {
    params.forEachIndexed { index, value ->
        // Время — всегда в UTC: столбцы timestamptz, драйвер принимает OffsetDateTime.
        val jdbcValue = (value as? Instant)?.atOffset(ZoneOffset.UTC) ?: value
        setObject(index + 1, jdbcValue)
    }
    return this
}
