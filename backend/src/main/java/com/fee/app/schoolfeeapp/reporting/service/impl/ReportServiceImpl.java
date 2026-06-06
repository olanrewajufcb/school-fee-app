package com.fee.app.schoolfeeapp.reporting.service.impl;


import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.payment.domain.Payment;
import com.fee.app.schoolfeeapp.payment.repository.PaymentRepository;
import com.fee.app.schoolfeeapp.reporting.dto.FeeCollectionReportData;
import com.fee.app.schoolfeeapp.reporting.dto.response.DailySummaryResponse;
import com.fee.app.schoolfeeapp.reporting.service.ReportService;
import com.fee.app.schoolfeeapp.reporting.utils.ReportPdfGenerator;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
class ReportServiceImpl implements ReportService {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("PDF", "CSV");
    private static final Set<String> SUPPORTED_PAYMENT_STATUSES =
            Set.of("PENDING", "COMPLETED", "FAILED", "CANCELLED");
    private static final String CURRENT_TERM = "current";
    private static final String DEFAULT_REPORT_STATUS = "COMPLETED";
    private static final ZoneId REPORT_ZONE = ZoneId.systemDefault();
    private static final DefaultDataBufferFactory DATA_BUFFER_FACTORY =
            new DefaultDataBufferFactory();

    private final PaymentRepository paymentRepository;
    private final TermRepository termRepository;
    private final JwtUtils jwtUtils;
    private final ReportPdfGenerator pdfGenerator;

    @Override
    public Mono<DataBuffer> generateFeeCollectionReport(
            String termId, String classId, String status, String format) {

        return Mono.fromCallable(() -> validateFeeCollectionFilters(termId, classId, status, format))
                .flatMap(filters -> jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user.getSchoolId());

                    return resolveTerm(filters.termId(), schoolId)
                            .flatMap(term -> buildFeeCollectionData(
                                    schoolId,
                                    term.getId(),
                                    filters.classId(),
                                    filters.status()))
                            .map(reportData -> DATA_BUFFER_FACTORY.wrap(
                                    generateReportBytes(reportData, schoolId, filters.format())));
                }));
    }

    @Override
    public Mono<DailySummaryResponse> getDailySummary(LocalDate startDate, LocalDate endDate) {
        return Mono.fromCallable(() -> validateDateRange(startDate, endDate))
                .flatMap(range -> jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user.getSchoolId());

                    Instant start = range.startDate().atStartOfDay(REPORT_ZONE).toInstant();
                    Instant end = range.endDate().plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

                    return paymentRepository
                            .findBySchoolIdAndCreatedAtBetweenAndStatus(
                                    schoolId, start, end.minusNanos(1), DEFAULT_REPORT_STATUS)
                            .collectList()
                            .map(payments -> buildDailySummary(
                                    payments, range.startDate(), range.endDate()));
                }));
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private Mono<Term> resolveTerm(String termId, UUID schoolId) {
        if (CURRENT_TERM.equalsIgnoreCase(termId)) {
            return termRepository.findCurrentTermsBySchoolId(schoolId)
                    .next()
                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                            "NO_CURRENT_TERM", "No current term found")));
        }
        return termRepository.findByIdAndSchoolId(UUID.fromString(termId), schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "TERM_NOT_FOUND", "Term not found: " + termId)));
    }

    private Mono<FeeCollectionReportData> buildFeeCollectionData(
            UUID schoolId, UUID termId, UUID classId, String status) {

        return paymentRepository
                .findFeeCollectionReportPayments(schoolId, termId, classId, status)
                .collectList()
                .map(payments -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("schoolId", schoolId);
                    data.put("termId", termId);
                    data.put("classId", classId);
                    data.put("status", status);
                    data.put("generatedAt", Instant.now());
                    data.put("totalPayments", payments.size());
                    data.put("totalAmount", payments.stream()
                            .map(this::amountOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    data.put("byMethod", payments.stream()
                            .collect(Collectors.groupingBy(
                                    this::paymentMethodOf,
                                    Collectors.counting())));
                    data.put("payments", payments);
                    return new FeeCollectionReportData(data);
                });
    }

    private DailySummaryResponse buildDailySummary(
            List<Payment> payments, LocalDate startDate, LocalDate endDate) {

        BigDecimal totalCollected = payments.stream()
                .map(this::amountOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, DailySummaryResponse.PaymentMethodSummary> byMethod = payments.stream()
                .collect(Collectors.groupingBy(
                        this::paymentMethodOf,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> new DailySummaryResponse.PaymentMethodSummary(
                                        list.stream()
                                                .map(this::amountOf)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                                        list.size())
                        )));

        Map<LocalDate, List<Payment>> byDate = payments.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCreatedAt() != null
                                ? p.getCreatedAt().atZone(REPORT_ZONE).toLocalDate()
                                : startDate));

        List<DailySummaryResponse.DailyBreakdown> dailyBreakdown =
                startDate.datesUntil(endDate.plusDays(1))
                        .map(date -> {
                            List<Payment> dailyPayments = byDate.getOrDefault(date, List.of());
                            return new DailySummaryResponse.DailyBreakdown(
                                    date,
                                    dailyPayments.stream()
                                            .map(this::amountOf)
                                            .reduce(BigDecimal.ZERO, BigDecimal::add),
                                    dailyPayments.size());
                        })
                .toList();

        return new DailySummaryResponse(
                new DailySummaryResponse.PeriodInfo(startDate, endDate),
                totalCollected,
                payments.size(),
                byMethod,
                dailyBreakdown);
    }

    private byte[] generateReportBytes(
            FeeCollectionReportData reportData, UUID schoolId, String format) {
        if ("PDF".equals(format)) {
            return pdfGenerator.generateFeeCollectionPdf(reportData, schoolId.toString());
        }
        return generateCsv(reportData);
    }

    private byte[] generateCsv(FeeCollectionReportData data) {
        StringBuilder csv = new StringBuilder();
        csv.append("Payment ID,Date,Amount,Method,Status,Narration\n");

        @SuppressWarnings("unchecked")
        List<Payment> payments = (List<Payment>) data.data().get("payments");
        if (payments != null) {
            for (Payment p : payments) {
                csv.append(String.join(",",
                        csvValue(p.getId()),
                        csvValue(p.getCreatedAt()),
                        csvValue(amountOf(p)),
                        csvValue(paymentMethodOf(p)),
                        csvValue(p.getStatus()),
                        csvValue(p.getNarration())))
                        .append('\n');
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private ReportFilters validateFeeCollectionFilters(
            String termId, String classId, String status, String format) {
        String normalizedTermId = (termId == null || termId.isBlank())
                ? CURRENT_TERM
                : termId.trim();
        if (!CURRENT_TERM.equalsIgnoreCase(normalizedTermId)) {
            parseUuid(normalizedTermId, "termId", "INVALID_REPORT_FILTER");
        } else {
            normalizedTermId = CURRENT_TERM;
        }

        UUID parsedClassId = null;
        if (classId != null && !classId.isBlank()) {
            parsedClassId = parseUuid(classId.trim(), "classId", "INVALID_REPORT_FILTER");
        }

        String normalizedStatus = status == null || status.isBlank()
                ? DEFAULT_REPORT_STATUS
                : status.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_PAYMENT_STATUSES.contains(normalizedStatus)) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_FILTER",
                    "Unsupported payment status: " + status,
                    "status");
        }

        String normalizedFormat = format == null || format.isBlank()
                ? "PDF"
                : format.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(normalizedFormat)) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_FORMAT",
                    "Unsupported report format: " + format,
                    "format");
        }

        return new ReportFilters(
                normalizedTermId,
                parsedClassId,
                normalizedStatus,
                normalizedFormat);
    }

    private DateRange validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_DATE_RANGE",
                    "Start date is required",
                    "startDate");
        }
        if (endDate == null) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_DATE_RANGE",
                    "End date is required",
                    "endDate");
        }
        if (startDate.isAfter(endDate)) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_DATE_RANGE",
                    "Start date cannot be after end date",
                    "startDate");
        }
        return new DateRange(startDate, endDate);
    }

    private UUID requireSchoolId(UUID schoolId) {
        if (schoolId == null) {
            throw new SchoolFeeException(
                    "SCHOOL_CONTEXT_REQUIRED",
                    "A school context is required to generate reports");
        }
        return schoolId;
    }

    private UUID parseUuid(String value, String field, String code) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new SchoolFeeException(
                    code,
                    "Invalid " + field + ": " + value,
                    field,
                    e);
        }
    }

    private BigDecimal amountOf(Payment payment) {
        return payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
    }

    private String paymentMethodOf(Payment payment) {
        String method = payment.getPaymentMethod();
        return method == null || method.isBlank() ? "OTHER" : method.trim().toUpperCase(Locale.ROOT);
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String raw = String.valueOf(value);
        boolean needsQuoting = Stream.of(",", "\"", "\n", "\r")
                .anyMatch(raw::contains);
        if (!needsQuoting) {
            return raw;
        }
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    private record ReportFilters(
            String termId,
            UUID classId,
            String status,
            String format) {
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

}
