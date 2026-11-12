package com.calme.projects;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class ProjectPlanRules {

    public static final BigDecimal MIN_BUFFER = new BigDecimal("0.200");

    private ProjectPlanRules() {
    }

    /**
     * Validates dependency indexes and returns the steps in a deterministic
     * topological order. Throws IllegalArgumentException for invalid input.
     */
    public static List<PlanStep> validateAndSort(List<PlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Le projet doit contenir au moins une étape.");
        }

        int size = steps.size();
        Map<Integer, PlanStep> byIndex = new HashMap<>();
        for (PlanStep step : steps) {
            if (step.index() < 0 || step.index() >= size) {
                throw new IllegalArgumentException("Une étape a un index invalide.");
            }
            if (byIndex.put(step.index(), step) != null) {
                throw new IllegalArgumentException("Une étape est répétée.");
            }
            if (step.title() == null || step.title().isBlank()) {
                throw new IllegalArgumentException("Le titre d'une étape est obligatoire.");
            }
            if (step.estimateMin() <= 0 || step.estimateMax() < step.estimateMin()) {
                throw new IllegalArgumentException("Une estimation d'étape est invalide.");
            }
        }
        if (byIndex.size() != size) {
            throw new IllegalArgumentException("Les étapes doivent être numérotées de 0 à n-1.");
        }

        List<Set<Integer>> adjacency = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            adjacency.add(new HashSet<>());
        }
        int[] inDegree = new int[size];

        for (PlanStep step : steps) {
            Set<Integer> dependencies = step.dependsOn() == null
                    ? Set.of()
                    : new HashSet<>(step.dependsOn());
            for (Integer dependency : dependencies) {
                if (dependency == null || dependency < 0 || dependency >= size) {
                    throw new IllegalArgumentException("Une dépendance pointe vers une étape inexistante.");
                }
                if (dependency == step.index()) {
                    throw new IllegalArgumentException("Une étape ne peut pas dépendre d'elle-même.");
                }
                if (adjacency.get(dependency).add(step.index())) {
                    inDegree[step.index()]++;
                }
            }
        }

        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int index = 0; index < size; index++) {
            if (inDegree[index] == 0) {
                ready.add(index);
            }
        }

        List<PlanStep> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            int index = ready.poll();
            ordered.add(byIndex.get(index));
            for (int next : adjacency.get(index)) {
                inDegree[next]--;
                if (inDegree[next] == 0) {
                    ready.add(next);
                }
            }
        }

        if (ordered.size() != size) {
            throw new IllegalArgumentException("Les dépendances forment un cycle.");
        }
        return ordered;
    }

    public static BigDecimal requiredMinutes(List<PlanStep> steps, BigDecimal bufferRatio) {
        if (bufferRatio == null || bufferRatio.compareTo(MIN_BUFFER) < 0) {
            throw new IllegalArgumentException("La marge du projet doit être d'au moins 20 %.");
        }
        int total = steps.stream().mapToInt(PlanStep::estimateMax).sum();
        return BigDecimal.valueOf(total).multiply(BigDecimal.ONE.add(bufferRatio));
    }

    public static boolean fits(BigDecimal requiredMinutes, int availableMinutes) {
        return requiredMinutes.compareTo(BigDecimal.valueOf(availableMinutes)) <= 0;
    }
}
