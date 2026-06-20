package com.fee.app.schoolfeeapp.student.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import com.fee.app.schoolfeeapp.student.domain.AttendanceSession;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.domain.StudentAttendance;
import com.fee.app.schoolfeeapp.student.dto.request.CreateAttendanceSessionRequest;
import com.fee.app.schoolfeeapp.student.dto.request.MarkAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.response.*;
import com.fee.app.schoolfeeapp.student.repository.AttendanceSessionRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentAttendanceRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import com.fee.app.schoolfeeapp.student.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final StudentAttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final StudentGuardianRepository guardianRepository;
    private final StudentGuardianLinkRepository guardianLinkRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final TermRepository termRepository;

    // ========================================================================
    // SESSION MANAGEMENT
    // ========================================================================

    @Override
    public Mono<AttendanceSessionResponse> createSession(CreateAttendanceSessionRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = user.getSchoolId();
                    return userRepository.findByKeycloakIdAndDeletedAtIsNull(user.getUserId())
                            .map(User::getId)
                            .flatMap(localUserId ->
                                    // Check if session already exists for this class/date/type
                                    sessionRepository
                                            .findByClassIdAndDateAndSessionType(
                                                    request.classId(), request.date(), request.sessionType())
                                            .flatMap(existing -> getSessionStats(existing)
                                                    .map(stats -> buildSessionResponse(
                                                            existing, stats.total(), stats.marked(),
                                                            stats.present(), stats.absent(), stats.late())))
                                            .switchIfEmpty(Mono.defer(() -> {
                                                AttendanceSession session = AttendanceSession.builder()
                                                        .schoolId(schoolId)
                                                        .classId(request.classId())
                                                        .termId(request.termId())
                                                        .date(request.date())
                                                        .sessionType(request.sessionType())
                                                        .markedBy(localUserId)
                                                        .isComplete(false)
                                                        .build();

                                                return sessionRepository.save(session)
                                                        .map(saved -> buildSessionResponse(saved, 0, 0, 0, 0, 0))
                                                        .onErrorResume(org.springframework.dao.DataIntegrityViolationException.class, ex -> {
                                                            log.warn("Concurrent session creation detected for class {}, date {}, type {}. Retrying fetch.",
                                                                    request.classId(), request.date(), request.sessionType());
                                                            return sessionRepository.findByClassIdAndDateAndSessionType(
                                                                            request.classId(), request.date(), request.sessionType())
                                                                    .flatMap(existing -> getSessionStats(existing)
                                                                            .map(stats -> buildSessionResponse(
                                                                                    existing, stats.total(), stats.marked(),
                                                                                    stats.present(), stats.absent(), stats.late())))
                                                                    .switchIfEmpty(Mono.error(ex));
                                                        });
                                            })));
                });
    }

    @Override
    public Mono<List<AttendanceSessionResponse>> getSessions(UUID classId, LocalDate date) {
        return jwtUtils.getCurrentUser()
                .flatMapMany(user -> {
                    if (date != null) {
                        return sessionRepository.findByClassIdAndDate(classId, date);
                    }
                    return termRepository.findCurrentTermsBySchoolId(user.getSchoolId())
                            .next()
                            .flatMapMany(term -> sessionRepository.findByClassIdAndTermId(classId, term.getId()))
                            .switchIfEmpty(Flux.empty());
                })
                .flatMap(session -> getSessionStats(session)
                        .map(stats -> buildSessionResponse(
                                session, stats.total(), stats.marked(),
                                stats.present(), stats.absent(), stats.late())))
                .collectList();
    }

    // ========================================================================
    // MARK ATTENDANCE
    // ========================================================================

    @Override
    public Mono<List<AttendanceResponse>> markAttendance(UUID sessionId, MarkAttendanceRequest request) {
        Mono<List<AttendanceResponse>> submission = jwtUtils.getCurrentUser()
                .flatMap(user -> sessionRepository.findByIdForUpdate(sessionId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "SESSION_NOT_FOUND", "Attendance session not found")))
                        .flatMap(session -> {
                            UUID schoolId = user.getSchoolId();

                            return userRepository.findByKeycloakIdAndDeletedAtIsNull(user.getUserId())
                                    .map(User::getId)
                                    .flatMap(localUserId -> Flux.fromIterable(request.marks())
                                            .concatMap(mark -> saveAttendanceMark(
                                                    schoolId, session, localUserId, mark)
                                                    .flatMap(savedMark -> {
                                                        if (savedMark.created()
                                                                && isArrivalNotificationStatus(mark.status())) {
                                                            return sendAttendanceNotification(
                                                                    savedMark.attendance(),
                                                                    session,
                                                                    user.getSchoolName())
                                                                    .thenReturn(savedMark.attendance());
                                                        }
                                                        return Mono.just(savedMark.attendance());
                                                    }))
                                            .collectList()
                                            .flatMap(savedRecords -> {
                                                session.setComplete(true);
                                                session.setMarkedAt(Instant.now());
                                                return sessionRepository.save(session)
                                                        .thenReturn(savedRecords);
                                            })
                                            .flatMap(savedRecords -> {
                                                if (savedRecords.isEmpty()) {
                                                    return Mono.just(Collections.emptyList());
                                                }
                                                List<UUID> studentIds = savedRecords.stream()
                                                        .map(StudentAttendance::getStudentId)
                                                        .toList();
                                                return studentRepository.findAllById(studentIds)
                                                        .collectMap(Student::getId)
                                                        .map(studentMap -> savedRecords.stream()
                                                                .map(record -> {
                                                                    Student student = studentMap.get(record.getStudentId());
                                                                    String studentName = student != null 
                                                                            ? student.getFirstName() + " " + student.getLastName() 
                                                                            : "Unknown Student";
                                                                    String admissionNumber = student != null 
                                                                            ? student.getAdmissionNumber() 
                                                                            : "";
                                                                    return new AttendanceResponse(
                                                                            record.getId(),
                                                                            record.getStudentId(),
                                                                            studentName,
                                                                            admissionNumber,
                                                                            record.getStatus(),
                                                                            record.getDate(),
                                                                            session.getSessionType(),
                                                                            record.getArrivalTime(),
                                                                            record.getBroughtBy(),
                                                                            record.getDepartureTime(),
                                                                            record.getPickedUpBy(),
                                                                            record.getPickUpPersonName(),
                                                                            record.getPickUpPersonPhone(),
                                                                            record.getNotes());
                                                                })
                                                                .toList());
                                            }));
                        }));

        // Attendance marks, session completion, and notification events must either
        // all commit or all roll back. This prevents a notification failure from
        // leaving attendance rows behind that collide with the teacher's retry.
        return transactionalOperator.transactional(submission);
    }

    @Override
    public Mono<List<AttendanceResponse>> getSessionMarks(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .switchIfEmpty(Mono.error(new SchoolFeeException("SESSION_NOT_FOUND", "Attendance session not found")))
                .flatMap(session -> attendanceRepository.findBySessionId(sessionId)
                        .collectList()
                        .flatMap(records -> {
                            if (records.isEmpty()) {
                                return Mono.just(Collections.emptyList());
                            }
                            List<UUID> studentIds = records.stream()
                                    .map(StudentAttendance::getStudentId)
                                    .toList();
                            return studentRepository.findAllById(studentIds)
                                    .collectMap(Student::getId)
                                    .map(studentMap -> records.stream()
                                            .map(record -> {
                                                Student student = studentMap.get(record.getStudentId());
                                                String studentName = student != null 
                                                        ? student.getFirstName() + " " + student.getLastName() 
                                                        : "Unknown Student";
                                                String admissionNumber = student != null 
                                                        ? student.getAdmissionNumber() 
                                                        : "";
                                                return new AttendanceResponse(
                                                        record.getId(),
                                                        record.getStudentId(),
                                                        studentName,
                                                        admissionNumber,
                                                        record.getStatus(),
                                                        record.getDate(),
                                                        session.getSessionType(),
                                                        record.getArrivalTime(),
                                                        record.getBroughtBy(),
                                                        record.getDepartureTime(),
                                                        record.getPickedUpBy(),
                                                        record.getPickUpPersonName(),
                                                        record.getPickUpPersonPhone(),
                                                        record.getNotes());
                                            })
                                            .toList());
                        }));
    }

    @Override
    public Mono<AttendanceResponse> updateAttendanceMark(UUID markId, UpdateAttendanceRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> attendanceRepository.findById(markId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "MARK_NOT_FOUND", "Attendance record not found")))
                        .flatMap(record -> {
                            record.setStatus(request.status().toUpperCase());
                            if (request.arrivalTime() != null) {
                                if (request.arrivalTime().isBlank()) {
                                    record.setArrivalTime(null);
                                } else {
                                    try {
                                        record.setArrivalTime(LocalTime.parse(request.arrivalTime()));
                                    } catch (java.time.format.DateTimeParseException e) {
                                        return Mono.error(new SchoolFeeException("INVALID_TIME_FORMAT", "Arrival time must be in HH:mm format"));
                                    }
                                }
                            }
                            record.setBroughtBy(request.broughtBy());
                            if (request.departureTime() != null) {
                                if (request.departureTime().isBlank()) {
                                    record.setDepartureTime(null);
                                } else {
                                    try {
                                        record.setDepartureTime(LocalTime.parse(request.departureTime()));
                                    } catch (java.time.format.DateTimeParseException e) {
                                        return Mono.error(new SchoolFeeException("INVALID_TIME_FORMAT", "Departure time must be in HH:mm format"));
                                    }
                                }
                            }
                            record.setPickedUpBy(request.pickedUpBy());
                            record.setPickUpPersonName(request.pickUpPersonName());
                            record.setPickUpPersonPhone(request.pickUpPersonPhone());
                            record.setNotes(request.notes());
                            record.setUpdatedAt(Instant.now());

                            return attendanceRepository.save(record);
                        })
                        .flatMap(this::toAttendanceResponse));
    }

    // ========================================================================
    // VIEW ATTENDANCE
    // ========================================================================

    @Override
    public Mono<TodayAttendanceResponse> getTodayClassAttendance(UUID classId) {
        LocalDate today = LocalDate.now();

        return jwtUtils.getCurrentUser()
                .flatMap(user -> classRepository.findById(classId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException("CLASS_NOT_FOUND", "Class not found")))
                        .filter(cls -> Objects.equals(cls.getSchoolId(), user.getSchoolId()))
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "ACCESS_DENIED", "Class does not belong to your school")))
                        .flatMap(cls -> Mono.zip(
                                        studentRepository.findActiveBySchoolIdAndCurrentClassId(
                                                        user.getSchoolId(), classId)
                                                .collectList(),
                                        attendanceRepository.findByClassIdAndDate(classId, today)
                                                .collectList())
                                .flatMap(tuple -> buildTodayResponse(
                                        classId, cls.getName(), today,
                                        tuple.getT1(), tuple.getT2())))
                );
    }

    @Override
    public Mono<List<AttendanceResponse>> getStudentAttendance(UUID studentId, UUID termId) {
        return verifyStudentAccess(studentId)
                .then(jwtUtils.getCurrentUser())
                .flatMap(user -> studentRepository.findById(studentId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException("STUDENT_NOT_FOUND", "Student not found")))
                        .flatMap(student -> attendanceRepository.findByStudentIdAndTermIdOrderByDateDesc(studentId, termId)
                                .collectList()
                                .flatMap(records -> {
                                    if (records.isEmpty()) {
                                        return Mono.just(Collections.emptyList());
                                    }
                                    List<UUID> sessionIds = records.stream()
                                            .map(StudentAttendance::getSessionId)
                                            .distinct()
                                            .toList();
                                    return sessionRepository.findAllById(sessionIds)
                                            .collectMap(AttendanceSession::getId)
                                            .map(sessionMap -> records.stream()
                                                    .map(record -> {
                                                        AttendanceSession session = sessionMap.get(record.getSessionId());
                                                        String sessionType = session != null ? session.getSessionType() : "";
                                                        return new AttendanceResponse(
                                                                record.getId(),
                                                                record.getStudentId(),
                                                                student.getFirstName() + " " + student.getLastName(),
                                                                student.getAdmissionNumber(),
                                                                record.getStatus(),
                                                                record.getDate(),
                                                                sessionType,
                                                                record.getArrivalTime(),
                                                                record.getBroughtBy(),
                                                                record.getDepartureTime(),
                                                                record.getPickedUpBy(),
                                                                record.getPickUpPersonName(),
                                                                record.getPickUpPersonPhone(),
                                                                record.getNotes());
                                                    })
                                                    .toList());
                                })));
    }

    @Override
    public Mono<AttendanceSummaryResponse> getStudentAttendanceSummary(UUID studentId, UUID termId) {
        return verifyStudentAccess(studentId)
                .then(attendanceRepository.getAttendanceCounts(studentId, termId))
                .map(counts -> {
                    long total = counts.getTotal() != null ? counts.getTotal() : 0;
                    long present = counts.getPresent() != null ? counts.getPresent() : 0;
                    long absent = counts.getAbsent() != null ? counts.getAbsent() : 0;
                    long late = counts.getLate() != null ? counts.getLate() : 0;
                    double percentage = total > 0 ? ((double) present / total) * 100 : 0;

                    return new AttendanceSummaryResponse(
                            (int) total, (int) present, (int) absent, (int) late, 0,
                            Math.round(percentage * 100.0) / 100.0);
                })
                .defaultIfEmpty(new AttendanceSummaryResponse(0, 0, 0, 0, 0, 0));
    }

    // ========================================================================
    // PARENT VIEW
    // ========================================================================

    @Override
    public Mono<List<ParentAttendanceResponse>> getMyChildrenTodayAttendance() {
        LocalDate today = LocalDate.now();

        return jwtUtils.getCurrentUser()
                .flatMapMany(parentUser -> {
                    if (!parentUser.isParent()) {
                        return Flux.error(new SchoolFeeException("ACCESS_DENIED", "Only parents"));
                    }

                    return guardianRepository.findByKeycloakId(parentUser.getUserId())
                            .flatMapMany(guardian ->
                                    guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardian.getId()))
                            .filter(link -> Boolean.TRUE.equals(link.getCanViewAttendance()))
                            .flatMap(link ->
                                    attendanceRepository.findByStudentIdAndDate(link.getStudentId(), today)
                                            .collectList()
                                            .flatMap(records ->
                                                    studentRepository.findById(link.getStudentId())
                                                            .flatMap(student -> {
                                                                if (student.getCurrentClassId() == null) {
                                                                    return buildParentResponse(student, "Unassigned Class", records);
                                                                }
                                                                return classRepository.findById(student.getCurrentClassId())
                                                                        .map(ClassEntity::getName)
                                                                        .defaultIfEmpty("Unknown Class")
                                                                        .flatMap(className -> buildParentResponse(student, className, records));
                                                            })));
                })
                .collectList();
    }

    @Override
    public Mono<List<ParentAttendanceResponse>> getMyChildrenAttendance(UUID studentId, LocalDate date) {
        return jwtUtils.getCurrentUser()
                .flatMapMany(parentUser -> {
                    if (!parentUser.isParent()) {
                        return Flux.error(new SchoolFeeException("ACCESS_DENIED", "Only parents"));
                    }

                    return guardianRepository.findByKeycloakId(parentUser.getUserId())
                            .flatMap(guardian -> guardianLinkRepository
                                    .findByGuardianIdAndStudentIdAndDeletedAtIsNull(
                                            guardian.getId(), studentId))
                            .filter(link -> Boolean.TRUE.equals(link.getCanViewAttendance()))
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "ACCESS_DENIED",
                                    "You do not have permission to view this child's attendance")))
                            .thenMany(attendanceRepository.findByStudentIdAndDate(studentId, date))
                            .collectList()
                            .flatMapMany(records ->
                                    studentRepository.findById(studentId)
                                            .flatMap(student -> {
                                                if (student.getCurrentClassId() == null) {
                                                    return buildParentResponse(student, "Unassigned Class", records);
                                                }
                                                return classRepository.findById(student.getCurrentClassId())
                                                        .map(ClassEntity::getName)
                                                        .defaultIfEmpty("Unknown Class")
                                                        .flatMap(className -> buildParentResponse(student, className, records));
                                            })
                                            .flux());
                })
                .collectList();
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private Mono<AttendanceResponse> toAttendanceResponse(StudentAttendance record) {
        Mono<String> sessionType = sessionRepository.findById(record.getSessionId())
                .map(AttendanceSession::getSessionType)
                .defaultIfEmpty("");
        return Mono.zip(studentRepository.findById(record.getStudentId()), sessionType)
                .map(tuple -> new AttendanceResponse(
                        record.getId(),
                        record.getStudentId(),
                        tuple.getT1().getFirstName() + " " + tuple.getT1().getLastName(),
                        tuple.getT1().getAdmissionNumber(),
                        record.getStatus(),
                        record.getDate(),
                        tuple.getT2(),
                        record.getArrivalTime(),
                        record.getBroughtBy(),
                        record.getDepartureTime(),
                        record.getPickedUpBy(),
                        record.getPickUpPersonName(),
                        record.getPickUpPersonPhone(),
                        record.getNotes()));
    }

    private Mono<SavedAttendanceMark> saveAttendanceMark(
            UUID schoolId,
            AttendanceSession session,
            UUID localUserId,
            MarkAttendanceRequest.AttendanceMark mark) {
        return attendanceRepository
                .findByStudentIdAndSessionId(mark.studentId(), session.getId())
                .hasElement()
                .flatMap(alreadyExists -> attendanceRepository.upsertAttendanceMark(
                                schoolId,
                                session.getId(),
                                mark.studentId(),
                                session.getClassId(),
                                session.getTermId(),
                                session.getDate(),
                                mark.status().toUpperCase(),
                                parseTime(mark.arrivalTime()),
                                mark.broughtBy(),
                                parseTime(mark.departureTime()),
                                mark.pickedUpBy(),
                                mark.pickUpPersonName(),
                                mark.pickUpPersonPhone(),
                                mark.notes(),
                                localUserId)
                        .map(saved -> new SavedAttendanceMark(saved, !alreadyExists)));
    }

    private LocalTime parseTime(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value);
    }

    private boolean isArrivalNotificationStatus(String status) {
        return "PRESENT".equalsIgnoreCase(status) || "LATE".equalsIgnoreCase(status);
    }

    private Mono<Void> sendAttendanceNotification(
            StudentAttendance attendance,
            AttendanceSession session,
            String schoolName) {
        return studentRepository.findById(attendance.getStudentId())
                .flatMap(student ->
                        findPrimaryGuardian(student.getId())
                                .flatMap(guardian -> {
                                    String message;
                                    String resolvedSchoolName = schoolName == null || schoolName.isBlank()
                                            ? "School"
                                            : schoolName;
                                    if ("MORNING_ARRIVAL".equals(session.getSessionType())) {
                                        message = String.format(
                                                "%s arrived at school at %s. Brought by %s. — %s",
                                                student.getFirstName(),
                                                attendance.getArrivalTime(),
                                                attendance.getBroughtBy() != null ? attendance.getBroughtBy() : "Parent",
                                                resolvedSchoolName);
                                    } else {
                                        message = String.format(
                                                "%s left school at %s. Picked up by %s. — %s",
                                                student.getFirstName(),
                                                attendance.getDepartureTime(),
                                                attendance.getPickUpPersonName() != null
                                                        ? attendance.getPickUpPersonName() : "Parent",
                                                resolvedSchoolName);
                                    }

                                    Map<String, Object> payload = new HashMap<>();
                                    payload.put("schoolId", attendance.getSchoolId().toString());
                                    payload.put("schoolName", resolvedSchoolName);
                                    payload.put("guardianId", guardian.getId().toString());
                                    payload.put("guardianPhone", guardian.getPhone());
                                    payload.put("guardianEmail", guardian.getEmail());
                                    payload.put("message", message);
                                    payload.put("studentId", student.getId().toString());
                                    payload.put("notificationType", session.getSessionType());

                                    OutboxEvent event = OutboxEvent.builder()
                                            .eventType("ATTENDANCE_NOTIFICATION")
                                            .aggregateId(attendance.getId())
                                            .aggregateType("STUDENT_ATTENDANCE")
                                            .payload(objectMapper.valueToTree(payload))
                                            .status("PENDING")
                                            .retryCount(0)
                                            .maxRetries(3)
                                            .nextRetryAt(Instant.now())
                                            .createdAt(Instant.now())
                                            .build();

                                    return outboxEventRepository.save(event).then();
                                }));
    }

    private Mono<StudentGuardian> findPrimaryGuardian(UUID studentId) {
        return guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId)
                .next()
                .flatMap(link -> guardianRepository.findById(link.getGuardianId()));
    }

    private Mono<Void> verifyStudentAccess(UUID studentId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    if ("PARENT".equals(user.getUserType())) {
                        return guardianRepository.findByKeycloakId(user.getUserId())
                                .flatMap(guardian -> guardianLinkRepository
                                        .findByGuardianIdAndStudentIdAndDeletedAtIsNull(
                                                guardian.getId(), studentId))
                                .filter(link -> Boolean.TRUE.equals(link.getCanViewAttendance()))
                                .switchIfEmpty(Mono.error(new SchoolFeeException(
                                        "ACCESS_DENIED",
                                        "You do not have permission to view this child's attendance")))
                                .then();
                    }
                    // For staff, ensure the student belongs to their school
                    return studentRepository.findById(studentId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException("STUDENT_NOT_FOUND", "Student not found")))
                            .filter(student -> Objects.equals(student.getSchoolId(), user.getSchoolId()))
                            .switchIfEmpty(Mono.error(new SchoolFeeException("ACCESS_DENIED", "Student does not belong to your school")))
                            .then();
                });
    }

    private AttendanceSessionResponse buildSessionResponse(
            AttendanceSession session, int total, int marked, int present, int absent, int late) {
        return new AttendanceSessionResponse(
                session.getId(),
                session.getClassId(),
                null, // Phase 2: className
                session.getTermId(),
                null, // Phase 2: termName
                session.getDate(),
                session.getSessionType(),
                session.isComplete(),
                total,
                marked,
                present,
                absent,
                late);
    }

    private Mono<SessionStats> getSessionStats(AttendanceSession session) {
        return attendanceRepository.findBySessionId(session.getId())
                .collectList()
                .map(records -> {
                    int total = records.size();
                    int present = (int) records.stream()
                            .filter(r -> "PRESENT".equals(r.getStatus())).count();
                    int late = (int) records.stream()
                            .filter(r -> "LATE".equals(r.getStatus())).count();
                    int absent = (int) records.stream()
                            .filter(r -> "ABSENT".equals(r.getStatus())).count();
                    return new SessionStats(total, total, present, absent, late);
                })
                .defaultIfEmpty(new SessionStats(0, 0, 0, 0, 0));
    }

    private Mono<TodayAttendanceResponse> buildTodayResponse(
            UUID classId, String className, LocalDate date,
            List<Student> students, List<StudentAttendance> records) {
        if (records.isEmpty()) {
            List<AttendanceResponse> allStudents = students.stream()
                    .map(student -> new AttendanceResponse(
                            null,
                            student.getId(),
                            student.getFirstName() + " " + student.getLastName(),
                            student.getAdmissionNumber(),
                            "NOT_MARKED",
                            date,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null))
                    .toList();
            return Mono.just(new TodayAttendanceResponse(
                    classId, className, date.toString(),
                    students.size(), 0, 0, 0, students.size(),
                    allStudents));
        }

        Map<UUID, Student> studentMap = students.stream()
                .collect(java.util.stream.Collectors.toMap(Student::getId, s -> s));

        List<UUID> sessionIds = records.stream()
                .map(StudentAttendance::getSessionId)
                .distinct()
                .toList();

        return sessionRepository.findAllById(sessionIds)
                .collectMap(AttendanceSession::getId)
                .map(sessionMap -> {
                    List<AttendanceResponse> responses = records.stream()
                            .map(record -> {
                                Student student = studentMap.get(record.getStudentId());
                                String studentName = student != null 
                                        ? student.getFirstName() + " " + student.getLastName() 
                                        : "Unknown Student";
                                String admissionNumber = student != null 
                                        ? student.getAdmissionNumber() 
                                        : "";
                                AttendanceSession session = sessionMap.get(record.getSessionId());
                                String sessionType = session != null ? session.getSessionType() : "";
                                return new AttendanceResponse(
                                        record.getId(),
                                        record.getStudentId(),
                                        studentName,
                                        admissionNumber,
                                        record.getStatus(),
                                        record.getDate(),
                                        sessionType,
                                        record.getArrivalTime(),
                                        record.getBroughtBy(),
                                        record.getDepartureTime(),
                                        record.getPickedUpBy(),
                                        record.getPickUpPersonName(),
                                        record.getPickUpPersonPhone(),
                                        record.getNotes());
                            })
                            .toList();

                    Map<UUID, AttendanceResponse> morningByStudent = responses.stream()
                            .filter(response -> "MORNING_ARRIVAL".equals(response.sessionType()))
                            .collect(java.util.stream.Collectors.toMap(
                                    AttendanceResponse::studentId,
                                    response -> response,
                                    (first, ignored) -> first));
                    int present = (int) morningByStudent.values().stream()
                            .filter(response -> "PRESENT".equals(response.status())
                                    || "LATE".equals(response.status()))
                            .count();
                    int absent = (int) morningByStudent.values().stream()
                            .filter(response -> "ABSENT".equals(response.status()))
                            .count();
                    int late = (int) morningByStudent.values().stream()
                            .filter(response -> "LATE".equals(response.status()))
                            .count();
                    int notMarked = Math.max(0, students.size() - morningByStudent.size());

                    List<AttendanceResponse> allStudents = new ArrayList<>(responses);
                    Set<UUID> markedStudentIds = responses.stream()
                            .map(AttendanceResponse::studentId)
                            .collect(java.util.stream.Collectors.toSet());
                    students.stream()
                            .filter(student -> !markedStudentIds.contains(student.getId()))
                            .map(student -> new AttendanceResponse(
                                    null,
                                    student.getId(),
                                    student.getFirstName() + " " + student.getLastName(),
                                    student.getAdmissionNumber(),
                                    "NOT_MARKED",
                                    date,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null))
                            .forEach(allStudents::add);

                    return new TodayAttendanceResponse(
                            classId, className, date.toString(),
                            students.size(), present, absent, late, notMarked,
                            allStudents);
                });
    }

    private Mono<ParentAttendanceResponse> buildParentResponse(
            Student student, String className, List<StudentAttendance> records) {
        if (records.isEmpty()) {
            return Mono.just(new ParentAttendanceResponse(
                    student.getId(),
                    student.getFirstName() + " " + student.getLastName(),
                    className,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
        }

        List<UUID> sessionIds = records.stream()
                .map(StudentAttendance::getSessionId)
                .distinct()
                .toList();

        return sessionRepository.findAllById(sessionIds)
                .collectMap(AttendanceSession::getId)
                .map(sessionMap -> {
                    List<AttendanceWithSession> entries = records.stream()
                            .map(record -> {
                                AttendanceSession session = sessionMap.get(record.getSessionId());
                                String sessionType = session != null ? session.getSessionType() : "";
                                return new AttendanceWithSession(record, sessionType);
                            })
                            .toList();

                    StudentAttendance morning = entries.stream()
                            .filter(entry -> "MORNING_ARRIVAL".equals(entry.sessionType()))
                            .map(AttendanceWithSession::attendance)
                            .findFirst()
                            .orElse(null);
                    StudentAttendance afternoon = entries.stream()
                            .filter(entry -> "AFTERNOON_DEPARTURE".equals(entry.sessionType()))
                            .map(AttendanceWithSession::attendance)
                            .findFirst()
                            .orElse(null);

                    return new ParentAttendanceResponse(
                            student.getId(),
                            student.getFirstName() + " " + student.getLastName(),
                            className,
                            records.get(0).getDate(),
                            morning != null ? morning.getArrivalTime() : null,
                            morning != null ? morning.getBroughtBy() : null,
                            parentArrivalStatus(morning),
                            afternoon != null ? afternoon.getDepartureTime() : null,
                            afternoon != null ? afternoon.getStatus() : null,
                            afternoon != null ? afternoon.getPickedUpBy() : null,
                            afternoon != null ? afternoon.getPickUpPersonName() : null,
                            afternoon != null ? afternoon.getPickUpPersonPhone() : null);
                });
    }

    private String parentArrivalStatus(StudentAttendance morning) {
        if (morning == null) {
            return null;
        }
        if ("PRESENT".equals(morning.getStatus())) {
            return morning.getArrivalTime() != null
                    && morning.getArrivalTime().isBefore(LocalTime.of(8, 0))
                    ? "ON_TIME"
                    : "LATE";
        }
        return morning.getStatus();
    }

    private record AttendanceWithSession(
            StudentAttendance attendance, String sessionType) {}

    private record SavedAttendanceMark(
            StudentAttendance attendance, boolean created) {}

    private record SessionStats(int total, int marked, int present, int absent, int late) {}
}
