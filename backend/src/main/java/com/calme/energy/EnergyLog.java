package com.calme.energy;

import java.time.LocalDate;

import com.calme.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "energy_log")
public class EnergyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "energy_date", nullable = false)
    private LocalDate energyDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnergyLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnergySource source;

    protected EnergyLog() {
    }

    public EnergyLog(AppUser user, LocalDate energyDate, EnergyLevel level, EnergySource source) {
        this.user = user;
        this.energyDate = energyDate;
        this.level = level;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public LocalDate getEnergyDate() {
        return energyDate;
    }

    public EnergyLevel getLevel() {
        return level;
    }

    public EnergySource getSource() {
        return source;
    }
}
