package com.example.statementservice.controller;

import com.example.statementservice.api.AdminApi;
import com.example.statementservice.mapper.UploadResponseApiMapper;
import com.example.statementservice.model.api.UploadResponse;
import com.example.statementservice.service.StatementUploadService;
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

    @Override
    public ResponseEntity<UploadResponse> uploadStatement(
            String xMessageDigest, MultipartFile file, String accountNumber, String date, String xCorrelationId) {
        var dto = this.statementUploadService.upload(xMessageDigest, file, accountNumber, date);
        var api = uploadResponseApiMapper.toApi(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(api);
    }
}
