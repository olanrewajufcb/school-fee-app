package com.fee.app.schoolfeeapp.student.service;

import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.student.dto.request.EnrollStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.response.*;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface StudentService {

    Mono<EnrollStudentResponse> enrollStudent(EnrollStudentRequest request);
    Mono<PageResponse<StudentListResponse>> listStudents(UUID classId, String status, String search, Pageable pageable);
    Mono<StudentDetailResponse> getStudentDetails(UUID studentId);
    Mono<List<MyChildrenResponse>> getMyChildren();
    Mono<UpdateStudentResponse> updateStudent(UUID studentId, UpdateStudentRequest request);

}