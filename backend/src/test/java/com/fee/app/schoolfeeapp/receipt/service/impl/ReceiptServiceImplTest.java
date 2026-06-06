package com.fee.app.schoolfeeapp.receipt.service.impl;

import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.domain.StudentFee;
import com.fee.app.schoolfeeapp.fee.repository.StudentFeeRepository;
import com.fee.app.schoolfeeapp.notification.service.SmsService;
import com.fee.app.schoolfeeapp.payment.domain.Payment;
import com.fee.app.schoolfeeapp.payment.domain.PaymentAllocation;
import com.fee.app.schoolfeeapp.payment.domain.Receipt;
import com.fee.app.schoolfeeapp.payment.repository.PaymentAllocationRepository;
import com.fee.app.schoolfeeapp.payment.repository.PaymentRepository;
import com.fee.app.schoolfeeapp.payment.repository.ReceiptRepository;
import com.fee.app.schoolfeeapp.receipt.utils.ReceiptPdfGenerator;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceImplTest {

    @Mock
    private ReceiptRepository receiptRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentAllocationRepository allocationRepository;
    @Mock
    private StudentFeeRepository studentFeeRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private SmsService smsService;
    @Mock
    private JwtUtils jwtUtils;

    private ReceiptServiceImpl receiptService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID PARENT_USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID OTHER_PARENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PAYMENT_ID = UUID.fromString("f6a7b890-1234-4567-f123-456789012345");
    private static final UUID STUDENT_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID STUDENT_FEE_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234");
    private static final String RECEIPT_NUMBER = "RCP-2026-ABC123";

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptServiceImpl(
                receiptRepository,
                paymentRepository,
                allocationRepository,
                studentFeeRepository,
                studentRepository,
                schoolRepository,
                new ReceiptPdfGenerator(),
                smsService,
                jwtUtils);
    }

    @Test
    @DisplayName("Should return receipt details for owning parent")
    void shouldReturnReceiptDetailsForOwningParent() {
        stubAuthorizedReceipt(parentUser(), receipt(), payment(PARENT_USER_ID));

        StepVerifier.create(receiptService.getReceiptDetails(" " + RECEIPT_NUMBER + " "))
                .assertNext(response -> {
                    assertThat(response.receiptNumber()).isEqualTo(RECEIPT_NUMBER);
                    assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
                    assertThat(response.schoolName()).isEqualTo("Grace International School");
                    assertThat(response.schoolAddress()).isEqualTo("12 School Road, Lagos, Lagos");
                    assertThat(response.paidBy()).isEqualTo("Ada Parent");
                    assertThat(response.amount()).isEqualByComparingTo("5000");
                    assertThat(response.amountInWords()).isEqualTo("Five Thousand Naira Only");
                    assertThat(response.paymentMethod()).isEqualTo("PAYSTACK");
                    assertThat(response.breakdown()).hasSize(1);
                    assertThat(response.breakdown().getFirst().studentName()).isEqualTo("Ada Lovelace");
                    assertThat(response.smsSent()).isTrue();
                    assertThat(response.emailSent()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject blank receipt number before auth lookup")
    void shouldRejectBlankReceiptNumberBeforeAuthLookup() {
        StepVerifier.create(receiptService.getReceiptDetails("   "))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_RECEIPT_REQUEST");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
        verify(receiptRepository, never()).findByReceiptNumber(anyString());
    }

    @Test
    @DisplayName("Should reject parent reading another payer receipt")
    void shouldRejectParentReadingAnotherPayerReceipt() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(receiptRepository.findByReceiptNumber(RECEIPT_NUMBER)).thenReturn(Mono.just(receipt()));
        when(paymentRepository.findByIdAndSchoolId(PAYMENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(payment(OTHER_PARENT_ID)));

        StepVerifier.create(receiptService.getReceiptDetails(RECEIPT_NUMBER))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();

        verify(schoolRepository, never()).findById(SCHOOL_ID);
    }

    @Test
    @DisplayName("Should reject school-scoped user reading another school receipt")
    void shouldRejectSchoolScopedUserReadingAnotherSchoolReceipt() {
        Receipt otherSchoolReceipt = receipt();
        otherSchoolReceipt.setSchoolId(OTHER_SCHOOL_ID);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(receiptRepository.findByReceiptNumber(RECEIPT_NUMBER)).thenReturn(Mono.just(otherSchoolReceipt));

        StepVerifier.create(receiptService.getReceiptDetails(RECEIPT_NUMBER))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RECEIPT_NOT_IN_SCHOOL");
                })
                .verify();

        verify(paymentRepository, never()).findByIdAndSchoolId(PAYMENT_ID, OTHER_SCHOOL_ID);
    }

    @Test
    @DisplayName("Should return PDF data for authorized receipt")
    void shouldReturnPdfDataForAuthorizedReceipt() {
        stubAuthorizedReceipt(parentUser(), receipt(), payment(PARENT_USER_ID));

        StepVerifier.create(receiptService.downloadReceiptPdf(RECEIPT_NUMBER))
                .assertNext(buffer -> {
                    byte[] bytes = readBytes(buffer);
                    assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
                    assertThat(bytes.length).isGreaterThan(100);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fail when linked payment is missing")
    void shouldFailWhenLinkedPaymentIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(receiptRepository.findByReceiptNumber(RECEIPT_NUMBER)).thenReturn(Mono.just(receipt()));
        when(paymentRepository.findByIdAndSchoolId(PAYMENT_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(receiptService.getReceiptDetails(RECEIPT_NUMBER))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("PAYMENT_NOT_FOUND");
                })
                .verify();
    }

    private void stubAuthorizedReceipt(SchoolFeeUser user, Receipt receipt, Payment payment) {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(user));
        when(receiptRepository.findByReceiptNumber(RECEIPT_NUMBER)).thenReturn(Mono.just(receipt));
        when(paymentRepository.findByIdAndSchoolId(PAYMENT_ID, SCHOOL_ID)).thenReturn(Mono.just(payment));
        when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Mono.just(school()));
        when(allocationRepository.findByPaymentId(PAYMENT_ID))
                .thenReturn(Flux.just(allocation(BigDecimal.valueOf(5000))));
        when(studentFeeRepository.findById(STUDENT_FEE_ID)).thenReturn(Mono.just(studentFee()));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.just(student()));
    }

    private byte[] readBytes(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }

    private Receipt receipt() {
        return Receipt.builder()
                .id(UUID.randomUUID())
                .paymentId(PAYMENT_ID)
                .receiptNumber(RECEIPT_NUMBER)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .amountInWords("Five Thousand Naira Only")
                .paymentDate(Instant.parse("2026-06-05T10:00:00Z"))
                .paymentMethod("PAYSTACK")
                .paidBy(PARENT_USER_ID)
                .paidByName("Ada Parent")
                .generatedBy(PARENT_USER_ID)
                .smsSent(true)
                .emailSent(false)
                .receiptGeneratedAt(Instant.parse("2026-06-05T10:02:00Z"))
                .createdAt(Instant.parse("2026-06-05T10:02:00Z"))
                .build();
    }

    private Payment payment(UUID paidBy) {
        return Payment.builder()
                .id(PAYMENT_ID)
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .paymentMethod("PAYSTACK")
                .paymentMode("ONLINE")
                .status("COMPLETED")
                .paidBy(paidBy)
                .payerName("Ada Parent")
                .createdAt(Instant.parse("2026-06-05T09:55:00Z"))
                .updatedAt(Instant.parse("2026-06-05T10:00:00Z"))
                .build();
    }

    private PaymentAllocation allocation(BigDecimal amount) {
        return PaymentAllocation.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .paymentId(PAYMENT_ID)
                .studentFeeId(STUDENT_FEE_ID)
                .amount(amount)
                .createdAt(Instant.now())
                .build();
    }

    private StudentFee studentFee() {
        return StudentFee.builder()
                .id(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .totalAmount(BigDecimal.valueOf(5000))
                .discountAmount(BigDecimal.ZERO)
                .build();
    }

    private Student student() {
        return Student.builder()
                .id(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .firstName("Ada")
                .lastName("Lovelace")
                .admissionNumber("STU260010")
                .build();
    }

    private School school() {
        return School.builder()
                .id(SCHOOL_ID)
                .name("Grace International School")
                .address("12 School Road")
                .city("Lagos")
                .state("Lagos")
                .build();
    }

    private SchoolFeeUser parentUser() {
        return SchoolFeeUser.builder()
                .userId(PARENT_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("PARENT")
                .roles(Set.of("PARENT"))
                .build();
    }
}
