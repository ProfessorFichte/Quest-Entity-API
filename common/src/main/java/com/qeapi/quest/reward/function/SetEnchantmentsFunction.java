package com.qeapi.quest.reward.function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.Optional;

public record SetEnchantmentsFunction(List<EnchantmentEntry> enchantments, boolean add) implements ItemFunction {

    public record EnchantmentEntry(ResourceLocation id, int level) {
        public static final Codec<EnchantmentEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ResourceLocation.CODEC.fieldOf("id").forGetter(EnchantmentEntry::id),
                        Codec.INT.optionalFieldOf("level", 1).forGetter(EnchantmentEntry::level)
                ).apply(instance, EnchantmentEntry::new)
        );
    }

    public static final MapCodec<SetEnchantmentsFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EnchantmentEntry.CODEC.listOf().fieldOf("enchantments").forGetter(SetEnchantmentsFunction::enchantments),
                    Codec.BOOL.optionalFieldOf("add", true).forGetter(SetEnchantmentsFunction::add)
            ).apply(instance, SetEnchantmentsFunction::new)
    );

    @Override
    public ItemStack apply(ItemStack stack, ServerPlayer player) {
        ItemEnchantments.Mutable mutable;
        if (add) {
            ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            mutable = new ItemEnchantments.Mutable(existing);
        } else {
            mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        }

        RegistryAccess registryAccess = null;
        if (player != null) {
            registryAccess = player.level().registryAccess();
        } else {
            // no player means this is a display-only call (e.g. GUI preview) - fall back to the client level
            try {
                var minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    registryAccess = minecraft.level.registryAccess();
                }
            } catch (Exception ignored) {
                // not on client, or client not ready yet
            }
        }

        if (registryAccess != null) {
            Registry<Enchantment> registry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
            for (EnchantmentEntry entry : enchantments) {
                Optional<Holder.Reference<Enchantment>> enchantmentHolder = registry.getHolder(
                        ResourceKey.create(Registries.ENCHANTMENT, entry.id())
                );
                enchantmentHolder.ifPresent(holder -> mutable.set(holder, entry.level()));
            }
        }

        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("set_enchantments");
    }

    @Override
    public MapCodec<? extends ItemFunction> getCodec() {
        return CODEC;
    }
}
