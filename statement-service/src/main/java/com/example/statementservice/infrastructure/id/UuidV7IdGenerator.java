package com.example.statementservice.infrastructure.id;

import com.example.statementservice.shared.IdGeneratorPort;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidV7IdGenerator implements IdGeneratorPort {

    @Override
    public UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
