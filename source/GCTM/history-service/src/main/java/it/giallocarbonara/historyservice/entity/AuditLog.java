package it.giallocarbonara.historyservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.time.Instant;

@Document(indexName = "system_logs")
public record AuditLog(
        @Id String id,
        @Field(type = FieldType.Date) Instant timestamp,
        @Field(type = FieldType.Keyword) String type,
        @Field(type = FieldType.Keyword) String correlationId,
        @Field(type = FieldType.Object) Object payload
) {}