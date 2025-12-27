package systems.choochoo.transit_data_archivers.njt.modules

import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import dagger.Module
import dagger.Provides
import jakarta.inject.Singleton


@Module
internal class ObjectMapperModule {
    companion object {
        @Provides
        @Singleton
        fun provideObjectMapper(): ObjectMapper = jsonMapper {
            addModule(kotlinModule())
            addModule(ProtobufModule())
            configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            configure(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION, true)
        }
    }
}
