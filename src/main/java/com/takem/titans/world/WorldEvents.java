package com.takem.titans.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.takem.titans.TakemConfig;
import com.takem.titans.fx.Fx;
import com.takem.titans.odm.OdmGear;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.odm.OdmServer;
import com.takem.titans.registry.ModItems;
import com.takem.titans.story.Story;
import com.takem.titans.story.Training;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.level.LevelEvent.CreateSpawnPosition;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(
    modid = "takemtitans"
)
public final class WorldEvents {
    private WorldEvents() {
    }

    @SubscribeEvent
    public static void onCreateSpawn(CreateSpawnPosition event) {
        if (event.getLevel() instanceof ServerLevel level && TitanSpawner.isShiganshina(level)) {
            event.getSettings().setSpawn(new BlockPos(0, 67, 12), 0.0F);
            level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
            // dying to a titan is punishment enough; losing the gear on top of it just sends people back to the menu
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, level.getServer());
            Rules rules = Rules.get(level);
            rules.keepInventorySet = true;
            rules.setDirty();
            event.setCanceled(true);
            return;
        }
    }

    /** Worlds made before keepInventory was the default get it once; whatever the player sets afterwards stays. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        if (TitanSpawner.isShiganshina(level)) {
            if (event.getServer().isSingleplayer()) {
                // the title menu's "Continue" button picks this up
                Path root = event.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                Properties p = new Properties();
                p.setProperty("folder", String.valueOf(root.getFileName()));
                p.setProperty("name", event.getServer().getWorldData().getLevelName());

                try (OutputStream out = Files.newOutputStream(FMLPaths.CONFIGDIR.get().resolve("takemtitans-adventure.properties"))) {
                    p.store(out, "Takem Titans: the last Shiganshina world played on this computer");
                } catch (IOException ignored) {
                }
            }

            Rules rules = Rules.get(level);
            if (!rules.keepInventorySet) {
                rules.keepInventorySet = true;
                rules.setDirty();
                level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, event.getServer());
            }
        }
    }

    static final class Rules extends SavedData {
        boolean keepInventorySet;

        static Rules get(ServerLevel level) {
            return level.getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(Rules::new, (tag, registries) -> {
                    Rules r = new Rules();
                    r.keepInventorySet = tag.getBoolean("KeepInventorySet");
                    return r;
                }, null), "takemtitans_rules");
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("KeepInventorySet", this.keepInventorySet);
            return tag;
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TitanSpawner.isShiganshina(player.level())) {
            OdmPlayerData data = OdmServer.data(player);
            if (!TakemConfig.STARTER_KIT.get() || !TakemConfig.TUTORIAL.get()) {
                // no drills on this server: graduate everyone straight away so nothing waits on the instructor
                if (data.training < Training.DONE) {
                    data.training = Training.DONE;
                }

                if (TakemConfig.STARTER_KIT.get() && !data.starterKitGiven) {
                    data.starterKitGiven = true;
                    giveKit(player);
                    player.sendSystemMessage(Component.translatable("message.takemtitans.welcome"));
                }
            } else if (data.training < Training.DONE) {
                // a new recruit arrives with nothing: Instructor Brandt hands out the gear at the training yard
                Training.greet(player);
            }
        }
    }

    public static void giveKit(ServerPlayer player) {
        giveStarterGear(player);
        giveFieldKit(player);
    }

    /** What a recruit straps on for the drills: gear, cloak, blades and a little spare gas and steel. */
    public static void giveStarterGear(ServerPlayer player) {
        equipOrGive(player, EquipmentSlot.LEGS, new ItemStack(ModItems.ODM_GEAR.get()));
        equipOrGive(player, EquipmentSlot.CHEST, new ItemStack(ModItems.SCOUT_CLOAK.get()));
        if (player.getMainHandItem().isEmpty()) {
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ODM_BLADES.get()));
        } else {
            give(player, new ItemStack(ModItems.ODM_BLADES.get()));
        }

        give(player, new ItemStack(ModItems.BLADE_PACK.get(), 2));
        give(player, new ItemStack(ModItems.GAS_CANISTER.get(), 2));
    }

    /** The rest of a soldier's kit, handed over at graduation. */
    public static void giveFieldKit(ServerPlayer player) {
        give(player, new ItemStack(ModItems.BLADE_PACK.get(), 2));
        give(player, new ItemStack(ModItems.GAS_CANISTER.get(), 2));
        give(player, new ItemStack(ModItems.FLARE_GUN.get()));
        give(player, new ItemStack(ModItems.FLARE_GREEN.get(), 8));
        give(player, new ItemStack(ModItems.FLARE_RED.get(), 8));
        give(player, new ItemStack(ModItems.FLARE_BLACK.get(), 4));
        give(player, new ItemStack(ModItems.THUNDER_SPEAR.get(), 2));
        give(player, new ItemStack(Items.BREAD, 16));
    }

    public static void equipOrGive(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        if (player.getItemBySlot(slot).isEmpty()) {
            player.setItemSlot(slot, stack);
        } else {
            give(player, stack);
        }
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                                                "titans"
                                                                            )
                                                                            .requires(s -> s.hasPermission(2)))
                                                                        .then(((LiteralArgumentBuilder)Commands.literal("breach").executes(ctx -> {
                                                                            ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
                                                                            if (!TitanSpawner.isShiganshina(level)) {
                                                                                ((CommandSourceStack)ctx.getSource())
                                                                                    .sendFailure(Component.translatable("command.takemtitans.not_shiganshina"));
                                                                                return 0;
                                                                            } else if (!BreachEvent.start(level)) {
                                                                                ((CommandSourceStack)ctx.getSource())
                                                                                    .sendFailure(Component.translatable("command.takemtitans.breach_running"));
                                                                                return 0;
                                                                            } else {
                                                                                ((CommandSourceStack)ctx.getSource())
                                                                                    .sendSuccess(
                                                                                        () -> Component.translatable("command.takemtitans.breach_started"),
                                                                                        true
                                                                                    );
                                                                                return 1;
                                                                            }
                                                                        })).then(Commands.literal("reset").executes(ctx -> {
                                                                            BreachData.get(((CommandSourceStack)ctx.getSource()).getLevel()).setBreached(false);
                                                                            ((CommandSourceStack)ctx.getSource())
                                                                                .sendSuccess(
                                                                                    () -> Component.translatable("command.takemtitans.breach_reset"), true
                                                                                );
                                                                            return 1;
                                                                        }))))
                                                                    .then(Commands.literal("kit").executes(ctx -> {
                                                                        giveKit(((CommandSourceStack)ctx.getSource()).getPlayerOrException());
                                                                        return 1;
                                                                    })))
                                                                .then(Commands.literal("elite").executes(ctx -> {
                                                                    ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                                                    give(p, OdmGear.createFullGear(ModItems.ADVANCED_ODM_GEAR.get()));
                                                                    give(p, new ItemStack(ModItems.ACKERMAN_BLADES.get()));
                                                                    give(p, new ItemStack(ModItems.VETERAN_BLADES.get()));
                                                                    give(p, new ItemStack(ModItems.THUNDER_SPEAR.get(), 4));
                                                                    give(p, new ItemStack(ModItems.COLOSSAL_SERUM.get(), 2));
                                                                    give(p, new ItemStack(ModItems.RUMBLING_HORN.get()));
                                                                    give(p, OdmGear.createFullGear(ModItems.GODSPEED_ODM_GEAR.get()));
                                                                    give(p, new ItemStack(ModItems.TITANBREAKER_BLADES.get()));
                                                                    give(p, new ItemStack(ModItems.BEAST_BOULDER.get(), 4));
                                                                    give(p, new ItemStack(ModItems.PATHS_SHARD.get(), 4));
                                                                    ((CommandSourceStack)ctx.getSource())
                                                                        .sendSuccess(() -> Component.translatable("command.takemtitans.elite"), false);
                                                                    return 1;
                                                                })))
                                                            .then(Commands.literal("overdrive").executes(ctx -> {
                                                                ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                                                OdmServer.startOverdrive(p, OdmServer.data(p));
                                                                return 1;
                                                            })))
                                                        .then(
                                                            Commands.literal("collapse")
                                                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
                                                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                                                    Destruction.requestCollapse(
                                                                        ((CommandSourceStack)ctx.getSource()).getLevel(), pos, 20, 64, 8
                                                                    );
                                                                    return 1;
                                                                }))
                                                        ))
                                                    .then(
                                                        Commands.literal("blast")
                                                            .then(
                                                                Commands.argument("pos", Vec3Argument.vec3())
                                                                    .then(
                                                                        Commands.argument("radius", FloatArgumentType.floatArg(1.0F, 16.0F)).executes(ctx -> {
                                                                            Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                                                                            float r = FloatArgumentType.getFloat(ctx, "radius");
                                                                            ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
                                                                            Fx.send(level, 4, pos, r, 0.0F, 192.0);
                                                                            Destruction.blast(level, pos, (double)r);
                                                                            return 1;
                                                                        })
                                                                    )
                                                            )
                                                    ))
                                                .then(
                                                    Commands.literal("boulders")
                                                        .then(
                                                            Commands.argument("pos", Vec3Argument.vec3())
                                                                .executes(
                                                                    ctx -> {
                                                                        BoulderBarrage.start(
                                                                            ((CommandSourceStack)ctx.getSource()).getLevel(),
                                                                            Vec3Argument.getVec3(ctx, "pos"),
                                                                            ((CommandSourceStack)ctx.getSource()).getEntity()
                                                                        );
                                                                        return 1;
                                                                    }
                                                                )
                                                        )
                                                ))
                                            .then(
                                                Commands.literal("rift")
                                                    .then(
                                                        Commands.argument("pos", Vec3Argument.vec3())
                                                            .executes(
                                                                ctx -> {
                                                                    Rift.start(
                                                                        ((CommandSourceStack)ctx.getSource()).getLevel(),
                                                                        Vec3Argument.getVec3(ctx, "pos"),
                                                                        ((CommandSourceStack)ctx.getSource()).getEntity()
                                                                    );
                                                                    return 1;
                                                                }
                                                            )
                                                    )
                                            ))
                                        .then(Commands.literal("trost").executes(ctx -> {
                                            Entity e = ((CommandSourceStack)ctx.getSource()).getEntityOrException();
                                            e.teleportTo(0.5, 67.0, -387.5);
                                            return 1;
                                        })))
                                    .then(Commands.literal("shiganshina").executes(ctx -> {
                                        Entity e = ((CommandSourceStack)ctx.getSource()).getEntityOrException();
                                        e.teleportTo(0.5, 67.0, 12.5);
                                        return 1;
                                    })))
                                .then(
                                    Commands.literal("raid")
                                        .executes(
                                            ctx -> TitanRaid.start(
                                                        ((CommandSourceStack)ctx.getSource()).getLevel(),
                                                        ((CommandSourceStack)ctx.getSource()).getPlayerOrException()
                                                    )
                                                    ? 1
                                                    : 0
                                        )
                                )
                                .then(Commands.literal("tunnels").executes(ctx -> {
                                    TunnelDungeon.ensureBuilt(((CommandSourceStack)ctx.getSource()).getLevel());
                                    ((CommandSourceStack)ctx.getSource()).getEntityOrException().teleportTo(304.5, 68.0, -1093.5);
                                    return 1;
                                }))
                                .then(Commands.literal("karanes").executes(ctx -> toDistrict((CommandSourceStack)ctx.getSource(), "karanes")))
                                .then(Commands.literal("krolva").executes(ctx -> toDistrict((CommandSourceStack)ctx.getSource(), "krolva")))
                                .then(Commands.literal("stohess").executes(ctx -> toDistrict((CommandSourceStack)ctx.getSource(), "stohess")))
                                .then(Commands.literal("ehrmich").executes(ctx -> toDistrict((CommandSourceStack)ctx.getSource(), "ehrmich")))
                                .then(Commands.literal("mitras").executes(ctx -> {
                                    Entity e = ((CommandSourceStack)ctx.getSource()).getEntityOrException();
                                    e.teleportTo(0.5, 67.0, -880.5);
                                    return 1;
                                })))
                            .then(((LiteralArgumentBuilder)Commands.literal("story").then(Commands.literal("next").executes(ctx -> {
                                Story.skipStage(((CommandSourceStack)ctx.getSource()).getPlayerOrException());
                                return 1;
                            }))).then(Commands.literal("reset").executes(ctx -> {
                                Story.reset(((CommandSourceStack)ctx.getSource()).getPlayerOrException());
                                return 1;
                            }))))
                        .then(Commands.literal("xp").then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000)).executes(ctx -> {
                            Story.addXp(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount"));
                            return 1;
                        }))))
                    .then(Commands.literal("rumbling").executes(ctx -> {
                        Rumbling.start(((CommandSourceStack)ctx.getSource()).getLevel(), ((CommandSourceStack)ctx.getSource()).getEntityOrException());
                        return 1;
                    })))
                .then(Commands.literal("serum").then(Commands.argument("pos", Vec3Argument.vec3()).executes(ctx -> {
                    Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                    ColossalBlast.start(((CommandSourceStack)ctx.getSource()).getLevel(), pos, ((CommandSourceStack)ctx.getSource()).getEntity());
                    return 1;
                })))
        );
        // separate literal: Brigadier merges it into /titans
        d.register(
            Commands.literal("titans")
                .requires(s -> s.hasPermission(2))
                .then(
                    Commands.literal("training")
                        .then(Commands.literal("skip").executes(ctx -> {
                            Training.skip(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("restart").executes(ctx -> {
                            Training.restart(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("yard").executes(ctx -> {
                            ctx.getSource().getPlayerOrException().teleportTo(TrainingGround.GATE.x, TrainingGround.GATE.y, TrainingGround.GATE.z);
                            return 1;
                        }))
                )
        );
    }

    private static int toDistrict(CommandSourceStack src, String name) throws CommandSyntaxException {
        Entity e = src.getEntityOrException();

        for (ShiganshinaLayout.Bulge b : ShiganshinaLayout.BULGES) {
            if (b.name().equals(name)) {
                double nx = b.cx();
                double nz = b.cz() - -1100.0;
                double len = Math.max(1.0E-4, Math.sqrt(nx * nx + nz * nz));
                double x = b.cx() + nx / len * b.r() * 0.45;
                double z = b.cz() + nz / len * b.r() * 0.45;
                int y = src.getLevel().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(x), (int)Math.floor(z));
                e.teleportTo(Math.floor(x) + 0.5, (double)Math.max(y, 67), Math.floor(z) + 0.5);
                return 1;
            }
        }

        return 0;
    }
}
