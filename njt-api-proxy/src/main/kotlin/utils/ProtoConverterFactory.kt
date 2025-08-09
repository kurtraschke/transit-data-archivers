package systems.choochoo.transit_data_archivers.njt.utils

import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Type


internal class ProtoConverterFactory private constructor(private val registry: ExtensionRegistryLite?) : Converter.Factory() {
    companion object {
        fun create(): ProtoConverterFactory {
            return ProtoConverterFactory(null)
        }

        fun createWithRegistry(registry: ExtensionRegistryLite?): ProtoConverterFactory {
            return ProtoConverterFactory(registry)
        }
    }

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation?>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        if (type !is Class<*> || !MessageLite::class.java.isAssignableFrom(type)) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val parser =
            try {
                type.getDeclaredMethod("parser").invoke(null) as Parser<MessageLite>
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e.cause)
            }

        return ProtoResponseBodyConverter(parser, registry)
    }
}


internal class ProtoResponseBodyConverter<T : MessageLite>(
    private val parser: Parser<T>,
    private val registry: ExtensionRegistryLite?
) : Converter<ResponseBody, T?> {

    override fun convert(value: ResponseBody): T? {
        return try {
            if (registry == null) {
                parser.parsePartialFrom(value.byteStream())
            } else {
                parser.parsePartialFrom(value.byteStream(), registry)
            }
        } catch (e: InvalidProtocolBufferException) {
            throw RuntimeException(e)
        } finally {
            value.close()
        }
    }
}