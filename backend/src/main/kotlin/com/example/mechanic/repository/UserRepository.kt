package com.example.mechanic.repository

import com.example.mechanic.db.Database
import com.example.mechanic.model.UserRecord
import java.sql.ResultSet
import java.util.UUID

class UserRepository(private val db: Database) {
    fun findByLogin(login: String): UserRecord? = db.connection { c ->
        c.prepareStatement("""
            SELECT id, login, password_hash, name, role, onec_employee_id
            FROM users WHERE login = ?
        """.trimIndent()).use { ps ->
            ps.setString(1, login)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    fun findById(id: UUID): UserRecord? = db.connection { c -> findById(c, id) }

    fun findById(c: java.sql.Connection, id: UUID): UserRecord? =
        c.prepareStatement("SELECT id, login, password_hash, name, role, onec_employee_id FROM users WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }

    private fun ResultSet.toUser() = UserRecord(
        id = getObject("id", UUID::class.java),
        login = getString("login"),
        passwordHash = getString("password_hash"),
        name = getString("name"),
        role = getString("role"),
        oneCEmployeeId = getString("onec_employee_id")
    )
}
