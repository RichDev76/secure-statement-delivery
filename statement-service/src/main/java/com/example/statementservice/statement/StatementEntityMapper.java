package com.example.statementservice.statement;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StatementEntityMapper {

    @Mapping(target = "statementId", source = "id")
    @Mapping(target = "fileName", source = "uploadFileName")
    @Mapping(target = "fileSize", source = "sizeBytes")
    @Mapping(target = "downloadLink", ignore = true)
    StatementDto toDto(Statement entity);
}
