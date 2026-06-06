package com.fee.app.schoolfeeapp.reporting.service.impl;

import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.payment.domain.Payment;
import com.fee.app.schoolfeeapp.payment.repository.PaymentRepository;
import com.fee.app.schoolfeeapp.reporting.dto.response.DailySummaryResponse;
import com.fee.app.schoolfeeapp.reporting.utils.ReportPdfGenerator;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TermRepository termRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private ReportPdfGenerator pdfGenerator;

    private ReportServiceImpl reportService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID TERM_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID CLASS_ID = UUID.fromString("e4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID PAYMENT_ID = UUID.fromString("f4e5f6a7-b890-1234-def1-234567890123");

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                paymentRepository,
                termRepository,
                jwtUtils,
                pdfGenerator);
    }

    @Test
    @DisplayName("Should generate CSV fee collection report with normalized filters")
    void shouldGenerateCsvFeeCollectionReportWithNormalizedFilters() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(currentTerm()));
        when(paymentRepository.findFeeCollectionReportPayments(
                SCHOOL_ID, TERM_ID, CLASS_ID, "COMPLETED"))
                .thenReturn(Flux.just(payment(
                        PAYMENT_ID,
                        BigDecimal.valueOf(1500),
                        "paystack",
                        "COMPLETED",
                        "First term, tuition").build()));

        StepVerifier.create(reportService.generateFeeCollectionReport(
                        " current ", CLASS_ID.toString(), " completed ", " csv "))
                .assertNext(buffer -> {
                    String csv = bufferToString(buffer);
                    assertThat(csv).contains("Payment ID,Date,Amount,Method,Status,Narration");
                    assertThat(csv).contains(PAYMENT_ID.toString());
                    assertThat(csv).contains("PAYSTACK");
                    assertThat(csv).contains("\"First term, tuition\"");
                })
                .verifyComplete();

        verify(paymentRepository).findFeeCollectionReportPayments(
                SCHOOL_ID, TERM_ID, CLASS_ID, "COMPLETED");
    }

    @Test
    @DisplayName("Should reject unsupported report format before auth lookup")
    void shouldRejectUnsupportedReportFormatBeforeAuthLookup() {
        StepVerifier.create(reportService.generateFeeCollectionReport(
                        "current", null, "COMPLETED", "xlsx"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_REPORT_FORMAT");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("format");
                })
                .verify();

        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("Should reject invalid class ID before auth lookup")
    void shouldRejectInvalidClassIdBeforeAuthLookup() {
        StepVerifier.create(reportService.generateFeeCollectionReport(
                        "current", "not-a-uuid", "COMPLETED", "CSV"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_REPORT_FILTER");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("classId");
                })
                .verify();

        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("Should use school-scoped term lookup for explicit term ID")
    void shouldUseSchoolScopedTermLookupForExplicitTermId() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(reportService.generateFeeCollectionReport(
                        TERM_ID.toString(), null, null, "CSV"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();

        verify(termRepository).findByIdAndSchoolId(TERM_ID, SCHOOL_ID);
        verify(paymentRepository, never()).findFeeCollectionReportPayments(
                SCHOOL_ID, TERM_ID, null, "COMPLETED");
    }

    @Test
    @DisplayName("Should reject daily summary when start date is after end date")
    void shouldRejectDailySummaryWhenStartDateAfterEndDate() {
        StepVerifier.create(reportService.getDailySummary(
                        LocalDate.parse("2026-06-03"),
                        LocalDate.parse("2026-06-01")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_REPORT_DATE_RANGE");
                })
                .verify();

        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("Should build daily summary with zero days and method totals")
    void shouldBuildDailySummaryWithZeroDaysAndMethodTotals() {
        LocalDate startDate = LocalDate.parse("2026-06-01");
        LocalDate endDate = LocalDate.parse("2026-06-03");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(paymentRepository.findBySchoolIdAndCreatedAtBetweenAndStatus(
                SCHOOL_ID,
                startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
                endDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().minusNanos(1),
                "COMPLETED"))
                .thenReturn(Flux.just(
                        payment(UUID.randomUUID(), BigDecimal.valueOf(1000), "PAYSTACK", "COMPLETED",
                                "June 1").createdAt(Instant.parse("2026-06-01T10:00:00Z")).build(),
                        payment(UUID.randomUUID(), BigDecimal.valueOf(500), null, "COMPLETED",
                                "June 3").createdAt(Instant.parse("2026-06-03T12:00:00Z")).build()));

        StepVerifier.create(reportService.getDailySummary(startDate, endDate))
                .assertNext(response -> {
                    assertThat(response.totalCollected()).isEqualByComparingTo("1500");
                    assertThat(response.totalTransactions()).isEqualTo(2);
                    assertThat(response.byPaymentMethod()).containsKeys("PAYSTACK", "OTHER");
                    assertThat(response.dailyBreakdown()).hasSize(3);
                    assertThat(response.dailyBreakdown().get(0).amount()).isEqualByComparingTo("1000");
                    assertThat(response.dailyBreakdown().get(1).amount()).isEqualByComparingTo("0");
                    assertThat(response.dailyBreakdown().get(2).amount()).isEqualByComparingTo("500");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should require school context for reports")
    void shouldRequireSchoolContextForReports() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(USER_ID)
                .roles(Set.of("SCHOOL_ADMIN"))
                .userType("SCHOOL_ADMIN")
                .build()));

        StepVerifier.create(reportService.getDailySummary(
                        LocalDate.parse("2026-06-01"),
                        LocalDate.parse("2026-06-01")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
                })
                .verify();
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .roles(Set.of("SCHOOL_ADMIN"))
                .userType("SCHOOL_ADMIN")
                .build();
    }

    private Term currentTerm() {
        return Term.builder()
                .id(TERM_ID)
                .sessionId(UUID.randomUUID())
                .name("First Term")
                .isCurrent(true)
                .build();
    }

    private Payment.PaymentBuilder payment(
            UUID paymentId,
            BigDecimal amount,
            String paymentMethod,
            String status,
            String narration) {
        return Payment.builder()
                .id(paymentId)
                .schoolId(SCHOOL_ID)
                .studentId(UUID.randomUUID())
                .studentFeeId(UUID.randomUUID())
                .amount(amount)
                .paymentMethod(paymentMethod)
                .status(status)
                .narration(narration)
                .createdAt(Instant.parse("2026-06-01T09:00:00Z"));
    }

    private String bufferToString(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
