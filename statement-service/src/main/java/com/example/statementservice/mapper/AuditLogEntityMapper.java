package com.example.statementservice.mapper;

import com.example.statementservice.model.dto.AuditLogDto;
import com.example.statementservice.model.entity.AuditLog;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AuditLogEntityMapper {

    @Mappings({
        @Mapping(target = "action", source = "action"),
        @Mapping(target = "ipAddress", expression = "java(extractDetail(entity, \"ip\"))"),
        @Mapping(target = "userAgent", expression = "java(extractDetail(entity, \"userAgent\"))"),
        @Mapping(target = "details", expression = "java(extractReason(entity))")
    })
    AuditLogDto toDto(AuditLog entity);

    List<AuditLogDto> toDtos(List<AuditLog> entities);

    default String extractDetail(AuditLog log, String key) {
        if (log.getDetails() == null) return null;
        var val = log.getDetails().get(key);
        return val == null ? null : String.valueOf(val);
    }

    default Map<String, Object> extractReason(AuditLog log) {
        if (log == null || log.getDetails() == null) {
            return Collections.emptyMap();
        }
        var reason = log.getDetails().get("reason");
        return reason == null ? Collections.emptyMap() : Collections.singletonMap("reason", reason);
    }
}
