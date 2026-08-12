package com.example.statementservice.statement.upload.infrastructure;

import com.example.statementservice.model.api.UploadResponse;
import com.example.statementservice.shared.DateMapper;
import com.example.statementservice.statement.upload.UploadResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = {DateMapper.class})
public interface UploadResponseApiMapper {
    @Mapping(target = "uploadedAt", source = "uploadedAt", qualifiedByName = "toLocalOffset")
    UploadResponse toApi(UploadResponseDto dto);
}
