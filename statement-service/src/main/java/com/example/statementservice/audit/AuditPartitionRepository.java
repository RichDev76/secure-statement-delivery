package com.example.statementservice.audit;

public interface AuditPartitionRepository {

    void createUpcomingPartitions(int monthsAhead);

    int countDefaultPartitionRows();
}
