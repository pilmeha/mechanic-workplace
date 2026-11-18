package com.example.mechanicworkplace.data.remote.onecmobile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OneCMobileDtosTest {

    @Test
    fun `decodes order list and ignores fields that are not guaranteed`() {
        val payload = """
            {
              "result": {
                "items": [
                  {
                    "id": "6f1c2a3b-0000-0000-0000-000000000001",
                    "number": "00000001234",
                    "date": "2026-11-12T09:00:00",
                    "state": { "code": "0f2e0000-0000-0000-0000-000000000001", "name": "В работе" },
                    "client": { "id": "client-1", "name": "ООО Ромашка" },
                    "vehicle": null,
                    "plannedDate": "2026-11-13T18:00:00",
                    "sumWorks": 12500.00,
                    "posted": false,
                    "worksCount": 3,
                    "hasPhotos": true,
                    "plate": "A123BC",
                    "mileage": 100500
                  }
                ],
                "total": 12,
                "limit": 50,
                "offset": 0
              },
              "error": null
            }
        """.trimIndent()

        val envelope = OneCMobileJson.decodeFromString<ApiEnvelope<PageDto<OrderListItemDto>>>(payload)
        assertNull(envelope.error)

        val page = requireNotNull(envelope.result)
        assertEquals(12, page.total)
        assertEquals(50, page.limit)
        assertEquals(0, page.offset)

        val item = page.items.single()
        assertEquals("00000001234", item.number)
        assertEquals("0f2e0000-0000-0000-0000-000000000001", item.state.code)
        assertEquals("В работе", item.state.name)
        assertEquals("ООО Ромашка", item.client?.name)
        assertNull(item.vehicle)
        assertEquals("12500.00", item.sumWorks?.toPlainString())
        assertFalse(item.posted)
    }

    @Test
    fun `decodes order card with works defects vehicles and executors`() {
        val payload = """
            {
              "result": {
                "id": "order-1",
                "number": "00000001234",
                "date": "2026-11-12T09:00:00",
                "state": { "code": "state-1", "name": "В работе" },
                "posted": false,
                "client": { "id": "client-1", "name": "ООО Ромашка" },
                "summaryOrder": { "id": "summary-1", "number": "00000000456" },
                "vehicles": [
                  { "id": "vehicle-1", "model": "Lada Vesta", "vin": "XTA123" },
                  { "id": "vehicle-2", "model": "Kia Rio", "vin": "FB456" }
                ],
                "description": "Стук в передней подвеске",
                "works": [
                  {
                    "lineId": "line-1",
                    "work": { "id": "work-1", "name": "Замена амортизатора" },
                    "quantity": 2,
                    "normHour": 0.8,
                    "price": 1500.00,
                    "sum": 3000.00,
                    "vatRate": "НДС20",
                    "sumVat": 500.00,
                    "note": "",
                    "executors": [
                      {
                        "employeeId": "employee-1",
                        "name": "Петров П.",
                        "shopId": "shop-1",
                        "shopName": "Цех №1",
                        "percent": 100
                      }
                    ]
                  }
                ],
                "defects": [
                  {
                    "reasonId": "reason-1",
                    "reason": "Износ амортизатора",
                    "content": "Течь масла",
                    "type": "Неисправность",
                    "normHour": 0.5
                  }
                ]
              },
              "error": null
            }
        """.trimIndent()

        val card = requireNotNull(
            OneCMobileJson.decodeFromString<ApiEnvelope<OrderCardDto>>(payload).result
        )

        assertEquals(2, card.vehicles.size)
        assertEquals("Lada Vesta", card.vehicles.first().model)
        assertEquals("00000000456", card.summaryOrder?.number)

        val work = card.works.single()
        assertEquals("line-1", work.lineId)
        assertEquals("work-1", work.work.id)
        assertEquals("2", work.quantity?.toPlainString())
        assertEquals("1500.00", work.price?.toPlainString())
        assertEquals("3000.00", work.sum?.toPlainString())
        assertEquals("НДС20", work.vatRate)
        assertEquals("500.00", work.sumVat?.toPlainString())

        val executor = work.executors.single()
        assertEquals("employee-1", executor.employeeId)
        assertEquals("Цех №1", executor.shopName)
        assertEquals(100, executor.percent)

        val defect = card.defects.single()
        assertEquals("reason-1", defect.reasonId)
        assertEquals("Течь масла", defect.content)
        assertEquals("0.5", defect.normHour?.toPlainString())
    }

    @Test
    fun `decodes post response`() {
        val payload = """
            {
              "result": {
                "posted": true,
                "state": { "code": "state-done", "name": "Выполнен" }
              },
              "error": null
            }
        """.trimIndent()

        val result = requireNotNull(
            OneCMobileJson.decodeFromString<ApiEnvelope<PostResultDto>>(payload).result
        )
        assertTrue(result.posted)
        assertEquals("Выполнен", result.state.name)
    }

    @Test
    fun `decodes photo 201 response`() {
        val payload = """
            {
              "result": {
                "photo": {
                  "id": "photo-1",
                  "fileName": "photo_1.jpg",
                  "size": 245678,
                  "uploadedBy": "Иванов Иван",
                  "uploadedAt": "2026-11-12T10:12:00",
                  "canDelete": true
                }
              },
              "error": null
            }
        """.trimIndent()

        val photo = requireNotNull(
            OneCMobileJson.decodeFromString<ApiEnvelope<UploadPhotoResultDto>>(payload).result
        ).photo
        assertEquals("photo-1", photo.id)
        assertEquals(245678L, photo.size)
        assertTrue(photo.canDelete)
    }

    @Test
    fun `decodes states and defects catalogs`() {
        val states = """
            { "result": { "items": [ { "id": "state-1", "name": "В работе", "order": 10 } ], "total": 1 }, "error": null }
        """.trimIndent()
        val defects = """
            { "result": { "items": [ { "id": "reason-1", "name": "Износ амортизатора", "code": "000123", "type": "Неисправность", "normHour": 0.5 } ], "total": 1 }, "error": null }
        """.trimIndent()

        val state = requireNotNull(
            OneCMobileJson.decodeFromString<ApiEnvelope<ItemsDto<StateCatalogItemDto>>>(states).result
        ).items.single()
        assertEquals("state-1", state.id)
        assertEquals(10, state.order)

        val defect = requireNotNull(
            OneCMobileJson.decodeFromString<ApiEnvelope<ItemsDto<DefectCatalogItemDto>>>(defects).result
        ).items.single()
        assertEquals("000123", defect.code)
        assertEquals("0.5", defect.normHour?.toPlainString())
    }

    @Test
    fun `decodes envelope error without result`() {
        val payload = """
            { "result": null, "error": { "code": "VALIDATION", "message": "Не передан reasonId" } }
        """.trimIndent()

        val envelope = OneCMobileJson.decodeFromString<ApiEnvelope<OrderCardDto>>(payload)
        assertNull(envelope.result)
        assertEquals("VALIDATION", envelope.error?.code)
        assertEquals("Не передан reasonId", envelope.error?.message)
    }

    @Test
    fun `works request keeps lineId and omits nulls and shopId`() {
        val request = SaveWorksRequest(
            works = listOf(
                SaveWorkRowDto(
                    lineId = "line-1",
                    workId = "work-1",
                    quantity = BigDecimal("2"),
                    price = BigDecimal("1500.00"),
                    note = "заменено",
                    executors = listOf(SaveExecutorDto(employeeId = "employee-1", percent = 100))
                ),
                SaveWorkRowDto(
                    lineId = null,
                    workId = "work-2"
                )
            )
        )

        val encoded = OneCMobileJson.encodeToString(SaveWorksRequest.serializer(), request)
        assertTrue(encoded.contains("\"lineId\":\"line-1\""))
        assertTrue(encoded.contains("\"price\":1500.00"))
        assertTrue(encoded.contains("\"quantity\":2"))
        assertTrue(encoded.contains("\"employeeId\":\"employee-1\""))
        assertFalse(encoded.contains("shopId"))
        assertFalse(encoded.contains("shop_id"))
        // New row must not send lineId at all.
        assertTrue(encoded.contains("{\"workId\":\"work-2\"}"))
    }
}
