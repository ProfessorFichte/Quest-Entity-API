package com.qeapi.compat;

import com.qeapi.QuestAPI;
import net.levelz.access.LevelManagerAccess;
import net.levelz.level.LevelManager;
import net.levelz.level.Skill;
import net.levelz.util.LevelHelper;
import net.levelz.util.PacketHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

// All direct references to LevelZ's classes live in this file only - callers must check isLoaded()
// first, so the JVM never resolves them when the mod isn't present. LevelZ only ships a Fabric
// build, so this only ever activates there.
public final class LevelZCompat {

    private static final String MOD_ID = "levelz";

    private LevelZCompat() {}

    public static boolean isLoaded() {
        return ModCompatUtil.isModLoaded(MOD_ID);
    }

    // LevelZ ships one standalone 16x16 PNG per skill, addressed by convention from the skill's
    // key - no Java-side icon lookup needed, unlike Pufferfish's Skills.
    public static ResourceLocation skillIcon(String skillId) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/sprites/" + skillId + ".png");
    }

    private static Optional<Skill> findSkill(String skillKey) {
        return LevelManager.SKILLS.values().stream()
                .filter(skill -> skill.getKey().equals(skillKey))
                .findFirst();
    }

    public static int getSkillLevel(Player player, String skillKey) {
        Optional<Skill> skill = findSkill(skillKey);
        if (skill.isEmpty()) return 0;
        LevelManager levelManager = ((LevelManagerAccess) player).getLevelManager();
        return levelManager.getSkillLevel(skill.get().getId());
    }

    // setSkillLevel() is a plain map write with no side effects, so the client sync and
    // attribute-modifier reapplication have to be replicated here from LevelZ's own
    // resetSkill()/skill GUI handlers (see LevelHelper.updateSkill/PacketHelper.updatePlayerSkills).
    public static void addSkillLevels(ServerPlayer player, String skillKey, int levels) {
        Optional<Skill> skillOpt = findSkill(skillKey);
        if (skillOpt.isEmpty()) {
            QuestAPI.LOGGER.warn("[LevelZCompat] Unknown skill: {}", skillKey);
            return;
        }

        Skill skill = skillOpt.get();
        LevelManager levelManager = ((LevelManagerAccess) player).getLevelManager();
        int newLevel = Math.min(levelManager.getSkillLevel(skill.getId()) + levels, skill.getMaxLevel());
        levelManager.setSkillLevel(skill.getId(), newLevel);

        LevelHelper.updateSkill(player, skill);
        PacketHelper.updatePlayerSkills(player, null);
    }
}
