package gj.cloud.vm.global.config;

import gj.cloud.vm.domain.vm.enums.PlanType;
import gj.cloud.vm.domain.vm.enums.VmStatus;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

import java.util.List;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory) {
        R2dbcDialect dialect = DialectResolver.getDialect(connectionFactory);
        return R2dbcCustomConversions.of(dialect, List.of(
                new StringToVmStatusConverter(),
                new VmStatusToStringConverter(),
                new StringToPlanTypeConverter(),
                new PlanTypeToStringConverter()
        ));
    }

    @ReadingConverter
    static class StringToVmStatusConverter implements Converter<String, VmStatus> {
        public VmStatus convert(String source) { return VmStatus.valueOf(source); }
    }

    @WritingConverter
    static class VmStatusToStringConverter implements Converter<VmStatus, String> {
        public String convert(VmStatus source) { return source.name(); }
    }

    @ReadingConverter
    static class StringToPlanTypeConverter implements Converter<String, PlanType> {
        public PlanType convert(String source) { return PlanType.valueOf(source); }
    }

    @WritingConverter
    static class PlanTypeToStringConverter implements Converter<PlanType, String> {
        public String convert(PlanType source) { return source.name(); }
    }
}
