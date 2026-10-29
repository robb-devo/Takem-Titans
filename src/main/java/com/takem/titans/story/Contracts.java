package com.takem.titans.story;

import com.takem.titans.entity.titan.AbstractTitan;
import com.takem.titans.odm.OdmPlayerData;
import com.takem.titans.registry.ModItems;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

final class Contracts {
    static final int KINDS = 5;

    private Contracts() {
    }

    static void talk(ServerPlayer player, OdmPlayerData d, List<Component> lines, List<Integer> optIds, List<Component> optLabels) {
        if (d.contractKind <= 0) {
            RandomSource r = player.getRandom();
            // a hunt for marked titans only once there are any about
            d.contractKind = 1 + r.nextInt(Story.threatTier(player) >= 2 ? KINDS : KINDS - 1);

            d.contractNeed = switch (d.contractKind) {
                case 1 -> 6 + r.nextInt(7);
                case 2, 3 -> 2 + r.nextInt(3);
                case 5 -> 1 + r.nextInt(2);
                default -> 2 + r.nextInt(2);
            };
            d.stageCount = 0;
            lines.add(Component.translatable("dialogue.takemtitans.contract.new." + r.nextInt(3), player.getName()));
            lines.add(
                Component.translatable(
                    "dialogue.takemtitans.contract.task", Component.translatable("contract.takemtitans.kind." + d.contractKind, d.stageCount, d.contractNeed)
                )
            );
            lines.add(Component.translatable("dialogue.takemtitans.contract.reward", reward(weight(d)), badges(weight(d))));
            optIds.add(1);
            optLabels.add(Component.translatable("dialogue.takemtitans.contract.accept"));
            Story.sync(player);
        } else {
            lines.add(
                Component.translatable(
                    "dialogue.takemtitans.contract.progress",
                    Component.translatable("contract.takemtitans.kind." + d.contractKind, d.stageCount, d.contractNeed)
                )
            );
            lines.add(Component.translatable("dialogue.takemtitans.contract.done_count", d.contractsDone));
        }
    }

    static void accept(ServerPlayer player, OdmPlayerData d) {
    }

    /** How much a contract is worth: a marked titan counts as four ordinary ones. */
    static int weight(OdmPlayerData d) {
        return d.contractKind == 5 ? d.contractNeed * 4 : d.contractNeed;
    }

    static int reward(int need) {
        return 150 + 30 * need;
    }

    static int badges(int need) {
        return 4 + need;
    }

    static boolean counts(int kind, AbstractTitan t) {
        return switch (kind) {
            case 1 -> true;
            case 2 -> Story.isAbnormal(t);
            case 3 -> t.getTitanHeight() >= 12.0F;
            case 4 -> Story.isCrawler(t);
            case 5 -> t.elite() != com.takem.titans.entity.titan.Elite.NONE;
            default -> false;
        };
    }

    static void onKill(ServerPlayer player, OdmPlayerData d, AbstractTitan titan) {
        if (d.contractKind > 0 && counts(d.contractKind, titan)) {
            d.stageCount++;
            if (d.stageCount < d.contractNeed) {
                Story.sync(player);
            } else {
                int need = weight(d);
                d.contractKind = 0;
                d.contractNeed = 0;
                d.stageCount = 0;
                d.contractsDone++;
                Story.give(player, new ItemStack(ModItems.MERIT_BADGE.get(), badges(need)));
                Story.banner(
                    player,
                    "stage",
                    Component.translatable("story.takemtitans.contract_complete"),
                    Component.literal("+" + reward(need) + " XP  ·  ").append(Component.translatable("story.takemtitans.badges", badges(need)))
                );
                Story.addXp(player, reward(need));
            }
        }
    }
}
