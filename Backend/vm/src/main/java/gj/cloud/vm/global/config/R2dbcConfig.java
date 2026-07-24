package gj.cloud.vm.global.config;

import gj.cloud.vm.domain.collab.enums.CollaborationStatus;
import gj.cloud.vm.domain.collab.enums.CollaborationType;
import gj.cloud.vm.domain.collab.enums.ScopeType;
import gj.cloud.vm.domain.org.enums.MemberRole;
import gj.cloud.vm.domain.org.enums.MemberStatus;
import gj.cloud.vm.domain.port.enums.Protocol;
import gj.cloud.vm.domain.port.enums.Visibility;
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
                new PlanTypeToStringConverter(),
                new StringToProtocolConverter(),
                new ProtocolToStringConverter(),
                new StringToVisibilityConverter(),
                new VisibilityToStringConverter(),
                new StringToMemberRoleConverter(),
                new MemberRoleToStringConverter(),
                new StringToMemberStatusConverter(),
                new MemberStatusToStringConverter(),
                new StringToScopeTypeConverter(),
                new ScopeTypeToStringConverter(),
                new StringToCollaborationTypeConverter(),
                new CollaborationTypeToStringConverter(),
                new StringToCollaborationStatusConverter(),
                new CollaborationStatusToStringConverter()
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

    @ReadingConverter
    static class StringToProtocolConverter implements Converter<String, Protocol> {
        public Protocol convert(String source) { return Protocol.valueOf(source); }
    }

    @WritingConverter
    static class ProtocolToStringConverter implements Converter<Protocol, String> {
        public String convert(Protocol source) { return source.name(); }
    }

    @ReadingConverter
    static class StringToVisibilityConverter implements Converter<String, Visibility> {
        public Visibility convert(String source) { return Visibility.valueOf(source); }
    }

    @WritingConverter
    static class VisibilityToStringConverter implements Converter<Visibility, String> {
        public String convert(Visibility source) { return source.name(); }
    }

    @ReadingConverter
    static class StringToMemberRoleConverter implements Converter<String, MemberRole> {
        public MemberRole convert(String source) { return MemberRole.valueOf(source); }
    }

    @WritingConverter
    static class MemberRoleToStringConverter implements Converter<MemberRole, String> {
        public String convert(MemberRole source) { return source.name(); }
    }

    @ReadingConverter
    static class StringToMemberStatusConverter implements Converter<String, MemberStatus> {
        public MemberStatus convert(String source) { return MemberStatus.valueOf(source); }
    }

    @WritingConverter
    static class MemberStatusToStringConverter implements Converter<MemberStatus, String> {
        public String convert(MemberStatus source) { return source.name(); }
    }

    @ReadingConverter
    static class StringToScopeTypeConverter implements Converter<String, ScopeType> {
        public ScopeType convert(String source) { return ScopeType.valueOf(source); }
    }

    @WritingConverter
    static class ScopeTypeToStringConverter implements Converter<ScopeType, String> {
        public String convert(ScopeType source) { return source.name(); }
    }

    @ReadingConverter
    static class StringToCollaborationTypeConverter implements Converter<String, CollaborationType> {
        public CollaborationType convert(String source) { return CollaborationType.valueOf(source); }
    }

    @WritingConverter
    static class CollaborationTypeToStringConverter implements Converter<CollaborationType, String> {
        public String convert(CollaborationType source) { return source.name(); }
    }

    @ReadingConverter
    static class StringToCollaborationStatusConverter implements Converter<String, CollaborationStatus> {
        public CollaborationStatus convert(String source) { return source == null ? null : CollaborationStatus.valueOf(source); }
    }

    @WritingConverter
    static class CollaborationStatusToStringConverter implements Converter<CollaborationStatus, String> {
        public String convert(CollaborationStatus source) { return source == null ? null : source.name(); }
    }
}
