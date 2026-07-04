package com.fee.app.schoolfeeapp.result.service.impl;


import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.notification.service.SmsService;
import com.fee.app.schoolfeeapp.result.domain.*;
import com.fee.app.schoolfeeapp.result.dto.request.*;
import com.fee.app.schoolfeeapp.result.dto.response.*;
import com.fee.app.schoolfeeapp.result.repository.*;
import com.fee.app.schoolfeeapp.result.service.ResultService;
import com.fee.app.schoolfeeapp.result.service.ScoreComputationEngine;
import com.fee.app.schoolfeeapp.result.utils.ResultPdfGenerator;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.enums.GradeLevel;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class ResultServiceImpl implements ResultService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final String REPORT_JOB_PROCESSING = "PROCESSING";
    private static final String REPORT_JOB_COMPLETED = "COMPLETED";
    private static final List<GradeBand> DEFAULT_GRADE_BANDS = List.of(
            new GradeBand("A1", 75, 100, "Excellent"),
            new GradeBand("B2", 70, 74, "Very Good"),
            new GradeBand("B3", 65, 69, "Good"),
            new GradeBand("C4", 60, 64, "Credit"),
            new GradeBand("C5", 55, 59, "Credit"),
            new GradeBand("C6", 50, 54, "Credit"),
            new GradeBand("D7", 45, 49, "Pass"),
            new GradeBand("E8", 40, 44, "Pass"),
            new GradeBand("F9", 0, 39, "Fail"));
    private static final List<DefaultTrait> DEFAULT_ASSESSMENT_TRAITS = List.of(
            new DefaultTrait("Punctuality", "AFFECTIVE", 1),
            new DefaultTrait("Attentiveness", "AFFECTIVE", 2),
            new DefaultTrait("Neatness", "AFFECTIVE", 3),
            new DefaultTrait("Conduct", "AFFECTIVE", 4),
            new DefaultTrait("Handwriting", "PSYCHOMOTOR", 1),
            new DefaultTrait("Verbal Fluency", "PSYCHOMOTOR", 2),
            new DefaultTrait("Sports", "PSYCHOMOTOR", 3),
            new DefaultTrait("Creativity", "PSYCHOMOTOR", 4));

    private final CaComponentRepository caComponentRepository;
    private final CaScoreRepository caScoreRepository;
    private final ScoreRepository scoreRepository;
    private final FinalScoreRepository finalScoreRepository;
    private final ClassRankingRepository rankingRepository;
    private final GradeConfigRepository gradeConfigRepository;
    private final ReportCommentRepository commentRepository;
    private final PublishedResultRepository publishedResultRepository;
    private final ScoreAuditLogRepository auditLogRepository;
    private final ReportCardTemplateRepository reportCardTemplateRepository;
    private final AssessmentTraitRepository assessmentTraitRepository;
    private final StudentTraitAssessmentRepository studentTraitAssessmentRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final SchoolRepository schoolRepository;
    private final TermRepository termRepository;
    private final AcademicSessionRepository academicSessionRepository;
    private final StudentGuardianRepository guardianRepository;
    private final StudentGuardianLinkRepository guardianLinkRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;
    private final ScoreComputationEngine computationEngine;
    private final ResultPdfGenerator resultPdfGenerator;
    private final SmsService smsService;
    private final Map<UUID, ReportCardJobState> reportCardJobs = new ConcurrentHashMap<>();


    // ========================================================================
    // CA CONFIGURATION
    // ========================================================================

    @Override
    public Mono<CaConfigResponse> configureCa(CaConfigRequest request) {
        return Mono.fromCallable(() -> validateAndNormalizeCaConfig(request))
                .flatMap(config -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                            UUID schoolId = requireSchoolId(user);
                            return transactionalOperator.transactional(
                                    schoolRepository.findActiveByIdForUpdate(schoolId)
                                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                                    "SCHOOL_NOT_FOUND",
                                                    "School not found")))
                                            .then(caScoreRepository.existsBySchoolIdAndActiveComponents(schoolId))
                                            .flatMap(hasScores -> {
                                                if (Boolean.TRUE.equals(hasScores)) {
                                                    return Mono.error(new SchoolFeeException(
                                                            "CA_CONFIG_IN_USE",
                                                            "CA configuration already has recorded scores and cannot be replaced"));
                                                }
                                                return caComponentRepository.deactivateActiveBySchoolId(schoolId)
                                                        .thenMany(Flux.fromIterable(config.components())
                                                                .concatMap(component -> caComponentRepository.insert(
                                                                        toCaComponentEntity(schoolId, component))))
                                                        .collectList();
                                            })
                                            .map(saved -> new CaConfigResponse(
                                                    saved.size(),
                                                    config.examWeightPercentage().doubleValue(),
                                                    "CA configuration updated")));
                        }));
    }

    @Override
    public Mono<CaConfigResponse> getCaConfig() {
        return jwtUtils.getCurrentUser()
                .flatMapMany(user -> caComponentRepository.findBySchoolIdAndIsActiveTrue(requireSchoolId(user)))
                .collectList()
                .map(components -> {
                    BigDecimal caWeight = components.stream()
                            .map(CaComponent::getWeightPercentage)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal examWeight = ONE_HUNDRED.subtract(caWeight);
                    return new CaConfigResponse(
                            components.size(),
                            examWeight.doubleValue(),
                            "Current CA configuration");
                });
    }

    // ========================================================================
    // SCORE ENTRY
    // ========================================================================

    @Override
    public Mono<CaScoreResponse> enterCaScores(CaScoreRequest request) {
        return Mono.fromCallable(() -> validateCaScoreRequest(request))
                .flatMap(validatedRequest -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                            UUID schoolId = requireSchoolId(user);
                            Mono<CaScoreResponse> transaction = resolveLocalUserId(user.getUserId())
                                    .flatMap(localUserId -> verifyCaScoreContext(validatedRequest, schoolId)
                                            .then(verifyNotPublished(schoolId, validatedRequest.termId()))
                                            .thenMany(Flux.fromIterable(validatedRequest.scores())
                                                    .concatMap(scoreReq -> caScoreRepository.insert(
                                                            toCaScoreEntity(validatedRequest, scoreReq, schoolId, localUserId))))
                                            .collectList()
                                            .flatMap(scores -> computeFinalScores(
                                                            validatedRequest.classId(),
                                                            validatedRequest.termId(),
                                                            validatedRequest.subjectId())
                                                    .then(autoComputeRankings(
                                                            validatedRequest.classId(),
                                                            validatedRequest.termId(),
                                                            schoolId))
                                                    .thenReturn(new CaScoreResponse(
                                                    UUID.randomUUID(),
                                                    validatedRequest.classId().toString(),
                                                    validatedRequest.subjectId().toString(),
                                                    validatedRequest.caComponentId().toString(),
                                                    scores.size(),
                                                    "CA scores recorded"))));

                            return transactionalOperator.transactional(transaction)
                                    .onErrorMap(DuplicateKeyException.class, this::duplicateScoreException);
                        }));
    }

    @Override
    public Mono<ExamScoreResponse> enterExamScores(ExamScoreRequest request) {
        return Mono.fromCallable(() -> validateExamScoreRequest(request))
                .flatMap(validatedRequest -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                            UUID schoolId = requireSchoolId(user);
                            Mono<ExamScoreResponse> transaction = resolveLocalUserId(user.getUserId())
                                    .flatMap(localUserId -> verifyExamScoreContext(validatedRequest, schoolId)
                                            .then(verifyNotPublished(schoolId, validatedRequest.termId()))
                                            .thenMany(Flux.fromIterable(validatedRequest.scores())
                                                    .concatMap(scoreReq -> scoreRepository.insert(
                                                            toExamScoreEntity(validatedRequest, scoreReq, schoolId, localUserId))))
                                            .collectList()
                                            .flatMap(scores -> computeFinalScores(
                                                            validatedRequest.classId(),
                                                            validatedRequest.termId(),
                                                            validatedRequest.subjectId())
                                                    .then(autoComputeRankings(
                                                            validatedRequest.classId(),
                                                            validatedRequest.termId(),
                                                            schoolId))
                                                    .thenReturn(new ExamScoreResponse(
                                                            UUID.randomUUID(),
                                                            validatedRequest.classId().toString(),
                                                            validatedRequest.subjectId().toString(),
                                                            scores.size(),
                                                            scores.size(),
                                                            null,
                                                            null,
                                                            null,
                                                            "Exam scores recorded. Final scores computed."))));

                            return transactionalOperator.transactional(transaction)
                                    .onErrorMap(DuplicateKeyException.class, this::duplicateScoreException);
                        }));
    }

    @Override
    public Mono<UpdateScoreResponse> updateScore(UUID scoreId, UpdateScoreRequest request) {
        return Mono.fromCallable(() -> validateUpdateScoreRequest(scoreId, request))
                .flatMap(validatedRequest -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                            UUID schoolId = requireSchoolId(user);
                            Mono<UpdateScoreResponse> transaction = resolveLocalUserId(user.getUserId())
                                    .flatMap(localUserId -> scoreRepository.findByIdAndSchoolIdForUpdate(scoreId, schoolId)
                                            .switchIfEmpty(Mono.error(new SchoolFeeException("SCORE_NOT_FOUND", "Score not found", "scoreId")))
                                            .flatMap(score -> verifyNotPublished(schoolId, score.getTermId())
                                                    .then(Mono.defer(() -> doUpdateScore(score, validatedRequest, schoolId, localUserId)))));

                            return transactionalOperator.transactional(transaction);
                        }));
    }

    private Mono<UpdateScoreResponse> doUpdateScore(
            ResultScore score, UpdateScoreRequest request, UUID schoolId, UUID localUserId) {
        return getCaConfig()
                .flatMap(caConfig -> examRepository.findByIdAndSchoolIdAndTermId(score.getExamId(), schoolId, score.getTermId())
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "EXAM_NOT_FOUND",
                                "Exam not found",
                                "examId")))
                        .flatMap(exam -> normalizeExamMaxScore(exam, caConfig)))
                .flatMap(exam -> {
                    int maxScore = exam.getMaxScore();
                    if (request.score().compareTo(BigDecimal.valueOf(maxScore)) > 0) {
                        return Mono.error(new SchoolFeeException(
                                "INVALID_SCORE",
                                "Score must be between 0 and " + maxScore,
                                "score"));
                    }

                    BigDecimal oldScore = score.getScore();
                    Instant updatedAt = Instant.now();
                    score.setScore(request.score());
                    score.setMaxScore(maxScore);
                    score.setUpdatedAt(updatedAt);

                    return scoreRepository.save(score)
                            .flatMap(saved -> auditScoreChange("EXAM_SCORE", saved.getId(), saved.getStudentId(),
                                    saved.getSubjectId(), saved.getTermId(), oldScore, request.score(),
                                    schoolId, localUserId, request.reason(), updatedAt))
                            .then(computeFinalScores(score.getClassId(), score.getTermId(), score.getSubjectId()))
                            .then(autoComputeRankings(score.getClassId(), score.getTermId(), schoolId))
                            .thenReturn(new UpdateScoreResponse(score.getId(), request.score(), oldScore, updatedAt));
                });
    }

    // ========================================================================
    // RESULTS VIEWING
    // ========================================================================

    @Override
    public Mono<StudentResultResponse> getStudentResult(UUID studentId, UUID termId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, schoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "STUDENT_NOT_FOUND",
                                    "Student not found",
                                    "studentId")))
                            .flatMap(student -> termRepository.findByIdAndSchoolId(termId, schoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "TERM_NOT_FOUND",
                                            "Term not found",
                                            "termId")))
                                    .flatMap(term -> {
                                        Mono<Void> accessCheck = user.isParent()
                                                ? verifyParentAccess(user.getUserId(), studentId)
                                                        .then(Mono.defer(() -> verifyResultsPublished(schoolId, termId)))
                                                : Mono.empty();
                                        return accessCheck.then(Mono.defer(() -> buildStudentResult(student, term, schoolId)));
                                    }));
                });
    }

    @Override
    public Mono<ClassResultSheetResponse> getClassResultSheet(UUID classId, UUID termId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> buildClassResultSheet(classId, termId, user.getSchoolId()));
    }

    @Override
    public Mono<List<MyChildResultResponse>> getMyChildrenResults() {
        return jwtUtils.getCurrentUser()
                .flatMapMany(parentUser -> {
                    if (!parentUser.isParent()) {
                        return Flux.error(new SchoolFeeException("ACCESS_DENIED", "Only parents"));
                    }
                    UUID schoolId = requireSchoolId(parentUser);
                    return guardianRepository.findByKeycloakId(parentUser.getUserId())
                            .flatMapMany(guardian ->
                                    guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardian.getId()))
                            .filter(link -> link != null && link.getStudentId() != null && Boolean.TRUE.equals(link.getCanViewResults()))
                            .map(link -> link.getStudentId())
                            .distinct()
                            .concatMap(studentId -> buildMyChildResult(studentId, schoolId));
                })
                .collectList();
    }

    @Override
    public Mono<List<PublishedTermResultResponse>> getPublishedStudentResults(UUID studentId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    if (!user.isParent()) {
                        return Mono.error(new SchoolFeeException(
                                "ACCESS_DENIED",
                                "Only parents can view a child's published result history"));
                    }
                    return verifyParentAccess(user.getUserId(), studentId)
                            .thenMany(publishedResultRepository.findBySchoolIdOrderByPublishedAtDesc(schoolId))
                            .concatMap(published -> termRepository
                                    .findByIdAndSchoolId(published.getTermId(), schoolId)
                                    .flatMap(term -> buildPublishedTermSummary(
                                            studentId,
                                            term,
                                            schoolId)))
                            .collectList();
                });
    }

    // ========================================================================
    // RANKINGS
    // ========================================================================

    @Override
    public Mono<Integer> recomputeRankings(UUID classId, UUID termId, UUID schoolId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    if (classId == null) {
                        return Mono.error(new SchoolFeeException("INVALID_RANKING_REQUEST", "Class is required", "classId"));
                    }
                    if (termId == null) {
                        return Mono.error(new SchoolFeeException("INVALID_RANKING_REQUEST", "Term is required", "termId"));
                    }
                    UUID effectiveSchoolId = schoolId != null ? schoolId : requireSchoolId(user);
                    return classRepository.findByIdAndSchoolId(classId, effectiveSchoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "CLASS_NOT_FOUND",
                                    "Class not found",
                                    "classId")))
                            .then(termRepository.findByIdAndSchoolId(termId, effectiveSchoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "TERM_NOT_FOUND",
                                            "Term not found",
                                            "termId"))))
                            .then(autoComputeRankings(classId, termId, effectiveSchoolId))
                            .thenMany(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(classId, termId))
                            .count()
                            .map(Long::intValue);
                });
    }

    // ========================================================================
    // REPORT CARDS
    // ========================================================================

    @Override
    public Mono<ReportCardJobResponse> generateReportCards(ReportCardRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    List<UUID> distinctStudentIds = request.studentIds().stream().distinct().toList();
                    if (distinctStudentIds.isEmpty()) {
                        return Mono.error(new SchoolFeeException(
                                "INVALID_REPORT_CARD_REQUEST",
                                "At least one student is required",
                                "studentIds"));
                    }
                    UUID jobId = UUID.randomUUID();
                    return classRepository.findByIdAndSchoolId(request.classId(), schoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "CLASS_NOT_FOUND",
                                    "Class not found",
                                    "classId")))
                            .then(termRepository.findByIdAndSchoolId(request.termId(), schoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "TERM_NOT_FOUND",
                                            "Term not found",
                                            "termId"))))
                            .then(validateReportCardTemplate(request.templateId(), schoolId))
                            .then(verifyStudentsBelongToClass(distinctStudentIds, request.classId(), schoolId))
                            .then(Mono.defer(() -> {
                                int totalStudents = distinctStudentIds.size();
                                reportCardJobs.put(jobId, new ReportCardJobState(
                                        schoolId,
                                        REPORT_JOB_COMPLETED,
                                        totalStudents,
                                        totalStudents,
                                        0,
                                        Instant.now(),
                                        "Report cards generated"));
                                return Mono.just(new ReportCardJobResponse(
                                        jobId,
                                        REPORT_JOB_PROCESSING,
                                        totalStudents,
                                        0,
                                        0,
                                        null,
                                        null,
                                        "Report card generation started"));
                            }));
                });
    }

    @Override
    public Mono<ReportCardJobResponse> getReportCardJobStatus(UUID jobId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    ReportCardJobState state = reportCardJobs.get(jobId);
                    if (state == null || !schoolId.equals(state.schoolId())) {
                        return Mono.error(new SchoolFeeException(
                                "REPORT_CARD_JOB_NOT_FOUND",
                                "Report card job not found",
                                "jobId"));
                    }
                    return Mono.just(new ReportCardJobResponse(
                            jobId,
                            state.status(),
                            state.totalStudents(),
                            state.completedStudents(),
                            state.failedStudents(),
                            null,
                            state.completedAt(),
                            state.message()));
                });
    }

    @Override
    public Mono<ReportCardTemplateResponse> createReportCardTemplate(ReportCardTemplateRequest request) {
        return Mono.fromCallable(() -> validateTemplateRequest(request))
                .flatMap(validated -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                            UUID schoolId = requireSchoolId(user);
                            Instant now = Instant.now();
                            Mono<ReportCardTemplateResponse> transaction = reportCardTemplateRepository
                                    .existsActiveByNormalizedName(schoolId, validated.educationLevel(), validated.name())
                                    .flatMap(exists -> {
                                        if (Boolean.TRUE.equals(exists)) {
                                            return Mono.error(new SchoolFeeException(
                                                    "REPORT_CARD_TEMPLATE_EXISTS",
                                                    "A report-card template with this name already exists for this education level",
                                                    "name"));
                                        }
                                        Mono<Integer> clearDefault = validated.isDefault()
                                                ? reportCardTemplateRepository.clearDefaultForLevel(
                                                        schoolId,
                                                        validated.educationLevel(),
                                                        now)
                                                : Mono.just(0);
                                        return clearDefault.then(reportCardTemplateRepository.insert(
                                                schoolId,
                                                validated.name(),
                                                validated.educationLevel(),
                                                validated.config(),
                                                validated.isDefault(),
                                                now,
                                                now));
                                    })
                                    .map(this::toTemplateResponse);

                            return transactionalOperator.transactional(transaction)
                                    .onErrorMap(DuplicateKeyException.class, ex -> new SchoolFeeException(
                                            "REPORT_CARD_TEMPLATE_EXISTS",
                                            "A report-card template with this name already exists for this education level",
                                            "name",
                                            ex));
                        }));
    }

    @Override
    public Mono<List<ReportCardTemplateResponse>> getReportCardTemplates() {
        return jwtUtils.getCurrentUser()
                .flatMapMany(user -> reportCardTemplateRepository.findActiveBySchoolId(requireSchoolId(user)))
                .map(this::toTemplateResponse)
                .collectList();
    }

    // ========================================================================
    // COMMENTS
    // ========================================================================

    @Override
    public Mono<ReportCommentResponse> addTeacherComment(UUID studentId, UUID termId, String comment) {
        return addTeacherComment(studentId, termId, comment, false);
    }

    @Override
    public Mono<ReportCommentResponse> addTeacherComment(UUID studentId, UUID termId, String comment, boolean autoGenerate) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return resolveLocalUserId(user.getUserId())
                            .flatMap(localUserId -> validateCommentContext(studentId, termId, schoolId)
                                    .then(resolveCommentText(studentId, termId, schoolId, comment, true, autoGenerate))
                                    .flatMap(resolved ->
                                            saveComment(studentId, termId, schoolId, resolved, true, localUserId)));
                });
    }

    @Override
    public Mono<ReportCommentResponse> addPrincipalComment(UUID studentId, UUID termId, String comment) {
        return addPrincipalComment(studentId, termId, comment, false);
    }

    @Override
    public Mono<ReportCommentResponse> addPrincipalComment(UUID studentId, UUID termId, String comment, boolean autoGenerate) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return resolveLocalUserId(user.getUserId())
                            .flatMap(localUserId -> validateCommentContext(studentId, termId, schoolId)
                                    .then(resolveCommentText(studentId, termId, schoolId, comment, false, autoGenerate))
                                    .flatMap(resolved ->
                                            saveComment(studentId, termId, schoolId, resolved, false, localUserId)));
                });
    }

    // ========================================================================
    // PUBLICATION
    // ========================================================================

    @Override
    public Mono<PublishResultResponse> publishResults(UUID termId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return resolveLocalUserId(user.getUserId())
                            .flatMap(localUserId -> termRepository.findByIdAndSchoolId(termId, schoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "TERM_NOT_FOUND",
                                            "Term not found",
                                            "termId")))
                                    .then(Mono.defer(() -> publishResultsForTerm(termId, schoolId, localUserId)))
                                    .onErrorResume(DuplicateKeyException.class, ex -> Mono.error(new SchoolFeeException(
                                            "RESULTS_ALREADY_PUBLISHED",
                                            "Results are already published for this term"))));
                });
    }

    @Override
    public Mono<PublishResultResponse> unpublishResults(UUID termId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    Mono<PublishResultResponse> transaction = resolveLocalUserId(user.getUserId())
                            .flatMap(localUserId -> termRepository.findByIdAndSchoolId(termId, schoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "TERM_NOT_FOUND",
                                            "Term not found",
                                            "termId")))
                                    .then(Mono.defer(() -> publishedResultRepository.findBySchoolIdAndTermId(schoolId, termId)
                                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                                    "RESULTS_NOT_PUBLISHED",
                                                    "Results have not been published yet.")))
                                            .flatMap(published -> publishedResultRepository.delete(published))
                                            .thenReturn(toUnpublishResponse(termId, localUserId)))));
                    return transactionalOperator.transactional(transaction);
                });
    }

    // ========================================================================
    // GRADING RULES
    // ========================================================================

    @Override
    public Mono<GradingRuleResponse> configureGradingRules(GradingRuleRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    com.fasterxml.jackson.databind.JsonNode normalizedConfig = normalizeGradingConfig(request.config());
                    return gradeConfigRepository.findBySchoolId(schoolId)
                            .flatMap(existing -> {
                                existing.setConfig(normalizedConfig);
                                existing.setUpdatedAt(Instant.now());
                                return gradeConfigRepository.save(existing);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                GradeConfig config = GradeConfig.builder()
                                        .schoolId(schoolId)
                                        .config(normalizedConfig)
                                        .isActive(true)
                                        .createdAt(Instant.now())
                                        .build();
                                return gradeConfigRepository.save(config);
                            }))
                            .map(saved -> new GradingRuleResponse(schoolId,
                                    countConfiguredGrades(saved.getConfig()),
                                    "Grading rules updated",
                                    saved.getConfig()));
                });
    }

    @Override
    public Mono<GradingRuleResponse> getGradingRules() {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return gradeConfigRepository.findBySchoolId(schoolId)
                            .map(config -> new GradingRuleResponse(schoolId,
                                    countConfiguredGrades(config.getConfig()),
                                    "Current grading rules",
                                    config.getConfig()))
                            .defaultIfEmpty(new GradingRuleResponse(schoolId, 0,
                                    "No grading rules configured",
                                    null));
                });
    }

    @Override
    public Mono<List<SubjectLookupResponse>> getSubjectsForClass(UUID classId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return classRepository.findByIdAndSchoolId(classId, schoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException("CLASS_NOT_FOUND", "Class not found", "classId")))
                            .thenMany(classSubjectRepository.findByClassIdAndIsActiveTrue(classId))
                            .flatMap(cs -> subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(cs.getSubjectId(), schoolId))
                            .map(subject -> new SubjectLookupResponse(subject.getId(), subject.getName(), subject.getCode()))
                            .collectList();
                });
    }

    @Override
    public Mono<List<CaComponentLookupResponse>> getCaComponents() {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return caComponentRepository.findBySchoolIdAndIsActiveTrue(schoolId)
                            .map(comp -> new CaComponentLookupResponse(
                                    comp.getId(),
                                    comp.getName(),
                                    comp.getMaxScore(),
                                    comp.getWeightPercentage() != null ? comp.getWeightPercentage().doubleValue() : 0.0,
                                    comp.getSortOrder()
                            ))
                            .collectList();
                });
    }

    @Override
    public Mono<List<ExamLookupResponse>> getExamsForTerm(UUID termId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return termRepository.findByIdAndSchoolId(termId, schoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException("TERM_NOT_FOUND", "Term not found", "termId")))
                            .thenMany(examRepository.findByTermId(termId))
                            .filter(exam -> exam.getSchoolId().equals(schoolId))
                            .collectList()
                            .zipWith(getCaConfig())
                            .flatMap(tuple -> {
                                List<Exam> exams = tuple.getT1();
                                CaConfigResponse caConfig = tuple.getT2();
                                if (exams.isEmpty()) {
                                    return resolveLocalUserId(user.getUserId())
                                            .flatMap(localUserId -> {
                                                int examMaxScore = configuredExamMaxScore(caConfig);
                                                Exam defaultExam = Exam.builder()
                                                        .id(null) // Let database generate id (forces INSERT in R2DBC)
                                                        .schoolId(schoolId)
                                                        .termId(termId)
                                                        .name("End of Term Exam")
                                                        .examType("END_OF_TERM")
                                                        .maxScore(examMaxScore)
                                                        .weightPercentage(BigDecimal.valueOf(examMaxScore))
                                                        .isPublished(false)
                                                        .createdBy(localUserId)
                                                        .createdAt(Instant.now())
                                                        .updatedAt(Instant.now())
                                                        .build();
                                                return examRepository.save(defaultExam)
                                                        .map(saved -> List.of(new ExamLookupResponse(saved.getId(), saved.getName(), saved.getMaxScore())));
                                            });
                                }
                                return Flux.fromIterable(exams)
                                        .concatMap(exam -> normalizeExamMaxScore(exam, caConfig)
                                                .map(normalized -> new ExamLookupResponse(
                                                        normalized.getId(),
                                                        normalized.getName(),
                                                        normalized.getMaxScore())))
                                        .collectList();
                            });
                });
    }

    @Override
    public Mono<List<AssessmentTraitResponse>> getAssessmentTraits(UUID classId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return classRepository.findByIdAndSchoolId(classId, schoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException("CLASS_NOT_FOUND", "Class not found", "classId")))
                            .flatMap(classEntity -> ensureAssessmentTraits(schoolId, educationLevelForClass(classEntity)));
                });
    }

    @Override
    public Mono<List<TraitAssessmentValueResponse>> getTraitAssessmentValues(UUID classId, UUID termId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return classRepository.findByIdAndSchoolId(classId, schoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException("CLASS_NOT_FOUND", "Class not found", "classId")))
                            .then(termRepository.findByIdAndSchoolId(termId, schoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException("TERM_NOT_FOUND", "Term not found", "termId"))))
                            .thenMany(studentTraitAssessmentRepository.findValuesForClass(classId, termId, schoolId))
                            .map(value -> new TraitAssessmentValueResponse(
                                    value.getStudentId(),
                                    value.getTraitId(),
                                    value.getRating(),
                                    value.getComment()))
                            .collectList();
                });
    }

    private Mono<Exam> normalizeExamMaxScore(Exam exam, CaConfigResponse caConfig) {
        int configuredMaxScore = configuredExamMaxScore(caConfig);
        BigDecimal configuredWeight = BigDecimal.valueOf(configuredMaxScore);
        boolean maxChanged = exam.getMaxScore() != configuredMaxScore;
        boolean weightChanged = exam.getWeightPercentage() == null
                || exam.getWeightPercentage().compareTo(configuredWeight) != 0;

        if (!maxChanged && !weightChanged) {
            return Mono.just(exam);
        }

        exam.setMaxScore(configuredMaxScore);
        exam.setWeightPercentage(configuredWeight);
        exam.setUpdatedAt(Instant.now());
        return examRepository.save(exam);
    }

    private Mono<Void> normalizeExamsForTerm(UUID termId, UUID schoolId) {
        return getCaConfig()
                .flatMapMany(caConfig -> examRepository.findByTermId(termId)
                        .filter(exam -> exam.getSchoolId().equals(schoolId))
                        .concatMap(exam -> normalizeExamMaxScore(exam, caConfig)))
                .then();
    }

    private int configuredExamMaxScore(CaConfigResponse caConfig) {
        double examWeight = caConfig != null && caConfig.examWeightPercentage() != null
                ? caConfig.examWeightPercentage()
                : 60.0;
        return BigDecimal.valueOf(examWeight)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    @Override
    public Mono<TraitAssessmentSaveResponse> saveTraitAssessments(TraitAssessmentRequest request) {
        return Mono.fromCallable(() -> validateTraitAssessmentRequest(request))
                .flatMap(validated -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                            UUID schoolId = requireSchoolId(user);
                            Mono<TraitAssessmentSaveResponse> transaction = resolveLocalUserId(user.getUserId())
                                    .flatMap(localUserId -> classRepository.findByIdAndSchoolId(validated.classId(), schoolId)
                                            .switchIfEmpty(Mono.error(new SchoolFeeException("CLASS_NOT_FOUND", "Class not found", "classId")))
                                            .flatMap(classEntity -> termRepository.findByIdAndSchoolId(validated.termId(), schoolId)
                                                    .switchIfEmpty(Mono.error(new SchoolFeeException("TERM_NOT_FOUND", "Term not found", "termId")))
                                                    .then(verifyTraitAssessmentContext(validated, schoolId))
                                                    .thenMany(Flux.fromIterable(validated.entries())
                                                            .concatMap(entry -> studentTraitAssessmentRepository.upsert(
                                                                    toStudentTraitAssessment(validated, entry, schoolId, localUserId))))
                                                    .collectList()
                                                    .map(saved -> new TraitAssessmentSaveResponse(
                                                            saved.size(),
                                                            "Trait assessments saved"))));
                            return transactionalOperator.transactional(transaction);
                        }));
    }

    private Mono<UUID> resolveLocalUserId(UUID keycloakUserId) {
        return userRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakUserId)
                .map(User::getId)
                .switchIfEmpty(Mono.error(new SchoolFeeException("USER_NOT_FOUND", "Local user record not found", "userId")));
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private Mono<Void> verifyNotPublished(UUID schoolId, UUID termId) {
        return publishedResultRepository.findBySchoolIdAndTermId(schoolId, termId)
                .flatMap(p -> Mono.<Void>error(new SchoolFeeException(
                        "RESULTS_ALREADY_PUBLISHED",
                        "Results are published. Unpublish before modifying scores.")));
    }

    private Mono<List<AssessmentTraitResponse>> ensureAssessmentTraits(UUID schoolId, String educationLevel) {
        String level = normalizeEducationLevelForStorage(educationLevel);
        return assessmentTraitRepository.findActiveForLevel(schoolId, level)
                .collectList()
                .flatMap(existing -> {
                    if (!existing.isEmpty()) {
                        return Mono.just(existing);
                    }
                    Instant now = Instant.now();
                    return Flux.fromIterable(DEFAULT_ASSESSMENT_TRAITS)
                            .concatMap(trait -> assessmentTraitRepository.insertDefaultIfMissing(
                                    schoolId,
                                    level,
                                    trait.name(),
                                    trait.category(),
                                    trait.sortOrder(),
                                    now))
                            .thenMany(assessmentTraitRepository.findActiveForLevel(schoolId, level))
                            .collectList();
                })
                .map(traits -> traits.stream()
                        .map(this::toAssessmentTraitResponse)
                        .toList());
    }

    private AssessmentTraitResponse toAssessmentTraitResponse(AssessmentTrait trait) {
        return new AssessmentTraitResponse(
                trait.getId(),
                trait.getName(),
                trait.getCategory(),
                trait.getSortOrder());
    }

    private TraitAssessmentRequest validateTraitAssessmentRequest(TraitAssessmentRequest request) {
        if (request == null) {
            throw new SchoolFeeException("INVALID_TRAIT_ASSESSMENT", "Trait assessment request is required");
        }
        if (request.classId() == null) {
            throw new SchoolFeeException("INVALID_TRAIT_ASSESSMENT", "Class is required", "classId");
        }
        if (request.termId() == null) {
            throw new SchoolFeeException("INVALID_TRAIT_ASSESSMENT", "Term is required", "termId");
        }
        if (request.entries() == null || request.entries().isEmpty()) {
            throw new SchoolFeeException("INVALID_TRAIT_ASSESSMENT", "At least one assessment rating is required", "entries");
        }
        Set<String> uniqueKeys = new HashSet<>();
        for (TraitAssessmentRequest.Entry entry : request.entries()) {
            if (entry.studentId() == null || entry.traitId() == null) {
                throw new SchoolFeeException("INVALID_TRAIT_ASSESSMENT", "Student and trait are required", "entries");
            }
            if (entry.rating() == null || entry.rating().isBlank()) {
                throw new SchoolFeeException("INVALID_TRAIT_ASSESSMENT", "Rating is required", "rating");
            }
            if (!uniqueKeys.add(entry.studentId() + ":" + entry.traitId())) {
                throw new SchoolFeeException("DUPLICATE_TRAIT_ASSESSMENT", "Each student/trait can appear only once", "entries");
            }
        }
        return request;
    }

    private Mono<Void> verifyTraitAssessmentContext(TraitAssessmentRequest request, UUID schoolId) {
        Set<UUID> studentIds = request.entries().stream()
                .map(TraitAssessmentRequest.Entry::studentId)
                .collect(Collectors.toSet());
        Set<UUID> traitIds = request.entries().stream()
                .map(TraitAssessmentRequest.Entry::traitId)
                .collect(Collectors.toSet());

        Mono<Void> studentsMono = Flux.fromIterable(studentIds)
                .concatMap(studentId -> studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "STUDENT_NOT_FOUND",
                                "Student not found",
                                "studentId")))
                        .flatMap(student -> {
                            if (!request.classId().equals(student.getCurrentClassId())) {
                                return Mono.error(new SchoolFeeException(
                                        "STUDENT_NOT_IN_CLASS",
                                        "Student does not belong to the selected class",
                                        "studentId"));
                            }
                            return Mono.empty();
                        }))
                .then();

        Mono<Void> traitsMono = Flux.fromIterable(traitIds)
                .concatMap(traitId -> assessmentTraitRepository.findActiveByIdAndSchoolId(traitId, schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "ASSESSMENT_TRAIT_NOT_FOUND",
                                "Assessment trait not found",
                                "traitId"))))
                .then();

        return studentsMono.then(traitsMono);
    }

    private StudentTraitAssessment toStudentTraitAssessment(
            TraitAssessmentRequest request,
            TraitAssessmentRequest.Entry entry,
            UUID schoolId,
            UUID recordedBy) {
        Instant now = Instant.now();
        return StudentTraitAssessment.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .studentId(entry.studentId())
                .classId(request.classId())
                .termId(request.termId())
                .traitId(entry.traitId())
                .rating(entry.rating().trim())
                .comment(entry.comment() == null || entry.comment().isBlank() ? null : entry.comment().trim())
                .recordedBy(recordedBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String normalizeEducationLevelForStorage(String educationLevel) {
        if (educationLevel == null || educationLevel.isBlank()) {
            return "PRIMARY";
        }
        return educationLevel.toUpperCase(Locale.ROOT);
    }

    private Mono<PublishResultResponse> publishResultsForTerm(UUID termId, UUID schoolId, UUID userId) {
        return publishedResultRepository.findBySchoolIdAndTermId(schoolId, termId)
                .flatMap(existing -> Mono.<PublishResultResponse>error(new SchoolFeeException(
                        "RESULTS_ALREADY_PUBLISHED",
                        "Results are already published for this term")))
                .switchIfEmpty(Mono.defer(() -> {
                    PublishedResult published = PublishedResult.builder()
                            .schoolId(schoolId)
                            .termId(termId)
                            .publishedBy(userId)
                            .publishedAt(Instant.now())
                            .build();
                    return publishedResultRepository.save(published)
                            .map(saved -> toPublishResponse(termId, userId, saved.getPublishedAt()));
                }));
    }

    private PublishResultResponse toPublishResponse(UUID termId, UUID userId, Instant publishedAt) {
        return new PublishResultResponse(
                termId,
                "PUBLISHED",
                publishedAt != null ? publishedAt : Instant.now(),
                userId.toString(),
                "Results published. Parents can now view report cards.");
    }

    private PublishResultResponse toUnpublishResponse(UUID termId, UUID userId) {
        return new PublishResultResponse(
                termId,
                "UNPUBLISHED",
                Instant.now(),
                userId.toString(),
                "Results unpublished. Teachers can now modify scores.");
    }

    private Mono<Void> verifyResultsPublished(UUID schoolId, UUID termId) {
        return publishedResultRepository.findBySchoolIdAndTermId(schoolId, termId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "RESULTS_NOT_PUBLISHED",
                        "Results have not been published yet.")))
                .then();
    }

    private Mono<Void> verifyParentAccess(UUID userId, UUID studentId) {
        return guardianRepository.findByKeycloakId(userId)
                .flatMap(guardian -> guardianLinkRepository
                        .findByGuardianIdAndStudentIdAndDeletedAtIsNull(guardian.getId(), studentId))
                .flatMap(link -> Boolean.TRUE.equals(link.getCanViewResults())
                        ? Mono.just(link)
                        : Mono.error(new SchoolFeeException(
                                "ACCESS_DENIED",
                                "You can only view results for children linked to your account.")))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "ACCESS_DENIED", "You can only view your own children's results.")))
                .then();
    }

    private Mono<Void> computeFinalScores(UUID classId, UUID termId, UUID subjectId) {
        return computationEngine.computeFinalScores(classId, termId, subjectId);
    }

    private Mono<Void> autoComputeRankings(UUID classId, UUID termId, UUID schoolId) {
        Mono<ClassEntity> classMono = classRepository.findByIdAndSchoolId(classId, schoolId)
                .defaultIfEmpty(ClassEntity.builder().id(classId).build());
        Mono<Optional<GradeConfig>> gradeConfigMono = gradeConfigRepository.findBySchoolIdAndIsActiveTrue(schoolId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        return Mono.zip(classMono, gradeConfigMono)
                .flatMap(tuple -> {
                    String educationLevel = educationLevelForClass(tuple.getT1());
                    int passMark = resolvePassMark(tuple.getT2().map(GradeConfig::getConfig).orElse(null), educationLevel);
                    ScoreComputationEngine.RankingParameters params =
                            new ScoreComputationEngine.RankingParameters(passMark, 100);
                    return computationEngine.computeSubjectPositions(classId, termId)
                            .then(computationEngine.computeClassRankings(classId, termId, schoolId, params));
                });
    }

    private Mono<Void> auditScoreChange(String scoreType, UUID scoreId, UUID studentId,
                                         UUID subjectId, UUID termId, BigDecimal oldScore,
                                         BigDecimal newScore, UUID schoolId, UUID changedBy,
                                         String reason, Instant changedAt) {
        ScoreAuditLog audit = ScoreAuditLog.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .scoreType(scoreType)
                .scoreId(scoreId)
                .studentId(studentId)
                .subjectId(subjectId)
                .termId(termId)
                .oldScore(oldScore)
                .newScore(newScore)
                .changedBy(changedBy)
                .changedAt(changedAt)
                .reason(reason)
                .build();
        return auditLogRepository.insert(audit).then();
    }

    private Mono<ReportCommentResponse> saveComment(UUID studentId, UUID termId, UUID schoolId,
                                                     GeneratedComment comment, boolean isTeacher, UUID authorId) {
        return commentRepository.findByStudentIdAndTermIdAndSchoolId(studentId, termId, schoolId)
                .flatMap(existing -> {
                    applyComment(existing, comment, isTeacher, authorId);
                    existing.setUpdatedAt(Instant.now());
                    return commentRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> createComment(studentId, termId, schoolId, comment, isTeacher, authorId)))
                .onErrorResume(DuplicateKeyException.class, ex -> commentRepository
                        .findByStudentIdAndTermIdAndSchoolId(studentId, termId, schoolId)
                        .switchIfEmpty(Mono.error(ex))
                        .flatMap(existing -> {
                            applyComment(existing, comment, isTeacher, authorId);
                            existing.setUpdatedAt(Instant.now());
                            return commentRepository.save(existing);
                        }))
                .map(saved -> new ReportCommentResponse(
                        studentId,
                        termId,
                        comment.text(),
                        saved.getUpdatedAt() != null ? saved.getUpdatedAt() : Instant.now()));
    }

    private Mono<GeneratedComment> resolveCommentText(
            UUID studentId,
            UUID termId,
            UUID schoolId,
            String requestedComment,
            boolean isTeacher,
            boolean autoGenerate) {
        String manual = trimToNull(requestedComment);
        if (!autoGenerate && manual != null && !"AUTO".equalsIgnoreCase(manual)) {
            return Mono.just(new GeneratedComment(manual, false));
        }
        return generateCommentFromGrades(studentId, termId, schoolId, isTeacher)
                .map(comment -> new GeneratedComment(comment, true));
    }

    private Mono<String> generateCommentFromGrades(UUID studentId, UUID termId, UUID schoolId, boolean isTeacher) {
        Mono<Student> studentMono = studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException("STUDENT_NOT_FOUND", "Student not found", "studentId")));
        Mono<List<FinalScore>> finalScoresMono = finalScoreRepository
                .findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(studentId, termId, schoolId)
                .collectList();
        Mono<Optional<ClassRanking>> rankingMono = rankingRepository
                .findByStudentIdAndTermIdAndSchoolId(studentId, termId, schoolId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        return Mono.zip(studentMono, finalScoresMono, rankingMono)
                .flatMap(tuple -> {
                    Student student = tuple.getT1();
                    List<FinalScore> finalScores = tuple.getT2();
                    Optional<ClassRanking> ranking = tuple.getT3();
                    if (finalScores.isEmpty() && ranking.isEmpty()) {
                        return Mono.error(new SchoolFeeException(
                                "RESULT_SCORES_NOT_FOUND",
                                "Computed result scores are required before an automatic comment can be generated",
                                "studentId"));
                    }
                    return resolveGradeBandsForStudent(student, schoolId)
                            .map(gradeBands -> buildAutomaticComment(student, finalScores, ranking, gradeBands, isTeacher));
                });
    }

    private String buildAutomaticComment(
            Student student,
            List<FinalScore> finalScores,
            Optional<ClassRanking> ranking,
            List<GradeBand> gradeBands,
            boolean isTeacher) {
        BigDecimal average = ranking
                .map(ClassRanking::getAveragePercentage)
                .filter(Objects::nonNull)
                .orElseGet(() -> averageScore(finalScores));
        double avg = average.doubleValue();
        String grade = ranking
                .map(ClassRanking::getOverallGrade)
                .filter(value -> value != null && !value.isBlank())
                .orElseGet(() -> resolveGrade(gradeBands, avg));
        int subjectsTaken = ranking.map(ClassRanking::getSubjectsTaken).filter(value -> value > 0).orElse(finalScores.size());
        int subjectsPassed = ranking.map(ClassRanking::getSubjectsPassed).orElseGet(() -> countPassed(finalScores));
        String studentName = firstNameOrFullName(student);
        String performance = performancePhrase(avg);
        String gradeText = grade == null ? "" : " with an overall grade of " + grade;
        String passText = subjectsTaken <= 0
                ? ""
                : String.format(Locale.ROOT, ", passing %d of %d subject%s", subjectsPassed, subjectsTaken, subjectsTaken == 1 ? "" : "s");
        String positionText = ranking
                .filter(value -> value.getClassPosition() > 0 && value.getOutOf() > 0)
                .map(value -> String.format(Locale.ROOT, " and placing %s out of %d", ordinal(value.getClassPosition()), value.getOutOf()))
                .orElse("");

        if (isTeacher) {
            return String.format(Locale.ROOT,
                    "%s %s this term, earning an average of %.1f%%%s%s%s. %s",
                    studentName,
                    performance,
                    avg,
                    gradeText,
                    passText,
                    positionText,
                    teacherAdvice(avg));
        }
        return String.format(Locale.ROOT,
                "%s's overall performance is %.1f%%%s%s%s. %s",
                studentName,
                avg,
                gradeText,
                passText,
                positionText,
                principalAdvice(avg));
    }

    private BigDecimal averageScore(List<FinalScore> finalScores) {
        List<BigDecimal> scores = finalScores.stream()
                .map(FinalScore::getFinalScore)
                .filter(Objects::nonNull)
                .toList();
        if (scores.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return scores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private int countPassed(List<FinalScore> finalScores) {
        return (int) finalScores.stream()
                .map(FinalScore::getFinalScore)
                .filter(Objects::nonNull)
                .filter(score -> score.compareTo(BigDecimal.valueOf(40)) >= 0)
                .count();
    }

    private String performancePhrase(double average) {
        if (average >= 75) return "delivered an excellent performance";
        if (average >= 65) return "performed very well";
        if (average >= 50) return "made satisfactory progress";
        if (average >= 40) return "is making progress but needs more consistency";
        return "needs urgent academic support";
    }

    private String teacherAdvice(double average) {
        if (average >= 75) return "Encourage this learner to maintain the discipline and stretch further next term.";
        if (average >= 65) return "With continued focus, this learner can reach an excellent level.";
        if (average >= 50) return "More consistent revision and class participation will improve the result.";
        if (average >= 40) return "Close monitoring, regular practice, and timely completion of assignments are recommended.";
        return "A focused improvement plan, extra practice, and strong home support are urgently recommended.";
    }

    private String principalAdvice(double average) {
        if (average >= 75) return "This is commendable; keep up the excellent standard.";
        if (average >= 65) return "A very good result; continue to aim higher.";
        if (average >= 50) return "A fair result; greater consistency will lead to stronger achievement.";
        if (average >= 40) return "Promotion should be supported with deliberate academic improvement.";
        return "Immediate intervention is required to help the learner recover academically.";
    }

    private String firstNameOrFullName(Student student) {
        String firstName = trimToNull(student.getFirstName());
        return firstName != null ? firstName : fullName(student);
    }

    private String ordinal(int value) {
        int mod100 = value % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return value + "th";
        }
        return switch (value % 10) {
            case 1 -> value + "st";
            case 2 -> value + "nd";
            case 3 -> value + "rd";
            default -> value + "th";
        };
    }

    private void applyComment(ReportComment target, GeneratedComment comment, boolean isTeacher, UUID authorId) {
        if (isTeacher) {
            target.setTeacherComment(comment.text());
            target.setTeacherCommentFinal(comment.text());
            target.setTeacherId(authorId);
            if (comment.generated()) {
                target.setTeacherCommentAuto(comment.text());
                target.setCommentsGeneratedAt(Instant.now());
            }
        } else {
            target.setPrincipalComment(comment.text());
            target.setPrincipalCommentFinal(comment.text());
            target.setPrincipalId(authorId);
            if (comment.generated()) {
                target.setPrincipalCommentAuto(comment.text());
                target.setCommentsGeneratedAt(Instant.now());
            }
        }
    }

    private Mono<Void> validateCommentContext(UUID studentId, UUID termId, UUID schoolId) {
        return studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "STUDENT_NOT_FOUND",
                        "Student not found",
                        "studentId")))
                .then(Mono.defer(() -> termRepository.findByIdAndSchoolId(termId, schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "TERM_NOT_FOUND",
                                "Term not found",
                                "termId")))))
                .then();
    }

    private Mono<ReportComment> createComment(UUID studentId, UUID termId, UUID schoolId,
                                               GeneratedComment comment, boolean isTeacher, UUID authorId) {
        Instant now = Instant.now();
        ReportComment rc = ReportComment.builder()
                .schoolId(schoolId)
                .studentId(studentId)
                .termId(termId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        applyComment(rc, comment, isTeacher, authorId);
        return commentRepository.save(rc);
    }

    private Mono<StudentResultResponse> buildStudentResult(Student student, Term term, UUID schoolId) {
        UUID classId = student.getCurrentClassId();
        Mono<ClassEntity> classMono = classId == null
                ? Mono.just(ClassEntity.builder().build())
                : classRepository.findByIdAndSchoolId(classId, schoolId)
                        .defaultIfEmpty(ClassEntity.builder().id(classId).build());
        Mono<Long> classSizeMono = classId == null
                ? Mono.just(0L)
                : studentRepository.countActiveByCurrentClassId(classId);
        Mono<AcademicSession> sessionMono = term.getSessionId() == null
                ? Mono.just(AcademicSession.builder().build())
                : academicSessionRepository.findByIdAndDeletedAtIsNull(term.getSessionId())
                        .defaultIfEmpty(AcademicSession.builder().id(term.getSessionId()).build());
        Mono<List<FinalScore>> finalScoresMono = finalScoreRepository
                .findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(student.getId(), term.getId(), schoolId)
                .collectList();
        Mono<Optional<ClassRanking>> rankingMono = rankingRepository
                .findByStudentIdAndTermIdAndSchoolId(student.getId(), term.getId(), schoolId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        Mono<Optional<ReportComment>> commentMono = commentRepository
                .findByStudentIdAndTermIdAndSchoolId(student.getId(), term.getId(), schoolId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        Mono<Optional<GradeConfig>> gradeConfigMono = gradeConfigRepository
                .findBySchoolIdAndIsActiveTrue(schoolId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        Mono<List<ClassRanking>> classRankingsMono = classId == null
                ? Mono.just(List.of())
                : rankingRepository.findByClassIdAndTermIdOrderByAveragePercentageDesc(classId, term.getId())
                        .collectList();
        Mono<List<StudentResultResponse.TraitAssessment>> traitAssessmentsMono = studentTraitAssessmentRepository
                .findForStudentResult(student.getId(), term.getId(), schoolId)
                .map(this::toTraitAssessment)
                .collectList();

        return Mono.zip(classMono, classSizeMono, sessionMono, finalScoresMono, rankingMono, commentMono, gradeConfigMono, classRankingsMono)
                .flatMap(tuple -> {
                    List<GradeBand> gradeBands = resolveGradeBands(
                            tuple.getT7().map(GradeConfig::getConfig).orElse(null),
                            educationLevelForClass(tuple.getT1()));
                    return Flux.fromIterable(tuple.getT4())
                        .concatMap(finalScore -> buildSubjectResult(finalScore, schoolId, gradeBands))
                        .collectList()
                        .zipWith(traitAssessmentsMono)
                        .map(reportParts -> {
                            List<StudentResultResponse.SubjectResult> subjects = reportParts.getT1();
                            List<StudentResultResponse.TraitAssessment> assessments = reportParts.getT2();
                            Optional<ClassRanking> ranking = tuple.getT5();
                            Optional<ReportComment> comment = tuple.getT6();
                            return new StudentResultResponse(
                                    new StudentResultResponse.StudentInfo(
                                            student.getId(),
                                            student.getAdmissionNumber(),
                                            fullName(student),
                                            tuple.getT1().getName(),
                                            tuple.getT2().intValue(),
                                            student.getProfilePhotoUrl()),
                                    new StudentResultResponse.TermInfo(
                                            term.getId(),
                                            term.getName(),
                                            tuple.getT3().getName()),
                                    subjects,
                                    buildResultSummary(tuple.getT4(), ranking, tuple.getT8(), comment),
                                    buildRankingInfo(ranking),
                                    buildAttendanceInfo(comment),
                                    toGradeScale(gradeBands),
                                    filterTraitAssessments(assessments, "AFFECTIVE", "BEHAVIOURAL"),
                                    filterTraitAssessments(assessments, "PSYCHOMOTOR"),
                                    comment.map(this::effectiveTeacherComment).orElse(null),
                                    comment.map(this::effectivePrincipalComment).orElse(null));
                        });
                });
    }

    private Mono<StudentResultResponse.SubjectResult> buildSubjectResult(
            FinalScore finalScore,
            UUID schoolId,
            List<GradeBand> gradeBands) {
        Mono<Subject> subjectMono = subjectRepository.findById(finalScore.getSubjectId())
                .defaultIfEmpty(Subject.builder().id(finalScore.getSubjectId()).name("Unknown Subject").build());
        Mono<List<StudentResultResponse.CaBreakdown>> caScoresMono = caScoreRepository
                .findByStudentIdAndSubjectIdAndTermIdAndSchoolId(
                        finalScore.getStudentId(),
                        finalScore.getSubjectId(),
                        finalScore.getTermId(),
                        schoolId)
                .concatMap(score -> caComponentRepository.findById(score.getCaComponentId())
                        .map(component -> new StudentResultResponse.CaBreakdown(
                                component.getName(),
                                score.getScore(),
                                score.getMaxScore()))
                        .defaultIfEmpty(new StudentResultResponse.CaBreakdown(
                                "Continuous Assessment",
                                score.getScore(),
                                score.getMaxScore())))
                .collectList();
        Mono<Optional<ResultScore>> examScoreMono = scoreRepository
                .findLatestByStudentIdAndSubjectIdAndTermIdAndSchoolId(
                        finalScore.getStudentId(),
                        finalScore.getSubjectId(),
                        finalScore.getTermId(),
                        schoolId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return Mono.zip(subjectMono, caScoresMono, examScoreMono)
                .map(tuple -> new StudentResultResponse.SubjectResult(
                        finalScore.getSubjectId(),
                        tuple.getT1().getName(),
                        tuple.getT2(),
                        nullToZero(finalScore.getCaTotal()),
                        finalScore.getCaMaxTotal(),
                        nullToZero(finalScore.getExamScore()),
                        finalScore.getExamMaxScore(),
                        nullToZero(finalScore.getFinalScore()),
                        100,
                        nullToZero(finalScore.getFinalScore()),
                        resolveStoredOrComputedGrade(finalScore, gradeBands),
                        finalScore.getRemark(),
                        finalScore.getPoints(),
                        finalScore.getSubjectPosition() == null ? 0 : finalScore.getSubjectPosition(),
                        null,
                        null,
                        null,
                        tuple.getT3().map(ResultScore::getId).orElse(null),
                        tuple.getT3().map(ResultScore::getExamId).orElse(null)));
    }

    private Mono<ClassResultSheetResponse> buildClassResultSheet(UUID classId, UUID termId, UUID schoolId) {
        Mono<ClassEntity> classMono = classRepository.findByIdAndSchoolId(classId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException("CLASS_NOT_FOUND", "Class not found", "classId")));
        Mono<Term> termMono = termRepository.findByIdAndSchoolId(termId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException("TERM_NOT_FOUND", "Term not found", "termId")));
        Mono<Long> classSizeMono = studentRepository.countActiveByCurrentClassId(classId);

        // Fetch subjects
        Mono<List<Subject>> subjectsListMono = classSubjectRepository.findByClassIdAndIsActiveTrue(classId)
                .flatMap(cs -> subjectRepository.findById(cs.getSubjectId()))
                .collectList()
                .map(list -> list.stream()
                        .sorted(Comparator.comparing(Subject::getName))
                        .toList())
                .cache();

        // Fetch students
        Mono<List<Student>> studentsListMono = studentRepository.findActiveBySchoolIdAndCurrentClassId(schoolId, classId)
                .collectList();

        // Fetch rankings
        Mono<List<ClassRanking>> rankingsListMono = subjectsListMono
                .flatMapMany(subjects -> normalizeExamsForTerm(termId, schoolId)
                        .thenMany(Flux.fromIterable(subjects)
                                .concatMap(subject -> computeFinalScores(classId, termId, subject.getId()))))
                .then(autoComputeRankings(classId, termId, schoolId))
                .thenMany(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(classId, termId))
                .collectList();
        Mono<Optional<GradeConfig>> gradeConfigMono = gradeConfigRepository
                .findBySchoolIdAndIsActiveTrue(schoolId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return Mono.zip(
                        classMono,
                        termMono,
                        classSizeMono,
                        subjectsListMono,
                        studentsListMono,
                        rankingsListMono,
                        gradeConfigMono)
                .flatMap(tuple -> {
                    ClassEntity classEntity = tuple.getT1();
                    Term term = tuple.getT2();
                    int classSize = tuple.getT3().intValue();
                    List<Subject> subjects = tuple.getT4();
                    List<Student> students = tuple.getT5();
                    List<ClassRanking> rankings = tuple.getT6();
                    List<GradeBand> gradeBands = resolveGradeBands(
                            tuple.getT7().map(GradeConfig::getConfig).orElse(null),
                            educationLevelForClass(classEntity));

                    List<String> subjectNames = subjects.stream().map(Subject::getName).toList();

                    // For each student, map their row
                    List<Mono<ClassResultSheetResponse.StudentRow>> studentRowMonos = students.stream()
                            .map(student -> {
                                ClassRanking ranking = rankings.stream()
                                        .filter(r -> r.getStudentId().equals(student.getId()))
                                        .findFirst()
                                        .orElse(null);

                                 int position = ranking == null ? 0 : ranking.getClassPosition();
                                 double average = ranking == null ? 0.0 : (ranking.getAveragePercentage() == null ? 0.0 : ranking.getAveragePercentage().doubleValue());
                                 String overallGrade = ranking == null ? null : ranking.getOverallGrade();
                                 if (overallGrade == null || overallGrade.isBlank()) {
                                     overallGrade = resolveGrade(gradeBands, average);
                                 }
                                 String resolvedOverallGrade = overallGrade;

                                // Fetch final scores for this student and term
                                return finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(student.getId(), termId, schoolId)
                                        .collectList()
                                        .map(finalScores -> {
                                            List<ClassResultSheetResponse.SubjectScore> subjectScores = subjects.stream()
                                                    .map(subject -> {
                                                        FinalScore finalScore = finalScores.stream()
                                                                .filter(fs -> fs.getSubjectId().equals(subject.getId()))
                                                                .findFirst()
                                                                .orElse(null);

                                                        if (finalScore == null) {
                                                            return new ClassResultSheetResponse.SubjectScore(
                                                                    subject.getName(),
                                                                    0.0,
                                                                    null,
                                                                    0
                                                            );
                                                        }

                                                        return new ClassResultSheetResponse.SubjectScore(
                                                                subject.getName(),
                                                                finalScore.getFinalScore() == null ? 0.0 : finalScore.getFinalScore().doubleValue(),
                                                                resolveStoredOrComputedGrade(finalScore, gradeBands),
                                                                finalScore.getSubjectPosition() == null ? 0 : finalScore.getSubjectPosition()
                                                        );
                                                    })
                                                    .toList();

                                            return new ClassResultSheetResponse.StudentRow(
                                                    student.getId().toString(),
                                                    student.getAdmissionNumber(),
                                                    fullName(student),
                                                    position,
                                                    average,
                                                    resolvedOverallGrade,
                                                    subjectScores
                                            );
                                        });
                            })
                            .toList();

                    if (studentRowMonos.isEmpty()) {
                        return Mono.just(new ClassResultSheetResponse(
                                classEntity.getName(),
                                term.getName(),
                                classSize,
                                subjectNames,
                                List.of()
                        ));
                    }

                    return Flux.merge(studentRowMonos)
                            .collectList()
                            .map(studentRows -> {
                                // Sort by position (ascending, but non-zero first)
                                List<ClassResultSheetResponse.StudentRow> sortedRows = studentRows.stream()
                                        .sorted((a, b) -> {
                                            if (a.position() == 0 && b.position() == 0) {
                                                return a.name().compareTo(b.name());
                                            }
                                            if (a.position() == 0) return 1;
                                            if (b.position() == 0) return -1;
                                            return Integer.compare(a.position(), b.position());
                                        })
                                        .toList();

                                return new ClassResultSheetResponse(
                                        classEntity.getName(),
                                        term.getName(),
                                        classSize,
                                        subjectNames,
                                        sortedRows
                                );
                            });
                });
    }

    private String resolveStoredOrComputedGrade(FinalScore finalScore, List<GradeBand> gradeBands) {
        if (finalScore.getGrade() != null && !finalScore.getGrade().isBlank()) {
            return finalScore.getGrade();
        }
        return finalScore.getFinalScore() == null
                ? null
                : resolveGrade(gradeBands, finalScore.getFinalScore().doubleValue());
    }

    private String resolveGrade(List<GradeBand> gradeBands, double score) {
        return gradeBands.stream()
                .filter(band -> score >= band.minScore() && score <= band.maxScore())
                .map(GradeBand::grade)
                .findFirst()
                .orElse(null);
    }

    private List<GradeBand> resolveGradeBands(com.fasterxml.jackson.databind.JsonNode config) {
        return resolveGradeBands(config, null);
    }

    private List<GradeBand> resolveGradeBands(com.fasterxml.jackson.databind.JsonNode config, String educationLevel) {
        com.fasterxml.jackson.databind.JsonNode selectedConfig = selectGradingConfigForLevel(config, educationLevel);
        if (selectedConfig != null && selectedConfig.path("grades").isArray()) {
            return gradeBandsFrom(selectedConfig.path("grades"));
        }
        if (config != null && config.isArray()) {
            return gradeBandsFrom(config);
        }
        if (config != null && config.path("grades").isArray()) {
            return gradeBandsFrom(config.path("grades"));
        }
        return DEFAULT_GRADE_BANDS;
    }

    private List<GradeBand> gradeBandsFrom(com.fasterxml.jackson.databind.JsonNode gradesNode) {
        List<GradeBand> configured = new ArrayList<>();
        gradesNode.forEach(node -> {
            String grade = node.path("grade").asText();
            if (!grade.isBlank() && node.has("minScore") && node.has("maxScore")) {
                configured.add(new GradeBand(
                        grade,
                        node.path("minScore").asDouble(),
                        node.path("maxScore").asDouble(),
                        node.path("remark").asText("")));
            }
        });
        return configured.isEmpty() ? DEFAULT_GRADE_BANDS : configured;
    }

    private int resolvePassMark(com.fasterxml.jackson.databind.JsonNode config, String educationLevel) {
        com.fasterxml.jackson.databind.JsonNode selectedConfig = selectGradingConfigForLevel(config, educationLevel);
        if (selectedConfig != null && selectedConfig.has("passMark")) {
            return selectedConfig.path("passMark").asInt(40);
        }
        if (config != null && config.has("passMark")) {
            return config.path("passMark").asInt(40);
        }
        return 40;
    }

    private com.fasterxml.jackson.databind.JsonNode selectGradingConfigForLevel(
            com.fasterxml.jackson.databind.JsonNode config,
            String educationLevel) {
        if (config == null || educationLevel == null || educationLevel.isBlank()) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode byLevel = config.path("byEducationLevel");
        if (byLevel.isObject()) {
            com.fasterxml.jackson.databind.JsonNode exact = byLevel.path(educationLevel);
            if (!exact.isMissingNode() && !exact.isNull()) {
                return exact;
            }
        }
        return null;
    }

    private Mono<List<GradeBand>> resolveGradeBandsForStudent(Student student, UUID schoolId) {
        Mono<Optional<ClassEntity>> classMono = student.getCurrentClassId() == null
                ? Mono.just(Optional.empty())
                : classRepository.findByIdAndSchoolId(student.getCurrentClassId(), schoolId)
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty());
        return Mono.zip(
                        gradeConfigRepository.findBySchoolIdAndIsActiveTrue(schoolId)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        classMono)
                .map(tuple -> resolveGradeBands(
                        tuple.getT1().map(GradeConfig::getConfig).orElse(null),
                        tuple.getT2().map(this::educationLevelForClass).orElse(null)));
    }

    private String educationLevelForClass(ClassEntity classEntity) {
        return classEntity == null ? null : educationLevelForGradeLevel(classEntity.getGradeLevel());
    }

    private String educationLevelForGradeLevel(String gradeLevel) {
        String normalized = trimToNull(gradeLevel);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (GradeLevel.isValid(upper)) {
            return GradeLevel.valueOf(upper).getCategory();
        }
        if (Set.of("NURSERY", "PRIMARY", "JUNIOR_SECONDARY", "SENIOR_SECONDARY").contains(upper)) {
            return upper;
        }
        if (upper.startsWith("PRIMARY")) return "PRIMARY";
        if (upper.startsWith("NURSERY")) return "NURSERY";
        if (upper.startsWith("JSS") || upper.startsWith("JUNIOR")) return "JUNIOR_SECONDARY";
        if (upper.startsWith("SSS") || upper.startsWith("SENIOR")) return "SENIOR_SECONDARY";
        return null;
    }

    private Mono<MyChildResultResponse> buildMyChildResult(UUID studentId, UUID schoolId) {
        return studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, schoolId)
                .flatMap(student -> {
                    Mono<String> classNameMono = student.getCurrentClassId() == null
                            ? Mono.just("")
                            : classRepository.findByIdAndSchoolId(student.getCurrentClassId(), schoolId)
                                    .map(ClassEntity::getName)
                                    .defaultIfEmpty("");

                    Mono<Term> termMono = termRepository.findCurrentTermsBySchoolId(schoolId).next();

                    return Mono.zip(classNameMono, termMono.map(Optional::of).defaultIfEmpty(Optional.empty()))
                            .flatMap(tuple -> {
                                String className = tuple.getT1();
                                Optional<Term> optTerm = tuple.getT2();

                                if (optTerm.isEmpty()) {
                                    return Mono.just(new MyChildResultResponse(
                                            studentId,
                                            null,
                                            student.getFirstName(),
                                            student.getLastName(),
                                            className,
                                            null,
                                            null,
                                            List.of()
                                    ));
                                }

                                Term term = optTerm.get();
                                UUID termId = term.getId();
                                String termName = term.getName();

                                return publishedResultRepository
                                        .findBySchoolIdAndTermId(schoolId, termId)
                                        .map(ignored -> true)
                                        .defaultIfEmpty(false)
                                        .flatMap(isPublished -> {
                                            if (!isPublished) {
                                                return Mono.just(new MyChildResultResponse(
                                                        studentId,
                                                        termId,
                                                        student.getFirstName(),
                                                        student.getLastName(),
                                                        className,
                                                        termName,
                                                        null,
                                                        List.of(),
                                                        null));
                                            }
                                            Mono<List<FinalScore>> finalScoresMono = finalScoreRepository
                                                    .findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(studentId, termId, schoolId)
                                                    .collectList();

                                            Mono<Optional<ClassRanking>> rankingMono = rankingRepository
                                                    .findByStudentIdAndTermIdAndSchoolId(studentId, termId, schoolId)
                                                    .map(Optional::of)
                                                    .defaultIfEmpty(Optional.empty());

                                            Mono<Optional<ReportComment>> commentMono = commentRepository
                                                    .findByStudentIdAndTermIdAndSchoolId(studentId, termId, schoolId)
                                                    .map(Optional::of)
                                                    .defaultIfEmpty(Optional.empty());

                                            return Mono.zip(finalScoresMono, rankingMono, commentMono)
                                        .flatMap(scoresRankingComment -> {
                                            List<FinalScore> finalScores = scoresRankingComment.getT1();
                                            Optional<ClassRanking> optRanking = scoresRankingComment.getT2();
                                            Optional<ReportComment> optComment = scoresRankingComment.getT3();

                                            final MyChildResultResponse.ResultSummary summary;
                                            if (!finalScores.isEmpty()) {
                                                BigDecimal totalScore = finalScores.stream()
                                                        .map(FinalScore::getFinalScore)
                                                        .filter(Objects::nonNull)
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                                int subjectsTaken = finalScores.size();
                                                double average = totalScore.divide(BigDecimal.valueOf(subjectsTaken), 2, RoundingMode.HALF_UP).doubleValue();
                                                String overallGrade = optRanking.map(ClassRanking::getOverallGrade).orElse(null);

                                                int classPosition = optRanking
                                                        .map(ClassRanking::getClassPosition)
                                                        .orElse(0);
                                                int outOf = optRanking
                                                        .map(ClassRanking::getOutOf)
                                                        .orElse(0);
                                                summary = new MyChildResultResponse.ResultSummary(
                                                        average,
                                                        subjectsTaken,
                                                        overallGrade,
                                                        classPosition,
                                                        outOf);
                                            } else {
                                                summary = null;
                                            }

                                            MyChildResultResponse.AttendanceSummary attendanceSummary = optComment.map(c -> {
                                                int open = c.getAttendanceDaysOpen() == null ? 0 : c.getAttendanceDaysOpen();
                                                int present = c.getAttendanceDaysPresent() == null ? 0 : c.getAttendanceDaysPresent();
                                                int absent = c.getAttendanceDaysAbsent() == null ? Math.max(open - present, 0) : c.getAttendanceDaysAbsent();
                                                double rate = open == 0 ? 0 : ((double) present / open) * 100;
                                                return new MyChildResultResponse.AttendanceSummary(open, present, absent, rate);
                                            }).orElse(null);

                                            List<Mono<MyChildResultResponse.TopSubject>> topSubjectMonos = finalScores.stream()
                                                    .filter(fs -> fs.getFinalScore() != null)
                                                    .map(fs -> subjectRepository.findById(fs.getSubjectId())
                                                            .map(subject -> new MyChildResultResponse.TopSubject(
                                                                    subject.getName(),
                                                                    fs.getFinalScore().doubleValue(),
                                                                    fs.getGrade()
                                                            ))
                                                            .defaultIfEmpty(new MyChildResultResponse.TopSubject(
                                                                    "Unknown Subject",
                                                                    fs.getFinalScore().doubleValue(),
                                                                    fs.getGrade()
                                                            )))
                                                    .toList();

                                            if (topSubjectMonos.isEmpty()) {
                                                return Mono.just(new MyChildResultResponse(
                                                        studentId,
                                                        termId,
                                                        student.getFirstName(),
                                                        student.getLastName(),
                                                        className,
                                                        termName,
                                                        summary,
                                                        List.of(),
                                                        attendanceSummary
                                                ));
                                            }

                                            return Flux.merge(topSubjectMonos)
                                                    .collectList()
                                                    .map(topSubjects -> {
                                                        List<MyChildResultResponse.TopSubject> sortedTopSubjects = topSubjects.stream()
                                                                .sorted(Comparator.comparingDouble(MyChildResultResponse.TopSubject::score).reversed())
                                                                .limit(3)
                                                                .toList();

                                                        return new MyChildResultResponse(
                                                                studentId,
                                                                termId,
                                                                student.getFirstName(),
                                                                student.getLastName(),
                                                                className,
                                                                termName,
                                                                summary,
                                                                sortedTopSubjects,
                                                                attendanceSummary
                                                        );
                                                    });
                                        });
                                        });
                            });
                })
                .defaultIfEmpty(new MyChildResultResponse(studentId, null, null, null, null, null, null, List.of(), null));
    }

    private Mono<PublishedTermResultResponse> buildPublishedTermSummary(
            UUID studentId,
            Term term,
            UUID schoolId) {
        Mono<List<FinalScore>> scoresMono = finalScoreRepository
                .findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(
                        studentId,
                        term.getId(),
                        schoolId)
                .collectList();
        Mono<Optional<ClassRanking>> rankingMono = rankingRepository
                .findByStudentIdAndTermIdAndSchoolId(studentId, term.getId(), schoolId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        Mono<String> sessionNameMono = term.getSessionId() == null
                ? Mono.just("")
                : academicSessionRepository.findByIdAndDeletedAtIsNull(term.getSessionId())
                        .map(AcademicSession::getName)
                        .defaultIfEmpty("");

        return Mono.zip(scoresMono, rankingMono, sessionNameMono)
                .map(tuple -> {
                    List<FinalScore> scores = tuple.getT1();
                    Optional<ClassRanking> ranking = tuple.getT2();
                    double average = scores.isEmpty()
                            ? 0
                            : scores.stream()
                                    .map(FinalScore::getFinalScore)
                                    .filter(Objects::nonNull)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP)
                                    .doubleValue();
                    String grade = ranking.map(ClassRanking::getOverallGrade)
                            .filter(value -> !value.isBlank())
                            .orElseGet(() -> resolveGrade(DEFAULT_GRADE_BANDS, average));
                    return new PublishedTermResultResponse(
                            term.getId(),
                            term.getName(),
                            tuple.getT3(),
                            average,
                            grade,
                            ranking.map(ClassRanking::getClassPosition).orElse(0),
                            ranking.map(ClassRanking::getOutOf).orElse(0));
                });
    }

    private CaScoreRequest validateCaScoreRequest(CaScoreRequest request) {
        if (request == null) {
            throw new SchoolFeeException("INVALID_SCORE_BATCH", "CA score request is required");
        }
        validateCommonScoreBatch(
                request.classId(),
                request.subjectId(),
                request.termId(),
                request.maxScore(),
                request.scores() == null ? null : request.scores().stream()
                        .map(score -> new ScoreValue(score.studentId(), score.score()))
                        .toList());
        if (request.caComponentId() == null) {
            throw new SchoolFeeException("INVALID_SCORE_BATCH", "CA component is required", "caComponentId");
        }
        return request;
    }

    private ExamScoreRequest validateExamScoreRequest(ExamScoreRequest request) {
        if (request == null) {
            throw new SchoolFeeException("INVALID_SCORE_BATCH", "Exam score request is required");
        }
        validateCommonScoreBatch(
                request.classId(),
                request.subjectId(),
                request.termId(),
                request.maxScore(),
                request.scores() == null ? null : request.scores().stream()
                        .map(score -> new ScoreValue(score.studentId(), score.score()))
                        .toList());
        if (request.examId() == null) {
            throw new SchoolFeeException("INVALID_SCORE_BATCH", "Exam is required", "examId");
        }
        return request;
    }

    private UpdateScoreRequest validateUpdateScoreRequest(UUID scoreId, UpdateScoreRequest request) {
        if (scoreId == null) {
            throw new SchoolFeeException("INVALID_SCORE", "Score id is required", "scoreId");
        }
        if (request == null || request.score() == null) {
            throw new SchoolFeeException("INVALID_SCORE", "Score is required", "score");
        }
        validateScoreValue(request.score(), Integer.MAX_VALUE);
        return request;
    }

    private void validateCommonScoreBatch(
            UUID classId, UUID subjectId, UUID termId, int maxScore, List<ScoreValue> scores) {
        if (classId == null) {
            throw new SchoolFeeException("INVALID_SCORE_BATCH", "Class is required", "classId");
        }
        if (subjectId == null) {
            throw new SchoolFeeException("INVALID_SCORE_BATCH", "Subject is required", "subjectId");
        }
        if (termId == null) {
            throw new SchoolFeeException("INVALID_SCORE_BATCH", "Term is required", "termId");
        }
        if (maxScore <= 0) {
            throw new SchoolFeeException("INVALID_SCORE_BATCH", "Maximum score must be positive", "maxScore");
        }
        if (scores == null || scores.isEmpty()) {
            throw new SchoolFeeException("INVALID_SCORE_BATCH", "At least one score is required", "scores");
        }

        Set<UUID> studentIds = new HashSet<>();
        for (ScoreValue score : scores) {
            if (score.studentId() == null) {
                throw new SchoolFeeException("INVALID_SCORE_BATCH", "Student is required", "scores.studentId");
            }
            if (!studentIds.add(score.studentId())) {
                throw new SchoolFeeException(
                        "DUPLICATE_SCORE_ENTRY",
                        "Each student can appear only once in a score batch",
                        "scores.studentId");
            }
            validateScoreValue(score.score(), maxScore);
        }
    }

    private void validateScoreValue(BigDecimal score, int maxScore) {
        if (score == null) {
            throw new SchoolFeeException("INVALID_SCORE", "Score is required", "score");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(maxScore)) > 0) {
            throw new SchoolFeeException(
                    "INVALID_SCORE",
                    "Score must be between 0 and " + maxScore,
                    "score");
        }
    }

    private Mono<Void> verifyCaScoreContext(CaScoreRequest request, UUID schoolId) {
        return loadScoreContext(request.classId(), request.subjectId(), request.termId(), schoolId)
                .then(caComponentRepository.findByIdAndSchoolIdAndIsActiveTrue(request.caComponentId(), schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "CA_COMPONENT_NOT_FOUND",
                                "CA component not found",
                                "caComponentId")))
                        .flatMap(component -> {
                            if (component.getMaxScore() != request.maxScore()) {
                                return Mono.error(new SchoolFeeException(
                                        "INVALID_SCORE_BATCH",
                                        "CA max score must match the configured component max score",
                                        "maxScore"));
                            }
                            return Mono.empty();
                        }))
                .then(verifyStudentsBelongToClass(
                        request.scores().stream()
                                .map(CaScoreRequest.ScoreEntry::studentId)
                                .toList(),
                        request.classId(),
                        schoolId));
    }

    private Mono<Void> verifyExamScoreContext(ExamScoreRequest request, UUID schoolId) {
        return loadScoreContext(request.classId(), request.subjectId(), request.termId(), schoolId)
                .then(getCaConfig()
                        .flatMap(caConfig -> examRepository.findByIdAndSchoolIdAndTermId(request.examId(), schoolId, request.termId())
                                .switchIfEmpty(Mono.error(new SchoolFeeException(
                                        "EXAM_NOT_FOUND",
                                        "Exam not found",
                                        "examId")))
                                .flatMap(exam -> normalizeExamMaxScore(exam, caConfig))
                                .flatMap(exam -> {
                                    if (exam.getMaxScore() != request.maxScore()) {
                                        return Mono.error(new SchoolFeeException(
                                                "INVALID_SCORE_BATCH",
                                                "Exam max score must match the configured exam max score",
                                                "maxScore"));
                                    }
                                    return Mono.empty();
                                })))
                .then(verifyStudentsBelongToClass(
                        request.scores().stream()
                                .map(ExamScoreRequest.ScoreEntry::studentId)
                                .toList(),
                        request.classId(),
                        schoolId));
    }

    private Mono<Void> loadScoreContext(UUID classId, UUID subjectId, UUID termId, UUID schoolId) {
        Mono<ClassEntity> classMono = classRepository.findByIdAndSchoolId(classId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "CLASS_NOT_FOUND",
                        "Class not found",
                        "classId")));
        Mono<Term> termMono = termRepository.findByIdAndSchoolId(termId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "TERM_NOT_FOUND",
                        "Term not found",
                        "termId")));
        Mono<Subject> subjectMono = subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(subjectId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SUBJECT_NOT_FOUND",
                        "Subject not found",
                        "subjectId")));
        Mono<ClassSubject> classSubjectMono = classSubjectRepository
                .findByClassIdAndSubjectIdAndSchoolIdAndIsActiveTrue(classId, subjectId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SUBJECT_NOT_ASSIGNED",
                        "Subject is not assigned to this class",
                        "subjectId")));

        return Mono.zip(classMono, termMono, subjectMono, classSubjectMono)
                .flatMap(tuple -> {
                    ClassEntity cls = tuple.getT1();
                    Term term = tuple.getT2();
                    if (!Boolean.TRUE.equals(cls.getIsActive())) {
                        return Mono.error(new SchoolFeeException(
                                "CLASS_INACTIVE",
                                "Inactive classes cannot receive scores",
                                "classId"));
                    }
                    if (!Objects.equals(cls.getAcademicSessionId(), term.getSessionId())) {
                        return Mono.error(new SchoolFeeException(
                                "TERM_NOT_IN_CLASS_SESSION",
                                "Term does not belong to the class academic session",
                                "termId"));
                    }
                    if ("COMPLETED".equalsIgnoreCase(Objects.toString(term.getStatus(), ""))
                            || term.getCompletedAt() != null) {
                        return Mono.error(new SchoolFeeException(
                                "TERM_ALREADY_COMPLETED",
                                "Completed terms cannot receive score changes",
                                "termId"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> verifyStudentsBelongToClass(List<UUID> studentIds, UUID classId, UUID schoolId) {
        return Flux.fromIterable(studentIds)
                .concatMap(studentId -> studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "STUDENT_NOT_FOUND",
                                "Student not found: " + studentId,
                                "scores.studentId")))
                        .flatMap(student -> {
                            if (!Objects.equals(student.getCurrentClassId(), classId)) {
                                return Mono.error(new SchoolFeeException(
                                        "STUDENT_NOT_IN_CLASS",
                                        "Student does not belong to this class: " + studentId,
                                        "scores.studentId"));
                            }
                            if (!"ACTIVE".equalsIgnoreCase(Objects.toString(student.getEnrollmentStatus(), ""))) {
                                return Mono.error(new SchoolFeeException(
                                        "STUDENT_INACTIVE",
                                        "Inactive students cannot receive scores: " + studentId,
                                        "scores.studentId"));
                            }
                            return Mono.just(student);
                        }))
                .then();
    }

    private CaScore toCaScoreEntity(
            CaScoreRequest request,
            CaScoreRequest.ScoreEntry scoreReq,
            UUID schoolId,
            UUID recordedBy) {
        Instant now = Instant.now();
        return CaScore.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .studentId(scoreReq.studentId())
                .subjectId(request.subjectId())
                .classId(request.classId())
                .termId(request.termId())
                .caComponentId(request.caComponentId())
                .score(scoreReq.score())
                .maxScore(request.maxScore())
                .recordedBy(recordedBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private ResultScore toExamScoreEntity(
            ExamScoreRequest request,
            ExamScoreRequest.ScoreEntry scoreReq,
            UUID schoolId,
            UUID recordedBy) {
        Instant now = Instant.now();
        return ResultScore.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .examId(request.examId())
                .studentId(scoreReq.studentId())
                .subjectId(request.subjectId())
                .classId(request.classId())
                .termId(request.termId())
                .score(scoreReq.score())
                .maxScore(request.maxScore())
                .recordedBy(recordedBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private StudentResultResponse.ResultSummary buildResultSummary(
            List<FinalScore> finalScores,
            Optional<ClassRanking> ranking,
            List<ClassRanking> classRankings,
            Optional<ReportComment> comment) {
        if (finalScores.isEmpty()) {
            return new StudentResultResponse.ResultSummary(
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, null,
                    BigDecimal.ZERO, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    comment.map(ReportComment::getPromotionStatus).orElse(null));
        }
        BigDecimal totalScore = finalScores.stream()
                .map(FinalScore::getFinalScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPoints = finalScores.stream()
                .map(FinalScore::getPoints)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int subjectsTaken = finalScores.size();
        int subjectsPassed = ranking.map(ClassRanking::getSubjectsPassed)
                .orElse((int) finalScores.stream()
                        .map(FinalScore::getFinalScore)
                        .filter(Objects::nonNull)
                        .filter(score -> score.compareTo(BigDecimal.valueOf(40)) >= 0)
                        .count());
        BigDecimal average = totalScore.divide(BigDecimal.valueOf(subjectsTaken), 2, RoundingMode.HALF_UP);
        return new StudentResultResponse.ResultSummary(
                totalScore,
                subjectsTaken * 100,
                average,
                ranking.map(ClassRanking::getOverallGrade).orElse(null),
                totalPoints,
                subjectsTaken,
                subjectsPassed,
                subjectsTaken - subjectsPassed,
                averageOfRankings(classRankings),
                highestAverage(classRankings),
                lowestAverage(classRankings),
                comment.map(ReportComment::getPromotionStatus).orElse(null));
    }

    private BigDecimal averageOfRankings(List<ClassRanking> rankings) {
        List<BigDecimal> averages = rankings.stream()
                .map(ClassRanking::getAveragePercentage)
                .filter(Objects::nonNull)
                .toList();
        if (averages.isEmpty()) return BigDecimal.ZERO;
        return averages.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(averages.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal highestAverage(List<ClassRanking> rankings) {
        return rankings.stream()
                .map(ClassRanking::getAveragePercentage)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal lowestAverage(List<ClassRanking> rankings) {
        return rankings.stream()
                .map(ClassRanking::getAveragePercentage)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private List<StudentResultResponse.GradeScaleItem> toGradeScale(List<GradeBand> gradeBands) {
        return gradeBands.stream()
                .sorted(Comparator.comparingDouble(GradeBand::maxScore).reversed())
                .map(band -> new StudentResultResponse.GradeScaleItem(
                        band.grade(),
                        BigDecimal.valueOf(band.minScore()),
                        BigDecimal.valueOf(band.maxScore()),
                        band.remark()))
                .toList();
    }

    private StudentResultResponse.TraitAssessment toTraitAssessment(StudentTraitAssessmentView view) {
        return new StudentResultResponse.TraitAssessment(
                view.getName(),
                view.getCategory(),
                view.getRating(),
                view.getComment());
    }

    private List<StudentResultResponse.TraitAssessment> filterTraitAssessments(
            List<StudentResultResponse.TraitAssessment> assessments,
            String... categories) {
        Set<String> allowed = Arrays.stream(categories)
                .map(category -> category.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return assessments.stream()
                .filter(assessment -> assessment.category() != null)
                .filter(assessment -> allowed.contains(assessment.category().toUpperCase(Locale.ROOT)))
                .toList();
    }

    private StudentResultResponse.RankingInfo buildRankingInfo(Optional<ClassRanking> ranking) {
        return ranking.map(value -> {
                    double percentile = value.getOutOf() <= 0
                            ? 0
                            : ((double) (value.getOutOf() - value.getClassPosition() + 1) / value.getOutOf()) * 100;
                    return new StudentResultResponse.RankingInfo(
                            value.getClassPosition(),
                            value.getOutOf(),
                            percentile,
                            value.getOutOf() > 0 && value.getClassPosition() <= Math.ceil(value.getOutOf() / 3.0));
                })
                .orElse(null);
    }

    private StudentResultResponse.AttendanceInfo buildAttendanceInfo(Optional<ReportComment> comment) {
        return comment.map(value -> {
                    int open = value.getAttendanceDaysOpen() == null ? 0 : value.getAttendanceDaysOpen();
                    int present = value.getAttendanceDaysPresent() == null ? 0 : value.getAttendanceDaysPresent();
                    int absent = value.getAttendanceDaysAbsent() == null ? Math.max(open - present, 0) : value.getAttendanceDaysAbsent();
                    double rate = open == 0 ? 0 : ((double) present / open) * 100;
                    return new StudentResultResponse.AttendanceInfo(open, present, absent, rate);
                })
                .orElse(null);
    }

    private String effectiveTeacherComment(ReportComment comment) {
        String finalComment = trimToNull(comment.getTeacherCommentFinal());
        return finalComment != null ? finalComment : comment.getTeacherComment();
    }

    private String effectivePrincipalComment(ReportComment comment) {
        String finalComment = trimToNull(comment.getPrincipalCommentFinal());
        return finalComment != null ? finalComment : comment.getPrincipalComment();
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String fullName(Student student) {
        return Arrays.stream(new String[] {student.getFirstName(), student.getMiddleName(), student.getLastName()})
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private SchoolFeeException duplicateScoreException(Throwable cause) {
        return new SchoolFeeException(
                "SCORE_ALREADY_EXISTS",
                "A score already exists for one or more students in this batch",
                "scores",
                cause);
    }

    private ReportCardTemplateRequest validateTemplateRequest(ReportCardTemplateRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_CARD_TEMPLATE",
                    "Report-card template request is required");
        }
        String name = trimToNull(request.name());
        if (name == null) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_CARD_TEMPLATE",
                    "Template name is required",
                    "name");
        }
        if (name.length() > 100) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_CARD_TEMPLATE",
                    "Template name cannot exceed 100 characters",
                    "name");
        }
        String educationLevel = normalizeEducationLevel(request.educationLevel());
        if (request.config() == null || request.config().isNull() || request.config().isMissingNode()) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_CARD_TEMPLATE",
                    "Template config is required",
                    "config");
        }
        if (!request.config().isObject()) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_CARD_TEMPLATE",
                    "Template config must be a JSON object",
                    "config");
        }
        return new ReportCardTemplateRequest(
                name,
                educationLevel,
                request.config(),
                request.isDefault());
    }

    private String normalizeEducationLevel(String educationLevel) {
        String normalized = trimToNull(educationLevel);
        if (normalized == null) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_CARD_TEMPLATE",
                    "Education level is required",
                    "educationLevel");
        }
        String upper = normalized.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        boolean valid = Set.of("NURSERY", "PRIMARY", "JUNIOR_SECONDARY", "SENIOR_SECONDARY").contains(upper);
        if (!valid && GradeLevel.isValid(upper)) {
            upper = GradeLevel.valueOf(upper).getCategory();
            valid = true;
        }
        if (!valid) {
            throw new SchoolFeeException(
                    "INVALID_REPORT_CARD_TEMPLATE",
                    "Education level must be one of NURSERY, PRIMARY, JUNIOR_SECONDARY, or SENIOR_SECONDARY",
                    "educationLevel");
        }
        return upper;
    }

    private com.fasterxml.jackson.databind.JsonNode normalizeGradingConfig(com.fasterxml.jackson.databind.JsonNode config) {
        if (config == null || config.isNull() || config.isMissingNode()) {
            com.fasterxml.jackson.databind.node.ObjectNode empty = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            empty.putObject("byEducationLevel");
            return empty;
        }
        return config;
    }

    private int countConfiguredGrades(com.fasterxml.jackson.databind.JsonNode config) {
        if (config == null || config.isNull() || config.isMissingNode()) {
            return 0;
        }
        if (config.isArray()) {
            return config.size();
        }
        if (config.path("byEducationLevel").isObject()) {
            int count = 0;
            Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = config.path("byEducationLevel").fields();
            while (fields.hasNext()) {
                com.fasterxml.jackson.databind.JsonNode levelConfig = fields.next().getValue();
                count += levelConfig.path("grades").isArray() ? levelConfig.path("grades").size() : 0;
            }
            return count;
        }
        return config.path("grades").isArray() ? config.path("grades").size() : 0;
    }

    private ReportCardTemplateResponse toTemplateResponse(ReportCardTemplate template) {
        return new ReportCardTemplateResponse(
                template.getId(),
                template.getName(),
                template.getEducationLevel(),
                template.getConfig(),
                template.isDefaultTemplate(),
                template.isActive(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    private Mono<Void> validateReportCardTemplate(UUID templateId, UUID schoolId) {
        if (templateId == null) {
            return Mono.empty();
        }
        return reportCardTemplateRepository.findActiveByIdAndSchoolId(templateId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "REPORT_CARD_TEMPLATE_NOT_FOUND",
                        "Report-card template not found",
                        "templateId")))
                .then();
    }

    private UUID requireSchoolId(SchoolFeeUser user) {
        if (user == null || user.getSchoolId() == null) {
            throw new SchoolFeeException(
                    "SCHOOL_CONTEXT_REQUIRED",
                    "A school context is required");
        }
        return user.getSchoolId();
    }

    private NormalizedCaConfig validateAndNormalizeCaConfig(CaConfigRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_CA_CONFIG",
                    "CA configuration request is required");
        }
        if (request.components() == null || request.components().isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_CA_CONFIG",
                    "At least one CA component is required",
                    "components");
        }

        List<NormalizedCaComponent> components = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (CaConfigRequest.CaComponentRequest component : request.components()) {
            if (component == null) {
                throw new SchoolFeeException(
                        "INVALID_CA_CONFIG",
                        "CA component cannot be null",
                        "components");
            }

            String name = trimToNull(component.name());
            if (name == null) {
                throw new SchoolFeeException(
                        "INVALID_CA_CONFIG",
                        "CA component name is required",
                        "components.name");
            }
            String normalizedNameKey = name.toLowerCase(Locale.ROOT);
            if (!names.add(normalizedNameKey)) {
                throw new SchoolFeeException(
                        "DUPLICATE_CA_COMPONENT",
                        "CA component names must be unique",
                        "components.name");
            }

            if (component.maxScore() <= 0) {
                throw new SchoolFeeException(
                        "INVALID_CA_CONFIG",
                        "CA component max score must be positive",
                        "components.maxScore");
            }
            if (component.sortOrder() < 0) {
                throw new SchoolFeeException(
                        "INVALID_CA_CONFIG",
                        "CA component sort order cannot be negative",
                        "components.sortOrder");
            }
            if (!sortOrders.add(component.sortOrder())) {
                throw new SchoolFeeException(
                        "DUPLICATE_CA_COMPONENT",
                        "CA component sort orders must be unique",
                        "components.sortOrder");
            }

            BigDecimal weight = normalizePercentage(
                    component.weightPercentage(),
                    "CA component weight must be positive",
                    "components.weightPercentage");
            components.add(new NormalizedCaComponent(
                    name,
                    component.maxScore(),
                    weight,
                    component.sortOrder()));
        }

        BigDecimal caTotal = components.stream()
                .map(NormalizedCaComponent::weightPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal examWeight = normalizePercentage(
                request.examWeightPercentage(),
                "Exam weight must be positive",
                "examWeightPercentage");
        if (caTotal.add(examWeight).compareTo(ONE_HUNDRED) != 0) {
            throw new SchoolFeeException(
                    "INVALID_WEIGHTS",
                    "CA + Exam weights must equal 100%");
        }

        List<NormalizedCaComponent> orderedComponents = components.stream()
                .sorted(Comparator.comparingInt(NormalizedCaComponent::sortOrder))
                .collect(Collectors.toList());
        return new NormalizedCaConfig(orderedComponents, examWeight);
    }

    private BigDecimal normalizePercentage(double value, String message, String field) {
        if (!Double.isFinite(value)) {
            throw new SchoolFeeException("INVALID_CA_CONFIG", message, field);
        }
        BigDecimal percentage = BigDecimal.valueOf(value);
        if (percentage.compareTo(BigDecimal.ZERO) <= 0 || percentage.compareTo(ONE_HUNDRED) > 0) {
            throw new SchoolFeeException("INVALID_CA_CONFIG", message, field);
        }
        return percentage;
    }

    private CaComponent toCaComponentEntity(UUID schoolId, NormalizedCaComponent component) {
        return CaComponent.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .name(component.name())
                .maxScore(component.maxScore())
                .weightPercentage(component.weightPercentage())
                .sortOrder(component.sortOrder())
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedCaConfig(
            List<NormalizedCaComponent> components,
            BigDecimal examWeightPercentage) {
    }

    private record NormalizedCaComponent(
            String name,
            int maxScore,
            BigDecimal weightPercentage,
            int sortOrder) {
    }

    private record ScoreValue(UUID studentId, BigDecimal score) {
    }

    private record GradeBand(String grade, double minScore, double maxScore, String remark) {
    }

    private record ReportCardJobState(
            UUID schoolId,
            String status,
            int totalStudents,
            int completedStudents,
            int failedStudents,
            Instant completedAt,
            String message
    ) {
    }


    @Override
    public Mono<DataBuffer> downloadStudentResultPdf(UUID studentId, UUID termId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> getStudentResult(studentId, termId)
                .map(result -> {
                    byte[] pdfBytes = resultPdfGenerator.generateStudentResultPdf(
                            result,
                            user.getSchoolName());
                    return new DefaultDataBufferFactory().wrap(pdfBytes);
                }));
    }

    @Override
    public Mono<ShareResultResponse> shareStudentResult(
            UUID studentId, UUID termId, ShareResultRequest request) {

        return getStudentResult(studentId, termId)
                .flatMap(result -> {
                    String studentName = result.student() == null
                            ? "Student"
                            : result.student().fullName();
                    String termName = result.term() == null
                            ? "Published"
                            : result.term().name() + " " + result.term().sessionName();
                    String average = result.summary() == null
                            ? "N/A"
                            : result.summary().average().stripTrailingZeros().toPlainString() + "%";
                    String grade = result.summary() == null
                            ? "N/A"
                            : Objects.toString(result.summary().overallGrade(), "N/A");
                    String position = result.ranking() == null
                            ? "N/A"
                            : result.ranking().classPosition() + " of " + result.ranking().outOf();
                    String shareUrl = "https://schoolfee.app/dashboard?section=results&studentId="
                            + studentId + "&termId=" + termId;

                    String shareText = String.format(
                            "%s - %s Results%nAverage: %s | Grade: %s | Position: %s%nView full report: %s",
                            studentName, termName, average, grade, position, shareUrl);

                    if ("SMS".equalsIgnoreCase(request.channel())) {
                        return smsService.send(request.recipient(), shareText)
                                .thenReturn(new ShareResultResponse("SMS", Instant.now(),
                                        "Result sent to " + request.recipient(),
                                        shareText,
                                        shareUrl));
                    } else if ("WHATSAPP".equalsIgnoreCase(request.channel())) {
                        return Mono.just(new ShareResultResponse("WHATSAPP", Instant.now(),
                                "WhatsApp message prepared",
                                shareText,
                                shareUrl));
                    } else {
                        return Mono.just(new ShareResultResponse("EMAIL", Instant.now(),
                                "Email sharing is not configured yet",
                                shareText,
                                shareUrl));
                    }
                });
    }

    /**
     * Build result data for PDF generation or sharing.
     */
    private Mono<StudentResultData> buildStudentResultData(UUID studentId, UUID termId) {
        return studentRepository.findById(studentId)
                .zipWith(termRepository.findById(termId))
                .flatMap(tuple -> {
                    Student student = tuple.getT1();
                    Term term = tuple.getT2();

                    return finalScoreRepository.findByStudentIdAndTermIdOrderBySubjectId(studentId, termId)
                            .flatMap(fs -> subjectRepository.findById(fs.getSubjectId())
                                    .map(subject -> new SubjectResultData(
                                            subject.getName(),
                                            fs.getCaTotal(),
                                            fs.getCaMaxTotal(),
                                            fs.getExamScore(),
                                            fs.getExamMaxScore(),
                                            fs.getFinalScore(),
                                            fs.getGrade(),
                                            fs.getRemark(),
                                            fs.getSubjectPosition())))
                            .collectList()
                            .flatMap(subjects -> {
                                // Get ranking
                                return rankingRepository.findByStudentIdAndTermId(studentId, termId)
                                        .map(ranking -> new StudentResultData(
                                                student.getFirstName() + " " + student.getLastName(),
                                                student.getAdmissionNumber(),
                                                null, // Phase 2: className
                                                term.getName(),
                                                subjects,
                                                calculateAverage(subjects),
                                                calculateOverallGrade(subjects),
                                                ranking.getClassPosition() + " of " + ranking.getOutOf(),
                                                ranking.getSubjectsPassed() + "/" + ranking.getSubjectsTaken(),
                                                null, // Phase 2: teacher comment
                                                null  // Phase 2: principal comment
                                        ))
                                        .defaultIfEmpty(new StudentResultData(
                                                student.getFirstName() + " " + student.getLastName(),
                                                student.getAdmissionNumber(),
                                                null, term.getName(), subjects,
                                                calculateAverage(subjects),
                                                calculateOverallGrade(subjects),
                                                "N/A", "N/A", null, null));
                            });
                });
    }



    private String calculateAverage(List<SubjectResultData> subjects) {
        if (subjects.isEmpty()) return "N/A";
        BigDecimal total = subjects.stream()
                .map(SubjectResultData::finalScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = subjects.stream()
                .map(SubjectResultData::finalScore)
                .filter(Objects::nonNull)
                .count();
        return count > 0
                ? total.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP) + "%"
                : "N/A";
    }

    private String calculateOverallGrade(List<SubjectResultData> subjects) {
        // Phase 2: Use grading config to determine overall grade
        return "N/A";
    }

    private record DefaultTrait(String name, String category, int sortOrder) {}
    private record GeneratedComment(String text, boolean generated) {}
}
