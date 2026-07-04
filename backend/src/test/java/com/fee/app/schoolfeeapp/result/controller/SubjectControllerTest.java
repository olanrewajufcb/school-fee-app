package com.fee.app.schoolfeeapp.result.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.result.dto.request.AssignSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.request.CreateSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.response.ClassSubjectResponse;
import com.fee.app.schoolfeeapp.result.dto.response.SubjectResponse;
import com.fee.app.schoolfeeapp.result.service.SubjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectControllerTest {

    @Mock
    private SubjectService subjectService;
    @InjectMocks
    private SubjectController subjectController;

    @Test
    void shouldCreateSubjectWithCreatedStatus() {
        CreateSubjectRequest request = new CreateSubjectRequest("Mathematics", "MTH", "SCIENCE");
        SubjectResponse response = subject("Mathematics", "MTH");
        when(subjectService.createSubject(request)).thenReturn(Mono.just(response));

        StepVerifier.create(subjectController.createSubject(request))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    ApiResponse<SubjectResponse> body = entity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(response);
                })
                .verifyComplete();

        verify(subjectService).createSubject(request);
    }

    @Test
    void shouldListSubjects() {
        List<SubjectResponse> subjects = List.of(
                subject("English", "ENG"),
                subject("Mathematics", "MTH"));
        when(subjectService.listSubjects()).thenReturn(Mono.just(subjects));

        StepVerifier.create(subjectController.listSubjects())
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().getData()).containsExactlyElementsOf(subjects);
                })
                .verifyComplete();

        verify(subjectService).listSubjects();
    }

    @Test
    void shouldUpdateSubject() {
        UUID id = UUID.randomUUID();
        CreateSubjectRequest request = new CreateSubjectRequest("English Language", "ENG", "LANGUAGES");
        SubjectResponse response = new SubjectResponse(id, "English Language", "ENG", "LANGUAGES", true);
        when(subjectService.updateSubject(id, request)).thenReturn(Mono.just(response));

        StepVerifier.create(subjectController.updateSubject(id, request))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().getData()).isEqualTo(response);
                })
                .verifyComplete();

        verify(subjectService).updateSubject(id, request);
    }

    @Test
    void shouldPropagateServiceError() {
        CreateSubjectRequest request = new CreateSubjectRequest("Mathematics", "MTH", "SCIENCE");
        SchoolFeeException duplicate = new SchoolFeeException(
                "DUPLICATE_RESOURCE", "Duplicate subject", "name");
        when(subjectService.createSubject(request)).thenReturn(Mono.error(duplicate));

        StepVerifier.create(subjectController.createSubject(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(duplicate))
                .verify();
    }

    private SubjectResponse subject(String name, String code) {
        return new SubjectResponse(UUID.randomUUID(), name, code, "GENERAL", true);
    }

    @Test
    void shouldDeactivateSubject() {
        UUID subjectId = UUID.randomUUID();

        when(subjectService.deactivateSubject(subjectId)).thenReturn(Mono.empty());

        StepVerifier.create(subjectController.deactivateSubject(subjectId))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody()).isNotNull();
                })
                .verifyComplete();

        verify(subjectService).deactivateSubject(subjectId);
    }

    @Test
    void shouldAssignSubjectToClass() {
        UUID id = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        AssignSubjectRequest request = new AssignSubjectRequest(classId, teacherId);
        ClassSubjectResponse response = new ClassSubjectResponse(id,  classId, "English",
                "ENG", teacherId, "Tolu");
        when(subjectService.assignSubjectToClass(id, request)).thenReturn(Mono.just(response));

        StepVerifier.create(subjectController.assignSubjectToClass(id, request))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().getData()).isEqualTo(response);
                })
                .verifyComplete();

        verify(subjectService).assignSubjectToClass(id, request);
    }

    @Test
    void shouldGetSubjectsForClass() {
        UUID id = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        List<ClassSubjectResponse> response = List.of(new ClassSubjectResponse(id,  classId, "English",
                "ENG", teacherId, "Tolu"));
        when(subjectService.getSubjectsForClass(id)).thenReturn(Mono.just(response));

        StepVerifier.create(subjectController.getSubjectsForClass(id))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().getData()).isEqualTo(response);
                })
                .verifyComplete();

        verify(subjectService).getSubjectsForClass(id);
    }

    @Test
    void shouldRemoveSubjectFromClass() {
        UUID subjectId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();

        when(subjectService.removeSubjectFromClass(subjectId, classId)).thenReturn(Mono.empty());

        StepVerifier.create(subjectController.removeSubjectFromClass(subjectId, classId))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody()).isNotNull();
                })
                .verifyComplete();

        verify(subjectService).removeSubjectFromClass(subjectId, classId);
    }
}
