package systems.choochoo.transit_data_archivers.model;

import com.jerolba.carpet.annotation.ParquetJson;
import org.apache.parquet.io.api.Binary;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Set;

public record FeedContentsRow(
        @NotNull String producer,
        @NotNull String feed,
        @NotNull Instant fetchTime,
        @NotNull Boolean isError,
        String errorMessage,
        Integer responseTimeMillis,
        Integer statusCode,
        String statusMessage,
        String protocol,
        @ParquetJson String responseHeaders,
        Binary responseBody,
        Integer responseBodyLength,
        @ParquetJson String responseContents,
        Set<String> enabledExtensions
) {
}

// This is a Java record rather than a Kotlin data class with the @JvmRecord annotation because of KT-44706:
// https://youtrack.jetbrains.com/issue/KT-44706/KAPT-JvmRecord-causes-Record-is-an-API-that-is-part-of-a-preview-feature
// We tried using `-Xuse-k2-kapt` but that did not resolve the issue.