package com.fee.app.schoolfeeapp.school.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fee.app.schoolfeeapp.auth.service.IdentityProviderService;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.dto.request.CloseSessionRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult;
import com.fee.app.schoolfeeapp.school.dto.request.CreateAcademicSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.CreateSchoolRequest;
import com.fee.app.schoolfeeapp.school.dto.request.UpdateSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.UpdateSchoolRequest;
import com.fee.app.schoolfeeapp.school.dto.response.AcademicSessionResponse;
import com.fee.app.schoolfeeapp.school.dto.response.CreateSchoolResponse;
import com.fee.app.schoolfeeapp.school.dto.response.SchoolResponse;
import com.fee.app.schoolfeeapp.school.dto.response.SchoolSummaryResponse;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeReportingRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeReportingRepository.DashboardSummaryStats;
import static org.mockito.Mockito.lenient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolServiceImplTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private AcademicSessionRepository sessionRepository;

    @Mock
    private TermRepository termRepository;

    @Mock
    private IdentityProviderService keycloakAdminService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TransactionalOperator transactionalOperator;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FeeReportingRepository feeReportingRepository;

    @Spy
    private ObjectMapper objectMapper;

    private SchoolServiceImpl schoolService;

    private static final UUID ADMIN_KEYCLOAK_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678902");
    private static final UUID ADMIN_USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID SESSION_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID OTHER_SESSION_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890124");
    private static final UUID TERM_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234");
    private static final UUID OTHER_TERM_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901235");

    @BeforeEach
    void setUp() {
        schoolService = new SchoolServiceImpl(
                schoolRepository,
                sessionRepository,
                termRepository,
                keycloakAdminService,
                jwtUtils,
                transactionalOperator,
                outboxEventRepository,
                studentRepository,
                userRepository,
                feeReportingRepository);

        lenient().when(studentRepository.countBySchoolIdAndDeletedAtIsNull(any())).thenReturn(Mono.just(0L));
        lenient().when(userRepository.countBySchoolIdWithFilters(any(), any(), any(), any())).thenReturn(Mono.just(0L));
        lenient().when(feeReportingRepository.getDashboardSummary(any(), any()))
                .thenReturn(Mono.just(new DashboardSummaryStats(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0)));
    }

    @Test
    @DisplayName("Should return current school with current term and payment config")
    void shouldReturnCurrentSchoolWithCurrentTermAndPaymentConfig() {
        School school = existingSchool();
        school.setPaymentConfig(schoolServiceObjectMapperPaymentConfig());
        Term currentTerm = currentTerm();
        AcademicSession session = currentSession();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(school));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(currentTerm));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(session));

        StepVerifier.create(schoolService.getCurrentSchool())
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.name()).isEqualTo("Grace International School");
                    assertThat(response.code()).isEqualTo("GIS");
                    assertThat(response.status()).isEqualTo("ACTIVE");
                    assertThat(response.currentTerm()).isNotNull();
                    assertThat(response.currentTerm().termId()).isEqualTo(TERM_ID);
                    assertThat(response.currentTerm().name()).isEqualTo("First Term");
                    assertThat(response.currentTerm().sessionName()).isEqualTo("2025/2026 Academic Year");
                    assertThat(response.paymentConfig())
                            .containsEntry("paystackPublicKey", "123456")
                            .containsEntry("paystackSubaccountCode", "GIS");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return current school without current term when none exists")
    void shouldReturnCurrentSchoolWithoutCurrentTermWhenNoneExists() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.empty());

        StepVerifier.create(schoolService.getCurrentSchool())
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.currentTerm()).isNull();
                    assertThat(response.paymentConfig()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fail current school lookup when school is missing")
    void shouldFailCurrentSchoolLookupWhenSchoolIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(schoolService.getCurrentSchool())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    assertThat(exception.getMessage()).isEqualTo("School not found");
                })
                .verify();
    }

    @Test
    @DisplayName("Should return current school sessions with ordered terms")
    void shouldReturnCurrentSchoolSessionsWithOrderedTerms() {
        AcademicSession latestSession = academicSession(
                OTHER_SESSION_ID,
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                false);
        AcademicSession previousSession = currentSession();
        Term firstTerm = term(TERM_ID, OTHER_SESSION_ID, "First Term", 1, true);
        Term secondTerm = term(OTHER_TERM_ID, OTHER_SESSION_ID, "Second Term", 2, false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findBySchoolIdOrderByStartDateDesc(SCHOOL_ID))
                .thenReturn(Flux.just(latestSession, previousSession));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(OTHER_SESSION_ID))
                .thenReturn(Flux.just(firstTerm, secondTerm));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(schoolService.getCurrentSchoolSessions())
                .assertNext(sessions -> {
                    assertThat(sessions)
                            .extracting(AcademicSessionResponse::name)
                            .containsExactly("2026/2027 Academic Year", "2025/2026 Academic Year");

                    AcademicSessionResponse latest = sessions.getFirst();
                    assertThat(latest.sessionId()).isEqualTo(OTHER_SESSION_ID);
                    assertThat(latest.isCurrent()).isFalse();
                    assertThat(latest.terms()).hasSize(2);
                    assertThat(latest.terms())
                            .extracting(AcademicSessionResponse.TermResponse::termNumber)
                            .containsExactly(1, 2);
                    assertThat(latest.terms())
                            .extracting(AcademicSessionResponse.TermResponse::name)
                            .containsExactly("First Term", "Second Term");

                    AcademicSessionResponse previous = sessions.get(1);
                    assertThat(previous.sessionId()).isEqualTo(SESSION_ID);
                    assertThat(previous.isCurrent()).isTrue();
                    assertThat(previous.terms()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty current school sessions list")
    void shouldReturnEmptyCurrentSchoolSessionsList() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(sessionRepository.findBySchoolIdOrderByStartDateDesc(SCHOOL_ID)).thenReturn(Flux.empty());

        StepVerifier.create(schoolService.getCurrentSchoolSessions())
                .assertNext(sessions -> assertThat(sessions).isEmpty())
                .verifyComplete();

        verify(termRepository, never()).findBySessionIdOrderByTermNumberAsc(any());
    }

    @Test
    @DisplayName("Should create academic session without changing current session")
    void shouldCreateAcademicSessionWithoutChangingCurrentSession() {
        CreateAcademicSessionRequest request = validCreateAcademicSessionRequest(false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(termRepository.saveAll(ArgumentMatchers.<Iterable<Term>>any()))
                .thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(any(UUID.class)))
                .thenAnswer(invocation -> Flux.just(
                        term(TERM_ID, invocation.getArgument(0), "First Term", 1, false),
                        term(OTHER_TERM_ID, invocation.getArgument(0), "Second Term", 2, false)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.createSession(request))
                .assertNext(response -> {
                    assertThat(response.name()).isEqualTo("2026/2027 Academic Year");
                    assertThat(response.isCurrent()).isFalse();
                    assertThat(response.terms())
                            .extracting(AcademicSessionResponse.TermResponse::termNumber)
                            .containsExactly(1, 2);
                    assertThat(response.terms())
                            .extracting(AcademicSessionResponse.TermResponse::isCurrent)
                            .containsExactly(false, false);
                })
                .verifyComplete();

        ArgumentCaptor<AcademicSession> sessionCaptor = ArgumentCaptor.forClass(AcademicSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(sessionCaptor.getValue().getName()).isEqualTo("2026/2027 Academic Year");
        assertThat(sessionCaptor.getValue().getIsCurrent()).isFalse();

        ArgumentCaptor<Iterable<Term>> termsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(termRepository).saveAll(termsCaptor.capture());
        List<Term> savedTerms = new ArrayList<>();
        termsCaptor.getValue().forEach(savedTerms::add);
        assertThat(savedTerms)
                .extracting(Term::getTermNumber)
                .containsExactly((short) 1, (short) 2);
        assertThat(savedTerms)
                .extracting(Term::getIsCurrent)
                .containsExactly(false, false);

        verify(sessionRepository, never()).findBySchoolIdAndIsCurrentTrue(SCHOOL_ID);
        verify(termRepository, never()).findBySessionId(SESSION_ID);
    }

    @Test
    @DisplayName("Should create current academic session and unset previous current session and term")
    void shouldCreateCurrentAcademicSessionAndUnsetPreviousCurrentSessionAndTerm() {
        CreateAcademicSessionRequest request = validCreateAcademicSessionRequest(true);
        AcademicSession previousCurrentSession = currentSession();
        Term previousCurrentTerm = currentTerm();
        Term previousNonCurrentTerm = term(OTHER_TERM_ID, SESSION_ID, "Second Term", 2, false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findBySchoolIdAndIsCurrentTrue(SCHOOL_ID))
                .thenReturn(Mono.just(previousCurrentSession));
        when(termRepository.findBySessionId(SESSION_ID))
                .thenReturn(Flux.just(previousCurrentTerm, previousNonCurrentTerm));
        when(termRepository.save(any(Term.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(termRepository.saveAll(ArgumentMatchers.<Iterable<Term>>any()))
                .thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(any(UUID.class)))
                .thenAnswer(invocation -> Flux.just(
                        term(TERM_ID, invocation.getArgument(0), "First Term", 1, true),
                        term(OTHER_TERM_ID, invocation.getArgument(0), "Second Term", 2, false)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.createSession(request))
                .assertNext(response -> {
                    assertThat(response.isCurrent()).isTrue();
                    assertThat(response.terms())
                            .extracting(AcademicSessionResponse.TermResponse::isCurrent)
                            .containsExactly(true, false);
                })
                .verifyComplete();

        assertThat(previousCurrentSession.getIsCurrent()).isFalse();
        assertThat(previousCurrentTerm.getIsCurrent()).isFalse();
        assertThat(previousNonCurrentTerm.getIsCurrent()).isFalse();

        ArgumentCaptor<AcademicSession> sessionCaptor = ArgumentCaptor.forClass(AcademicSession.class);
        verify(sessionRepository, times(2)).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getAllValues())
                .extracting(AcademicSession::getIsCurrent)
                .containsExactly(false, true);

        ArgumentCaptor<Iterable<Term>> termsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(termRepository).saveAll(termsCaptor.capture());
        List<Term> newTerms = new ArrayList<>();
        termsCaptor.getValue().forEach(newTerms::add);
        assertThat(newTerms)
                .extracting(Term::getTermNumber)
                .containsExactly((short) 1, (short) 2);
        assertThat(newTerms)
                .extracting(Term::getIsCurrent)
                .containsExactly(true, false);

        verify(termRepository).save(previousCurrentTerm);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should fail academic session creation when school is missing")
    void shouldFailAcademicSessionCreationWhenSchoolIsMissing() {
        CreateAcademicSessionRequest request = validCreateAcademicSessionRequest(false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(schoolService.createSession(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(transactionalOperator, never()).transactional(any(Mono.class));
        verify(sessionRepository, never()).save(any(AcademicSession.class));
        verify(termRepository, never()).saveAll(ArgumentMatchers.<Iterable<Term>>any());
    }

    @Test
    @DisplayName("Should map current session duplicate key race to domain error")
    void shouldMapCurrentSessionDuplicateKeyRaceToDomainError() {
        CreateAcademicSessionRequest request = validCreateAcademicSessionRequest(true);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findBySchoolIdAndIsCurrentTrue(SCHOOL_ID)).thenReturn(Mono.empty());
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("idx_one_current_session")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.createSession(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CURRENT_SESSION_CONFLICT");
                    assertThat(exception.getField()).isEqualTo("setAsCurrent");
                    assertThat(exception.getCause()).isInstanceOf(DuplicateKeyException.class);
                })
                .verify();

        verify(termRepository, never()).saveAll(ArgumentMatchers.<Iterable<Term>>any());
    }

    @Test
    @DisplayName("Should reject academic session with end date before start date")
    void shouldRejectAcademicSessionWithEndDateBeforeStartDate() {
        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                "2026/2027 Academic Year",
                LocalDate.of(2027, 9, 7),
                LocalDate.of(2026, 9, 8),
                List.of(firstTermRequest()),
                false);

        StepVerifier.create(schoolService.createSession(request))
                .expectErrorSatisfies(error -> assertInvalidSessionConfig(error, "endDate"))
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
        verify(sessionRepository, never()).save(any(AcademicSession.class));
    }

    @Test
    @DisplayName("Should reject academic session with duplicate term numbers")
    void shouldRejectAcademicSessionWithDuplicateTermNumbers() {
        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                List.of(
                        firstTermRequest(),
                        new CreateAcademicSessionRequest.TermRequest(
                                "Another First Term",
                                1,
                                LocalDate.of(2027, 1, 5),
                                LocalDate.of(2027, 4, 4))),
                false);

        StepVerifier.create(schoolService.createSession(request))
                .expectErrorSatisfies(error -> assertInvalidTermConfig(error, "terms[1].termNumber"))
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject academic session with term outside session range")
    void shouldRejectAcademicSessionWithTermOutsideSessionRange() {
        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                List.of(new CreateAcademicSessionRequest.TermRequest(
                        "First Term",
                        1,
                        LocalDate.of(2026, 8, 25),
                        LocalDate.of(2026, 12, 19))),
                false);

        StepVerifier.create(schoolService.createSession(request))
                .expectErrorSatisfies(error -> assertInvalidTermConfig(error, "terms[0].startDate"))
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject academic session with overlapping terms")
    void shouldRejectAcademicSessionWithOverlappingTerms() {
        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                List.of(
                        firstTermRequest(),
                        new CreateAcademicSessionRequest.TermRequest(
                                "Second Term",
                                2,
                                LocalDate.of(2026, 12, 15),
                                LocalDate.of(2027, 4, 4))),
                false);

        StepVerifier.create(schoolService.createSession(request))
                .expectErrorSatisfies(error -> assertInvalidTermConfig(error, "terms"))
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should set academic session current and move current term")
    void shouldSetAcademicSessionCurrentAndMoveCurrentTerm() {
        AcademicSession previousCurrentSession = currentSession();
        AcademicSession targetSession = academicSession(
                OTHER_SESSION_ID,
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                false);
        Term previousCurrentTerm = currentTerm();
        Term targetFirstTerm = term(
                OTHER_TERM_ID,
                OTHER_SESSION_ID,
                "First Term",
                1,
                false);
        Term targetSecondTerm = term(
                UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901236"),
                OTHER_SESSION_ID,
                "Second Term",
                2,
                false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(targetSession));
        when(sessionRepository.findBySchoolIdAndIsCurrentTrue(SCHOOL_ID))
                .thenReturn(Mono.just(previousCurrentSession));
        when(termRepository.findBySessionId(SESSION_ID)).thenReturn(Flux.just(previousCurrentTerm));
        when(termRepository.save(any(Term.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(OTHER_SESSION_ID))
                .thenAnswer(invocation -> Flux.just(targetFirstTerm, targetSecondTerm));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentSession(OTHER_SESSION_ID))
                .assertNext(response -> {
                    assertThat(response.sessionId()).isEqualTo(OTHER_SESSION_ID);
                    assertThat(response.isCurrent()).isTrue();
                    assertThat(response.terms())
                            .extracting(AcademicSessionResponse.TermResponse::isCurrent)
                            .containsExactly(true, false);
                })
                .verifyComplete();

        assertThat(previousCurrentSession.getIsCurrent()).isFalse();
        assertThat(previousCurrentTerm.getIsCurrent()).isFalse();
        assertThat(targetSession.getIsCurrent()).isTrue();
        assertThat(targetFirstTerm.getIsCurrent()).isTrue();

        verify(termRepository).save(previousCurrentTerm);
        verify(termRepository).save(targetFirstTerm);
        verify(sessionRepository, times(2)).save(any(AcademicSession.class));
        verify(transactionalOperator).transactional(any(Mono.class));
        verify(schoolRepository, never()).findByIdAndIsActiveTrue(SCHOOL_ID);
    }

    @Test
    @DisplayName("Should leave already current session current and repair missing current term")
    void shouldLeaveAlreadyCurrentSessionCurrentAndRepairMissingCurrentTerm() {
        AcademicSession targetSession = currentSession();
        Term firstTerm = currentTerm();
        firstTerm.setIsCurrent(false);
        Term secondTerm = term(OTHER_TERM_ID, SESSION_ID, "Second Term", 2, false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(targetSession));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID))
                .thenAnswer(invocation -> Flux.just(firstTerm, secondTerm));
        when(termRepository.save(any(Term.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentSession(SESSION_ID))
                .assertNext(response -> {
                    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
                    assertThat(response.isCurrent()).isTrue();
                    assertThat(response.terms())
                            .extracting(AcademicSessionResponse.TermResponse::isCurrent)
                            .containsExactly(true, false);
                })
                .verifyComplete();

        assertThat(firstTerm.getIsCurrent()).isTrue();
        verify(termRepository).save(firstTerm);
        verify(sessionRepository, never()).findBySchoolIdAndIsCurrentTrue(SCHOOL_ID);
        verify(sessionRepository, never()).save(any(AcademicSession.class));
        verify(transactionalOperator).transactional(any(Mono.class));
        verify(schoolRepository, never()).findByIdAndIsActiveTrue(SCHOOL_ID);
    }

    @Test
    @DisplayName("Should fail setting current session when current user school is inactive")
    void shouldFailSettingCurrentSessionWhenCurrentUserSchoolIsInactive() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentSession(SESSION_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(sessionRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should fail setting current session when session is missing")
    void shouldFailSettingCurrentSessionWhenSessionIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentSession(OTHER_SESSION_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_FOUND");
                })
                .verify();

        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should fail setting current session when session belongs to another school")
    void shouldFailSettingCurrentSessionWhenSessionBelongsToAnotherSchool() {
        AcademicSession otherSchoolSession = AcademicSession.builder()
                .id(OTHER_SESSION_ID)
                .schoolId(OTHER_SCHOOL_ID)
                .name("Other School Academic Year")
                .startDate(LocalDate.of(2026, 9, 8))
                .endDate(LocalDate.of(2027, 9, 7))
                .isCurrent(false)
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(otherSchoolSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentSession(OTHER_SESSION_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                })
                .verify();

        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should reject setting completed academic session current")
    void shouldRejectSettingCompletedAcademicSessionCurrent() {
        AcademicSession closedSession = academicSession(
                OTHER_SESSION_ID,
                "2024/2025 Academic Year",
                LocalDate.of(2024, 9, 8),
                LocalDate.of(2025, 9, 7),
                false);
        closedSession.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(closedSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentSession(OTHER_SESSION_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    assertThat(exception.getField()).isEqualTo("sessionId");
                })
                .verify();

        verify(sessionRepository, never()).findBySchoolIdAndIsCurrentTrue(SCHOOL_ID);
        verify(sessionRepository, never()).save(any(AcademicSession.class));
        verify(termRepository, never()).findBySessionIdOrderByTermNumberAsc(any(UUID.class));
    }

    @Test
    @DisplayName("Should map set current session duplicate key race to domain error")
    void shouldMapSetCurrentSessionDuplicateKeyRaceToDomainError() {
        AcademicSession targetSession = academicSession(
                OTHER_SESSION_ID,
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(targetSession));
        when(sessionRepository.findBySchoolIdAndIsCurrentTrue(SCHOOL_ID)).thenReturn(Mono.empty());
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("idx_one_current_session")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentSession(OTHER_SESSION_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CURRENT_SESSION_CONFLICT");
                    assertThat(exception.getField()).isEqualTo("sessionId");
                    assertThat(exception.getCause()).isInstanceOf(DuplicateKeyException.class);
                })
                .verify();

        verify(termRepository, never()).findBySessionIdOrderByTermNumberAsc(any(UUID.class));
    }

    @Test
    @DisplayName("Should reject null current session id")
    void shouldRejectNullCurrentSessionId() {
        StepVerifier.create(schoolService.setCurrentSession(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_SESSION");
                    assertThat(exception.getField()).isEqualTo("sessionId");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should update academic session fields and terms")
    void shouldUpdateAcademicSessionFieldsAndTerms() {
        AcademicSession session = currentSession();
        Term firstTerm = currentTerm();
        Term secondTerm = termWithDates(
                OTHER_TERM_ID,
                SESSION_ID,
                "Second Term",
                2,
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 4, 4),
                false);
        UpdateSessionRequest request = new UpdateSessionRequest(
                "2025/2026 Revised Academic Year",
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 9, 7),
                List.of(new UpdateSessionRequest.TermUpdate(
                        TERM_ID,
                        "Opening Term",
                        LocalDate.of(2025, 9, 1),
                        LocalDate.of(2025, 12, 18))));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID))
                .thenReturn(Flux.just(firstTerm, secondTerm));
        when(termRepository.save(any(Term.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.updateSession(SESSION_ID, request))
                .assertNext(response -> {
                    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
                    assertThat(response.name()).isEqualTo("2025/2026 Revised Academic Year");
                    assertThat(response.updatedAt()).isNotNull();
                })
                .verifyComplete();

        assertThat(session.getName()).isEqualTo("2025/2026 Revised Academic Year");
        assertThat(session.getStartDate()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(firstTerm.getName()).isEqualTo("Opening Term");
        assertThat(firstTerm.getStartDate()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(firstTerm.getEndDate()).isEqualTo(LocalDate.of(2025, 12, 18));

        verify(termRepository).save(firstTerm);
        verify(termRepository, never()).save(secondTerm);
        verify(sessionRepository).save(session);
        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(schoolRepository, never()).findByIdAndIsActiveTrue(SCHOOL_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should reject empty academic session update")
    void shouldRejectEmptyAcademicSessionUpdate() {
        UpdateSessionRequest request = new UpdateSessionRequest(null, null, null, null);

        StepVerifier.create(schoolService.updateSession(SESSION_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_SESSION_UPDATE");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject updating completed academic session")
    void shouldRejectUpdatingCompletedAcademicSession() {
        AcademicSession closedSession = academicSession(
                OTHER_SESSION_ID,
                "2024/2025 Academic Year",
                LocalDate.of(2024, 9, 8),
                LocalDate.of(2025, 9, 7),
                false);
        closedSession.setStatus("COMPLETED");
        UpdateSessionRequest request = new UpdateSessionRequest(
                "Reopened Academic Year",
                null,
                null,
                null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findById(OTHER_SESSION_ID)).thenReturn(Mono.just(closedSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.updateSession(OTHER_SESSION_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    assertThat(exception.getField()).isEqualTo("sessionId");
                })
                .verify();

        verify(sessionRepository, never()).save(any(AcademicSession.class));
        verify(termRepository, never()).findBySessionIdOrderByTermNumberAsc(any(UUID.class));
    }

    @Test
    @DisplayName("Should reject academic session update with invalid date range")
    void shouldRejectAcademicSessionUpdateWithInvalidDateRange() {
        UpdateSessionRequest request = new UpdateSessionRequest(
                null,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2025, 9, 8),
                null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.updateSession(SESSION_ID, request))
                .expectErrorSatisfies(error -> assertInvalidSessionConfig(error, "endDate"))
                .verify();

        verify(sessionRepository, never()).save(any(AcademicSession.class));
    }

    @Test
    @DisplayName("Should reject session date update that would put existing term outside range")
    void shouldRejectSessionDateUpdateThatWouldPutExistingTermOutsideRange() {
        AcademicSession session = currentSession();
        Term firstTerm = currentTerm();
        UpdateSessionRequest request = new UpdateSessionRequest(
                null,
                null,
                LocalDate.of(2025, 10, 1),
                null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID))
                .thenReturn(Flux.just(firstTerm));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.updateSession(SESSION_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM_CONFIG");
                    assertThat(exception.getMessage()).contains("within the academic session");
                })
                .verify();

        verify(sessionRepository, never()).save(any(AcademicSession.class));
        verify(termRepository, never()).save(any(Term.class));
    }

    @Test
    @DisplayName("Should reject academic session term update for missing term")
    void shouldRejectAcademicSessionTermUpdateForMissingTerm() {
        UpdateSessionRequest request = new UpdateSessionRequest(
                null,
                null,
                null,
                List.of(new UpdateSessionRequest.TermUpdate(
                        OTHER_TERM_ID,
                        "Missing Term",
                        null,
                        null)));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID))
                .thenReturn(Flux.just(currentTerm()));
        when(termRepository.findById(OTHER_TERM_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.updateSession(SESSION_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();

        verify(sessionRepository, never()).save(any(AcademicSession.class));
    }

    @Test
    @DisplayName("Should reject academic session term update for term in another session")
    void shouldRejectAcademicSessionTermUpdateForTermInAnotherSession() {
        Term otherSessionTerm = termWithDates(
                OTHER_TERM_ID,
                OTHER_SESSION_ID,
                "Other Session Term",
                1,
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 12, 19),
                false);
        UpdateSessionRequest request = new UpdateSessionRequest(
                null,
                null,
                null,
                List.of(new UpdateSessionRequest.TermUpdate(
                        OTHER_TERM_ID,
                        "Wrong Term",
                        null,
                        null)));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID))
                .thenReturn(Flux.just(currentTerm()));
        when(termRepository.findById(OTHER_TERM_ID)).thenReturn(Mono.just(otherSessionTerm));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.updateSession(SESSION_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("TERM_NOT_IN_SESSION");
                })
                .verify();

        verify(sessionRepository, never()).save(any(AcademicSession.class));
    }

    @Test
    @DisplayName("Should reject academic session update that overlaps terms")
    void shouldRejectAcademicSessionUpdateThatOverlapsTerms() {
        Term firstTerm = currentTerm();
        Term secondTerm = termWithDates(
                OTHER_TERM_ID,
                SESSION_ID,
                "Second Term",
                2,
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 4, 4),
                false);
        UpdateSessionRequest request = new UpdateSessionRequest(
                null,
                null,
                null,
                List.of(new UpdateSessionRequest.TermUpdate(
                        OTHER_TERM_ID,
                        "Second Term",
                        LocalDate.of(2025, 12, 15),
                        null)));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID))
                .thenReturn(Flux.just(firstTerm, secondTerm));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.updateSession(SESSION_ID, request))
                .expectErrorSatisfies(error -> assertInvalidTermConfig(error, "terms"))
                .verify();

        verify(termRepository, never()).save(any(Term.class));
        verify(sessionRepository, never()).save(any(AcademicSession.class));
    }

    @Test
    @DisplayName("Should map stale academic session update to conflict error")
    void shouldMapStaleAcademicSessionUpdateToConflictError() {
        UpdateSessionRequest request = new UpdateSessionRequest(
                "Revised Academic Year",
                null,
                null,
                null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale session row")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.updateSession(SESSION_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("version");
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should close academic session and complete all terms")
    void shouldCloseAcademicSessionAndCompleteAllTerms() {
        AcademicSession session = academicSession(
                OTHER_SESSION_ID,
                "2024/2025 Academic Year",
                LocalDate.of(2024, 9, 8),
                LocalDate.of(2025, 9, 7),
                false);
        session.setStatus("ACTIVE");
        Term firstTerm = termWithDates(
                TERM_ID,
                OTHER_SESSION_ID,
                "First Term",
                1,
                LocalDate.of(2024, 9, 8),
                LocalDate.of(2024, 12, 19),
                true);
        Term secondTerm = termWithDates(
                OTHER_TERM_ID,
                OTHER_SESSION_ID,
                "Second Term",
                2,
                LocalDate.of(2025, 1, 5),
                LocalDate.of(2025, 4, 4),
                false);
        firstTerm.setStatus("ACTIVE");
        secondTerm.setStatus("ACTIVE");
        CloseSessionRequest request = new CloseSessionRequest(true, true, "  End of session audit  ");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findBySessionId(OTHER_SESSION_ID)).thenReturn(Flux.just(firstTerm, secondTerm));
        when(termRepository.save(any(Term.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.closeSession(OTHER_SESSION_ID, request))
                .assertNext(response -> {
                    assertThat(response.sessionId()).isEqualTo(OTHER_SESSION_ID);
                    assertThat(response.status()).isEqualTo("COMPLETED");
                    assertThat(response.closedAt()).isNotNull();
                    assertThat(response.termsCompleted()).isEqualTo(2);
                    assertThat(response.studentsArchived()).isZero();
                    assertThat(response.message()).contains("Terms marked completed");
                    assertThat(response.message()).contains("Student archiving is not yet implemented");
                })
                .verifyComplete();

        assertThat(session.getStatus()).isEqualTo("COMPLETED");
        assertThat(session.getIsCurrent()).isFalse();
        assertThat(session.getClosedAt()).isNotNull();
        assertThat(session.getClosedBy()).isEqualTo(ADMIN_USER_ID);
        assertThat(session.getClosedNotes()).isEqualTo("End of session audit");
        assertThat(session.getUpdatedAt()).isEqualTo(session.getClosedAt());
        assertThat(firstTerm.getStatus()).isEqualTo("COMPLETED");
        assertThat(firstTerm.getIsCurrent()).isFalse();
        assertThat(firstTerm.getCompletedAt()).isEqualTo(session.getClosedAt());
        assertThat(firstTerm.getCompletedBy()).isEqualTo(ADMIN_USER_ID);
        assertThat(secondTerm.getStatus()).isEqualTo("COMPLETED");
        assertThat(secondTerm.getCompletedBy()).isEqualTo(ADMIN_USER_ID);

        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(sessionRepository).findByIdForUpdate(OTHER_SESSION_ID);
        verify(sessionRepository).save(session);
        verify(termRepository).save(firstTerm);
        verify(termRepository).save(secondTerm);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should close academic session without completing terms")
    void shouldCloseAcademicSessionWithoutCompletingTerms() {
        AcademicSession session = academicSession(
                OTHER_SESSION_ID,
                "2024/2025 Academic Year",
                LocalDate.of(2024, 9, 8),
                LocalDate.of(2025, 9, 7),
                false);
        CloseSessionRequest request = new CloseSessionRequest(false, false, null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(session));
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.closeSession(OTHER_SESSION_ID, request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("COMPLETED");
                    assertThat(response.termsCompleted()).isZero();
                    assertThat(response.studentsArchived()).isZero();
                    assertThat(response.message()).isEqualTo("Session closed.");
                })
                .verifyComplete();

        assertThat(session.getStatus()).isEqualTo("COMPLETED");
        assertThat(session.getClosedNotes()).isNull();
        verify(termRepository, never()).findBySessionId(any(UUID.class));
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("Should reject closing current academic session")
    void shouldRejectClosingCurrentAcademicSession() {
        AcademicSession session = currentSession();
        CloseSessionRequest request = new CloseSessionRequest(true, false, "Close current");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.closeSession(SESSION_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CANNOT_CLOSE_CURRENT_SESSION");
                    assertThat(exception.getField()).isEqualTo("sessionId");
                })
                .verify();

        verify(termRepository, never()).findBySessionId(any(UUID.class));
        verify(sessionRepository, never()).save(any(AcademicSession.class));
    }

    @Test
    @DisplayName("Should reject closing already completed academic session")
    void shouldRejectClosingAlreadyCompletedAcademicSession() {
        AcademicSession session = academicSession(
                OTHER_SESSION_ID,
                "2024/2025 Academic Year",
                LocalDate.of(2024, 9, 8),
                LocalDate.of(2025, 9, 7),
                false);
        session.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(session));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.closeSession(OTHER_SESSION_ID, new CloseSessionRequest(true, false, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                })
                .verify();

        verify(termRepository, never()).findBySessionId(any(UUID.class));
        verify(sessionRepository, never()).save(any(AcademicSession.class));
    }

    @Test
    @DisplayName("Should reject closing session when school is inactive")
    void shouldRejectClosingSessionWhenSchoolIsInactive() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.closeSession(OTHER_SESSION_ID, new CloseSessionRequest(false, false, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(sessionRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(sessionRepository, never()).save(any(AcademicSession.class));
    }

    @Test
    @DisplayName("Should reject closing session in another school")
    void shouldRejectClosingSessionInAnotherSchool() {
        AcademicSession otherSchoolSession = AcademicSession.builder()
                .id(OTHER_SESSION_ID)
                .schoolId(OTHER_SCHOOL_ID)
                .name("Other School Academic Year")
                .startDate(LocalDate.of(2024, 9, 8))
                .endDate(LocalDate.of(2025, 9, 7))
                .isCurrent(false)
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(otherSchoolSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.closeSession(OTHER_SESSION_ID, new CloseSessionRequest(false, false, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                })
                .verify();

        verify(sessionRepository, never()).save(any(AcademicSession.class));
    }

    @Test
    @DisplayName("Should map stale close session save to conflict error")
    void shouldMapStaleCloseSessionSaveToConflictError() {
        AcademicSession session = academicSession(
                OTHER_SESSION_ID,
                "2024/2025 Academic Year",
                LocalDate.of(2024, 9, 8),
                LocalDate.of(2025, 9, 7),
                false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(session));
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale session row")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.closeSession(OTHER_SESSION_ID, new CloseSessionRequest(false, false, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("version");
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null close session id")
    void shouldRejectNullCloseSessionId() {
        StepVerifier.create(schoolService.closeSession(null, new CloseSessionRequest(false, false, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_SESSION");
                    assertThat(exception.getField()).isEqualTo("sessionId");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should return school by id")
    void shouldReturnSchoolById() {
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.empty());

        StepVerifier.create(schoolService.getSchoolById(SCHOOL_ID))
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.code()).isEqualTo("GIS");
                    assertThat(response.status()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fail school by id lookup when school is missing")
    void shouldFailSchoolByIdLookupWhenSchoolIsMissing() {
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(schoolService.getSchoolById(SCHOOL_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    assertThat(exception.getMessage()).contains(SCHOOL_ID.toString());
                })
                .verify();
    }

    @Test
    @DisplayName("Should update school through versioned save")
    void shouldUpdateSchoolThroughVersionedSave() {
        UpdateSchoolRequest request = new UpdateSchoolRequest(
                "updated@gis.edu",
                "+2348011111111",
                null,
                "Ikeja",
                null,
                "https://cdn.example.com/logo.png",
                null,
                null);
        School existingSchool = existingSchool();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(existingSchool));
        when(schoolRepository.save(any(School.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.empty());

        StepVerifier.create(schoolService.updateSchool(request))
                .assertNext(response -> {
                    assertThat(response).isInstanceOf(SchoolResponse.class);
                    assertThat(response.email()).isEqualTo("updated@gis.edu");
                    assertThat(response.phone()).isEqualTo("+2348011111111");
                    assertThat(response.city()).isEqualTo("Ikeja");
                    assertThat(response.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
                })
                .verifyComplete();

        ArgumentCaptor<School> schoolCaptor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(schoolCaptor.capture());
        School savedSchool = schoolCaptor.getValue();
        assertThat(savedSchool.getId()).isEqualTo(SCHOOL_ID);
        assertThat(savedSchool.getVersion()).isEqualTo(7);
        assertThat(savedSchool.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update only provided fields and preserve omitted fields")
    void shouldUpdateOnlyProvidedFieldsAndPreserveOmittedFields() {
        UpdateSchoolRequest request = new UpdateSchoolRequest(
                null,
                null,
                "34 Updated Avenue",
                null,
                "Ogun",
                null,
                new UpdateSchoolRequest.PaymentConfig(
                        "654321",
                        "NEWGIS",
                        List.of("BANK_TRANSFER", "CARD")),
                new UpdateSchoolRequest.SmsConfig(
                        "AFRICASTALKING",
                        "secret-key",
                        "gis-user",
                        "GIS"));
        School existingSchool = existingSchool();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(existingSchool));
        when(schoolRepository.save(any(School.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.empty());

        StepVerifier.create(schoolService.updateSchool(request))
                .assertNext(response -> {
                    assertThat(response.email()).isEqualTo("hello@gis.edu");
                    assertThat(response.phone()).isEqualTo("+2348012345678");
                    assertThat(response.address()).isEqualTo("34 Updated Avenue");
                    assertThat(response.city()).isEqualTo("Lagos");
                    assertThat(response.state()).isEqualTo("Ogun");
                    assertThat(response.logoUrl()).isNull();
                    assertThat(response.paymentConfig())
                            .containsEntry("paystackPublicKey", "654321")
                            .containsEntry("paystackSubaccountCode", "NEWGIS");
                    assertThat(response.paymentConfig().get("acceptedPaymentMethods"))
                            .isEqualTo(List.of("BANK_TRANSFER", "CARD"));
                })
                .verifyComplete();

        ArgumentCaptor<School> schoolCaptor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(schoolCaptor.capture());
        School savedSchool = schoolCaptor.getValue();
        assertThat(savedSchool.getEmail()).isEqualTo("hello@gis.edu");
        assertThat(savedSchool.getPhone()).isEqualTo("+2348012345678");
        assertThat(savedSchool.getAddress()).isEqualTo("34 Updated Avenue");
        assertThat(savedSchool.getCity()).isEqualTo("Lagos");
        assertThat(savedSchool.getState()).isEqualTo("Ogun");
        assertThat(savedSchool.getPaymentConfig().path("paystackPublicKey").asText()).isEqualTo("654321");
        assertThat(savedSchool.getSmsConfig().path("provider").asText()).isEqualTo("AFRICASTALKING");
        assertThat(savedSchool.getSmsConfig().path("senderId").asText()).isEqualTo("GIS");
    }

    @Test
    @DisplayName("Should fail update when current user school is missing")
    void shouldFailUpdateWhenCurrentUserSchoolIsMissing() {
        UpdateSchoolRequest request = new UpdateSchoolRequest(
                "updated@gis.edu",
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(schoolService.updateSchool(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    assertThat(exception.getMessage()).isEqualTo("School not found");
                })
                .verify();

        verify(schoolRepository, never()).save(any(School.class));
    }

    @Test
    @DisplayName("Should map stale school update to conflict error")
    void shouldMapStaleSchoolUpdateToConflictError() {
        UpdateSchoolRequest request = new UpdateSchoolRequest(
                "updated@gis.edu",
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(schoolRepository.save(any(School.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale school row")));

        StepVerifier.create(schoolService.updateSchool(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("version");
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should deactivate school successfully")
    void shouldDeactivateSchoolSuccessfully() {
        when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(schoolRepository.save(any(School.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(schoolService.deactivateSchool(SCHOOL_ID))
                .verifyComplete();

        ArgumentCaptor<School> schoolCaptor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(schoolCaptor.capture());
        School savedSchool = schoolCaptor.getValue();
        assertThat(savedSchool.getIsActive()).isFalse();
        assertThat(savedSchool.getVersion()).isEqualTo(7);
        assertThat(savedSchool.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should fail deactivation when school is missing")
    void shouldFailDeactivationWhenSchoolIsMissing() {
        when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(schoolService.deactivateSchool(SCHOOL_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    assertThat(exception.getMessage()).contains(SCHOOL_ID.toString());
                })
                .verify();

        verify(schoolRepository, never()).save(any(School.class));
    }

    @Test
    @DisplayName("Should map stale school deactivation to conflict error")
    void shouldMapStaleSchoolDeactivationToConflictError() {
        when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(schoolRepository.save(any(School.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale school row")));

        StepVerifier.create(schoolService.deactivateSchool(SCHOOL_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("version");
                })
                .verify();
    }

    @Test
    @DisplayName("Should list active schools with current term and pagination metadata")
    void shouldListActiveSchoolsWithCurrentTermAndPaginationMetadata() {
        Pageable pageable = PageRequest.of(1, 2);
        School activeSchool = schoolSummarySchool(SCHOOL_ID, "Grace International School", "GIS", true);

        when(schoolRepository.findByActiveStatus(true, 2, 2L)).thenReturn(Flux.just(activeSchool));
        when(schoolRepository.countByActiveStatus(true)).thenReturn(Mono.just(5L));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(currentTerm()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(currentSession()));

        StepVerifier.create(schoolService.listSchools("ACTIVE", pageable))
                .assertNext(response -> {
                    assertThat(response.page()).isEqualTo(1);
                    assertThat(response.size()).isEqualTo(2);
                    assertThat(response.totalElements()).isEqualTo(5);
                    assertThat(response.totalPages()).isEqualTo(3);
                    assertThat(response.content()).hasSize(1);

                    SchoolSummaryResponse summary = response.content().getFirst();
                    assertThat(summary.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(summary.name()).isEqualTo("Grace International School");
                    assertThat(summary.code()).isEqualTo("GIS");
                    assertThat(summary.status()).isEqualTo("ACTIVE");
                    assertThat(summary.currentTerm()).isEqualTo("First Term 2025/2026 Academic Year");
                })
                .verifyComplete();

        verify(schoolRepository).findByActiveStatus(true, 2, 2L);
        verify(schoolRepository).countByActiveStatus(true);
    }

    @Test
    @DisplayName("Should list schools with dynamic student counts, active users, and collection rate")
    void shouldListSchoolsWithDynamicStats() {
        Pageable pageable = PageRequest.of(0, 10);
        School activeSchool = schoolSummarySchool(SCHOOL_ID, "Grace International School", "GIS", true);

        when(schoolRepository.findByActiveStatus(true, 10, 0L)).thenReturn(Flux.just(activeSchool));
        when(schoolRepository.countByActiveStatus(true)).thenReturn(Mono.just(1L));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(currentTerm()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        
        when(studentRepository.countBySchoolIdAndDeletedAtIsNull(SCHOOL_ID)).thenReturn(Mono.just(15L));
        when(userRepository.countBySchoolIdWithFilters(SCHOOL_ID, null, true, null)).thenReturn(Mono.just(5L));
        
        DashboardSummaryStats stats = new DashboardSummaryStats(
                BigDecimal.valueOf(1000), // expected
                BigDecimal.valueOf(750),  // collected
                BigDecimal.valueOf(250),  // outstanding
                5, 3, 7
        );
        when(feeReportingRepository.getDashboardSummary(SCHOOL_ID, TERM_ID)).thenReturn(Mono.just(stats));

        StepVerifier.create(schoolService.listSchools("ACTIVE", pageable))
                .assertNext(response -> {
                    assertThat(response.content()).hasSize(1);
                    SchoolSummaryResponse summary = response.content().getFirst();
                    assertThat(summary.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(summary.studentCount()).isEqualTo(15);
                    assertThat(summary.activeUsers()).isEqualTo(5);
                    assertThat(summary.collectionRate()).isEqualTo(75.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should list inactive schools when status is inactive")
    void shouldListInactiveSchoolsWhenStatusIsInactive() {
        Pageable pageable = PageRequest.of(0, 10);
        School inactiveSchool = schoolSummarySchool(OTHER_SCHOOL_ID, "Inactive School", "INS", false);

        when(schoolRepository.findByActiveStatus(false, 10, 0L)).thenReturn(Flux.just(inactiveSchool));
        when(schoolRepository.countByActiveStatus(false)).thenReturn(Mono.just(1L));
        when(termRepository.findCurrentTermsBySchoolId(OTHER_SCHOOL_ID)).thenReturn(Flux.empty());

        StepVerifier.create(schoolService.listSchools("inactive", pageable))
                .assertNext(response -> {
                    assertThat(response.totalElements()).isEqualTo(1);
                    assertThat(response.totalPages()).isEqualTo(1);
                    assertThat(response.content()).hasSize(1);
                    assertThat(response.content().getFirst().status()).isEqualTo("INACTIVE");
                    assertThat(response.content().getFirst().currentTerm()).isNull();
                })
                .verifyComplete();

        verify(schoolRepository).findByActiveStatus(false, 10, 0L);
        verify(schoolRepository).countByActiveStatus(false);
    }

    @Test
    @DisplayName("Should list all schools when status is all")
    void shouldListAllSchoolsWhenStatusIsAll() {
        Pageable pageable = PageRequest.of(0, 20);
        School activeSchool = schoolSummarySchool(SCHOOL_ID, "Grace International School", "GIS", true);
        School inactiveSchool = schoolSummarySchool(OTHER_SCHOOL_ID, "Inactive School", "INS", false);

        when(schoolRepository.findByActiveStatus(isNull(), eq(20), eq(0L)))
                .thenReturn(Flux.just(activeSchool, inactiveSchool));
        when(schoolRepository.countByActiveStatus(isNull())).thenReturn(Mono.just(2L));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID)).thenReturn(Flux.empty());
        when(termRepository.findCurrentTermsBySchoolId(OTHER_SCHOOL_ID)).thenReturn(Flux.empty());

        StepVerifier.create(schoolService.listSchools(" all ", pageable))
                .assertNext(response -> {
                    assertThat(response.totalElements()).isEqualTo(2);
                    assertThat(response.totalPages()).isEqualTo(1);
                    assertThat(response.content())
                            .extracting(SchoolSummaryResponse::status)
                            .containsExactly("ACTIVE", "INACTIVE");
                })
                .verifyComplete();

        verify(schoolRepository).findByActiveStatus(isNull(), eq(20), eq(0L));
        verify(schoolRepository).countByActiveStatus(isNull());
    }

    @Test
    @DisplayName("Should return empty school page")
    void shouldReturnEmptySchoolPage() {
        Pageable pageable = PageRequest.of(0, 10);

        when(schoolRepository.findByActiveStatus(true, 10, 0L)).thenReturn(Flux.empty());
        when(schoolRepository.countByActiveStatus(true)).thenReturn(Mono.just(0L));

        Mono<PageResponse<SchoolSummaryResponse>> result = schoolService.listSchools(null, pageable);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.content()).isEmpty();
                    assertThat(response.page()).isZero();
                    assertThat(response.size()).isEqualTo(10);
                    assertThat(response.totalElements()).isZero();
                    assertThat(response.totalPages()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject invalid school list status")
    void shouldRejectInvalidSchoolListStatus() {
        Pageable pageable = PageRequest.of(0, 10);

        StepVerifier.create(schoolService.listSchools("ARCHIVED", pageable))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_STATUS");
                    assertThat(exception.getField()).isEqualTo("status");
                    assertThat(exception.getMessage()).contains("ACTIVE, INACTIVE, or ALL");
                })
                .verify();

        verify(schoolRepository, never()).findByActiveStatus(any(), anyInt(), anyLong());
        verify(schoolRepository, never()).countByActiveStatus(any());
    }

    @Test
    @DisplayName("Should create school aggregate, admin, and outbox event")
    void shouldCreateSchoolAggregateAdminAndOutboxEvent() {
        CreateSchoolRequest request = validRequestWithTermConfig(
                new CreateSchoolRequest.TermConfig(0, List.of(), null));

        when(schoolRepository.findByCode(request.code())).thenReturn(Mono.empty());
        when(schoolRepository.save(any(School.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(sessionRepository.save(any(AcademicSession.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(termRepository.saveAll(ArgumentMatchers.<Iterable<Term>>any()))
                .thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
        when(keycloakAdminService.createStaffUser(any(), any(UUID.class), eq(request.name())))
                .thenReturn(Mono.just(new KeycloakUserResult(ADMIN_KEYCLOAK_ID, "tempPassword")));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Mono<CreateSchoolResponse> result = schoolService.createSchool(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.name()).isEqualTo(request.name());
                    assertThat(response.code()).isEqualTo(request.code());
                    assertThat(response.adminUserCreated()).isTrue();
                    assertThat(response.adminTemporaryPassword()).isEqualTo("Sent via email");
                    assertThat(response.message()).contains(request.adminUser().email());
                })
                .verifyComplete();

        ArgumentCaptor<Iterable<Term>> termsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(termRepository).saveAll(termsCaptor.capture());
        List<Term> terms = new ArrayList<>();
        termsCaptor.getValue().forEach(terms::add);
        assertThat(terms)
                .extracting(Term::getName)
                .containsExactly("First Term", "Second Term", "Third Term");
        assertThat(terms).filteredOn(Term::getIsCurrent).hasSize(1);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("SCHOOL_CREATED");
        assertThat(event.getAggregateType()).isEqualTo("SCHOOL");
        assertThat(event.getPayload().path("schoolName").asText()).isEqualTo(request.name());
        assertThat(event.getPayload().path("schoolCode").asText()).isEqualTo(request.code());
        assertThat(event.getPayload().path("adminKeycloakId").asText())
                .isEqualTo(ADMIN_KEYCLOAK_ID.toString());
        assertThat(event.getPayload().path("termIds")).hasSize(3);
    }

    @Test
    @DisplayName("Should return conflict error when school code already exists")
    void shouldReturnConflictErrorWhenSchoolCodeAlreadyExists() {
        CreateSchoolRequest request = validRequestWithTermConfig(null);
        when(schoolRepository.findByCode(request.code()))
                .thenReturn(Mono.just(School.builder().id(UUID.randomUUID()).code(request.code()).build()));

        StepVerifier.create(schoolService.createSchool(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("code");
                })
                .verify();

        verify(schoolRepository, never()).save(any(School.class));
        verify(keycloakAdminService, never()).createStaffUser(any(), any(UUID.class), any());
    }

    @Test
    @DisplayName("Should map duplicate key race to same school code error")
    void shouldMapDuplicateKeyRaceToSchoolCodeError() {
        CreateSchoolRequest request = validRequestWithTermConfig(null);
        when(schoolRepository.findByCode(request.code())).thenReturn(Mono.empty());
        when(schoolRepository.save(any(School.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate school code")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.createSchool(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("code");
                    assertThat(exception.getCause()).isInstanceOf(DuplicateKeyException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject invalid academic year start format")
    void shouldRejectInvalidAcademicYearStartFormat() {
        CreateSchoolRequest request = validRequestWithTermConfig(
                new CreateSchoolRequest.TermConfig(3, List.of("One", "Two", "Three"), "September"));

        when(schoolRepository.findByCode(request.code())).thenReturn(Mono.empty());
        when(schoolRepository.save(any(School.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.createSchool(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM_CONFIG");
                    assertThat(exception.getField()).isEqualTo("termConfig.academicYearStart");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject academic year start outside Nigerian school range - too early")
    void shouldRejectAcademicYearStartTooEarly() {
        // January is not a valid start month for Nigerian basic education
        CreateSchoolRequest request = validRequestWithTermConfig(
                new CreateSchoolRequest.TermConfig(3, null, "01-15"));

        when(schoolRepository.findByCode(request.code())).thenReturn(Mono.empty());
        when(schoolRepository.save(any(School.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.createSchool(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM_CONFIG");
                    assertThat(exception.getMessage()).contains("outside the valid range");
                    assertThat(exception.getMessage()).contains("08-25");
                    assertThat(exception.getMessage()).contains("09-30");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject academic year start outside Nigerian school range - too late")
    void shouldRejectAcademicYearStartTooLate() {
        // November is not a valid start month for Nigerian basic education
        CreateSchoolRequest request = validRequestWithTermConfig(
                new CreateSchoolRequest.TermConfig(3, null, "11-01"));

        when(schoolRepository.findByCode(request.code())).thenReturn(Mono.empty());
        when(schoolRepository.save(any(School.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.createSchool(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM_CONFIG");
                    assertThat(exception.getMessage()).contains("outside the valid range");
                })
                .verify();
    }

    @Test
    @DisplayName("Should accept valid Nigerian academic year start dates")
    void shouldAcceptValidNigerianAcademicYearStartDates() {
        // Test boundary values and typical dates
        String[] validDates = {"08-25", "09-01", "09-08", "09-15", "09-30"};

        for (String date : validDates) {
            CreateSchoolRequest request = validRequestWithTermConfig(
                    new CreateSchoolRequest.TermConfig(3, null, date));

            when(schoolRepository.findByCode(request.code())).thenReturn(Mono.empty());
            when(schoolRepository.save(any(School.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(sessionRepository.save(any(AcademicSession.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(termRepository.saveAll(ArgumentMatchers.<Iterable<Term>>any()))
                    .thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
            when(keycloakAdminService.createStaffUser(any(), any(UUID.class), eq(request.name())))
                    .thenReturn(Mono.just(new KeycloakUserResult(ADMIN_KEYCLOAK_ID, "tempPassword")));
            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(transactionalOperator.transactional(any(Mono.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Should complete without error
            StepVerifier.create(schoolService.createSchool(request))
                    .expectNextCount(1)
                    .verifyComplete();

            // Reset mocks for next iteration
            reset(schoolRepository, sessionRepository, termRepository, 
                  keycloakAdminService, outboxEventRepository, transactionalOperator);
        }
    }

    // ========================================================================
    // SET CURRENT TERM TESTS
    // ========================================================================

    @Test
    @DisplayName("Should set term as current successfully")
    void shouldSetTermAsCurrentSuccessfully() {
        Term targetTerm = term(TERM_ID, SESSION_ID, "Second Term", 2, false);
        Term previousCurrentTerm = term(OTHER_TERM_ID, SESSION_ID, "First Term", 1, true);
        AcademicSession session = currentSession();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findByIdForUpdate(TERM_ID)).thenReturn(Mono.just(targetTerm));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID))
                .thenReturn(Flux.just(previousCurrentTerm, targetTerm));
        when(termRepository.save(any(Term.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .assertNext(response -> {
                    assertThat(response.termId()).isEqualTo(TERM_ID);
                    assertThat(response.name()).isEqualTo("Second Term");
                    assertThat(response.sessionName()).isEqualTo("2025/2026 Academic Year");
                    assertThat(response.isCurrent()).isTrue();
                    assertThat(response.previousCurrentTerm()).isNotNull();
                    assertThat(response.previousCurrentTerm().termId()).isEqualTo(OTHER_TERM_ID);
                    assertThat(response.previousCurrentTerm().name()).isEqualTo("First Term");
                    assertThat(response.previousCurrentTerm().status()).isEqualTo("COMPLETED");
                })
                .verifyComplete();

        verify(transactionalOperator).transactional(any(Mono.class));
        verify(termRepository).findByIdForUpdate(TERM_ID);
        verify(termRepository).save(previousCurrentTerm);
        verify(termRepository).save(targetTerm);
        assertThat(previousCurrentTerm.getIsCurrent()).isFalse();
        assertThat(previousCurrentTerm.getStatus()).isEqualTo("COMPLETED");
        assertThat(previousCurrentTerm.getCompletedAt()).isNotNull();
        assertThat(previousCurrentTerm.getCompletedBy()).isEqualTo(ADMIN_USER_ID);
        assertThat(targetTerm.getIsCurrent()).isTrue();
        assertThat(targetTerm.getStatus()).isEqualTo("ACTIVE");
        assertThat(targetTerm.getCompletedAt()).isNull();
        assertThat(targetTerm.getCompletedBy()).isNull();
    }

    @Test
    @DisplayName("Should return early when term is already current (idempotency)")
    void shouldReturnEarlyWhenTermIsAlreadyCurrent() {
        Term currentTerm = currentTerm();
        AcademicSession session = currentSession();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findByIdForUpdate(TERM_ID)).thenReturn(Mono.just(currentTerm));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .assertNext(response -> {
                    assertThat(response.termId()).isEqualTo(TERM_ID);
                    assertThat(response.isCurrent()).isTrue();
                    // No previous term since it was already current
                    assertThat(response.previousCurrentTerm()).isNull();
                })
                .verifyComplete();

        // Should not save anything since term is already current
        verify(termRepository, never()).save(any(Term.class));
        verify(termRepository, never()).findBySessionIdOrderByTermNumberAsc(any(UUID.class));
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should fail when term id is null")
    void shouldFailWhenTermIdIsNull() {
        StepVerifier.create(schoolService.setCurrentTerm(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM");
                    assertThat(exception.getField()).isEqualTo("termId");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
        verify(termRepository, never()).findByIdForUpdate(any(UUID.class));
    }

    @Test
    @DisplayName("Should fail when term is not found")
    void shouldFailWhenTermIsNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findByIdForUpdate(TERM_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();

        verify(transactionalOperator).transactional(any(Mono.class));
        verify(termRepository).findByIdForUpdate(TERM_ID);
    }

    @Test
    @DisplayName("Should fail setting current term when current user school is inactive")
    void shouldFailSettingCurrentTermWhenCurrentUserSchoolIsInactive() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(termRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should fail when term does not belong to user's school")
    void shouldFailWhenTermDoesNotBelongToUsersSchool() {
        Term term = term(TERM_ID, OTHER_SESSION_ID, "First Term", 1, false);
        AcademicSession otherSession = academicSession(
                OTHER_SESSION_ID,
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                false);
        otherSession.setSchoolId(OTHER_SCHOOL_ID);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findByIdForUpdate(TERM_ID)).thenReturn(Mono.just(term));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(otherSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("TERM_NOT_IN_SCHOOL");
                })
                .verify();

        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should fail when term is not in the current academic session")
    void shouldFailWhenTermIsNotInCurrentAcademicSession() {
        Term term = term(TERM_ID, OTHER_SESSION_ID, "First Term", 1, false);
        AcademicSession nonCurrentSession = academicSession(
                OTHER_SESSION_ID,
                "2024/2025 Academic Year",
                LocalDate.of(2024, 9, 8),
                LocalDate.of(2025, 9, 7),
                false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findByIdForUpdate(TERM_ID)).thenReturn(Mono.just(term));
        when(sessionRepository.findByIdForUpdate(OTHER_SESSION_ID)).thenReturn(Mono.just(nonCurrentSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("TERM_NOT_IN_CURRENT_SESSION");
                    assertThat(exception.getField()).isEqualTo("termId");
                })
                .verify();

        verify(termRepository, never()).findBySessionIdOrderByTermNumberAsc(any(UUID.class));
        verify(termRepository, never()).save(any(Term.class));
    }

    @Test
    @DisplayName("Should fail when parent session is completed")
    void shouldFailWhenParentSessionIsCompleted() {
        Term term = term(TERM_ID, SESSION_ID, "First Term", 1, false);
        AcademicSession completedSession = currentSession();
        completedSession.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findByIdForUpdate(TERM_ID)).thenReturn(Mono.just(term));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(completedSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                })
                .verify();

        verify(transactionalOperator).transactional(any(Mono.class));
        verify(termRepository, never()).save(any(Term.class));
    }

    @Test
    @DisplayName("Should map duplicate current term race to domain error")
    void shouldMapDuplicateCurrentTermRaceToDomainError() {
        Term targetTerm = term(TERM_ID, SESSION_ID, "Second Term", 2, false);
        AcademicSession session = currentSession();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findByIdForUpdate(TERM_ID)).thenReturn(Mono.just(targetTerm));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID)).thenReturn(Flux.empty());
        when(termRepository.save(targetTerm))
                .thenReturn(Mono.error(new DuplicateKeyException("idx_one_current_term")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CURRENT_TERM_CONFLICT");
                    assertThat(exception.getField()).isEqualTo("termId");
                    assertThat(exception.getCause()).isInstanceOf(DuplicateKeyException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should use default terms per year when config is missing or invalid")
    void shouldUseDefaultTermsPerYearWhenConfigIsMissing() {
        // Branch 1: config is null
        CreateSchoolRequest request1 = validRequestWithTermConfig(null);
        // You can use reflection to access the private method,
        // or just trigger the creation logic and verify terms generated
        assertThat(invokeResolveTermsPerYear(request1)).isEqualTo(3);

        // Branch 2: termsPerYear <= 0
        CreateSchoolRequest request2 = validRequestWithTermConfig(
                new CreateSchoolRequest.TermConfig(0, null, null));
        assertThat(invokeResolveTermsPerYear(request2)).isEqualTo(3);
    }

    @Test
    @DisplayName("Should resolve term names with various edge cases")
    void shouldResolveTermNamesEdgeCases() {
        // Branch: names is null, triggers default names
        CreateSchoolRequest request1 = validRequestWithTermConfig(
                new CreateSchoolRequest.TermConfig(3, null, null));
        assertThat(invokeResolveTermNames(request1, 3)).hasSize(3).contains("First Term");

        // Branch: names contains blanks, triggers filter
        CreateSchoolRequest request2 = validRequestWithTermConfig(
                new CreateSchoolRequest.TermConfig(2, List.of("Term1", "  "), null));
        assertThat(invokeResolveTermNames(request2, 2)).hasSize(2).contains("Term 2");

        // Branch: names.size < termsPerYear, triggers for-loop
        CreateSchoolRequest request3 = validRequestWithTermConfig(
                new CreateSchoolRequest.TermConfig(4, List.of("T1"), null));
        List<String> names = invokeResolveTermNames(request3, 4);
        assertThat(names).contains("Term 2", "Term 3", "Term 4");
    }
    @SuppressWarnings("unchecked")
    private int invokeResolveTermsPerYear(CreateSchoolRequest request) {
        try {
            var method = SchoolServiceImpl.class.getDeclaredMethod("resolveTermsPerYear", CreateSchoolRequest.class);
            method.setAccessible(true);
            return (int) method.invoke(schoolService, request);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeResolveTermNames(CreateSchoolRequest request, int termsPerYear) {
        try {
            var method = SchoolServiceImpl.class.getDeclaredMethod("resolveTermNames", CreateSchoolRequest.class, int.class);
            method.setAccessible(true);
            return (List<String>) method.invoke(schoolService, request, termsPerYear);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    @DisplayName("Should map stale current term update to conflict error")
    void shouldMapStaleCurrentTermUpdateToConflictError() {
        Term targetTerm = term(TERM_ID, SESSION_ID, "Second Term", 2, false);
        AcademicSession session = currentSession();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findByIdForUpdate(TERM_ID)).thenReturn(Mono.just(targetTerm));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID)).thenReturn(Flux.empty());
        when(termRepository.save(targetTerm))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale term row")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("version");
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                })
                .verify();
    }
    @Test
    @DisplayName("Should reject null create session request")
    void shouldRejectNullCreateSessionRequest() {
        StepVerifier.create(schoolService.createSession(null))
                .expectErrorMatches(error -> error instanceof SchoolFeeException &&
                        ((SchoolFeeException) error).getErrorCode().equals("INVALID_SESSION_CONFIG"))
                .verify();
    }
    @Test
    @DisplayName("Should reject session with blank name")
    void shouldRejectSessionWithBlankName() {
        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                "   ", // Blank name
                LocalDate.now(),
                LocalDate.now().plusMonths(1),
                List.of(firstTermRequest()),
                false);

        StepVerifier.create(schoolService.createSession(request))
                .expectErrorMatches(error -> error instanceof SchoolFeeException &&
                        ((SchoolFeeException) error).getField().equals("name"))
                .verify();
    }

    @Test
    @DisplayName("Should validate term update date business rules")
    void shouldValidateTermUpdateDates() throws Exception {
        Term term = currentTerm();
        LocalDate sessionStart = LocalDate.of(2026, 9, 8);
        LocalDate sessionEnd = LocalDate.of(2027, 9, 7);

        // 1. Test startDate == null
        assertThatThrownBy(() -> invokeValidateTermUpdateDates(term, null, LocalDate.now(), sessionStart, sessionEnd))
                .isInstanceOf(SchoolFeeException.class)
                .hasMessage("Term start date is required");

        // 2. Test endDate == null
        assertThatThrownBy(() -> invokeValidateTermUpdateDates(term, LocalDate.now(), null, sessionStart, sessionEnd))
                .isInstanceOf(SchoolFeeException.class)
                .hasMessage("Term end date is required");

        // 3. Test endDate is before startDate
        assertThatThrownBy(() -> invokeValidateTermUpdateDates(term, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1), sessionStart, sessionEnd))
                .isInstanceOf(SchoolFeeException.class)
                .hasMessage("Term end date must be on or after start date");

        // 4. Test date range violation (startDate too early)
        assertThatThrownBy(() -> invokeValidateTermUpdateDates(term, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 1), sessionStart, sessionEnd))
                .isInstanceOf(SchoolFeeException.class)
                .hasMessage("Term dates must fall within the academic session date range");

        // 5. Test date range violation (endDate too late)
        assertThatThrownBy(() -> invokeValidateTermUpdateDates(term, LocalDate.of(2026, 9, 10), LocalDate.of(2027, 10, 1), sessionStart, sessionEnd))
                .isInstanceOf(SchoolFeeException.class)
                .hasMessage("Term dates must fall within the academic session date range");
    }

    private void invokeValidateTermUpdateDates(Term term, LocalDate start, LocalDate end, LocalDate sStart, LocalDate sEnd) throws Exception {
        var method = SchoolServiceImpl.class.getDeclaredMethod("validateTermUpdateDates",
                Term.class, LocalDate.class, LocalDate.class, LocalDate.class, LocalDate.class);
        method.setAccessible(true);
        try {
            method.invoke(schoolService, term, start, end, sStart, sEnd);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause(); // Unwrap the SchoolFeeException
        }
    }

    @Test
    @DisplayName("Should handle case when no previous current term exists")
    void shouldHandleCaseWhenNoPreviousCurrentTermExists() {
        Term term = term(TERM_ID, SESSION_ID, "First Term", 1, false);
        AcademicSession session = currentSession();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(termRepository.findByIdForUpdate(TERM_ID)).thenReturn(Mono.just(term));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(termRepository.findBySessionIdOrderByTermNumberAsc(SESSION_ID)).thenReturn(Flux.empty());
        when(termRepository.save(any(Term.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(schoolService.setCurrentTerm(TERM_ID))
                .assertNext(response -> {
                    assertThat(response.termId()).isEqualTo(TERM_ID);
                    assertThat(response.isCurrent()).isTrue();
                    assertThat(response.previousCurrentTerm()).isNull();
                })
                .verifyComplete();

        verify(termRepository).save(term);
        assertThat(term.getIsCurrent()).isTrue();
    }
    @Test
    @DisplayName("Should test all branches of jsonNodeToMap helper")
    void shouldTestJsonNodeToMapBranches() throws Exception {
        // 1. Test null node branch
        assertThat(invokeJsonNodeToMap(null)).isEmpty();

        // 2. Test node.isNull() branch
        assertThat(invokeJsonNodeToMap(objectMapper.nullNode())).isEmpty();

        // 3. Test successful conversion
        ObjectNode validNode = objectMapper.createObjectNode().put("key", "value");
        Map<String, Object> map = invokeJsonNodeToMap(validNode);
        assertThat(map).containsEntry("key", "value");

        // 4. Test catch block branch (trigger exception)
        // We pass an ArrayNode to a method expecting a Map.class,
        // which will cause objectMapper.convertValue to throw an IllegalArgumentException.
        JsonNode arrayNode = objectMapper.createArrayNode().add("not-a-map");
        Map<String, Object> resultFromError = invokeJsonNodeToMap(arrayNode);
        assertThat(resultFromError).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeJsonNodeToMap(JsonNode node) throws Exception {
        var method = SchoolServiceImpl.class.getDeclaredMethod("jsonNodeToMap", JsonNode.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(schoolService, node);
    }

    private CreateSchoolRequest validRequestWithTermConfig(CreateSchoolRequest.TermConfig termConfig) {
        return new CreateSchoolRequest(
                "Grace International School",
                "GIS",
                "hello@gis.edu",
                "+2348012345678",
                "12 School Road",
                "Lagos",
                "Lagos",
                null,
                null,
                null,
                null,
                termConfig,
                new CreateSchoolRequest.AdminUser(
                        "admin@gis.edu",
                        "Ada",
                        "Lovelace",
                "+2348098765432"));
    }

    private CreateAcademicSessionRequest validCreateAcademicSessionRequest(boolean setAsCurrent) {
        return new CreateAcademicSessionRequest(
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                List.of(secondTermRequest(), firstTermRequest()),
                setAsCurrent);
    }

    private CreateAcademicSessionRequest.TermRequest firstTermRequest() {
        return new CreateAcademicSessionRequest.TermRequest(
                "First Term",
                1,
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 12, 19));
    }

    private CreateAcademicSessionRequest.TermRequest secondTermRequest() {
        return new CreateAcademicSessionRequest.TermRequest(
                "Second Term",
                2,
                LocalDate.of(2027, 1, 5),
                LocalDate.of(2027, 4, 4));
    }

    private void assertInvalidSessionConfig(Throwable error, String field) {
        assertThat(error).isInstanceOf(SchoolFeeException.class);
        SchoolFeeException exception = (SchoolFeeException) error;
        assertThat(exception.getErrorCode()).isEqualTo("INVALID_SESSION_CONFIG");
        assertThat(exception.getField()).isEqualTo(field);
    }

    private void assertInvalidTermConfig(Throwable error, String field) {
        assertThat(error).isInstanceOf(SchoolFeeException.class);
        SchoolFeeException exception = (SchoolFeeException) error;
        assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM_CONFIG");
        assertThat(exception.getField()).isEqualTo(field);
    }

    private School schoolSummarySchool(UUID schoolId, String name, String code, boolean active) {
        return School.builder()
                .id(schoolId)
                .name(name)
                .code(code)
                .city("Lagos")
                .state("Lagos")
                .isActive(active)
                .createdAt(Instant.now())
                .build();
    }

    private School existingSchool() {
        return School.builder()
                .id(SCHOOL_ID)
                .name("Grace International School")
                .code("GIS")
                .email("hello@gis.edu")
                .phone("+2348012345678")
                .address("12 School Road")
                .city("Lagos")
                .state("Lagos")
                .country("Nigeria")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(7)
                .build();
    }

    private Term currentTerm() {
        return Term.builder()
                .id(TERM_ID)
                .sessionId(SESSION_ID)
                .name("First Term")
                .termNumber((short) 1)
                .startDate(LocalDate.of(2025, 9, 8))
                .endDate(LocalDate.of(2025, 12, 19))
                .isCurrent(true)
                .build();
    }

    private Term term(UUID termId, UUID sessionId, String name, int termNumber, boolean current) {
        return Term.builder()
                .id(termId)
                .sessionId(sessionId)
                .name(name)
                .termNumber((short) termNumber)
                .startDate(LocalDate.of(2026, 9, 8).plusMonths(termNumber - 1L))
                .endDate(LocalDate.of(2026, 12, 19).plusMonths(termNumber - 1L))
                .isCurrent(current)
                .build();
    }

    private Term termWithDates(
            UUID termId,
            UUID sessionId,
            String name,
            int termNumber,
            LocalDate startDate,
            LocalDate endDate,
            boolean current) {
        return Term.builder()
                .id(termId)
                .sessionId(sessionId)
                .name(name)
                .termNumber((short) termNumber)
                .startDate(startDate)
                .endDate(endDate)
                .isCurrent(current)
                .build();
    }

    private AcademicSession currentSession() {
        return AcademicSession.builder()
                .id(SESSION_ID)
                .schoolId(SCHOOL_ID)
                .name("2025/2026 Academic Year")
                .startDate(LocalDate.of(2025, 9, 8))
                .endDate(LocalDate.of(2026, 9, 7))
                .isCurrent(true)
                .build();
    }

    private AcademicSession academicSession(
            UUID sessionId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean current) {
        return AcademicSession.builder()
                .id(sessionId)
                .schoolId(SCHOOL_ID)
                .name(name)
                .startDate(startDate)
                .endDate(endDate)
                .isCurrent(current)
                .build();
    }

    private com.fasterxml.jackson.databind.JsonNode schoolServiceObjectMapperPaymentConfig() {
        return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .build()
                .valueToTree(Map.of(
                        "paystackPublicKey", "123456",
                        "paystackSubaccountCode", "GIS",
                        "acceptedPaymentMethods", List.of("PAYSTACK", "CARD")));
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(ADMIN_USER_ID)
                .schoolId(SCHOOL_ID)
                .schoolName("Grace International School")
                .email("admin@gis.edu")
                .firstName("Ada")
                .lastName("Lovelace")
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }
}
