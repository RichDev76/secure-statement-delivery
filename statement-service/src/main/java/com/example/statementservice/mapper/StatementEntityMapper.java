package com.example.statementservice.mapper;

import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.model.entity.Statement;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StatementEntityMapper {

    @Mapping(target = "statementId", source = "id")
    @Mapping(target = "fileName", source = "uploadFileName")
    @Mapping(target = "fileSize", source = "sizeBytes")
    @Mapping(target = "downloadLink", ignore = true)
    StatementDto toDto(Statement entity);

    List<StatementDto> toDtos(List<Statement> entities);
}
