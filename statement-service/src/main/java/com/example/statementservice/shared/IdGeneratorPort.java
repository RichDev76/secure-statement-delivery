package com.example.statementservice.shared;

import java.util.UUID;

public interface IdGeneratorPort {

    UUID newId();
}
