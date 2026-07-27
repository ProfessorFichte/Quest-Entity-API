package com.qeapi.compat;

import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.api.Category;
import net.puffish.skillsmod.api.Experience;
import net.puffish.skillsmod.api.SkillsAPI;

// Optional integration with Pufferfish's Skills (net.puffish:skillsmod). All direct references to
// skillsmod's classes live in this file only - callers must check isLoaded() first, so the JVM
// never needs to resolve these classes when skillsmod isn't present.
public final class PufferfishSkillsCompat {

    private static final String MOD_ID = "puffish_skills";

    private PufferfishSkillsCompat() {}

    public static boolean isLoaded() {
        return ModCompatUtil.isModLoaded(MOD_ID);
    }

    public static void addExperience(ServerPlayer player, ResourceLocation skillTreeId, int amount) {
        SkillsAPI.getCategory(skillTreeId).flatMap(Category::getExperience).ifPresentOrElse(
                experience -> experience.addTotal(player, amount),
                () -> QuestEntityAPI.LOGGER.warn("[PufferfishSkillsCompat] Unknown skill tree: {}", skillTreeId)
        );
    }

    // skillsmod has no direct "add level" call, so this computes the extra total XP needed to
    // reach (current level + levels).
    public static void addLevels(ServerPlayer player, ResourceLocation skillTreeId, int levels) {
        SkillsAPI.getCategory(skillTreeId).flatMap(Category::getExperience).ifPresentOrElse(
                experience -> {
                    int currentLevel = experience.getLevel(player);
                    int currentTotal = experience.getTotal(player);
                    int targetTotal = experience.getRequiredTotal(currentLevel + levels);
                    if (targetTotal > currentTotal) {
                        experience.addTotal(player, targetTotal - currentTotal);
                    }
                },
                () -> QuestEntityAPI.LOGGER.warn("[PufferfishSkillsCompat] Unknown skill tree: {}", skillTreeId)
        );
    }
}
