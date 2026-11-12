package com.calme.planner;

import com.calme.blocks.BlockType;
import com.calme.energy.EnergyLevel;

public record NowResponse(
        TaskSummary task,
        EnergyLevel energy,
        BlockType blockType,
        int capacityMinutes,
        String message) {
}
