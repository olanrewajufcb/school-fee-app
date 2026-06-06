package com.fee.app.schoolfeeapp.common.config.converters;


import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(DatabaseClient databaseClient,
                                                         JsonNodeReadingConverter readingConverter,
                                                         JsonNodeWritingConverter writingConverter) {
        var dialect = DialectResolver.getDialect(databaseClient.getConnectionFactory());
        List<Object> converters = new ArrayList<>();
        converters.add(readingConverter);
        converters.add(writingConverter);
        return R2dbcCustomConversions.of(dialect, converters);
    }
}
