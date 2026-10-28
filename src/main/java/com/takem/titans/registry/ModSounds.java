package com.takem.titans.registry;

import com.takem.titans.TakemTitans;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, "takemtitans");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOOK_FIRE = reg("odm.hook_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEART_BEAT = reg("tunnel.heart_beat");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEART_HIT = reg("tunnel.heart_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEART_BURST = reg("tunnel.heart_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> TUNNEL_AMBIENCE = reg("tunnel.ambience");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_HEAVY = reg("cannon.heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_BATTERY = reg("cannon.battery");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_RELOAD = reg("cannon.reload");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSIVE_THROW = reg("explosive.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_STICK = reg("explosive.charge_stick");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_BEEP = reg("explosive.charge_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLUSTER_SPLIT = reg("explosive.cluster_split");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOMBLET_POP = reg("explosive.bomblet_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> THERMO_CLOUD = reg("explosive.thermo_cloud");
    public static final DeferredHolder<SoundEvent, SoundEvent> THERMO_BLAST = reg("explosive.thermo_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIG_BLAST = reg("explosive.big_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_TAIL = reg("explosive.blast_tail");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOOK_HIT = reg("odm.hook_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOOK_HIT_FLESH = reg("odm.hook_hit_flesh");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOOK_RETRACT = reg("odm.hook_retract");
    public static final DeferredHolder<SoundEvent, SoundEvent> REEL = reg("odm.reel");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAS = reg("odm.gas");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAS_BURST = reg("odm.gas_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAS_EMPTY = reg("odm.gas_empty");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = reg("odm.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> REFILL = reg("odm.refill");
    public static final DeferredHolder<SoundEvent, SoundEvent> EQUIP = reg("odm.equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_SLASH = reg("blade.slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_HIT = reg("blade.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_NAPE = reg("blade.nape");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_BREAK = reg("blade.break");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_RELOAD = reg("blade.reload");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_DRAW = reg("blade.draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_STEP = reg("titan.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_GROAN = reg("titan.groan");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_ROAR = reg("titan.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_HURT = reg("titan.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_DEATH = reg("titan.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_STEAM = reg("titan.steam");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_BITE = reg("titan.bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_GRAB = reg("titan.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_FALL = reg("titan.fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABNORMAL_SCREECH = reg("titan.abnormal_screech");
    public static final DeferredHolder<SoundEvent, SoundEvent> FEMALE_SCREAM = reg("titan.female_scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> GHOUL_SHRIEK = reg("titan.ghoul_shriek");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSAL_ARRIVAL = reg("titan.colossal_arrival");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSAL_STEAM = reg("titan.colossal_steam");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALL_BREACH = reg("titan.wall_breach");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLING_STEP = reg("titan.rumbling_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLING_EMERGE = reg("titan.rumbling_emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_HIT_FLESH = reg("titan.hit_flesh");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLING_HORN = reg("event.rumbling_horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLING_QUAKE = reg("event.rumbling_quake");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOULDER_WHOOSH = reg("event.boulder_whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOULDER_IMPACT = reg("event.boulder_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOULDER_THROW = reg("event.boulder_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_OPEN = reg("event.rift_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_HUM = reg("event.rift_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_IMPLODE = reg("event.rift_implode");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_DETONATE = reg("event.rift_detonate");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUILDING_COLLAPSE = reg("event.building_collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLASS_SHATTER = reg("event.glass_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SONIC_BOOM = reg("odm.sonic_boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> AFTERBURNER = reg("odm.afterburner");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLASH_WAVE = reg("blade.slash_wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLASH_WAVE_CUT = reg("blade.slash_wave_cut");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_IMPACT = reg("blade.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> NAPE_THUNDER = reg("blade.nape_thunder");
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_SLAM = reg("odm.meteor_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_SCREAM = reg("npc.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_CLANG = reg("titan.armor_clang");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_SHATTER = reg("titan.armor_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAST_ROAR = reg("titan.beast_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = reg("story.level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUEST_COMPLETE = reg("story.quest_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUTSCENE_STING = reg("story.cutscene_sting");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_MUSIC = reg("story.boss_music");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_EXPLORE = reg("music.explore");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DANGER = reg("music.danger");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_BATTLE = reg("music.battle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIALOGUE_BLIP = reg("story.dialogue_blip");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_SHOOT = reg("item.flare_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_BURN = reg("item.flare_burn");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_SPEAR_LAUNCH = reg("item.thunder_spear_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_SPEAR_BLAST = reg("item.thunder_spear_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_FIRE = reg("block.cannon_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_MOVE = reg("block.gate_move");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL = reg("block.alarm_bell");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(TakemTitans.id(name)));
    }
}
