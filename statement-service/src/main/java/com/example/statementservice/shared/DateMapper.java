package com.example.statementservice.shared;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DateMapper {

    private final Clock clock;

    @Named("toLocalOffset")
    public OffsetDateTime toLocalOffset(OffsetDateTime source) {
        if (source == null) {
            return null;
        }
        return source.atZoneSameInstant(clock.getZone()).toOffsetDateTime();
    }
}
