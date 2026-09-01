package com.example.statementservice.statement.infrastructure;

import com.example.statementservice.model.api.BaseStatement;
import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.shared.DateMapper;
import com.example.statementservice.statement.StatementDto;
import java.net.URI;
import java.util.List;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.ReportingPolicy;

// unmappedTargetPolicy = ERROR: toApi(BaseStatement, URI) relies on implicit name-based matching
// for every field except downloadLink - this turns a future field added to either generated model
// without a corresponding mapping into a build failure instead of a silently-dropped value.
@Mapper(
        componentModel = "spring",
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = {DateMapper.class})
public interface StatementApiMapper {

    // StatementSummary is BaseStatement's fields plus downloadLink - not a Java subtype of
    // BaseStatement despite the OpenAPI allOf composition (codegen produces flat, independent
    // classes) - so this maps through toBase() rather than repeating the shared field list.
    default StatementSummary toApi(StatementDto dto) {
        if (dto == null) {
            return null;
        }
        return toApi(toBase(dto), dto.downloadLink());
    }

    @Mapping(target = "downloadLink", source = "downloadLink")
    StatementSummary toApi(BaseStatement base, URI downloadLink);

    @Mappings({
        @Mapping(target = "statementId", source = "statementId"),
        @Mapping(target = "accountNumber", source = "accountNumber"),
        @Mapping(target = "uploadedAt", source = "uploadedAt", qualifiedByName = "toLocalOffset"),
        @Mapping(target = "fileSize", source = "fileSize"),
        @Mapping(target = "fileName", source = "fileName"),
        @Mapping(
                target = "date",
                expression = "java(dto.statementDate() != null ? dto.statementDate().toString() : null)")
    })
    BaseStatement toBase(StatementDto dto);

    List<BaseStatement> toBases(List<StatementDto> dtos);
}
