package com.example.statementservice.shared;

import java.util.UUID;

// Lets infrastructure reference a domain object by id without reflection or printing its contents.
public interface Identified {

    UUID getId();
}
