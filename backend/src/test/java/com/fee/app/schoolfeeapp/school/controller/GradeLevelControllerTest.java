package com.fee.app.schoolfeeapp.school.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.dto.request.ConfigureGradeLevelsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.ConfigureGradeLevelsResponse;
import com.fee.app.schoolfeeapp.school.dto.response.GradeLevelResponse;
import com.fee.app.schoolfeeapp.school.service.GradeLevelService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeLevelControllerTest {

    @Mock
    private GradeLevelService gradeLevelService;

    @InjectMocks
    private GradeLevelController gradeLevelController;

    @Test
    @DisplayName("Should get school grade levels successfully")
    void shouldGetSchoolGradeLevelsSuccessfully() {
        List<GradeLevelResponse> serviceResponse = gradeLevelResponses();
        when(gradeLevelService.getSchoolGradeLevels()).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(gradeLevelController.getGradeLevels())
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<GradeLevelResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getErrors()).isNull();
                    assertThat(body.getData()).containsExactlyElementsOf(serviceResponse);
                })
                .verifyComplete();

        verify(gradeLevelService, times(1)).getSchoolGradeLevels();
    }

    @Test
    @DisplayName("Should propagate get school grade levels error")
    void shouldPropagateGetSchoolGradeLevelsError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "SCHOOL_NOT_FOUND",
                "School not found");
        when(gradeLevelService.getSchoolGradeLevels()).thenReturn(Mono.error(expectedError));

        StepVerifier.create(gradeLevelController.getGradeLevels())
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(gradeLevelService, times(1)).getSchoolGradeLevels();
    }

    @Test
    @DisplayName("Should get available grade levels successfully")
    void shouldGetAvailableGradeLevelsSuccessfully() {
        List<GradeLevelResponse> serviceResponse = gradeLevelResponses();
        when(gradeLevelService.getAvailableGradeLevels()).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(gradeLevelController.getAvailableGradeLevels())
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<GradeLevelResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).containsExactlyElementsOf(serviceResponse);
                })
                .verifyComplete();

        verify(gradeLevelService, times(1)).getAvailableGradeLevels();
    }

    @Test
    @DisplayName("Should configure grade levels successfully")
    void shouldConfigureGradeLevelsSuccessfully() {
        ConfigureGradeLevelsRequest request = validRequest();
        ConfigureGradeLevelsResponse serviceResponse = new ConfigureGradeLevelsResponse(
                2,
                List.of("PRIMARY_1", "PRIMARY_2"),
                "Grade levels configured. You can now create classes for these levels.");
        when(gradeLevelService.configureGradeLevels(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(gradeLevelController.configureGradeLevels(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<ConfigureGradeLevelsResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getErrors()).isNull();
                    assertThat(body.getData().enabledLevels()).isEqualTo(2);
                    assertThat(body.getData().enabledLevelCodes()).containsExactly("PRIMARY_1", "PRIMARY_2");
                })
                .verifyComplete();

        verify(gradeLevelService, times(1)).configureGradeLevels(request);
    }

    @Test
    @DisplayName("Should propagate configure grade levels error")
    void shouldPropagateConfigureGradeLevelsError() {
        ConfigureGradeLevelsRequest request = validRequest();
        SchoolFeeException expectedError = new SchoolFeeException(
                "INVALID_GRADE_LEVELS",
                "Invalid grade level codes: BAD_CODE",
                "enabledLevels");
        when(gradeLevelService.configureGradeLevels(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(gradeLevelController.configureGradeLevels(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(gradeLevelService, times(1)).configureGradeLevels(request);
    }

    private List<GradeLevelResponse> gradeLevelResponses() {
        return List.of(
                new GradeLevelResponse("PRIMARY_1", "Primary 1", "PRIMARY", 4),
                new GradeLevelResponse("PRIMARY_2", "Primary 2", "PRIMARY", 5));
    }

    private ConfigureGradeLevelsRequest validRequest() {
        return new ConfigureGradeLevelsRequest(
                List.of("PRIMARY_1", "PRIMARY_2"),
                null);
    }
}
