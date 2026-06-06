package com.fee.app.schoolfeeapp.result.service.impl;


import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.result.domain.*;
import com.fee.app.schoolfeeapp.result.dto.request.CaConfigRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ExamScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.request.GradingRuleRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ReportCardRequest;
import com.fee.app.schoolfeeapp.result.dto.response.*;
import com.fee.app.schoolfeeapp.result.repository.*;
import com.fee.app.schoolfeeapp.result.service.ResultService;
import com.fee.app.schoolfeeapp.result.service.ScoreComputationEngine;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final CaComponentRepository caComponentRepository;
    private final CaScoreRepository caScoreRepository;
    private final ScoreRepository scoreRepository;
    private final FinalScoreRepository finalScoreRepository;
    private final ClassRankingRepository rankingRepository;
    private final GradeConfigRepository gradeConfigRepository;
    private final ReportCommentRepository commentRepository;
    private final PublishedResultRepository publishedResultRepository;
    private final ScoreAuditLogRepository auditLogRepository;
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
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;
    private final ScoreComputationEngine computationEngine;
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
                            Mono<CaScoreResponse> transaction = verifyCaScoreContext(validatedRequest, schoolId)
                                    .then(verifyNotPublished(schoolId, validatedRequest.termId()))
                                    .thenMany(Flux.fromIterable(validatedRequest.scores())
                                            .concatMap(scoreReq -> caScoreRepository.insert(
                                                    toCaScoreEntity(validatedRequest, scoreReq, schoolId, user.getUserId()))))
                                    .collectList()
                                    .map(scores -> new CaScoreResponse(
                                            UUID.randomUUID(),
                                            validatedRequest.classId().toString(),
                                            validatedRequest.subjectId().toString(),
                                            validatedRequest.caComponentId().toString(),
                                            scores.size(),
                                            "CA scores recorded"));

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
                            Mono<ExamScoreResponse> transaction = verifyExamScoreContext(validatedRequest, schoolId)
                                    .then(verifyNotPublished(schoolId, validatedRequest.termId()))
                                    .thenMany(Flux.fromIterable(validatedRequest.scores())
                                            .concatMap(scoreReq -> scoreRepository.insert(
                                                    toExamScoreEntity(validatedRequest, scoreReq, schoolId, user.getUserId()))))
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
                                                    "Exam scores recorded. Final scores computed.")));

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
                            Mono<UpdateScoreResponse> transaction = scoreRepository.findByIdAndSchoolIdForUpdate(scoreId, schoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException("SCORE_NOT_FOUND", "Score not found", "scoreId")))
                                    .flatMap(score -> verifyNotPublished(schoolId, score.getTermId())
                                            .then(Mono.defer(() -> doUpdateScore(score, validatedRequest, schoolId, user))));

                            return transactionalOperator.transactional(transaction);
                        }));
    }

    private Mono<UpdateScoreResponse> doUpdateScore(
            ResultScore score, UpdateScoreRequest request, UUID schoolId, SchoolFeeUser user) {
        if (request.score().compareTo(BigDecimal.valueOf(score.getMaxScore())) > 0) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_SCORE",
                    "Score must be between 0 and " + score.getMaxScore(),
                    "score"));
        }

        BigDecimal oldScore = score.getScore();
        Instant updatedAt = Instant.now();
        score.setScore(request.score());
        score.setUpdatedAt(updatedAt);

        return scoreRepository.save(score)
                .flatMap(saved -> auditScoreChange("EXAM_SCORE", saved.getId(), saved.getStudentId(),
                        saved.getSubjectId(), saved.getTermId(), oldScore, request.score(),
                        schoolId, user.getUserId(), request.reason(), updatedAt))
                .then(computeFinalScores(score.getClassId(), score.getTermId(), score.getSubjectId()))
                .then(autoComputeRankings(score.getClassId(), score.getTermId(), schoolId))
                .thenReturn(new UpdateScoreResponse(score.getId(), request.score(), oldScore, updatedAt));
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
                    return guardianRepository.findByUserIdAndDeletedAtIsNull(parentUser.getUserId())
                            .flatMapMany(guardian ->
                                    guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardian.getId()))
                            .filter(link -> Boolean.TRUE.equals(link.getCanViewResults()))
                            .map(link -> link.getStudentId())
                            .distinct()
                            .concatMap(studentId -> buildMyChildResult(studentId, schoolId));
                })
                .collectList();
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

    // ========================================================================
    // COMMENTS
    // ========================================================================

    @Override
    public Mono<ReportCommentResponse> addTeacherComment(UUID studentId, UUID termId, String comment) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return validateCommentContext(studentId, termId, schoolId)
                            .then(Mono.defer(() ->
                                    saveComment(studentId, termId, schoolId, comment, true, user.getUserId())));
                });
    }

    @Override
    public Mono<ReportCommentResponse> addPrincipalComment(UUID studentId, UUID termId, String comment) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return validateCommentContext(studentId, termId, schoolId)
                            .then(Mono.defer(() ->
                                    saveComment(studentId, termId, schoolId, comment, false, user.getUserId())));
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
                    return termRepository.findByIdAndSchoolId(termId, schoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "TERM_NOT_FOUND",
                                    "Term not found",
                                    "termId")))
                            .then(Mono.defer(() -> publishResultsForTerm(termId, schoolId, user.getUserId())))
                            .onErrorResume(DuplicateKeyException.class, ex -> Mono.error(new SchoolFeeException(
                                    "RESULTS_ALREADY_PUBLISHED",
                                    "Results are already published for this term")));
                });
    }

    @Override
    public Mono<PublishResultResponse> unpublishResults(UUID termId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return termRepository.findByIdAndSchoolId(termId, schoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "TERM_NOT_FOUND",
                                    "Term not found",
                                    "termId")))
                            .then(Mono.defer(() -> publishedResultRepository.findBySchoolIdAndTermId(schoolId, termId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "RESULTS_NOT_PUBLISHED",
                                            "Results have not been published yet.")))
                                    .flatMap(published -> publishedResultRepository.delete(published))
                                    .thenReturn(toUnpublishResponse(termId, user.getUserId()))));
                });
    }

    // ========================================================================
    // GRADING RULES
    // ========================================================================

    @Override
    public Mono<GradingRuleResponse> configureGradingRules(GradingRuleRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> gradeConfigRepository.findBySchoolId(user.getSchoolId())
                        .flatMap(existing -> {
                            existing.setConfig(request.config());
                            existing.setUpdatedAt(Instant.now());
                            return gradeConfigRepository.save(existing);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            GradeConfig config = GradeConfig.builder()
                                    .id(UUID.randomUUID())
                                    .schoolId(user.getSchoolId())
                                    .config(request.config())
                                    .isActive(true)
                                    .build();
                            return gradeConfigRepository.save(config);
                        }))
                        .map(saved -> new GradingRuleResponse(user.getSchoolId(), request.config().size(),
                                "Grading rules updated")));
    }

    @Override
    public Mono<GradingRuleResponse> getGradingRules() {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> gradeConfigRepository.findBySchoolId(user.getSchoolId())
                        .map(config -> new GradingRuleResponse(user.getSchoolId(),
                                config.getConfig().size(), "Current grading rules")));
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
        return guardianRepository.findByUserIdAndDeletedAtIsNull(userId)
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
        ScoreComputationEngine.RankingParameters params =
                new ScoreComputationEngine.RankingParameters(40, 100);
        return computationEngine.computeSubjectPositions(classId, termId)
                .then(computationEngine.computeClassRankings(classId, termId, schoolId, params));
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
                                                     String comment, boolean isTeacher, UUID authorId) {
        return commentRepository.findByStudentIdAndTermIdAndSchoolId(studentId, termId, schoolId)
                .flatMap(existing -> {
                    if (isTeacher) {
                        existing.setTeacherComment(comment);
                        existing.setTeacherId(authorId);
                    } else {
                        existing.setPrincipalComment(comment);
                        existing.setPrincipalId(authorId);
                    }
                    existing.setUpdatedAt(Instant.now());
                    return commentRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> createComment(studentId, termId, schoolId, comment, isTeacher, authorId)))
                .onErrorResume(DuplicateKeyException.class, ex -> commentRepository
                        .findByStudentIdAndTermIdAndSchoolId(studentId, termId, schoolId)
                        .switchIfEmpty(Mono.error(ex))
                        .flatMap(existing -> {
                            if (isTeacher) {
                                existing.setTeacherComment(comment);
                                existing.setTeacherId(authorId);
                            } else {
                                existing.setPrincipalComment(comment);
                                existing.setPrincipalId(authorId);
                            }
                            existing.setUpdatedAt(Instant.now());
                            return commentRepository.save(existing);
                        }))
                .map(saved -> new ReportCommentResponse(
                        studentId,
                        termId,
                        comment,
                        saved.getUpdatedAt() != null ? saved.getUpdatedAt() : Instant.now()));
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
                                               String comment, boolean isTeacher, UUID authorId) {
        Instant now = Instant.now();
        ReportComment rc = ReportComment.builder()
                .schoolId(schoolId)
                .studentId(studentId)
                .termId(termId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        if (isTeacher) {
            rc.setTeacherComment(comment);
            rc.setTeacherId(authorId);
        } else {
            rc.setPrincipalComment(comment);
            rc.setPrincipalId(authorId);
        }
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

        return Mono.zip(classMono, classSizeMono, sessionMono, finalScoresMono, rankingMono, commentMono)
                .flatMap(tuple -> Flux.fromIterable(tuple.getT4())
                        .concatMap(finalScore -> buildSubjectResult(finalScore, schoolId))
                        .collectList()
                        .map(subjects -> {
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
                                    buildResultSummary(tuple.getT4(), ranking),
                                    buildRankingInfo(ranking),
                                    buildAttendanceInfo(comment),
                                    comment.map(ReportComment::getTeacherComment).orElse(null),
                                    comment.map(ReportComment::getPrincipalComment).orElse(null));
                        }));
    }

    private Mono<StudentResultResponse.SubjectResult> buildSubjectResult(FinalScore finalScore, UUID schoolId) {
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

        return Mono.zip(subjectMono, caScoresMono)
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
                        finalScore.getGrade(),
                        finalScore.getRemark(),
                        finalScore.getPoints(),
                        finalScore.getSubjectPosition() == null ? 0 : finalScore.getSubjectPosition(),
                        null,
                        null,
                        null));
    }

    private Mono<ClassResultSheetResponse> buildClassResultSheet(UUID classId, UUID termId, UUID schoolId) {
        return Mono.just(new ClassResultSheetResponse(null, null, 0, List.of(), List.of()));
    }

    private Mono<MyChildResultResponse> buildMyChildResult(UUID studentId, UUID schoolId) {
        return Mono.just(new MyChildResultResponse(studentId, null, null, null, null, null, List.of()));
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
                .then(examRepository.findByIdAndSchoolIdAndTermId(request.examId(), schoolId, request.termId())
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "EXAM_NOT_FOUND",
                                "Exam not found",
                                "examId")))
                        .flatMap(exam -> {
                            if (exam.getMaxScore() != request.maxScore()) {
                                return Mono.error(new SchoolFeeException(
                                        "INVALID_SCORE_BATCH",
                                        "Exam max score must match the configured exam max score",
                                        "maxScore"));
                            }
                            return Mono.empty();
                        }))
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
            List<FinalScore> finalScores, Optional<ClassRanking> ranking) {
        if (finalScores.isEmpty()) {
            return new StudentResultResponse.ResultSummary(
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, null,
                    BigDecimal.ZERO, 0, 0, 0);
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
                subjectsTaken - subjectsPassed);
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
}
