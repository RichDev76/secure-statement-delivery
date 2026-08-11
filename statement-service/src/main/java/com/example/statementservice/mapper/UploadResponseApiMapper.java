package com.example.statementservice.mapper;

import com.example.statementservice.model.api.UploadResponse;
import com.example.statementservice.model.dto.UploadResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = {DateMapper.class})
public interface UploadResponseApiMapper {
    @Mapping(target = "uploadedAt", source = "uploadedAt", qualifiedByName = "toLocalOffset")
    UploadResponse toApi(UploadResponseDto dto);
}
