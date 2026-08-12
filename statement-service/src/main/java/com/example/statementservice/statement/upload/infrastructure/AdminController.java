package com.example.statementservice.statement.upload.infrastructure;

import com.example.statementservice.api.AdminApi;
import com.example.statementservice.infrastructure.web.RequestInfoProvider;
import com.example.statementservice.model.api.UploadResponse;
import com.example.statementservice.statement.upload.StatementUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class AdminController implements AdminApi {

    private final StatementUploadService statementUploadService;
    private final UploadResponseApiMapper uploadResponseApiMapper;
    private final RequestInfoProvider requestInfoProvider;

    @Override
    public ResponseEntity<UploadResponse> uploadStatement(
            String xMessageDigest, MultipartFile file, String accountNumber, String date, String xCorrelationId) {
        var dto = this.statementUploadService.upload(
                xMessageDigest, file, accountNumber, date, requestInfoProvider.get());
        var api = uploadResponseApiMapper.toApi(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(api);
    }
}
