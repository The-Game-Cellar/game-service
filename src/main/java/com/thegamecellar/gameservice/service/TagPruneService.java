package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Tag;
import com.thegamecellar.gameservice.repository.TagRepository;
import com.thegamecellar.gameservice.util.CuratedTagAllowlist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagPruneService {

    private static final int SAMPLE_SIZE = 30;

    private final TagRepository tagRepository;
    private final CuratedTagAllowlist allowlist;

    @Transactional
    public Map<String, Object> pruneToAllowlist() {
        if (!allowlist.isEnabled()) {
            log.warn("Tag prune skipped: allowlist not loaded. Add curated-tags.txt to game-service resources.");
            return Map.of(
                    "skipped", true,
                    "reason", "Curated allowlist not loaded (see CuratedTagAllowlist startup logs)"
            );
        }

        List<Tag> all = tagRepository.findAll();
        int examined = all.size();
        int kept = 0;
        int dropped = 0;
        int joinRowsDeleted = 0;
        List<String> sampleDropped = new ArrayList<>();

        for (Tag tag : all) {
            if (allowlist.isAllowed(tag.getName())) {
                kept++;
                continue;
            }
            joinRowsDeleted += tagRepository.deleteJoinRowsForTag(tag.getId());
            tagRepository.delete(tag);
            dropped++;
            if (sampleDropped.size() < SAMPLE_SIZE) {
                sampleDropped.add(tag.getName());
            }
        }

        log.info("Tag prune complete: examined={} kept={} dropped={} joinRowsDeleted={}",
                examined, kept, dropped, joinRowsDeleted);

        return Map.of(
                "examined", examined,
                "kept", kept,
                "dropped", dropped,
                "joinRowsDeleted", joinRowsDeleted,
                "allowlistSize", allowlist.size(),
                "sampleDropped", sampleDropped
        );
    }
}
