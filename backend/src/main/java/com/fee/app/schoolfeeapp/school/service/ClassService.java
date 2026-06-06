package com.fee.app.schoolfeeapp.school.service;

import com.fee.app.schoolfeeapp.school.dto.request.CreateClassRequest;
import com.fee.app.schoolfeeapp.school.dto.request.PromoteStudentsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface ClassService {

    Mono<ClassResponse> createClass(CreateClassRequest request);
    Mono<List<ClassResponse>> listClasses(String sessionId, String gradeLevel, String status);
    Mono<ClassDetailResponse> getClassDetails(UUID classId);
    Mono<UpdateClassResponse> updateClass(UUID classId, UpdateClassRequest request);
    Mono<Void> deactivateClass(UUID classId);
    Mono<PromoteStudentsResponse> promoteStudents(PromoteStudentsRequest request);
}