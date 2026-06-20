package com.fee.app.schoolfeeapp.fee.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeeReportingRepository Static Helpers Unit Tests")
class FeeReportingRepositoryTest {

    @Test
    @DisplayName("Should cover all branches of money() helper")
    void shouldCoverAllBranchesOfMoneyHelper() throws Exception {
        Method moneyMethod = FeeReportingRepository.class.getDeclaredMethod("money", Object.class);
        moneyMethod.setAccessible(true);

        // Branch 1: value instanceof BigDecimal
        BigDecimal exactDecimal = new BigDecimal("5000.50");
        BigDecimal result1 = (BigDecimal) moneyMethod.invoke(null, exactDecimal);
        assertThat(result1).isEqualTo(exactDecimal);

        // Branch 2: value instanceof Number (e.g., Double, Integer, Long from DB driver)
        Double doubleVal = 150.75;
        BigDecimal result2 = (BigDecimal) moneyMethod.invoke(null, doubleVal);
        assertThat(result2).isEqualByComparingTo(BigDecimal.valueOf(150.75));

        Long longVal = 1000L;
        BigDecimal result3 = (BigDecimal) moneyMethod.invoke(null, longVal);
        assertThat(result3).isEqualByComparingTo(BigDecimal.valueOf(1000.0));

        // Branch 3: Fallback (String, other objects, or null)
        BigDecimal result4 = (BigDecimal) moneyMethod.invoke(null, "Not a number");
        assertThat(result4).isEqualTo(BigDecimal.ZERO);

        BigDecimal result5 = (BigDecimal) moneyMethod.invoke(null, (Object) null);
        assertThat(result5).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should cover all branches of number() helper")
    void shouldCoverAllBranchesOfNumberHelper() throws Exception {
        Method numberMethod = FeeReportingRepository.class.getDeclaredMethod("number", Object.class);
        numberMethod.setAccessible(true);

        // Branch 1: value instanceof Number
        Integer intVal = 42;
        Number result1 = (Number) numberMethod.invoke(null, intVal);
        assertThat(result1).isEqualTo(42);

        Long longVal = 999L;
        Number result2 = (Number) numberMethod.invoke(null, longVal);
        assertThat(result2).isEqualTo(999L);

        // Branch 2: Fallback (String, other objects, or null)
        Number result3 = (Number) numberMethod.invoke(null, "Not a number");
        assertThat(result3).isEqualTo(0);

        Number result4 = (Number) numberMethod.invoke(null, (Object) null);
        assertThat(result4).isEqualTo(0);
    }
}