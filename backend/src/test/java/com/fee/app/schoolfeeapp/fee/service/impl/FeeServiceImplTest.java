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
import com.fee.app.schoolfeeapp.fee.dto.response.FeeStructureResponse;
import com.fee.app.schoolfeeapp.fee.dto.response.StudentFeeResponse;
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

    // ========================================================================
    // CREATE FEE STRUCTURE VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject createFeeStructure when request is null")
    void shouldRejectCreateFeeStructureWhenRequestIsNull() {
        StepVerifier.create(feeService.createFeeStructure(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_FEE_STRUCTURE");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject createFeeStructure when session ID, term ID, or due date is null")
    void shouldRejectCreateFeeStructureWhenIdsOrDateNull() {
        CreateFeeStructureRequest reqSessionNull = new CreateFeeStructureRequest("Name", null, TERM_ID, List.of(CLASS_ID), dueDate(), validItems(), null);
        CreateFeeStructureRequest reqTermNull = new CreateFeeStructureRequest("Name", SESSION_ID, null, List.of(CLASS_ID), dueDate(), validItems(), null);
        CreateFeeStructureRequest reqDateNull = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), null, validItems(), null);

        StepVerifier.create(feeService.createFeeStructure(reqSessionNull))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("sessionId"))
                .verify();

        StepVerifier.create(feeService.createFeeStructure(reqTermNull))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("termId"))
                .verify();

        StepVerifier.create(feeService.createFeeStructure(reqDateNull))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("dueDate"))
                .verify();
    }

    @Test
    @DisplayName("Should reject createFeeStructure when due date is in the past")
    void shouldRejectCreateFeeStructureWhenDueDateInPast() {
        CreateFeeStructureRequest req = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), LocalDate.now().minusDays(1), validItems(), null);
        StepVerifier.create(feeService.createFeeStructure(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_DUE_DATE");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject createFeeStructure when class list is empty or contains null")
    void shouldRejectCreateFeeStructureWhenClassListEmptyOrContainsNull() {
        CreateFeeStructureRequest reqEmpty = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(), dueDate(), validItems(), null);
        
        java.util.ArrayList<UUID> classList = new java.util.ArrayList<>();
        classList.add(null);
        CreateFeeStructureRequest reqNullClass = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, classList, dueDate(), validItems(), null);

        StepVerifier.create(feeService.createFeeStructure(reqEmpty))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("applicableToClassIds"))
                .verify();

        StepVerifier.create(feeService.createFeeStructure(reqNullClass))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("applicableToClassIds[0]"))
                .verify();
    }

    @Test
    @DisplayName("Should reject createFeeStructure when items list is empty or contains null item or invalid amount")
    void shouldRejectCreateFeeStructureWhenItemsListInvalid() {
        CreateFeeStructureRequest reqEmpty = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), dueDate(), List.of(), null);
        
        java.util.ArrayList<CreateFeeStructureRequest.FeeItemRequest> itemsList = new java.util.ArrayList<>();
        itemsList.add(null);
        CreateFeeStructureRequest reqNullItem = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), dueDate(), itemsList, null);

        CreateFeeStructureRequest reqInvalidAmount = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), dueDate(), 
                List.of(new CreateFeeStructureRequest.FeeItemRequest(CATEGORY_ID, "Desc", BigDecimal.ZERO, true, 1)), null);

        StepVerifier.create(feeService.createFeeStructure(reqEmpty))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("items"))
                .verify();

        StepVerifier.create(feeService.createFeeStructure(reqNullItem))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("items[0]"))
                .verify();

        StepVerifier.create(feeService.createFeeStructure(reqInvalidAmount))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("items[0].amount"))
                .verify();
    }

    @Test
    @DisplayName("Should reject createFeeStructure when late fee config contains negative values")
    void shouldRejectCreateFeeStructureWhenLateFeeConfigNegative() {
        CreateFeeStructureRequest req1 = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), dueDate(), validItems(),
                new CreateFeeStructureRequest.LateFeeConfig(-1, 5.0, BigDecimal.valueOf(100)));
        CreateFeeStructureRequest req2 = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), dueDate(), validItems(),
                new CreateFeeStructureRequest.LateFeeConfig(5, -2.0, BigDecimal.valueOf(100)));
        CreateFeeStructureRequest req3 = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), dueDate(), validItems(),
                new CreateFeeStructureRequest.LateFeeConfig(5, 5.0, BigDecimal.valueOf(-50)));

        StepVerifier.create(feeService.createFeeStructure(req1))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("lateFeeConfig.applyAfterDays"))
                .verify();

        StepVerifier.create(feeService.createFeeStructure(req2))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("lateFeeConfig.percentageAmount"))
                .verify();

        StepVerifier.create(feeService.createFeeStructure(req3))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("lateFeeConfig.flatAmount"))
                .verify();
    }

    // ========================================================================
    // CREATE FEE STRUCTURE DEPENDENCY VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject createFeeStructure when session does not belong to school or is closed")
    void shouldRejectCreateFeeStructureWhenSessionInvalid() {
        AcademicSession wrongSchoolSession = activeSession();
        wrongSchoolSession.setSchoolId(UUID.randomUUID());

        AcademicSession completedSession = activeSession();
        completedSession.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(wrongSchoolSession));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(any())).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                })
                .verify();

        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(completedSession));
        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject createFeeStructure when term does not belong to session or is completed")
    void shouldRejectCreateFeeStructureWhenTermInvalid() {
        Term wrongSessionTerm = activeTerm();
        wrongSessionTerm.setSessionId(UUID.randomUUID());

        Term completedTerm = activeTerm();
        completedTerm.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(wrongSessionTerm));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_IN_SESSION");
                })
                .verify();

        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(completedTerm));
        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_ALREADY_COMPLETED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject createFeeStructure when due date violates term bounds")
    void shouldRejectCreateFeeStructureWhenDueDateViolatesBounds() {
        Term term = activeTerm();
        term.setStartDate(LocalDate.now().plusDays(10));
        
        CreateFeeStructureRequest reqBeforeTerm = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), LocalDate.now().plusDays(5), validItems(), null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(term));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(feeService.createFeeStructure(reqBeforeTerm))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_DUE_DATE");
                    assertThat(((SchoolFeeException) error).getMessage()).contains("before the term start date");
                })
                .verify();

        AcademicSession session = activeSession();
        session.setEndDate(LocalDate.now().plusDays(15));
        CreateFeeStructureRequest reqAfterSession = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), LocalDate.now().plusDays(20), validItems(), null);

        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        Term normalTerm = activeTerm();
        normalTerm.setStartDate(LocalDate.now().minusDays(5));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(normalTerm));

        StepVerifier.create(feeService.createFeeStructure(reqAfterSession))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_DUE_DATE");
                    assertThat(((SchoolFeeException) error).getMessage()).contains("after the session end date");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject createFeeStructure when class is inactive")
    void shouldRejectCreateFeeStructureWhenClassInactive() {
        ClassEntity inactiveCls = activeClass();
        inactiveCls.setIsActive(false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(inactiveCls));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("CLASS_INACTIVE");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject createFeeStructure when fee category is not found")
    void shouldRejectCreateFeeStructureWhenCategoryNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass()));
        when(feeCategoryRepository.existsByIdAndSchoolId(CATEGORY_ID, SCHOOL_ID)).thenReturn(Mono.just(false));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("FEE_CATEGORY_NOT_FOUND");
                })
                .verify();
    }

    // ========================================================================
    // RESOLVE DASHBOARD TERM VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject resolveDashboardTerm when term is current but not found")
    void shouldRejectResolveDashboardTermWhenCurrentNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.empty());

        StepVerifier.create(feeService.getFeeDashboard("current"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                    assertThat(((SchoolFeeException) error).getMessage()).contains("Current term not found");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject resolveDashboardTerm when term ID is invalid UUID")
    void shouldRejectResolveDashboardTermWhenTermIdInvalidUuid() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));

        StepVerifier.create(feeService.getFeeDashboard("not-a-uuid"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_TERM_ID");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject resolveDashboardTerm when term is not found")
    void shouldRejectResolveDashboardTermWhenNotFound() {
        UUID randomTermId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findById(randomTermId)).thenReturn(Mono.empty());

        StepVerifier.create(feeService.getFeeDashboard(randomTermId.toString()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject resolveDashboardTerm when term belongs to another school")
    void shouldRejectResolveDashboardTermWhenWrongSchool() {
        Term term = activeTerm();
        AcademicSession sessionOfAnotherSchool = activeSession();
        sessionOfAnotherSchool.setSchoolId(UUID.randomUUID());

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(term));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(sessionOfAnotherSchool));

        StepVerifier.create(feeService.getFeeDashboard(TERM_ID.toString()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();
    }

    // ========================================================================
    // STUDENT FEE STATUS DERIVATION SCENARIOS
    // ========================================================================

    @Test
    @DisplayName("Should derive status PAID when balance is zero")
    void shouldDerivePaidStatusWhenBalanceZero() {
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
                        ledgerEntry(studentFeeId, "PAYMENT", BigDecimal.valueOf(-10000))));

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().status()).isEqualTo("PAID");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should derive status OVERDUE when unpaid and due date is in the past")
    void shouldDeriveOverdueStatusWhenUnpaidAndPastDueDate() {
        UUID studentFeeId = UUID.randomUUID();
        StudentFee fee = StudentFee.builder()
                .id(studentFeeId)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .feeStructureId(STRUCTURE_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().minusDays(2))
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
                .thenReturn(Flux.empty());

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().status()).isEqualTo("OVERDUE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should derive status PENDING when unpaid and due date is in the future")
    void shouldDerivePendingStatusWhenUnpaidAndFutureDueDate() {
        UUID studentFeeId = UUID.randomUUID();
        StudentFee fee = StudentFee.builder()
                .id(studentFeeId)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .feeStructureId(STRUCTURE_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(5))
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
                .thenReturn(Flux.empty());

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().status()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should create fee structure successfully with null late fee config")
    void shouldCreateFeeStructureSuccessfullyWithNullLateFeeConfig() {
        CreateFeeStructureRequest request = new CreateFeeStructureRequest(
                "Primary 1 Tuition",
                SESSION_ID,
                TERM_ID,
                List.of(CLASS_ID),
                dueDate(),
                validItems(),
                null); // null lateFeeConfig

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
                    assertThat(response.status()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject fee assignment when no classes for structure")
    void shouldRejectFeeAssignmentWhenNoClassesForStructure() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findByIdAndSchoolIdForUpdate(STRUCTURE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeStructure()));
        when(structureClassRepository.findByFeeStructureId(STRUCTURE_ID))
                .thenReturn(Flux.empty()); // no classes
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(feeService.assignFeesToStudents(STRUCTURE_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("NO_CLASSES_FOR_STRUCTURE");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject fee assignment when structure due date is null")
    void shouldRejectFeeAssignmentWhenStructureDueDateIsNull() {
        FeeStructure structure = activeStructure();
        structure.setDueDate(null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findByIdAndSchoolIdForUpdate(STRUCTURE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(structure));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(feeService.assignFeesToStudents(STRUCTURE_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_FEE_STRUCTURE");
                })
                .verify();
    }

    @Test
    @DisplayName("Should assign fees when due date is near (reminder date is now)")
    void shouldAssignFeesToActiveStudentsWhenDueDateIsNear() {
        FeeStructure structure = activeStructure();
        structure.setDueDate(LocalDate.now().plusDays(2)); // nextReminder = now - 1 (not in future)

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findByIdAndSchoolIdForUpdate(STRUCTURE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(structure));
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
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(feeService.assignFeesToStudents(STRUCTURE_ID))
                .assertNext(response -> {
                    assertThat(response.structureId()).isEqualTo(STRUCTURE_ID);
                    assertThat(response.studentsAssigned()).isEqualTo(1);
                    assertThat(response.nextReminderDate()).isEqualTo(LocalDate.now()); // should fallback to now
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject dashboard term when session belongs to another school")
    void shouldRejectDashboardTermWhenSessionBelongsToAnotherSchool() {
        UUID anotherSchoolId = UUID.randomUUID();
        AcademicSession session = activeSession();
        session.setSchoolId(anotherSchoolId); 

        Term term = activeTerm();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(term));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(session));

        StepVerifier.create(feeService.getFeeDashboard(TERM_ID.toString()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject getStudentFees when student ID is null")
    void shouldRejectGetStudentFeesWhenStudentIdIsNull() {
        StepVerifier.create(feeService.getStudentFees(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_STUDENT");
                })
                .verify();
    }

    @Test
    @DisplayName("Should get outstanding fee IDs successfully")
    void shouldGetOutstandingFeeIdsSuccessfully() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(activeTerm()));
        when(feeReportingRepository.getOutstandingFeeIds(eq(SCHOOL_ID), eq(TERM_ID), eq("filter"), any(LocalDate.class)))
                .thenReturn(Flux.just(STRUCTURE_ID));

        StepVerifier.create(feeService.getOutstandingFeeIds("current", "filter"))
                .assertNext(ids -> {
                    assertThat(ids).containsExactly(STRUCTURE_ID);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject create fee structure when session is deleted")
    void shouldRejectCreateFeeStructureWhenSessionIsDeleted() {
        AcademicSession session = activeSession();
        session.setDeletedAt(Instant.now()); 

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(any(UUID.class))).thenReturn(Mono.empty());

        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SESSION_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject create fee structure when session has closedAt")
    void shouldRejectCreateFeeStructureWhenSessionHasClosedAt() {
        AcademicSession session = activeSession();
        session.setClosedAt(Instant.now()); 

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(any(UUID.class))).thenReturn(Mono.empty());

        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject create fee structure when term has completedAt")
    void shouldRejectCreateFeeStructureWhenTermHasCompletedAt() {
        Term term = activeTerm();
        term.setCompletedAt(Instant.now()); 

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(term));

        StepVerifier.create(feeService.createFeeStructure(validCreateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_ALREADY_COMPLETED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should create fee structure successfully when term start date and session end date are null")
    void shouldCreateFeeStructureSuccessfullyWhenDatesAreNull() {
        AcademicSession session = activeSession();
        session.setEndDate(null); 

        Term term = activeTerm();
        term.setStartDate(null); 

        CreateFeeStructureRequest request = validCreateRequest();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findByIdAndDeletedAtIsNullForUpdate(TERM_ID)).thenReturn(Mono.just(term));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass()));
        when(feeCategoryRepository.existsByIdAndSchoolId(CATEGORY_ID, SCHOOL_ID)).thenReturn(Mono.just(true));
        when(structureRepository.existsActiveBySchoolIdAndTermIdAndNameIgnoreCase(
                SCHOOL_ID, TERM_ID, "Primary 1 Tuition"))
                .thenReturn(Mono.just(false));
        when(structureRepository.save(any(FeeStructure.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(itemRepository.save(any(FeeStructureItem.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(structureClassRepository.insertLink(any(), any())).thenReturn(Mono.just(1));
        when(studentRepository.countActiveBySchoolIdAndCurrentClassIdIn(any(), any())).thenReturn(Mono.just(2L));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(feeService.createFeeStructure(request))
                .assertNext(response -> assertThat(response.status()).isEqualTo("ACTIVE"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should successfully call private toStudentFeeResponse(StudentFee) via reflection")
    @SuppressWarnings("unchecked")
    void shouldCallPrivateToStudentFeeResponseUsingReflection() throws Exception {
        UUID studentFeeId = UUID.randomUUID();
        StudentFee fee = StudentFee.builder()
                .id(studentFeeId)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .feeStructureId(STRUCTURE_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(5))
                .isLateFeeApplied(false)
                .lateFeeAmount(BigDecimal.ZERO)
                .build();

        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(activeTerm()));
        when(structureRepository.findById(STRUCTURE_ID)).thenReturn(Mono.just(activeStructure()));
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(studentFeeId))
                .thenReturn(Flux.empty());

        java.lang.reflect.Method method = FeeServiceImpl.class.getDeclaredMethod("toStudentFeeResponse", StudentFee.class);
        method.setAccessible(true);
        Mono<StudentFeeResponse> mono = (Mono<StudentFeeResponse>) method.invoke(feeService, fee);

        StepVerifier.create(mono)
                .assertNext(response -> {
                    assertThat(response.structureName()).isEqualTo("Primary 1 Tuition");
                    assertThat(response.status()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should derive status PENDING when dueDate is null")
    void shouldDerivePendingStatusWhenDueDateIsNull() {
        UUID studentFeeId = UUID.randomUUID();
        StudentFee fee = StudentFee.builder()
                .id(studentFeeId)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .feeStructureId(STRUCTURE_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(null) 
                .isLateFeeApplied(false)
                .lateFeeAmount(BigDecimal.ZERO)
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(activeTerm()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID)));
        when(studentFeeRepository.findByStudentIdAndSchoolId(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fee));
        when(structureRepository.findById(STRUCTURE_ID)).thenReturn(Mono.just(activeStructure()));
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(studentFeeId))
                .thenReturn(Flux.empty());

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().status()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should derive status successfully when ledger entry has negative amount")
    void shouldDeriveStatusWhenLedgerEntryHasNegativeAmount() {
        UUID studentFeeId = UUID.randomUUID();
        StudentFee fee = StudentFee.builder()
                .id(studentFeeId)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .feeStructureId(STRUCTURE_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(5))
                .isLateFeeApplied(false)
                .lateFeeAmount(BigDecimal.ZERO)
                .build();

        LedgerEntry negativeEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .entryType("PAYMENT")
                .amount(BigDecimal.valueOf(-5000)) 
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(activeTerm()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID)));
        when(studentFeeRepository.findByStudentIdAndSchoolId(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fee));
        when(structureRepository.findById(STRUCTURE_ID)).thenReturn(Mono.just(activeStructure()));
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(studentFeeId))
                .thenReturn(Flux.just(negativeEntry));

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().amountPaid()).isEqualByComparingTo("5000.00");
                    assertThat(responses.getFirst().status()).isEqualTo("PAID");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get fee structures when termFilter is null or blank")
    void shouldGetFeeStructuresWhenTermFilterIsNullOrBlank() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findBySchoolIdAndStatus(SCHOOL_ID, "ACTIVE"))
                .thenReturn(Flux.just(activeStructure()));

        when(structureClassRepository.findByFeeStructureId(STRUCTURE_ID))
                .thenReturn(Flux.empty()); 
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(feeReportingRepository.getStructureCollectionStats(SCHOOL_ID, STRUCTURE_ID))
                .thenReturn(Mono.just(new FeeReportingRepository.CollectionStats(
                        BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), 2
                )));

        StepVerifier.create(feeService.getFeeStructures("ACTIVE", null))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().applicableToClasses()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should calculate collection rate as 0 when expected collection amount is null")
    void shouldCalculateCollectionRateAsZeroWhenExpectedAmountIsNull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findBySchoolIdAndStatus(SCHOOL_ID, "ACTIVE"))
                .thenReturn(Flux.just(activeStructure()));

        when(structureClassRepository.findByFeeStructureId(STRUCTURE_ID))
                .thenReturn(Flux.empty());
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(feeReportingRepository.getStructureCollectionStats(SCHOOL_ID, STRUCTURE_ID))
                .thenReturn(Mono.just(new FeeReportingRepository.CollectionStats(
                        null, BigDecimal.valueOf(5000), 2
                ))); 

        StepVerifier.create(feeService.getFeeStructures("ACTIVE", null))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().collectionRate()).isEqualTo(0.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject create fee structure when class IDs list is null")
    void shouldRejectCreateFeeStructureWhenClassListIsNull() {
        CreateFeeStructureRequest req = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, null, dueDate(), validItems(), null);
        StepVerifier.create(feeService.createFeeStructure(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("applicableToClassIds");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject create fee structure when items list is null")
    void shouldRejectCreateFeeStructureWhenItemsListIsNull() {
        CreateFeeStructureRequest req = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), dueDate(), null, null);
        StepVerifier.create(feeService.createFeeStructure(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("items");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject create fee structure when fee item amount is null")
    void shouldRejectCreateFeeStructureWhenFeeItemAmountIsNull() {
        CreateFeeStructureRequest req = new CreateFeeStructureRequest("Name", SESSION_ID, TERM_ID, List.of(CLASS_ID), dueDate(),
                List.of(new CreateFeeStructureRequest.FeeItemRequest(CATEGORY_ID, "Desc", null, true, 1)), null);
        StepVerifier.create(feeService.createFeeStructure(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("items[0].amount");
                })
                .verify();
    }

    @Test
    @DisplayName("Should resolve dashboard term when term ID is null or blank")
    void shouldResolveDashboardTermWhenTermIdIsNullOrBlank() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(activeTerm()));
        when(feeReportingRepository.getDashboardSummary(eq(SCHOOL_ID), eq(TERM_ID)))
                .thenReturn(Mono.just(new FeeReportingRepository.DashboardSummaryStats(
                        BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(5000),
                        1, 1, 0
                )));
        when(feeReportingRepository.getClassCollections(eq(SCHOOL_ID), eq(TERM_ID)))
                .thenReturn(Flux.empty());
        when(feeReportingRepository.getDeadlineStats(eq(SCHOOL_ID), eq(TERM_ID), any(LocalDate.class)))
                .thenReturn(Mono.just(new FeeReportingRepository.DeadlineStats(
                        0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO
                )));
        when(feeReportingRepository.getDailyCollectionTrend(eq(SCHOOL_ID), eq(TERM_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(feeService.getFeeDashboard(null))
                .assertNext(response -> assertThat(response).isNotNull())
                .verifyComplete();

        StepVerifier.create(feeService.getFeeDashboard("   "))
                .assertNext(response -> assertThat(response).isNotNull())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle null findCurrentTermsBySchoolId flux in toStudentFeeResponse")
    void shouldHandleNullCurrentTermsFluxInToStudentFeeResponse() {
        UUID studentFeeId = UUID.randomUUID();
        StudentFee fee = StudentFee.builder()
                .id(studentFeeId)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .feeStructureId(STRUCTURE_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(5))
                .isLateFeeApplied(false)
                .lateFeeAmount(BigDecimal.ZERO)
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(null); 
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID)));
        when(studentFeeRepository.findByStudentIdAndSchoolId(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fee));
        when(structureRepository.findById(STRUCTURE_ID)).thenReturn(Mono.just(activeStructure()));
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(studentFeeId))
                .thenReturn(Flux.empty());

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle missing term mapping in toStudentFeeResponse")
    void shouldHandleMissingTermMappingInToStudentFeeResponse() {
        UUID studentFeeId = UUID.randomUUID();
        StudentFee fee = StudentFee.builder()
                .id(studentFeeId)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .feeStructureId(STRUCTURE_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(5))
                .isLateFeeApplied(false)
                .lateFeeAmount(BigDecimal.ZERO)
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(activeTerm()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID)));
        when(studentFeeRepository.findByStudentIdAndSchoolId(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fee));
        when(structureRepository.findById(STRUCTURE_ID)).thenReturn(Mono.just(activeStructure()));
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.empty()); 
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(studentFeeId))
                .thenReturn(Flux.empty());

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().termName()).isEqualTo("Unknown");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should determine upcoming status under various term configurations")
    void shouldDetermineUpcomingStatusUnderVariousTermConfigurations() {
        UUID studentFeeId = UUID.randomUUID();
        StudentFee fee = StudentFee.builder()
                .id(studentFeeId)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .feeStructureId(STRUCTURE_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(5))
                .isLateFeeApplied(true)
                .lastReminderSentAt(Instant.now())
                .lateFeeAmount(BigDecimal.ZERO)
                .build();

        Term upcomingTerm1 = activeTerm();
        upcomingTerm1.setIsCurrent(false);
        upcomingTerm1.setStartDate(LocalDate.now().plusMonths(2));
        Term currentTerm1 = activeTerm();
        currentTerm1.setStartDate(LocalDate.now().minusMonths(1));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(currentTerm1));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID)));
        when(studentFeeRepository.findByStudentIdAndSchoolId(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fee));
        when(structureRepository.findById(STRUCTURE_ID)).thenReturn(Mono.just(activeStructure()));
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(upcomingTerm1));
        
        LedgerEntry lateFeeEntry = LedgerEntry.builder()
                .entryType("LATE_FEE_APPLIED")
                .amount(BigDecimal.valueOf(1500))
                .build();
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(studentFeeId))
                .thenReturn(Flux.just(lateFeeEntry));

        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().isUpcomingTerm()).isTrue();
                    assertThat(responses.getFirst().lateFeeAmount()).isEqualByComparingTo("1500");
                })
                .verifyComplete();

        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.empty());
        StepVerifier.create(feeService.getStudentFees(STUDENT_ID))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().isUpcomingTerm()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should calculate collection rate as 0 when expected collection amount is zero or negative")
    void shouldCalculateCollectionRateAsZeroWhenExpectedAmountIsZeroOrNegative() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(structureRepository.findBySchoolIdAndStatus(SCHOOL_ID, "ACTIVE"))
                .thenReturn(Flux.just(activeStructure()));
        when(structureClassRepository.findByFeeStructureId(STRUCTURE_ID)).thenReturn(Flux.empty());
        when(itemRepository.findByFeeStructureIdOrderBySortOrderAsc(STRUCTURE_ID))
                .thenReturn(Flux.just(feeItem("Tuition", BigDecimal.valueOf(10000), true)));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(activeTerm()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        
        when(feeReportingRepository.getStructureCollectionStats(SCHOOL_ID, STRUCTURE_ID))
                .thenReturn(Mono.just(new FeeReportingRepository.CollectionStats(
                        BigDecimal.ZERO, BigDecimal.valueOf(5000), 2
                )));

        StepVerifier.create(feeService.getFeeStructures("ACTIVE", null))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().collectionRate()).isEqualTo(0.0);
                })
                .verifyComplete();

        when(feeReportingRepository.getStructureCollectionStats(SCHOOL_ID, STRUCTURE_ID))
                .thenReturn(Mono.just(new FeeReportingRepository.CollectionStats(
                        BigDecimal.valueOf(-100), BigDecimal.valueOf(5000), 2
                )));

        StepVerifier.create(feeService.getFeeStructures("ACTIVE", null))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.getFirst().collectionRate()).isEqualTo(0.0);
                })
                .verifyComplete();
    }
}
