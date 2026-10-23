package com.takem.titans.network;

import com.takem.titans.odm.OdmServer;
import com.takem.titans.skill.Skills;
import com.takem.titans.story.Shop;
import com.takem.titans.story.Story;
import java.util.function.BiConsumer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TakemNetwork {
    public static BiConsumer<HookSyncPayload, IPayloadContext> hookSyncHandler = (p, c) -> {
    };
    public static BiConsumer<FeedbackPayload, IPayloadContext> feedbackHandler = (p, c) -> {
    };
    public static BiConsumer<FxPayload, IPayloadContext> fxHandler = (p, c) -> {
    };
    public static BiConsumer<SectionDetachPayload, IPayloadContext> detachHandler = (p, c) -> {
    };
    public static BiConsumer<StoryPayload, IPayloadContext> storyHandler = (p, c) -> {
    };

    private TakemNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(HookActionPayload.TYPE, HookActionPayload.STREAM_CODEC, OdmServer::handleHookAction);
        r.playToServer(OdmInputPayload.TYPE, OdmInputPayload.STREAM_CODEC, OdmServer::handleInput);
        r.playToServer(OdmActionPayload.TYPE, OdmActionPayload.STREAM_CODEC, OdmServer::handleAction);
        r.playToClient(HookSyncPayload.TYPE, HookSyncPayload.STREAM_CODEC, (p, c) -> hookSyncHandler.accept(p, c));
        r.playToClient(FeedbackPayload.TYPE, FeedbackPayload.STREAM_CODEC, (p, c) -> feedbackHandler.accept(p, c));
        r.playToClient(FxPayload.TYPE, FxPayload.STREAM_CODEC, (p, c) -> fxHandler.accept(p, c));
        r.playToServer(StoryActionPayload.TYPE, StoryActionPayload.STREAM_CODEC, Story::handleAction);
        r.playToServer(ShopBuyPayload.TYPE, ShopBuyPayload.STREAM_CODEC, Shop::handleBuy);
        r.playToServer(SkillActionPayload.TYPE, SkillActionPayload.STREAM_CODEC, Skills::handle);
        r.playToClient(StoryPayload.TYPE, StoryPayload.STREAM_CODEC, (p, c) -> storyHandler.accept(p, c));
        r.playToClient(SectionDetachPayload.TYPE, SectionDetachPayload.STREAM_CODEC, (p, c) -> detachHandler.accept(p, c));
    }
}
