package com.fee.app.schoolfeeapp.fee.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.domain.FeeStructure;
import com.fee.app.schoolfeeapp.fee.domain.FeeStructureClass;
import com.fee.app.schoolfeeapp.fee.domain.FeeStructureItem;
import com.fee.app.schoolfeeapp.fee.domain.LedgerEntry;
import com.fee.app.schoolfeeapp.fee.domain.StudentFee;
import com.fee.app.schoolfeeapp.fee.dto.request.CreateFeeStructureRequest;
import com.fee.app.schoolfeeapp.fee.dto.response.FeeDashboardResponse;
import com.fee.app.schoolfeeapp.fee.repository.FeeCategoryRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeReportingRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeStructureClassRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeStructureItemRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeStructureRepository;
import com.fee.app.schoolfeeapp.fee.repository.LedgerEntryRepository;
import com.fee.app.schoolfeeapp.fee.repository.StudentFeeRepository;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.SchoolStudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeServiceImplTest {

    @Mock
    private FeeStructureRepository structureRepository;
    @Mock
    private FeeStructureItemRepository itemRepository;
    @Mock
    private FeeStructureClassRepository structureClassRepository;
    @Mock
    private StudentFeeRepository studentFeeRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private FeeCategoryRepository feeCategoryRepository;
    @Mock
    private FeeReportingRepository feeReportingRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private SchoolStudentGuardianLinkRepository guardianLinkRepository;
    @Mock
    private AcademicSessionRepository sessionRepository;
    @Mock
    private TermRepository termRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private UserRepository userRepository;

    private FeeServiceImpl feeService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID SESSION_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID TERM_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234");
    private static final UUID CLASS_ID = UUID.fromString("f6a7b890-1234-4567-f123-456789012345");
    private static final UUID CATEGORY_ID = UUID.fromString("a7b89012-3456-7890-1234-567890123456");
    private static final UUID STRUCTURE_ID = UUID.fromString("b8901234-5678-9012-3456-789012345678");
    private static final UUID STUDENT_ID = UUID.fromString("c9012345-6789-0123-4567-890123456789");

    @BeforeEach
    void setUp() {
        feeService = new FeeServiceImpl(
                structureRepository,
                itemRepository,
                structureClassRepository,
                studentFeeRepository,
                ledgerEntryRepository,
                feeCategoryRepository,
                feeReportingRepository,
                classRepository,
                studentRepository,
                guardianLinkRepository,
                sessionRepository,
                termRepository,
                jwtUtils,
                transactionalOperator,
                userRepository);

        org.mockito.Mockito.lenient().when(userRepository.findByKeycloakIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Mono.just(User.builder().id(USER_ID).keycloakId(USER_ID).build()));
    }

    @Test
    @DisplayName("Should create fee structure with locked session and term")
    void shouldCreateFeeStructureWithLockedSessionAndTerm() {
        CreateFeeStructureRequest request = validCreateRequest();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass()));
        when(feeCategoryRepository.existsByIdAndSchoolId(CATEGORY_ID, SCHOOL_ID)).thenReturn(Mono.just(true));
        when(structureRepository.existsActiveBySchoolIdAndTermIdAndNameIgnoreCase(
                SCHOOL_ID, TERM_ID, "Primary 1 Tuition"))
                .thenReturn(Mono.just(false));
        when(structureRepository.save(any(FeeStructure.class))).thenAnswer(invocation -> {
            FeeStructure structure = invocation.getArgument(0);
            structure.setCreatedAt(Instant.now());
            return Mono.just(structure);
        });
        when(itemRepository.save(any(FeeStructureItem.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(structureClassRepository.insertLink(any(UUID.class), eq(CLASS_ID))).thenReturn(Mono.just(1));
        when(studentRepository.countActiveBySchoolIdAndCurrentClassIdIn(eq(SCHOOL_ID), anyList()))
                .thenReturn(Mono.just(2L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(feeService.createFeeStructure(request))
                .assertNext(response -> {
                    assertThat(response.structureId()).isNotNull();
                    assertThat(response.name()).isEqualTo("Primary 1 Tuition");
                    assertThat(response.totalAmount()).isEqualByComparingTo("15000.00");
                    assertThat(response.mandatoryAmount()).isEqualByComparingTo("10000.00");
                    assertThat(response.applicableClassCount()).isEqualTo(1);
                    assertThat(response.estimatedStudentCount()).isEqualTo(2);
                    assertThat(response.status()).isEqualTo("ACTIVE");
                })
                .verifyComplete();

        ArgumentCaptor<FeeStructure> structureCaptor = ArgumentCaptor.forClass(FeeStructure.class);
        verify(structureRepository).save(structureCaptor.capture());
        FeeStructure savedStructure = structureCaptor.getValue();
        assertThat(savedStructure.getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(savedStructure.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(savedStructure.getDueDate()).isEqualTo(dueDate());
        verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        verify(termRepository).findByIdAndDeletedAtIsNullForUpdate(TERM_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should reject invalid create request before auth lookup")
    void shouldRejectInvalidCreateRequestBeforeAuthLookup() {
        CreateFeeStructureRequest request = new CreateFeeStructureRequest(
                " ",
                SESSION_ID,
                TERM_ID,
                List.of(CLASS_ID),
                dueDate(),
                validItems(),
                null);

        StepVerifier.create(feeService.createFeeStructure(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_FEE_STRUCTURE");
                    assertThat(exception.getField()).isEqualTo("name");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject duplicate active fee structure in locked term")
    void shouldRejectDuplicateActiveFeeStructureInLockedTerm() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass()));
        when(feeCategoryRepository.existsByIdAndSchoolId(CATEGORY_ID, SCHOOL_ID)).thenReturn(Mono.just(true));
        when(structureRepository.existsActiveBySchoolIdAndTermIdAndNameIgnoreCase(
                SCHOOL_ID, TERM_ID, "Primary 1 Tuition"))
                .thenReturn(Mono.just(true));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("DUPLICATE_FEE_STRUCTURE");
                })
                .verify();

        verify(structureRepository, never()).save(any(FeeStructure.class));
    }

    @Test
    @DisplayName("Should assign fees only to students without existing fee")
    void shouldAssignFeesOnlyToStudentsWithoutExistingFee() {
        FeeStructure structure = activeStructure();
        Student student = student(STUDENT_ID);
        UUID existingStudentId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findByIdAndSchoolIdForUpdate(STRUCTURE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(structure));
        when(structureClassRepository.findByFeeStructureId(STRUCTURE_ID))
                .thenReturn(Flux.just(FeeStructureClass.builder()
                        .feeStructureId(STRUCTURE_ID)
                        .classId(CLASS_ID)
                        .build()));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassIdIn(eq(SCHOOL_ID), anyList()))
                .thenReturn(Flux.just(student, student(existingStudentId)));
        when(studentFeeRepository.findByStudentIdAndFeeStructureId(STUDENT_ID, STRUCTURE_ID))
                .thenReturn(Mono.empty());
        when(studentFeeRepository.findByStudentIdAndFeeStructureId(existingStudentId, STRUCTURE_ID))
                .thenReturn(Mono.just(StudentFee.builder().id(UUID.randomUUID()).build()));
        when(studentFeeRepository.save(any(StudentFee.class))).thenAnswer(invocation -> {
            StudentFee fee = invocation.getArgument(0);
            fee.setId(UUID.randomUUID());
            return Mono.just(fee);
        });
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(feeService.assignFeesToStudents(STRUCTURE_ID))
                .assertNext(response -> {
                    assertThat(response.structureId()).isEqualTo(STRUCTURE_ID);
                    assertThat(response.studentsAssigned()).isEqualTo(1);
                    assertThat(response.totalExpectedAmount()).isEqualByComparingTo("10000.00");
                    assertThat(response.status()).isEqualTo("ASSIGNED");
                })
                .verifyComplete();

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getRecordedBy()).isEqualTo(USER_ID);
        assertThat(ledgerCaptor.getValue().getIdempotencyKey()).isNotNull();
        verify(structureRepository).findByIdAndSchoolIdForUpdate(STRUCTURE_ID, SCHOOL_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should skip assignment duplicate save race")
    void shouldSkipAssignmentDuplicateSaveRace() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findByIdAndSchoolIdForUpdate(STRUCTURE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeStructure()));
        when(structureClassRepository.findByFeeStructureId(STRUCTURE_ID))
                .thenReturn(Flux.just(FeeStructureClass.builder()
                        .feeStructureId(STRUCTURE_ID)
                        .classId(CLASS_ID)
                        .build()));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassIdIn(eq(SCHOOL_ID), anyList()))
                .thenReturn(Flux.just(student(STUDENT_ID)));
        when(studentFeeRepository.findByStudentIdAndFeeStructureId(STUDENT_ID, STRUCTURE_ID))
                .thenReturn(Mono.empty());
        when(studentFeeRepository.save(any(StudentFee.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(feeService.assignFeesToStudents(STRUCTURE_ID))
                .assertNext(response -> assertThat(response.studentsAssigned()).isZero())
                .verifyComplete();

        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should reject null structure id before auth lookup")
    void shouldRejectNullStructureIdBeforeAuthLookup() {
        StepVerifier.create(feeService.assignFeesToStudents(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("structureId");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject inactive structure assignment")
    void shouldRejectInactiveStructureAssignment() {
        FeeStructure inactive = activeStructure();
        inactive.setStatus("INACTIVE");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findByIdAndSchoolIdForUpdate(STRUCTURE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(inactive));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(feeService.assignFeesToStudents(STRUCTURE_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("STRUCTURE_INACTIVE");
                })
                .verify();

        verify(studentRepository, never()).findActiveBySchoolIdAndCurrentClassIdIn(any(), anyList());
    }

    @Test
    @DisplayName("Should list enriched fee structures")
    void shouldListEnrichedFeeStructures() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findBySchoolIdAndStatus(SCHOOL_ID, "ACTIVE"))
                .thenReturn(Flux.just(activeStructure()));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(structureClassRepository.findByFeeStructureId(STRUCTURE_ID))
                .thenReturn(Flux.just(FeeStructureClass.builder()
                        .feeStructureId(STRUCTURE_ID)
                        .classId(CLASS_ID)
                        .build()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass()));
        when(studentRepository.countActiveBySchoolIdAndCurrentClassIdIn(eq(SCHOOL_ID), anyList()))
                .thenReturn(Mono.just(3L));
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(
                        feeItem("Tuition", BigDecimal.valueOf(10000), true),
                        feeItem("Sports", BigDecimal.valueOf(5000), false)));
        when(feeReportingRepository.getStructureCollectionStats(SCHOOL_ID, STRUCTURE_ID))
                .thenReturn(Mono.just(new FeeReportingRepository.CollectionStats(
                        BigDecimal.valueOf(30000),
                        BigDecimal.valueOf(15000),
                        2)));

        StepVerifier.create(feeService.getFeeStructures("ACTIVE", null))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    var response = responses.getFirst();
                    assertThat(response.structureId()).isEqualTo(STRUCTURE_ID);
                    assertThat(response.termName()).isEqualTo("First Term");
                    assertThat(response.sessionName()).isEqualTo("2025/2026");
                    assertThat(response.mandatoryAmount()).isEqualByComparingTo("10000");
                    assertThat(response.applicableToClasses()).containsExactly("Primary 1");
                    assertThat(response.studentCount()).isEqualTo(3);
                    assertThat(response.collectionRate()).isEqualTo(50.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject invalid fee structure status before auth lookup")
    void shouldRejectInvalidFeeStructureStatusBeforeAuthLookup() {
        StepVerifier.create(feeService.getFeeStructures("ARCHIVED", null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_STATUS");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("status");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should return student fees with live ledger balance")
    void shouldReturnStudentFeesWithLiveLedgerBalance() {
        UUID studentFeeId = UUID.randomUUID();
        StudentFee fee = StudentFee.builder()
                .id(studentFeeId)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .feeStructureId(STRUCTURE_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(7))
                .isLateFeeApplied(false)
                .lateFeeAmount(BigDecimal.ZERO)
                .build();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID)));
        when(studentFeeRepository.findByStudentIdAndSchoolId(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fee));
        when(structureRepository.findById(STRUCTURE_ID)).thenReturn(Mono.just(activeStructure()));
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(studentFeeId))
                .thenReturn(Flux.just(
                        ledgerEntry(studentFeeId, "FEE_ASSIGNED", BigDecimal.valueOf(10000)),
                        ledgerEntry(studentFeeId, "PAYMENT", BigDecimal.valueOf(-4000))));

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    var response = responses.getFirst();
                    assertThat(response.amountPaid()).isEqualByComparingTo("4000");
                    assertThat(response.balance()).isEqualByComparingTo("6000");
                    assertThat(response.status()).isEqualTo("PARTIAL");
                    assertThat(response.termName()).isEqualTo("First Term");
                    assertThat(response.items()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject parent without fee access to student")
    void shouldRejectParentWithoutFeeAccessToStudent() {
        SchoolFeeUser parent = currentUser();
        parent.setUserType("PARENT");
        parent.setRoles(Set.of("PARENT"));
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parent));
        when(guardianLinkRepository.findFeeAccessByGuardianUserIdAndStudentIdAndSchoolId(
                USER_ID, STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();

        verify(studentFeeRepository, never()).findByStudentIdAndSchoolId(any(), any());
    }

    @Test
    @DisplayName("Should return fee dashboard from live reporting data")
    void shouldReturnFeeDashboardFromLiveReportingData() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(activeTerm()));
        when(feeReportingRepository.getDashboardSummary(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(new FeeReportingRepository.DashboardSummaryStats(
                        BigDecimal.valueOf(30000),
                        BigDecimal.valueOf(15000),
                        BigDecimal.valueOf(15000),
                        1,
                        1,
                        1)));
        when(feeReportingRepository.getClassCollections(SCHOOL_ID, TERM_ID))
                .thenReturn(Flux.just(new FeeReportingRepository.ClassCollectionStats(
                        CLASS_ID.toString(),
                        "Primary 1",
                        2,
                        BigDecimal.valueOf(30000),
                        BigDecimal.valueOf(15000))));
        when(feeReportingRepository.getDeadlineStats(eq(SCHOOL_ID), eq(TERM_ID), any(LocalDate.class)))
                .thenReturn(Mono.just(new FeeReportingRepository.DeadlineStats(
                        1,
                        BigDecimal.valueOf(5000),
                        2,
                        BigDecimal.valueOf(10000),
                        3,
                        BigDecimal.valueOf(15000))));
        when(feeReportingRepository.getDailyCollectionTrend(
                eq(SCHOOL_ID), eq(TERM_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Flux.just(new FeeReportingRepository.DailyCollectionStats(
                        "2026-06-04",
                        BigDecimal.valueOf(15000),
                        2)));

        StepVerifier.create(feeService.getFeeDashboard("current"))
                .assertNext(response -> {
                    assertThat(response.termName()).isEqualTo("First Term");
                    assertThat(response.summary().totalExpected()).isEqualByComparingTo("30000");
                    assertThat(response.summary().collectionRate()).isEqualTo(50.0);
                    assertThat(response.byClass()).hasSize(1);
                    assertThat(response.upcomingDeadlines().overdue().count()).isEqualTo(3);
                    assertThat(response.dailyCollectionTrend()).hasSize(1);
                })
                .verifyComplete();
    }

    private FeeStructureItem feeItem(String description, BigDecimal amount, boolean mandatory) {
        return FeeStructureItem.builder()
                .id(UUID.randomUUID())
                .feeStructureId(STRUCTURE_ID)
                .description(description)
                .amount(amount)
                .isMandatory(mandatory)
                .sortOrder(mandatory ? 1 : 2)
                .build();
    }

    private LedgerEntry ledgerEntry(UUID studentFeeId, String entryType, BigDecimal amount) {
        return LedgerEntry.builder()
                .id(UUID.randomUUID())
                .studentFeeId(studentFeeId)
                .schoolId(SCHOOL_ID)
                .studentId(STUDENT_ID)
                .entryType(entryType)
                .amount(amount)
                .balanceAfter(BigDecimal.ZERO)
                .sourceEntityType("test")
                .sourceEntityId(STRUCTURE_ID)
                .transactionDate(Instant.now())
                .idempotencyKey(UUID.randomUUID())
                .build();
    }

    private CreateFeeStructureRequest validCreateRequest() {
        return new CreateFeeStructureRequest(
                " Primary 1 Tuition ",
                SESSION_ID,
                TERM_ID,
                List.of(CLASS_ID, CLASS_ID),
                dueDate(),
                validItems(),
                new CreateFeeStructureRequest.LateFeeConfig(
                        14,
                        5.0,
                        BigDecimal.valueOf(500)));
    }

    private List<CreateFeeStructureRequest.FeeItemRequest> validItems() {
        return List.of(
                new CreateFeeStructureRequest.FeeItemRequest(
                        CATEGORY_ID,
                        " Tuition ",
                        BigDecimal.valueOf(10000),
                        true,
                        1),
                new CreateFeeStructureRequest.FeeItemRequest(
                        null,
                        " Sports ",
                        BigDecimal.valueOf(5000),
                        false,
                        2));
    }

    private LocalDate dueDate() {
        return LocalDate.now().plusDays(30);
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }

    private AcademicSession activeSession() {
        return AcademicSession.builder()
                .id(SESSION_ID)
                .schoolId(SCHOOL_ID)
                .name("2025/2026 Academic Year")
                .startDate(LocalDate.now().minusMonths(3))
                .endDate(LocalDate.now().plusMonths(3))
                .status("ACTIVE")
                .build();
    }

    private Term activeTerm() {
        return Term.builder()
                .id(TERM_ID)
                .sessionId(SESSION_ID)
                .name("First Term")
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(2))
                .status("ACTIVE")
                .build();
    }

    private ClassEntity activeClass() {
        return ClassEntity.builder()
                .id(CLASS_ID)
                .schoolId(SCHOOL_ID)
                .name("Primary 1")
                .gradeLevel("PRIMARY_1")
                .isActive(true)
                .build();
    }

    private FeeStructure activeStructure() {
        return FeeStructure.builder()
                .id(STRUCTURE_ID)
                .schoolId(SCHOOL_ID)
                .name("Primary 1 Tuition")
                .academicSessionId(SESSION_ID)
                .termId(TERM_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .dueDate(dueDate())
                .status("ACTIVE")
                .build();
    }

    private Student student(UUID studentId) {
        return Student.builder()
                .id(studentId)
                .schoolId(SCHOOL_ID)
                .firstName("Ada")
                .lastName("Lovelace")
                .currentClassId(CLASS_ID)
                .enrollmentStatus("ACTIVE")
                .build();
    }
}
