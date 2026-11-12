package com.calme.blocks;

import java.time.LocalDate;
import java.util.Optional;

import com.calme.user.AppUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockService {

    private final BlockRepository blocks;

    public BlockService(BlockRepository blocks) {
        this.blocks = blocks;
    }

    @Transactional(readOnly = true)
    public Optional<BlockType> currentType(AppUser user, LocalDate date) {
        return blocks
                .findFirstByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                        user.getId(), date, date)
                .map(Block::getType);
    }
}
