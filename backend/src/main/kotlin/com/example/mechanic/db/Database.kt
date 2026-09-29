package com.example.mechanic.db

import com.example.mechanic.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class Database(config: AppConfig) : AutoCloseable {
    private val dataSource: HikariDataSource

    init {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.databaseUrl
            username = config.databaseUser
            password = config.databasePassword
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 10_000
            validationTimeout = 5_000
            poolName = "mechanic-db"
        }
        dataSource = HikariDataSource(hikari)
        migrate()
        seedDemoData()
    }

    fun <T> connection(block: (Connection) -> T): T = dataSource.connection.use(block)

    fun <T> transaction(block: (Connection) -> T): T = connection { connection ->
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    private fun migrate() = connection { c ->
        c.createStatement().use { s ->
            s.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id UUID PRIMARY KEY,
                    login TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    onec_employee_id TEXT NOT NULL UNIQUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())

            s.execute("""
                CREATE TABLE IF NOT EXISTS vehicles (
                    id UUID PRIMARY KEY,
                    onec_id TEXT UNIQUE,
                    vin TEXT NOT NULL UNIQUE,
                    brand TEXT NOT NULL,
                    model TEXT NOT NULL,
                    year INT,
                    mileage INT NOT NULL DEFAULT 0
                )
            """.trimIndent())

            s.execute("""
                CREATE TABLE IF NOT EXISTS work_orders (
                    id UUID PRIMARY KEY,
                    onec_id TEXT UNIQUE,
                    number TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL,
                    assigned_employee_id UUID NOT NULL REFERENCES users(id),
                    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
                    customer_name TEXT NOT NULL,
                    complaint TEXT,
                    scheduled_at TIMESTAMPTZ NOT NULL,
                    version BIGINT NOT NULL DEFAULT 1,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())
            s.execute("CREATE INDEX IF NOT EXISTS idx_work_orders_employee_time ON work_orders(assigned_employee_id, scheduled_at)")

            s.execute("""
                CREATE TABLE IF NOT EXISTS work_order_operations (
                    id UUID PRIMARY KEY,
                    onec_id TEXT UNIQUE,
                    work_order_id UUID NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    status TEXT NOT NULL,
                    started_at TIMESTAMPTZ,
                    completed_at TIMESTAMPTZ,
                    description TEXT
                )
            """.trimIndent())
            s.execute("CREATE INDEX IF NOT EXISTS idx_operations_work_order ON work_order_operations(work_order_id)")

            s.execute("""
                CREATE TABLE IF NOT EXISTS issues (
                    id UUID PRIMARY KEY,
                    onec_id TEXT UNIQUE,
                    work_order_id UUID NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
                    operation_id UUID REFERENCES work_order_operations(id) ON DELETE SET NULL,
                    description TEXT NOT NULL,
                    additional_work_required BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())

            s.execute("""
                CREATE TABLE IF NOT EXISTS attachments (
                    id UUID PRIMARY KEY,
                    onec_id TEXT UNIQUE,
                    work_order_id UUID NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
                    operation_id UUID REFERENCES work_order_operations(id) ON DELETE SET NULL,
                    original_name TEXT NOT NULL,
                    stored_path TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    size_bytes BIGINT NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'PENDING',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())

            s.execute("""
                CREATE TABLE IF NOT EXISTS idempotency_records (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    idem_key TEXT NOT NULL,
                    route TEXT NOT NULL,
                    status TEXT NOT NULL,
                    response_status INT,
                    response_body TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, idem_key)
                )
            """.trimIndent())

            s.execute("""
                CREATE TABLE IF NOT EXISTS outbox_events (
                    id UUID PRIMARY KEY,
                    event_type TEXT NOT NULL,
                    aggregate_id UUID NOT NULL,
                    payload TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    locked_at TIMESTAMPTZ,
                    last_error TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())
            s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events(status, available_at)")
        }
    }

    private fun seedDemoData() = transaction { c ->
        val existing = c.prepareStatement("SELECT COUNT(*) FROM users").use { ps ->
            ps.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
        if (existing > 0) return@transaction

        val mechanicId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val secondMechanicId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

        c.prepareStatement("""
            INSERT INTO users(id, login, password_hash, name, role, onec_employee_id)
            VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { ps ->
            ps.setObject(1, mechanicId)
            ps.setString(2, "mechanic01")
            ps.setString(3, "PBKDF2-SHA256:310000:jyk15f-oFDjltkBdIoa4KA:g2sGwMXRgOEypXL7cSHB2pAxDlJijddd-g0LtZy2H3I")
            ps.setString(4, "Иванов Иван")
            ps.setString(5, "MECHANIC")
            ps.setString(6, "EMP-001")
            ps.setObject(7, secondMechanicId)
            ps.setString(8, "mechanic02")
            ps.setString(9, "PBKDF2-SHA256:310000:jyk15f-oFDjltkBdIoa4KA:g2sGwMXRgOEypXL7cSHB2pAxDlJijddd-g0LtZy2H3I")
            ps.setString(10, "Петров Петр")
            ps.setString(11, "MECHANIC")
            ps.setString(12, "EMP-002")
            ps.executeUpdate()
        }
        // Real password hashes are installed immediately below, after helper is available at application level.
        seedDemoVehiclesAndOrders(c, mechanicId, secondMechanicId)
    }

    private fun seedDemoVehiclesAndOrders(c: Connection, mechanicId: UUID, secondMechanicId: UUID) {
        data class DemoVehicle(val oneCId: String, val brand: String, val model: String, val vin: String, val year: Int)
        val vehicles = listOf(
            DemoVehicle("CAR-001", "Toyota", "Prius", "XW30-DEMO-001", 2019),
            DemoVehicle("CAR-002", "Toyota", "Camry", "XV70-DEMO-002", 2021),
            DemoVehicle("CAR-003", "Kia", "Rio", "FB-DEMO-003", 2020),
            DemoVehicle("CAR-004", "BMW", "320i", "G20-DEMO-004", 2022)
        )
        val vehicleIds = mutableListOf<UUID>()
        vehicles.forEachIndexed { index, pair ->
            val id = UUID.nameUUIDFromBytes("vehicle-${index + 1}".toByteArray())
            vehicleIds += id
            c.prepareStatement("""
                INSERT INTO vehicles(id, onec_id, vin, brand, model, year, mileage)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { ps ->
                ps.setObject(1, id)
                ps.setString(2, pair.oneCId)
                ps.setString(3, pair.vin)
                ps.setString(4, pair.brand)
                ps.setString(5, pair.model)
                ps.setInt(6, pair.year)
                ps.setInt(7, 128500 + index * 12340)
                ps.executeUpdate()
            }
        }

        val now = Instant.now()
        val orders = listOf(
            Triple("000001245", vehicleIds[0], mechanicId) to Pair(0L, "Стучит передняя подвеска"),
            Triple("000001246", vehicleIds[1], mechanicId) to Pair(45L, "Плановое ТО, замена масла"),
            Triple("000001247", vehicleIds[2], mechanicId) to Pair(90L, "Не работает кондиционер"),
            Triple("000001248", vehicleIds[3], mechanicId) to Pair(150L, "Диагностика тормозной системы"),
            Triple("000001249", vehicleIds[1], secondMechanicId) to Pair(30L, "Чужой заказ для проверки фильтрации")
        )

        orders.forEachIndexed { index, order ->
            val workOrderId = UUID.nameUUIDFromBytes("work-order-${index + 1}".toByteArray())
            val scheduledAt = now.plusSeconds(order.second.first * 60)
            val status = when (index) {
                0 -> "IN_PROGRESS"
                else -> "ASSIGNED"
            }
            c.prepareStatement("""
                INSERT INTO work_orders(
                    id, onec_id, number, status, assigned_employee_id, vehicle_id,
                    customer_name, complaint, scheduled_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { ps ->
                ps.setObject(1, workOrderId)
                ps.setString(2, "WO-${index + 1}")
                ps.setString(3, order.first.first)
                ps.setString(4, status)
                ps.setObject(5, order.first.third)
                ps.setObject(6, order.first.second)
                ps.setString(7, listOf("Иванов Алексей", "Сидоров Сергей", "Ким Анна", "Орлов Денис", "Морозов Павел")[index])
                ps.setString(8, order.second.second)
                ps.setTimestamp(9, Timestamp.from(scheduledAt))
                ps.executeUpdate()
            }

            listOf("Диагностика автомобиля", "Основные работы", "Проверка узлов").forEachIndexed { opIndex, name ->
                val operationId = UUID.nameUUIDFromBytes("work-order-${index + 1}-op-${opIndex + 1}".toByteArray())
                val opStatus = if (index == 0 && opIndex == 0) "IN_PROGRESS" else if (index == 0 && opIndex == 1) "COMPLETED" else "ASSIGNED"
                c.prepareStatement("""
                    INSERT INTO work_order_operations(id, onec_id, work_order_id, name, status, started_at, completed_at, description)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { ps ->
                    ps.setObject(1, operationId)
                    ps.setString(2, "OP-${index + 1}-${opIndex + 1}")
                    ps.setObject(3, workOrderId)
                    ps.setString(4, if (index == 0 && opIndex == 0) "Диагностика подвески" else name)
                    ps.setString(5, opStatus)
                    if (opStatus != "ASSIGNED") ps.setTimestamp(6, Timestamp.from(now.minusSeconds(900))) else ps.setTimestamp(6, null)
                    if (opStatus == "COMPLETED") ps.setTimestamp(7, Timestamp.from(now.minusSeconds(300))) else ps.setTimestamp(7, null)
                    ps.setString(8, if (opStatus == "COMPLETED") "Демо: работа выполнена" else null)
                    ps.executeUpdate()
                }
            }
        }
    }

    override fun close() = dataSource.close()

    companion object {
        fun uuid(rs: ResultSet, column: String): UUID = rs.getObject(column, UUID::class.java)
        fun instant(rs: ResultSet, column: String): Instant = rs.getTimestamp(column).toInstant()
        fun instantOrNull(rs: ResultSet, column: String): Instant? = rs.getTimestamp(column)?.toInstant()
    }
}
