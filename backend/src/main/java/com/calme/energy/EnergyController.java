package com.calme.energy;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    private final EnergyService energy;

    public EnergyController(EnergyService energy) {
        this.energy = energy;
    }

    @GetMapping("/today")
    public EnergyResponse today() {
        return energy.currentToday();
    }

    @PutMapping("/today")
    public EnergyResponse setToday(@Valid @RequestBody EnergyRequest request) {
        return energy.setToday(request.level());
    }
}
