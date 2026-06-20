package com.fee.app.schoolfeeapp.result.service;

import com.fee.app.schoolfeeapp.result.dto.request.AssignSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.request.CreateSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.response.ClassSubjectResponse;
import com.fee.app.schoolfeeapp.result.dto.response.SubjectResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface SubjectService {

    Mono<SubjectResponse> createSubject(CreateSubjectRequest request);
    Mono<List<SubjectResponse>> listSubjects();
    Mono<SubjectResponse> updateSubject(UUID subjectId, CreateSubjectRequest request);
    Mono<Void> deactivateSubject(UUID subjectId);

    Mono<ClassSubjectResponse> assignSubjectToClass(UUID classId, AssignSubjectRequest request);
    Mono<List<ClassSubjectResponse>> getSubjectsForClass(UUID classId);
    Mono<Void> removeSubjectFromClass(UUID classId, UUID subjectId);
}