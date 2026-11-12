package com.calme.energy;

public enum EnergyLevel {
    LOW(0.30),
    MID(0.40),
    GOOD(0.60);

    private final double capacityRatio;

    EnergyLevel(double capacityRatio) {
        this.capacityRatio = capacityRatio;
    }

    public double capacityRatio() {
        return capacityRatio;
    }
}
