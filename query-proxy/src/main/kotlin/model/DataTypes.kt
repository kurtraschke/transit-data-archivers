package systems.choochoo.transit_data_archivers.query_proxy.model

import kotlinx.datetime.LocalDate
import kotlin.reflect.KClass

@Suppress("unused")
enum class DataTypes(val typeClass: KClass<*>) {
    STRING(String::class),
    BOOLEAN(Boolean::class),
    UINT8(UByte::class),
    UINT16(UShort::class),
    UINT32(UInt::class),
    UINT64(ULong::class),
    INT8(Byte::class),
    INT16(Short::class),
    INT32(Int::class),
    INT64(Long::class),
    DATE(LocalDate::class),
}