package com.fee.app.schoolfeeapp.school.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;


@Data
@Builder
@Table("school.schools")
public class School {

    @Id
    private UUID id;

    private String name;
    private String code;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;

    @Column("country")
    private String country;

    @Column("logo_url")
    private String logoUrl;

    @Column("payment_config")
    private JsonNode paymentConfig;

    @Column("sms_config")
    private JsonNode smsConfig;

    @Column("term_config")
    private JsonNode termConfig;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    @Column("version")
    private Integer version;

    }
