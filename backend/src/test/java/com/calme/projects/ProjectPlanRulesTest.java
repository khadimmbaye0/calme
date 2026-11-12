package com.calme.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import com.calme.tasks.TaskPriority;

import org.junit.jupiter.api.Test;

class ProjectPlanRulesTest {

    @Test
    void sortsStepsInDeterministicTopologicalOrder() {
        List<PlanStep> steps = List.of(
                step(0, "Cadrage", 30),
                step(1, "Recherche", 60, 0),
                step(2, "Rédaction", 90, 0, 1));

        List<PlanStep> ordered = ProjectPlanRules.validateAndSort(steps);

        assertThat(ordered).extracting(PlanStep::index).containsExactly(0, 1, 2);
    }

    @Test
    void rejectsCycles() {
        List<PlanStep> steps = List.of(
                step(0, "A", 30, 1),
                step(1, "B", 30, 0));

        assertThatThrownBy(() -> ProjectPlanRules.validateAndSort(steps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectsOutOfRangeAndSelfDependencies() {
        assertThatThrownBy(() -> ProjectPlanRules.validateAndSort(List.of(step(0, "A", 30, 4))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inexistante");

        assertThatThrownBy(() -> ProjectPlanRules.validateAndSort(List.of(step(0, "A", 30, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("elle-même");
    }

    @Test
    void computesRequiredMinutesWithBuffer() {
        List<PlanStep> steps = List.of(step(0, "A", 60), step(1, "B", 40));

        BigDecimal required = ProjectPlanRules.requiredMinutes(steps, new BigDecimal("0.20"));

        assertThat(required).isEqualByComparingTo("120.000");
    }

    @Test
    void rejectsTooSmallBuffer() {
        assertThatThrownBy(() -> ProjectPlanRules.requiredMinutes(
                List.of(step(0, "A", 60)), new BigDecimal("0.10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20 %");
    }

    @Test
    void comparesRequiredMinutesToAvailableCapacity() {
        assertThat(ProjectPlanRules.fits(new BigDecimal("120"), 120)).isTrue();
        assertThat(ProjectPlanRules.fits(new BigDecimal("120.1"), 120)).isFalse();
    }

    @Test
    void rejectsDuplicateDependenciesWithoutDoubleCounting() {
        List<PlanStep> steps = List.of(
                step(0, "A", 30),
                new PlanStep(1, "B", 30, 30, TaskPriority.NORMAL, List.of(0, 0)));

        List<PlanStep> ordered = ProjectPlanRules.validateAndSort(steps);

        assertThat(ordered).extracting(PlanStep::index).containsExactly(0, 1);
    }

    private PlanStep step(int index, String title, int estimate, int... dependencies) {
        List<Integer> dependsOn = java.util.Arrays.stream(dependencies).boxed().toList();
        return new PlanStep(index, title, estimate, estimate, TaskPriority.NORMAL, dependsOn);
    }
}
