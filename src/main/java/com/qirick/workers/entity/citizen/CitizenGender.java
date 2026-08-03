package com.qirick.workers.entity.citizen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * A Citizen is either male or female. The gender decides which player model
 * variant and which default skin the entity is rendered with.
 *
 * <p>Male citizens use the wide ("Steve") model, female citizens use the slim
 * ("Alex") model. Both textures come from vanilla, so the mod ships no skins of
 * its own for now.
 */
public enum CitizenGender implements StringRepresentable {

    MALE("male", false, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png")),
    FEMALE("female", true, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/slim/alex.png"));

    private static final CitizenGender[] BY_ID = values();

    private final String name;
    private final boolean slim;
    private final ResourceLocation texture;

    CitizenGender(String name, boolean slim, ResourceLocation texture) {
        this.name = name;
        this.slim = slim;
        this.texture = texture;
    }

    /** {@code true} when this gender renders with the 3px-arm (Alex) player model. */
    public boolean isSlim() {
        return this.slim;
    }

    public ResourceLocation getTexture() {
        return this.texture;
    }

    public byte getId() {
        return (byte) this.ordinal();
    }

    public static CitizenGender byId(byte id) {
        if (id < 0 || id >= BY_ID.length) {
            return MALE;
        }
        return BY_ID[id];
    }

    public static CitizenGender random(RandomSource random) {
        return BY_ID[random.nextInt(BY_ID.length)];
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
