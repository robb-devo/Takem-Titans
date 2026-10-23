package com.takem.titans.registry;

import com.takem.titans.odm.OdmPlayerData;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries.Keys;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(Keys.ATTACHMENT_TYPES, "takemtitans");
    public static final Supplier<AttachmentType<OdmPlayerData>> ODM = ATTACHMENTS.register(
        "odm", () -> AttachmentType.<CompoundTag, OdmPlayerData>serializable(OdmPlayerData::new).copyOnDeath().build()
    );

    private ModAttachments() {
    }
}
