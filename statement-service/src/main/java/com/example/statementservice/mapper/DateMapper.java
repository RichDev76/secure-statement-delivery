package com.example.statementservice.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class DateMapper {

    @Named("toLocalOffset")
    public OffsetDateTime toLocalOffset(OffsetDateTime source) {
        if (source == null) {
            return null;
        }
        return source.atZoneSameInstant(ZoneId.of("Africa/Johannesburg")).toOffsetDateTime();
    }
}
